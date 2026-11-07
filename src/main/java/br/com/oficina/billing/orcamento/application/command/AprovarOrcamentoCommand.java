package br.com.oficina.billing.orcamento.application.command;

import java.util.UUID;

import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;

/**
 * @param clienteSolicitante cliente autenticado que decide (token da Lambda); nulo quando quem decide
 *                           e um funcionario. Cliente so decide o proprio orcamento.
 */
public record AprovarOrcamentoCommand(String numeroOrcamento, CanalDecisao canal, UUID clienteSolicitante) {

    public AprovarOrcamentoCommand(String numeroOrcamento, CanalDecisao canal) {
        this(numeroOrcamento, canal, null);
    }

    public AprovarOrcamentoCommand(String numeroOrcamento) {
        this(numeroOrcamento, CanalDecisao.FUNCIONARIO, null);
    }
}
