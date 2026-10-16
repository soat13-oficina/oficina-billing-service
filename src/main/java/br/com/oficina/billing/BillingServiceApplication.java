package br.com.oficina.billing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * Sem usuario em memoria: a autenticacao e so por JWT (e pelos tokens dos webhooks). Sem a exclusao,
 * o Spring cria um usuario padrao e escreve a senha gerada no log.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BillingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(BillingServiceApplication.class, args);
    }
}
