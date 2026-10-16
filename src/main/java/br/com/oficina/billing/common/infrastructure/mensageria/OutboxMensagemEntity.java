package br.com.oficina.billing.common.infrastructure.mensageria;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Linha da tabela {@code outbox_mensagens} (migration V22). */
@Entity
@Table(name = "outbox_mensagens")
public class OutboxMensagemEntity {

    private static final int LIMITE_ERRO = 2000;

    @Id
    private UUID id;

    @Column(name = "fila_destino", nullable = false)
    private String filaDestino;

    @Column(nullable = false)
    private String tipo;

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "criada_em", nullable = false)
    private LocalDateTime criadaEm;

    @Column(name = "publicada_em")
    private LocalDateTime publicadaEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;

    protected OutboxMensagemEntity() {
    }

    OutboxMensagemEntity(UUID id, String filaDestino, String tipo, String groupId, String payload, LocalDateTime criadaEm) {
        this.id = id;
        this.filaDestino = filaDestino;
        this.tipo = tipo;
        this.groupId = groupId;
        this.payload = payload;
        this.criadaEm = criadaEm;
    }

    void marcarPublicada(LocalDateTime instante) {
        this.publicadaEm = instante;
    }

    void registrarFalha(String erro) {
        this.tentativas++;
        this.ultimoErro = erro == null ? null : erro.substring(0, Math.min(erro.length(), LIMITE_ERRO));
    }

    public UUID getId() {
        return id;
    }

    public String getFilaDestino() {
        return filaDestino;
    }

    public String getTipo() {
        return tipo;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getPayload() {
        return payload;
    }

    public LocalDateTime getCriadaEm() {
        return criadaEm;
    }

    public LocalDateTime getPublicadaEm() {
        return publicadaEm;
    }

    public int getTentativas() {
        return tentativas;
    }

    public String getUltimoErro() {
        return ultimoErro;
    }
}
