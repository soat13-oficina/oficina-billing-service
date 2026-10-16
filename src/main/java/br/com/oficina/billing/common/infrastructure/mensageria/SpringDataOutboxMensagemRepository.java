package br.com.oficina.billing.common.infrastructure.mensageria;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpringDataOutboxMensagemRepository extends JpaRepository<OutboxMensagemEntity, UUID> {

    /** Pendentes que ainda nao esgotaram as tentativas, na ordem em que foram gravadas. */
    List<OutboxMensagemEntity> findByPublicadaEmIsNullAndTentativasLessThanOrderByCriadaEmAscIdAsc(int maxTentativas, Limit limite);

    long countByPublicadaEmIsNull();

    /** Ultima mensagem de um tipo para a OS - base do reenvio de comandos. */
    Optional<OutboxMensagemEntity> findFirstByGroupIdAndTipoOrderByCriadaEmDesc(String groupId, String tipo);
}
