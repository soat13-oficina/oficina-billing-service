package br.com.oficina.billing.support.catalogo;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import br.com.oficina.billing.orcamento.application.port.CatalogoPecas;

/** Cadastro de pecas dos testes, no lugar da consulta REST a Execution. */
public class CatalogoPecasEmMemoria implements CatalogoPecas {

    private final Map<String, PecaCatalogo> pecas = new ConcurrentHashMap<>();

    public CatalogoPecasEmMemoria cadastrar(String id, String descricao, String preco) {
        pecas.put(id, new PecaCatalogo(id, descricao, new BigDecimal(preco)));
        return this;
    }

    public void remover(String id) {
        pecas.remove(id);
    }

    public void limpar() {
        pecas.clear();
    }

    @Override
    public Map<String, PecaCatalogo> consultar(Collection<String> pecaInsumoIds) {
        Map<String, PecaCatalogo> encontradas = new LinkedHashMap<>();
        for (String id : new LinkedHashSet<>(pecaInsumoIds)) {
            PecaCatalogo peca = pecas.get(id);
            if (peca != null) {
                encontradas.put(id, peca);
            }
        }
        return encontradas;
    }
}
