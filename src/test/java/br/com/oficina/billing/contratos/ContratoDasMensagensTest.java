package br.com.oficina.billing.contratos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import br.com.oficina.billing.common.infrastructure.mensageria.ConsumidorMensagens;
import br.com.oficina.billing.common.infrastructure.mensageria.OutboxMensagemEntity;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataMensagemProcessadaRepository;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.orcamento.application.command.AprovarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.RejeitarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.usecase.AprovarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.RejeitarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.support.catalogo.CatalogoPecasEmMemoria;
import br.com.oficina.billing.support.contratos.ContratosDeMensagens;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Teste de contrato (ADR 0005) dos dois lados:
 * <ul>
 *   <li>consumidor: o exemplo de cada comando que o Billing recebe, como o contrato documenta, e
 *       processado sem erro;</li>
 *   <li>produtor: todo evento que o Billing grava no outbox passa no JSON Schema do seu tipo.</li>
 * </ul>
 */
@SpringBootTest
class ContratoDasMensagensTest {

    private static final ContratosDeMensagens CONTRATOS = new ContratosDeMensagens();

    private static final Set<String> PRODUZIDOS = Set.of(
            "OrcamentoGerado", "OrcamentoFalhou", "OrcamentoAprovado", "OrcamentoRejeitado",
            "AprovacaoRevertida", "OrcamentoCancelado");

    @Autowired
    private ConsumidorMensagens consumidor;

    @Autowired
    private OrcamentoRepository orcamentos;

    @Autowired
    private SpringDataOrcamentoRepository springDataOrcamentos;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    @Autowired
    private SpringDataMensagemProcessadaRepository processadas;

    @Autowired
    private CatalogoPecasEmMemoria catalogo;

    @Autowired
    private AprovarOrcamentoUseCase aprovar;

    @Autowired
    private RejeitarOrcamentoUseCase rejeitar;

    @BeforeEach
    @AfterEach
    void limpar() {
        springDataOrcamentos.deleteAll();
        outbox.deleteAll();
        processadas.deleteAll();
        catalogo.limpar();
    }

    /** Exemplo do contrato com {@code messageId} novo (e, se pedido, outra OS). */
    private static ObjectNode exemplo(String tipo, UUID ordemServicoId) {
        ObjectNode envelope = (ObjectNode) CONTRATOS.exemplo(tipo);
        envelope.put("messageId", UUID.randomUUID().toString());
        if (ordemServicoId != null) {
            envelope.put("ordemServicoId", ordemServicoId.toString());
        }
        return envelope;
    }

    @Test
    void exemplosDosComandosSaoProcessadosEOsEventosRespeitamOContrato() {
        // GerarOrcamento do contrato, com as pecas do exemplo no catalogo.
        ObjectNode gerar = exemplo("GerarOrcamento", null);
        for (JsonNode peca : gerar.get("payload").get("pecas")) {
            catalogo.cadastrar(peca.get("pecaInsumoId").asString(), "Peca do exemplo", "80.00");
        }
        consumidor.consumir(gerar.toString());
        UUID ordemDoExemplo = UUID.fromString(gerar.get("ordemServicoId").asString());
        Orcamento gerado = orcamentos.buscarPorOrdemDeServicoId(ordemDoExemplo).orElseThrow();
        assertThat(gerado.getSagaId()).hasToString(gerar.get("sagaId").asString());

        // Mesmo comando para outra OS, com uma peca fora do catalogo: OrcamentoFalhou.
        ObjectNode comPecaInvalida = exemplo("GerarOrcamento", UUID.randomUUID());
        ((ObjectNode) comPecaInvalida.get("payload").get("pecas").get(0)).put("pecaInsumoId", "nao-existe");
        consumidor.consumir(comPecaInvalida.toString());

        // Aprovado e revertido pelo ReverterAprovacao do contrato.
        aprovar.aprovarOrcamento(new AprovarOrcamentoCommand(gerado.getNumeroOrcamento()));
        ObjectNode reverter = exemplo("ReverterAprovacao", ordemDoExemplo);
        ((ObjectNode) reverter.get("payload")).put("orcamentoId", gerado.getId().toString());
        consumidor.consumir(reverter.toString());
        assertThat(orcamentos.buscarPorId(gerado.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.AGUARDANDO_APROVACAO);

        // Cancelado pelo CancelarOrcamento do contrato.
        ObjectNode cancelar = exemplo("CancelarOrcamento", ordemDoExemplo);
        ((ObjectNode) cancelar.get("payload")).put("orcamentoId", gerado.getId().toString());
        consumidor.consumir(cancelar.toString());
        assertThat(orcamentos.buscarPorId(gerado.getId()).orElseThrow().getStatus()).isEqualTo(StatusOrcamento.CANCELADO);

        // Rejeicao (evento espontaneo).
        Orcamento outro = orcamentos.salvar(OrcamentosDeTeste.novo("ORC-CONTRATO", StatusOrcamento.AGUARDANDO_APROVACAO));
        rejeitar.rejeitarOrcamento(new RejeitarOrcamentoCommand(outro.getNumeroOrcamento()));

        List<String> erros = new ArrayList<>();
        Set<String> tipos = new TreeSet<>();
        for (OutboxMensagemEntity mensagem : outbox.findAll()) {
            tipos.add(mensagem.getTipo());
            erros.addAll(CONTRATOS.validar(mensagem.getPayload()));
        }
        assertThat(erros).as("eventos fora do contrato").isEmpty();
        assertThat(tipos).containsExactlyInAnyOrderElementsOf(PRODUZIDOS);
    }
}
