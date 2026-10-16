package br.com.oficina.billing.orcamento.domain.model;

public enum StatusOrcamento {
    AGUARDANDO_APROVACAO,
    APROVADO,
    REJEITADO,
    /** Fase 4: compensacao da Saga (CancelarOrcamento), p.ex. prazo de decisao vencido. */
    CANCELADO
}
