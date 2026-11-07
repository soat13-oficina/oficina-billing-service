package br.com.oficina.billing.orcamento.application.usecase;

import java.util.List;

import br.com.oficina.billing.orcamento.application.query.ConsultarOrcamentoQuery;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;

public interface ConsultarOrcamentoUseCase {
    List<Orcamento> consultarOrcamento(ConsultarOrcamentoQuery query);
}
