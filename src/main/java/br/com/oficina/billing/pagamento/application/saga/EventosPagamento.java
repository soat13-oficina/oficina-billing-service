package br.com.oficina.billing.pagamento.application.saga;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.Filas;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Eventos do pagamento para o orquestrador ({@code os-saga-replies}), gravados no outbox na transacao
 * corrente. Respostas a comandos levam {@code causationId}; resultado do pagamento (webhook) e
 * expiracao sao espontaneos.
 */
@Component
public class EventosPagamento {

    private final PublicadorMensagens publicador;
    private final FabricaEnvelopes fabrica;
    private final MeterRegistry registry;

    @Autowired
    public EventosPagamento(PublicadorMensagens publicador, FabricaEnvelopes fabrica, MeterRegistry registry) {
        this.publicador = publicador;
        this.fabrica = fabrica;
        this.registry = registry;
    }

    /** Sem metricas compartilhadas: para quem monta o publicador na mao (testes). */
    public EventosPagamento(PublicadorMensagens publicador, FabricaEnvelopes fabrica) {
        this(publicador, fabrica, new SimpleMeterRegistry());
    }

    // Metricas de negocio do pagamento (PLANO-FASE-4.md, secao 12): um contador por desfecho, contado
    // quando o evento e gravado - o Billing e a autoridade do pagamento, entao e aqui que o fato nasce.
    private void contar(String nome) {
        registry.counter("oficina.pagamento." + nome).increment();
    }

    public void solicitado(Envelope comando, Pagamento pagamento) {
        contar("criados");
        responder(comando, MensagensPagamento.PAGAMENTO_SOLICITADO, new MensagensPagamento.PagamentoSolicitado(
                pagamento.getId(), dinheiro(pagamento.getValor()), pagamento.getLinkPagamento(), instante(pagamento.getExpiraEm())));
    }

    public void estornado(Envelope comando, Pagamento pagamento) {
        contar("estornados");
        responder(comando, MensagensPagamento.PAGAMENTO_ESTORNADO, new MensagensPagamento.PagamentoEstornado(
                pagamento.getId(), dinheiro(pagamento.getValor()), pagamento.getMpRefundId()));
    }

    public void estornoFalhou(Envelope comando, UUID pagamentoId, String motivo) {
        contar("estornos.falhas");
        responder(comando, MensagensPagamento.ESTORNO_FALHOU, new MensagensPagamento.EstornoFalhou(pagamentoId, motivo));
    }

    public void confirmado(Pagamento pagamento) {
        contar("aprovados");
        espontaneo(pagamento, MensagensPagamento.PAGAMENTO_CONFIRMADO, new MensagensPagamento.PagamentoConfirmado(
                pagamento.getId(), pagamento.getMpPaymentId(), dinheiro(pagamento.getValor()), instante(pagamento.getAtualizadoEm())));
    }

    public void recusado(Pagamento pagamento) {
        contar("recusados");
        espontaneo(pagamento, MensagensPagamento.PAGAMENTO_RECUSADO, new MensagensPagamento.PagamentoRecusado(
                pagamento.getId(), pagamento.getMpPaymentId(), pagamento.getMotivo()));
    }

    public void expirado(Pagamento pagamento) {
        contar("expirados");
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
