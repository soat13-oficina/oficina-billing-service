package br.com.oficina.billing.pagamento.application.saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * Tipos e payloads das mensagens da Saga do lado do pagamento (futuro Billing Service), como em
 * {@code docs/contratos/mensagens.md}. Copia propria do modulo: o que o une ao OS Service e o
 * contrato, nao uma classe compartilhada.
 */
public final class MensagensPagamento {

    // Comandos recebidos do orquestrador
    public static final String SOLICITAR_PAGAMENTO = "SolicitarPagamento";
    public static final String ESTORNAR_PAGAMENTO = "EstornarPagamento";

    // Eventos publicados
    public static final String PAGAMENTO_SOLICITADO = "PagamentoSolicitado";
    public static final String PAGAMENTO_CONFIRMADO = "PagamentoConfirmado";
    public static final String PAGAMENTO_RECUSADO = "PagamentoRecusado";
    public static final String PAGAMENTO_EXPIRADO = "PagamentoExpirado";
    public static final String PAGAMENTO_ESTORNADO = "PagamentoEstornado";
    public static final String ESTORNO_FALHOU = "EstornoFalhou";

    private MensagensPagamento() {
    }

    public record SolicitarPagamento(UUID orcamentoId, Instant expiraEm) {
    }

    public record EstornarPagamento(UUID orcamentoId, UUID pagamentoId, String motivo) {
    }

    public record PagamentoSolicitado(
            UUID pagamentoId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor, String linkPagamento, Instant expiraEm) {
    }

    public record PagamentoConfirmado(
            UUID pagamentoId, String mpPaymentId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor, Instant aprovadoEm) {
    }

    public record PagamentoRecusado(UUID pagamentoId, String mpPaymentId, String motivo) {
    }

    public record PagamentoExpirado(UUID pagamentoId, Instant expiradoEm) {
    }

    public record PagamentoEstornado(
            UUID pagamentoId, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valor, String mpRefundId) {
    }

    public record EstornoFalhou(UUID pagamentoId, String motivo) {
    }
}
