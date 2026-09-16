package br.com.conde.tesouraria.calendario.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Set;

/**
 * Calendário de dias úteis (lógica pura, sem banco). Recebe o conjunto de feriados
 * já carregado; pode combinar várias praças, o que é o padrão em câmbio (liquidação
 * só em dia útil nas duas moedas).
 */
public final class Calendario {

    private final Set<LocalDate> feriados;

    public Calendario(Set<LocalDate> feriados) {
        this.feriados = Set.copyOf(feriados);
    }

    public boolean ehFimDeSemana(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        return dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
    }

    public boolean ehDiaUtil(LocalDate d) {
        return !ehFimDeSemana(d) && !feriados.contains(d);
    }

    /** Próximo dia útil a partir de d, inclusive. */
    public LocalDate proximoDiaUtil(LocalDate d) {
        LocalDate x = d;
        while (!ehDiaUtil(x)) x = x.plusDays(1);
        return x;
    }

    /** Dia útil anterior a partir de d, inclusive. */
    public LocalDate diaUtilAnterior(LocalDate d) {
        LocalDate x = d;
        while (!ehDiaUtil(x)) x = x.minusDays(1);
        return x;
    }

    /** Soma n dias úteis (n pode ser negativo). D+0 devolve o próprio dia se for útil. */
    public LocalDate somarDiasUteis(LocalDate d, int n) {
        LocalDate x = d;
        int passo = Integer.signum(n);
        int restantes = Math.abs(n);
        while (restantes > 0) {
            x = x.plusDays(passo);
            if (ehDiaUtil(x)) restantes--;
        }
        return x;
    }

    /** Dias úteis no intervalo [inicio, fim), convenção usada no cálculo de DI. */
    public int diasUteisEntre(LocalDate inicio, LocalDate fim) {
        if (fim.isBefore(inicio)) throw new DomainException("fim anterior ao início");
        int n = 0;
        for (LocalDate x = inicio; x.isBefore(fim); x = x.plusDays(1)) if (ehDiaUtil(x)) n++;
        return n;
    }

    /** Convenção "modified following": próximo dia útil, salvo se mudar de mês, quando volta ao anterior. */
    public LocalDate ajustarModifiedFollowing(LocalDate d) {
        LocalDate p = proximoDiaUtil(d);
        return p.getMonth() == d.getMonth() ? p : diaUtilAnterior(d);
    }
}
