package br.com.oficina.billing.orcamento.domain.model;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class PecaOrcamento {
    @Column(name = "peca_insumo_id", nullable = false)
    private String pecaInsumoId;

    @Column(name = "descricao", nullable = false)
    private String descricao;

    @Column(name = "preco", nullable = false, precision = 19, scale = 2)
    private BigDecimal preco;

    @Column(name = "quantidade", nullable = false)
    private int quantidade;

    protected PecaOrcamento() {
    }

    public PecaOrcamento(String pecaInsumoId, String descricao, BigDecimal preco, int quantidade) {
        this.pecaInsumoId = pecaInsumoId;
        this.descricao = descricao;
        this.preco = preco;
        this.quantidade = quantidade;
    }

    public String getPecaInsumoId() {
        return pecaInsumoId;
    }

    public String getDescricao() {
        return descricao;
    }

    public BigDecimal getPreco() {
        return preco;
    }

    public int getQuantidade() {
        return quantidade;
    }
}
