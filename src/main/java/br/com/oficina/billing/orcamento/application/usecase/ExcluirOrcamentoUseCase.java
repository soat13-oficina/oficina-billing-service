package br.com.oficina.billing.orcamento.application.usecase;

import br.com.oficina.billing.orcamento.application.command.ExcluirOrcamentoCommand;

public interface ExcluirOrcamentoUseCase {
    void excluirOrcamento(ExcluirOrcamentoCommand command);
}
