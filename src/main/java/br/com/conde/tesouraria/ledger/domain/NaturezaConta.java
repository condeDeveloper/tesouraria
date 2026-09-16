package br.com.conde.tesouraria.ledger.domain;

import java.math.BigDecimal;

/** Define o sinal do saldo: conta devedora cresce com débitos, credora cresce com créditos. */
public enum NaturezaConta {
    DEVEDORA,
    CREDORA;

    /** Saldo a partir dos totais brutos de débitos e créditos. */
    public BigDecimal saldo(BigDecimal debitos, BigDecimal creditos) {
        return this == DEVEDORA ? debitos.subtract(creditos) : creditos.subtract(debitos);
    }
}
