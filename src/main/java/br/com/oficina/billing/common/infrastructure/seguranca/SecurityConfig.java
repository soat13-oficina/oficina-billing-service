package br.com.oficina.billing.common.infrastructure.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import br.com.oficina.billing.integracao.infrastructure.seguranca.WebhookTokenFilter;

/**
 * JWT para tudo, com tres excecoes: documentacao, health/metricas e o webhook de decisao de
 * orcamento (autenticado pelo {@code X-Webhook-Token}). Cliente (token da Lambda) so consulta e decide
 * orcamentos - e so os proprios, regra aplicada no controller; alterar, excluir e gerir tokens de
 * integracao e coisa de funcionario.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String FUNCIONARIO = UsuarioAutenticado.TipoUsuario.FUNCIONARIO.name();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final WebhookTokenFilter webhookTokenFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, WebhookTokenFilter webhookTokenFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.webhookTokenFilter = webhookTokenFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        // Em nuvem o management responde na 8081, fora do Service e do Ingress;
                        // o permitAll serve ao desenvolvimento local, onde tudo vive na 8080.
                        .requestMatchers("/actuator/prometheus").permitAll()
                        // Sistema externo do cliente: X-Webhook-Token, validado pelo WebhookTokenFilter.
                        .requestMatchers(HttpMethod.POST, "/integracoes/orcamentos/*/decisao").permitAll()
                        .requestMatchers("/integracoes/tokens/**").hasRole(FUNCIONARIO)
                        .requestMatchers(HttpMethod.PUT, "/orcamentos/**").hasRole(FUNCIONARIO)
                        .requestMatchers(HttpMethod.DELETE, "/orcamentos/**").hasRole(FUNCIONARIO)
                        .anyRequest().authenticated())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sem token valido: 401 (e nao o 403 padrao), para o cliente saber que precisa autenticar.
                .exceptionHandling(erros -> erros.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(webhookTokenFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
