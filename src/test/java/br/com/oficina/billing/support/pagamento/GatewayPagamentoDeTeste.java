package br.com.oficina.billing.support.pagamento;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/** Provedor de pagamento dos testes de unidade: respostas configuradas, nada de rede. */
public class GatewayPagamentoDeTeste implements GatewayPagamento {

    public int cobrancas;
    public boolean recusarEstorno;
    private final Map<String, SituacaoNoProvedor> porReferencia = new HashMap<>();
    private final Map<UUID, SituacaoNoProvedor> porCobranca = new HashMap<>();

    /** O provedor passa a conhecer o pagamento {@code referencia}, feito para a cobranca {@code pagamentoId}. */
    public GatewayPagamentoDeTeste pagamentoNoProvedor(String referencia, UUID pagamentoId, ResultadoNoProvedor resultado, String motivo) {
        SituacaoNoProvedor situacao = new SituacaoNoProvedor(Optional.ofNullable(pagamentoId), referencia, resultado, motivo);
        porReferencia.put(referencia, situacao);
        if (pagamentoId != null) {
            porCobranca.put(pagamentoId, situacao);
        }
        return this;
    }

    @Override
    public Cobranca criarCobranca(Pagamento pagamento, String descricao) {
        cobrancas++;
        return new Cobranca("PREF-" + pagamento.getId(), "https://checkout.teste/" + pagamento.getId());
    }

    @Override
    public Estorno estornar(Pagamento pagamento) {
        return recusarEstorno
                ? new Estorno(false, null, "Prazo de estorno do cartao encerrado")
                : new Estorno(true, "REFUND-" + pagamento.getMpPaymentId(), null);
    }

    @Override
    public Optional<SituacaoNoProvedor> consultarPagamento(String referenciaPagamento) {
        return Optional.ofNullable(porReferencia.get(referenciaPagamento));
    }

    @Override
    public Optional<SituacaoNoProvedor> buscarPorCobranca(Pagamento pagamento) {
        return Optional.ofNullable(porCobranca.get(pagamento.getId()));
    }
}
