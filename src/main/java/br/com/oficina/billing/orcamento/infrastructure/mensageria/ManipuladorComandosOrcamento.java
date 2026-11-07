package br.com.oficina.billing.orcamento.infrastructure.mensageria;

import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import br.com.oficina.billing.orcamento.application.saga.CancelarOrcamentoPelaSaga;
import br.com.oficina.billing.orcamento.application.saga.GerarOrcamentoPelaSaga;
import br.com.oficina.billing.orcamento.application.saga.MensagensOrcamento;
import br.com.oficina.billing.orcamento.application.saga.ReverterAprovacaoPelaSaga;

/** Comandos do orquestrador ({@code billing-commands}) que o modulo de orcamento atende. */
@Component
public class ManipuladorComandosOrcamento implements ManipuladorMensagem {

    private final GerarOrcamentoPelaSaga gerarOrcamento;
    private final ReverterAprovacaoPelaSaga reverterAprovacao;
    private final CancelarOrcamentoPelaSaga cancelarOrcamento;

    public ManipuladorComandosOrcamento(
            GerarOrcamentoPelaSaga gerarOrcamento,
            ReverterAprovacaoPelaSaga reverterAprovacao,
            CancelarOrcamentoPelaSaga cancelarOrcamento) {
        this.gerarOrcamento = gerarOrcamento;
        this.reverterAprovacao = reverterAprovacao;
        this.cancelarOrcamento = cancelarOrcamento;
    }

    @Override
    public Set<String> tipos() {
        return Set.of(MensagensOrcamento.GERAR_ORCAMENTO, MensagensOrcamento.REVERTER_APROVACAO, MensagensOrcamento.CANCELAR_ORCAMENTO);
    }

    @Override
    public void tratar(Envelope envelope) {
        switch (envelope.type()) {
            case MensagensOrcamento.GERAR_ORCAMENTO -> gerarOrcamento.gerar(envelope);
            case MensagensOrcamento.REVERTER_APROVACAO -> reverterAprovacao.reverter(envelope);
            default -> cancelarOrcamento.cancelar(envelope);
        }
    }
}
