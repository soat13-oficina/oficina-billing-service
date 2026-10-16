package br.com.oficina.billing.orcamento.domain.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class OrcamentoTest {

    @Test
    void deveExporDadosDoOrcamentoECalcularValorTotal() {
        LocalDateTime criadoEm = LocalDateTime.of(2030, 1, 1, 10, 0);
        LocalDateTime validade = LocalDateTime.of(2030, 1, 10, 10, 0);
        UUID ordemDeServicoId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID funcionarioId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Orcamento orcamento = new Orcamento(
                "orc-1",
                ordemDeServicoId,
                funcionarioId,
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
                criadoEm,
                validade,
                "Prioridade alta",
                StatusOrcamento.AGUARDANDO_APROVACAO);

        orcamento.enviarParaAprovacao(LocalDateTime.of(2030, 1, 2, 9, 0));

        assertEquals("orc-1", orcamento.getNumeroOrcamento());
        assertEquals(ordemDeServicoId, orcamento.getOrdemDeServicoId());
        assertEquals(funcionarioId, orcamento.getFuncionarioId());
        assertEquals("Joao Silva", orcamento.getClienteNome());
        assertEquals("12345678909", orcamento.getClienteCpf());
        assertEquals("ABC1D23", orcamento.getPlacaVeiculo());
        assertEquals("Toyota", orcamento.getMarcaVeiculo());
        assertEquals("Corolla", orcamento.getModeloVeiculo());
        assertEquals("Troca de pastilhas", orcamento.getDescricaoDiagnostico());
        assertEquals(List.of("Troca de pastilhas"), orcamento.getServicosPropostos());
        assertEquals(List.of("Pastilha dianteira"), orcamento.getPecasPrevistas());
        assertEquals(new BigDecimal("150.00"), orcamento.getValorMaoDeObra());
        assertEquals(new BigDecimal("250.00"), orcamento.getValorPecas());
        assertEquals(new BigDecimal("400.00"), orcamento.getValorTotal());
        assertEquals(criadoEm, orcamento.getCriadoEm());
        assertEquals(validade, orcamento.getValidade());
        assertEquals("Prioridade alta", orcamento.getObservacoes());
        assertEquals(StatusOrcamento.AGUARDANDO_APROVACAO, orcamento.getStatus());
        assertEquals(LocalDateTime.of(2030, 1, 2, 9, 0), orcamento.getEnviadoParaAprovacaoEm());
    }

    @Test
    void deveReconstituirComClienteIdEAprovarOuRejeitar() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID clienteId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        UUID ordemDeServicoId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        UUID funcionarioId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        Orcamento orcamento = Orcamento.reconstituir(
                id,
                "orc-2",
                clienteId,
                ordemDeServicoId,
                funcionarioId,
                "Maria Souza",
                "20100000053",
                "XYZ9Z99",
                "Honda",
                "City",
                "Revisao",
                List.of("Revisao"),
                List.of(new PecaOrcamento("peca-002", "Fluido", new BigDecimal("50.00"), 1)),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 2, 1, 10, 0),
                LocalDateTime.of(2030, 2, 10, 10, 0),
                "Sem observacoes",
                StatusOrcamento.AGUARDANDO_APROVACAO);

        orcamento.aprovar();
        assertEquals(StatusOrcamento.APROVADO, orcamento.getStatus());
        assertEquals(clienteId, orcamento.getClienteId());
        assertEquals(id, orcamento.getId());

        Orcamento orcamentoRejeitado = Orcamento.reconstituir(
                id,
                "orc-3",
                clienteId,
                ordemDeServicoId,
                funcionarioId,
                "Maria Souza",
                "20100000053",
                "XYZ9Z99",
                "Honda",
                "City",
                "Revisao",
                List.of("Revisao"),
                List.of(new PecaOrcamento("peca-002", "Fluido", new BigDecimal("50.00"), 1)),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 2, 1, 10, 0),
                LocalDateTime.of(2030, 2, 10, 10, 0),
                "Sem observacoes",
                StatusOrcamento.AGUARDANDO_APROVACAO);

        orcamentoRejeitado.rejeitar();
        assertEquals(StatusOrcamento.REJEITADO, orcamentoRejeitado.getStatus());
    }
}
