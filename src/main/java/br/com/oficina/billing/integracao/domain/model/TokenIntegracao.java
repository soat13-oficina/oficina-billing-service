package br.com.oficina.billing.integracao.domain.model;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tokens_integracao")
public class TokenIntegracao {

    protected TokenIntegracao() {
    }

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(nullable = false)
    private String rotulo;

    @Column(name = "hash_token", nullable = false, unique = true)
    private String hashToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusTokenIntegracao status;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "criado_por", nullable = false)
    private String criadoPor;

    @Column(name = "revogado_em")
    private LocalDateTime revogadoEm;

    @Column(name = "revogado_por")
    private String revogadoPor;

    private TokenIntegracao(String rotulo, String hashToken, String criadoPor) {
        this.rotulo = rotulo;
        this.hashToken = hashToken;
        this.criadoPor = criadoPor;
        this.status = StatusTokenIntegracao.ATIVO;
        this.criadoEm = LocalDateTime.now();
    }

    public static TokenIntegracao criar(String rotulo, String hashToken, String criadoPor) {
        return new TokenIntegracao(rotulo, hashToken, criadoPor);
    }

    public void revogar(String revogadoPor) {
        this.status = StatusTokenIntegracao.REVOGADO;
        this.revogadoEm = LocalDateTime.now();
        this.revogadoPor = revogadoPor;
    }

    public boolean estaAtivo() {
        return status == StatusTokenIntegracao.ATIVO;
    }

    public UUID getId() {
        return id;
    }

    public String getRotulo() {
        return rotulo;
    }

    public String getHashToken() {
        return hashToken;
    }

    public StatusTokenIntegracao getStatus() {
        return status;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public String getCriadoPor() {
        return criadoPor;
    }

    public LocalDateTime getRevogadoEm() {
        return revogadoEm;
    }

    public String getRevogadoPor() {
        return revogadoPor;
    }
}
