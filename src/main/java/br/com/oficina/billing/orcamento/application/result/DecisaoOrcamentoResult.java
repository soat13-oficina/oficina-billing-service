package br.com.oficina.billing.orcamento.application.result;

import java.util.UUID;

import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;

/**
 * Resultado da decisao externa. Fase 4: sem a situacao da OS - quem move a ordem e o orquestrador
 * da Saga, logo depois, ao receber o evento da decisao.
 */
public record DecisaoOrcamentoResult(
        String numeroOrcamento,
        StatusOrcamento statusOrcamento,
        UUID ordemDeServicoId) {
}
