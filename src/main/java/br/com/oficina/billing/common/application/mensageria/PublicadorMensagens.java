package br.com.oficina.billing.common.application.mensageria;

import java.util.Optional;
import java.util.UUID;

/**
 * Porta de saida para comandos e eventos da Saga.
 *
 * <p>Publicar NAO envia nada na hora: grava a mensagem no outbox na transacao corrente, que
 * precisa existir. A mensagem so sai se a transacao confirmar - e sai mesmo que o pod caia logo
 * depois, porque o publicador do outbox a encontra pendente no proximo ciclo (ADR 0005).
 */
public interface PublicadorMensagens {

    /**
     * @param fila nome logico da fila de destino (ver {@link Filas})
     */
    void publicar(String fila, Envelope envelope);

    /**
     * Reenvia o ultimo comando {@code tipo} gravado para a OS: mesmo payload, sagaId e correlacao,
     * {@code messageId} novo (ADR 0004 - reenvio por timeout de resposta). Tambem grava no outbox.
     *
     * @return o envelope reenviado; vazio se nenhum comando daquele tipo foi enviado para a OS
     */
    Optional<Envelope> reenviar(String fila, String tipo, UUID ordemServicoId);
}
