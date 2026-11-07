package br.com.oficina.billing.pagamento.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;

@Repository
public class JpaPagamentoRepository implements PagamentoRepository {

    private final SpringDataPagamentoRepository repository;

    public JpaPagamentoRepository(SpringDataPagamentoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        return repository.save(pagamento);
    }

    @Override
    public Optional<Pagamento> buscarPorId(UUID id) {
        return repository.findById(id);
    }

    @Override
    public List<Pagamento> buscarPorOrcamento(UUID orcamentoId) {
        return repository.findByOrcamentoIdOrderByCriadoEmDesc(orcamentoId);
    }

    @Override
    public List<Pagamento> buscarPendentesVencidos(LocalDateTime agora) {
        return repository.findByStatusAndExpiraEmBeforeOrderByExpiraEm(StatusPagamento.PENDENTE, agora);
    }
}
