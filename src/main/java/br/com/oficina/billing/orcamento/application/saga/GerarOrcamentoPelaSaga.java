package br.com.oficina.billing.orcamento.application.saga;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.CadastrarNovoOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.application.service.PecasNaoEncontradasException;
import br.com.oficina.billing.orcamento.application.usecase.CadastrarNovoOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Passo {@code GerarOrcamento} da Saga (ADR 0004, passo 2): o orcamento nasce do snapshot do
 * diagnostico que vem no comando - sem consultar cliente nem OS.
 *
 * <p>Falha de negocio (peca inexistente, desconto maior que o total...) nao e excecao para o
 * consumidor: vira a resposta {@code OrcamentoFalhou}, e a Saga decide o que fazer. Idempotente pelo
 * estado: comando reenviado encontra o orcamento da OS e so repete o {@code OrcamentoGerado}.
 */
@Service
public class GerarOrcamentoPelaSaga {
    private static final Logger log = LoggerFactory.getLogger(GerarOrcamentoPelaSaga.class);

    private final CadastrarNovoOrcamentoUseCase cadastrar;
    private final OrcamentoRepository orcamentos;
    private final EventosOrcamento eventos;
    private final JsonMapper jsonMapper;
    private final Duration validadePadrao;

    public GerarOrcamentoPelaSaga(
            CadastrarNovoOrcamentoUseCase cadastrar,
            OrcamentoRepository orcamentos,
            EventosOrcamento eventos,
            JsonMapper jsonMapper,
            @Value("${orcamento.validade-padrao:P7D}") Duration validadePadrao) {
        this.cadastrar = cadastrar;
        this.orcamentos = orcamentos;
        this.eventos = eventos;
        this.jsonMapper = jsonMapper;
        this.validadePadrao = validadePadrao;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void gerar(Envelope comando) {
        MensagensOrcamento.GerarOrcamento pedido = jsonMapper.treeToValue(comando.payload(), MensagensOrcamento.GerarOrcamento.class);
        Optional<Orcamento> existente = orcamentos.buscarPorOrdemDeServicoId(comando.ordemServicoId());
        if (existente.isPresent()) {
            log.info("Orcamento ja gerado para a OS; resposta repetida. numeroOrcamento={}", existente.get().getNumeroOrcamento());
            eventos.orcamentoGerado(comando, existente.get());
            return;
        }
        try {
            UUID id = cadastrar.cadastrarNovoOrcamento(paraCadastro(pedido, comando));
            Orcamento orcamento = orcamentos.buscarPorId(id)
                    .orElseThrow(() -> new IllegalStateException("Orcamento recem-criado nao encontrado: " + id));
            log.info("Orcamento gerado pela Saga. numeroOrcamento={}, numeroOrdemServico={}",
                    orcamento.getNumeroOrcamento(), pedido.numeroOrdemServico());
            eventos.orcamentoGerado(comando, orcamento);
        } catch (PecasNaoEncontradasException e) {
            log.info("Orcamento nao gerado: pecas inexistentes. numeroOrdemServico={}, pecas={}",
                    pedido.numeroOrdemServico(), e.getPecaInsumoIds());
            eventos.orcamentoFalhou(comando, e.getMessage(), e.getPecaInsumoIds());
        } catch (RegraDeNegocioException e) {
            log.info("Orcamento nao gerado. numeroOrdemServico={}, motivo={}", pedido.numeroOrdemServico(), e.getMessage());
            eventos.orcamentoFalhou(comando, e.getMessage(), null);
        }
    }

    private CadastrarNovoOrcamentoCommand paraCadastro(MensagensOrcamento.GerarOrcamento pedido, Envelope comando) {
        List<MensagensOrcamento.Servico> servicos = pedido.servicos() == null ? List.of() : pedido.servicos();
        List<MensagensOrcamento.ItemPeca> pecas = pedido.pecas() == null ? List.of() : pedido.pecas();
        LocalDateTime validade = pedido.validade() != null
                ? LocalDateTime.ofInstant(pedido.validade(), ZoneId.systemDefault())
                : LocalDateTime.now().plus(validadePadrao);
        return new CadastrarNovoOrcamentoCommand(
                "ORC-" + UUID.randomUUID(),
                new CadastrarNovoOrcamentoCommand.ClienteOrcamento(
                        pedido.cliente().id(), pedido.cliente().nome(), pedido.cliente().documento()),
                comando.ordemServicoId(),
                pedido.funcionario().id(),
                pedido.veiculo().placa(),
                pedido.veiculo().marca(),
                pedido.veiculo().modelo(),
                pedido.descricaoDiagnostico(),
                servicos.stream().map(MensagensOrcamento.Servico::descricao).toList(),
                pecas.stream().map(item -> new PecaOrcamentoInput(item.pecaInsumoId(), item.quantidade())).toList(),
                maoDeObra(pedido, servicos),
                pedido.desconto(),
                validade,
                pedido.observacoes(),
                comando.sagaId());
    }

    /** Total informado (Passo 1) ou a soma da mao de obra de cada servico (diagnostico na Execution). */
    private static BigDecimal maoDeObra(MensagensOrcamento.GerarOrcamento pedido, List<MensagensOrcamento.Servico> servicos) {
        if (pedido.valorMaoDeObra() != null) {
            return pedido.valorMaoDeObra();
        }
        List<BigDecimal> valores = servicos.stream().map(MensagensOrcamento.Servico::valorMaoDeObra).filter(Objects::nonNull).toList();
        return valores.isEmpty() ? null : valores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
