package br.com.oficina.billing.orcamento.application.port;

import java.util.UUID;

/**
 * Cobrancas do orcamento (modulo pagamento, mesmo servico: Billing). O cancelamento do orcamento pela
 * Saga cancela tambem a cobranca pendente, para um pagamento tardio nao ser aceito.
 */
public interface PagamentosDoOrcamento {

    /** Sem modulo de pagamento (testes de unidade): nada a cancelar. */
    PagamentosDoOrcamento NENHUM = orcamentoId -> false;

    /** @return {@code true} se havia cobranca pendente e ela foi cancelada */
    boolean cancelarPendente(UUID orcamentoId);
}
