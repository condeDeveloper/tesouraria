package br.com.conde.tesouraria.risco.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ValorEmRiscoTest {

    @Test
    void retornosLogaritmicos() {
        double[] r = ValorEmRisco.retornosLog(List.of(100.0, 110.0, 99.0));
        assertThat(r).hasSize(2);
        assertThat(r[0]).isCloseTo(Math.log(1.1), within(1e-12));
        assertThat(r[1]).isCloseTo(Math.log(0.9), within(1e-12));
    }

    @Test
    void varHistoricoLeOPercentilDasPerdas() {
        // 100 retornos: -0,10 ... +0,098 em passos de 0,002
        double[] r = new double[100];
        for (int i = 0; i < 100; i++) r[i] = -0.10 + i * 0.002;
        // 5% pior: posição 0,05 x 99 = 4,95 -> entre r[4] = -0,092 e r[5] = -0,090 -> -0,0901
        assertThat(ValorEmRisco.historico(r, 0.95)).isCloseTo(0.0901, within(1e-9));
        assertThat(ValorEmRisco.historico(r, 0.99)).isCloseTo(0.09802, within(1e-9));
    }

    @Test
    void varHistoricoNuncaEhNegativo() {
        double[] r = new double[40];
        for (int i = 0; i < 40; i++) r[i] = 0.01 + i * 0.001; // só ganhos
        assertThat(ValorEmRisco.historico(r, 0.95)).isZero();
    }

    @Test
    void exigeObservacoesMinimasEConfiancaValida() {
        assertThatThrownBy(() -> ValorEmRisco.historico(new double[10], 0.95)).isInstanceOf(DomainException.class).hasMessageContaining("pelo menos 30");
        assertThatThrownBy(() -> ValorEmRisco.historico(new double[40], 1.5)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> ValorEmRisco.parametrico(0.15, 0.3, 1)).isInstanceOf(DomainException.class);
    }

    @Test
    void quantilDaNormal() {
        assertThat(ValorEmRisco.z(0.95)).isCloseTo(1.6448536, within(1e-6));
        assertThat(ValorEmRisco.z(0.99)).isCloseTo(2.3263479, within(1e-6));
        assertThat(ValorEmRisco.z(0.5)).isCloseTo(0, within(1e-9));
    }

    @Test
    void varParametricoEscalaComVolEHorizonte() {
        double v1 = ValorEmRisco.parametrico(0.15, 0.95, 1);
        assertThat(v1).isCloseTo(1.6448536 * 0.15 / Math.sqrt(252), within(1e-9));
        assertThat(ValorEmRisco.parametrico(0.15, 0.95, 4)).isCloseTo(v1 * 2, within(1e-12));
        assertThat(ValorEmRisco.parametrico(0.30, 0.95, 1)).isCloseTo(v1 * 2, within(1e-12));
    }

    @Test
    void volatilidadeAnualizadaDeSerieSimulada() {
        Random rnd = new Random(42);
        List<Double> precos = new ArrayList<>(List.of(5.0));
        double sigmaDiaria = 0.15 / Math.sqrt(252);
        for (int i = 0; i < 5000; i++) precos.add(precos.get(precos.size() - 1) * Math.exp(sigmaDiaria * rnd.nextGaussian()));
        double vol = ValorEmRisco.volatilidadeAnualizada(ValorEmRisco.retornosLog(precos));
        assertThat(vol).isCloseTo(0.15, within(0.01));
        // VaR histórico e paramétrico devem ficar próximos para uma série normal
        double hist = ValorEmRisco.historico(ValorEmRisco.retornosLog(precos), 0.95);
        double param = ValorEmRisco.parametrico(vol, 0.95, 1);
        assertThat(hist).isCloseTo(param, within(param * 0.1));
    }
}
