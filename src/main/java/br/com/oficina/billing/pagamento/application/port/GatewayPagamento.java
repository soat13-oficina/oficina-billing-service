package br.com.oficina.billing.pagamento.application.port;

import java.util.Optional;
import java.util.UUID;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/**
 * Provedor de pagamento (ADR 0007): Mercado Pago Checkout Pro ({@code GatewayPagamentoMercadoPago})
 * ou o simulado do desenvolvimento local ({@code pagamento.gateway=simulado}).
 */
public interface GatewayPagamento {

    /** Cria a cobranca (preferencia, com a {@code idempotencyKey} da cobranca) e devolve o link para pagar. */
    Cobranca criarCobranca(Pagamento pagamento, String descricao);

    /** Devolve o valor ao cliente. Recusa do provedor nao e excecao: vem em {@link Estorno#realizado()}. */
    Estorno estornar(Pagamento pagamento);

    /**
     * Situacao de um pagamento no provedor, pelo id que o webhook informou. O corpo do webhook nunca e
     * confiavel: o resultado vem sempre desta consulta. Vazio = provedor nao conhece o id.
     */
    Optional<SituacaoNoProvedor> consultarPagamento(String referenciaPagamento);

    /**
     * Pagamento feito para a cobranca ({@code external_reference}), se houver - consultado antes de
     * expirar, para nao expirar um pagamento aprovado cujo webhook atrasou.
     */
    Optional<SituacaoNoProvedor> buscarPorCobranca(Pagamento pagamento);

    /** @param referenciaExterna id da preferencia no provedor */
    record Cobranca(String referenciaExterna, String link) {
    }

    /** @param referenciaExterna id do estorno no provedor, quando realizado */
    record Estorno(boolean realizado, String referenciaExterna, String motivoFalha) {
    }

    /**
     * @param pagamentoId         cobranca deste servico ({@code external_reference}); vazio se o provedor nao a trouxe
     * @param referenciaPagamento id do pagamento no provedor
     * @param motivo              detalhe do provedor ({@code status_detail})
     */
    record SituacaoNoProvedor(Optional<UUID> pagamentoId, String referenciaPagamento, ResultadoNoProvedor resultado, String motivo) {
    }

    enum ResultadoNoProvedor {
        APROVADO,
        RECUSADO,
        /** Pendente, em analise ou outro estado que ainda nao decide nada. */
        PENDENTE
    }
}
