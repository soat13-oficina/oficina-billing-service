package br.com.oficina.billing.orcamento.application.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.application.command.ExcluirOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.usecase.ExcluirOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Service
public class ExcluirOrcamentoService implements ExcluirOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(ExcluirOrcamentoService.class);

    private final OrcamentoRepository orcamentoRepository;

    public ExcluirOrcamentoService(OrcamentoRepository orcamentoRepository) {
        this.orcamentoRepository = orcamentoRepository;
    }

    @Override
    public void excluirOrcamento(ExcluirOrcamentoCommand command) {
        log.info("Iniciando exclusao de orcamento. numeroOrcamento={}", command.orcamentoId());
        orcamentoRepository.buscarPorNumeroOrcamento(command.orcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."));
        orcamentoRepository.excluirPorNumeroOrcamento(command.orcamentoId());
        log.info("Orcamento excluido com sucesso. numeroOrcamento={}", command.orcamentoId());
    }
}
