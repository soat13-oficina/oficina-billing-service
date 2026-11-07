package br.com.oficina.billing.support.catalogo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Nos testes com contexto Spring, o catalogo de pecas e o em memoria: a consulta REST a Execution
 * (CatalogoPecasRest) tem teste proprio, contra um servidor HTTP local.
 */
@Configuration
public class CatalogoPecasDeTesteConfig {

    @Bean
    @Primary
    public CatalogoPecasEmMemoria catalogoPecasDeTeste() {
        return new CatalogoPecasEmMemoria();
    }
}
