package br.com.conde.tesouraria.derivativos.domain;

import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContratoNdfTest {

    private final UUID cp = UUID.randomUUID();
    private final LocalDate d0 = LocalDate.of(2026, 9, 15), fix = LocalDate.of(2026, 12, 14), liq = LocalDate.of(2026, 12, 15);

    private ContratoNdf ndf(LadoOperacao lado) {
        return new ContratoNdf("NDF-2026-000001", cp, ParMoedas.de("USDBRL"), lado, new BigDecimal("1000000"),
                new BigDecimal("5.4200"), new BigDecimal("5.4150"), d0, fix, liq, "mesa");
    }

    @Test
    void compraGanhaComAltaDaPtax() {
        ContratoNdf c = ndf(LadoOperacao.COMPRA);
        // PTAX 5,50 contra termo 5,42: (5,50 - 5,42) x 1.000.000 = 80.000 a receber
        assertThat(c.ajustePara(new BigDecimal("5.50"))).isEqualTo(Money.of("80000", "BRL"));
        assertThat(c.ajustePara(new BigDecimal("5.30"))).isEqualTo(Money.of("-120000", "BRL"));
    }

    @Test
    void vendaGanhaComQuedaDaPtax() {
        ContratoNdf c = ndf(LadoOperacao.VENDA);
        assertThat(c.ajustePara(new BigDecimal("5.30"))).isEqualTo(Money.of("120000", "BRL"));
        assertThat(c.ajustePara(new BigDecimal("5.50"))).isEqualTo(Money.of("-80000", "BRL"));
    }

    @Test
    void valorPresenteDescontaOAjusteAoForward() {
        ContratoNdf c = ndf(LadoOperacao.COMPRA);
        // forward 5,47, ajuste bruto 50.000, fator de desconto 0,97 -> 48.500
        assertThat(c.valorPresente(new BigDecimal("5.47"), new BigDecimal("0.97"))).isEqualTo(Money.of("48500", "BRL"));
    }

    @Test
    void cicloFixarELiquidar() {
        ContratoNdf c = ndf(LadoOperacao.COMPRA);
        assertThatThrownBy(() -> c.liquidar(liq)).hasMessageContaining("fixado");
        c.fixar(new BigDecimal("5.4700"));
        assertThat(c.getSituacao()).isEqualTo(SituacaoContrato.FIXADO);
        assertThat(c.getAjuste()).isEqualByComparingTo("50000.0000");
        assertThatThrownBy(() -> c.liquidar(fix)).hasMessageContaining("só liquida em");
        c.liquidar(liq);
        assertThat(c.getSituacao()).isEqualTo(SituacaoContrato.LIQUIDADO);
        assertThatThrownBy(c::cancelar).isInstanceOf(DomainException.class);
    }

    @Test
    void validacoes() {
        assertThatThrownBy(() -> new ContratoNdf("X", cp, ParMoedas.de("BRLUSD"), LadoOperacao.COMPRA, BigDecimal.ONE, BigDecimal.ONE, null, d0, fix, liq, "m"))
                .hasMessageContaining("cotado em BRL");
        assertThatThrownBy(() -> new ContratoNdf("X", cp, ParMoedas.de("USDBRL"), LadoOperacao.COMPRA, BigDecimal.ONE, BigDecimal.ONE, null, d0, liq, fix, "m"))
                .hasMessageContaining("fora de ordem");
        assertThatThrownBy(() -> ndf(LadoOperacao.COMPRA).fixar(BigDecimal.ZERO)).hasMessageContaining("inválida");
        ContratoNdf c = ndf(LadoOperacao.COMPRA);
        c.cancelar();
        assertThat(c.getSituacao()).isEqualTo(SituacaoContrato.CANCELADO);
        assertThatThrownBy(() -> c.fixar(BigDecimal.ONE)).hasMessageContaining("não está aberto");
    }
}
