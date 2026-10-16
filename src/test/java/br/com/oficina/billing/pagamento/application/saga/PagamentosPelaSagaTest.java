package br.com.oficina.billing.pagamento.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;
import br.com.oficina.billing.pagamento.infrastructure.gateway.GatewayPagamentoSimulado;
import br.com.oficina.billing.support.mensageria.PublicadorMensagensEmMemoria;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;
import br.com.oficina.billing.support.persistence.TestPagamentoRepository;

/** Passo SolicitarPagamento e compensacao EstornarPagamento do lado do pagamento (futuro Billing). */
class PagamentosPelaSagaTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final Instant EXPIRA = Instant.parse("2026-10-17T15:00:00Z");

    /** Gateway que conta as chamadas e pode recusar o estorno. */
    private static final class GatewayDeTeste implements GatewayPagamento {
        private final GatewayPagamentoSimulado simulado = new GatewayPagamentoSimulado("https://checkout.teste");
        int cobrancas;
        boolean recusarEstorno;

        @Override
        public Cobranca criarCobranca(Pagamento pagamento, String descricao) {
            cobrancas++;
            return simulado.criarCobranca(pagamento, descricao);
        }

        @Override
        public Estorno estornar(Pagamento pagamento) {
            return recusarEstorno ? new Estorno(false, null, "Prazo de estorno do cartao encerrado") : simulado.estornar(pagamento);
        }

        @Override
        public java.util.Optional<SituacaoNoProvedor> consultarPagamento(String referenciaPagamento) {
            return simulado.consultarPagamento(referenciaPagamento);
        }

        @Override
        public java.util.Optional<SituacaoNoProvedor> buscarPorCobranca(Pagamento pagamento) {
            return simulado.buscarPorCobranca(pagamento);
        }
    }

    private final TestPagamentoRepository pagamentos = new TestPagamentoRepository();
    private final TestOrcamentoRepository orcamentos = new TestOrcamentoRepository();
    private final GatewayDeTeste gateway = new GatewayDeTeste();
    private final PublicadorMensagensEmMemoria publicador = new PublicadorMensagensEmMemoria();
    private final FabricaEnvelopes fabrica = PublicadorMensagensEmMemoria.fabrica();
    private final PagamentosPelaSaga service = new PagamentosPelaSaga(
            pagamentos, orcamentos, gateway, new EventosPagamento(publicador, fabrica), PublicadorMensagensEmMemoria.JSON);
    private final UUID ordem = UUID.randomUUID();

    private Orcamento orcamentoAprovado() {
        Orcamento orcamento = orcamentos.salvar(new Orcamento(
                "ORC-1", UUID.randomUUID(), ordem, UUID.randomUUID(), "Maria", "52998224725",
                "ABC1D23", "Toyota", "Corolla", "Troca de oleo", List.of("Troca de oleo"), List.of(),
                new BigDecimal("530"), BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
                StatusOrcamento.APROVADO));
        return orcamento;
    }

    private Envelope solicitar(UUID orcamentoId) {
        return fabrica.novo(MensagensPagamento.SOLICITAR_PAGAMENTO, SAGA, ordem, new MensagensPagamento.SolicitarPagamento(orcamentoId, EXPIRA));
    }

    private Envelope estornar(UUID orcamentoId, UUID pagamentoId) {
        return fabrica.novo(MensagensPagamento.ESTORNAR_PAGAMENTO, SAGA, ordem,
                new MensagensPagamento.EstornarPagamento(orcamentoId, pagamentoId, "FALHA_EXECUCAO"));
    }

    private Pagamento pagamentoAprovado(Orcamento orcamento) {
        service.solicitar(solicitar(orcamento.getId()));
        Pagamento pagamento = pagamentos.todos().get(0);
        pagamento.aprovar("PAY-1", LocalDateTime.now());
        publicador.limpar();
        return pagamento;
    }

    @Test
    void solicitacaoCriaACobrancaNoGatewayEResponde() {
        Orcamento orcamento = orcamentoAprovado();
        Envelope comando = solicitar(orcamento.getId());

        service.solicitar(comando);

        Pagamento pagamento = pagamentos.todos().get(0);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getValor()).isEqualByComparingTo("530.00");
        assertThat(pagamento.getOrcamentoId()).isEqualTo(orcamento.getId());
        assertThat(pagamento.getOrdemDeServicoId()).isEqualTo(ordem);
        assertThat(pagamento.getSagaId()).isEqualTo(SAGA);
        assertThat(pagamento.getExpiraEm()).isEqualTo(LocalDateTime.ofInstant(EXPIRA, ZoneId.systemDefault()));
        assertThat(pagamento.getMpPreferenceId()).isEqualTo("SIM-PREF-" + pagamento.getId());
        assertThat(pagamento.getLinkPagamento()).isEqualTo("https://checkout.teste/" + pagamento.getId());

        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensPagamento.PAGAMENTO_SOLICITADO);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(resposta.payload().get("valor").asString()).as("dinheiro com duas casas").isEqualTo("530.00");
        assertThat(publicador.payload(resposta, MensagensPagamento.PagamentoSolicitado.class)).satisfies(solicitado -> {
            assertThat(solicitado.pagamentoId()).isEqualTo(pagamento.getId());
            assertThat(solicitado.linkPagamento()).isEqualTo(pagamento.getLinkPagamento());
            assertThat(solicitado.expiraEm()).isEqualTo(EXPIRA);
        });
    }

    @Test
    void solicitacaoReenviadaNaoCriaOutraCobranca() {
        Orcamento orcamento = orcamentoAprovado();
        service.solicitar(solicitar(orcamento.getId()));
        publicador.limpar();

        Envelope reenvio = solicitar(orcamento.getId());
        service.solicitar(reenvio);

        assertThat(pagamentos.todos()).hasSize(1);
        assertThat(gateway.cobrancas).isEqualTo(1);
        Envelope resposta = publicador.unica();
        assertThat(resposta.causationId()).isEqualTo(reenvio.messageId());
        assertThat(publicador.payload(resposta, MensagensPagamento.PagamentoSolicitado.class).pagamentoId())
                .isEqualTo(pagamentos.todos().get(0).getId());
    }

    @Test
    void orcamentoInexistenteEhErroDoComando() {
        Envelope comando = solicitar(UUID.randomUUID());

        assertThatThrownBy(() -> service.solicitar(comando)).isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(publicador.publicadas()).isEmpty();
    }

    @Test
    void estornoDevolveOPagamentoCancelaOOrcamentoEConfirma() {
        Orcamento orcamento = orcamentoAprovado();
        Pagamento pagamento = pagamentoAprovado(orcamento);
        Envelope comando = estornar(orcamento.getId(), pagamento.getId());

        service.estornar(comando);

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.getMpRefundId()).startsWith("SIM-REFUND-");
        assertThat(pagamento.getMotivo()).isEqualTo("FALHA_EXECUCAO");
        assertThat(orcamentos.buscarPorId(orcamento.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensPagamento.PAGAMENTO_ESTORNADO);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(publicador.payload(resposta, MensagensPagamento.PagamentoEstornado.class))
                .isEqualTo(new MensagensPagamento.PagamentoEstornado(pagamento.getId(), new BigDecimal("530.00"), pagamento.getMpRefundId()));
    }

    @Test
    void estornoRepetidoSoRepublicaAConfirmacao() {
        Orcamento orcamento = orcamentoAprovado();
        Pagamento pagamento = pagamentoAprovado(orcamento);
        service.estornar(estornar(orcamento.getId(), pagamento.getId()));
        String referencia = pagamento.getMpRefundId();
        publicador.limpar();

        service.estornar(estornar(orcamento.getId(), pagamento.getId()));

        assertThat(pagamento.getMpRefundId()).as("nao estorna duas vezes").isEqualTo(referencia);
        assertThat(publicador.tipos()).containsExactly(MensagensPagamento.PAGAMENTO_ESTORNADO);
    }

    @Test
    void estornoRecusadoPeloProvedorRespondeEstornoFalhouEMantemOPagamento() {
        Orcamento orcamento = orcamentoAprovado();
        Pagamento pagamento = pagamentoAprovado(orcamento);
        gateway.recusarEstorno = true;

        service.estornar(estornar(orcamento.getId(), pagamento.getId()));

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(orcamentos.buscarPorId(orcamento.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.APROVADO);
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensPagamento.ESTORNO_FALHOU);
        assertThat(publicador.payload(resposta, MensagensPagamento.EstornoFalhou.class))
                .isEqualTo(new MensagensPagamento.EstornoFalhou(pagamento.getId(), "Prazo de estorno do cartao encerrado"));
    }

    @Test
    void pagamentoInexistenteOuNaoAprovadoNaoTemEstorno() {
        Orcamento orcamento = orcamentoAprovado();
        service.solicitar(solicitar(orcamento.getId()));
        Pagamento pendente = pagamentos.todos().get(0);
        publicador.limpar();
        UUID inexistente = UUID.randomUUID();

        service.estornar(estornar(orcamento.getId(), inexistente));
        service.estornar(estornar(orcamento.getId(), pendente.getId()));

        assertThat(publicador.tipos()).containsExactly(MensagensPagamento.ESTORNO_FALHOU, MensagensPagamento.ESTORNO_FALHOU);
        assertThat(publicador.payload(publicador.publicadas().get(0).envelope(), MensagensPagamento.EstornoFalhou.class))
                .isEqualTo(new MensagensPagamento.EstornoFalhou(inexistente, "Pagamento nao encontrado"));
        assertThat(publicador.payload(publicador.ultima(), MensagensPagamento.EstornoFalhou.class).motivo()).contains("PENDENTE");
        assertThat(pendente.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
    }
}
