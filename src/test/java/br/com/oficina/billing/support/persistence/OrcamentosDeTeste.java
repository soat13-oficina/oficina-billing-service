package br.com.oficina.billing.support.persistence;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;

/** Orcamentos prontos para os testes - no Billing nao ha OS nem cliente para semear, so o snapshot. */
public final class OrcamentosDeTeste {

    public static final UUID CLIENTE_ID = UUID.fromString("7b8c9d0e-1f2a-4b3c-8d4e-5f6a7b8c9d0e");
    public static final UUID FUNCIONARIO_ID = UUID.fromString("2c3d4e5f-6a7b-4c8d-9e0f-1a2b3c4d5e6f");

    private OrcamentosDeTeste() {
    }

    public static Orcamento novo(String numero, StatusOrcamento status) {
        return novo(numero, UUID.randomUUID(), CLIENTE_ID, status);
    }

    public static Orcamento novo(String numero, UUID ordemDeServicoId, UUID clienteId, StatusOrcamento status) {
        Orcamento orcamento = new Orcamento(
                numero,
                clienteId,
                ordemDeServicoId,
                FUNCIONARIO_ID,
                "Maria Silva",
                "52998224725",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Descricao diagnostico",
                List.of("Servico 1"),
                List.of(new PecaOrcamento("filtro", "Filtro de oleo", new BigDecimal("80.00"), 2)),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                LocalDateTime.now(),
                LocalDateTime.now().plusDays(30),
                null,
                status);
        orcamento.vincularSaga(UUID.randomUUID());
        return orcamento;
    }
}
