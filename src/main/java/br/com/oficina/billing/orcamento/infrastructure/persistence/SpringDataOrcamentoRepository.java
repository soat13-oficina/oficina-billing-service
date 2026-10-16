package br.com.oficina.billing.orcamento.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface SpringDataOrcamentoRepository extends JpaRepository<Orcamento, UUID>, JpaSpecificationExecutor<Orcamento> {
    Optional<Orcamento> findByNumeroOrcamento(String numeroOrcamento);

    Optional<Orcamento> findByOrdemDeServicoId(UUID ordemDeServicoId);

    void deleteByNumeroOrcamento(String numeroOrcamento);
}
