package br.com.oficina.billing.orcamento.application.query;

public record ConsultarOrcamentoQuery(
        String numeroOrcamento,
        String cpfCliente,
        String placaVeiculo) {
}
