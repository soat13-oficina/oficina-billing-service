package br.com.oficina.billing.integracao.infrastructure.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import br.com.oficina.billing.integracao.domain.model.StatusTokenIntegracao;
import br.com.oficina.billing.integracao.domain.model.TokenIntegracao;
import br.com.oficina.billing.integracao.infrastructure.persistence.TokenIntegracaoRepository;
import br.com.oficina.billing.integracao.infrastructure.seguranca.TokenIntegracaoService.TokenGerado;
import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;

@ExtendWith(MockitoExtension.class)
class TokenIntegracaoServiceTest {

    @Mock
    private TokenIntegracaoRepository repository;

    @InjectMocks
    private TokenIntegracaoService service;

    @Test
    void deveGerarTokenComPrefixoEArmazenarApenasHash() {
        when(repository.save(any(TokenIntegracao.class))).thenAnswer(inv -> inv.getArgument(0));

        TokenGerado gerado = service.gerar("ERP X", "user@oficina.com");

        assertTrue(gerado.tokenEmClaro().startsWith("oki_"));
        // O segredo em claro NUNCA é igual ao hash persistido.
        assertNotEquals(gerado.tokenEmClaro(), gerado.token().getHashToken());
        assertEquals("ERP X", gerado.token().getRotulo());
        assertEquals("user@oficina.com", gerado.token().getCriadoPor());
        assertEquals(StatusTokenIntegracao.ATIVO, gerado.token().getStatus());
    }

    @Test
    void deveGerarTokensDiferentesEmGeracoesConsecutivas() {
        when(repository.save(any(TokenIntegracao.class))).thenAnswer(inv -> inv.getArgument(0));

        String a = service.gerar("X", "u").tokenEmClaro();
        String b = service.gerar("X", "u").tokenEmClaro();

        assertNotEquals(a, b);
    }

    @Test
    void deveRecusarGeracaoSemRotulo() {
        assertThrows(RegraDeNegocioException.class, () -> service.gerar("  ", "u"));
    }

    @Test
    void validarDeveSerVerdadeiroParaTokenAtivoEFalsoCasoContrario() {
        when(repository.findByHashTokenAndStatus(any(), eq(StatusTokenIntegracao.ATIVO)))
                .thenReturn(Optional.of(TokenIntegracao.criar("X", "hash", "u")))
                .thenReturn(Optional.empty());

        assertTrue(service.validar("oki_qualquer"));
        assertFalse(service.validar("oki_outro"));
        assertFalse(service.validar(null));
    }

    @Test
    void revogarTokenInexistenteDeveFalhar() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.revogar(id, "u"));
    }
}
