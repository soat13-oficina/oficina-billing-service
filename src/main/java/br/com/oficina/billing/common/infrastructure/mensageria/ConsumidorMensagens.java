package br.com.oficina.billing.common.infrastructure.mensageria;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import io.micrometer.core.instrument.MeterRegistry;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recebe uma mensagem do transporte e a entrega ao {@link ManipuladorMensagem} do seu tipo, com
 * idempotencia por messageId (ADR 0005).
 *
 * <p>Tudo numa transacao propria ({@link Propagation#REQUIRES_NEW}): o efeito do manipulador e o
 * registro em {@code mensagens_processadas} confirmam juntos ou nao confirmam. Lancar devolve a
 * mensagem ao transporte, que tenta de novo.
 */
@Component
public class ConsumidorMensagens {
    private static final Logger log = LoggerFactory.getLogger(ConsumidorMensagens.class);

    static final String MDC_SAGA_ID = "saga_id";
    static final String MDC_ORDEM_SERVICO_ID = "ordem_servico_id";
    static final String MDC_MESSAGE_ID = "message_id";
    static final String MDC_MESSAGE_TYPE = "message_type";

    private final Map<String, ManipuladorMensagem> manipuladoresPorTipo = new HashMap<>();
    private final SpringDataMensagemProcessadaRepository processadas;
    private final JsonMapper jsonMapper;
    private final MeterRegistry meterRegistry;

    public ConsumidorMensagens(
            ObjectProvider<ManipuladorMensagem> manipuladores,
            SpringDataMensagemProcessadaRepository processadas,
            JsonMapper jsonMapper,
            MeterRegistry meterRegistry) {
        for (ManipuladorMensagem manipulador : manipuladores.orderedStream().toList()) {
            for (String tipo : manipulador.tipos()) {
                ManipuladorMensagem anterior = manipuladoresPorTipo.putIfAbsent(tipo, manipulador);
                if (anterior != null) {
                    throw new IllegalStateException("Dois manipuladores para o tipo " + tipo + ": "
                            + anterior.getClass().getSimpleName() + " e " + manipulador.getClass().getSimpleName());
                }
            }
        }
        this.processadas = processadas;
        this.jsonMapper = jsonMapper;
        this.meterRegistry = meterRegistry;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void consumir(String corpo) {
        Envelope envelope = ler(corpo);
        if (processadas.existsById(envelope.messageId())) {
            log.info("Mensagem duplicada descartada. messageId={}, tipo={}", envelope.messageId(), envelope.type());
            contar(envelope, "duplicada");
            return;
        }
        ManipuladorMensagem manipulador = manipuladoresPorTipo.get(envelope.type());
        if (manipulador == null) {
            throw new MensagemInvalidaException("Nenhum manipulador para o tipo " + envelope.type());
        }
        Map<String, String> mdcAnterior = MDC.getCopyOfContextMap();
        try {
            preencherMdc(envelope);
            manipulador.tratar(envelope);
            processadas.save(new MensagemProcessadaEntity(envelope.messageId(), envelope.type(), LocalDateTime.now()));
            contar(envelope, "processada");
        } finally {
            if (mdcAnterior == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(mdcAnterior);
            }
        }
    }

    private Envelope ler(String corpo) {
        try {
            return jsonMapper.readValue(corpo, Envelope.class);
        } catch (JacksonException | IllegalArgumentException e) {
            throw new MensagemInvalidaException("Mensagem fora do contrato: " + e.getMessage(), e);
        }
    }

    /** Mesmas chaves snake_case do resto da aplicacao: o log estruturado as leva ao nivel raiz do JSON. */
    private static void preencherMdc(Envelope envelope) {
        MDC.put(FabricaEnvelopes.MDC_CORRELATION_ID, envelope.correlationId());
        MDC.put(MDC_ORDEM_SERVICO_ID, envelope.ordemServicoId().toString());
        MDC.put(MDC_MESSAGE_ID, envelope.messageId().toString());
        MDC.put(MDC_MESSAGE_TYPE, envelope.type());
        if (envelope.sagaId() != null) {
            MDC.put(MDC_SAGA_ID, envelope.sagaId().toString());
        }
    }

    private void contar(Envelope envelope, String resultado) {
        meterRegistry.counter("oficina.mensageria.consumidas", "tipo", envelope.type(), "resultado", resultado).increment();
    }
}
