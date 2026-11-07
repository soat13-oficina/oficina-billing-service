package br.com.oficina.billing.orcamento.application.usecase;

import br.com.oficina.billing.orcamento.application.command.AprovarOrcamentoCommand;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;

public interface AprovarOrcamentoUseCase {
    Orcamento aprovarOrcamento(AprovarOrcamentoCommand command);
}
