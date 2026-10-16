package br.com.oficina.billing.orcamento.application.saga;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.application.port.PagamentosDoOrcamento;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import tools.jackson.databind.json.JsonMapper;

/**
 * Compensacao {@code CancelarOrcamento} (ADR 0004): o orcamento deixa de valer - e a cobranca
 * pendente, se houver - e a Saga recebe a confirmacao {@code OrcamentoCancelado}.
 *
 * <p>Idempotente pelo estado: um comando reenviado por timeout (messageId novo) encontra o orcamento
 * ja cancelado e so republica a confirmacao - e isso que destrava a Saga quando a resposta original
 * se perdeu (ADR 0005).
 */
@Service
public class CancelarOrcamentoPelaSaga {
    private static final Logger log = LoggerFactory.getLogger(CancelarOrcamentoPelaSaga.class);

    private final OrcamentoRepository orcamentoRepository;
    private final PagamentosDoOrcamento pagamentos;
    private final EventosOrcamento eventos;
    private final JsonMapper jsonMapper;

    public CancelarOrcamentoPelaSaga(
            OrcamentoRepository orcamentoRepository,
            PagamentosDoOrcamento pagamentos,
            EventosOrcamento eventos,
            JsonMapper jsonMapper) {
        this.orcamentoRepository = orcamentoRepository;
        this.pagamentos = pagamentos;
        this.eventos = eventos;
        this.jsonMapper = jsonMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelar(Envelope comando) {
        MensagensOrcamento.CancelarOrcamento pedido =
                jsonMapper.treeToValue(comando.payload(), MensagensOrcamento.CancelarOrcamento.class);
        Orcamento orcamento = orcamentoRepository.buscarPorId(pedido.orcamentoId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado: " + pedido.orcamentoId()));
        if (orcamento.cancelar()) {
            orcamentoRepository.atualizar(orcamento);
            log.info("Orcamento cancelado pela Saga. numeroOrcamento={}, motivo={}", orcamento.getNumeroOrcamento(), pedido.motivo());
        } else {
            log.info("Orcamento ja estava cancelado; confirmacao republicada. numeroOrcamento={}", orcamento.getNumeroOrcamento());
        }
        boolean pagamentoCancelado = pagamentos.cancelarPendente(orcamento.getId());
        eventos.orcamentoCancelado(comando, orcamento, pagamentoCancelado);
    }
}
