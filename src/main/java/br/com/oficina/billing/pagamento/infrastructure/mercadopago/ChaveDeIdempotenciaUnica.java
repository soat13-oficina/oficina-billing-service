package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPRequest;
import com.mercadopago.net.MPResponse;

/**
 * O SDK 3.7.0 sempre gera a propria {@code X-Idempotency-Key} e acrescenta a nossa em minusculas: a
 * requisicao sairia com duas chaves diferentes (achado do spike). Quando a nossa existe, a gerada sai -
 * e e a nossa, guardada em {@code pagamentos.idempotency_key}, que o Mercado Pago ve em toda tentativa.
 */
class ChaveDeIdempotenciaUnica implements MPHttpClient {

    static final String NOSSA = "x-idempotency-key";
    private static final String GERADA_PELO_SDK = "X-Idempotency-Key";

    private final MPHttpClient http;

    ChaveDeIdempotenciaUnica(MPHttpClient http) {
        this.http = http;
    }

    @Override
    public MPResponse send(MPRequest request) throws MPException, MPApiException {
        if (request.getHeaders() != null && request.getHeaders().containsKey(NOSSA)) {
            request.getHeaders().remove(GERADA_PELO_SDK);
        }
        return http.send(request);
    }
}
