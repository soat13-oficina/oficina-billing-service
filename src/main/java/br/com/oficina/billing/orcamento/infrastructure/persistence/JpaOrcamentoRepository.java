package br.com.oficina.billing.orcamento.infrastructure.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Repository
@Transactional(readOnly = true)
public class JpaOrcamentoRepository implements OrcamentoRepository {
    private final SpringDataOrcamentoRepository repository;

    public JpaOrcamentoRepository(SpringDataOrcamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public Orcamento salvar(Orcamento orcamento) {
        return repository.save(orcamento);
    }

    @Override
    @Transactional
    public void atualizar(Orcamento orcamento) {
        repository.save(orcamento);
    }

    @Override
    @Transactional
    public void excluirPorNumeroOrcamento(String numeroOrcamento) {
        repository.deleteByNumeroOrcamento(numeroOrcamento);
    }

    @Override
    public Optional<Orcamento> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Orcamento> buscarPorNumeroOrcamento(String numeroOrcamento) {
        return repository.findByNumeroOrcamento(numeroOrcamento);
    }

    @Override
    public Optional<Orcamento> buscarPorOrdemDeServicoId(UUID ordemDeServicoId) {
        return repository.findByOrdemDeServicoId(ordemDeServicoId);
    }

    @Override
    public List<Orcamento> buscarPorFiltros(String numeroOrcamento, String cpfCliente, String placaVeiculo) {
        Specification<Orcamento> specification = Specification.where(campoIgual("numeroOrcamento", numeroOrcamento))
                .and(campoIgual("clienteCpf", cpfCliente))
                .and(campoIgual("placaVeiculo", placaVeiculo));
        return repository.findAll(specification);
    }

    @Override
    public List<Orcamento> buscarTodos() {
        return repository.findAll();
    }

    private static <T> Specification<Orcamento> campoIgual(String campo, T valor) {
        return (root, query, criteriaBuilder) -> valor == null ? null : criteriaBuilder.equal(root.get(campo), valor);
    }
}
