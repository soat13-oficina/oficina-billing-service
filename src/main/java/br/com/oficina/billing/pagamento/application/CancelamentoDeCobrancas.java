package br.com.oficina.billing.pagamento.application;

import java.time.LocalDateTime;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import br.com.oficina.billing.orcamento.application.port.PagamentosDoOrcamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;

/** O orcamento cancelado pela Saga leva junto a cobranca pendente (mesmo servico: Billing). */
@Component
public class CancelamentoDeCobrancas implements PagamentosDoOrcamento {
    private static final Logger log = LoggerFactory.getLogger(CancelamentoDeCobrancas.class);

    private final PagamentoRepository pagamentos;

    public CancelamentoDeCobrancas(PagamentoRepository pagamentos) {
        this.pagamentos = pagamentos;
    }

    @Override
    public boolean cancelarPendente(UUID orcamentoId) {
        LocalDateTime agora = LocalDateTime.now();
        boolean cancelou = false;
        for (Pagamento pagamento : pagamentos.buscarPorOrcamento(orcamentoId)) {
            if (pagamento.cancelar(agora)) {
                pagamentos.salvar(pagamento);
                log.info("Cobranca pendente cancelada com o orcamento. pagamentoId={}", pagamento.getId());
                cancelou = true;
            }
        }
        return cancelou;
    }
}
