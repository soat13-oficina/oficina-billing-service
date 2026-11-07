package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.common.infrastructure.mensageria.SpringDataOutboxMensagemRepository;
import br.com.oficina.billing.orcamento.application.command.DecidirOrcamentoExternamenteCommand;
import br.com.oficina.billing.orcamento.application.saga.EventosOrcamento;
import br.com.oficina.billing.orcamento.application.usecase.DecidirOrcamentoExternamenteUseCase;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.DecisaoOrcamento;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;
import br.com.oficina.billing.orcamento.infrastructure.persistence.SpringDataOrcamentoRepository;
import br.com.oficina.billing.support.persistence.OrcamentosDeTeste;

/**
 * A decisao do orcamento e o evento da Saga no outbox confirmam juntos: se o evento nao pode ser
 * gravado, a decisao e desfeita (ADR 0005).
 */
@SpringBootTest
@Import(DecidirOrcamentoAtomicidadeIntegrationTest.FalhaAoPublicarEventoConfig.class)
class DecidirOrcamentoAtomicidadeIntegrationTest {

    @Autowired
    private DecidirOrcamentoExternamenteUseCase decidirOrcamentoExternamenteUseCase;

    @Autowired
    private OrcamentoRepository orcamentoRepository;

    @Autowired
    private SpringDataOrcamentoRepository springDataOrcamentoRepository;

    @Autowired
    private SpringDataOutboxMensagemRepository outbox;

    // Sem @Transactional (proposital: o setup precisa estar commitado para exercitar o rollback do use
    // case como uma transacao separada). Por isso a limpeza roda antes e depois.
    @BeforeEach
    @AfterEach
    void limparTabelas() {
        springDataOrcamentoRepository.deleteAll();
        outbox.deleteAll();
    }

    @Test
    void deveDesfazerADecisaoQuandoOEventoDaSagaNaoPodeSerGravado() {
        springDataOrcamentoRepository.save(OrcamentosDeTeste.novo("ORC-ATOM-1", StatusOrcamento.AGUARDANDO_APROVACAO));

        assertThrows(RuntimeException.class, () -> decidirOrcamentoExternamenteUseCase.decidir(
                new DecidirOrcamentoExternamenteCommand("ORC-ATOM-1", DecisaoOrcamento.APROVADO)));
        assertThrows(RuntimeException.class, () -> decidirOrcamentoExternamenteUseCase.decidir(
                new DecidirOrcamentoExternamenteCommand("ORC-ATOM-1", DecisaoOrcamento.REJEITADO)));

        Orcamento orcamento = orcamentoRepository.buscarPorNumeroOrcamento("ORC-ATOM-1").orElseThrow();
        assertEquals(StatusOrcamento.AGUARDANDO_APROVACAO, orcamento.getStatus(),
                "Orçamento deve permanecer aguardando aprovação após o rollback");
        assertEquals(0, outbox.count());
    }

    /**
     * Substitui a publicacao dos eventos do orcamento por uma que falha, para exercitar o rollback
     * transacional da decisao. Nao usa Mockito (restricao da constituicao) - e um duplo escrito a mao.
     */
    @TestConfiguration
    static class FalhaAoPublicarEventoConfig {
        @Bean
        @Primary
        EventosOrcamento eventosQueFalham() {
            return new EventosOrcamento() {
                @Override
                public void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }

                @Override
                public void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }

                @Override
                public void orcamentoGerado(Envelope comando, Orcamento orcamento) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }

                @Override
                public void orcamentoFalhou(Envelope comando, String motivo, java.util.List<String> pecasInvalidas) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }

                @Override
                public void aprovacaoRevertida(Envelope comando, Orcamento orcamento) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }

                @Override
                public void orcamentoCancelado(Envelope comando, Orcamento orcamento, boolean pagamentoCancelado) {
                    throw new IllegalStateException("Falha simulada ao gravar o evento no outbox");
                }
            };
        }
    }
}
