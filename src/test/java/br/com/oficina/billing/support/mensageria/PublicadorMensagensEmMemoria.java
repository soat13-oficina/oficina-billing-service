package br.com.oficina.billing.support.mensageria;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;
import tools.jackson.databind.json.JsonMapper;

/**
 * Duplo do outbox para os testes de unidade dos participantes da Saga: guarda o que foi publicado,
 * na ordem. Sem transacao nem entrega - quem quer o caminho completo usa o contexto Spring.
 */
public class PublicadorMensagensEmMemoria implements PublicadorMensagens {

    public static final JsonMapper JSON = JsonMapper.builder().build();

    public record Publicada(String fila, Envelope envelope) {
    }

    private final List<Publicada> publicadas = new ArrayList<>();

    /** Fabrica com relogio fixo, servico "teste". */
    public static FabricaEnvelopes fabrica() {
        return new FabricaEnvelopes(JSON, "teste", Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC));
    }

    @Override
    public void publicar(String fila, Envelope envelope) {
        publicadas.add(new Publicada(fila, envelope));
    }

    @Override
    public Optional<Envelope> reenviar(String fila, String tipo, UUID ordemServicoId) {
        Optional<Envelope> anterior = publicadas.reversed().stream()
                .map(Publicada::envelope)
                .filter(e -> e.type().equals(tipo) && e.ordemServicoId().equals(ordemServicoId))
                .findFirst();
        anterior.ifPresent(e -> publicar(fila, e));
        return anterior;
    }

    public List<Publicada> publicadas() {
        return List.copyOf(publicadas);
    }

    public Envelope unica() {
        if (publicadas.size() != 1) {
            throw new AssertionError("Esperada uma mensagem publicada; foram " + publicadas.size() + ": " + tipos());
        }
        return publicadas.get(0).envelope();
    }

    public Envelope ultima() {
        if (publicadas.isEmpty()) {
            throw new AssertionError("Nenhuma mensagem publicada");
        }
        return publicadas.get(publicadas.size() - 1).envelope();
    }

    public List<String> tipos() {
        return publicadas.stream().map(p -> p.envelope().type()).toList();
    }

    public <T> T payload(Envelope envelope, Class<T> tipo) {
        return JSON.treeToValue(envelope.payload(), tipo);
    }

    public void limpar() {
        publicadas.clear();
    }
}
