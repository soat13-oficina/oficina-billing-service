package br.com.oficina.billing.integracao.infrastructure.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class WebhookTokenFilter extends OncePerRequestFilter {
    private static final String HEADER_TOKEN = "X-Webhook-Token";
    private static final String PREFIXO_WEBHOOK_ORCAMENTO = "/integracoes/orcamentos/";

    private final String webhookSecret;
    private final TokenIntegracaoService tokenIntegracaoService;

    public WebhookTokenFilter(
            @Value("${orcamento.webhook.secret}") String webhookSecret,
            TokenIntegracaoService tokenIntegracaoService) {
        this.webhookSecret = webhookSecret;
        this.tokenIntegracaoService = tokenIntegracaoService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!ehWebhookOrcamento(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER_TOKEN);
        // Aceita um token de integracao gerado (validado por hash) OU o segredo estatico (coexistencia
        // temporaria; o estatico sera descontinuado em etapa posterior — spec 015).
        boolean tokenGeradoValido = tokenIntegracaoService.validar(token);
        boolean segredoEstaticoValido = token != null && webhookSecret != null && !webhookSecret.isBlank()
                && MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), webhookSecret.getBytes(StandardCharsets.UTF_8));
        if (!tokenGeradoValido && !segredoEstaticoValido) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean ehWebhookOrcamento(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && request.getRequestURI().startsWith(PREFIXO_WEBHOOK_ORCAMENTO)
                && request.getRequestURI().endsWith("/decisao");
    }
}
