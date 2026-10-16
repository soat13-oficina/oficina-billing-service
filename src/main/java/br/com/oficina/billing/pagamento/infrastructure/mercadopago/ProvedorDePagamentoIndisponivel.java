package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

/**
 * Falha transitoria do Mercado Pago (rede, 5xx, 429). Sobe: o comando volta para a fila ou o webhook
 * e reenviado pelo proprio Mercado Pago, e a operacao e repetida com a mesma chave de idempotencia.
 */
public class ProvedorDePagamentoIndisponivel extends RuntimeException {

    public ProvedorDePagamentoIndisponivel(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
