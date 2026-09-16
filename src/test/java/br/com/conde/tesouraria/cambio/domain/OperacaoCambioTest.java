package br.com.conde.tesouraria.cambio.domain;

import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperacaoCambioTest {

    private final UUID cp = UUID.randomUUID();
    private final LocalDate d0 = LocalDate.of(2026, 9, 15), d2 = LocalDate.of(2026, 9, 17);

    private OperacaoCambio compraUsd(String taxa, String ref) {
        return new OperacaoCambio("CAM-2026-000001", cp, ParMoedas.de("USDBRL"), LadoOperacao.COMPRA,
                new BigDecimal("100000"), new BigDecimal(taxa), ref == null ? null : new BigDecimal(ref), d0, d2, "mesa");
    }

    @Test
    void calculaValorCotadoEPernas() {
        OperacaoCambio op = compraUsd("5.3050", "5.3080");
        assertThat(op.getValorCotado()).isEqualByComparingTo("530500.0000");
        assertThat(op.recebe()).isEqualTo(Money.of("100000", "USD"));
        assertThat(op.entrega()).isEqualTo(Money.of("530500", "BRL"));
        assertThat(op.getSituacao()).isEqualTo(SituacaoOperacao.ABERTA);
    }

    @Test
    void resultadoSobreReferencia() {
        // comprou USD a 5,3050 quando a referência era 5,3080: ganhou 0,0030 x 100.000 = 300 BRL
        assertThat(compraUsd("5.3050", "5.3080").resultadoSobreReferencia()).isEqualTo(Money.of("300", "BRL"));
        // vendeu a 5,3050 com referência 5,3080: perdeu 300
        OperacaoCambio venda = new OperacaoCambio("CAM-2026-000002", cp, ParMoedas.de("USDBRL"), LadoOperacao.VENDA,
                new BigDecimal("100000"), new BigDecimal("5.3050"), new BigDecimal("5.3080"), d0, d2, "mesa");
        assertThat(venda.resultadoSobreReferencia()).isEqualTo(Money.of("-300", "BRL"));
        assertThat(venda.recebe().moeda()).isEqualTo("BRL");
        assertThat(compraUsd("5.30", null).resultadoSobreReferencia().ehZero()).isTrue();
    }

    @Test
    void liquidaSoNaDataOuDepois() {
        OperacaoCambio op = compraUsd("5.30", null);
        assertThatThrownBy(() -> op.liquidar(d0)).isInstanceOf(DomainException.class).hasMessageContaining("só liquida em");
        op.liquidar(d2);
        assertThat(op.getSituacao()).isEqualTo(SituacaoOperacao.LIQUIDADA);
        assertThat(op.getLiquidadoEm()).isNotNull();
        assertThatThrownBy(() -> op.cancelar()).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> op.liquidar(d2)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancelaSoSeAberta() {
        OperacaoCambio op = compraUsd("5.30", null);
        op.cancelar();
        assertThat(op.getSituacao()).isEqualTo(SituacaoOperacao.CANCELADA);
        assertThatThrownBy(() -> op.liquidar(d2)).isInstanceOf(DomainException.class);
    }

    @Test
    void validaEntradas() {
        assertThatThrownBy(() -> new OperacaoCambio("X", cp, ParMoedas.de("USDBRL"), LadoOperacao.COMPRA, BigDecimal.ZERO, BigDecimal.ONE, null, d0, d2, "m"))
                .isInstanceOf(DomainException.class).hasMessageContaining("positivo");
        assertThatThrownBy(() -> new OperacaoCambio("X", cp, ParMoedas.de("USDBRL"), LadoOperacao.COMPRA, BigDecimal.ONE, BigDecimal.ONE, null, d2, d0, "m"))
                .isInstanceOf(DomainException.class).hasMessageContaining("anterior");
        assertThat(LadoOperacao.COMPRA.inverso()).isEqualTo(LadoOperacao.VENDA);
    }
}
