package br.com.oficina.billing.orcamento.infrastructure.web;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.oficina.billing.orcamento.application.command.AlterarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.AprovarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.ExcluirOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.RejeitarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.query.ConsultarOrcamentoQuery;
import br.com.oficina.billing.orcamento.application.usecase.AlterarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.AprovarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.ConsultarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.ExcluirOrcamentoUseCase;
import br.com.oficina.billing.orcamento.application.usecase.RejeitarOrcamentoUseCase;
import br.com.oficina.billing.common.infrastructure.seguranca.UsuarioAutenticado;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.infrastructure.web.request.AlterarOrcamentoRequest;
import br.com.oficina.billing.orcamento.infrastructure.web.response.OrcamentoResponse;

@RestController
@RequestMapping("/orcamentos")
public class OrcamentoController implements OrcamentoControllerSwagger {
    private static final Logger log = LoggerFactory.getLogger(OrcamentoController.class);

    private final ConsultarOrcamentoUseCase consultarOrcamentoUseCase;
    private final AlterarOrcamentoUseCase alterarOrcamentoUseCase;
    private final ExcluirOrcamentoUseCase excluirOrcamentoUseCase;
    private final AprovarOrcamentoUseCase aprovarOrcamentoUseCase;
    private final RejeitarOrcamentoUseCase rejeitarOrcamentoUseCase;

    public OrcamentoController(
            ConsultarOrcamentoUseCase consultarOrcamentoUseCase,
            AlterarOrcamentoUseCase alterarOrcamentoUseCase,
            ExcluirOrcamentoUseCase excluirOrcamentoUseCase,
            AprovarOrcamentoUseCase aprovarOrcamentoUseCase,
            RejeitarOrcamentoUseCase rejeitarOrcamentoUseCase) {
        this.consultarOrcamentoUseCase = consultarOrcamentoUseCase;
        this.alterarOrcamentoUseCase = alterarOrcamentoUseCase;
        this.excluirOrcamentoUseCase = excluirOrcamentoUseCase;
        this.aprovarOrcamentoUseCase = aprovarOrcamentoUseCase;
        this.rejeitarOrcamentoUseCase = rejeitarOrcamentoUseCase;
    }


    @GetMapping("/{orcamentoId}")
    public ResponseEntity<OrcamentoResponse> consultar(@PathVariable String orcamentoId) {
        log.info("Recebida requisicao de consulta de orcamento. numeroOrcamento={}", orcamentoId);
        OrcamentoResponse response = consultarOrcamentoUseCase
                .consultarOrcamento(new ConsultarOrcamentoQuery(orcamentoId, null, null))
                .stream()
                .filter(OrcamentoController::visivelParaOUsuario)
                .findFirst()
                .map(OrcamentoResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."));
        log.info("Requisicao de consulta de orcamento concluida. numeroOrcamento={}", orcamentoId);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<OrcamentoResponse>> consultarPorFiltros(
            @RequestParam(required = false) String numeroOrcamento,
            @RequestParam(required = false) String cpfCliente,
            @RequestParam(required = false) String placaVeiculo) {
        log.info("Recebida requisicao de consulta de orcamentos. numeroInformado={}, cpfInformado={}, placaInformada={}",
                numeroOrcamento != null && !numeroOrcamento.isBlank(),
                cpfCliente != null && !cpfCliente.isBlank(),
                placaVeiculo != null && !placaVeiculo.isBlank());
        List<OrcamentoResponse> orcamentos = consultarOrcamentoUseCase
                .consultarOrcamento(new ConsultarOrcamentoQuery(numeroOrcamento, cpfCliente, placaVeiculo))
                .stream()
                .filter(OrcamentoController::visivelParaOUsuario)
                .map(OrcamentoResponse::from)
                .toList();
        log.info("Requisicao de consulta de orcamentos concluida. quantidadeOrcamentos={}", orcamentos.size());
        return ResponseEntity.ok(orcamentos);
    }

    @PutMapping("/{orcamentoId}")
    public ResponseEntity<Void> alterar(
            @PathVariable String orcamentoId,
            @RequestBody AlterarOrcamentoRequest request) {
        log.info("Recebida requisicao de alteracao de orcamento. numeroOrcamento={}, clienteId={}, ordemDeServicoId={}",
                orcamentoId, request.clienteId(), request.ordemDeServicoId());
        alterarOrcamentoUseCase.alterarOrcamento(new AlterarOrcamentoCommand(
                orcamentoId,
                validarClienteId(request.clienteId()),
                request.ordemDeServicoId(),
                request.funcionarioId(),
                request.placaVeiculo(),
                request.marcaVeiculo(),
                request.modeloVeiculo(),
                request.descricaoDiagnostico(),
                request.servicosPropostos(),
                request.toPecasInput(),
                request.valorMaoDeObra(),
                request.desconto(),
                request.validade(),
                request.observacoes()));
        log.info("Requisicao de alteracao de orcamento concluida. numeroOrcamento={}", orcamentoId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{orcamentoId}/aprovacao")
    public ResponseEntity<OrcamentoResponse> aprovar(@PathVariable String orcamentoId) {
        log.info("Recebida requisicao de aprovacao de orcamento. numeroOrcamento={}", orcamentoId);
        OrcamentoResponse response = OrcamentoResponse.from(
                aprovarOrcamentoUseCase.aprovarOrcamento(new AprovarOrcamentoCommand(orcamentoId, canalDoUsuario(), clienteSolicitante())));
        log.info("Requisicao de aprovacao de orcamento concluida. numeroOrcamento={}, status={}", orcamentoId, response.status());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{orcamentoId}/rejeicao")
    public ResponseEntity<Void> rejeitar(@PathVariable String orcamentoId) {
        log.info("Recebida requisicao de rejeicao de orcamento. numeroOrcamento={}", orcamentoId);
        rejeitarOrcamentoUseCase.rejeitarOrcamento(new RejeitarOrcamentoCommand(orcamentoId, canalDoUsuario(), clienteSolicitante()));
        log.info("Requisicao de rejeicao de orcamento concluida. numeroOrcamento={}", orcamentoId);
        return ResponseEntity.noContent().build();
    }

    /** Cliente autenticado pelo CPF (token da Lambda) ou funcionario decidindo por ele. */
    private static CanalDecisao canalDoUsuario() {
        return UsuarioAutenticado.atual().filter(UsuarioAutenticado::isCliente).isPresent()
                ? CanalDecisao.CLIENTE : CanalDecisao.FUNCIONARIO;
    }

    /**
     * Cliente so decide o proprio orcamento (claim {@code clienteId} do token da Lambda). Token de cliente
     * sem {@code clienteId} valido nao enxerga orcamento nenhum.
     */
    private static UUID clienteSolicitante() {
        return UsuarioAutenticado.atual().filter(UsuarioAutenticado::isCliente).map(usuario -> {
            try {
                if (usuario.clienteId() != null) {
                    return UUID.fromString(usuario.clienteId());
                }
            } catch (IllegalArgumentException e) {
                // cai no 404 abaixo
            }
            throw new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado.");
        }).orElse(null);
    }

    private static boolean visivelParaOUsuario(Orcamento orcamento) {
        return UsuarioAutenticado.atual()
                .filter(UsuarioAutenticado::isCliente)
                .map(usuario -> orcamento.getClienteId().toString().equals(usuario.clienteId()))
                .orElse(true);
    }

    @DeleteMapping("/{orcamentoId}")
    public ResponseEntity<Void> excluir(@PathVariable String orcamentoId) {
        log.info("Recebida requisicao de exclusao de orcamento. numeroOrcamento={}", orcamentoId);
        excluirOrcamentoUseCase.excluirOrcamento(new ExcluirOrcamentoCommand(orcamentoId));
        log.info("Requisicao de exclusao de orcamento concluida. numeroOrcamento={}", orcamentoId);
        return ResponseEntity.noContent().build();
    }

    private String validarClienteId(String clienteId) {
        try {
            return UUID.fromString(clienteId).toString();
        } catch (IllegalArgumentException exception) {
            throw new RegraDeNegocioException("Identificador do cliente invalido.");
        }
    }
}
