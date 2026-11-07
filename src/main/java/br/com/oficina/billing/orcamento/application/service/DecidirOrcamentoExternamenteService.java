package br.com.oficina.billing.orcamento.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.domain.exception.ConflitoDeRecursoException;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.AprovarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.DecidirOrcamentoExternamenteCommand;
import br.com.oficina.billing.orcamento.application.command.RejeitarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.result.DecisaoOrcamentoResult;
import br.com.oficina.billing.orcamento.application.usecase.AprovarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.DecidirOrcamentoExternamenteUseCase;
import br.com.oficina.billing.orcamento.application.usecase.RejeitarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.DecisaoOrcamento;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

/**
 * Decisao do orcamento vinda de sistema externo (webhook com {@code X-Webhook-Token}).
 *
 * <p>Fase 4: decide SO o orcamento. A aprovacao ou rejeicao publica {@code OrcamentoAprovado} /
 * {@code OrcamentoRejeitado} na mesma transacao, e e o orquestrador da Saga que move a ordem de
 * servico ao receber o evento (ADR 0004). Por isso a resposta nao traz mais a situacao da OS - ela
 * muda logo depois, de forma assincrona, e pode ser consultada em {@code GET /ordens-servico/{n}}.
 */
@Service
public class DecidirOrcamentoExternamenteService implements DecidirOrcamentoExternamenteUseCase {
    private static final Logger log = LoggerFactory.getLogger(DecidirOrcamentoExternamenteService.class);

    private final OrcamentoRepository orcamentoRepository;
    private final AprovarOrcamentoUseCase aprovarOrcamentoUseCase;
    private final RejeitarOrcamentoUseCase rejeitarOrcamentoUseCase;

    public DecidirOrcamentoExternamenteService(
            OrcamentoRepository orcamentoRepository,
            AprovarOrcamentoUseCase aprovarOrcamentoUseCase,
            RejeitarOrcamentoUseCase rejeitarOrcamentoUseCase) {
        this.orcamentoRepository = orcamentoRepository;
        this.aprovarOrcamentoUseCase = aprovarOrcamentoUseCase;
        this.rejeitarOrcamentoUseCase = rejeitarOrcamentoUseCase;
    }

    @Override
    @Transactional
    public DecisaoOrcamentoResult decidir(DecidirOrcamentoExternamenteCommand command) {
        if (command.decisao() == null) {
            throw new RegraDeNegocioException("Decisao do orcamento e obrigatoria.");
        }

        log.info("Recebida decisao externa de orcamento. numeroOrcamento={}, decisao={}",
                command.numeroOrcamento(),
                command.decisao());
        Orcamento orcamento = orcamentoRepository.buscarPorNumeroOrcamento(command.numeroOrcamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."));

        if (orcamento.getStatus() == StatusOrcamento.APROVADO || orcamento.getStatus() == StatusOrcamento.REJEITADO) {
            return tratarDecisaoJaRegistrada(command, orcamento);
        }
        if (orcamento.getStatus() != StatusOrcamento.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("Orcamento so pode receber decisao quando estiver aguardando aprovacao.");
        }

        if (command.decisao() == DecisaoOrcamento.APROVADO) {
            aprovarOrcamentoUseCase.aprovarOrcamento(new AprovarOrcamentoCommand(command.numeroOrcamento(), CanalDecisao.INTEGRACAO));
            return resposta(orcamento, StatusOrcamento.APROVADO);
        }
        rejeitarOrcamentoUseCase.rejeitarOrcamento(new RejeitarOrcamentoCommand(command.numeroOrcamento(), CanalDecisao.INTEGRACAO));
        return resposta(orcamento, StatusOrcamento.REJEITADO);
    }

    /** Retry da mesma decisao e idempotente; decisao divergente da registrada e conflito. */
    private DecisaoOrcamentoResult tratarDecisaoJaRegistrada(DecidirOrcamentoExternamenteCommand command, Orcamento orcamento) {
        boolean mesmaDecisao = (orcamento.getStatus() == StatusOrcamento.APROVADO
                && command.decisao() == DecisaoOrcamento.APROVADO)
                || (orcamento.getStatus() == StatusOrcamento.REJEITADO
                && command.decisao() == DecisaoOrcamento.REJEITADO);
        if (!mesmaDecisao) {
            throw new ConflitoDeRecursoException("Decisao divergente da decisao ja registrada para o orcamento.");
        }
        return resposta(orcamento, orcamento.getStatus());
    }

    private static DecisaoOrcamentoResult resposta(Orcamento orcamento, StatusOrcamento status) {
        return new DecisaoOrcamentoResult(orcamento.getNumeroOrcamento(), status, orcamento.getOrdemDeServicoId());
    }
}
