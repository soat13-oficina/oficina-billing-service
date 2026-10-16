package br.com.oficina.billing.orcamento.domain.model;

/** Por onde veio a decisao do orcamento - vai no evento OrcamentoAprovado/OrcamentoRejeitado. */
public enum CanalDecisao {
    /** Cliente autenticado pelo CPF (token da Lambda). */
    CLIENTE,
    /** Funcionario da oficina, em nome do cliente. */
    FUNCIONARIO,
    /** Sistema externo pelo webhook de decisao (X-Webhook-Token). */
    INTEGRACAO
}
