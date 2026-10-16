package br.com.oficina.billing.pagamento.application.saga;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.Filas;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/**
 * Eventos do pagamento para o orquestrador ({@code os-saga-replies}), gravados no outbox na transacao
 * corrente. Respostas a comandos levam {@code causationId}; resultado do pagamento (webhook) e
 * expiracao sao espontaneos.
 */
@Component
public class EventosPagamento {

    private final PublicadorMensagens publicador;
    private final FabricaEnvelopes fabrica;

    public EventosPagamento(PublicadorMensagens publicador, FabricaEnvelopes fabrica) {
        this.publicador = publicador;
        this.fabrica = fabrica;
    }

    public void solicitado(Envelope comando, Pagamento pagamento) {
        responder(comando, MensagensPagamento.PAGAMENTO_SOLICITADO, new MensagensPagamento.PagamentoSolicitado(
                pagamento.getId(), dinheiro(pagamento.getValor()), pagamento.getLinkPagamento(), instante(pagamento.getExpiraEm())));
    }

    public void estornado(Envelope comando, Pagamento pagamento) {
        responder(comando, MensagensPagamento.PAGAMENTO_ESTORNADO, new MensagensPagamento.PagamentoEstornado(
                pagamento.getId(), dinheiro(pagamento.getValor()), pagamento.getMpRefundId()));
    }

    public void estornoFalhou(Envelope comando, UUID pagamentoId, String motivo) {
        responder(comando, MensagensPagamento.ESTORNO_FALHOU, new MensagensPagamento.EstornoFalhou(pagamentoId, motivo));
    }

    public void confirmado(Pagamento pagamento) {
        espontaneo(pagamento, MensagensPagamento.PAGAMENTO_CONFIRMADO, new MensagensPagamento.PagamentoConfirmado(
                pagamento.getId(), pagamento.getMpPaymentId(), dinheiro(pagamento.getValor()), instante(pagamento.getAtualizadoEm())));
    }

    public void recusado(Pagamento pagamento) {
        espontaneo(pagamento, MensagensPagamento.PAGAMENTO_RECUSADO, new MensagensPagamento.PagamentoRecusado(
                pagamento.getId(), pagamento.getMpPaymentId(), pagamento.getMotivo()));
    }

    public void expirado(Pagamento pagamento) {
        espontaneo(pagamento, MensagensPagamento.PAGAMENTO_EXPIRADO, new MensagensPagamento.PagamentoExpirado(
                pagamento.getId(), instante(pagamento.getAtualizadoEm())));
    }

    private void responder(Envelope comando, String tipo, Object payload) {
        publicador.publicar(Filas.OS_SAGA_REPLIES, fabrica.resposta(comando, tipo, payload));
    }

    private void espontaneo(Pagamento pagamento, String tipo, Object payload) {
        publicador.publicar(Filas.OS_SAGA_REPLIES, fabrica.novo(tipo, pagamento.getSagaId(), pagamento.getOrdemDeServicoId(), payload));
    }

    private static Instant instante(LocalDateTime data) {
        return data.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Contrato: dinheiro com duas casas ("530.00"). */
    private static BigDecimal dinheiro(BigDecimal valor) {
        return valor.setScale(2, RoundingMode.HALF_UP);
    }
}
