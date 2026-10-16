package br.com.oficina.billing.orcamento.infrastructure.web;

import br.com.oficina.billing.orcamento.infrastructure.web.request.DecisaoOrcamentoRequest;
import br.com.oficina.billing.orcamento.infrastructure.web.response.DecisaoOrcamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Webhook de Decisão de Orçamento",
        description = "Recebe a decisão externa (aprovação/rejeição) de um orçamento via webhook autenticado por token.")
public interface WebhookDecisaoOrcamentoControllerSwagger {

    @Operation(
            summary = "Registrar decisão externa de orçamento",
            description = "Processa a decisão (APROVADO/REJEITADO) de um orçamento. Decisão do orçamento, reserva "
                    + "de peças e o evento da Saga (OrcamentoAprovado/OrcamentoRejeitado) são gravados na mesma "
                    + "transação; a ordem de serviço é movida pelo orquestrador da Saga logo depois, de forma "
                    + "assíncrona (Fase 4). Decisões idênticas a uma "
                    + "já registrada são idempotentes; decisões divergentes resultam em conflito (409). "
                    + "Autenticado via header X-Webhook-Token (token de integração gerado ou o segredo "
                    + "estático ORCAMENTO_WEBHOOK_SECRET) — não usa o Bearer JWT das demais rotas.",
            security = @SecurityRequirement(name = "webhookToken"))
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Decisão processada com sucesso, ou decisão idêntica à já registrada (idempotente)",
                    content = @Content(schema = @Schema(implementation = DecisaoOrcamentoResponse.class))),
            @ApiResponse(responseCode = "400",
                    description = "Entrada inválida (decisão ausente) ou orçamento em estado inválido para decisão",
                    content = @Content),
            @ApiResponse(responseCode = "404",
                    description = "Orçamento inexistente",
                    content = @Content),
            @ApiResponse(responseCode = "409",
                    description = "Decisão divergente de uma decisão já registrada para o orçamento",
                    content = @Content)
    })
    ResponseEntity<DecisaoOrcamentoResponse> decidir(
            @Parameter(description = "Número do orçamento alvo da decisão.") String numeroOrcamento,
            DecisaoOrcamentoRequest request);
}
