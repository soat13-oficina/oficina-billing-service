package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.application.command.ExcluirOrcamentoCommand;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

class ExcluirOrcamentoServiceTest {

    @Test
    void deveExcluirOrcamentoExistente() {
        TestOrcamentoRepository repository = new TestOrcamentoRepository();
        repository.salvar(novoOrcamento());
        ExcluirOrcamentoService service = new ExcluirOrcamentoService(repository);

        service.excluirOrcamento(new ExcluirOrcamentoCommand("orc-1"));

        assertTrue(repository.buscarPorNumeroOrcamento("orc-1").isEmpty());
    }

    @Test
    void deveFalharAoExcluirOrcamentoInexistente() {
        ExcluirOrcamentoService service = new ExcluirOrcamentoService(new TestOrcamentoRepository());

        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.excluirOrcamento(new ExcluirOrcamentoCommand("orc-404")));

        assertEquals("Orcamento nao encontrado para o numero informado.", exception.getMessage());
    }

    private Orcamento novoOrcamento() {
        return new Orcamento(
                "orc-1",
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Joao Silva",
                "12345678909",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de pastilhas",
                List.of("Troca de pastilhas"),
                List.of(new PecaOrcamento("peca-001", "Pastilha dianteira", new BigDecimal("250.00"), 1)),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 1, 1, 10, 0),
                LocalDateTime.of(2030, 1, 10, 10, 0),
                "Prioridade alta",
                StatusOrcamento.AGUARDANDO_APROVACAO);
    }
}
