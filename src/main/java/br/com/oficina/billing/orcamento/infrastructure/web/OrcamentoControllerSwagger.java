package br.com.oficina.billing.orcamento.infrastructure.web;

import java.util.List;

import br.com.oficina.billing.orcamento.infrastructure.web.request.AlterarOrcamentoRequest;
import br.com.oficina.billing.orcamento.infrastructure.web.response.OrcamentoResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Orcamentos", description = "Operações de cadastro, consulta, alteração, aprovação, rejeição e exclusão de orçamentos")
@SecurityRequirement(name = "bearerAuth")
public interface OrcamentoControllerSwagger {

    @Operation(summary = "Consultar orçamento", description = "Consulta um orçamento pelo número do orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento encontrado",
                    content = @Content(schema = @Schema(implementation = OrcamentoResponse.class))),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content)
    })
    ResponseEntity<OrcamentoResponse> consultar(String orcamentoId);

    @Operation(
            summary = "Consultar orçamentos",
            description = "Consulta orçamentos por número do orçamento, CPF do cliente e/ou placa do veículo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamentos retornados com sucesso",
                    content = @Content(array = @ArraySchema(schema = @Schema(implementation = OrcamentoResponse.class))))
    })
    ResponseEntity<List<OrcamentoResponse>> consultarPorFiltros(
            @Parameter(description = "Número do orçamento para filtro exato.") String numeroOrcamento,
            @Parameter(description = "CPF do cliente vinculado ao orçamento.") String cpfCliente,
            @Parameter(description = "Placa do veículo vinculada ao orçamento.") String placaVeiculo);

    @Operation(
            summary = "Alterar orçamento",
            description = "Atualiza os dados de um orçamento existente a partir do número do orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Orçamento alterado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos para alteração", content = @Content),
            @ApiResponse(responseCode = "404", description = "Orçamento, cliente ou peça/insumo não encontrado", content = @Content)
    })
    ResponseEntity<Void> alterar(String orcamentoId, AlterarOrcamentoRequest request);

    @Operation(
            summary = "Aprovar orçamento",
            description = "Aprova um orçamento aguardando aprovação. As peças previstas são reservadas automaticamente no estoque. Se alguma peça não tiver estoque suficiente, a aprovação falha (400) e o orçamento permanece com status AGUARDANDO_APROVACAO.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orçamento aprovado com sucesso e peças reservadas",
                    content = @Content(schema = @Schema(implementation = OrcamentoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Status inválido para aprovação ou estoque insuficiente", content = @Content),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content)
    })
    ResponseEntity<OrcamentoResponse> aprovar(String orcamentoId);

    @Operation(
            summary = "Rejeitar orçamento",
            description = "Rejeita um orçamento aguardando aprovação. Nenhuma reserva de estoque é afetada.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Orçamento rejeitado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Status inválido para rejeição", content = @Content),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content)
    })
    ResponseEntity<Void> rejeitar(String orcamentoId);

    @Operation(summary = "Excluir orçamento", description = "Remove um orçamento pelo número do orçamento.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Orçamento excluído com sucesso"),
            @ApiResponse(responseCode = "404", description = "Orçamento não encontrado", content = @Content)
    })
    ResponseEntity<Void> excluir(String orcamentoId);
}
