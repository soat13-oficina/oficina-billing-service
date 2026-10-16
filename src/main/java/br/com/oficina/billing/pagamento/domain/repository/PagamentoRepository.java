package br.com.oficina.billing.pagamento.domain.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.oficina.billing.pagamento.domain.model.Pagamento;

public interface PagamentoRepository {

    Pagamento salvar(Pagamento pagamento);

    Optional<Pagamento> buscarPorId(UUID id);

    /** Cobrancas do orcamento, da mais recente para a mais antiga. */
    List<Pagamento> buscarPorOrcamento(UUID orcamentoId);

    /** Pendentes com prazo vencido, para o expirador. */
    List<Pagamento> buscarPendentesVencidos(LocalDateTime agora);
}
