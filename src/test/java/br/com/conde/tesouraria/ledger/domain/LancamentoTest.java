package br.com.conde.tesouraria.ledger.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LancamentoTest {

    private final ContaContabil caixaBrl = new ContaContabil("1.1.01.BRL", "Caixa BRL", TipoConta.ATIVO, "BRL", null);
    private final ContaContabil receitaBrl = new ContaContabil("4.1.01.BRL", "Receita BRL", TipoConta.RECEITA, "BRL", null);
    private final ContaContabil caixaUsd = new ContaContabil("1.1.01.USD", "Caixa USD", TipoConta.ATIVO, "USD", null);
    private final LocalDate hoje = LocalDate.of(2026, 9, 15);

    @Test
    void lancamentoBalanceadoEhAceito() {
        Lancamento l = Lancamento.novo("k1", hoje, "Receita", "MANUAL")
                .debito(caixaBrl, Money.of("100", "BRL"))
                .credito(receitaBrl, Money.of("100", "BRL"))
                .build();
        assertThat(l.getPartidas()).hasSize(2);
        assertThat(l.moedas()).containsExactly("BRL");
    }

    @Test
    void recusaDesbalanceado() {
        assertThatThrownBy(() -> Lancamento.novo("k2", hoje, "Errado", "MANUAL")
                .debito(caixaBrl, Money.of("100", "BRL"))
                .credito(receitaBrl, Money.of("90", "BRL"))
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("desbalanceado em BRL");
    }

    @Test
    void balanceiaPorMoedaSeparadamente() {
        // um lançamento multimoeda precisa fechar em cada moeda
        assertThatThrownBy(() -> Lancamento.novo("k3", hoje, "Multimoeda", "CAMBIO")
                .debito(caixaBrl, Money.of("500", "BRL"))
                .credito(caixaUsd, Money.of("100", "USD"))
                .build())
                .isInstanceOf(DomainException.class);
    }

    @Test
    void recusaPartidaEmMoedaDiferenteDaConta() {
        assertThatThrownBy(() -> Lancamento.novo("k4", hoje, "Moeda errada", "MANUAL")
                .debito(caixaBrl, Money.of("100", "USD"))
                .credito(receitaBrl, Money.of("100", "USD"))
                .build())
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("é em BRL");
    }

    @Test
    void recusaMenosDeDuasPartidasEValorNaoPositivo() {
        assertThatThrownBy(() -> Lancamento.novo("k5", hoje, "Uma só", "MANUAL").debito(caixaBrl, Money.of("1", "BRL")).build())
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> Lancamento.novo("k6", hoje, "Zero", "MANUAL")
                .debito(caixaBrl, Money.zero("BRL")).credito(receitaBrl, Money.zero("BRL")).build())
                .isInstanceOf(DomainException.class);
    }

    @Test
    void estornoInverteAsPartidasEMarcaOOriginal() {
        Lancamento l = Lancamento.novo("k7", hoje, "Receita", "MANUAL")
                .debito(caixaBrl, Money.of("100", "BRL")).credito(receitaBrl, Money.of("100", "BRL")).build();
        Lancamento e = l.estornar("ESTORNO:k7", hoje.plusDays(1), "admin");
        assertThat(l.isEstornado()).isTrue();
        assertThat(l.getEstornadoPor()).isEqualTo(e.getId());
        assertThat(e.getPartidas().get(0).getTipo()).isEqualTo(TipoPartida.CREDITO);
        assertThat(e.getPartidas().get(0).getConta()).isSameAs(caixaBrl);
        assertThat(e.getDescricao()).startsWith("Estorno:");
        assertThatThrownBy(() -> l.estornar("x", hoje, "admin")).hasMessageContaining("já estornado");
    }

    @Test
    void naturezaDefineOSinalDoSaldo() {
        java.math.BigDecimal cem = new java.math.BigDecimal("100");
        java.math.BigDecimal trinta = new java.math.BigDecimal("30");
        assertThat(NaturezaConta.DEVEDORA.saldo(cem, trinta)).isEqualByComparingTo("70");
        assertThat(NaturezaConta.CREDORA.saldo(cem, trinta)).isEqualByComparingTo("-70");
        assertThat(TipoConta.PASSIVO.naturezaPadrao()).isEqualTo(NaturezaConta.CREDORA);
    }
}
