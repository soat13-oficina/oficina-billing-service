package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.common.domain.exception.RegraDeNegocioException;
import br.com.oficina.billing.orcamento.application.command.CadastrarNovoOrcamentoCommand;
import br.com.oficina.billing.orcamento.application.command.PecaOrcamentoInput;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.orcamento.domain.model.StatusOrcamento;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;
import br.com.oficina.billing.support.catalogo.CatalogoPecasEmMemoria;

/** Fase 4: o orcamento nasce do snapshot do comando GerarOrcamento - sem consultar o cliente. */
class CadastrarNovoOrcamentoServiceTest {

    private static final String PECA_ID = "peca-001";
    private static final UUID CLIENTE_ID = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final UUID SAGA_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");

    private final TestOrcamentoRepository repository = new TestOrcamentoRepository();
    private final CatalogoPecasEmMemoria catalogo = new CatalogoPecasEmMemoria();
    private final CadastrarNovoOrcamentoService service =
            new CadastrarNovoOrcamentoService(repository, catalogo);

    @Test
    void cadastraComSnapshotDoClienteEPrecoDoCatalogo() {
        catalogo.cadastrar(PECA_ID, "Pastilha dianteira", "250.00");

        UUID id = service.cadastrarNovoOrcamento(comando("orc-1", List.of(new PecaOrcamentoInput(PECA_ID, 1)), new BigDecimal("150.00"), BigDecimal.ZERO));

        Orcamento orcamento = repository.buscarPorNumeroOrcamento("orc-1").orElseThrow();
        assertNotNull(id);
        assertEquals(id, orcamento.getId());
        assertEquals(new BigDecimal("400.00"), orcamento.getValorTotal());
        assertEquals(StatusOrcamento.AGUARDANDO_APROVACAO, orcamento.getStatus());
        assertEquals(CLIENTE_ID, orcamento.getClienteId());
        assertEquals("Joao Silva", orcamento.getClienteNome());
        assertEquals("12345678909", orcamento.getClienteCpf());
        assertEquals(SAGA_ID, orcamento.getSagaId());
        assertEquals("Pastilha dianteira", orcamento.getPecasOrcamento().get(0).getDescricao());
    }

    @Test
    void numeroRepetidoEConflitoDeNegocio() {
        repository.salvar(orcamentoExistente("orc-1", UUID.randomUUID()));

        RegraDeNegocioException exception = assertThrows(RegraDeNegocioException.class,
                () -> service.cadastrarNovoOrcamento(comando("orc-1", List.of(), new BigDecimal("100.00"), BigDecimal.ZERO)));

        assertEquals("Ja existe orcamento cadastrado com o mesmo numero.", exception.getMessage());
    }

    @Test
    void umOrcamentoPorOrdemDeServico() {
        repository.salvar(orcamentoExistente("orc-antigo", UUID.fromString("33333333-3333-3333-3333-333333333333")));

        RegraDeNegocioException exception = assertThrows(RegraDeNegocioException.class,
                () -> service.cadastrarNovoOrcamento(comando("orc-2", List.of(), new BigDecimal("100.00"), BigDecimal.ZERO)));

        assertEquals("Ja existe orcamento para a ordem de servico informada.", exception.getMessage());
    }

    @Test
    void listaTodasAsPecasInexistentes() {
        catalogo.cadastrar(PECA_ID, "Pastilha", "250.00");

        PecasNaoEncontradasException exception = assertThrows(PecasNaoEncontradasException.class,
                () -> service.cadastrarNovoOrcamento(comando("orc-1", List.of(
                        new PecaOrcamentoInput(PECA_ID, 1),
                        new PecaOrcamentoInput("sumiu-1", 1),
                        new PecaOrcamentoInput("sumiu-2", 2)), new BigDecimal("100.00"), BigDecimal.ZERO)));

        assertEquals(List.of("sumiu-1", "sumiu-2"), exception.getPecaInsumoIds());
    }

    @Test
    void recusaQuantidadeZeradaMaoDeObraAusenteValidadeAusenteEDescontoMaiorQueOTotal() {
        catalogo.cadastrar(PECA_ID, "Pastilha", "10.00");

        assertThrows(RegraDeNegocioException.class, () -> service.cadastrarNovoOrcamento(
                comando("orc-1", List.of(new PecaOrcamentoInput(PECA_ID, 0)), new BigDecimal("100.00"), BigDecimal.ZERO)));
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrarNovoOrcamento(
                comando("orc-2", List.of(), null, BigDecimal.ZERO)));
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrarNovoOrcamento(
                comando("orc-3", List.of(), new BigDecimal("-1.00"), BigDecimal.ZERO)));
        assertThrows(RegraDeNegocioException.class, () -> service.cadastrarNovoOrcamento(new CadastrarNovoOrcamentoCommand(
                "orc-4", cliente(), UUID.randomUUID(), UUID.randomUUID(), "ABC1D23", "Toyota", "Corolla", "Revisao",
                List.of(), List.of(), new BigDecimal("10.00"), null, null, null, SAGA_ID)));
        RegraDeNegocioException desconto = assertThrows(RegraDeNegocioException.class, () -> service.cadastrarNovoOrcamento(
                comando("orc-5", List.of(new PecaOrcamentoInput(PECA_ID, 1)), new BigDecimal("10.00"), new BigDecimal("50.00"))));
        assertEquals("Desconto nao pode ser maior que o valor total do orcamento.", desconto.getMessage());
    }

    private static CadastrarNovoOrcamentoCommand comando(String numero, List<PecaOrcamentoInput> pecas, BigDecimal maoDeObra, BigDecimal desconto) {
        return new CadastrarNovoOrcamentoCommand(
                numero,
                cliente(),
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("66666666-6666-6666-6666-666666666666"),
                "ABC1D23",
                "Toyota",
                "Corolla",
                "Troca de pastilhas",
                List.of("Troca de pastilhas"),
                pecas,
                maoDeObra,
                desconto,
                LocalDateTime.of(2030, 1, 10, 10, 0),
                "Prioridade alta",
                SAGA_ID);
    }

    private static CadastrarNovoOrcamentoCommand.ClienteOrcamento cliente() {
        return new CadastrarNovoOrcamentoCommand.ClienteOrcamento(CLIENTE_ID, "Joao Silva", "12345678909");
    }

    private static Orcamento orcamentoExistente(String numero, UUID ordemDeServicoId) {
        return new Orcamento(
                numero,
                CLIENTE_ID,
                ordemDeServicoId,
                UUID.randomUUID(),
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
