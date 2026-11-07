package br.com.oficina.billing.orcamento.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Transactional
class JpaOrcamentoRepositoryIntegrationTest {

    @Autowired
    private JpaOrcamentoRepository repository;

    @Test
    void devePersistirBuscarAtualizarEFiltrarOrcamentoNoBanco() {
        UUID clienteId = UUID.randomUUID();
        UUID ordemId = UUID.randomUUID();
        UUID funcionarioId = UUID.randomUUID();
        Orcamento orcamento = new Orcamento(
                "ORC-2026-0001",
                clienteId,
                ordemId,
                funcionarioId,
                "Maria Silva",
                "12345678909",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de freios",
                List.of("Troca de disco", "Alinhamento"),
                List.of(
                        new PecaOrcamento("peca-disco-001", "Disco de freio", new BigDecimal("350.00"), 1),
                        new PecaOrcamento("peca-pastilha-001", "Pastilha", new BigDecimal("180.00"), 1)),
                new BigDecimal("420.00"),
                new BigDecimal("50.00"),
                LocalDateTime.of(2026, 4, 14, 10, 0),
                LocalDateTime.of(2026, 4, 21, 10, 0),
                "Cliente aguarda aprovacao",
                StatusOrcamento.REJEITADO);

        Orcamento salvo = repository.salvar(orcamento);

        assertTrue(repository.buscarPorId(salvo.getId()).isPresent());
        assertTrue(repository.buscarPorNumeroOrcamento("ORC-2026-0001").isPresent());
        assertTrue(repository.buscarPorOrdemDeServicoId(ordemId).isPresent());
        assertEquals(new BigDecimal("900.00"), repository.buscarPorId(salvo.getId()).orElseThrow().getValorTotal());

        salvo.enviarParaAprovacao(LocalDateTime.of(2026, 4, 14, 11, 0));
        repository.atualizar(salvo);

        Orcamento atualizado = repository.buscarPorId(salvo.getId()).orElseThrow();
        assertEquals(StatusOrcamento.AGUARDANDO_APROVACAO, atualizado.getStatus());
        assertEquals(2, atualizado.getPecasOrcamento().size());

        List<Orcamento> filtrados = repository.buscarPorFiltros("ORC-2026-0001", "12345678909", "ABC1D23");
        assertEquals(1, filtrados.size());
    }

    @Test
    void deveExcluirOrcamentoPorNumero() {
        repository.salvar(new Orcamento(
                "ORC-2026-0002",
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Carlos",
                "20120202247",
                "DEF2G34",
                "Honda",
                "Civic",
                "Revisao",
                List.of("Checklist"),
                List.of(),
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2026, 4, 14, 9, 0),
                LocalDateTime.of(2026, 4, 20, 9, 0),
                null,
                StatusOrcamento.APROVADO));

        repository.excluirPorNumeroOrcamento("ORC-2026-0002");

        assertFalse(repository.buscarPorNumeroOrcamento("ORC-2026-0002").isPresent());
    }
}
