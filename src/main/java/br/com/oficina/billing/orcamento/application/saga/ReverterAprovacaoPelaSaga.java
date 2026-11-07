package br.com.oficina.billing.orcamento.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compensacao {@code ReverterAprovacao} (ADR 0004, passo 4): faltou estoque, o orcamento volta a
 * aguardar a decisao do cliente e a Saga recebe {@code AprovacaoRevertida}. Idempotente pelo estado.
 */
@Service
public class ReverterAprovacaoPelaSaga {
    private static final Logger log = LoggerFactory.getLogger(ReverterAprovacaoPelaSaga.class);

    private final OrcamentoRepository orcamentoRepository;
    private final EventosOrcamento eventos;
    private final JsonMapper jsonMapper;

    public ReverterAprovacaoPelaSaga(OrcamentoRepository orcamentoRepository, EventosOrcamento eventos, JsonMapper jsonMapper) {
        this.orcamentoRepository = orcamentoRepository;
        this.eventos = eventos;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reverter(Envelope comando) {
        MensagensOrcamento.ReverterAprovacao pedido =
                jsonMapper.treeToValue(comando.payload(), MensagensOrcamento.ReverterAprovacao.class);
        Orcamento orcamento = orcamentoRepository.buscarPorId(pedido.orcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado: " + pedido.orcamentoId()));
        if (orcamento.reverterAprovacao()) {
            orcamentoRepository.atualizar(orcamento);
            log.info("Aprovacao revertida pela Saga. numeroOrcamento={}, motivo={}", orcamento.getNumeroOrcamento(), pedido.motivo());
        } else {
            log.info("Aprovacao ja estava revertida; confirmacao republicada. numeroOrcamento={}", orcamento.getNumeroOrcamento());
        }
        eventos.aprovacaoRevertida(comando, orcamento);
    }
}
