package br.com.oficina.billing.pagamento.infrastructure.scheduling;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import br.com.oficina.billing.pagamento.application.ResultadoPagamentoService;

/**
 * Expira as cobrancas vencidas a cada {@code pagamento.expiracao.intervalo-ms} (30 s por padrao).
 * Desligavel por {@code pagamento.expiracao.agendamento-habilitado=false} - os testes chamam o servico direto.
 */
@Component
@ConditionalOnProperty(name = "pagamento.expiracao.agendamento-habilitado", havingValue = "true", matchIfMissing = true)
public class AgendadorExpiracaoPagamentos {
    private static final Logger log = LoggerFactory.getLogger(AgendadorExpiracaoPagamentos.class);

    private final ResultadoPagamentoService resultados;

    public AgendadorExpiracaoPagamentos(ResultadoPagamentoService resultados) {
        this.resultados = resultados;
    }

    @Scheduled(fixedDelayString = "${pagamento.expiracao.intervalo-ms:30000}")
    public void expirar() {
        try {
            int expirados = resultados.expirarVencidos(LocalDateTime.now());
            if (expirados > 0) {
                log.info("Cobrancas expiradas neste ciclo. quantidade={}", expirados);
            }
        } catch (RuntimeException e) {
            // Conflito de versao com um resultado chegando ao mesmo tempo, por exemplo: o proximo ciclo tenta de novo.
            log.warn("Falha no ciclo de expiracao de pagamentos. erro={}", e.toString());
        }
    }
}
