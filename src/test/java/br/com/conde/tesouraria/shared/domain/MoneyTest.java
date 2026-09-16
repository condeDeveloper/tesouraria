package br.com.conde.tesouraria.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void somaESubtraiNaMesmaMoeda() {
        Money a = Money.of("100.10", "BRL");
        Money b = Money.of("0.90", "BRL");
        assertThat(a.mais(b)).isEqualTo(Money.of("101.00", "BRL"));
        assertThat(a.menos(b)).isEqualTo(Money.of("99.20", "BRL"));
    }

    @Test
    void recusaOperacaoEntreMoedasDiferentes() {
        assertThatThrownBy(() -> Money.of("1", "BRL").mais(Money.of("1", "USD")))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("moedas diferentes");
    }

    @Test
    void arredondaComBankersRounding() {
        assertThat(Money.of("2.345", "USD").quantia()).isEqualByComparingTo("2.34");
        assertThat(Money.of("2.355", "USD").quantia()).isEqualByComparingTo("2.36");
    }

    @Test
    void multiplicaEDivide() {
        Money m = Money.of("10", "USD");
        assertThat(m.vezes(new BigDecimal("5.5"))).isEqualTo(Money.of("55", "USD"));
        assertThat(m.dividido(new BigDecimal("3"))).isEqualTo(Money.of("3.33", "USD"));
        assertThatThrownBy(() -> m.dividido(BigDecimal.ZERO)).isInstanceOf(DomainException.class);
    }

    @Test
    void igualdadeIgnoraEscala() {
        assertThat(Money.of("5", "EUR")).isEqualTo(Money.of("5.00", "EUR"));
        assertThat(Money.of("5", "EUR").hashCode()).isEqualTo(Money.of("5.00", "EUR").hashCode());
    }

    @Test
    void validaCodigoDaMoeda() {
        assertThatThrownBy(() -> Money.of("1", "real")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Money.of("1", "BR")).isInstanceOf(DomainException.class);
        assertThat(CodigoMoeda.normalizar(" usd ")).isEqualTo("USD");
    }

    @Test
    void sinaisEComparacao() {
        assertThat(Money.zero("BRL").ehZero()).isTrue();
        assertThat(Money.of("-1", "BRL").ehNegativo()).isTrue();
        assertThat(Money.of("2", "BRL")).isGreaterThan(Money.of("1", "BRL"));
        assertThat(Money.of("-3", "BRL").absoluto()).isEqualTo(Money.of("3", "BRL"));
    }
}
