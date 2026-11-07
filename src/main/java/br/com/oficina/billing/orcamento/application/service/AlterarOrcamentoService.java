package br.com.oficina.billing.orcamento.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.application.port.CatalogoPecas;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import org.springframework.stereotype.Service;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.orcamento.application.command.AlterarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.usecase.AlterarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Service
public class AlterarOrcamentoService implements AlterarOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(AlterarOrcamentoService.class);

    private final OrcamentoRepository orcamentoRepository;
    private final CatalogoPecas catalogoPecas;

    public AlterarOrcamentoService(
            OrcamentoRepository orcamentoRepository,
            CatalogoPecas catalogoPecas) {
        this.orcamentoRepository = orcamentoRepository;
        this.catalogoPecas = catalogoPecas;
    }

    @Override
    public void alterarOrcamento(AlterarOrcamentoCommand command) {
        log.info("Iniciando alteracao de orcamento. numeroOrcamento={}, clienteId={}, ordemDeServicoId={}",
                command.numeroOrcamento(), command.clienteId(), command.ordemDeServicoId());
        Orcamento orcamentoAtual = orcamentoRepository.buscarPorNumeroOrcamento(command.numeroOrcamento())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Orcamento nao encontrado para o numero informado."));
        UUID clienteId = paraUuid(command.clienteId(), "Identificador do cliente invalido.");
        UUID ordemDeServicoId = paraUuid(command.ordemDeServicoId(), "Identificador da ordem de servico invalido.");
        // Funcionário de origem é imutável: preserva o que abriu o orçamento, ignorando o do comando (FR-009).
        UUID funcionarioId = orcamentoAtual.getFuncionarioId();
        // O Billing nao consulta clientes (ADR 0003): o snapshot do cliente vem da OS, no GerarOrcamento,
        // e nao muda numa alteracao do orcamento.
        if (!clienteId.equals(orcamentoAtual.getClienteId())) {
            throw new RegraDeNegocioException("O cliente do orcamento vem da ordem de servico e nao pode ser alterado.");
        }

        List<PecaOrcamento> pecasOrcamento = resolverPecas(command.pecasPrevistas());

        Orcamento orcamentoAtualizado = Orcamento.reconstituir(
                orcamentoAtual.getId(),
                command.numeroOrcamento(),
                orcamentoAtual.getClienteId(),
                ordemDeServicoId,
                funcionarioId,
                orcamentoAtual.getClienteNome(),
                orcamentoAtual.getClienteCpf(),
                command.placaVeiculo(),
                command.marcaVeiculo(),
                command.modeloVeiculo(),
                command.descricaoDiagnostico(),
                command.servicosPropostos(),
                pecasOrcamento,
                command.valorMaoDeObra(),
                command.desconto(),
                orcamentoAtual.getCriadoEm(),
                command.validade(),
                command.observacoes(),
                orcamentoAtual.getStatus());

        if (orcamentoAtual.getEnviadoParaAprovacaoEm() != null) {
            orcamentoAtualizado.enviarParaAprovacao(orcamentoAtual.getEnviadoParaAprovacaoEm());
        }
        orcamentoRepository.atualizar(orcamentoAtualizado);
        log.info("Orcamento alterado com sucesso. numeroOrcamento={}", command.numeroOrcamento());
    }

    private List<PecaOrcamento> resolverPecas(List<PecaOrcamentoInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, CatalogoPecas.PecaCatalogo> catalogo =
                catalogoPecas.consultar(inputs.stream().map(PecaOrcamentoInput::pecaInsumoId).toList());
        List<PecaOrcamento> pecas = new ArrayList<>();
        for (PecaOrcamentoInput input : inputs) {
            CatalogoPecas.PecaCatalogo peca = catalogo.get(input.pecaInsumoId());
            if (peca == null) {
                throw new RecursoNaoEncontradoException("Peca/Insumo nao encontrada com o ID: " + input.pecaInsumoId());
            }
            if (input.quantidade() <= 0) {
                throw new RegraDeNegocioException("A quantidade da peca deve ser maior que zero.");
            }
            pecas.add(new PecaOrcamento(peca.pecaInsumoId(), peca.descricao(), peca.preco(), input.quantidade()));
        }
        return pecas;
    }

    private UUID paraUuid(String valor, String mensagemErro) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException exception) {
            throw new RegraDeNegocioException(mensagemErro);
        }
    }
}
