package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.mercadopago.net.MPRequest;

import br.com.oficina.billing.pagamento.application.port.GatewayPagamento.Cobranca;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento.Estorno;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento.ResultadoNoProvedor;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento.SituacaoNoProvedor;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/** Adaptador do Checkout Pro sobre o SDK oficial, com o HTTP trocado por um duble (sem rede). */
class GatewayPagamentoMercadoPagoTest {

    private static final LocalDateTime EXPIRA = LocalDateTime.of(2030, 1, 1, 12, 0);

    private final MercadoPagoHttpFalso http = new MercadoPagoHttpFalso();

    private GatewayPagamentoMercadoPago gateway(boolean sandbox, String notificationUrl) {
        return new GatewayPagamentoMercadoPago(new PropriedadesMercadoPago("TEST-token", "segredo", notificationUrl, sandbox,
                Duration.ofSeconds(3), null), http);
    }

    private static Pagamento pagamento() {
        return Pagamento.novo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("530"), EXPIRA, LocalDateTime.now());
    }

    private static Pagamento aprovado(String mpPaymentId) {
        Pagamento pagamento = pagamento();
        pagamento.aprovar(mpPaymentId, LocalDateTime.now());
        return pagamento;
    }

    @Test
    void preferenciaLevaReferenciaNotificacaoExpiracaoValorEUmaChaveDeIdempotenciaSo() {
        http.responder(201, """
                {"id":"123-abc","init_point":"https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc",
                 "sandbox_init_point":"https://sandbox.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc"}
                """);
        Pagamento pagamento = pagamento();

        Cobranca cobranca = gateway(false, "https://gw.exemplo/webhooks/mercadopago").criarCobranca(pagamento, "Orcamento ORC-1 - veiculo ABC1D23");

        assertThat(cobranca).isEqualTo(new Cobranca("123-abc", "https://www.mercadopago.com.br/checkout/v1/redirect?pref_id=123-abc"));
        MPRequest enviada = http.recebidas.getFirst();
        assertThat(enviada.getUri()).endsWith("/checkout/preferences");
        assertThat(enviada.getHeaders())
                .containsEntry("x-idempotency-key", pagamento.getIdempotencyKey().toString())
                .doesNotContainKey("X-Idempotency-Key")
                .containsEntry("Authorization", "Bearer TEST-token");
        JsonObject corpo = enviada.getPayload();
        assertThat(corpo.get("external_reference").getAsString()).isEqualTo(pagamento.getId().toString());
        assertThat(corpo.get("notification_url").getAsString()).isEqualTo("https://gw.exemplo/webhooks/mercadopago");
        assertThat(corpo.get("expires").getAsBoolean()).isTrue();
        assertThat(corpo.get("expiration_date_to").getAsString()).startsWith("2030-01-01T12:00:00");
        JsonObject item = corpo.getAsJsonArray("items").get(0).getAsJsonObject();
        assertThat(item.get("unit_price").getAsBigDecimal()).isEqualByComparingTo("530.00");
        assertThat(item.get("currency_id").getAsString()).isEqualTo("BRL");
        assertThat(item.get("title").getAsString()).isEqualTo("Orcamento ORC-1 - veiculo ABC1D23");
    }

    @Test
    void emSandboxOLinkEhODeTesteESemNotificationUrlOCampoNaoVai() {
        http.responder(201, """
                {"id":"p1","init_point":"https://prod/p1","sandbox_init_point":"https://sandbox/p1"}
                """);

        Cobranca cobranca = gateway(true, " ").criarCobranca(pagamento(), "Orcamento");

        assertThat(cobranca.link()).isEqualTo("https://sandbox/p1");
        assertThat(http.recebidas.getFirst().getPayload().has("notification_url")).isFalse();
    }

    @Test
    void preferenciaRecusadaOuSemRedeSobeParaOComandoVoltarAFila() {
        http.responder(400, "{\"message\":\"invalid unit_price\"}");
        assertThatThrownBy(() -> gateway(false, null).criarCobranca(pagamento(), "x"))
                .isInstanceOf(ProvedorDePagamentoIndisponivel.class).hasMessageContaining("400");

        http.limpar();
        http.semRede();
        assertThatThrownBy(() -> gateway(false, null).criarCobranca(pagamento(), "x"))
                .isInstanceOf(ProvedorDePagamentoIndisponivel.class);
    }

    @Test
    void estornoTotalComChaveDerivadaDaCobranca() {
        http.responder(201, "{\"id\":99,\"payment_id\":555,\"amount\":530.00,\"status\":\"approved\"}");
        Pagamento pagamento = aprovado("555");

        Estorno estorno = gateway(false, null).estornar(pagamento);

        assertThat(estorno).isEqualTo(new Estorno(true, "99", null));
        MPRequest enviada = http.recebidas.getFirst();
        assertThat(enviada.getUri()).endsWith("/v1/payments/555/refunds");
        assertThat(enviada.getHeaders().get("x-idempotency-key"))
                .isNotBlank()
                .isNotEqualTo(pagamento.getIdempotencyKey().toString());
    }

    @Test
    void estornoRecusadoEhRespostaETransitorioSobe() {
        http.responder(400, "{\"message\":\"Payment too old to be refunded\"}");
        Estorno recusado = gateway(false, null).estornar(aprovado("555"));
        assertThat(recusado.realizado()).isFalse();
        assertThat(recusado.motivoFalha()).contains("400").contains("too old");

        http.limpar();
        http.responder(500, "{}");
        assertThatThrownBy(() -> gateway(false, null).estornar(aprovado("556"))).isInstanceOf(ProvedorDePagamentoIndisponivel.class);

        http.limpar();
        http.semRede();
        assertThatThrownBy(() -> gateway(false, null).estornar(aprovado("557"))).isInstanceOf(ProvedorDePagamentoIndisponivel.class);

        assertThat(gateway(false, null).estornar(pagamento()).realizado()).as("sem pagamento no provedor").isFalse();
    }

    @Test
    void consultaMapeiaOStatusDoMercadoPago() {
        UUID cobranca = UUID.randomUUID();
        http.responder(200, "{\"id\":555,\"status\":\"approved\",\"status_detail\":\"accredited\",\"external_reference\":\"" + cobranca + "\"}")
                .responder(200, "{\"id\":556,\"status\":\"rejected\",\"status_detail\":\"cc_rejected_other_reason\",\"external_reference\":\"" + cobranca + "\"}")
                .responder(200, "{\"id\":557,\"status\":\"in_process\",\"external_reference\":\"nao-e-uuid\"}")
                .responder(404, "{\"message\":\"Payment not found\"}");
        GatewayPagamentoMercadoPago mp = gateway(false, null);

        assertThat(mp.consultarPagamento("555")).contains(new SituacaoNoProvedor(java.util.Optional.of(cobranca), "555", ResultadoNoProvedor.APROVADO, "accredited"));
        assertThat(mp.consultarPagamento("556").orElseThrow().resultado()).isEqualTo(ResultadoNoProvedor.RECUSADO);
        assertThat(mp.consultarPagamento("557").orElseThrow()).satisfies(s -> {
            assertThat(s.resultado()).isEqualTo(ResultadoNoProvedor.PENDENTE);
            assertThat(s.pagamentoId()).isEmpty();
        });
        assertThat(mp.consultarPagamento("558")).isEmpty();
        assertThat(mp.consultarPagamento("nao-numerico")).isEmpty();
        assertThat(http.recebidas.getFirst().getUri()).endsWith("/v1/payments/555");
    }

    @Test
    void consultaComFalhaDoProvedorSobe() {
        http.responder(503, "{}");
        assertThatThrownBy(() -> gateway(false, null).consultarPagamento("555")).isInstanceOf(ProvedorDePagamentoIndisponivel.class);

        http.limpar();
        http.semRede();
        assertThatThrownBy(() -> gateway(false, null).consultarPagamento("555")).isInstanceOf(ProvedorDePagamentoIndisponivel.class);
    }

    @Test
    void buscaPelaCobrancaPrefereOAprovadoESenaoOMaisRecente() {
        Pagamento pagamento = pagamento();
        String ref = pagamento.getId().toString();
        http.responder(200, "{\"results\":[{\"id\":2,\"status\":\"rejected\",\"external_reference\":\"" + ref + "\"},"
                        + "{\"id\":1,\"status\":\"approved\",\"external_reference\":\"" + ref + "\"}],\"paging\":{\"total\":2}}")
                .responder(200, "{\"results\":[{\"id\":4,\"status\":\"rejected\",\"external_reference\":\"" + ref + "\"},"
                        + "{\"id\":3,\"status\":\"pending\",\"external_reference\":\"" + ref + "\"}],\"paging\":{\"total\":2}}")
                .responder(200, "{\"results\":[],\"paging\":{\"total\":0}}");
        GatewayPagamentoMercadoPago mp = gateway(false, null);

        assertThat(mp.buscarPorCobranca(pagamento).orElseThrow().referenciaPagamento()).isEqualTo("1");
        assertThat(mp.buscarPorCobranca(pagamento).orElseThrow().referenciaPagamento()).isEqualTo("4");
        assertThat(mp.buscarPorCobranca(pagamento)).isEmpty();
        assertThat(http.recebidas.getFirst().getUri()).contains("/v1/payments/search")
                .contains("external_reference=" + ref).contains("sort=date_created").contains("criteria=desc");
    }

    @Test
    void semAccessTokenNaoSobe() {
        assertThatThrownBy(() -> new GatewayPagamentoMercadoPago(
                new PropriedadesMercadoPago(" ", "s", null, true, null, null), http))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MERCADOPAGO_ACCESS_TOKEN");
    }
}
