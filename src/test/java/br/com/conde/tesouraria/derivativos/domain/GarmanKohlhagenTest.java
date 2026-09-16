package br.com.conde.tesouraria.derivativos.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import static br.com.conde.tesouraria.derivativos.domain.GarmanKohlhagen.Tipo.CALL;
import static br.com.conde.tesouraria.derivativos.domain.GarmanKohlhagen.Tipo.PUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GarmanKohlhagenTest {

    // S = 5,30 ; K = 5,40 ; T = 0,5 ano ; rd = 14% ; rf = 5% (contínuas) ; σ = 15%
    private final double S = 5.30, K = 5.40, T = 0.5, rd = 0.14, rf = 0.05, vol = 0.15;
    private final double dfDom = Math.exp(-rd * T), dfEst = Math.exp(-rf * T);

    @Test
    void cdfDaNormalPadrao() {
        assertThat(GarmanKohlhagen.cdf(0)).isCloseTo(0.5, within(1e-9));
        assertThat(GarmanKohlhagen.cdf(1.96)).isCloseTo(0.9750021, within(1e-6));
        assertThat(GarmanKohlhagen.cdf(-1.96)).isCloseTo(0.0249979, within(1e-6));
        assertThat(GarmanKohlhagen.cdf(3)).isCloseTo(0.9986501, within(1e-6));
    }

    @Test
    void paridadePutCall() {
        var c = GarmanKohlhagen.precificar(CALL, S, K, T, dfDom, dfEst, vol);
        var p = GarmanKohlhagen.precificar(PUT, S, K, T, dfDom, dfEst, vol);
        // C − P = S·e^{−rf T} − K·e^{−rd T}
        assertThat(c.preco() - p.preco()).isCloseTo(S * dfEst - K * dfDom, within(1e-9));
        assertThat(c.forward()).isCloseTo(S * Math.exp((rd - rf) * T), within(1e-9));
    }

    @Test
    void valorConhecidoDeReferencia() {
        // Cálculo de referência com as mesmas fórmulas fechadas
        double F = S * Math.exp((rd - rf) * T);
        double d1 = (Math.log(F / K) + 0.5 * vol * vol * T) / (vol * Math.sqrt(T));
        double d2 = d1 - vol * Math.sqrt(T);
        double esperado = dfDom * (F * GarmanKohlhagen.cdf(d1) - K * GarmanKohlhagen.cdf(d2));
        var c = GarmanKohlhagen.precificar(CALL, S, K, T, dfDom, dfEst, vol);
        assertThat(c.preco()).isCloseTo(esperado, within(1e-10));
        assertThat(c.preco()).isBetween(0.15, 0.30); // ordem de grandeza plausível para o cenário
    }

    @Test
    void gregasTemSinalEIntervaloCorretos() {
        var c = GarmanKohlhagen.precificar(CALL, S, K, T, dfDom, dfEst, vol);
        var p = GarmanKohlhagen.precificar(PUT, S, K, T, dfDom, dfEst, vol);
        assertThat(c.delta()).isBetween(0.0, 1.0);
        assertThat(p.delta()).isBetween(-1.0, 0.0);
        assertThat(c.delta() - p.delta()).isCloseTo(dfEst, within(1e-9)); // delta_call − delta_put = e^{−rf T}
        assertThat(c.gamma()).isPositive().isCloseTo(p.gamma(), within(1e-12));
        assertThat(c.vega()).isPositive().isCloseTo(p.vega(), within(1e-12));
        assertThat(c.rho()).isPositive();
        assertThat(p.rho()).isNegative();
    }

    @Test
    void deltaPorDiferencasFinitasConfereComAFormula() {
        double h = 1e-5;
        var base = GarmanKohlhagen.precificar(CALL, S, K, T, dfDom, dfEst, vol);
        var up = GarmanKohlhagen.precificar(CALL, S + h, K, T, dfDom, dfEst, vol);
        var down = GarmanKohlhagen.precificar(CALL, S - h, K, T, dfDom, dfEst, vol);
        assertThat((up.preco() - down.preco()) / (2 * h)).isCloseTo(base.delta(), within(1e-6));
        assertThat((up.preco() - 2 * base.preco() + down.preco()) / (h * h)).isCloseTo(base.gamma(), within(1e-3));
    }

    @Test
    void payoffNoVencimento() {
        assertThat(GarmanKohlhagen.payoff(CALL, 5.60, 5.40)).isCloseTo(0.20, within(1e-12));
        assertThat(GarmanKohlhagen.payoff(CALL, 5.20, 5.40)).isZero();
        assertThat(GarmanKohlhagen.payoff(PUT, 5.20, 5.40)).isCloseTo(0.20, within(1e-12));
        assertThat(GarmanKohlhagen.payoff(PUT, 5.60, 5.40)).isZero();
    }

    @Test
    void validaEntradas() {
        assertThatThrownBy(() -> GarmanKohlhagen.precificar(CALL, 0, K, T, dfDom, dfEst, vol)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> GarmanKohlhagen.precificar(CALL, S, K, 0, dfDom, dfEst, vol)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> GarmanKohlhagen.precificar(CALL, S, K, T, dfDom, dfEst, 0)).isInstanceOf(DomainException.class);
    }
}
