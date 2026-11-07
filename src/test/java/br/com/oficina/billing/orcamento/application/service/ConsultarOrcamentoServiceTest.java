package br.com.oficina.billing.orcamento.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.oficina.billing.orcamento.application.query.ConsultarOrcamentoQuery;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.model.PecaOrcamento;
import br.com.oficina.billing.support.persistence.TestOrcamentoRepository;

class ConsultarOrcamentoServiceTest {

    @Test
    void deveConsultarOrcamentoPorId() {
        TestOrcamentoRepository repository = new TestOrcamentoRepository();
        repository.salvar(novoOrcamento());
        ConsultarOrcamentoService service = new ConsultarOrcamentoService(repository);

        Orcamento orcamento = service.consultarOrcamento(new ConsultarOrcamentoQuery("orc-1", null, null)).getFirst();

        assertEquals("orc-1", orcamento.getNumeroOrcamento());
    }

    @Test
    void deveConsultarOrcamentoPorCpfClienteOuPlacaVeiculo() {
        TestOrcamentoRepository repository = new TestOrcamentoRepository();
        repository.salvar(novoOrcamento());
        repository.salvar(novoOrcamento("orc-2", "Maria Souza", "20100000053", "XYZ1A23"));
        ConsultarOrcamentoService service = new ConsultarOrcamentoService(repository);

        List<Orcamento> porCpf = service.consultarOrcamento(new ConsultarOrcamentoQuery(null, "12345678909", null));
        List<Orcamento> porPlaca = service.consultarOrcamento(new ConsultarOrcamentoQuery(null, null, "XYZ1A23"));

        assertEquals(1, porCpf.size());
        assertEquals("orc-1", porCpf.getFirst().getNumeroOrcamento());
        assertEquals(1, porPlaca.size());
        assertEquals("orc-2", porPlaca.getFirst().getNumeroOrcamento());
    }

    private Orcamento novoOrcamento() {
        return novoOrcamento("orc-1", "Joao Silva", "12345678909", "ABC1D23");
    }

    private Orcamento novoOrcamento(String numeroOrcamento, String clienteNome, String clienteCpf, String placaVeiculo) {
        return new Orcamento(
                numeroOrcamento,
                UUID.fromString("33333333-3333-3333-3333-333333333333"),
                UUID.fromString("44444444-4444-4444-4444-444444444444"),
                clienteNome,
                clienteCpf,
                placaVeiculo,
                "Toyota",
                "Corolla",
                "Troca de pastilhas",
                List.of("Troca de pastilhas"),
                List.of(new PecaOrcamento("peca-001", "Pastilha dianteira", new BigDecimal("250.00"), 1)),
                new BigDecimal("150.00"),
                BigDecimal.ZERO,
                LocalDateTime.of(2030, 1, 1, 10, 0),
                LocalDateTime.of(2030, 1, 10, 10, 0),
                "Prioridade alta",
                br.com.oficina.billing.orcamento.domain.model.StatusOrcamento.AGUARDANDO_APROVACAO);
    }
}
