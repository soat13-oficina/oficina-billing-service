package br.com.oficina.billing.pagamento.application;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.domain.exception.ConflitoDeRecursoException;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.application.port.GatewayPagamento.SituacaoNoProvedor;
import br.com.oficina.billing.pagamento.application.saga.EventosPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;

/**
 * Resultado de uma cobranca: notificacao do Mercado Pago (o resultado vem da consulta ao provedor,
 * nunca do corpo do webhook), simulacao no desenvolvimento local e expiracao pelo prazo. Cada
 * mudanca publica o evento para a Saga - o Billing e a autoridade do pagamento (ADR 0004 e 0007).
 */
@Service
public class ResultadoPagamentoService {
    private static final Logger log = LoggerFactory.getLogger(ResultadoPagamentoService.class);

    public enum Resultado {
        APROVADO,
        RECUSADO
    }

    private final PagamentoRepository pagamentos;
    private final OrcamentoRepository orcamentos;
    private final EventosPagamento eventos;
    private final GatewayPagamento gateway;

    public ResultadoPagamentoService(
            PagamentoRepository pagamentos, OrcamentoRepository orcamentos, EventosPagamento eventos, GatewayPagamento gateway) {
        this.pagamentos = pagamentos;
        this.orcamentos = orcamentos;
        this.eventos = eventos;
        this.gateway = gateway;
    }

    /**
     * Registra o resultado. Repetir o mesmo resultado nao e erro (o webhook chega mais de uma vez) e
     * nao publica de novo; resultado diferente de um ja registrado e conflito (409).
     */
    @Transactional
    public Pagamento registrar(UUID pagamentoId, Resultado resultado, String referenciaPagamento, String motivo) {
        if (resultado == null) {
            throw new RegraDeNegocioException("Resultado do pagamento e obrigatorio (APROVADO ou RECUSADO).");
        }
        return aplicar(consultar(pagamentoId), resultado, referenciaPagamento, motivo, LocalDateTime.now());
    }

    /**
     * Notificacao do provedor sobre um pagamento: consulta a situacao real e aplica. Pagamento que o
     * provedor nao conhece, de outra cobranca ou ainda pendente nao muda nada.
     *
     * @return a cobranca afetada, se alguma mudou ou ja estava com esse resultado
     */
    @Transactional
    public Optional<Pagamento> registrarNotificacao(String referenciaPagamento) {
        Optional<SituacaoNoProvedor> situacao = gateway.consultarPagamento(referenciaPagamento);
        if (situacao.isEmpty() || situacao.get().pagamentoId().isEmpty()) {
            log.warn("Notificacao de pagamento sem cobranca correspondente. referenciaPagamento={}", referenciaPagamento);
            return Optional.empty();
        }
        Optional<Pagamento> pagamento = pagamentos.buscarPorId(situacao.get().pagamentoId().get());
        if (pagamento.isEmpty()) {
            log.warn("Notificacao de pagamento de cobranca desconhecida. referenciaPagamento={}, pagamentoId={}",
                    referenciaPagamento, situacao.get().pagamentoId().get());
            return Optional.empty();
        }
        return Optional.of(aplicarSituacao(pagamento.get(), situacao.get(), LocalDateTime.now()));
    }

    /**
     * Expira as cobrancas pendentes com prazo vencido. Antes, pergunta ao provedor: um pagamento aprovado
     * (ou recusado) cujo webhook atrasou e registrado como tal, e nao expirado.
     *
     * @return quantas cobrancas foram expiradas
     */
    @Transactional
    public int expirarVencidos(LocalDateTime agora) {
        List<Pagamento> vencidos = pagamentos.buscarPendentesVencidos(agora);
        int expirados = 0;
        for (Pagamento pagamento : vencidos) {
            Optional<SituacaoNoProvedor> noProvedor = gateway.buscarPorCobranca(pagamento);
            if (noProvedor.isPresent() && noProvedor.get().resultado() != GatewayPagamento.ResultadoNoProvedor.PENDENTE) {
                log.info("Cobranca vencida ja tinha resultado no provedor; registrado em vez de expirar. pagamentoId={}, resultado={}",
                        pagamento.getId(), noProvedor.get().resultado());
                aplicarSituacao(pagamento, noProvedor.get(), agora);
                continue;
            }
            pagamento.expirar(agora);
            pagamentos.salvar(pagamento);
            eventos.expirado(pagamento);
            expirados++;
            log.info("Pagamento expirado. pagamentoId={}, expiraEm={}", pagamento.getId(), pagamento.getExpiraEm());
        }
        return expirados;
    }

    private Pagamento aplicarSituacao(Pagamento pagamento, SituacaoNoProvedor situacao, LocalDateTime agora) {
        Resultado resultado = switch (situacao.resultado()) {
            case APROVADO -> Resultado.APROVADO;
            case RECUSADO -> Resultado.RECUSADO;
            case PENDENTE -> null;
        };
        if (resultado == null) {
            log.info("Pagamento ainda pendente no provedor. pagamentoId={}, referenciaPagamento={}",
                    pagamento.getId(), situacao.referenciaPagamento());
            return pagamento;
        }
        try {
            return aplicar(pagamento, resultado, situacao.referenciaPagamento(), situacao.motivo(), agora);
        } catch (ConflitoDeRecursoException e) {
            // Ex.: aprovado no provedor depois de a cobranca expirar ou ser cancelada. Exige acao manual
            // (estorno pelo painel) - o webhook nao deve ficar sendo reenviado por isso.
            log.error("Resultado do provedor diverge da cobranca; acao manual necessaria. pagamentoId={}, status={}, resultado={}",
                    pagamento.getId(), pagamento.getStatus(), resultado);
            return pagamento;
        }
    }

    private Pagamento aplicar(Pagamento pagamento, Resultado resultado, String referenciaPagamento, String motivo, LocalDateTime agora) {
        if (resultado == Resultado.APROVADO) {
            if (pagamento.aprovar(referenciaPagamento, agora)) {
                pagamentos.salvar(pagamento);
                eventos.confirmado(pagamento);
                log.info("Pagamento aprovado. pagamentoId={}, valor={}", pagamento.getId(), pagamento.getValor());
            }
        } else if (pagamento.recusar(referenciaPagamento, motivo == null || motivo.isBlank() ? "Pagamento recusado" : motivo, agora)) {
            pagamentos.salvar(pagamento);
            eventos.recusado(pagamento);
            log.info("Pagamento recusado. pagamentoId={}, motivo={}", pagamento.getId(), pagamento.getMotivo());
        }
        return pagamento;
    }

    @Transactional(readOnly = true)
    public Pagamento consultar(UUID pagamentoId) {
        return pagamentos.buscarPorId(pagamentoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pagamento nao encontrado: " + pagamentoId));
    }

    /**
     * Cobranca, vista por um cliente (token da Lambda): so a do proprio orcamento - a de outro cliente
     * "nao existe" para ele. {@code clienteSolicitante} nulo = funcionario.
     */
    @Transactional(readOnly = true)
    public Pagamento consultar(UUID pagamentoId, UUID clienteSolicitante) {
        Pagamento pagamento = consultar(pagamentoId);
        if (clienteSolicitante != null && orcamentos.buscarPorId(pagamento.getOrcamentoId())
                .filter(orcamento -> clienteSolicitante.equals(orcamento.getClienteId())).isEmpty()) {
            throw new RecursoNaoEncontradoException("Pagamento nao encontrado: " + pagamentoId);
        }
        return pagamento;
    }

    /** Cobranca mais recente do orcamento. */
    @Transactional(readOnly = true)
    public Pagamento consultarPorOrcamento(String numeroOrcamento) {
        return consultarPorOrcamento(numeroOrcamento, null);
    }

    /** Cobranca mais recente do orcamento; cliente so a do proprio orcamento. */
    @Transactional(readOnly = true)
    public Pagamento consultarPorOrcamento(String numeroOrcamento, UUID clienteSolicitante) {
        UUID orcamentoId = orcamentos.buscarPorNumeroOrcamento(numeroOrcamento)
                .filter(orcamento -> clienteSolicitante == null || clienteSolicitante.equals(orcamento.getClienteId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."))
                .getId();
        return pagamentos.buscarPorOrcamento(orcamentoId).stream().findFirst()
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento ainda sem cobranca: o pagamento e pedido depois da aprovacao e da reserva das pecas."));
    }
}
