package br.com.oficina.billing.orcamento.infrastructure.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;

@SpringBootTest
class WebhookDecisaoOrcamentoControllerTest {

    private static final String TOKEN_VALIDO = "segredo-teste";
    private static final String HEADER_TOKEN = "X-Webhook-Token";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SpringDataOrcamentoRepository springDataOrcamentoRepository;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        limparTabelas();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    // Sem @Transactional: as requisicoes percorrem o filtro de seguranca e o webhook de verdade.
    @AfterEach
    void tearDown() {
        limparTabelas();
    }

    private void limparTabelas() {
        springDataOrcamentoRepository.deleteAll();
        outbox.deleteAll();
    }

    @Test
    void deveAprovarOrcamentoViaWebhookComTokenValido() throws Exception {
        UUID ordem = UUID.randomUUID();
        criarOrcamento("ORC-WHK-001", ordem, StatusOrcamento.AGUARDANDO_APROVACAO);

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-001/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "APROVADO" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOrcamento").value("ORC-WHK-001"))
                .andExpect(jsonPath("$.statusOrcamento").value("APROVADO"))
                .andExpect(jsonPath("$.ordemServicoId").value(ordem.toString()));
    }

    @Test
    void deveRejeitarOrcamentoViaWebhookComTokenValido() throws Exception {
        UUID ordem = UUID.randomUUID();
        criarOrcamento("ORC-WHK-002", ordem, StatusOrcamento.AGUARDANDO_APROVACAO);

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-002/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "REJEITADO" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statusOrcamento").value("REJEITADO"));
    }

    @Test
    void deveRetornar401ComTokenInvalido() throws Exception {
        mockMvc.perform(post("/integracoes/orcamentos/ORC-001/decisao")
                        .header(HEADER_TOKEN, "token-errado")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "APROVADO" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar401SemToken() throws Exception {
        mockMvc.perform(post("/integracoes/orcamentos/ORC-001/decisao")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "APROVADO" }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deveRetornar404ParaOrcamentoInexistente() throws Exception {
        mockMvc.perform(post("/integracoes/orcamentos/ORC-INEXISTENTE/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "APROVADO" }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deveRetornar409ComDecisaoDivergenteParaOrcamentoJaDecidido() throws Exception {
        UUID ordem = UUID.randomUUID();
        criarOrcamento("ORC-WHK-003", ordem, StatusOrcamento.AGUARDANDO_APROVACAO);

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-003/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "APROVADO" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-003/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "decisao": "REJEITADO" }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Decisao divergente da decisao ja registrada para o orcamento."));
    }

    @Test
    void deveRetornarMesmaDecisaoEmRetryIdenticoDeAprovacao() throws Exception {
        UUID ordem = UUID.randomUUID();
        criarOrcamento("ORC-WHK-004", ordem, StatusOrcamento.AGUARDANDO_APROVACAO);
        String payload = """
                { "decisao": "APROVADO" }
                """;

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-004/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/integracoes/orcamentos/ORC-WHK-004/decisao")
                        .header(HEADER_TOKEN, TOKEN_VALIDO)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.numeroOrcamento").value("ORC-WHK-004"));
    }

    private void criarOrcamento(String numero, UUID ordem, StatusOrcamento status) {
        springDataOrcamentoRepository.save(OrcamentosDeTeste.novo(numero, ordem, OrcamentosDeTeste.CLIENTE_ID, status));
    }
}
