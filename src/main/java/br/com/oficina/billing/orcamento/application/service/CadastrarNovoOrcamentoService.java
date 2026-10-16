package br.com.oficina.billing.orcamento.application.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.CadastrarNovoOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.application.port.CatalogoPecas;
import br.com.oficina.billing.orcamento.application.usecase.CadastrarNovoOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Service
public class CadastrarNovoOrcamentoService implements CadastrarNovoOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(CadastrarNovoOrcamentoService.class);

    private final OrcamentoRepository orcamentoRepository;
    private final CatalogoPecas catalogoPecas;

    public CadastrarNovoOrcamentoService(OrcamentoRepository orcamentoRepository, CatalogoPecas catalogoPecas) {
        this.orcamentoRepository = orcamentoRepository;
        this.catalogoPecas = catalogoPecas;
    }

    /**
     * @throws PecasNaoEncontradasException com todas as pecas que nao existem no catalogo
     * @throws RegraDeNegocioException      dado do orcamento invalido
     */
    @Override
    public UUID cadastrarNovoOrcamento(CadastrarNovoOrcamentoCommand command) {
        log.info("Iniciando cadastro de orcamento. numeroOrcamento={}, clienteId={}, ordemDeServicoId={}",
                command.numeroOrcamento(), command.cliente().id(), command.ordemDeServicoId());
        if (orcamentoRepository.buscarPorNumeroOrcamento(command.numeroOrcamento()).isPresent()) {
            throw new RegraDeNegocioException("Ja existe orcamento cadastrado com o mesmo numero.");
        }
        // Cardinalidade 1:1 com a ordem de servico: no maximo um orcamento por OS (FR-004).
        if (orcamentoRepository.buscarPorOrdemDeServicoId(command.ordemDeServicoId()).isPresent()) {
            throw new RegraDeNegocioException("Ja existe orcamento para a ordem de servico informada.");
        }
        if (command.valorMaoDeObra() == null || command.valorMaoDeObra().signum() < 0) {
            throw new RegraDeNegocioException("Valor da mao de obra e obrigatorio e nao pode ser negativo.");
        }
        if (command.validade() == null) {
            throw new RegraDeNegocioException("Validade do orcamento e obrigatoria.");
        }

        List<PecaOrcamento> pecasOrcamento = resolverPecas(command.pecasPrevistas());

        Orcamento orcamento = new Orcamento(
                command.numeroOrcamento(),
                command.cliente().id(),
                command.ordemDeServicoId(),
                command.funcionarioId(),
                command.cliente().nome(),
                command.cliente().documento(),
                command.placaVeiculo(),
                command.marcaVeiculo(),
                command.modeloVeiculo(),
                command.descricaoDiagnostico(),
                command.servicosPropostos(),
                pecasOrcamento,
                command.valorMaoDeObra(),
                command.desconto(),
                LocalDateTime.now(),
                command.validade(),
                command.observacoes(),
                StatusOrcamento.AGUARDANDO_APROVACAO);

        // Desconto nao pode tornar o total negativo (edge case da spec 014).
        if (orcamento.getValorTotal() != null && orcamento.getValorTotal().signum() < 0) {
            throw new RegraDeNegocioException("Desconto nao pode ser maior que o valor total do orcamento.");
        }

        orcamento.vincularSaga(command.sagaId());
        Orcamento salvo = orcamentoRepository.salvar(orcamento);
        log.info("Orcamento cadastrado com sucesso. numeroOrcamento={}", command.numeroOrcamento());
        return salvo.getId();
    }

    private List<PecaOrcamento> resolverPecas(List<PecaOrcamentoInput> inputs) {
        if (inputs == null || inputs.isEmpty()) {
            return new ArrayList<>();
        }
        if (inputs.stream().anyMatch(input -> input.quantidade() <= 0)) {
            throw new RegraDeNegocioException("A quantidade da peca deve ser maior que zero.");
        }
        Map<String, CatalogoPecas.PecaCatalogo> catalogo =
                catalogoPecas.consultar(inputs.stream().map(PecaOrcamentoInput::pecaInsumoId).toList());
        List<String> inexistentes = inputs.stream()
                .map(PecaOrcamentoInput::pecaInsumoId)
                .filter(id -> !catalogo.containsKey(id))
                .distinct()
                .toList();
        if (!inexistentes.isEmpty()) {
            throw new PecasNaoEncontradasException(inexistentes);
        }
        return inputs.stream()
                .map(input -> {
                    CatalogoPecas.PecaCatalogo peca = catalogo.get(input.pecaInsumoId());
                    return new PecaOrcamento(peca.pecaInsumoId(), peca.descricao(), peca.preco(), input.quantidade());
                })
                .toList();
    }
}
