package br.com.oficina.billing.orcamento.infrastructure.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import br.com.oficina.billing.orcamento.application.port.CatalogoPecas.PecaCatalogo;

/** Cliente do REST interno de pecas contra um servidor HTTP local (docs/contratos/rest-interno.md do OS). */
class CatalogoPecasRestTest {

    private HttpServer servidor;
    private final List<Requisicao> recebidas = new ArrayList<>();
    private int status = 200;
    private String resposta = """
            { "pecas": [ { "pecaInsumoId": "filtro", "descricao": "Filtro de oleo", "preco": "80.00" } ],
              "inexistentes": ["sumiu"] }
            """;

    record Requisicao(String metodo, String caminho, String token, String requestId, String corpo) {
    }

    @BeforeEach
    void subir() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.createContext("/internal/pecas-insumos/consulta", this::responder);
        servidor.start();
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
        MDC.clear();
    }

    private void responder(HttpExchange troca) throws IOException {
        recebidas.add(new Requisicao(troca.getRequestMethod(), troca.getRequestURI().getPath(),
                troca.getRequestHeaders().getFirst("X-Service-Token"), troca.getRequestHeaders().getFirst("x-request-id"),
                new String(troca.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
        byte[] corpo = resposta.getBytes(StandardCharsets.UTF_8);
        troca.getResponseHeaders().add("Content-Type", "application/json");
        troca.sendResponseHeaders(status, corpo.length);
        troca.getResponseBody().write(corpo);
        troca.close();
    }

    private CatalogoPecasRest cliente() {
        return new CatalogoPecasRest("http://localhost:" + servidor.getAddress().getPort(), "segredo-servico", Duration.ofSeconds(2));
    }

    @Test
    void consultaComTokenECorrelacaoEDevolveSoAsEncontradas() {
        MDC.put("correlation_id", "req-42");

        Map<String, PecaCatalogo> pecas = cliente().consultar(List.of("filtro", "sumiu", "filtro"));

        assertThat(pecas).containsOnlyKeys("filtro");
        assertThat(pecas.get("filtro")).isEqualTo(new PecaCatalogo("filtro", "Filtro de oleo", new java.math.BigDecimal("80.00")));
        assertThat(recebidas).singleElement().satisfies(r -> {
            assertThat(r.metodo()).isEqualTo("POST");
            assertThat(r.token()).isEqualTo("segredo-servico");
            assertThat(r.requestId()).isEqualTo("req-42");
            assertThat(r.corpo()).isEqualTo("{\"pecaInsumoIds\":[\"filtro\",\"sumiu\"]}");
        });
    }

    @Test
    void semIdsNaoChamaOServico() {
        assertThat(cliente().consultar(List.of())).isEmpty();
        assertThat(cliente().consultar(null)).isEmpty();
        assertThat(recebidas).isEmpty();
    }

    @Test
    void respostaSemPecasViraMapaVazioESemCorrelacaoNaoMandaOHeader() {
        resposta = "{ \"inexistentes\": [\"sumiu\"] }";

        assertThat(cliente().consultar(List.of("sumiu"))).isEmpty();
        assertThat(recebidas.getFirst().requestId()).isNull();
    }

    @Test
    void erroDoServicoSobeParaOComandoVoltarAFila() {
        status = 401;
        resposta = "{}";

        assertThatThrownBy(() -> cliente().consultar(List.of("filtro"))).isInstanceOf(HttpClientErrorException.Unauthorized.class);
        assertThat(recebidas).as("4xx nao e repetido").hasSize(1);
    }

    @Test
    void falhaDeRedeEhTentadaDeNovoUmaVezEDepoisSobe() {
        servidor.stop(0);

        assertThatThrownBy(() -> cliente().consultar(List.of("filtro"))).isInstanceOf(ResourceAccessException.class);
    }
}
