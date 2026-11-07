package br.com.oficina.billing.common.infrastructure.mensageria;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.infrastructure.mensageria.TransporteMensagens.MensagemSaida;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Envia as mensagens pendentes do outbox pelo transporte ativo (ADR 0005).
 *
 * <p>Regras:
 * <ul>
 *   <li>ordem de gravacao ({@code criada_em}) - a mesma em que a Saga as produziu;</li>
 *   <li>falhou uma mensagem de uma OS, as seguintes DAQUELA OS esperam o proximo ciclo - enviar a
 *       seguinte inverteria a ordem; as outras OSs seguem normalmente;</li>
 *   <li>esgotadas as tentativas, a mensagem para de ser lida (equivale a DLQ) e e logada como erro.</li>
 * </ul>
 * Uma replica por vez ({@link TravaPublicacaoOutbox}). Duplicata residual (pod cai depois de enviar
 * e antes de confirmar) e absorvida pela deduplicacao do SQS e pela idempotencia do consumidor.
 */
@Component
public class PublicadorOutbox {
    private static final Logger log = LoggerFactory.getLogger(PublicadorOutbox.class);

    private final SpringDataOutboxMensagemRepository repository;
    private final TransporteMensagens transporte;
    private final TravaPublicacaoOutbox trava;
    private final MeterRegistry meterRegistry;
    private final int maxTentativas;
    private final int lote;

    public PublicadorOutbox(
            SpringDataOutboxMensagemRepository repository,
            TransporteMensagens transporte,
            TravaPublicacaoOutbox trava,
            MeterRegistry meterRegistry,
            @Value("${mensageria.outbox.max-tentativas:5}") int maxTentativas,
            @Value("${mensageria.outbox.lote:100}") int lote) {
        this.repository = repository;
        this.transporte = transporte;
        this.trava = trava;
        this.meterRegistry = meterRegistry;
        this.maxTentativas = maxTentativas;
        this.lote = lote;
    }

    /** @return quantas mensagens foram entregues neste ciclo */
    @Transactional
    public int publicarPendentes() {
        if (!trava.tentarAdquirir()) {
            return 0;
        }
        List<OutboxMensagemEntity> pendentes = repository
                .findByPublicadaEmIsNullAndTentativasLessThanOrderByCriadaEmAscIdAsc(maxTentativas, Limit.of(lote));
        Set<String> gruposComFalha = new HashSet<>();
        int entregues = 0;
        for (OutboxMensagemEntity mensagem : pendentes) {
            if (gruposComFalha.contains(mensagem.getGroupId())) {
                continue;
            }
            if (enviar(mensagem)) {
                entregues++;
            } else {
                gruposComFalha.add(mensagem.getGroupId());
            }
        }
        return entregues;
    }

    private boolean enviar(OutboxMensagemEntity mensagem) {
        try {
            transporte.enviar(new MensagemSaida(
                    mensagem.getFilaDestino(),
                    mensagem.getGroupId(),
                    mensagem.getId().toString(),
                    mensagem.getTipo(),
                    mensagem.getPayload()));
            mensagem.marcarPublicada(LocalDateTime.now());
            meterRegistry.counter("oficina.mensageria.publicadas", "fila", mensagem.getFilaDestino(), "tipo", mensagem.getTipo())
                    .increment();
            return true;
        } catch (RuntimeException e) {
            mensagem.registrarFalha(e.toString());
            meterRegistry.counter("oficina.mensageria.falhas", "fila", mensagem.getFilaDestino(), "tipo", mensagem.getTipo())
                    .increment();
            if (mensagem.getTentativas() >= maxTentativas) {
                log.error("Mensagem do outbox esgotou as tentativas e nao sera mais enviada. messageId={}, tipo={}, fila={}, erro={}",
                        mensagem.getId(), mensagem.getTipo(), mensagem.getFilaDestino(), e.toString());
            } else {
                log.warn("Falha ao enviar mensagem do outbox; nova tentativa no proximo ciclo. messageId={}, tipo={}, tentativa={}, erro={}",
                        mensagem.getId(), mensagem.getTipo(), mensagem.getTentativas(), e.toString());
            }
            return false;
        }
    }
}
