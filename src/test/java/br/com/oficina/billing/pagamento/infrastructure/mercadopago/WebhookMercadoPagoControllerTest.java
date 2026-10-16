package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.mercadopago.net.MPHttpClient;

import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;
import br.com.oficina.billing.pagamento.infrastructure.persistence.SpringDataPagamentoRepository;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;

/**
 * Webhook do Mercado Pago com o gateway real e o HTTP do SDK trocado por um duble: assinatura
 * {@code x-signature} validada, pagamento consultado na API, resultado publicado para a Saga.
 */
@SpringBootTest(properties = {
        "pagamento.gateway=mercadopago",
        "mercadopago.access-token=TEST-token",
        "mercadopago.webhook-secret=" + WebhookMercadoPagoControllerTest.SEGREDO
})
class WebhookMercadoPagoControllerTest {

    static final String SEGREDO = "segredo-do-webhook";

    @TestConfiguration
    static class HttpDoMercadoPago {
        @Bean
        @Primary
        MercadoPagoHttpFalso mercadoPagoHttpFalso() {
            return new MercadoPagoHttpFalso();
        }
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private MercadoPagoHttpFalso http;

    @Autowired
    private MPHttpClient httpDoGateway;

    @Autowired
    private PagamentoRepository pagamentos;

    @Autowired
    private SpringDataPagamentoRepository springDataPagamentos;

    @Autowired
    private SpringDataOrcamentoRepository orcamentos;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        limpar();
    }

    @AfterEach
    void limpar() {
        springDataPagamentos.deleteAll();
        orcamentos.deleteAll();
        outbox.deleteAll();
        http.limpar();
    }

    private Pagamento cobranca() {
        Orcamento orcamento = orcamentos.save(OrcamentosDeTeste.novo("ORC-" + UUID.randomUUID(), StatusOrcamento.APROVADO));
        return pagamentos.salvar(Pagamento.novo(orcamento.getId(), orcamento.getOrdemDeServicoId(), orcamento.getSagaId(),
                new BigDecimal("530"), LocalDateTime.now().plusHours(24), LocalDateTime.now()));
    }

    /** Assinatura como o Mercado Pago calcula: HMAC-SHA256 hex sobre "id:{data.id};request-id:{x-request-id};ts:{ts};". */
    private static String assinar(String dataId, String requestId, long ts, String segredo) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String manifesto = "id:" + dataId + ";request-id:" + requestId + ";ts:" + ts + ";";
        return "ts=" + ts + ",v1=" + HexFormat.of().formatHex(mac.doFinal(manifesto.getBytes(StandardCharsets.UTF_8)));
    }

    private ResultActions notificar(String tipo, String dataId, String assinatura) throws Exception {
        var requisicao = post("/webhooks/mercadopago").param("type", tipo).param("data.id", dataId)
                .header("x-request-id", "req-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\":\"payment.updated\",\"type\":\"" + tipo + "\",\"data\":{\"id\":\"" + dataId + "\"}}");
        if (assinatura != null) {
            requisicao.header("x-signature", assinatura);
        }
        return mvc.perform(requisicao);
    }

    @Test
    void pagamentoAprovadoNoMercadoPagoConfirmaACobrancaEPublicaParaASaga() throws Exception {
        assertThat(httpDoGateway).as("o gateway usa o duble").isSameAs(http);
        Pagamento pagamento = cobranca();
        http.responder(200, "{\"id\":555,\"status\":\"approved\",\"status_detail\":\"accredited\",\"external_reference\":\""
                + pagamento.getId() + "\"}");

        notificar("payment", "555", assinar("555", "req-1", Instant.now().getEpochSecond(), SEGREDO))
                .andExpect(status().isOk());

        Pagamento depois = pagamentos.buscarPorId(pagamento.getId()).orElseThrow();
        assertThat(depois.getStatus()).isEqualTo(StatusPagamento.APROVADO);
        assertThat(depois.getMpPaymentId()).isEqualTo("555");
        assertThat(http.recebidas).singleElement().satisfies(r -> assertThat(r.getUri()).endsWith("/v1/payments/555"));
        assertThat(outbox.findAll()).singleElement().satisfies(m -> assertThat(m.getTipo()).isEqualTo("PagamentoConfirmado"));
    }

    @Test
    void assinaturaAusenteInvalidaOuAntigaResponde401SemConsultarNada() throws Exception {
        long agora = Instant.now().getEpochSecond();

        notificar("payment", "555", null).andExpect(status().isUnauthorized());
        notificar("payment", "555", assinar("555", "req-1", agora, "outro-segredo")).andExpect(status().isUnauthorized());
        notificar("payment", "555", assinar("999", "req-1", agora, SEGREDO)).andExpect(status().isUnauthorized());
        notificar("payment", "555", assinar("555", "req-1", agora - 3600, SEGREDO)).andExpect(status().isUnauthorized());

        assertThat(http.recebidas).isEmpty();
    }

    @Test
    void notificacaoQueNaoEhDePagamentoEhConfirmadaEIgnorada() throws Exception {
        notificar("merchant_order", "777", assinar("777", "req-1", Instant.now().getEpochSecond(), SEGREDO))
                .andExpect(status().isOk());

        assertThat(http.recebidas).isEmpty();
        assertThat(outbox.findAll()).isEmpty();
    }

    @Test
    void mercadoPagoForaDoArRespondeErroParaEleReenviar() throws Exception {
        http.responder(503, "{}");

        notificar("payment", "555", assinar("555", "req-1", Instant.now().getEpochSecond(), SEGREDO))
                .andExpect(status().is5xxServerError());
    }
}
