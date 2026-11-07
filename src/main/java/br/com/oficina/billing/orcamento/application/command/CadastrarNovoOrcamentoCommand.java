package br.com.oficina.billing.orcamento.application.command;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Dados para gerar um orcamento. Fase 4: chega pelo comando {@code GerarOrcamento} da Saga, com o
 * snapshot do cliente, do veiculo e do diagnostico - o orcamento nao consulta cliente nem OS (ADR 0003).
 *
 * @param sagaId Saga da OS; vai nos eventos que o orcamento publicar
 */
public record CadastrarNovoOrcamentoCommand(
        String numeroOrcamento,
        ClienteOrcamento cliente,
        UUID ordemDeServicoId,
        UUID funcionarioId,
        String placaVeiculo,
        String marcaVeiculo,
        String modeloVeiculo,
        String descricaoDiagnostico,
        List<String> servicosPropostos,
        List<PecaOrcamentoInput> pecasPrevistas,
        BigDecimal valorMaoDeObra,
        BigDecimal desconto,
        LocalDateTime validade,
        String observacoes,
        UUID sagaId) {

    /** Snapshot do cliente no momento do orcamento (ADR 0002). */
    public record ClienteOrcamento(UUID id, String nome, String documento) {
    }
}
