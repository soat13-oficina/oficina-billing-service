package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentRefundClient;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPSearchRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentRefund;
import com.mercadopago.resources.preference.Preference;

import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/**
 * Mercado Pago Checkout Pro com o SDK oficial 3.7.0 (ADR 0007):
 * <ul>
 *   <li>cobranca = preferencia com {@code external_reference} = id da cobranca, {@code notification_url}
 *       e expiracao igual ao prazo da Saga; o cliente paga pelo link ({@code init_point});</li>
 *   <li>resultado = consulta {@code GET /v1/payments/{id}} (webhook) ou busca por {@code external_reference}
 *       (antes de expirar);</li>
 *   <li>estorno total = {@code POST /v1/payments/{id}/refunds}.</li>
 * </ul>
 * Toda criacao leva {@code X-Idempotency-Key}: a da cobranca na preferencia e uma derivada dela no
 * estorno. Recusa de negocio (4xx) no estorno vira {@link Estorno#realizado()} falso; falha transitoria
 * sobe como {@link ProvedorDePagamentoIndisponivel}.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "mercadopago", matchIfMissing = true)
public class GatewayPagamentoMercadoPago implements GatewayPagamento {
    private static final Logger log = LoggerFactory.getLogger(GatewayPagamentoMercadoPago.class);
    private static final int LIMITE_MOTIVO = 250;

    private final PropriedadesMercadoPago propriedades;
    private final PreferenceClient preferencias;
    private final PaymentClient pagamentos;
    private final PaymentRefundClient estornos;

    public GatewayPagamentoMercadoPago(PropriedadesMercadoPago propriedades, MPHttpClient http) {
        if (propriedades.accessToken() == null || propriedades.accessToken().isBlank()) {
            throw new IllegalStateException("mercadopago.access-token vazio: configure MERCADOPAGO_ACCESS_TOKEN "
                    + "ou use pagamento.gateway=simulado no desenvolvimento local");
        }
        MPHttpClient semChaveDuplicada = new ChaveDeIdempotenciaUnica(http);
        this.propriedades = propriedades;
        this.preferencias = new PreferenceClient(semChaveDuplicada);
        this.pagamentos = new PaymentClient(semChaveDuplicada);
        this.estornos = new PaymentRefundClient(semChaveDuplicada);
    }

    @Override
    public Cobranca criarCobranca(Pagamento pagamento, String descricao) {
        PreferenceRequest.PreferenceRequestBuilder pedido = PreferenceRequest.builder()
                .items(List.of(PreferenceItemRequest.builder()
                        .id(pagamento.getOrcamentoId().toString())
                        .title(descricao)
                        .quantity(1)
                        .currencyId("BRL")
                        .unitPrice(pagamento.getValor().setScale(2, RoundingMode.HALF_UP))
                        .build()))
                .externalReference(pagamento.getId().toString())
                .expires(true)
                .expirationDateTo(pagamento.getExpiraEm().atZone(ZoneId.systemDefault()).toOffsetDateTime());
        if (propriedades.notificationUrl() != null && !propriedades.notificationUrl().isBlank()) {
            pedido.notificationUrl(propriedades.notificationUrl());
        }
        try {
            Preference preferencia = preferencias.create(pedido.build(), opcoes(pagamento.getIdempotencyKey()));
            String link = propriedades.sandbox() ? preferencia.getSandboxInitPoint() : preferencia.getInitPoint();
            log.info("Preferencia criada no Mercado Pago. pagamentoId={}, preferenceId={}", pagamento.getId(), preferencia.getId());
            return new Cobranca(preferencia.getId(), link);
        } catch (MPApiException e) {
            throw new ProvedorDePagamentoIndisponivel("Mercado Pago recusou a preferencia (HTTP " + e.getStatusCode() + "): "
                    + conteudo(e), e);
        } catch (MPException e) {
            throw new ProvedorDePagamentoIndisponivel("Falha ao criar a preferencia no Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Override
    public Estorno estornar(Pagamento pagamento) {
        if (pagamento.getMpPaymentId() == null) {
            return new Estorno(false, null, "Pagamento sem referencia no Mercado Pago");
        }
        UUID chave = UUID.nameUUIDFromBytes(("estorno:" + pagamento.getIdempotencyKey()).getBytes(StandardCharsets.UTF_8));
        try {
            PaymentRefund estorno = estornos.refund(Long.valueOf(pagamento.getMpPaymentId()), opcoes(chave));
            return new Estorno(true, String.valueOf(estorno.getId()), null);
        } catch (MPApiException e) {
            if (transitorio(e)) {
                throw new ProvedorDePagamentoIndisponivel("Mercado Pago indisponivel no estorno (HTTP " + e.getStatusCode() + ")", e);
            }
            log.warn("Estorno recusado pelo Mercado Pago. pagamentoId={}, http={}", pagamento.getId(), e.getStatusCode());
            return new Estorno(false, null, limitar("Estorno recusado pelo Mercado Pago (HTTP " + e.getStatusCode() + "): " + conteudo(e)));
        } catch (MPException e) {
            throw new ProvedorDePagamentoIndisponivel("Falha ao estornar no Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SituacaoNoProvedor> consultarPagamento(String referenciaPagamento) {
        Long id;
        try {
            id = Long.valueOf(referenciaPagamento);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        try {
            return Optional.of(situacao(pagamentos.get(id, opcoes(null))));
        } catch (MPApiException e) {
            if (e.getStatusCode() == 404) {
                return Optional.empty();
            }
            throw new ProvedorDePagamentoIndisponivel("Falha ao consultar o pagamento no Mercado Pago (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new ProvedorDePagamentoIndisponivel("Falha ao consultar o pagamento no Mercado Pago: " + e.getMessage(), e);
        }
    }

    @Override
    public Optional<SituacaoNoProvedor> buscarPorCobranca(Pagamento pagamento) {
        MPSearchRequest busca = MPSearchRequest.builder()
                .filters(Map.of(
                        "external_reference", pagamento.getId().toString(),
                        "sort", "date_created",
                        "criteria", "desc"))
                .limit(30)
                .offset(0)
                .build();
        try {
            List<Payment> encontrados = pagamentos.search(busca, opcoes(null)).getResults();
            if (encontrados == null || encontrados.isEmpty()) {
                return Optional.empty();
            }
            // Um aprovado vence as tentativas recusadas; senao vale a mais recente.
            Payment escolhido = encontrados.stream()
                    .filter(p -> "approved".equals(p.getStatus()))
                    .findFirst()
                    .orElse(encontrados.getFirst());
            return Optional.of(situacao(escolhido));
        } catch (MPApiException e) {
            throw new ProvedorDePagamentoIndisponivel("Falha ao buscar pagamentos no Mercado Pago (HTTP " + e.getStatusCode() + ")", e);
        } catch (MPException e) {
            throw new ProvedorDePagamentoIndisponivel("Falha ao buscar pagamentos no Mercado Pago: " + e.getMessage(), e);
        }
    }

    /** Status do Mercado Pago: approved decide; rejected/cancelled recusam; o resto ainda nao decide. */
    static SituacaoNoProvedor situacao(Payment pagamento) {
        ResultadoNoProvedor resultado = switch (pagamento.getStatus() == null ? "" : pagamento.getStatus()) {
            case "approved" -> ResultadoNoProvedor.APROVADO;
            case "rejected", "cancelled" -> ResultadoNoProvedor.RECUSADO;
            default -> ResultadoNoProvedor.PENDENTE;
        };
        return new SituacaoNoProvedor(paraUuid(pagamento.getExternalReference()), String.valueOf(pagamento.getId()),
                resultado, pagamento.getStatusDetail());
    }

    private static Optional<UUID> paraUuid(String referenciaExterna) {
        try {
            return referenciaExterna == null ? Optional.empty() : Optional.of(UUID.fromString(referenciaExterna));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private MPRequestOptions opcoes(UUID chaveIdempotencia) {
        int timeoutMs = (int) propriedades.timeout().toMillis();
        MPRequestOptions.MPRequestOptionsBuilder opcoes = MPRequestOptions.builder()
                .accessToken(propriedades.accessToken())
                .connectionTimeout(timeoutMs)
                .connectionRequestTimeout(timeoutMs)
                .socketTimeout(timeoutMs);
        if (chaveIdempotencia != null) {
            // Em minusculas: e o que faz o SDK nao gerar a propria (ver ChaveDeIdempotenciaUnica).
            opcoes.customHeaders(Map.of(ChaveDeIdempotenciaUnica.NOSSA, chaveIdempotencia.toString()));
        }
        return opcoes.build();
    }

    private static boolean transitorio(MPApiException e) {
        return e.getStatusCode() >= 500 || e.getStatusCode() == 429;
    }

    private static String conteudo(MPApiException e) {
        return e.getApiResponse() == null || e.getApiResponse().getContent() == null ? "" : limitar(e.getApiResponse().getContent());
    }

    private static String limitar(String texto) {
        return texto.length() <= LIMITE_MOTIVO ? texto : texto.substring(0, LIMITE_MOTIVO);
    }
}
