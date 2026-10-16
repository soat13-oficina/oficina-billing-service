package br.com.oficina.billing.orcamento.application.saga;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Tipos e payloads das mensagens da Saga do lado do orcamento (futuro Billing Service), como em
 * {@code docs/contratos/mensagens.md}. Copia propria do modulo: quando o Billing for extraido, leva
 * esta classe junto - o que o une ao OS Service e o contrato, nao uma classe compartilhada.
 */
public final class MensagensOrcamento {

    // Comandos recebidos do orquestrador
    public static final String GERAR_ORCAMENTO = "GerarOrcamento";
    public static final String REVERTER_APROVACAO = "ReverterAprovacao";
    public static final String CANCELAR_ORCAMENTO = "CancelarOrcamento";

    // Eventos publicados
    public static final String ORCAMENTO_GERADO = "OrcamentoGerado";
    public static final String ORCAMENTO_FALHOU = "OrcamentoFalhou";
    public static final String ORCAMENTO_APROVADO = "OrcamentoAprovado";
    public static final String ORCAMENTO_REJEITADO = "OrcamentoRejeitado";
    public static final String APROVACAO_REVERTIDA = "AprovacaoRevertida";
    public static final String ORCAMENTO_CANCELADO = "OrcamentoCancelado";

    private MensagensOrcamento() {
    }

    // ------------------------------------------------------------------ comandos

    public record Cliente(UUID id, String nome, String documento, String tipo, String email) {
    }

    public record Veiculo(String placa, String marca, String modelo, Integer ano) {
    }

    public record Funcionario(UUID id, String nome) {
    }

    public record Servico(String descricao, BigDecimal valorMaoDeObra) {
    }

    public record ItemPeca(String pecaInsumoId, int quantidade) {
    }

    /** {@code valorMaoDeObra}, {@code desconto}, {@code validade} e {@code observacoes} sao opcionais (Passo 1). */
    public record GerarOrcamento(
            String numeroOrdemServico,
            Cliente cliente,
            Veiculo veiculo,
            Funcionario funcionario,
            String descricaoDiagnostico,
            List<Servico> servicos,
            List<ItemPeca> pecas,
            BigDecimal valorMaoDeObra,
            BigDecimal desconto,
            Instant validade,
            String observacoes) {
    }

    public record PecaFaltante(String pecaInsumoId, int solicitada, int disponivel) {
    }

    public record ReverterAprovacao(UUID orcamentoId, String motivo, List<PecaFaltante> pecasFaltantes) {
    }

    public record CancelarOrcamento(UUID orcamentoId, String motivo) {
    }

    // ------------------------------------------------------------------ eventos

    public record OrcamentoGerado(
            UUID orcamentoId,
            String numeroOrcamento,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorMaoDeObra,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorPecas,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal desconto,
            @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal valorTotal,
            Instant validade) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrcamentoFalhou(String motivo, List<String> pecasInvalidas) {
    }

    public record OrcamentoDecidido(UUID orcamentoId, String canal, Instant decididoEm) {
    }

    public record AprovacaoRevertida(UUID orcamentoId) {
    }

    public record OrcamentoCancelado(UUID orcamentoId, boolean pagamentoCancelado) {
    }
}
