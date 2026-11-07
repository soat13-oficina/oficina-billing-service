package br.com.oficina.billing.integracao.infrastructure.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Segredo estatico vazio nao abre o webhook: sem ele, so tokens de integracao gerados valem. */
class WebhookTokenFilterTest {

    private static MockHttpServletResponse decidir(WebhookTokenFilter filtro, String token) throws Exception {
        MockHttpServletRequest requisicao = new MockHttpServletRequest("POST", "/integracoes/orcamentos/ORC-1/decisao");
        if (token != null) {
            requisicao.addHeader("X-Webhook-Token", token);
        }
        MockHttpServletResponse resposta = new MockHttpServletResponse();
        filtro.doFilter(requisicao, resposta, new MockFilterChain());
        return resposta;
    }

    @Test
    void semSegredoConfiguradoHeaderVazioNaoPassa() throws Exception {
        WebhookTokenFilter semSegredo = new WebhookTokenFilter("", new TokenIntegracaoService(null));

        assertThat(decidir(semSegredo, "").getStatus()).isEqualTo(401);
        assertThat(decidir(semSegredo, null).getStatus()).isEqualTo(401);
    }

    @Test
    void segredoConfiguradoPassaEOutrasRotasNemSaoOlhadas() throws Exception {
        TokenIntegracaoService semTokensGerados = new TokenIntegracaoService(null) {
            @Override
            public boolean validar(String tokenEmClaro) {
                return false;
            }
        };
        WebhookTokenFilter filtro = new WebhookTokenFilter("segredo", semTokensGerados);

        assertThat(decidir(filtro, "segredo").getStatus()).isEqualTo(200);
        assertThat(decidir(filtro, "outro-segredo").getStatus()).isEqualTo(401);

        MockHttpServletResponse outraRota = new MockHttpServletResponse();
        filtro.doFilter(new MockHttpServletRequest("GET", "/orcamentos"), outraRota, new MockFilterChain());
        assertThat(outraRota.getStatus()).isEqualTo(200);
    }
}
