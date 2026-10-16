package br.com.oficina.billing.orcamento.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.orcamento.application.service.CadastrarNovoOrcamentoService;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.mensageria.PublicadorMensagensEmMemoria;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;
import br.com.oficina.billing.support.catalogo.CatalogoPecasEmMemoria;

/** Passo GerarOrcamento do lado do orcamento (futuro Billing): o orcamento nasce do snapshot do comando. */
class GerarOrcamentoPelaSagaTest {

    private static final UUID SAGA = UUID.randomUUID();
    private static final UUID CLIENTE = UUID.randomUUID();
    private static final UUID FUNCIONARIO = UUID.randomUUID();
    private static final Instant VALIDADE = Instant.parse("2030-01-01T03:00:00Z");

    private final TestOrcamentoRepository orcamentos = new TestOrcamentoRepository();
    private final CatalogoPecasEmMemoria pecas = new CatalogoPecasEmMemoria();
    private final PublicadorMensagensEmMemoria publicador = new PublicadorMensagensEmMemoria();
    private final FabricaEnvelopes fabrica = PublicadorMensagensEmMemoria.fabrica();
    private final GerarOrcamentoPelaSaga service = new GerarOrcamentoPelaSaga(
            new CadastrarNovoOrcamentoService(orcamentos, pecas),
            orcamentos,
            new PublicadorEventosOrcamento(publicador, fabrica),
            PublicadorMensagensEmMemoria.JSON,
            Duration.ofDays(7));
    private final UUID ordem = UUID.randomUUID();

    private Envelope comando(List<MensagensOrcamento.Servico> servicos, List<MensagensOrcamento.ItemPeca> itens,
                             BigDecimal maoDeObra, BigDecimal desconto, Instant validade) {
        return fabrica.novo(MensagensOrcamento.GERAR_ORCAMENTO, SAGA, ordem, new MensagensOrcamento.GerarOrcamento(
                "OS-1",
                new MensagensOrcamento.Cliente(CLIENTE, "Maria", "52998224725", "PF", "maria@exemplo.com"),
                new MensagensOrcamento.Veiculo("ABC1D23", "Toyota", "Corolla", 2020),
                new MensagensOrcamento.Funcionario(FUNCIONARIO, "Ana"),
                "Troca de oleo",
                servicos,
                itens,
                maoDeObra,
                desconto,
                validade,
                "Cliente com pressa"));
    }

    private void peca(String id, String preco) {
        pecas.cadastrar(id, "Peca " + id, preco);
    }

    @Test
    void geraOOrcamentoComOSnapshotDoComandoEResponde() {
        peca("filtro", "80.00");
        Envelope comando = comando(List.of(new MensagensOrcamento.Servico("Troca de oleo", null)),
                List.of(new MensagensOrcamento.ItemPeca("filtro", 2)), new BigDecimal("200.00"), new BigDecimal("10.00"), VALIDADE);

        service.gerar(comando);

        Orcamento orcamento = orcamentos.buscarPorOrdemDeServicoId(ordem).orElseThrow();
        assertThat(orcamento.getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
        assertThat(orcamento.getSagaId()).isEqualTo(SAGA);
        assertThat(orcamento.getClienteId()).isEqualTo(CLIENTE);
        assertThat(orcamento.getClienteNome()).isEqualTo("Maria");
        assertThat(orcamento.getFuncionarioId()).isEqualTo(FUNCIONARIO);
        assertThat(orcamento.getPlacaVeiculo()).isEqualTo("ABC1D23");
        assertThat(orcamento.getServicosPropostos()).containsExactly("Troca de oleo");
        assertThat(orcamento.getValorTotal()).isEqualByComparingTo("350.00");
        assertThat(orcamento.getValidade()).isEqualTo(LocalDateTime.ofInstant(VALIDADE, ZoneId.systemDefault()));
        assertThat(orcamento.getNumeroOrcamento()).startsWith("ORC-");

        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.ORCAMENTO_GERADO);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(resposta.payload().get("valorTotal").asString()).isEqualTo("350.00");
        assertThat(publicador.payload(resposta, MensagensOrcamento.OrcamentoGerado.class)).satisfies(gerado -> {
            assertThat(gerado.orcamentoId()).isEqualTo(orcamento.getId());
            assertThat(gerado.numeroOrcamento()).isEqualTo(orcamento.getNumeroOrcamento());
            assertThat(gerado.valorPecas()).isEqualByComparingTo("160.00");
            assertThat(gerado.validade()).isEqualTo(VALIDADE);
        });
    }

    @Test
    void semValidadeUsaOPadraoESemMaoDeObraTotalSomaOsServicos() {
        LocalDateTime antes = LocalDateTime.now();

        service.gerar(comando(List.of(
                        new MensagensOrcamento.Servico("Alinhamento", new BigDecimal("100.00")),
                        new MensagensOrcamento.Servico("Balanceamento", new BigDecimal("50.00")),
                        new MensagensOrcamento.Servico("Revisao", null)),
                null, null, null, null));

        Orcamento orcamento = orcamentos.buscarPorOrdemDeServicoId(ordem).orElseThrow();
        assertThat(orcamento.getValorMaoDeObra()).isEqualByComparingTo("150.00");
        assertThat(orcamento.getValidade()).isBetween(antes.plusDays(7), LocalDateTime.now().plusDays(7));
        assertThat(orcamento.getPecasOrcamento()).isEmpty();
    }

    @Test
    void comandoReenviadoEncontraOOrcamentoESoRepeteAResposta() {
        Envelope primeiro = comando(List.of(), List.of(), new BigDecimal("100.00"), null, VALIDADE);
        service.gerar(primeiro);
        UUID gerado = orcamentos.buscarPorOrdemDeServicoId(ordem).orElseThrow().getId();
        publicador.limpar();

        Envelope reenvio = comando(List.of(), List.of(), new BigDecimal("999.00"), null, VALIDADE);
        service.gerar(reenvio);

        assertThat(orcamentos.buscarTodos()).hasSize(1);
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.ORCAMENTO_GERADO);
        assertThat(resposta.causationId()).isEqualTo(reenvio.messageId());
        assertThat(publicador.payload(resposta, MensagensOrcamento.OrcamentoGerado.class).orcamentoId()).isEqualTo(gerado);
    }

    @Test
    void pecaInexistenteViraOrcamentoFalhouComAsPecasInvalidas() {
        peca("filtro", "80.00");

        service.gerar(comando(List.of(), List.of(
                        new MensagensOrcamento.ItemPeca("filtro", 1),
                        new MensagensOrcamento.ItemPeca("sumiu", 1)),
                new BigDecimal("100.00"), null, VALIDADE));

        assertThat(orcamentos.buscarTodos()).isEmpty();
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.ORCAMENTO_FALHOU);
        assertThat(publicador.payload(resposta, MensagensOrcamento.OrcamentoFalhou.class)).satisfies(falha -> {
            assertThat(falha.pecasInvalidas()).containsExactly("sumiu");
            assertThat(falha.motivo()).contains("sumiu");
        });
    }

    @Test
    void dadoFinanceiroInvalidoViraOrcamentoFalhouSemPecas() {
        service.gerar(comando(List.of(), List.of(), new BigDecimal("100.00"), new BigDecimal("500.00"), VALIDADE));

        assertThat(orcamentos.buscarTodos()).isEmpty();
        Envelope resposta = publicador.unica();
        assertThat(resposta.type()).isEqualTo(MensagensOrcamento.ORCAMENTO_FALHOU);
        assertThat(resposta.payload().has("pecasInvalidas")).as("campo opcional ausente").isFalse();
        assertThat(publicador.payload(resposta, MensagensOrcamento.OrcamentoFalhou.class).motivo()).contains("Desconto");
    }
}
