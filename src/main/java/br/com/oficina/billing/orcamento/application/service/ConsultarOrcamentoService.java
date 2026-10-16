package br.com.oficina.billing.orcamento.application.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.oficina.billing.orcamento.application.query.ConsultarOrcamentoQuery;
import br.com.oficina.billing.orcamento.application.usecase.ConsultarOrcamentoUseCase;
import br.com.oficina.billing.orcamento.domain.model.Orcamento;
import br.com.oficina.billing.orcamento.domain.repository.OrcamentoRepository;

@Service
public class ConsultarOrcamentoService implements ConsultarOrcamentoUseCase {
    private static final Logger log = LoggerFactory.getLogger(ConsultarOrcamentoService.class);

    private final OrcamentoRepository orcamentoRepository;

    public ConsultarOrcamentoService(OrcamentoRepository orcamentoRepository) {
        this.orcamentoRepository = orcamentoRepository;
    }

    @Override
    public List<Orcamento> consultarOrcamento(ConsultarOrcamentoQuery query) {
        log.info("Consultando orcamentos. numeroInformado={}, cpfInformado={}, placaInformada={}",
                query.numeroOrcamento() != null && !query.numeroOrcamento().isBlank(),
                query.cpfCliente() != null && !query.cpfCliente().isBlank(),
                query.placaVeiculo() != null && !query.placaVeiculo().isBlank());
        List<Orcamento> orcamentos = orcamentoRepository.buscarPorFiltros(
                query.numeroOrcamento(),
                query.cpfCliente(),
                query.placaVeiculo());
        log.info("Consulta de orcamentos finalizada. quantidadeOrcamentos={}", orcamentos.size());
        return orcamentos;
    }
}
