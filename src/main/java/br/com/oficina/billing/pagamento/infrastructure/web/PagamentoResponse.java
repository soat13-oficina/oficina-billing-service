package br.com.oficina.billing.pagamento.infrastructure.web;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "PagamentoResponse", description = "Cobrança de um orçamento aprovado")
public record PagamentoResponse(
        UUID pagamentoId,
        UUID orcamentoId,
        UUID ordemServicoId,
        @Schema(example = "530.00") BigDecimal valor,
        @Schema(example = "PENDENTE") StatusPagamento status,
        @Schema(description = "Link para o cliente pagar (simulado até o Mercado Pago entrar)") String linkPagamento,
        @Schema(description = "Prazo para pagar; depois disso a cobrança expira e a OS é cancelada") LocalDateTime expiraEm,
        @Schema(description = "Motivo da recusa, expiração, cancelamento ou estorno") String motivo,
        @Schema(description = "Id do pagamento no provedor") String referenciaPagamento,
        @Schema(description = "Id do estorno no provedor") String referenciaEstorno,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm) {

    public static PagamentoResponse from(Pagamento pagamento) {
        return new PagamentoResponse(
                pagamento.getId(),
                pagamento.getOrcamentoId(),
                pagamento.getOrdemDeServicoId(),
                pagamento.getValor(),
                pagamento.getStatus(),
                pagamento.getLinkPagamento(),
                pagamento.getExpiraEm(),
                pagamento.getMotivo(),
                pagamento.getMpPaymentId(),
                pagamento.getMpRefundId(),
                pagamento.getCriadoEm(),
                pagamento.getAtualizadoEm());
    }
}
