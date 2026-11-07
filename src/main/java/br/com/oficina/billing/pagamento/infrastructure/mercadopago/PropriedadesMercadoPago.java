package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mercado Pago Checkout Pro (ADR 0007). Credenciais no Secrets Manager {@code oficina/<env>/mercadopago}
 * ({@code access_token} e {@code webhook_secret}), materializadas como Secret do Kubernetes - nunca no
 * repositorio.
 *
 * @param accessToken          token da aplicacao (de teste em hml: {@code APP_USR-...} das credenciais de teste)
 * @param webhookSecret        segredo da assinatura do webhook ({@code x-signature}); vazio = nenhum webhook aceito
 * @param notificationUrl      {@code https://<gateway>/webhooks/mercadopago}, enviado em cada preferencia
 * @param sandbox              usa o {@code sandbox_init_point} como link de pagamento
 * @param timeout              conexao e leitura de cada chamada a API
 * @param toleranciaAssinatura idade maxima do {@code ts} da assinatura do webhook
 */
@ConfigurationProperties("mercadopago")
public record PropriedadesMercadoPago(
        String accessToken,
        String webhookSecret,
        String notificationUrl,
        boolean sandbox,
        Duration timeout,
        Duration toleranciaAssinatura) {

    public PropriedadesMercadoPago {
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        toleranciaAssinatura = toleranciaAssinatura == null ? Duration.ofMinutes(5) : toleranciaAssinatura;
    }
}
