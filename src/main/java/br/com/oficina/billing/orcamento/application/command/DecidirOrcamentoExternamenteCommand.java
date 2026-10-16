package br.com.oficina.billing.orcamento.application.command;

import br.com.oficina.billing.orcamento.domain.model.DecisaoOrcamento;

public record DecidirOrcamentoExternamenteCommand(String numeroOrcamento, DecisaoOrcamento decisao) {
}
