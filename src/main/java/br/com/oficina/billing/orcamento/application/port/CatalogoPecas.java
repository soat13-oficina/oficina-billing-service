package br.com.oficina.billing.orcamento.application.port;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/**
 * Descricao e preco atuais das pecas, que sao da Execution (ADR 0003). O orcamento guarda um
 * snapshot do que consultou (ADR 0002).
 *
 * <p>Adaptador: {@code CatalogoPecasRest}, cliente de {@code POST /internal/pecas-insumos/consulta}
 * (Execution Service).
 */
public interface CatalogoPecas {

    /** Pecas encontradas, por id. Id ausente no mapa = peca inexistente. */
    Map<String, PecaCatalogo> consultar(Collection<String> pecaInsumoIds);

    record PecaCatalogo(String pecaInsumoId, String descricao, BigDecimal preco) {
    }
}
