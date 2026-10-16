package br.com.oficina.billing.common.infrastructure.seguranca;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Identidade de quem fez a requisicao, montada so a partir das claims do JWT.
 *
 * <p>Este servico nao tem tabela de usuarios: confia na assinatura do token, emitido pelo
 * OS Service (funcionario) ou pela Lambda de autenticacao (cliente), com o segredo
 * compartilhado {@code oficina/<env>/jwt-secret}.
 *
 * @param subject   e-mail do funcionario ou CPF do cliente
 * @param tipo      {@link TipoUsuario#FUNCIONARIO} ou {@link TipoUsuario#CLIENTE}
 * @param clienteId id do cliente (so em token de cliente); usado para conferir que o cliente
 *                  decide apenas o proprio orcamento
 * @param nome      nome exibivel, quando o token traz
 */
public record UsuarioAutenticado(String subject, TipoUsuario tipo, String clienteId, String nome) {

    public boolean isCliente() {
        return tipo == TipoUsuario.CLIENTE;
    }

    /** Usuario da requisicao corrente, se autenticada por JWT. */
    public static Optional<UsuarioAutenticado> atual() {
        Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
        return autenticacao != null && autenticacao.getPrincipal() instanceof UsuarioAutenticado usuario
                ? Optional.of(usuario)
                : Optional.empty();
    }

    public enum TipoUsuario {
        FUNCIONARIO,
        CLIENTE
    }
}
