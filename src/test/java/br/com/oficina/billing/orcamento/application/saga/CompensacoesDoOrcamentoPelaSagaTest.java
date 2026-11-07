package br.com.oficina.billing.orcamento.application.saga;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.port.PagamentosDoOrcamento;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.mensageria.PublicadorMensagensEmMemoria;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

/** Compensacoes ReverterAprovacao e CancelarOrcamento do lado do orcamento (futuro Billing). */
class CompensacoesDoOrcamentoPelaSagaTest {

    private static final UUID SAGA = UUID.randomUUID();

    private final TestOrcamentoRepository orcamentos = new TestOrcamentoRepository();
    private final PublicadorMensagensEmMemoria publicador = new PublicadorMensagensEmMemoria();
    private final FabricaEnvelopes fabrica = PublicadorMensagensEmMemoria.fabrica();
    private final PublicadorEventosOrcamento eventos = new PublicadorEventosOrcamento(publicador, fabrica);
    private final List<UUID> cobrancasCanceladas = new ArrayList<>();
    private final PagamentosDoOrcamento pagamentos = orcamentoId -> {
        cobrancasCanceladas.add(orcamentoId);
        return true;
    };
    private final ReverterAprovacaoPelaSaga reverter =
            new ReverterAprovacaoPelaSaga(orcamentos, eventos, PublicadorMensagensEmMemoria.JSON);
    private final CancelarOrcamentoPelaSaga cancelar =
            new CancelarOrcamentoPelaSaga(orcamentos, pagamentos, eventos, PublicadorMensagensEmMemoria.JSON);
    private final UUID ordem = UUID.randomUUID();

    private Orcamento orcamento(StatusOrcamento status) {
        return orcamentos.salvar(new Orcamento(
                "ORC-" + UUID.randomUUID(), UUID.randomUUID(), ordem, UUID.randomUUID(), "Maria", "52998224725",
                "ABC1D23", "Toyota", "Corolla", "Troca de oleo", List.of("Troca de oleo"), List.of(),
                new BigDecimal("200.00"), BigDecimal.ZERO, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null, status));
    }

    private Envelope reverterAprovacao(UUID orcamentoId) {
        return fabrica.novo(MensagensOrcamento.REVERTER_APROVACAO, SAGA, ordem, new MensagensOrcamento.ReverterAprovacao(
                orcamentoId, "Estoque insuficiente", List.of(new MensagensOrcamento.PecaFaltante("filtro", 2, 1))));
    }

    private Envelope cancelarOrcamento(UUID orcamentoId) {
        return fabrica.novo(MensagensOrcamento.CANCELAR_ORCAMENTO, SAGA, ordem,
                new MensagensOrcamento.CancelarOrcamento(orcamentoId, "PAGAMENTO_RECUSADO"));
    }

    @Test
    void aprovacaoRevertidaVoltaAAguardarODecisaoDoClienteEConfirma() {
        Orcamento aprovado = orcamento(StatusOrcamento.APROVADO);
        Envelope comando = reverterAprovacao(aprovado.getId());

        reverter.reverter(comando);

        assertThat(orcamentos.buscarPorId(aprovado.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.APROVACAO_REVERTIDA);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(publicador.payload(resposta, MensagensOrcamento.AprovacaoRevertida.class).orcamentoId()).isEqualTo(aprovado.getId());
    }

    @Test
    void reversaoRepetidaSoRepublicaAConfirmacao() {
        Orcamento aguardando = orcamento(StatusOrcamento.AGUARDANDO_APROVACAO);

        reverter.reverter(reverterAprovacao(aguardando.getId()));

        assertThat(orcamentos.buscarPorId(aguardando.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        assertThat(publicador.tipos()).containsExactly(MensagensOrcamento.APROVACAO_REVERTIDA);
    }

    @Test
    void orcamentoRejeitadoNaoTemAprovacaoParaReverter() {
        Orcamento rejeitado = orcamento(StatusOrcamento.REJEITADO);

        assertThatThrownBy(() -> reverter.reverter(reverterAprovacao(rejeitado.getId())))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> reverter.reverter(reverterAprovacao(UUID.randomUUID())))
                .isInstanceOf(RecursoNaoEncontradoException.class);
        assertThat(publicador.publicadas()).isEmpty();
    }

    @Test
    void cancelamentoDoOrcamentoAprovadoLevaACobrancaPendenteEConfirma() {
        Orcamento aprovado = orcamento(StatusOrcamento.APROVADO);
        Envelope comando = cancelarOrcamento(aprovado.getId());

        cancelar.cancelar(comando);

        assertThat(orcamentos.buscarPorId(aprovado.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(cobrancasCanceladas).containsExactly(aprovado.getId());
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.ORCAMENTO_CANCELADO);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(publicador.payload(resposta, MensagensOrcamento.OrcamentoCancelado.class))
                .isEqualTo(new MensagensOrcamento.OrcamentoCancelado(aprovado.getId(), true));
    }

    @Test
    void cancelamentoRepetidoRepublicaAConfirmacao() {
        Orcamento aguardando = orcamento(StatusOrcamento.AGUARDANDO_APROVACAO);
        cancelar.cancelar(cancelarOrcamento(aguardando.getId()));
        publicador.limpar();

        cancelar.cancelar(cancelarOrcamento(aguardando.getId()));

        assertThat(orcamentos.buscarPorId(aguardando.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.CANCELADO);
        assertThat(publicador.tipos()).containsExactly(MensagensOrcamento.ORCAMENTO_CANCELADO);
    }

    @Test
    void semModuloDePagamentoNaoHaCobrancaACancelar() {
        CancelarOrcamentoPelaSaga semPagamento = new CancelarOrcamentoPelaSaga(
                orcamentos, PagamentosDoOrcamento.NENHUM, eventos, PublicadorMensagensEmMemoria.JSON);
        Orcamento aguardando = orcamento(StatusOrcamento.AGUARDANDO_APROVACAO);

        semPagamento.cancelar(cancelarOrcamento(aguardando.getId()));

        assertThat(publicador.payload(publicador.unica(), MensagensOrcamento.OrcamentoCancelado.class).pagamentoCancelado()).isFalse();
    }
}
