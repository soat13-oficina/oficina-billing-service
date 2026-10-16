package br.com.oficina.billing.common.infrastructure.seguranca;

import java.util.UUID;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;

/**
 * Cliente da requisicao, para a regra "cliente so ve e decide o que e dele" (claim {@code clienteId}
 * do token da Lambda). Funcionario: {@code null}, sem restricao. Token de cliente sem {@code clienteId}
 * valido nao enxerga nada.
 */
public final class ClienteSolicitante {

    private ClienteSolicitante() {
    }

    /**
     * @param mensagemNaoEncontrado mensagem do 404 quando o token de cliente nao identifica um cliente
     * @return id do cliente autenticado, ou {@code null} se quem chama e funcionario
     */
    public static UUID atual(String mensagemNaoEncontrado) {
        return UsuarioAutenticado.atual().filter(UsuarioAutenticado::isCliente).map(usuario -> {
            try {
                if (usuario.clienteId() != null) {
                    return UUID.fromString(usuario.clienteId());
                }
            } catch (IllegalArgumentException e) {
                // cai no 404 abaixo
            }
            throw new RecursoNaoEncontradoException(mensagemNaoEncontrado);
        }).orElse(null);
    }
}
