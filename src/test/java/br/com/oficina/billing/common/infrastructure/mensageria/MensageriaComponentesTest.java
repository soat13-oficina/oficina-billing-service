package br.com.oficina.billing.common.infrastructure.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import tools.jackson.databind.json.JsonMapper;

/** Pecas da mensageria que nao precisam do contexto Spring. */
class MensageriaComponentesTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void doisManipuladoresParaOMesmoTipoImpedemASubida() {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("a", manipulador("Tipo"));
        beans.registerSingleton("b", manipulador("Tipo"));

        assertThatThrownBy(() -> new ConsumidorMensagens(
                beans.getBeanProvider(ManipuladorMensagem.class), null, JSON, new SimpleMeterRegistry()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Tipo");
    }

    @Test
    void fabricaUsaACorrelacaoDoMdcOuGeraUmaEARespostaApontaParaACausa() {
        Clock relogio = Clock.fixed(Instant.parse("2026-10-15T13:00:00Z"), ZoneOffset.UTC);
        FabricaEnvelopes fabrica = new FabricaEnvelopes(JSON, "oficina-os-service", relogio);
        UUID saga = UUID.randomUUID();
        UUID ordem = UUID.randomUUID();

        Envelope comando = fabrica.novo("GerarOrcamento", saga, ordem, Map.of("numeroOrdemServico", "OS-1"));
        Envelope resposta = fabrica.resposta(comando, "OrcamentoGerado", Map.of("valorTotal", "530.00"));

        assertThat(comando.correlationId()).as("sem MDC, gera uma").isNotBlank();
        assertThat(comando.causationId()).isNull();
        assertThat(comando.occurredAt()).isEqualTo(Instant.parse("2026-10-15T13:00:00Z"));
        assertThat(comando.source()).isEqualTo("oficina-os-service");
        assertThat(comando.version()).isEqualTo(1);
        assertThat(comando.payload().get("numeroOrdemServico").asString()).isEqualTo("OS-1");
        assertThat(resposta.sagaId()).isEqualTo(saga);
        assertThat(resposta.ordemServicoId()).isEqualTo(ordem);
        assertThat(resposta.causationId()).isEqualTo(comando.messageId());
        assertThat(resposta.messageId()).isNotEqualTo(comando.messageId());
    }

    @Test
    void envelopeIncompletoOuDeVersaoInvalidaEhRecusado() {
        var payload = JSON.createObjectNode();
        UUID id = UUID.randomUUID();
        UUID ordem = UUID.randomUUID();
        Instant agora = Instant.now();

        assertThatThrownBy(() -> new Envelope(null, "T", 1, null, ordem, "c", null, agora, "s", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Envelope(id, " ", 1, null, ordem, "c", null, agora, "s", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Envelope(id, null, 1, null, ordem, "c", null, agora, "s", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Envelope(id, "T", 1, null, null, "c", null, agora, "s", payload))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Envelope(id, "T", 1, null, ordem, "c", null, agora, "s", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Envelope(id, "T", 0, null, ordem, "c", null, agora, "s", payload))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void travaForaDoPostgresEhSempreConcedidaENoPostgresDependeDoAdvisoryLock() {
        assertThat(new TravaPublicacaoOutbox(null, false).tentarAdquirir()).isTrue();
        assertThat(new TravaPublicacaoOutbox(jdbcQueResponde(true), true).tentarAdquirir()).isTrue();
        assertThat(new TravaPublicacaoOutbox(jdbcQueResponde(false), true).tentarAdquirir()).isFalse();
        assertThat(new TravaPublicacaoOutbox(jdbcQueResponde(null), true).tentarAdquirir()).isFalse();
    }

    @Test
    void falhaDoCicloInteiroNaoDerrubaOAgendamento() {
        PublicadorOutbox quebrado = new PublicadorOutbox(null, null, null, null, 5, 100) {
            @Override
            public int publicarPendentes() {
                throw new IllegalStateException("banco fora");
            }
        };

        assertThatCode(() -> new AgendadorPublicacaoOutbox(quebrado).publicar()).doesNotThrowAnyException();
    }

    @Test
    void erroLongoEhTruncadoNoOutbox() {
        OutboxMensagemEntity mensagem = new OutboxMensagemEntity(UUID.randomUUID(), "f", "T", "g", "{}", null);

        mensagem.registrarFalha("x".repeat(5000));
        assertThat(mensagem.getUltimoErro()).hasSize(2000);
        mensagem.registrarFalha(null);
        assertThat(mensagem.getUltimoErro()).isNull();
        assertThat(mensagem.getTentativas()).isEqualTo(2);
    }

    private static JdbcTemplate jdbcQueResponde(Boolean resposta) {
        return new JdbcTemplate() {
            @Override
            @SuppressWarnings("unchecked")
            public <T> T queryForObject(String sql, Class<T> tipo, Object... args) {
                assertThat(sql).contains("pg_try_advisory_xact_lock");
                assertThat(args).containsExactly(TravaPublicacaoOutbox.CHAVE);
                return (T) resposta;
            }
        };
    }

    private static ManipuladorMensagem manipulador(String tipo) {
        return new ManipuladorMensagem() {
            @Override
            public Set<String> tipos() {
                return Set.of(tipo);
            }

            @Override
            public void tratar(Envelope envelope) {
            }
        };
    }
}
