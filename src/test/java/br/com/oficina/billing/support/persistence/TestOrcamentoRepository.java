package br.com.oficina.billing.support.persistence;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

public class TestOrcamentoRepository implements OrcamentoRepository {
    private final Map<UUID, Orcamento> orcamentos = new ConcurrentHashMap<>();

    @Override
    public Orcamento salvar(Orcamento orcamento) {
        Orcamento orcamentoPersistido = orcamento.getId() == null
                ? Orcamento.reconstituir(
                        UUID.randomUUID(),
                        orcamento.getNumeroOrcamento(),
                        orcamento.getClienteId(),
                        orcamento.getOrdemDeServicoId(),
                        orcamento.getFuncionarioId(),
                        orcamento.getClienteNome(),
                        orcamento.getClienteCpf(),
                        orcamento.getPlacaVeiculo(),
                        orcamento.getMarcaVeiculo(),
                        orcamento.getModeloVeiculo(),
                        orcamento.getDescricaoDiagnostico(),
                        orcamento.getServicosPropostos(),
                        orcamento.getPecasOrcamento(),
                        orcamento.getValorMaoDeObra(),
                        orcamento.getDesconto(),
                        orcamento.getCriadoEm(),
                        orcamento.getValidade(),
                        orcamento.getObservacoes(),
                        orcamento.getStatus())
                : orcamento;
        if (orcamento.getEnviadoParaAprovacaoEm() != null) {
            orcamentoPersistido.enviarParaAprovacao(orcamento.getEnviadoParaAprovacaoEm());
        }
        if (orcamento.getSagaId() != null) {
            orcamentoPersistido.vincularSaga(orcamento.getSagaId());
        }
        orcamentos.put(orcamentoPersistido.getId(), orcamentoPersistido);
        return orcamentoPersistido;
    }

    @Override
    public void atualizar(Orcamento orcamento) {
        orcamentos.put(orcamento.getId(), orcamento);
    }

    @Override
    public void excluirPorNumeroOrcamento(String numeroOrcamento) {
        buscarPorNumeroOrcamento(numeroOrcamento).ifPresent(orcamento -> orcamentos.remove(orcamento.getId()));
    }

    @Override
    public Optional<Orcamento> buscarPorId(UUID id) {
        return Optional.ofNullable(orcamentos.get(id));
    }

    @Override
    public Optional<Orcamento> buscarPorNumeroOrcamento(String numeroOrcamento) {
        return orcamentos.values().stream()
                .filter(orcamento -> orcamento.getNumeroOrcamento().equals(numeroOrcamento))
                .findFirst();
    }

    @Override
    public Optional<Orcamento> buscarPorOrdemDeServicoId(UUID ordemDeServicoId) {
        return orcamentos.values().stream()
                .filter(orcamento -> orcamento.getOrdemDeServicoId().equals(ordemDeServicoId))
                .findFirst();
    }

    @Override
    public List<Orcamento> buscarPorFiltros(String numeroOrcamento, String cpfCliente, String placaVeiculo) {
        return orcamentos.values().stream()
                .filter(orcamento -> numeroOrcamento == null
                        || orcamento.getNumeroOrcamento().equalsIgnoreCase(numeroOrcamento))
                .filter(orcamento -> cpfCliente == null
                        || orcamento.getClienteCpf().equalsIgnoreCase(cpfCliente))
                .filter(orcamento -> placaVeiculo == null
                        || orcamento.getPlacaVeiculo().equalsIgnoreCase(placaVeiculo))
                .toList();
    }

    @Override
    public List<Orcamento> buscarTodos() {
        return List.copyOf(orcamentos.values());
    }
}
