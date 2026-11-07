package br.com.oficina.billing.orcamento.infrastructure.catalogo;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import br.com.oficina.billing.orcamento.application.port.CatalogoPecas;

/**
 * Cadastro de pecas por REST interno ({@code POST /internal/pecas-insumos/consulta}, contrato em
 * {@code docs/contratos/rest-interno.md} do OS Service). No Passo 3 quem responde e o OS Service;
 * no Passo 4, a Execution - so muda {@code catalogo-pecas.url}.
 *
 * <p>Timeout curto e uma nova tentativa so em falha de rede (a consulta e idempotente). Erro que
 * sobra sobe: o comando {@code GerarOrcamento} volta para a fila e e tentado de novo.
 */
@Component
public class CatalogoPecasRest implements CatalogoPecas {
    private static final Logger log = LoggerFactory.getLogger(CatalogoPecasRest.class);
    static final String HEADER_TOKEN = "X-Service-Token";
    static final String HEADER_REQUEST_ID = "x-request-id";
    private static final String MDC_CORRELATION_ID = "correlation_id";

    private final RestClient http;

    public CatalogoPecasRest(
            @Value("${catalogo-pecas.url}") String url,
            @Value("${seguranca.service-token:}") String serviceToken,
            @Value("${catalogo-pecas.timeout:PT3S}") Duration timeout) {
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(timeout);
        fabrica.setReadTimeout(timeout);
        this.http = RestClient.builder()
                .baseUrl(url)
                .requestFactory(fabrica)
                .defaultHeader(HEADER_TOKEN, serviceToken)
                .build();
    }

    record Requisicao(List<String> pecaInsumoIds) {
    }

    record Resposta(List<Peca> pecas, List<String> inexistentes) {
    }

    record Peca(String pecaInsumoId, String descricao, BigDecimal preco) {
    }

    @Override
    public Map<String, PecaCatalogo> consultar(Collection<String> pecaInsumoIds) {
        Map<String, PecaCatalogo> encontradas = new LinkedHashMap<>();
        if (pecaInsumoIds == null || pecaInsumoIds.isEmpty()) {
            return encontradas;
        }
        Requisicao requisicao = new Requisicao(List.copyOf(new LinkedHashSet<>(pecaInsumoIds)));
        Resposta resposta;
        try {
            resposta = chamar(requisicao);
        } catch (ResourceAccessException e) {
            log.warn("Consulta de pecas falhou por rede; nova tentativa. erro={}", e.getMessage());
            resposta = chamar(requisicao);
        }
        if (resposta != null && resposta.pecas() != null) {
            resposta.pecas().forEach(p -> encontradas.put(p.pecaInsumoId(),
                    new PecaCatalogo(p.pecaInsumoId(), p.descricao(), p.preco())));
        }
        return encontradas;
    }

    private Resposta chamar(Requisicao requisicao) {
        String correlacao = MDC.get(MDC_CORRELATION_ID);
        return http.post()
                .uri("/internal/pecas-insumos/consulta")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> {
                    if (correlacao != null) {
                        h.set(HEADER_REQUEST_ID, correlacao);
                    }
                })
                .body(requisicao)
                .retrieve()
                .body(Resposta.class);
    }
}
