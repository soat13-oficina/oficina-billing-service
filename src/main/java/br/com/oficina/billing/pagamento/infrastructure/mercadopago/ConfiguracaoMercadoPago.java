package br.com.oficina.billing.pagamento.infrastructure.mercadopago;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mercadopago.net.MPDefaultHttpClient;
import com.mercadopago.net.MPHttpClient;

/** Cliente HTTP do SDK como bean: os testes trocam por um duble, sem chamar o Mercado Pago. */
@Configuration
@EnableConfigurationProperties(PropriedadesMercadoPago.class)
public class ConfiguracaoMercadoPago {

    @Bean
    @ConditionalOnProperty(name = "pagamento.gateway", havingValue = "mercadopago", matchIfMissing = true)
    public MPHttpClient mercadoPagoHttp() {
        return new MPDefaultHttpClient();
    }
}
