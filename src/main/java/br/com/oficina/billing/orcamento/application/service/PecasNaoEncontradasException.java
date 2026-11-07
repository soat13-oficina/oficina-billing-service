package br.com.oficina.billing.orcamento.application.service;

import java.util.List;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;

/** Pecas do orcamento que nao existem no catalogo; a Saga as recebe em {@code OrcamentoFalhou}. */
public class PecasNaoEncontradasException extends RecursoNaoEncontradoException {

    private final List<String> pecaInsumoIds;

    public PecasNaoEncontradasException(List<String> pecaInsumoIds) {
        super("Peca/Insumo nao encontrada com o ID: " + String.join(", ", pecaInsumoIds));
        this.pecaInsumoIds = List.copyOf(pecaInsumoIds);
    }

    public List<String> getPecaInsumoIds() {
        return pecaInsumoIds;
    }
}
