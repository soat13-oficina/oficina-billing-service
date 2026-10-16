package br.com.oficina.billing.orcamento.application.saga;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.Filas;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;

@Component
public class PublicadorEventosOrcamento implements EventosOrcamento {

    private final PublicadorMensagens publicador;
    private final FabricaEnvelopes fabrica;

    public PublicadorEventosOrcamento(PublicadorMensagens publicador, FabricaEnvelopes fabrica) {
        this.publicador = publicador;
        this.fabrica = fabrica;
    }

    @Override
    public void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal) {
        decidido(MensagensOrcamento.ORCAMENTO_APROVADO, orcamento, canal);
    }

    @Override
    public void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal) {
        decidido(MensagensOrcamento.ORCAMENTO_REJEITADO, orcamento, canal);
    }

    @Override
    public void orcamentoGerado(Envelope comando, Orcamento orcamento) {
        responder(comando, MensagensOrcamento.ORCAMENTO_GERADO, new MensagensOrcamento.OrcamentoGerado(
                orcamento.getId(),
                orcamento.getNumeroOrcamento(),
                dinheiro(orcamento.getValorMaoDeObra()),
                dinheiro(orcamento.getValorPecas()),
                dinheiro(orcamento.getDesconto()),
                dinheiro(orcamento.getValorTotal()),
                orcamento.getValidade().atZone(ZoneId.systemDefault()).toInstant()));
    }

    @Override
    public void orcamentoFalhou(Envelope comando, String motivo, List<String> pecasInvalidas) {
        responder(comando, MensagensOrcamento.ORCAMENTO_FALHOU, new MensagensOrcamento.OrcamentoFalhou(
                motivo, pecasInvalidas == null || pecasInvalidas.isEmpty() ? null : List.copyOf(pecasInvalidas)));
    }

    @Override
    public void aprovacaoRevertida(Envelope comando, Orcamento orcamento) {
        responder(comando, MensagensOrcamento.APROVACAO_REVERTIDA, new MensagensOrcamento.AprovacaoRevertida(orcamento.getId()));
    }

    @Override
    public void orcamentoCancelado(Envelope comando, Orcamento orcamento, boolean pagamentoCancelado) {
        responder(comando, MensagensOrcamento.ORCAMENTO_CANCELADO,
                new MensagensOrcamento.OrcamentoCancelado(orcamento.getId(), pagamentoCancelado));
    }

    private void responder(Envelope comando, String tipo, Object payload) {
        publicador.publicar(Filas.OS_SAGA_REPLIES, fabrica.resposta(comando, tipo, payload));
    }

    // Decisao e evento espontaneo (acao do cliente), nao resposta a comando: causationId nulo.
    private void decidido(String tipo, Orcamento orcamento, CanalDecisao canal) {
        publicador.publicar(Filas.OS_SAGA_REPLIES, fabrica.novo(tipo, orcamento.getSagaId(), orcamento.getOrdemDeServicoId(),
                new MensagensOrcamento.OrcamentoDecidido(orcamento.getId(), canal.name(), Instant.now())));
    }

    /** Contrato: dinheiro com duas casas ("530.00"). */
    private static BigDecimal dinheiro(BigDecimal valor) {
        return (valor == null ? BigDecimal.ZERO : valor).setScale(2, RoundingMode.HALF_UP);
    }
}
