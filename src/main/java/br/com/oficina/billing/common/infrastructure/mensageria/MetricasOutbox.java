package br.com.oficina.billing.common.infrastructure.mensageria;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Estado do outbox (ADR 0005) como gauges, para o alerta de "mensagem parada":
 *
 * <ul>
 *   <li>{@code oficina.outbox.pendentes} - esperando entrega (ainda com tentativas);</li>
 *   <li>{@code oficina.outbox.idade.maxima} - em segundos, a mais antiga delas: com o publicador
 *       rodando a cada poucos segundos, minutos aqui significam transporte fora do ar;</li>
 *   <li>{@code oficina.outbox.esgotadas} - desistiu de tentar ({@code mensageria.outbox.max-tentativas}):
 *       o equivalente a uma DLQ do lado de quem envia, e nao sai sozinha.</li>
 * </ul>
 */
@Component
public class MetricasOutbox {

    public MetricasOutbox(
            MeterRegistry registry,
            SpringDataOutboxMensagemRepository repository,
            @Value("${mensageria.outbox.max-tentativas:5}") int maxTentativas) {
        Gauge.builder("oficina.outbox.pendentes", () -> repository.countByPublicadaEmIsNullAndTentativasLessThan(maxTentativas))
                .description("Mensagens do outbox esperando entrega")
                .register(registry);
        Gauge.builder("oficina.outbox.esgotadas", () -> repository.countByPublicadaEmIsNullAndTentativasGreaterThanEqual(maxTentativas))
                .description("Mensagens do outbox que esgotaram as tentativas de envio")
                .register(registry);
        Gauge.builder("oficina.outbox.idade.maxima", () -> idadeDaMaisAntiga(repository, maxTentativas))
                .description("Idade, em segundos, da mensagem pendente mais antiga")
                .baseUnit("seconds")
                .register(registry);
    }

    private static double idadeDaMaisAntiga(SpringDataOutboxMensagemRepository repository, int maxTentativas) {
        return repository.findFirstByPublicadaEmIsNullAndTentativasLessThanOrderByCriadaEmAsc(maxTentativas)
                .map(m -> (double) Math.max(0, Duration.between(m.getCriadaEm(), LocalDateTime.now()).toSeconds()))
                .orElse(0.0);
    }
}
