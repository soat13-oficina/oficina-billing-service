package br.com.oficina.billing.orcamento.infrastructure.web.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AlterarOrcamentoRequest", description = "Dados necessários para alterar um orçamento")
public record AlterarOrcamentoRequest(
        String clienteId,
        String ordemDeServicoId,
        String funcionarioId,
        String placaVeiculo,
        String marcaVeiculo,
        String modeloVeiculo,
        String descricaoDiagnostico,
        List<String> servicosPropostos,
        List<PecaOrcamentoRequest> pecasPrevistas,
        BigDecimal valorMaoDeObra,
        BigDecimal desconto,
        LocalDateTime validade,
        String observacoes) {
    @Override
    @Schema(description = "Identificador UUID do cliente vinculado", example = "11111111-1111-1111-1111-111111111111")
    public String clienteId() {
        return clienteId;
    }

    @Override
    @Schema(description = "Identificador da ordem de serviço relacionada", example = "33333333-3333-3333-3333-333333333333")
    public String ordemDeServicoId() {
        return ordemDeServicoId;
    }

    @Override
    @Schema(description = "Identificador do funcionário responsável", example = "22222222-2222-2222-2222-222222222222")
    public String funcionarioId() {
        return funcionarioId;
    }

    @Override
    @Schema(description = "Placa do veículo vinculada ao orçamento", example = "ABC1D23")
    public String placaVeiculo() {
        return placaVeiculo;
    }

    @Override
    @Schema(description = "Marca do veículo", example = "Toyota")
    public String marcaVeiculo() {
        return marcaVeiculo;
    }

    @Override
    @Schema(description = "Modelo do veículo", example = "Corolla")
    public String modeloVeiculo() {
        return modeloVeiculo;
    }

    @Override
    @Schema(description = "Descrição do diagnóstico técnico", example = "Troca de óleo e revisão de freios")
    public String descricaoDiagnostico() {
        return descricaoDiagnostico;
    }

    @Override
    @Schema(description = "Lista de serviços propostos", example = "[\"Troca de óleo\", \"Revisão de freios\"]")
    public List<String> servicosPropostos() {
        return servicosPropostos;
    }

    @Override
    @Schema(description = "Lista de peças previstas referenciando o cadastro de peças/insumos")
    public List<PecaOrcamentoRequest> pecasPrevistas() {
        return pecasPrevistas;
    }

    @Override
    @Schema(description = "Valor estimado da mão de obra", example = "200.00")
    public BigDecimal valorMaoDeObra() {
        return valorMaoDeObra;
    }

    @Override
    @Schema(description = "Valor de desconto aplicado ao serviço", example = "25.00")
    public BigDecimal desconto() {
        return desconto;
    }

    @Override
    @Schema(description = "Data de validade do orçamento", example = "2030-01-01T00:00:00")
    public LocalDateTime validade() {
        return validade;
    }

    @Override
    @Schema(description = "Observações adicionais", example = "Peças sujeitas à disponibilidade em estoque")
    public String observacoes() {
        return observacoes;
    }

    public List<PecaOrcamentoInput> toPecasInput() {
        if (pecasPrevistas == null) {
            return List.of();
        }
        return pecasPrevistas.stream()
                .map(p -> new PecaOrcamentoInput(p.pecaInsumoId(), p.quantidade()))
                .toList();
    }

    @Schema(name = "AlterarOrcamentoPecaRequest", description = "Referência a uma peça/insumo cadastrada com quantidade desejada")
    public record PecaOrcamentoRequest(String pecaInsumoId, int quantidade) {
        @Override
        @Schema(description = "Identificador da peça/insumo no cadastro", example = "abc123")
        public String pecaInsumoId() {
            return pecaInsumoId;
        }

        @Override
        @Schema(description = "Quantidade necessária da peça", example = "2")
        public int quantidade() {
            return quantidade;
        }
    }
}
