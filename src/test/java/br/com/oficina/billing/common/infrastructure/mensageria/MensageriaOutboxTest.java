package br.com.oficina.billing.common.infrastructure.mensageria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.Filas;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;

/**
 * Caminho completo da mensageria do Passo 1: publicar (outbox, na transacao) -> publicador do
 * outbox -> transporte em memoria -> consumidor idempotente -> manipulador do tipo.
 */
@SpringBootTest
class MensageriaOutboxTest {

    @Autowired
    private PublicadorMensagens publicadorMensagens;

    @Autowired
    private PublicadorOutbox publicadorOutbox;

    @Autowired
    private ConsumidorMensagens consumidor;

    @Autowired
    private FabricaEnvelopes fabrica;

    @Autowired
    private TransactionTemplate transacao;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    @Autowired
    private SpringDataMensagemProcessadaRepository processadas;

    @Autowired
    private ManipuladorDeTeste manipulador;

    @BeforeEach
    void limpar() {
        outbox.deleteAll();
        processadas.deleteAll();
        manipulador.reiniciar();
    }

    @Test
    void mensagemPublicadaNaTransacaoChegaAoManipuladorPeloOutbox() {
        UUID ordem = UUID.randomUUID();
        Envelope envelope = publicarEmTransacao("TesteRecebido", ordem);

        assertThat(outbox.findById(envelope.messageId())).get()
                .satisfies(m -> {
                    assertThat(m.getPublicadaEm()).isNull();
                    assertThat(m.getGroupId()).isEqualTo(ordem.toString());
                    assertThat(m.getFilaDestino()).isEqualTo(Filas.EXECUTION_COMMANDS);
                });
        assertThat(manipulador.recebidas).isEmpty();

        assertThat(publicadorOutbox.publicarPendentes()).isEqualTo(1);

        assertThat(manipulador.recebidas).singleElement().isEqualTo(envelope);
        assertThat(outbox.findById(envelope.messageId()).orElseThrow().getPublicadaEm()).isNotNull();
        assertThat(processadas.existsById(envelope.messageId())).isTrue();
        assertThat(publicadorOutbox.publicarPendentes()).as("nada mais pendente").isZero();
    }

    @Test
    void publicarForaDeTransacaoEhErroDeProgramacao() {
        Envelope envelope = fabrica.novo("TesteRecebido", UUID.randomUUID(), UUID.randomUUID(), Map.of());

        assertThatThrownBy(() -> publicadorMensagens.publicar(Filas.EXECUTION_COMMANDS, envelope))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void transacaoDesfeitaNaoDeixaMensagemNoOutbox() {
        assertThatThrownBy(() -> transacao.executeWithoutResult(status -> {
            publicadorMensagens.publicar(Filas.EXECUTION_COMMANDS,
                    fabrica.novo("TesteRecebido", UUID.randomUUID(), UUID.randomUUID(), Map.of()));
            throw new IllegalStateException("regra de negocio falhou");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(outbox.count()).isZero();
    }

    @Test
    void mensagemRepetidaEhDescartadaPeloConsumidor() {
        Envelope envelope = publicarEmTransacao("TesteRecebido", UUID.randomUUID());
        String corpo = outbox.findById(envelope.messageId()).orElseThrow().getPayload();

        consumidor.consumir(corpo);
        consumidor.consumir(corpo);

        assertThat(manipulador.recebidas).hasSize(1);
    }

    @Test
    void falhaSeguraAsMensagensSeguintesDaMesmaOsMasNaoAsDeOutras() {
        UUID ordemA = UUID.randomUUID();
        UUID ordemB = UUID.randomUUID();
        manipulador.falhasRestantes.set(1);
        Envelope a1 = publicarEmTransacao("TesteFalhaControlada", ordemA);
        Envelope a2 = publicarEmTransacao("TesteRecebido", ordemA);
        Envelope b1 = publicarEmTransacao("TesteRecebido", ordemB);

        assertThat(publicadorOutbox.publicarPendentes()).isEqualTo(1);
        assertThat(manipulador.recebidas).containsExactly(b1);
        assertThat(outbox.findById(a1.messageId()).orElseThrow())
                .satisfies(m -> {
                    assertThat(m.getTentativas()).isEqualTo(1);
                    assertThat(m.getUltimoErro()).contains("falha simulada");
                });
        assertThat(outbox.findById(a2.messageId()).orElseThrow().getTentativas()).as("nem tentou").isZero();

        assertThat(publicadorOutbox.publicarPendentes()).isEqualTo(2);
        assertThat(manipulador.recebidas).containsExactly(b1, a1, a2);
    }

    @Test
    void mensagemQueEsgotaAsTentativasDeixaDeSerLida() {
        manipulador.falhasRestantes.set(Integer.MAX_VALUE);
        Envelope envelope = publicarEmTransacao("TesteFalhaControlada", UUID.randomUUID());

        for (int ciclo = 0; ciclo < 7; ciclo++) {
            publicadorOutbox.publicarPendentes();
        }

        assertThat(outbox.findById(envelope.messageId()).orElseThrow())
                .satisfies(m -> {
                    assertThat(m.getTentativas()).isEqualTo(5);
                    assertThat(m.getPublicadaEm()).isNull();
                });
    }

    @Test
    void manipuladorEnxergaACorrelacaoDaMensagemEOMdcEhRestaurado() {
        MDC.put(FabricaEnvelopes.MDC_CORRELATION_ID, "req-original");
        try {
            Envelope envelope = publicarEmTransacao("TesteRecebido", UUID.randomUUID());
            assertThat(envelope.correlationId()).isEqualTo("req-original");
            MDC.put(FabricaEnvelopes.MDC_CORRELATION_ID, "outra-requisicao");

            publicadorOutbox.publicarPendentes();

            assertThat(manipulador.correlacaoVista).isEqualTo("req-original");
            assertThat(manipulador.sagaVista).isEqualTo(envelope.sagaId().toString());
            assertThat(MDC.get(FabricaEnvelopes.MDC_CORRELATION_ID)).isEqualTo("outra-requisicao");
        } finally {
            MDC.clear();
        }
    }

    @Test
    void mensagemForaDoContratoOuDeTipoDesconhecidoEhInvalida() {
        assertThatThrownBy(() -> consumidor.consumir("{nao e json"))
                .isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> consumidor.consumir("{\"type\":\"SemMessageId\"}"))
                .isInstanceOf(MensagemInvalidaException.class);

        Envelope desconhecido = publicarEmTransacao("TipoQueNinguemTrata", UUID.randomUUID());
        String corpo = outbox.findById(desconhecido.messageId()).orElseThrow().getPayload();
        assertThatThrownBy(() -> consumidor.consumir(corpo))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("TipoQueNinguemTrata");
    }

    @Test
    void reenvioRepeteOComandoDaOrdemComMessageIdNovo() {
        UUID ordemA = UUID.randomUUID();
        Envelope original = publicarEmTransacao("TesteRecebido", ordemA);
        Envelope deOutraOrdem = fabrica.novo("TesteRecebido", UUID.randomUUID(), UUID.randomUUID(), Map.of("campo", "outro"));
        transacao.executeWithoutResult(status -> publicadorMensagens.publicar(Filas.EXECUTION_COMMANDS, deOutraOrdem));
        publicadorOutbox.publicarPendentes();
        manipulador.reiniciar();

        Envelope reenviado = transacao.execute(status ->
                publicadorMensagens.reenviar(Filas.EXECUTION_COMMANDS, "TesteRecebido", ordemA).orElseThrow());

        assertThat(reenviado.messageId()).isNotEqualTo(original.messageId());
        assertThat(reenviado.type()).isEqualTo(original.type());
        assertThat(reenviado.sagaId()).isEqualTo(original.sagaId());
        assertThat(reenviado.ordemServicoId()).isEqualTo(ordemA);
        assertThat(reenviado.correlationId()).isEqualTo(original.correlationId());
        assertThat(reenviado.causationId()).isEqualTo(original.causationId());
        assertThat(reenviado.source()).isEqualTo(original.source());
        assertThat(reenviado.payload()).isEqualTo(original.payload());
        assertThat(outbox.findById(reenviado.messageId()).orElseThrow().getPublicadaEm()).as("vai pelo outbox").isNull();

        assertThat(publicadorOutbox.publicarPendentes()).isEqualTo(1);
        assertThat(manipulador.recebidas).as("messageId novo: o consumidor nao descarta").containsExactly(reenviado);
    }

    @Test
    void semComandoAnteriorNaoHaReenvio() {
        publicarEmTransacao("TesteRecebido", UUID.randomUUID());
        long antes = outbox.count();

        var reenviado = transacao.execute(status ->
                publicadorMensagens.reenviar(Filas.EXECUTION_COMMANDS, "TesteRecebido", UUID.randomUUID()));

        assertThat(reenviado).isEmpty();
        assertThat(outbox.count()).isEqualTo(antes);
        assertThatThrownBy(() -> publicadorMensagens.reenviar(Filas.EXECUTION_COMMANDS, "TesteRecebido", UUID.randomUUID()))
                .as("fora de transacao").isInstanceOf(IllegalTransactionStateException.class);
    }

    private Envelope publicarEmTransacao(String tipo, UUID ordemServicoId) {
        Envelope envelope = fabrica.novo(tipo, UUID.randomUUID(), ordemServicoId, Map.of("campo", "valor"));
        transacao.executeWithoutResult(status -> publicadorMensagens.publicar(Filas.EXECUTION_COMMANDS, envelope));
        return envelope;
    }

    @TestConfiguration
    static class Configuracao {
        @Bean
        ManipuladorDeTeste manipuladorDeTeste() {
            return new ManipuladorDeTeste();
        }

        /** O Billing so tem o transporte SQS; aqui a entrega e no processo, para testar o outbox sozinho. */
        @Bean
        @Primary
        TransporteMensagens transporteNoProcesso(ConsumidorMensagens consumidor) {
            return mensagem -> consumidor.consumir(mensagem.corpo());
        }
    }

    static class ManipuladorDeTeste implements ManipuladorMensagem {
        final List<Envelope> recebidas = new ArrayList<>();
        final AtomicInteger falhasRestantes = new AtomicInteger();
        String correlacaoVista;
        String sagaVista;

        void reiniciar() {
            recebidas.clear();
            falhasRestantes.set(0);
            correlacaoVista = null;
            sagaVista = null;
        }

        @Override
        public Set<String> tipos() {
            return Set.of("TesteRecebido", "TesteFalhaControlada");
        }

        @Override
        public void tratar(Envelope envelope) {
            if ("TesteFalhaControlada".equals(envelope.type()) && falhasRestantes.getAndDecrement() > 0) {
                throw new IllegalStateException("falha simulada");
            }
            correlacaoVista = MDC.get(FabricaEnvelopes.MDC_CORRELATION_ID);
            sagaVista = MDC.get(ConsumidorMensagens.MDC_SAGA_ID);
            recebidas.add(envelope);
        }
    }
}
