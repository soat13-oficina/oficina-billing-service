package br.com.oficina.billing.pagamento.infrastructure.web;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.common.infrastructure.seguranca.TokensDeTeste;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;
import br.com.oficina.billing.pagamento.infrastructure.persistence.SpringDataPagamentoRepository;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;

/**
 * Contrato HTTP da cobranca e da simulacao (gateway simulado, perfil de desenvolvimento). O resultado
 * pelo Mercado Pago real tem teste proprio ({@code WebhookMercadoPagoControllerTest}).
 */
@SpringBootTest
class PagamentoControllerTest {

    private static final String FUNCIONARIO = "Bearer " + TokensDeTeste.funcionarioFase3();
    private static final String CLIENTE = "Bearer " + TokensDeTeste.cliente();

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private PagamentoRepository pagamentos;

    @Autowired
    private SpringDataPagamentoRepository springDataPagamentos;

    @Autowired
    private SpringDataOrcamentoRepository orcamentos;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    private MockMvc mvc;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).apply(springSecurity()).build();
        limpar();
    }

    @AfterEach
    void limpar() {
        springDataPagamentos.deleteAll();
        orcamentos.deleteAll();
        outbox.deleteAll();
    }

    private Pagamento cobranca(UUID clienteId) {
        Orcamento orcamento = orcamentos.save(OrcamentosDeTeste.novo("ORC-" + UUID.randomUUID(), UUID.randomUUID(), clienteId,
                StatusOrcamento.APROVADO));
        Pagamento pagamento = Pagamento.novo(orcamento.getId(), orcamento.getOrdemDeServicoId(), orcamento.getSagaId(),
                new BigDecimal("530"), LocalDateTime.now().plusHours(24), LocalDateTime.now());
        pagamento.registrarCobranca("SIM-PREF-1", "https://pagamento-simulado.oficina.local/checkout/" + pagamento.getId(), LocalDateTime.now());
        return pagamentos.salvar(pagamento);
    }

    private ResultActions simular(UUID pagamentoId, String corpo) throws Exception {
        return mvc.perform(post("/pagamentos/" + pagamentoId + "/simulacao").header("Authorization", FUNCIONARIO)
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    void consultaMostraValorStatusLinkEPrazo() throws Exception {
        Pagamento pagamento = cobranca(OrcamentosDeTeste.CLIENTE_ID);

        mvc.perform(get("/pagamentos/" + pagamento.getId()).header("Authorization", FUNCIONARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentoId").value(pagamento.getId().toString()))
                .andExpect(jsonPath("$.orcamentoId").value(pagamento.getOrcamentoId().toString()))
                .andExpect(jsonPath("$.valor").value(530))
                .andExpect(jsonPath("$.status").value("PENDENTE"))
                .andExpect(jsonPath("$.linkPagamento").value(pagamento.getLinkPagamento()))
                .andExpect(jsonPath("$.expiraEm").exists())
                .andExpect(jsonPath("$.referenciaPagamento").doesNotExist());
        String numeroOrcamento = orcamentos.findById(pagamento.getOrcamentoId()).orElseThrow().getNumeroOrcamento();
        mvc.perform(get("/orcamentos/" + numeroOrcamento + "/pagamento").header("Authorization", FUNCIONARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagamentoId").value(pagamento.getId().toString()));
    }

    @Test
    void clienteSoVeACobrancaDoProprioOrcamento() throws Exception {
        Pagamento minha = cobranca(OrcamentosDeTeste.CLIENTE_ID);
        Pagamento deOutro = cobranca(UUID.randomUUID());
        String orcamentoDeOutro = orcamentos.findById(deOutro.getOrcamentoId()).orElseThrow().getNumeroOrcamento();

        mvc.perform(get("/pagamentos/" + minha.getId()).header("Authorization", CLIENTE)).andExpect(status().isOk());
        mvc.perform(get("/pagamentos/" + deOutro.getId()).header("Authorization", CLIENTE)).andExpect(status().isNotFound());
        mvc.perform(get("/orcamentos/" + orcamentoDeOutro + "/pagamento").header("Authorization", CLIENTE))
                .andExpect(status().isNotFound());
    }

    @Test
    void simulacaoAprovaPublicaParaASagaERepetirOMesmoResultadoNaoEhErro() throws Exception {
        Pagamento pagamento = cobranca(OrcamentosDeTeste.CLIENTE_ID);

        simular(pagamento.getId(), "{ \"resultado\": \"APROVADO\" }")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"))
                .andExpect(jsonPath("$.referenciaPagamento").value(Matchers.startsWith("SIM-PAY-")));
        simular(pagamento.getId(), "{ \"resultado\": \"APROVADO\" }").andExpect(status().isOk());
        simular(pagamento.getId(), "{ \"resultado\": \"RECUSADO\", \"motivo\": \"tarde demais\" }")
                .andExpect(status().isConflict());

        org.assertj.core.api.Assertions.assertThat(outbox.findAll()).singleElement()
                .satisfies(m -> org.assertj.core.api.Assertions.assertThat(m.getTipo()).isEqualTo("PagamentoConfirmado"));
    }

    @Test
    void simulacaoSemResultadoOuComResultadoInvalidoResponde400() throws Exception {
        Pagamento pagamento = cobranca(OrcamentosDeTeste.CLIENTE_ID);

        simular(pagamento.getId(), "{ }").andExpect(status().isBadRequest());
        simular(pagamento.getId(), "{ \"resultado\": \"TALVEZ\" }").andExpect(status().isBadRequest());
    }

    @Test
    void pagamentoOuOrcamentoInexistenteResponde404ESemTokenResponde401() throws Exception {
        mvc.perform(get("/pagamentos/" + UUID.randomUUID()).header("Authorization", FUNCIONARIO)).andExpect(status().isNotFound());
        mvc.perform(get("/orcamentos/ORC-NAO-EXISTE/pagamento").header("Authorization", FUNCIONARIO)).andExpect(status().isNotFound());
        simular(UUID.randomUUID(), "{ \"resultado\": \"APROVADO\" }").andExpect(status().isNotFound());
        mvc.perform(get("/pagamentos/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }
}
