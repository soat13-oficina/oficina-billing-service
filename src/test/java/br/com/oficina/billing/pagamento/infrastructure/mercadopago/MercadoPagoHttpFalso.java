package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import com.mercadopago.exceptions.MPApiException;
import com.mercadopago.exceptions.MPException;
import com.mercadopago.net.MPHttpClient;
import com.mercadopago.net.MPRequest;
import com.mercadopago.net.MPResponse;

/**
 * Duble do cliente HTTP do SDK do Mercado Pago: guarda as requisicoes e devolve as respostas
 * enfileiradas (status + JSON). O SDK continua montando requisicao e interpretando resposta.
 */
public class MercadoPagoHttpFalso implements MPHttpClient {

    public final List<MPRequest> recebidas = new ArrayList<>();
    private final Deque<MPResponse> respostas = new ArrayDeque<>();
    private boolean semRede;

    public MercadoPagoHttpFalso responder(int status, String json) {
        respostas.add(new MPResponse(status, Map.of(), json));
        return this;
    }

    public MercadoPagoHttpFalso semRede() {
        this.semRede = true;
        return this;
    }

    public void limpar() {
        recebidas.clear();
        respostas.clear();
        semRede = false;
    }

    @Override
    public MPResponse send(MPRequest request) throws MPException, MPApiException {
        recebidas.add(request);
        if (semRede) {
            throw new MPException("Connection refused");
        }
        MPResponse resposta = respostas.poll();
        if (resposta == null) {
            throw new IllegalStateException("Nenhuma resposta enfileirada para " + request.getUri());
        }
        // Como o MPDefaultHttpClient: status acima de 299 vira MPApiException (o client do SDK nao confere).
        if (resposta.getStatusCode() > 299) {
            throw new MPApiException("Api error. Check response for details", resposta);
        }
        return resposta;
    }
}
