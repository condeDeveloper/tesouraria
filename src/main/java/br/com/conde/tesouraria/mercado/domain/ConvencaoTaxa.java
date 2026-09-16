package br.com.conde.tesouraria.mercado.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Como uma taxa anual vira fator de capitalização para um prazo. */
public enum ConvencaoTaxa {

    /** Exponencial, base 252 dias úteis: fator = (1 + i)^(du/252). Padrão do DI. */
    EXP_252 {
        @Override
        public BigDecimal fator(BigDecimal taxaAnual, int prazoDias) {
            double f = Math.pow(1 + taxaAnual.doubleValue(), prazoDias / 252.0);
            return new BigDecimal(f, MC).setScale(12, RoundingMode.HALF_EVEN);
        }
        @Override
        public boolean usaDiasUteis() { return true; }
    },

    /** Linear, base 360 dias corridos: fator = 1 + i * dc/360. Padrão do cupom cambial. */
    LINEAR_360 {
        @Override
        public BigDecimal fator(BigDecimal taxaAnual, int prazoDias) {
            return BigDecimal.ONE.add(taxaAnual.multiply(BigDecimal.valueOf(prazoDias)).divide(BigDecimal.valueOf(360), 12, RoundingMode.HALF_EVEN));
        }
        @Override
        public boolean usaDiasUteis() { return false; }
    };

    static final MathContext MC = new MathContext(20, RoundingMode.HALF_EVEN);

    public abstract BigDecimal fator(BigDecimal taxaAnual, int prazoDias);

    /** true se o prazo é medido em dias úteis, false se em dias corridos. */
    public abstract boolean usaDiasUteis();

    public BigDecimal fatorDesconto(BigDecimal taxaAnual, int prazoDias) {
        return BigDecimal.ONE.divide(fator(taxaAnual, prazoDias), 12, RoundingMode.HALF_EVEN);
    }
}
