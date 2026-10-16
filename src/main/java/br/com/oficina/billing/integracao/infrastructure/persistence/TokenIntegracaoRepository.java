package br.com.oficina.billing.integracao.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import br.com.oficina.billing.integracao.domain.model.StatusTokenIntegracao;
import br.com.oficina.billing.integracao.domain.model.TokenIntegracao;

@Repository
public interface TokenIntegracaoRepository extends JpaRepository<TokenIntegracao, UUID> {

    Optional<TokenIntegracao> findByHashTokenAndStatus(String hashToken, StatusTokenIntegracao status);
}
