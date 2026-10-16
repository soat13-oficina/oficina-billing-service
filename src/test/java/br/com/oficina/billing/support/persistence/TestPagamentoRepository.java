package br.com.oficina.billing.support.persistence;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.repository.PagamentoRepository;

public class TestPagamentoRepository implements PagamentoRepository {

    private final Map<UUID, Pagamento> pagamentos = new ConcurrentHashMap<>();

    @Override
    public Pagamento salvar(Pagamento pagamento) {
        pagamentos.put(pagamento.getId(), pagamento);
        return pagamento;
    }

    @Override
    public Optional<Pagamento> buscarPorId(UUID id) {
        return Optional.ofNullable(pagamentos.get(id));
    }

    @Override
    public List<Pagamento> buscarPorOrcamento(UUID orcamentoId) {
        return pagamentos.values().stream()
                .filter(p -> p.getOrcamentoId().equals(orcamentoId))
                .sorted(Comparator.comparing(Pagamento::getCriadoEm).reversed())
                .toList();
    }

    @Override
    public List<Pagamento> buscarPendentesVencidos(LocalDateTime agora) {
        return pagamentos.values().stream()
                .filter(p -> p.vencido(agora))
                .sorted(Comparator.comparing(Pagamento::getExpiraEm))
                .toList();
    }

    public List<Pagamento> todos() {
        return List.copyOf(pagamentos.values());
    }
}
