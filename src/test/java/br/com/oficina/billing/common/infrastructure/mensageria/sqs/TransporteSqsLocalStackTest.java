package br.com.oficina.billing.common.infrastructure.mensageria.sqs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.application.mensageria.FabricaEnvelopes;
import br.com.oficina.billing.common.application.mensageria.Filas;
import br.com.oficina.billing.common.application.mensageria.ManipuladorMensagem;
import br.com.oficina.billing.common.application.mensageria.PublicadorMensagens;
import br.com.oficina.billing.common.infrastructure.mensageria.PublicadorOutbox;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataMensagemProcessadaRepository;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.json.JsonMapper;

/**
 * Transporte SQS contra o LocalStack (ADR 0005): o outbox publica os eventos em os-saga-replies e o
 * receptor le billing-commands e entrega ao consumidor idempotente.
 */
@Testcontainers
@SpringBootTest(properties = {
        "mensageria.sqs.habilitado=true",
        "mensageria.sqs.consumo=billing-commands",
        "mensageria.sqs.ambiente=teste",
        "mensageria.sqs.espera=PT1S",
        "mensageria.sqs.receptor-automatico=false"
})
class TransporteSqsLocalStackTest {

    static {
        // Cadeia padrao de credenciais do SDK; o LocalStack aceita qualquer par.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
    }

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.14.0"))
            .withServices(LocalStackContainer.Service.SQS);

    private static SqsClient sqsDoTeste;
    private static String urlReplies;
    private static String urlComandos;
    private static String urlComandosDlq;

    @DynamicPropertySource
    static void localstack(DynamicPropertyRegistry registro) {
        registro.add("mensageria.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registro.add("mensageria.sqs.regiao", LOCALSTACK::getRegion);
    }

    @BeforeAll
    static void criarFilas() {
        sqsDoTeste = SqsClient.builder()
                .endpointOverride(LOCALSTACK.getEndpoint())
                .region(Region.of(LOCALSTACK.getRegion()))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build();
        urlReplies = criarFifo("oficina-teste-os-saga-replies.fifo", Map.of());
        urlComandosDlq = criarFifo("oficina-teste-billing-commands-dlq.fifo", Map.of());
        String arnDlq = sqsDoTeste.getQueueAttributes(r -> r.queueUrl(urlComandosDlq).attributeNames(QueueAttributeName.QUEUE_ARN))
                .attributes().get(QueueAttributeName.QUEUE_ARN);
        urlComandos = criarFifo("oficina-teste-billing-commands.fifo", Map.of(
                QueueAttributeName.VISIBILITY_TIMEOUT, "1",
                QueueAttributeName.REDRIVE_POLICY, "{\"deadLetterTargetArn\":\"" + arnDlq + "\",\"maxReceiveCount\":\"5\"}"));
    }

    private static String criarFifo(String nome, Map<QueueAttributeName, String> atributos) {
        Map<QueueAttributeName, String> todos = new java.util.HashMap<>(atributos);
        todos.put(QueueAttributeName.FIFO_QUEUE, "true");
        return sqsDoTeste.createQueue(r -> r.queueName(nome).attributes(todos)).queueUrl();
    }

    @Autowired
    private PublicadorMensagens publicador;

    @Autowired
    private PublicadorOutbox publicadorOutbox;

    @Autowired
    private ReceptorSqs receptor;

    @Autowired
    private FabricaEnvelopes fabrica;

    @Autowired
    private JsonMapper jsonMapper;

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
        for (String url : List.of(urlReplies, urlComandos, urlComandosDlq)) {
            sqsDoTeste.purgeQueue(r -> r.queueUrl(url));
        }
    }

    @Test
    void eventoSaiPeloSqsComGrupoDeduplicacaoEAtributos() {
        UUID ordem = UUID.randomUUID();
        MDC.put(FabricaEnvelopes.MDC_CORRELATION_ID, "req-123");
        Envelope evento;
        try {
            evento = fabrica.novo("TesteSqs", UUID.randomUUID(), ordem, Map.of("campo", "valor"));
        } finally {
            MDC.clear();
        }
        transacao.executeWithoutResult(s -> publicador.publicar(Filas.OS_SAGA_REPLIES, evento));

        assertThat(publicadorOutbox.publicarPendentes()).isEqualTo(1);

        List<Message> naFila = receber(urlReplies);
        assertThat(naFila).singleElement().satisfies(m -> {
            assertThat(jsonMapper.readValue(m.body(), Envelope.class)).isEqualTo(evento);
            assertThat(m.attributes()).containsEntry(MessageSystemAttributeName.MESSAGE_GROUP_ID, ordem.toString())
                    .containsEntry(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, evento.messageId().toString());
            assertThat(m.messageAttributes().get("type").stringValue()).isEqualTo("TesteSqs");
            assertThat(m.messageAttributes().get("version").stringValue()).isEqualTo("1");
            assertThat(m.messageAttributes().get("correlationId").stringValue()).isEqualTo("req-123");
        });
        assertThat(outbox.findById(evento.messageId()).orElseThrow().getPublicadaEm()).isNotNull();
    }

    @Test
    void receptorEntregaAoConsumidorEApagaDaFilaUmaVezSo() {
        Envelope evento = fabrica.novo("TesteSqs", UUID.randomUUID(), UUID.randomUUID(), Map.of("campo", "valor"));
        enviarParaComandos(evento, UUID.randomUUID().toString());
        enviarParaComandos(evento, UUID.randomUUID().toString()); // reentrega: mesmo messageId

        assertThat(receptor.receberLote(Filas.BILLING_COMMANDS)).isEqualTo(2);

        assertThat(manipulador.recebidas).containsExactly(evento);
        assertThat(processadas.existsById(evento.messageId())).isTrue();
        assertThat(receber(urlComandos)).isEmpty();
    }

    @Test
    void mensagemForaDoContratoVaiDiretoParaADlq() {
        sqsDoTeste.sendMessage(r -> r.queueUrl(urlComandos).messageGroupId("grupo-x")
                .messageDeduplicationId(UUID.randomUUID().toString()).messageBody("{nao e json"));
        Envelope semManipulador = fabrica.novo("TipoQueNinguemTrata", null, UUID.randomUUID(), Map.of());
        enviarParaComandos(semManipulador, UUID.randomUUID().toString());

        assertThat(receptor.receberLote(Filas.BILLING_COMMANDS)).isEqualTo(2);

        assertThat(receber(urlComandos)).isEmpty();
        List<Message> naDlq = receber(urlComandosDlq);
        assertThat(naDlq).extracting(Message::body).hasSize(2).contains("{nao e json");
        assertThat(naDlq).allSatisfy(m -> assertThat(m.messageAttributes()).containsKey("erro"));
    }

    @Test
    void falhaSeguraAsSeguintesDaMesmaOsAteDarCertoMasNaoAsDasOutras() throws InterruptedException {
        manipulador.falhasRestantes.set(1);
        UUID ordemA = UUID.randomUUID();
        Envelope a1 = fabrica.novo("TesteFalhaControlada", null, ordemA, Map.of("n", 1));
        Envelope a2 = fabrica.novo("TesteSqs", null, ordemA, Map.of("n", 2));
        Envelope b1 = fabrica.novo("TesteSqs", null, UUID.randomUUID(), Map.of("n", 3));
        enviarParaComandos(a1, a1.messageId().toString());
        enviarParaComandos(a2, a2.messageId().toString());
        enviarParaComandos(b1, b1.messageId().toString());

        // O lote pode vir partido; processa ate esvaziar (a1 volta depois do visibility timeout de 1 s).
        for (int i = 0; i < 15 && manipulador.recebidas.size() < 3; i++) {
            receptor.receberLote(Filas.BILLING_COMMANDS);
        }

        assertThat(manipulador.falhas).hasValue(1);
        assertThat(manipulador.recebidas).containsExactlyInAnyOrder(a1, a2, b1);
        assertThat(manipulador.recebidas.indexOf(a1)).as("ordem da OS preservada").isLessThan(manipulador.recebidas.indexOf(a2));
        assertThat(receber(urlComandosDlq)).isEmpty();
    }

    private void enviarParaComandos(Envelope envelope, String deduplicacao) {
        String corpo = jsonMapper.writeValueAsString(envelope);
        sqsDoTeste.sendMessage(r -> r.queueUrl(urlComandos).messageGroupId(envelope.ordemServicoId().toString())
                .messageDeduplicationId(deduplicacao).messageBody(corpo));
    }

    private static List<Message> receber(String url) {
        return sqsDoTeste.receiveMessage(r -> r.queueUrl(url).maxNumberOfMessages(10).waitTimeSeconds(1)
                .messageSystemAttributeNames(MessageSystemAttributeName.ALL).messageAttributeNames("All")).messages();
    }

    @TestConfiguration
    static class Configuracao {
        @Bean
        ManipuladorDeTeste manipuladorDeTeste() {
            return new ManipuladorDeTeste();
        }
    }

    static class ManipuladorDeTeste implements ManipuladorMensagem {
        final List<Envelope> recebidas = java.util.Collections.synchronizedList(new ArrayList<>());
        final AtomicInteger falhasRestantes = new AtomicInteger();
        final AtomicInteger falhas = new AtomicInteger();

        void reiniciar() {
            recebidas.clear();
            falhasRestantes.set(0);
            falhas.set(0);
        }

        @Override
        public Set<String> tipos() {
            return Set.of("TesteSqs", "TesteFalhaControlada");
        }

        @Override
        public void tratar(Envelope envelope) {
            if ("TesteFalhaControlada".equals(envelope.type()) && falhasRestantes.getAndDecrement() > 0) {
                falhas.incrementAndGet();
                throw new IllegalStateException("falha simulada");
            }
            recebidas.add(envelope);
        }
    }
}
