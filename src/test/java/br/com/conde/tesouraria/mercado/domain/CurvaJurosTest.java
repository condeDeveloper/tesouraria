package br.com.conde.tesouraria.mercado.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CurvaJurosTest {

    private final LocalDate ref = LocalDate.of(2026, 9, 15);
    private final CurvaJuros di = new CurvaJuros("DI", ref, ConvencaoTaxa.EXP_252,
            Map.of(21, new BigDecimal("0.14"), 63, new BigDecimal("0.13"), 252, new BigDecimal("0.12")));

    @Test
    void interpolaLinearmenteEntreVertices() {
        // metade do caminho entre 21 (14%) e 63 (13%) -> 13,5%
        assertThat(di.taxaPara(42)).isEqualByComparingTo("0.13500000");
        assertThat(di.taxaPara(21)).isEqualByComparingTo("0.14");
        assertThat(di.taxaPara(252)).isEqualByComparingTo("0.12");
    }

    @Test
    void extrapolaFlatForaDosVertices() {
        assertThat(di.taxaPara(1)).isEqualByComparingTo("0.14");
        assertThat(di.taxaPara(1000)).isEqualByComparingTo("0.12");
    }

    @Test
    void fatorExponencialBase252() {
        // 12% por 252 dias úteis = fator 1,12 exato
        assertThat(di.fator(252).doubleValue()).isCloseTo(1.12, within(1e-9));
        // 126 du a 12% (interpolação entre 63 e 252 dá ~12,33%): (1,1233)^(0,5)
        double taxa = di.taxaPara(126).doubleValue();
        assertThat(di.fator(126).doubleValue()).isCloseTo(Math.pow(1 + taxa, 0.5), within(1e-9));
        assertThat(di.fatorDesconto(252).doubleValue()).isCloseTo(1 / 1.12, within(1e-9));
    }

    @Test
    void fatorLinearBase360() {
        CurvaJuros cupom = new CurvaJuros("CUPOM_USD", ref, ConvencaoTaxa.LINEAR_360,
                Map.of(30, new BigDecimal("0.05"), 360, new BigDecimal("0.05")));
        assertThat(cupom.fator(180)).isEqualByComparingTo("1.025000000000");
        assertThat(cupom.fator(360)).isEqualByComparingTo("1.050000000000");
        assertThat(ConvencaoTaxa.LINEAR_360.usaDiasUteis()).isFalse();
        assertThat(ConvencaoTaxa.EXP_252.usaDiasUteis()).isTrue();
    }

    @Test
    void validaEntradas() {
        assertThatThrownBy(() -> new CurvaJuros("di", ref, ConvencaoTaxa.EXP_252, Map.of(1, BigDecimal.ONE, 2, BigDecimal.ONE)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> new CurvaJuros("DI", ref, ConvencaoTaxa.EXP_252, Map.of(1, BigDecimal.ONE)))
                .isInstanceOf(DomainException.class).hasMessageContaining("dois vértices");
        assertThatThrownBy(() -> new CurvaJuros("DI", ref, ConvencaoTaxa.EXP_252, Map.of(1, new BigDecimal("5"), 2, BigDecimal.ONE)))
                .isInstanceOf(DomainException.class).hasMessageContaining("plausível");
        assertThatThrownBy(() -> di.taxaPara(0)).isInstanceOf(DomainException.class);
    }

    @Test
    void parDeMoedas() {
        ParMoedas p = ParMoedas.de("usd/brl");
        assertThat(p.codigo()).isEqualTo("USDBRL");
        assertThat(p.inverso().codigo()).isEqualTo("BRLUSD");
        assertThat(p.envolve("USD")).isTrue();
        assertThatThrownBy(() -> ParMoedas.de("USDUSD")).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ParMoedas.de("USD")).isInstanceOf(DomainException.class);
    }
}
