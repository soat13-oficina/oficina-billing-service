package br.com.oficina.billing.pagamento.domain.model;

/** Ciclo de vida de uma cobranca (ADR 0007). */
public enum StatusPagamento {
    /** Cobranca criada, aguardando o cliente pagar. */
    PENDENTE,
    APROVADO,
    RECUSADO,
    /** Prazo vencido sem pagamento - o Billing e a autoridade (ADR 0004, timeouts). */
    EXPIRADO,
    /** Pagamento devolvido ao cliente (compensacao EstornarPagamento). */
    ESTORNADO,
    /** Orcamento cancelado pela Saga com a cobranca ainda pendente. */
    CANCELADO
}
