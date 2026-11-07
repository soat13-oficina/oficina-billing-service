package br.com.oficina.billing.pagamento.infrastructure.persistence;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;
import br.com.oficina.billing.pagamento.domain.model.StatusPagamento;

public interface SpringDataPagamentoRepository extends JpaRepository<Pagamento, UUID> {

    List<Pagamento> findByOrcamentoIdOrderByCriadoEmDesc(UUID orcamentoId);

    List<Pagamento> findByStatusAndExpiraEmBeforeOrderByExpiraEm(StatusPagamento status, LocalDateTime limite);
}
