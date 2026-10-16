package br.com.oficina.billing.orcamento.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.RejeitarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.saga.EventosOrcamento;
import br.com.oficina.billing.orcamento.application.usecase.RejeitarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Service
public class RejeitarOrcamentoService implements RejeitarOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(RejeitarOrcamentoService.class);

    private final OrcamentoRepository orcamentoRepository;
    private final EventosOrcamento eventos;

    public RejeitarOrcamentoService(OrcamentoRepository orcamentoRepository, EventosOrcamento eventos) {
        this.orcamentoRepository = orcamentoRepository;
        this.eventos = eventos;
    }

    /**
     * Rejeita e publica {@code OrcamentoRejeitado}; o orquestrador encerra a OS. Nao ha reserva a
     * liberar: as pecas so sao reservadas depois da aprovacao (passo ReservarPecas da Saga).
     */
    @Override
    @Transactional
    public void rejeitarOrcamento(RejeitarOrcamentoCommand command) {
        log.info("Iniciando rejeicao de orcamento. numeroOrcamento={}", command.numeroOrcamento());
        Orcamento orcamento = orcamentoRepository.buscarPorNumeroOrcamento(command.numeroOrcamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."));
        // Cliente so decide o proprio orcamento; o de outro cliente "nao existe" para ele.
        if (command.clienteSolicitante() != null && !command.clienteSolicitante().equals(orcamento.getClienteId())) {
            throw new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado.");
        }

        if (orcamento.getStatus() != StatusOrcamento.AGUARDANDO_APROVACAO) {
            throw new RegraDeNegocioException("Orcamento so pode ser rejeitado quando estiver aguardando aprovacao.");
        }

        orcamento.rejeitar();
        orcamentoRepository.atualizar(orcamento);
        eventos.orcamentoRejeitado(orcamento, command.canal());
        log.info("Orcamento rejeitado com sucesso. numeroOrcamento={}", command.numeroOrcamento());
    }
}
