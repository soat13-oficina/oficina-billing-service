package br.com.oficina.billing.orcamento.application.usecase;

import java.util.UUID;

import br.com.oficina.billing.orcamento.application.command.CadastrarNovoOrcamentoCommand;

public interface CadastrarNovoOrcamentoUseCase {

    /** @return id do orcamento criado */
    UUID cadastrarNovoOrcamento(CadastrarNovoOrcamentoCommand command);
}
