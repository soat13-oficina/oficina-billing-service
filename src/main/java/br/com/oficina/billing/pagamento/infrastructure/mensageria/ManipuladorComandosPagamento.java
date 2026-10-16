package br.com.oficina.billing.pagamento.infrastructure.mensageria;

import java.util.Set;

import org.springframework.stereotype.Component;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import br.com.oficina.billing.pagamento.application.saga.MensagensPagamento;
import br.com.oficina.billing.pagamento.application.saga.PagamentosPelaSaga;

/** Comandos do orquestrador ({@code billing-commands}) que o modulo de pagamento atende. */
@Component
public class ManipuladorComandosPagamento implements ManipuladorMensagem {

    private final PagamentosPelaSaga pagamentos;

    public ManipuladorComandosPagamento(PagamentosPelaSaga pagamentos) {
        this.pagamentos = pagamentos;
    }

    @Override
    public Set<String> tipos() {
        return Set.of(MensagensPagamento.SOLICITAR_PAGAMENTO, MensagensPagamento.ESTORNAR_PAGAMENTO);
    }

    @Override
    public void tratar(Envelope envelope) {
        if (MensagensPagamento.SOLICITAR_PAGAMENTO.equals(envelope.type())) {
            pagamentos.solicitar(envelope);
        } else {
            pagamentos.estornar(envelope);
        }
    }
}
