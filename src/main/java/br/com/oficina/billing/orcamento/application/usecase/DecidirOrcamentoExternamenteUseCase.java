package br.com.oficina.billing.orcamento.application.usecase;

import br.com.oficina.billing.orcamento.application.command.DecidirOrcamentoExternamenteCommand;
import br.com.oficina.billing.orcamento.application.result.DecisaoOrcamentoResult;

public interface DecidirOrcamentoExternamenteUseCase {
    DecisaoOrcamentoResult decidir(DecidirOrcamentoExternamenteCommand command);
}
