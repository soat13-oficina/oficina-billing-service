package br.com.oficina.billing.common.infrastructure.mensageria;

import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.stereotype.Component;

/**
 * Garante que so uma replica publica o outbox por vez (ADR 0005). Com duas replicas lendo as
 * mesmas pendentes, a ordem por OS poderia se inverter entre elas.
 *
 * <p>No PostgreSQL usa {@code pg_try_advisory_xact_lock}: nao bloqueia (quem nao conseguiu pula o
 * ciclo) e e liberado sozinho no fim da transacao do publicador - inclusive se o pod morrer. Em
 * outro banco (H2 dos testes, uma instancia so) a trava e sempre concedida.
 */
@Component
public class TravaPublicacaoOutbox {

    /** Chave arbitraria e fixa do advisory lock do outbox. */
    static final long CHAVE = 4_210_005L;

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgres;

    @Autowired
    public TravaPublicacaoOutbox(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this(jdbcTemplate, ehPostgres(dataSource));
    }

    TravaPublicacaoOutbox(JdbcTemplate jdbcTemplate, boolean postgres) {
        this.jdbcTemplate = jdbcTemplate;
        this.postgres = postgres;
    }

    /** Precisa ser chamado dentro da transacao do publicador. */
    public boolean tentarAdquirir() {
        if (!postgres) {
            return true;
        }
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("select pg_try_advisory_xact_lock(?)", Boolean.class, CHAVE));
    }

    private static boolean ehPostgres(DataSource dataSource) {
        try {
            String produto = JdbcUtils.extractDatabaseMetaData(dataSource, DatabaseMetaData::getDatabaseProductName);
            return "PostgreSQL".equalsIgnoreCase(produto);
        } catch (MetaDataAccessException e) {
            return false;
        }
    }
}
