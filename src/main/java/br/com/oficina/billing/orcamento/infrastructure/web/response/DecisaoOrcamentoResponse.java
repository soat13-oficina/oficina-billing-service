package br.com.oficina.billing.orcamento.infrastructure.web.response;

import java.util.UUID;

import br.com.oficina.billing.orcamento.application.result.DecisaoOrcamentoResult;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "DecisaoOrcamentoResponse", description = "Resultado da decisão externa de orçamento. "
        + "A ordem de serviço é movida pela Saga logo em seguida (consulte GET /ordens-servico/{numero} ou GET /sagas/{ordemServicoId}).")
public record DecisaoOrcamentoResponse(
        @Schema(description = "Número do orçamento", example = "ORC-1A2B3C4D")
        String numeroOrcamento,
        @Schema(description = "Status do orçamento após a decisão", example = "APROVADO")
        StatusOrcamento statusOrcamento,
        @Schema(description = "Ordem de serviço do orçamento")
        UUID ordemServicoId) {

    public static DecisaoOrcamentoResponse from(DecisaoOrcamentoResult result) {
        return new DecisaoOrcamentoResponse(result.numeroOrcamento(), result.statusOrcamento(), result.ordemDeServicoId());
    }
}
