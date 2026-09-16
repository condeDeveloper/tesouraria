package br.com.conde.tesouraria.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Valor monetário imutável: quantia em BigDecimal e código ISO 4217 da moeda.
 * Operações entre moedas diferentes são proibidas; a conversão é responsabilidade
 * do módulo de câmbio.
 */
public final class Money implements Comparable<Money> {

    public static final int ESCALA = 2;
    public static final RoundingMode ARREDONDAMENTO = RoundingMode.HALF_EVEN;

    private final BigDecimal quantia;
    private final String moeda;

    private Money(BigDecimal quantia, String moeda) {
        this.quantia = quantia.setScale(ESCALA, ARREDONDAMENTO);
        this.moeda = moeda;
    }

    public static Money of(BigDecimal quantia, String moeda) {
        Objects.requireNonNull(quantia, "quantia obrigatória");
        return new Money(quantia, CodigoMoeda.validar(moeda));
    }

    public static Money of(String quantia, String moeda) {
        return of(new BigDecimal(quantia), moeda);
    }

    public static Money of(double quantia, String moeda) {
        return of(BigDecimal.valueOf(quantia), moeda);
    }

    public static Money zero(String moeda) {
        return of(BigDecimal.ZERO, moeda);
    }

    public BigDecimal quantia() { return quantia; }
    public String moeda() { return moeda; }

    public Money mais(Money outro) {
        exigirMesmaMoeda(outro);
        return new Money(quantia.add(outro.quantia), moeda);
    }

    public Money menos(Money outro) {
        exigirMesmaMoeda(outro);
        return new Money(quantia.subtract(outro.quantia), moeda);
    }

    public Money vezes(BigDecimal fator) {
        return new Money(quantia.multiply(fator), moeda);
    }

    public Money dividido(BigDecimal divisor) {
        if (divisor.signum() == 0) throw new DomainException("divisão por zero");
        return new Money(quantia.divide(divisor, ESCALA + 6, ARREDONDAMENTO), moeda);
    }

    public Money negado() { return new Money(quantia.negate(), moeda); }
    public Money absoluto() { return new Money(quantia.abs(), moeda); }

    public boolean ehZero() { return quantia.signum() == 0; }
    public boolean ehPositivo() { return quantia.signum() > 0; }
    public boolean ehNegativo() { return quantia.signum() < 0; }

    public boolean mesmaMoeda(Money outro) { return moeda.equals(outro.moeda); }

    private void exigirMesmaMoeda(Money outro) {
        if (!mesmaMoeda(outro)) {
            throw new DomainException("moedas diferentes: " + moeda + " e " + outro.moeda);
        }
    }

    @Override
    public int compareTo(Money outro) {
        exigirMesmaMoeda(outro);
        return quantia.compareTo(outro.quantia);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money m)) return false;
        return quantia.compareTo(m.quantia) == 0 && moeda.equals(m.moeda);
    }

    @Override
    public int hashCode() { return Objects.hash(quantia.stripTrailingZeros(), moeda); }

    @Override
    public String toString() { return moeda + " " + quantia.toPlainString(); }
}
