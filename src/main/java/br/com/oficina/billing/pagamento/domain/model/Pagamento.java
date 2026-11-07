package br.com.oficina.billing.pagamento.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.oficina.billing.common.domain.exception.ConflitoDeRecursoException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Cobranca de um orcamento aprovado (ADR 0007). Futuro modulo do Billing Service. As colunas
 * {@code mp*} guardam as referencias do Mercado Pago (Passo 3); com o gateway simulado guardam as
 * referencias do simulador.
 *
 * <p>Transicoes repetidas para o mesmo estado nao sao erro (webhook e comando chegam mais de uma
 * vez); transicao para estado diferente de um ja final e conflito.
 */
@Entity
@Table(name = "pagamentos")
public class Pagamento {

    @Id
    private UUID id;

    @Column(name = "orcamento_id", nullable = false)
    private UUID orcamentoId;

    @Column(name = "ordem_de_servico_id", nullable = false)
    private UUID ordemDeServicoId;

    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusPagamento status;

    @Column(length = 500)
    private String motivo;

    @Column(name = "mp_preference_id", length = 80)
    private String mpPreferenceId;

    @Column(name = "mp_payment_id", length = 40, unique = true)
    private String mpPaymentId;

    @Column(name = "mp_refund_id", length = 40)
    private String mpRefundId;

    @Column(name = "link_pagamento", length = 1000)
    private String linkPagamento;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    // X-Idempotency-Key das chamadas ao Mercado Pago: a mesma em todas as tentativas desta cobranca.
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private UUID idempotencyKey;

    @Version
    private long versao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    protected Pagamento() {
    }

    public static Pagamento novo(
            UUID orcamentoId, UUID ordemDeServicoId, UUID sagaId, BigDecimal valor, LocalDateTime expiraEm, LocalDateTime agora) {
        Pagamento pagamento = new Pagamento();
        pagamento.id = UUID.randomUUID();
        pagamento.orcamentoId = orcamentoId;
        pagamento.ordemDeServicoId = ordemDeServicoId;
        pagamento.sagaId = sagaId;
        pagamento.valor = valor;
        pagamento.status = StatusPagamento.PENDENTE;
        pagamento.expiraEm = expiraEm;
        pagamento.idempotencyKey = UUID.randomUUID();
        pagamento.criadoEm = agora;
        pagamento.atualizadoEm = agora;
        return pagamento;
    }

    /** Cobranca criada no gateway (preferencia do Checkout Pro). */
    public void registrarCobranca(String referenciaExterna, String link, LocalDateTime agora) {
        this.mpPreferenceId = referenciaExterna;
        this.linkPagamento = link;
        this.atualizadoEm = agora;
    }

    /** @return {@code true} se aprovou agora; {@code false} se ja estava aprovado */
    public boolean aprovar(String referenciaPagamento, LocalDateTime agora) {
        if (status == StatusPagamento.APROVADO) {
            return false;
        }
        exigirPendente(StatusPagamento.APROVADO);
        this.status = StatusPagamento.APROVADO;
        this.mpPaymentId = referenciaPagamento;
        this.atualizadoEm = agora;
        return true;
    }

    /** @return {@code true} se recusou agora; {@code false} se ja estava recusado */
    public boolean recusar(String referenciaPagamento, String motivoRecusa, LocalDateTime agora) {
        if (status == StatusPagamento.RECUSADO) {
            return false;
        }
        exigirPendente(StatusPagamento.RECUSADO);
        this.status = StatusPagamento.RECUSADO;
        this.mpPaymentId = referenciaPagamento;
        this.motivo = motivoRecusa;
        this.atualizadoEm = agora;
        return true;
    }

    /** Prazo vencido sem pagamento. So pendente expira; os demais estados ficam como estao. */
    public boolean expirar(LocalDateTime agora) {
        if (status != StatusPagamento.PENDENTE) {
            return false;
        }
        this.status = StatusPagamento.EXPIRADO;
        this.motivo = "Prazo de pagamento vencido em " + expiraEm;
        this.atualizadoEm = agora;
        return true;
    }

    /** Orcamento cancelado pela Saga: cobranca pendente deixa de valer. */
    public boolean cancelar(LocalDateTime agora) {
        if (status != StatusPagamento.PENDENTE) {
            return false;
        }
        this.status = StatusPagamento.CANCELADO;
        this.motivo = "Orcamento cancelado pela Saga";
        this.atualizadoEm = agora;
        return true;
    }

    /** @return {@code true} se estornou agora; {@code false} se ja estava estornado */
    public boolean estornar(String referenciaEstorno, String motivoEstorno, LocalDateTime agora) {
        if (status == StatusPagamento.ESTORNADO) {
            return false;
        }
        if (status != StatusPagamento.APROVADO) {
            throw new ConflitoDeRecursoException("So pagamento aprovado pode ser estornado; pagamento em " + status + ".");
        }
        this.status = StatusPagamento.ESTORNADO;
        this.mpRefundId = referenciaEstorno;
        this.motivo = motivoEstorno;
        this.atualizadoEm = agora;
        return true;
    }

    public boolean vencido(LocalDateTime agora) {
        return status == StatusPagamento.PENDENTE && expiraEm.isBefore(agora);
    }

    private void exigirPendente(StatusPagamento destino) {
        if (status != StatusPagamento.PENDENTE) {
            throw new ConflitoDeRecursoException("Pagamento ja esta " + status + "; nao pode ir para " + destino + ".");
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getOrcamentoId() {
        return orcamentoId;
    }

    public UUID getOrdemDeServicoId() {
        return ordemDeServicoId;
    }

    public UUID getSagaId() {
        return sagaId;
    }

    public BigDecimal getValor() {
        return valor;
    }

    public StatusPagamento getStatus() {
        return status;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getMpPreferenceId() {
        return mpPreferenceId;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public String getMpRefundId() {
        return mpRefundId;
    }

    public String getLinkPagamento() {
        return linkPagamento;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public UUID getIdempotencyKey() {
        return idempotencyKey;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
