package br.com.oficina.billing.orcamento.application.saga;

import java.util.List;

import br.com.oficina.billing.common.application.mensageria.Envelope;
import br.com.oficina.billing.orcamento.domain.model.CanalDecisao;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;

/**
 * Eventos que o orcamento publica para o orquestrador da Saga ({@code os-saga-replies}). Gravados
 * no outbox na transacao corrente: so saem se a mudanca confirmar.
 */
public interface EventosOrcamento {

    /** Para os testes de unidade dos casos de uso que nao exercitam a mensageria. */
    EventosOrcamento NENHUM = new EventosOrcamento() {
        @Override
        public void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal) {
        }

        @Override
        public void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal) {
        }

        @Override
        public void orcamentoGerado(Envelope comando, Orcamento orcamento) {
        }

        @Override
        public void orcamentoFalhou(Envelope comando, String motivo, List<String> pecasInvalidas) {
        }

        @Override
        public void aprovacaoRevertida(Envelope comando, Orcamento orcamento) {
        }

        @Override
        public void orcamentoCancelado(Envelope comando, Orcamento orcamento, boolean pagamentoCancelado) {
        }
    };

    // Eventos espontaneos (acao do cliente ou do funcionario): sem causationId.

    void orcamentoAprovado(Orcamento orcamento, CanalDecisao canal);

    void orcamentoRejeitado(Orcamento orcamento, CanalDecisao canal);

    // Respostas a comandos da Saga: causationId = o comando.

    void orcamentoGerado(Envelope comando, Orcamento orcamento);

    void orcamentoFalhou(Envelope comando, String motivo, List<String> pecasInvalidas);

    void aprovacaoRevertida(Envelope comando, Orcamento orcamento);

    void orcamentoCancelado(Envelope comando, Orcamento orcamento, boolean pagamentoCancelado);
}
