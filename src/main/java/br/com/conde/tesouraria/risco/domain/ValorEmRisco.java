package br.com.conde.tesouraria.risco.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;

import java.util.Arrays;
import java.util.List;

/** Cálculo de Value at Risk (lógica pura). */
public final class ValorEmRisco {

    public static final int MINIMO_OBSERVACOES = 30;

    private ValorEmRisco() {}

    /** Retornos logarítmicos diários de uma série de preços ordenada por data. */
    public static double[] retornosLog(List<Double> precos) {
        if (precos.size() < 2) return new double[0];
        double[] r = new double[precos.size() - 1];
        for (int i = 1; i < precos.size(); i++) r[i - 1] = Math.log(precos.get(i) / precos.get(i - 1));
        return r;
    }

    /**
     * VaR histórico por unidade de exposição: perda que não é excedida com a confiança dada,
     * lida diretamente do percentil da distribuição empírica dos retornos.
     */
    public static double historico(double[] retornos, double confianca) {
        validarConfianca(confianca);
        if (retornos.length < MINIMO_OBSERVACOES) {
            throw new DomainException("VaR histórico precisa de pelo menos " + MINIMO_OBSERVACOES + " retornos; há " + retornos.length);
        }
        double[] ord = retornos.clone();
        Arrays.sort(ord);
        double pos = (1 - confianca) * (ord.length - 1);
        int i = (int) Math.floor(pos);
        double frac = pos - i;
        double quantil = i + 1 < ord.length ? ord[i] + (ord[i + 1] - ord[i]) * frac : ord[i];
        return Math.max(0, -quantil);
    }

    /** VaR paramétrico (normal) por unidade: z(confiança) × σ_anual / √252 × √horizonte. */
    public static double parametrico(double volAnual, double confianca, int horizonteDias) {
        validarConfianca(confianca);
        if (volAnual <= 0) throw new DomainException("volatilidade deve ser positiva");
        if (horizonteDias <= 0) throw new DomainException("horizonte deve ser positivo");
        return z(confianca) * volAnual / Math.sqrt(252) * Math.sqrt(horizonteDias);
    }

    /** Volatilidade anualizada (desvio padrão amostral × √252) dos retornos diários. */
    public static double volatilidadeAnualizada(double[] retornos) {
        if (retornos.length < 2) throw new DomainException("poucos retornos para estimar volatilidade");
        double media = Arrays.stream(retornos).average().orElse(0);
        double soma = 0;
        for (double r : retornos) soma += (r - media) * (r - media);
        return Math.sqrt(soma / (retornos.length - 1)) * Math.sqrt(252);
    }

    /** Quantil da normal padrão pela aproximação de Acklam (erro relativo ~1e-9). */
    static double z(double p) {
        double[] a = {-3.969683028665376e+01, 2.209460984245205e+02, -2.759285104469687e+02, 1.383577518672690e+02, -3.066479806614716e+01, 2.506628277459239e+00};
        double[] b = {-5.447609879822406e+01, 1.615858368580409e+02, -1.556989798598866e+02, 6.680131188771972e+01, -1.328068155288572e+01};
        double[] c = {-7.784894002430293e-03, -3.223964580411365e-01, -2.400758277161838e+00, -2.549732539343734e+00, 4.374664141464968e+00, 2.938163982698783e+00};
        double[] d = {7.784695709041462e-03, 3.224671290700398e-01, 2.445134137142996e+00, 3.754408661907416e+00};
        double plow = 0.02425, phigh = 1 - plow, q, r;
        if (p < plow) {
            q = Math.sqrt(-2 * Math.log(p));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (p <= phigh) {
            q = p - 0.5; r = q * q;
            return (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r + a[4]) * r + a[5]) * q / (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r + b[4]) * r + 1);
        }
        q = Math.sqrt(-2 * Math.log(1 - p));
        return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
    }

    private static void validarConfianca(double c) {
        if (c <= 0.5 || c >= 1) throw new DomainException("nível de confiança deve estar entre 0,5 e 1 (ex.: 0,95)");
    }
}
