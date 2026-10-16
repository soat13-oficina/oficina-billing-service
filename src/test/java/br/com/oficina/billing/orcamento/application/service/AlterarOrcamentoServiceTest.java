package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.domain.exception.RecursoNaoEncontradoException;
import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.AlterarOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.catalogo.CatalogoPecasEmMemoria;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

/** Alteracao do orcamento no Billing: o snapshot do cliente veio da OS e nao muda aqui. */
class AlterarOrcamentoServiceTest {

    private static final String PECA_ID = "peca-001";
    private static final String PECA_ID_2 = "peca-002";
    private static final UUID CLIENTE_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final TestOrcamentoRepository repository = new TestOrcamentoRepository();
    private final CatalogoPecasEmMemoria catalogo = new CatalogoPecasEmMemoria()
            .cadastrar(PECA_ID, "Pastilha dianteira", "250.00")
            .cadastrar(PECA_ID_2, "Fluido de freio", "100.00");
    private final AlterarOrcamentoService service = new AlterarOrcamentoService(repository, catalogo);

    private static AlterarOrcamentoCommand comando(String numero, UUID clienteId, String pecaId) {
        return new AlterarOrcamentoCommand(
                numero,
                clienteId.toString(),
                "77777777-7777-7777-7777-777777777777",
                "88888888-8888-8888-8888-888888888888",
                "XYZ9Z99",
                "Honda",
                "City",
                "Revisao de freios",
                List.of("Revisao freios"),
                List.of(new PecaOrcamentoInput(pecaId, 1)),
                new BigDecimal("200.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2031, 1, 10, 10, 0),
                "Aprovacao imediata");
    }

    @Test
    void deveAlterarOrcamentoPreservandoClienteFuncionarioCriadoEmEEnvio() {
        Orcamento orcamento = novoOrcamento();
        orcamento.enviarParaAprovacao(LocalDateTime.of(2030, 1, 2, 9, 0));
        repository.salvar(orcamento);

        service.alterarOrcamento(comando("orc-1", CLIENTE_ID, PECA_ID_2));

        Orcamento atualizado = repository.buscarPorNumeroOrcamento("orc-1").orElseThrow();
        assertNotNull(atualizado.getId());
        assertEquals(UUID.fromString("77777777-7777-7777-7777-777777777777"), atualizado.getOrdemDeServicoId());
        // Funcionario de origem e imutavel: a alteracao NAO troca o funcionario que abriu (FR-009/US4).
        assertEquals(UUID.fromString("44444444-4444-4444-4444-444444444444"), atualizado.getFuncionarioId());
        assertEquals(CLIENTE_ID, atualizado.getClienteId());
        assertEquals("Joao Silva", atualizado.getClienteNome());
        assertEquals("12345678909", atualizado.getClienteCpf());
        assertEquals("XYZ9Z99", atualizado.getPlacaVeiculo());
        assertEquals("Honda", atualizado.getMarcaVeiculo());
        assertEquals("City", atualizado.getModeloVeiculo());
        assertEquals(new BigDecimal("300.00"), atualizado.getValorTotal());
        assertEquals("Fluido de freio", atualizado.getPecasOrcamento().get(0).getDescricao());
        assertEquals(LocalDateTime.of(2030, 1, 1, 10, 0), atualizado.getCriadoEm());
        assertEquals(LocalDateTime.of(2030, 1, 2, 9, 0), atualizado.getEnviadoParaAprovacaoEm());
        assertEquals(StatusOrcamento.AGUARDANDO_APROVACAO, atualizado.getStatus());
    }

    @Test
    void naoTrocaOClienteDoOrcamento() {
        repository.salvar(novoOrcamento());

        RegraDeNegocioException erro = assertThrows(RegraDeNegocioException.class,
                () -> service.alterarOrcamento(comando("orc-1", UUID.randomUUID(), PECA_ID)));

        assertEquals("O cliente do orcamento vem da ordem de servico e nao pode ser alterado.", erro.getMessage());
    }

    @Test
    void recusaPecaForaDoCatalogoEQuantidadeZeradaEIdsInvalidos() {
        repository.salvar(novoOrcamento());

        assertThrows(RecursoNaoEncontradoException.class, () -> service.alterarOrcamento(comando("orc-1", CLIENTE_ID, "sumiu")));
        assertThrows(RegraDeNegocioException.class, () -> service.alterarOrcamento(new AlterarOrcamentoCommand(
                "orc-1", CLIENTE_ID.toString(), "77777777-7777-7777-7777-777777777777", null, "XYZ9Z99", "Honda", "City",
                "Revisao", List.of(), List.of(new PecaOrcamentoInput(PECA_ID, 0)), BigDecimal.TEN, BigDecimal.ZERO,
                LocalDateTime.of(2031, 1, 10, 10, 0), null)));
        assertThrows(RegraDeNegocioException.class, () -> service.alterarOrcamento(new AlterarOrcamentoCommand(
                "orc-1", "nao-e-uuid", "77777777-7777-7777-7777-777777777777", null, "XYZ9Z99", "Honda", "City",
                "Revisao", List.of(), List.of(), BigDecimal.TEN, BigDecimal.ZERO, LocalDateTime.of(2031, 1, 10, 10, 0), null)));
    }

    @Test
    void deveFalharAoAlterarOrcamentoInexistente() {
        RecursoNaoEncontradoException exception = assertThrows(
                RecursoNaoEncontradoException.class,
                () -> service.alterarOrcamento(comando("orc-404", CLIENTE_ID, PECA_ID)));

        assertEquals("Orcamento nao encontrado para o numero informado.", exception.getMessage());
    }

    @Test
    void devePreservarStatusAprovadoERejeitadoAoAlterarOrcamento() {
        Orcamento aprovado = novoOrcamento();
        aprovado.aprovar();
        repository.salvar(aprovado);
        service.alterarOrcamento(comando("orc-1", CLIENTE_ID, PECA_ID));
        assertEquals(StatusOrcamento.APROVADO, repository.buscarPorNumeroOrcamento("orc-1").orElseThrow().getStatus());

        TestOrcamentoRepository outro = new TestOrcamentoRepository();
        Orcamento rejeitado = novoOrcamento();
        rejeitado.rejeitar();
        outro.salvar(rejeitado);
        new AlterarOrcamentoService(outro, catalogo).alterarOrcamento(comando("orc-1", CLIENTE_ID, PECA_ID));
        assertEquals(StatusOrcamento.REJEITADO, outro.buscarPorNumeroOrcamento("orc-1").orElseThrow().getStatus());
    }

    private Orcamento novoOrcamento() {
        return new Orcamento(
                "orc-1",
                CLIENTE_ID,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                "Joao Silva",
                "12345678909",
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de pastilhas",
                List.of("Troca de pastilhas"),
                List.of(new PecaOrcamento(PECA_ID, "Pastilha dianteira", new BigDecimal("250.00"), 1)),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 1, 1, 10, 0),
                LocalDateTime.of(2030, 1, 10, 10, 0),
                "Prioridade alta",
                StatusOrcamento.AGUARDANDO_APROVACAO);
    }
}
