package br.com.oficina.billing.integracao.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import br.com.oficina.billing.integracao.infrastructure.web.dto.GerarTokenRequest;
import br.com.oficina.billing.integracao.infrastructure.web.dto.TokenGeradoResponse;
import br.com.oficina.billing.integracao.infrastructure.web.dto.TokenResumoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Tokens de Integração",
        description = "Geração e gestão de tokens de integração externa para autenticação do webhook")
public interface TokenIntegracaoControllerSwagger {

    @Operation(summary = "Gerar token de integração",
            description = "Gera um token opaco para um integrador externo. O segredo é exibido uma única vez nesta resposta e armazenado apenas como hash.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Token gerado (segredo exibido uma vez)"),
            @ApiResponse(responseCode = "400", description = "Rótulo ausente/inválido", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    ResponseEntity<TokenGeradoResponse> gerar(GerarTokenRequest request, Authentication authentication);

    @Operation(summary = "Listar tokens de integração",
            description = "Lista os tokens emitidos com rótulo, status e dados de auditoria. Nunca expõe o segredo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de tokens"),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    ResponseEntity<List<TokenResumoResponse>> listar();

    @Operation(summary = "Revogar token de integração",
            description = "Revoga um token; após a revogação ele deixa de ser aceito no webhook.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Token revogado"),
            @ApiResponse(responseCode = "404", description = "Token não encontrado", content = @Content),
            @ApiResponse(responseCode = "401", description = "Não autenticado", content = @Content)
    })
    ResponseEntity<Void> revogar(UUID id, Authentication authentication);
}
