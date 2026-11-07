package br.com.oficina.billing.common.infrastructure.mensageria;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha da tabela {@code mensagens_processadas} (migration V22): a idempotencia do consumidor. */
@Entity
@Table(name = "mensagens_processadas")
public class MensagemProcessadaEntity {

    @Id
    @Column(name = "mensagem_id")
    private UUID mensagemId;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "processada_em", nullable = false)
    private LocalDateTime processadaEm;

    protected MensagemProcessadaEntity() {
    }

    MensagemProcessadaEntity(UUID mensagemId, String tipo, LocalDateTime processadaEm) {
        this.mensagemId = mensagemId;
        this.tipo = tipo;
        this.processadaEm = processadaEm;
    }

    public UUID getMensagemId() {
        return mensagemId;
    }

    public String getTipo() {
        return tipo;
    }
}
