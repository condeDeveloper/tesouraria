package br.com.conde.tesouraria.mercado.domain;

import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.DomainException;

/**
 * Par de moedas na convenção de mercado: BASE seguida da COTADA. Em USDBRL, a taxa diz
 * quantos BRL compram 1 USD.
 */
public record ParMoedas(String base, String cotada) {

    public ParMoedas {
        base = CodigoMoeda.normalizar(base);
        cotada = CodigoMoeda.normalizar(cotada);
        if (base.equals(cotada)) throw new DomainException("par de moedas com a mesma moeda: " + base);
    }

    public static ParMoedas de(String codigo) {
        if (codigo == null || codigo.replace("/", "").length() != 6) throw new DomainException("par de moedas inválido: " + codigo);
        String c = codigo.replace("/", "").toUpperCase();
        return new ParMoedas(c.substring(0, 3), c.substring(3));
    }

    public String codigo() { return base + cotada; }

    public ParMoedas inverso() { return new ParMoedas(cotada, base); }

    public boolean envolve(String moeda) { return base.equals(moeda) || cotada.equals(moeda); }

    @Override
    public String toString() { return codigo(); }
}
