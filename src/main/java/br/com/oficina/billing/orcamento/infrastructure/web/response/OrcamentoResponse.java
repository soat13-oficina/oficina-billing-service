package br.com.oficina.billing.orcamento.infrastructure.web.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OrcamentoResponse", description = "Representação de um orçamento")
public record OrcamentoResponse(
        UUID id,
        String numeroOrcamento,
        UUID ordemDeServicoId,
        UUID funcionarioOrigemId,
        ClienteOrcamentoResponse cliente,
        VeiculoOrcamentoResponse veiculo,
        DetalhesServicoResponse detalhesServico,
        String status,
        LocalDateTime criadoEm,
        LocalDateTime enviadoParaAprovacaoEm) {

    @Override
    @Schema(description = "Identificador da ordem de serviço vinculada (1:1)")
    public UUID ordemDeServicoId() {
        return ordemDeServicoId;
    }

    @Override
    @Schema(description = "Identificador do funcionário de origem (quem abriu o orçamento)")
    public UUID funcionarioOrigemId() {
        return funcionarioOrigemId;
    }

    @Override
    @Schema(description = "Identificador UUID do orçamento", example = "55555555-5555-5555-5555-555555555555")
    public UUID id() {
        return id;
    }

    @Override
    @Schema(description = "Número do orçamento", example = "ORC-2026-0001")
    public String numeroOrcamento() {
        return numeroOrcamento;
    }

    @Override
    @Schema(description = "Dados do cliente vinculado")
    public ClienteOrcamentoResponse cliente() {
        return cliente;
    }

    @Override
    @Schema(description = "Dados do veículo vinculado")
    public VeiculoOrcamentoResponse veiculo() {
        return veiculo;
    }

    @Override
    @Schema(description = "Detalhes técnicos e financeiros do orçamento")
    public DetalhesServicoResponse detalhesServico() {
        return detalhesServico;
    }

    @Override
    @Schema(description = "Status atual do orçamento", example = "AGUARDANDO_APROVACAO")
    public String status() {
        return status;
    }

    @Override
    @Schema(description = "Data e hora de criação do orçamento", example = "2030-01-01T10:00:00")
    public LocalDateTime criadoEm() {
        return criadoEm;
    }

    @Override
    @Schema(description = "Data e hora de envio para aprovação", example = "2030-01-01T11:00:00")
    public LocalDateTime enviadoParaAprovacaoEm() {
        return enviadoParaAprovacaoEm;
    }

    public static OrcamentoResponse from(Orcamento orcamento) {
        return new OrcamentoResponse(
                orcamento.getId(),
                orcamento.getNumeroOrcamento(),
                orcamento.getOrdemDeServicoId(),
                orcamento.getFuncionarioId(),
                new ClienteOrcamentoResponse(orcamento.getClienteNome(), orcamento.getClienteCpf()),
                new VeiculoOrcamentoResponse(orcamento.getPlacaVeiculo(), orcamento.getMarcaVeiculo(), orcamento.getModeloVeiculo()),
                new DetalhesServicoResponse(
                        orcamento.getDescricaoDiagnostico(),
                        orcamento.getServicosPropostos(),
                        orcamento.getPecasOrcamento().stream().map(PecaResponse::from).toList(),
                        orcamento.getValorMaoDeObra(),
                        orcamento.getValorPecas(),
                        orcamento.getValorTotal(),
                        orcamento.getDesconto(),
                        orcamento.getValidade(),
                        orcamento.getObservacoes()),
                orcamento.getStatus().name(),
                orcamento.getCriadoEm(),
                orcamento.getEnviadoParaAprovacaoEm());
    }

    @Schema(name = "ClienteOrcamentoResponse", description = "Dados do cliente vinculados ao orçamento")
    public record ClienteOrcamentoResponse(String nome, String cpf) {
        @Override
        @Schema(description = "Nome do cliente", example = "Maria da Silva")
        public String nome() {
            return nome;
        }

        @Override
        @Schema(description = "CPF do cliente", example = "12345678909")
        public String cpf() {
            return cpf;
        }
    }

    @Schema(name = "VeiculoOrcamentoResponse", description = "Dados do veículo vinculados ao orçamento")
    public record VeiculoOrcamentoResponse(String placa, String marca, String modelo) {
        @Override
        @Schema(description = "Placa do veículo", example = "ABC1D23")
        public String placa() {
            return placa;
        }

        @Override
        @Schema(description = "Marca do veículo", example = "Toyota")
        public String marca() {
            return marca;
        }

        @Override
        @Schema(description = "Modelo do veículo", example = "Corolla")
        public String modelo() {
            return modelo;
        }
    }

    @Schema(name = "DetalhesServicoResponse", description = "Detalhes técnicos e financeiros do orçamento")
    public record DetalhesServicoResponse(
            String descricaoDiagnostico,
            List<String> servicosPropostos,
            List<PecaResponse> pecasPrevistas,
            BigDecimal valorMaoDeObra,
            BigDecimal valorPecas,
            BigDecimal valorTotal,
            BigDecimal desconto,
            LocalDateTime validade,
            String observacoes) {
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
        @Schema(description = "Lista de peças previstas com dados completos")
        public List<PecaResponse> pecasPrevistas() {
            return pecasPrevistas;
        }

        @Override
        @Schema(description = "Valor estimado da mão de obra", example = "200.00")
        public BigDecimal valorMaoDeObra() {
            return valorMaoDeObra;
        }

        @Override
        @Schema(description = "Valor estimado das peças", example = "150.00")
        public BigDecimal valorPecas() {
            return valorPecas;
        }

        @Override
        @Schema(description = "Valor total do orçamento", example = "350.00")
        public BigDecimal valorTotal() {
            return valorTotal;
        }

        @Override
        @Schema(description = "Valor de desconto aplicado ao orçamento", example = "25.00")
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
    }

    @Schema(name = "PecaResponse", description = "Peça prevista no orçamento vinculada ao estoque")
    public record PecaResponse(String pecaInsumoId, String descricao, BigDecimal precoUnitario, int quantidade) {
        @Override
        @Schema(description = "Identificador da peça/insumo no cadastro", example = "abc123")
        public String pecaInsumoId() {
            return pecaInsumoId;
        }

        @Override
        @Schema(description = "Descrição da peça prevista", example = "Pastilha de freio dianteira")
        public String descricao() {
            return descricao;
        }

        @Override
        @Schema(description = "Preço unitário da peça prevista", example = "189.90")
        public BigDecimal precoUnitario() {
            return precoUnitario;
        }

        @Override
        @Schema(description = "Quantidade prevista da peça", example = "2")
        public int quantidade() {
            return quantidade;
        }

        static PecaResponse from(PecaOrcamento peca) {
            return new PecaResponse(peca.getPecaInsumoId(), peca.getDescricao(), peca.getPreco(), peca.getQuantidade());
        }
    }
}
