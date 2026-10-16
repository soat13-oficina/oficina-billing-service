package br.com.oficina.billing.integracao.infrastructure.web.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import br.com.oficina.billing.integracao.domain.model.TokenIntegracao;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "TokenResumoResponse", description = "Resumo de um token de integração (sem o segredo)")
public record TokenResumoResponse(
        UUID id,
        String rotulo,
        String status,
        LocalDateTime criadoEm,
        String criadoPor,
        LocalDateTime revogadoEm,
        String revogadoPor) {

    public static TokenResumoResponse from(TokenIntegracao token) {
        return new TokenResumoResponse(
                token.getId(),
                token.getRotulo(),
                token.getStatus().name(),
                token.getCriadoEm(),
                token.getCriadoPor(),
                token.getRevogadoEm(),
                token.getRevogadoPor());
    }
}
