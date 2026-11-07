package br.com.oficina.billing.orcamento.infrastructure.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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

import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.common.infrastructure.seguranca.TokensDeTeste;
import br.com.oficina.billing.orcamento.application.command.CadastrarNovoOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.application.usecase.CadastrarNovoOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.support.catalogo.CatalogoPecasEmMemoria;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;

/** API de orcamentos com os tokens reais: funcionario (OS Service) e cliente (Lambda). */
@SpringBootTest
class OrcamentoControllerTest {

    private static final String FUNCIONARIO = "Bearer " + TokensDeTeste.funcionarioFase3();
    private static final String CLIENTE = "Bearer " + TokensDeTeste.cliente();

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private SpringDataOrcamentoRepository orcamentoRepository;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    @Autowired
    private CatalogoPecasEmMemoria catalogo;

    @Autowired
    private CadastrarNovoOrcamentoUseCase cadastrarNovoOrcamentoUseCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        limpar();
        catalogo.cadastrar("pastilha-001", "Pastilha dianteira", "250.00")
                .cadastrar("fluido-001", "Fluido de freio", "100.00");
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void limpar() {
        orcamentoRepository.deleteAll();
        outbox.deleteAll();
        catalogo.limpar();
    }

    @Test
    void funcionarioConsultaAlteraFiltraEExclui() throws Exception {
        cadastrarNovoOrcamentoUseCase.cadastrarNovoOrcamento(new CadastrarNovoOrcamentoCommand(
                "orc-1",
                new CadastrarNovoOrcamentoCommand.ClienteOrcamento(OrcamentosDeTeste.CLIENTE_ID, "Joao Silva", "12345678909"),
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de pastilhas",
                List.of("Troca de pastilhas"),
                List.of(new PecaOrcamentoInput("pastilha-001", 1)),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 12, 31, 10, 0),
                "Prioridade alta",
                null));

        mockMvc.perform(get("/orcamentos/orc-1").header("Authorization", FUNCIONARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.numeroOrcamento").value("orc-1"))
                .andExpect(jsonPath("$.cliente.nome").value("Joao Silva"))
                .andExpect(jsonPath("$.cliente.cpf").value("12345678909"))
                .andExpect(jsonPath("$.veiculo.placa").value("ABC1D23"))
                .andExpect(jsonPath("$.detalhesServico.valorTotal").value(400.0))
                .andExpect(jsonPath("$.status").value("AGUARDANDO_APROVACAO"));

        String alteracao = """
                {
                  "clienteId": "%s",
                  "ordemDeServicoId": "00000000-0000-0000-0000-000000000001",
                  "funcionarioId": "00000000-0000-0000-0000-000000000003",
                  "placaVeiculo": "ABC1D23",
                  "marcaVeiculo": "Toyota",
                  "modeloVeiculo": "Corolla",
                  "descricaoDiagnostico": "Revisao de freios",
                  "servicosPropostos": ["Revisao freios"],
                  "pecasPrevistas": [{"pecaInsumoId": "fluido-001", "quantidade": 1}],
                  "valorMaoDeObra": 200.00,
                  "desconto": 0.00,
                  "validade": "2031-01-15T10:00:00",
                  "observacoes": "Aprovacao imediata"
                }
                """.formatted(OrcamentosDeTeste.CLIENTE_ID);
        mockMvc.perform(put("/orcamentos/orc-1").header("Authorization", FUNCIONARIO)
                        .contentType(MediaType.APPLICATION_JSON).content(alteracao))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/orcamentos/orc-1").header("Authorization", FUNCIONARIO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.detalhesServico.valorTotal").value(300.0))
                .andExpect(jsonPath("$.detalhesServico.descricaoDiagnostico").value("Revisao de freios"));
        for (Map.Entry<String, String> filtro : Map.of(
                "cpfCliente", "12345678909", "placaVeiculo", "ABC1D23", "numeroOrcamento", "orc-1").entrySet()) {
            mockMvc.perform(get("/orcamentos").param(filtro.getKey(), filtro.getValue()).header("Authorization", FUNCIONARIO))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].numeroOrcamento").value("orc-1"));
        }

        mockMvc.perform(delete("/orcamentos/orc-1").header("Authorization", FUNCIONARIO))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/orcamentos/orc-1").header("Authorization", FUNCIONARIO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Orcamento nao encontrado para o numero informado."));
    }

    @Test
    void clienteSoEnxergaEDecideOProprioOrcamento() throws Exception {
        orcamentoRepository.save(OrcamentosDeTeste.novo("orc-meu", UUID.randomUUID(), OrcamentosDeTeste.CLIENTE_ID,
                StatusOrcamento.AGUARDANDO_APROVACAO));
        orcamentoRepository.save(OrcamentosDeTeste.novo("orc-outro", UUID.randomUUID(), UUID.randomUUID(),
                StatusOrcamento.AGUARDANDO_APROVACAO));

        mockMvc.perform(get("/orcamentos/orc-meu").header("Authorization", CLIENTE)).andExpect(status().isOk());
        mockMvc.perform(get("/orcamentos/orc-outro").header("Authorization", CLIENTE)).andExpect(status().isNotFound());
        mockMvc.perform(get("/orcamentos").header("Authorization", CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].numeroOrcamento").value("orc-meu"));
        mockMvc.perform(post("/orcamentos/orc-outro/aprovacao").header("Authorization", CLIENTE)).andExpect(status().isNotFound());
        mockMvc.perform(post("/orcamentos/orc-outro/rejeicao").header("Authorization", CLIENTE)).andExpect(status().isNotFound());

        mockMvc.perform(post("/orcamentos/orc-meu/aprovacao").header("Authorization", CLIENTE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APROVADO"));

        assertThat(outbox.findAll()).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo("OrcamentoAprovado");
            assertThat(m.getPayload()).contains("\"canal\":\"CLIENTE\"");
        });
        assertThat(orcamentoRepository.findByNumeroOrcamento("orc-outro").orElseThrow().getStatus())
                .isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);
    }

    @Test
    void funcionarioDecidePeloClienteEORegistroLevaOCanal() throws Exception {
        orcamentoRepository.save(OrcamentosDeTeste.novo("orc-func", StatusOrcamento.AGUARDANDO_APROVACAO));

        mockMvc.perform(post("/orcamentos/orc-func/rejeicao").header("Authorization", FUNCIONARIO))
                .andExpect(status().isNoContent());

        assertThat(outbox.findAll()).singleElement().satisfies(m -> {
            assertThat(m.getTipo()).isEqualTo("OrcamentoRejeitado");
            assertThat(m.getPayload()).contains("\"canal\":\"FUNCIONARIO\"");
        });
    }

    @Test
    void clienteNaoAlteraNemExcluiETokenDeClienteSemClienteIdNaoEnxergaNada() throws Exception {
        orcamentoRepository.save(OrcamentosDeTeste.novo("orc-meu", StatusOrcamento.AGUARDANDO_APROVACAO));
        String semClienteId = "Bearer " + TokensDeTeste.emitir("52998224725", Map.of("tipo", "CLIENTE"));

        mockMvc.perform(put("/orcamentos/orc-meu").header("Authorization", CLIENTE)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/orcamentos/orc-meu").header("Authorization", CLIENTE)).andExpect(status().isForbidden());
        mockMvc.perform(post("/orcamentos/orc-meu/aprovacao").header("Authorization", semClienteId)).andExpect(status().isNotFound());
        mockMvc.perform(get("/orcamentos/orc-meu").header("Authorization", semClienteId)).andExpect(status().isNotFound());
    }
}
