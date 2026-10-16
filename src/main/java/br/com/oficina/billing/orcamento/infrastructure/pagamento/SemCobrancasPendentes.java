package br.com.oficina.billing.orcamento.infrastructure.pagamento;

import java.util.UUID;

import org.springframework.stereotype.Component;

import br.com.oficina.billing.orcamento.application.port.PagamentosDoOrcamento;

/**
 * Enquanto o modulo de pagamento nao chega ao Billing (proxima entrega), cancelar o orcamento nao tem
 * cobranca para levar junto. Sai quando {@code pagamento} implementar a porta.
 */
@Component
public class SemCobrancasPendentes implements PagamentosDoOrcamento {

    @Override
    public boolean cancelarPendente(UUID orcamentoId) {
        return false;
    }
}
