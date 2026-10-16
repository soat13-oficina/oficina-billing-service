package br.com.oficina.billing.orcamento.application.usecase;

import br.com.oficina.billing.orcamento.application.command.AlterarOrcamentoCommand;

public interface AlterarOrcamentoUseCase {
    void alterarOrcamento(AlterarOrcamentoCommand command);
}
