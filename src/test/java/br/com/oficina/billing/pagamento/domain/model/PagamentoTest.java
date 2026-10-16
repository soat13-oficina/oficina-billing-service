package br.com.oficina.billing.pagamento.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.domain.exception.ConflitoDeRecursoException;

/** Ciclo de vida da cobranca (ADR 0007): repetir o mesmo resultado nao e erro; mudar um final e conflito. */
class PagamentoTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 16, 10, 0);
    private static final LocalDateTime EXPIRA = T0.plusHours(24);

    private Pagamento pendente() {
        return Pagamento.novo(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("360.00"), EXPIRA, T0);
    }

    @Test
    void cobrancaNovaNascePendenteComChaveDeIdempotenciaPropria() {
        Pagamento pagamento = pendente();
        pagamento.registrarCobranca("PREF-1", "https://checkout/1", T0.plusSeconds(1));

        assertThat(pagamento.getId()).isNotNull();
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.PENDENTE);
        assertThat(pagamento.getIdempotencyKey()).isNotNull().isNotEqualTo(pendente().getIdempotencyKey());
        assertThat(pagamento.getMpPreferenceId()).isEqualTo("PREF-1");
        assertThat(pagamento.getLinkPagamento()).isEqualTo("https://checkout/1");
        assertThat(pagamento.getValor()).isEqualByComparingTo("360.00");
        assertThat(pagamento.getExpiraEm()).isEqualTo(EXPIRA);
        assertThat(pagamento.getCriadoEm()).isEqualTo(T0);
        assertThat(pagamento.getAtualizadoEm()).isEqualTo(T0.plusSeconds(1));
    }

    @Test
    void aprovacaoRepetidaNaoEhErroERecusaDepoisDaAprovacaoEhConflito() {
        Pagamento pagamento = pendente();

        assertThat(pagamento.aprovar("PAY-1", T0)).isTrue();
        assertThat(pagamento.aprovar("PAY-1", T0)).isFalse();
        assertThat(pagamento.getMpPaymentId()).isEqualTo("PAY-1");
        assertThatThrownBy(() -> pagamento.recusar("PAY-1", "cartao", T0)).isInstanceOf(ConflitoDeRecursoException.class);
        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.APROVADO);
    }

    @Test
    void recusaRepetidaNaoEhErroEAprovacaoDepoisDaRecusaEhConflito() {
        Pagamento pagamento = pendente();

        assertThat(pagamento.recusar("PAY-2", "Saldo insuficiente", T0)).isTrue();
        assertThat(pagamento.recusar("PAY-2", "Saldo insuficiente", T0)).isFalse();
        assertThat(pagamento.getMotivo()).isEqualTo("Saldo insuficiente");
        assertThatThrownBy(() -> pagamento.aprovar("PAY-2", T0)).isInstanceOf(ConflitoDeRecursoException.class);
    }

    @Test
    void soPendenteExpiraOuECancelado() {
        Pagamento expirado = pendente();
        assertThat(expirado.vencido(EXPIRA.minusMinutes(1))).isFalse();
        assertThat(expirado.vencido(EXPIRA.plusMinutes(1))).isTrue();
        assertThat(expirado.expirar(EXPIRA.plusMinutes(1))).isTrue();
        assertThat(expirado.getStatus()).isEqualTo(StatusPagamento.EXPIRADO);
        assertThat(expirado.getMotivo()).contains("Prazo de pagamento vencido");
        assertThat(expirado.vencido(EXPIRA.plusDays(1))).as("so pendente vence").isFalse();
        assertThat(expirado.expirar(EXPIRA.plusDays(1))).isFalse();
        assertThat(expirado.cancelar(EXPIRA.plusDays(1))).isFalse();
        assertThatThrownBy(() -> expirado.aprovar("PAY-3", EXPIRA.plusDays(1))).isInstanceOf(ConflitoDeRecursoException.class);

        Pagamento cancelado = pendente();
        assertThat(cancelado.cancelar(T0)).isTrue();
        assertThat(cancelado.getStatus()).isEqualTo(StatusPagamento.CANCELADO);
        assertThat(cancelado.cancelar(T0)).isFalse();
        assertThat(cancelado.expirar(EXPIRA.plusDays(1))).isFalse();

        Pagamento aprovado = pendente();
        aprovado.aprovar("PAY-4", T0);
        assertThat(aprovado.expirar(EXPIRA.plusDays(1))).isFalse();
        assertThat(aprovado.cancelar(T0)).isFalse();
        assertThat(aprovado.getStatus()).isEqualTo(StatusPagamento.APROVADO);
    }

    @Test
    void soPagamentoAprovadoEhEstornadoEORepetidoNaoEhErro() {
        Pagamento pagamento = pendente();
        assertThatThrownBy(() -> pagamento.estornar("REF-1", "x", T0)).isInstanceOf(ConflitoDeRecursoException.class);

        pagamento.aprovar("PAY-5", T0);
        assertThat(pagamento.estornar("REF-1", "FALHA_EXECUCAO", T0.plusHours(1))).isTrue();
        assertThat(pagamento.estornar("REF-1", "FALHA_EXECUCAO", T0.plusHours(1))).isFalse();

        assertThat(pagamento.getStatus()).isEqualTo(StatusPagamento.ESTORNADO);
        assertThat(pagamento.getMpRefundId()).isEqualTo("REF-1");
        assertThat(pagamento.getMotivo()).isEqualTo("FALHA_EXECUCAO");
    }
}
