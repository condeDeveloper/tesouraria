package br.com.conde.tesouraria.calendario.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CalendarioTest {

    // Calendário com Carnaval 2026 (16 e 17/02) e Sexta-feira Santa (03/04)
    private final Calendario cal = new Calendario(Set.of(
            LocalDate.of(2026, 2, 16), LocalDate.of(2026, 2, 17), LocalDate.of(2026, 4, 3)));

    @Test
    void fimDeSemanaEFeriadoNaoSaoDiasUteis() {
        assertThat(cal.ehDiaUtil(LocalDate.of(2026, 2, 14))).isFalse(); // sábado
        assertThat(cal.ehDiaUtil(LocalDate.of(2026, 2, 16))).isFalse(); // carnaval
        assertThat(cal.ehDiaUtil(LocalDate.of(2026, 2, 18))).isTrue();  // quarta de cinzas
    }

    @Test
    void proximoEAnteriorDiaUtil() {
        assertThat(cal.proximoDiaUtil(LocalDate.of(2026, 2, 14))).isEqualTo(LocalDate.of(2026, 2, 18));
        assertThat(cal.diaUtilAnterior(LocalDate.of(2026, 2, 17))).isEqualTo(LocalDate.of(2026, 2, 13));
        assertThat(cal.proximoDiaUtil(LocalDate.of(2026, 2, 18))).isEqualTo(LocalDate.of(2026, 2, 18));
    }

    @Test
    void somaDiasUteisPulaFeriadosEFinsDeSemana() {
        // sexta 13/02 + 2 dias úteis: pula sáb, dom, carnaval (seg e ter) -> qua 18 e qui 19
        assertThat(cal.somarDiasUteis(LocalDate.of(2026, 2, 13), 2)).isEqualTo(LocalDate.of(2026, 2, 19));
        assertThat(cal.somarDiasUteis(LocalDate.of(2026, 2, 19), -2)).isEqualTo(LocalDate.of(2026, 2, 13));
        assertThat(cal.somarDiasUteis(LocalDate.of(2026, 2, 19), 0)).isEqualTo(LocalDate.of(2026, 2, 19));
    }

    @Test
    void contaDiasUteisNoIntervaloSemIncluirOFim() {
        // de seg 09/02 a seg 23/02: 5 dias (9-13) + 3 dias (18-20) = 8
        assertThat(cal.diasUteisEntre(LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 23))).isEqualTo(8);
        assertThat(cal.diasUteisEntre(LocalDate.of(2026, 2, 9), LocalDate.of(2026, 2, 9))).isZero();
    }

    @Test
    void modifiedFollowingVoltaQuandoMudaOMes() {
        // 31/01/2026 é sábado; próximo útil seria 02/02, mas muda o mês -> volta para 30/01
        assertThat(cal.ajustarModifiedFollowing(LocalDate.of(2026, 1, 31))).isEqualTo(LocalDate.of(2026, 1, 30));
        assertThat(cal.ajustarModifiedFollowing(LocalDate.of(2026, 2, 14))).isEqualTo(LocalDate.of(2026, 2, 18));
    }
}
