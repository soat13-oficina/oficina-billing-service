package br.com.oficina.billing.pagamento.infrastructure.gateway;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import br.com.oficina.billing.pagamento.application.port.GatewayPagamento;
import br.com.oficina.billing.pagamento.domain.model.Pagamento;

/**
 * Gateway de pagamento simulado, para o desenvolvimento local sem credenciais do Mercado Pago
 * ({@code pagamento.gateway=simulado}): nao fala com ninguem. A cobranca ganha um link ficticio e o
 * "pagamento" acontece por {@code POST /pagamentos/{id}/simulacao}, que faz o papel do webhook.
 * Estornos sempre dao certo.
 */
@Component
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "simulado")
public class GatewayPagamentoSimulado implements GatewayPagamento {
    private static final Logger log = LoggerFactory.getLogger(GatewayPagamentoSimulado.class);

    private final String linkBase;

    public GatewayPagamentoSimulado(@Value("${pagamento.simulado.link-base:https://pagamento-simulado.oficina.local/checkout}") String linkBase) {
        this.linkBase = linkBase;
    }

    @Override
    public Cobranca criarCobranca(Pagamento pagamento, String descricao) {
        String referencia = "SIM-PREF-" + pagamento.getId();
        log.info("Cobranca simulada criada. pagamentoId={}, valor={}, descricao={}", pagamento.getId(), pagamento.getValor(), descricao);
        return new Cobranca(referencia, linkBase + "/" + pagamento.getId());
    }

    @Override
    public Estorno estornar(Pagamento pagamento) {
        String referencia = "SIM-REFUND-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Estorno simulado realizado. pagamentoId={}, valor={}, estorno={}", pagamento.getId(), pagamento.getValor(), referencia);
        return new Estorno(true, referencia, null);
    }

    /** O resultado chega pela simulacao, direto no servico: o provedor simulado nao tem o que consultar. */
    @Override
    public Optional<SituacaoNoProvedor> consultarPagamento(String referenciaPagamento) {
        return Optional.empty();
    }

    @Override
    public Optional<SituacaoNoProvedor> buscarPorCobranca(Pagamento pagamento) {
        return Optional.empty();
    }
}
