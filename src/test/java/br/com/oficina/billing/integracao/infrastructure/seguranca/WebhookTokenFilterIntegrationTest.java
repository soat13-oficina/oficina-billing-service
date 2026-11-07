package br.com.oficina.billing.integracao.infrastructure.seguranca;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.oficina.billing.integracao.infrastructure.persistence.TokenIntegracaoRepository;

@SpringBootTest
class WebhookTokenFilterIntegrationTest {

    private static final String CAMINHO = "/integracoes/orcamentos/ORC-INEXISTENTE/decisao";
    private static final String CORPO = "{\"decisao\":\"APROVADO\"}";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private TokenIntegracaoRepository repository;

    @Autowired
    private TokenIntegracaoService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void deveAceitarTokenGeradoAtivo() throws Exception {
        String token = service.gerar("ERP X", "tester").tokenEmClaro();

        // Passa pelo filtro (não 401); o orçamento inexistente faz o controller retornar 404.
        mockMvc.perform(post(CAMINHO).with(csrf())
                        .header("X-Webhook-Token", token)
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRecusarTokenInvalido() throws Exception {
        mockMvc.perform(post(CAMINHO).with(csrf())
                        .header("X-Webhook-Token", "oki_invalido")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRecusarTokenRevogado() throws Exception {
        var gerado = service.gerar("ERP X", "tester");
        service.revogar(gerado.token().getId(), "tester");

        mockMvc.perform(post(CAMINHO).with(csrf())
                        .header("X-Webhook-Token", gerado.tokenEmClaro())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveAceitarSegredoEstaticoNaTransicao() throws Exception {
        // Coexistência: o segredo estático do profile de teste continua aceito.
        mockMvc.perform(post(CAMINHO).with(csrf())
                        .header("X-Webhook-Token", "segredo-teste")
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRecusarSemHeader() throws Exception {
        mockMvc.perform(post(CAMINHO).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
    }
}
