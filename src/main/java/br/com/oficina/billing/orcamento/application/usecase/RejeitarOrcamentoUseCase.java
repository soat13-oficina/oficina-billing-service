package br.com.oficina.billing.orcamento.application.usecase;

import br.com.oficina.billing.orcamento.application.command.RejeitarOrcamentoCommand;

public interface RejeitarOrcamentoUseCase {
    void rejeitarOrcamento(RejeitarOrcamentoCommand command);
}
