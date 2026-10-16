package br.com.oficina.billing.integracao.infrastructure.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.oficina.billing.integracao.domain.model.StatusTokenIntegracao;
import br.com.oficina.billing.integracao.domain.model.TokenIntegracao;
import br.com.oficina.billing.integracao.infrastructure.persistence.TokenIntegracaoRepository;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;

@Service
public class TokenIntegracaoService {
    private static final Logger log = LoggerFactory.getLogger(TokenIntegracaoService.class);
    private static final String PREFIXO = "oki_";
    private static final int BYTES_SEGREDO = 32;

    private final TokenIntegracaoRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public TokenIntegracaoService(TokenIntegracaoRepository repository) {
        this.repository = repository;
    }

    /**
     * Resultado da geração: a entidade persistida e o token em claro (exibido uma única vez).
     */
    public record TokenGerado(TokenIntegracao token, String tokenEmClaro) {
    }

    public TokenGerado gerar(String rotulo, String criadoPor) {
        if (rotulo == null || rotulo.isBlank()) {
            throw new RegraDeNegocioException("Rotulo do token de integracao e obrigatorio.");
        }
        byte[] aleatorio = new byte[BYTES_SEGREDO];
        secureRandom.nextBytes(aleatorio);
        String tokenEmClaro = PREFIXO + Base64.getUrlEncoder().withoutPadding().encodeToString(aleatorio);
        TokenIntegracao token = TokenIntegracao.criar(rotulo, hash(tokenEmClaro), criadoPor);
        TokenIntegracao salvo = repository.save(token);
        log.info("Token de integracao gerado. id={}, rotulo={}, criadoPor={}", salvo.getId(), rotulo, criadoPor);
        return new TokenGerado(salvo, tokenEmClaro);
    }

    public boolean validar(String tokenEmClaro) {
        if (tokenEmClaro == null || tokenEmClaro.isBlank()) {
            return false;
        }
        return repository.findByHashTokenAndStatus(hash(tokenEmClaro), StatusTokenIntegracao.ATIVO).isPresent();
    }

    public void revogar(UUID id, String revogadoPor) {
        TokenIntegracao token = repository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Token de integracao nao encontrado."));
        token.revogar(revogadoPor);
        repository.save(token);
        log.info("Token de integracao revogado. id={}, revogadoPor={}", id, revogadoPor);
    }

    public List<TokenIntegracao> listar() {
        return repository.findAll();
    }

    private static String hash(String valor) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(valor.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponivel.", e);
        }
    }
}
