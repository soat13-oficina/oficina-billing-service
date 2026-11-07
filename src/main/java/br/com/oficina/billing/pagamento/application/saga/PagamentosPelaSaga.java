package br.com.oficina.billing.pagamento.application.saga;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Passo {@code SolicitarPagamento} e compensacao {@code EstornarPagamento} da Saga (ADR 0004 e 0007).
 * Pagamento e orcamento moram no mesmo servico (Billing), por isso o pagamento le o orcamento direto.
 *
 * <p>Idempotente pelo estado: um {@code SolicitarPagamento} reenviado encontra a cobranca do orcamento
 * e repete o {@code PagamentoSolicitado}; um {@code EstornarPagamento} reenviado encontra o pagamento
 * estornado e repete o {@code PagamentoEstornado}.
 */
@Service
public class PagamentosPelaSaga {
    private static final Logger log = LoggerFactory.getLogger(PagamentosPelaSaga.class);

    private final PagamentoRepository pagamentos;
    private final OrcamentoRepository orcamentos;
    private final GatewayPagamento gateway;
    private final EventosPagamento eventos;
    private final JsonMapper jsonMapper;

    public PagamentosPelaSaga(
            PagamentoRepository pagamentos,
            OrcamentoRepository orcamentos,
            GatewayPagamento gateway,
            EventosPagamento eventos,
            JsonMapper jsonMapper) {
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
        this.gateway = gateway;
        this.eventos = eventos;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void solicitar(Envelope comando) {
        MensagensPagamento.SolicitarPagamento pedido =
                jsonMapper.treeToValue(comando.payload(), MensagensPagamento.SolicitarPagamento.class);
        Optional<Pagamento> vigente = pagamentos.buscarPorOrcamento(pedido.orcamentoId()).stream()
                .filter(p -> p.getStatus() == StatusPagamento.PENDENTE || p.getStatus() == StatusPagamento.APROVADO)
                .findFirst();
        if (vigente.isPresent()) {
            log.info("Cobranca ja criada para o orcamento; resposta repetida. pagamentoId={}", vigente.get().getId());
            eventos.solicitado(comando, vigente.get());
            return;
        }
        Orcamento orcamento = orcamentos.buscarPorId(pedido.orcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado: " + pedido.orcamentoId()));
        LocalDateTime agora = LocalDateTime.now();
        Pagamento pagamento = Pagamento.novo(orcamento.getId(), comando.ordemServicoId(), comando.sagaId(),
                orcamento.getValorTotal(), LocalDateTime.ofInstant(pedido.expiraEm(), ZoneId.systemDefault()), agora);
        GatewayPagamento.Cobranca cobranca = gateway.criarCobranca(pagamento,
                "Orcamento " + orcamento.getNumeroOrcamento() + " - veiculo " + orcamento.getPlacaVeiculo());
        pagamento.registrarCobranca(cobranca.referenciaExterna(), cobranca.link(), agora);
        pagamentos.salvar(pagamento);
        log.info("Cobranca criada. pagamentoId={}, numeroOrcamento={}, valor={}, expiraEm={}",
                pagamento.getId(), orcamento.getNumeroOrcamento(), pagamento.getValor(), pagamento.getExpiraEm());
        eventos.solicitado(comando, pagamento);
    }

    /**
     * Compensacao da execucao abortada: devolve o pagamento ao cliente e cancela o orcamento. Recusa do
     * provedor responde {@code EstornoFalhou}: a Saga tenta de novo e, esgotadas as tentativas, para
     * em {@code FALHA_COMPENSACAO}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void estornar(Envelope comando) {
        MensagensPagamento.EstornarPagamento pedido =
                jsonMapper.treeToValue(comando.payload(), MensagensPagamento.EstornarPagamento.class);
        Optional<Pagamento> encontrado = pagamentos.buscarPorId(pedido.pagamentoId());
        if (encontrado.isEmpty()) {
            eventos.estornoFalhou(comando, pedido.pagamentoId(), "Pagamento nao encontrado");
            return;
        }
        Pagamento pagamento = encontrado.get();
        if (pagamento.getStatus() == StatusPagamento.ESTORNADO) {
            log.info("Pagamento ja estornado; confirmacao republicada. pagamentoId={}", pagamento.getId());
            eventos.estornado(comando, pagamento);
            return;
        }
        if (pagamento.getStatus() != StatusPagamento.APROVADO) {
            eventos.estornoFalhou(comando, pagamento.getId(), "Pagamento em " + pagamento.getStatus() + " nao pode ser estornado");
            return;
        }
        GatewayPagamento.Estorno estorno = gateway.estornar(pagamento);
        if (!estorno.realizado()) {
            log.warn("Estorno recusado pelo provedor. pagamentoId={}, motivo={}", pagamento.getId(), estorno.motivoFalha());
            eventos.estornoFalhou(comando, pagamento.getId(), estorno.motivoFalha());
            return;
        }
        LocalDateTime agora = LocalDateTime.now();
        pagamento.estornar(estorno.referenciaExterna(), pedido.motivo(), agora);
        pagamentos.salvar(pagamento);
        Optional<Orcamento> orcamento = orcamentos.buscarPorId(pagamento.getOrcamentoId());
        if (orcamento.isPresent() && orcamento.get().cancelar()) {
            orcamentos.atualizar(orcamento.get());
        }
        log.info("Pagamento estornado e orcamento cancelado. pagamentoId={}, valor={}", pagamento.getId(), pagamento.getValor());
        eventos.estornado(comando, pagamento);
    }
}
