package br.com.oficina.billing.integracao.infrastructure.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GerarTokenRequest", description = "Dados para gerar um token de integração")
public record GerarTokenRequest(
        @Schema(description = "Rótulo descritivo do integrador", example = "ERP do cliente X")
        String rotulo) {
}
