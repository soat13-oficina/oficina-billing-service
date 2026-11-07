package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mercadopago.exceptions.MPInvalidWebhookSignatureException;
import com.mercadopago.webhook.WebhookSignatureValidator;

import br.com.oficina.billing.pagamento.application.ResultadoPagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import tools.jackson.databind.JsonNode;

/**
 * Notificacoes do Mercado Pago (ADR 0007). Rota publica no API Gateway, sem JWT: a autenticidade vem
 * da assinatura {@code x-signature} (HMAC-SHA256 sobre {@code id:{data.id};request-id:{x-request-id};ts:{ts};},
 * tolerancia de 5 min), validada pelo SDK. O corpo nunca e usado como resultado: o pagamento e
 * consultado na API. Tipos que nao sao {@code payment} sao confirmados e ignorados.
 */
@RestController
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "mercadopago", matchIfMissing = true)
@Tag(name = "Pagamentos")
public class WebhookMercadoPagoController {
    private static final Logger log = LoggerFactory.getLogger(WebhookMercadoPagoController.class);

    private final ResultadoPagamentoService resultados;
    private final PropriedadesMercadoPago propriedades;
    private final MeterRegistry registry;

    public WebhookMercadoPagoController(ResultadoPagamentoService resultados, PropriedadesMercadoPago propriedades, MeterRegistry registry) {
        this.resultados = resultados;
        this.propriedades = propriedades;
        this.registry = registry;
    }

    @Operation(summary = "Webhook do Mercado Pago",
            description = "Notificacao de pagamento. Valida x-signature, consulta o pagamento na API do Mercado Pago "
                    + "e publica PagamentoConfirmado ou PagamentoRecusado para a Saga.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notificacao recebida (inclusive tipos ignorados)"),
            @ApiResponse(responseCode = "401", description = "Assinatura ausente, invalida ou fora da tolerancia")
    })
    @PostMapping("/webhooks/mercadopago")
    public ResponseEntity<Void> notificar(
            @RequestParam(name = "data.id", required = false) String dataIdNaUrl,
            @RequestParam(name = "type", required = false) String tipoNaUrl,
            @RequestHeader(name = "x-signature", required = false) String assinatura,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestBody(required = false) JsonNode corpo) {
        String dataId = dataIdNaUrl != null ? dataIdNaUrl : texto(corpo, "data", "id");
        String tipo = tipoNaUrl != null ? tipoNaUrl : texto(corpo, "type", null);
        if (!assinaturaValida(assinatura, requestId, dataId)) {
            // Webhook que o Mercado Pago manda e nos recusamos: segredo errado/vazio ou assinatura vencida. O pagamento
            // segue pendente ate expirar, entao e o sinal de "cliente pagou e a Saga nao soube" (monitor).
            registry.counter("oficina.pagamento.webhook.rejeitados").increment();
            return ResponseEntity.status(401).build();
        }
        if (!"payment".equals(tipo) || dataId == null || dataId.isBlank()) {
            log.info("Notificacao do Mercado Pago ignorada. tipo={}, dataId={}", tipo, dataId);
            return ResponseEntity.ok().build();
        }
        log.info("Notificacao de pagamento do Mercado Pago. paymentId={}", dataId);
        resultados.registrarNotificacao(dataId);
        return ResponseEntity.ok().build();
    }

    private boolean assinaturaValida(String assinatura, String requestId, String dataId) {
        String segredo = propriedades.webhookSecret();
        if (segredo == null || segredo.isBlank()) {
            log.error("mercadopago.webhook-secret vazio: notificacao recusada.");
            return false;
        }
        if (assinatura == null || requestId == null || dataId == null) {
            log.warn("Notificacao do Mercado Pago sem x-signature, x-request-id ou data.id: recusada.");
            return false;
        }
        try {
            WebhookSignatureValidator.validate(assinatura, requestId, dataId, segredo, propriedades.toleranciaAssinatura());
            return true;
        } catch (MPInvalidWebhookSignatureException e) {
            log.warn("Assinatura do webhook do Mercado Pago invalida. motivo={}", e.getMessage());
            return false;
        }
    }

    private static String texto(JsonNode corpo, String campo, String subcampo) {
        if (corpo == null) {
            return null;
        }
        JsonNode valor = subcampo == null ? corpo.path(campo) : corpo.path(campo).path(subcampo);
        return valor.isMissingNode() || valor.isNull() ? null : valor.asString();
    }
}
