package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.AprovarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.saga.EventosOrcamento;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

/** Fase 4: aprovar nao reserva pecas - so aprova e publica OrcamentoAprovado (a reserva e passo da Saga). */
class AprovarOrcamentoServiceTest {

    @Test
    void aprovaEPublicaOEventoSemTocarNoEstoque() {
        TestOrcamentoRepository orcamentoRepository = new TestOrcamentoRepository();
        orcamentoRepository.salvar(orcamento(StatusOrcamento.AGUARDANDO_APROVACAO));
        List<CanalDecisao> publicados = new ArrayList<>();

        Orcamento aprovado = new AprovarOrcamentoService(orcamentoRepository, eventos(publicados))
                .aprovarOrcamento(new AprovarOrcamentoCommand("ORC-1", CanalDecisao.CLIENTE));

        assertEquals(StatusOrcamento.APROVADO, aprovado.getStatus());
        assertEquals(StatusOrcamento.APROVADO, orcamentoRepository.buscarPorNumeroOrcamento("ORC-1").orElseThrow().getStatus());
        assertEquals(List.of(CanalDecisao.CLIENTE), publicados);
    }

    @Test
    void naoAprovaOrcamentoQueNaoAguardaDecisao() {
        TestOrcamentoRepository orcamentoRepository = new TestOrcamentoRepository();
        orcamentoRepository.salvar(orcamento(StatusOrcamento.CANCELADO));
        List<CanalDecisao> publicados = new ArrayList<>();
        AprovarOrcamentoService service = new AprovarOrcamentoService(orcamentoRepository, eventos(publicados));

        assertThrows(RegraDeNegocioException.class,
                () -> service.aprovarOrcamento(new AprovarOrcamentoCommand("ORC-1", CanalDecisao.CLIENTE)));
        assertEquals(List.of(), publicados);
    }

    @Test
    void orcamentoInexistenteENaoEncontrado() {
        AprovarOrcamentoService service = new AprovarOrcamentoService(new TestOrcamentoRepository(), EventosOrcamento.NENHUM);

        assertThrows(RecursoNaoEncontradoException.class,
                () -> service.aprovarOrcamento(new AprovarOrcamentoCommand("ORC-X", CanalDecisao.FUNCIONARIO)));
    }

    private static EventosOrcamento eventos(List<CanalDecisao> publicados) {
        return new EventosOrcamento() {
            @Override
            public void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal) {
                publicados.add(canal);
            }

            @Override
            public void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal) {
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
            }
        };
    }

    private static Orcamento orcamento(StatusOrcamento status) {
        return new Orcamento(
                "ORC-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Maria Silva",
                "12345678909",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de freios",
                List.of("Troca de disco"),
                List.of(new PecaOrcamento("peca-disco-001", "Disco de freio", new BigDecimal("350.00"), 2)),
                new BigDecimal("420.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 1, 1, 10, 0),
                LocalDateTime.of(2030, 1, 8, 10, 0),
                "Cliente aguarda aprovacao",
                status);
    }
}
