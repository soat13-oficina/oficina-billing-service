package br.com.oficina.billing.orcamento.infrastructure.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.billing.orcamento.application.usecase.DecidirOrcamentoExternamenteUseCase;
import br.com.oficina.billing.orcamento.infrastructure.web.request.DecisaoOrcamentoRequest;
import br.com.oficina.billing.orcamento.infrastructure.web.response.DecisaoOrcamentoResponse;

@RestController
@RequestMapping("/integracoes/orcamentos")
public class WebhookDecisaoOrcamentoController implements WebhookDecisaoOrcamentoControllerSwagger {
    private static final Logger log = LoggerFactory.getLogger(WebhookDecisaoOrcamentoController.class);

    private final DecidirOrcamentoExternamenteUseCase decidirOrcamentoExternamenteUseCase;

    public WebhookDecisaoOrcamentoController(DecidirOrcamentoExternamenteUseCase decidirOrcamentoExternamenteUseCase) {
        this.decidirOrcamentoExternamenteUseCase = decidirOrcamentoExternamenteUseCase;
    }

    @Override
    @PostMapping("/{numeroOrcamento}/decisao")
    public ResponseEntity<DecisaoOrcamentoResponse> decidir(
            @PathVariable String numeroOrcamento,
            @RequestBody DecisaoOrcamentoRequest request) {
        log.info("Recebida decisao externa de orcamento. numeroOrcamento={}", numeroOrcamento);
        return ResponseEntity.ok(DecisaoOrcamentoResponse.from(
                decidirOrcamentoExternamenteUseCase.decidir(request.toCommand(numeroOrcamento))));
    }
}
