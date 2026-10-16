package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.ConflitoDeRecursoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.DecidirOrcamentoExternamenteCommand;
import br.com.oficina.billing.orcamento.application.result.DecisaoOrcamentoResult;
import br.com.oficina.billing.orcamento.application.saga.EventosOrcamento;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.DecisaoOrcamento;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

/**
 * Fase 4: a decisao externa decide SO o orcamento e publica o evento da Saga. Quem move a ordem de
 * servico e o orquestrador (coberto em OrquestradorSagaOrdemServicoTest e no BDD).
 */
class DecisaoOrcamentoServiceTest {

    private static final String NUMERO_ORC = "ORC-DECISAO-001";
    private static final UUID ORDEM_ID = UUID.fromString("a1111111-1111-1111-1111-111111111111");

    private TestOrcamentoRepository orcamentoRepository;
    private EventosRegistrados eventos;
    private DecidirOrcamentoExternamenteService service;

    @BeforeEach
    void setUp() {
        orcamentoRepository = new TestOrcamentoRepository();
        eventos = new EventosRegistrados();
        AprovarOrcamentoService aprovarService = new AprovarOrcamentoService(orcamentoRepository, eventos);
        RejeitarOrcamentoService rejeitarService = new RejeitarOrcamentoService(orcamentoRepository, eventos);
        service = new DecidirOrcamentoExternamenteService(orcamentoRepository, aprovarService, rejeitarService);
    }

    @Test
    void aprovacaoDecideOOrcamentoEPublicaOEventoParaASaga() {
        orcamentoAguardandoAprovacao();

        DecisaoOrcamentoResult resultado = service.decidir(
                new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.APROVADO));

        assertEquals(new DecisaoOrcamentoResult(NUMERO_ORC, StatusOrcamento.APROVADO, ORDEM_ID), resultado);
        assertEquals(StatusOrcamento.APROVADO, orcamentoRepository.buscarPorNumeroOrcamento(NUMERO_ORC).orElseThrow().getStatus());
        assertEquals(List.of("APROVADO:INTEGRACAO"), eventos.publicados);
    }

    @Test
    void rejeicaoDecideOOrcamentoEPublicaOEventoParaASaga() {
        orcamentoAguardandoAprovacao();

        DecisaoOrcamentoResult resultado = service.decidir(
                new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.REJEITADO));

        assertEquals(StatusOrcamento.REJEITADO, resultado.statusOrcamento());
        assertEquals(List.of("REJEITADO:INTEGRACAO"), eventos.publicados);
    }

    @Test
    void retryIdenticoEhIdempotenteENaoPublicaDeNovo() {
        orcamentoAguardandoAprovacao();

        service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.APROVADO));
        DecisaoOrcamentoResult resultado = service.decidir(
                new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.APROVADO));

        assertEquals(StatusOrcamento.APROVADO, resultado.statusOrcamento());
        assertEquals(1, eventos.publicados.size());
    }

    @Test
    void retryIdenticoDeRejeicaoTambemEhIdempotente() {
        orcamentoAguardandoAprovacao();

        service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.REJEITADO));
        DecisaoOrcamentoResult resultado = service.decidir(
                new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.REJEITADO));

        assertEquals(StatusOrcamento.REJEITADO, resultado.statusOrcamento());
    }

    @Test
    void decisaoDivergenteDaRegistradaEhConflito() {
        orcamentoAguardandoAprovacao();
        service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.APROVADO));

        ConflitoDeRecursoException exception = assertThrows(
                ConflitoDeRecursoException.class,
                () -> service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.REJEITADO)));

        assertEquals("Decisao divergente da decisao ja registrada para o orcamento.", exception.getMessage());
    }

    @Test
    void orcamentoCanceladoNaoRecebeDecisao() {
        Orcamento orcamento = orcamentoAguardandoAprovacao();
        orcamento.cancelar();
        orcamentoRepository.atualizar(orcamento);

        RegraDeNegocioException exception = assertThrows(
                RegraDeNegocioException.class,
                () -> service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, DecisaoOrcamento.APROVADO)));

        assertEquals("Orcamento so pode receber decisao quando estiver aguardando aprovacao.", exception.getMessage());
        assertEquals(List.of(), eventos.publicados);
    }

    @Test
    void decisaoEhObrigatoria() {
        orcamentoAguardandoAprovacao();

        RegraDeNegocioException exception = assertThrows(
                RegraDeNegocioException.class,
                () -> service.decidir(new DecidirOrcamentoExternamenteCommand(NUMERO_ORC, null)));

        assertEquals("Decisao do orcamento e obrigatoria.", exception.getMessage());
    }

    private Orcamento orcamentoAguardandoAprovacao() {
        Orcamento orc = new Orcamento(
                NUMERO_ORC,
                UUID.randomUUID(),
                ORDEM_ID,
                UUID.randomUUID(),
                "Maria",
                "12345678909",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de oleo",
                List.of("Troca de oleo"),
                List.of(),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30),
                null,
                StatusOrcamento.AGUARDANDO_APROVACAO);
        return orcamentoRepository.salvar(orc);
    }

    /** Dublê escrito a mão (sem Mockito, como o resto da suite). */
    static class EventosRegistrados implements EventosOrcamento {
        final List<String> publicados = new ArrayList<>();

        @Override
        public void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal) {
            publicados.add("APROVADO:" + canal);
        }

        @Override
        public void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal) {
            publicados.add("REJEITADO:" + canal);
        }

        @Override
        public void orcamentoGerado(Envelope comando, Orcamento orcamento) {
        }

        @Override
        public void orcamentoFalhou(Envelope comando, String motivo, List<String> pecasInvalidas) {
        }

        @Override
        public void aprovacaoRevertida(Envelope comando, Orcamento orcamento) {
        }

        @Override
        public void orcamentoCancelado(Envelope comando, Orcamento orcamento, boolean pagamentoCancelado) {
            publicados.add("CANCELADO");
        }
    }
}
