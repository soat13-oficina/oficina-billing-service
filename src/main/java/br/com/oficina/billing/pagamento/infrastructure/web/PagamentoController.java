package br.com.oficina.billing.pagamento.infrastructure.web;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.billing.common.infrastructure.seguranca.ClienteSolicitante;
import br.com.oficina.billing.pagamento.application.ResultadoPagamentoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Pagamentos", description = "Cobrança do orçamento aprovado (passo 5 da Saga). Gateway simulado até o Mercado Pago (Passo 3)")
public class PagamentoController {
    private static final Logger log = LoggerFactory.getLogger(PagamentoController.class);

    private final ResultadoPagamentoService pagamentos;

    public PagamentoController(ResultadoPagamentoService pagamentos) {
        this.pagamentos = pagamentos;
    }

    @Operation(summary = "Consultar pagamento", description = "Situação de uma cobrança: valor, status, link e prazo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                    content = @Content(schema = @Schema(implementation = PagamentoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Pagamento não encontrado", content = @Content)
    })
    @GetMapping("/pagamentos/{pagamentoId}")
    public ResponseEntity<PagamentoResponse> consultar(@PathVariable UUID pagamentoId) {
        return ResponseEntity.ok(PagamentoResponse.from(pagamentos.consultar(pagamentoId,
                ClienteSolicitante.atual("Pagamento nao encontrado: " + pagamentoId))));
    }

    @Operation(summary = "Consultar o pagamento de um orçamento",
            description = "Cobrança mais recente do orçamento, com o link para pagar. Existe depois que o orçamento "
                    + "é aprovado e as peças são reservadas (a OS fica em 'Aguardando Pagamento').")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagamento encontrado",
                    content = @Content(schema = @Schema(implementation = PagamentoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado ou ainda sem cobrança", content = @Content)
    })
    @GetMapping("/orcamentos/{numeroOrcamento}/pagamento")
    public ResponseEntity<PagamentoResponse> consultarPorOrcamento(@PathVariable String numeroOrcamento) {
        log.info("Recebida consulta de pagamento do orcamento. numeroOrcamento={}", numeroOrcamento);
        return ResponseEntity.ok(PagamentoResponse.from(pagamentos.consultarPorOrcamento(numeroOrcamento,
                ClienteSolicitante.atual("Orcamento nao encontrado para o numero informado."))));
    }
}
