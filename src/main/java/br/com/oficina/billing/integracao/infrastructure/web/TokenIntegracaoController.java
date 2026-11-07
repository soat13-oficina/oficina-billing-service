package br.com.oficina.billing.integracao.infrastructure.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import br.com.oficina.billing.common.infrastructure.seguranca.UsuarioAutenticado;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.billing.integracao.infrastructure.web.dto.GerarTokenRequest;
import br.com.oficina.billing.integracao.infrastructure.web.dto.TokenGeradoResponse;
import br.com.oficina.billing.integracao.infrastructure.web.dto.TokenResumoResponse;
import br.com.oficina.billing.integracao.infrastructure.seguranca.TokenIntegracaoService;

@RestController
@RequestMapping("/integracoes/tokens")
public class TokenIntegracaoController implements TokenIntegracaoControllerSwagger {
    private static final Logger log = LoggerFactory.getLogger(TokenIntegracaoController.class);

    private final TokenIntegracaoService tokenIntegracaoService;

    public TokenIntegracaoController(TokenIntegracaoService tokenIntegracaoService) {
        this.tokenIntegracaoService = tokenIntegracaoService;
    }

    @Override
    @PostMapping
    public ResponseEntity<TokenGeradoResponse> gerar(
            @RequestBody GerarTokenRequest request,
            Authentication authentication) {
        String criadoPor = autor(authentication);
        log.info("Recebida requisicao para gerar token de integracao. criadoPor={}", criadoPor);
        TokenGeradoResponse response = TokenGeradoResponse.from(
                tokenIntegracaoService.gerar(request.rotulo(), criadoPor));
        return ResponseEntity.created(URI.create("/integracoes/tokens/" + response.id())).body(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<List<TokenResumoResponse>> listar() {
        List<TokenResumoResponse> tokens = tokenIntegracaoService.listar().stream()
                .map(TokenResumoResponse::from)
                .toList();
        return ResponseEntity.ok(tokens);
    }

    @Override
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revogar(@PathVariable UUID id, Authentication authentication) {
        String revogadoPor = autor(authentication);
        log.info("Recebida requisicao para revogar token de integracao. id={}, revogadoPor={}", id, revogadoPor);
        tokenIntegracaoService.revogar(id, revogadoPor);
        return ResponseEntity.noContent().build();
    }

    /** Subject do JWT (e-mail do funcionario). */
    private static String autor(Authentication authentication) {
        return authentication.getPrincipal() instanceof UsuarioAutenticado usuario ? usuario.subject() : authentication.getName();
    }
}
