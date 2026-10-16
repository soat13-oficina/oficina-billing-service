package br.com.oficina.billing.pagamento.infrastructure.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.billing.pagamento.application.ResultadoPagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Faz o papel do cliente pagando no checkout + webhook do provedor. So existe com o gateway simulado
 * ({@code pagamento.gateway=simulado}, desenvolvimento local); com o Mercado Pago o resultado chega
 * por {@code POST /webhooks/mercadopago}.
 */
@RestController
@ConditionalOnProperty(name = "pagamento.gateway", havingValue = "simulado")
@Tag(name = "Pagamentos")
public class SimulacaoPagamentoController {
    private static final Logger log = LoggerFactory.getLogger(SimulacaoPagamentoController.class);

    private final ResultadoPagamentoService pagamentos;

    public SimulacaoPagamentoController(ResultadoPagamentoService pagamentos) {
        this.pagamentos = pagamentos;
    }

    @Schema(name = "SimulacaoPagamentoRequest", description = "Resultado que o provedor de pagamento devolveria")
    public record SimulacaoPagamentoRequest(
            @Schema(description = "APROVADO ou RECUSADO", example = "APROVADO") ResultadoPagamentoService.Resultado resultado,
            @Schema(description = "Motivo da recusa (opcional)", example = "Cartão recusado pelo emissor") String motivo) {
    }

    @Operation(summary = "Simular o resultado do pagamento",
            description = "Aprova ou recusa a cobrança como o webhook do provedor faria. Aprovado: a OS vai para a fila "
                    + "de execução. Recusado: a Saga libera as peças, cancela o orçamento e a OS fica 'Cancelada'. "
                    + "Repetir o mesmo resultado não tem efeito; resultado diferente do já registrado responde 409.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado registrado",
                    content = @Content(schema = @Schema(implementation = PagamentoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pagamento não encontrado", content = @Content),
            @ApiResponse(responseCode = "409", description = "Pagamento já com outro resultado, expirado ou cancelado", content = @Content),
            @ApiResponse(responseCode = "400", description = "Resultado ausente ou inválido", content = @Content)
    })
    @PostMapping("/pagamentos/{pagamentoId}/simulacao")
    public ResponseEntity<PagamentoResponse> simular(@PathVariable UUID pagamentoId, @RequestBody SimulacaoPagamentoRequest request) {
        log.info("Recebida simulacao de pagamento. pagamentoId={}, resultado={}", pagamentoId, request.resultado());
        String referencia = "SIM-PAY-" + UUID.randomUUID().toString().substring(0, 8);
        return ResponseEntity.ok(PagamentoResponse.from(
                pagamentos.registrar(pagamentoId, request.resultado(), referencia, request.motivo())));
    }
}
