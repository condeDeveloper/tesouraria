package br.com.conde.tesouraria.derivativos.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;

/**
 * Precificação de opções europeias de câmbio pelo modelo de Garman-Kohlhagen (Black-Scholes
 * com duas taxas de juros), escrito na forma de forward para casar com as curvas locais:
 * <pre>
 *   call = DFd · [F·N(d1) − K·N(d2)]      put = DFd · [K·N(−d2) − F·N(−d1)]
 *   d1 = [ln(F/K) + σ²T/2] / (σ√T)        d2 = d1 − σ√T
 * </pre>
 * onde F é o forward, DFd o fator de desconto na moeda cotada (BRL) e as taxas contínuas
 * rd e rf são obtidas dos fatores de desconto doméstico e estrangeiro.
 */
public final class GarmanKohlhagen {

    private GarmanKohlhagen() {}

    public enum Tipo { CALL, PUT }

    /** Resultado por unidade de notional (em moeda cotada por unidade de moeda base). */
    public record Resultado(double preco, double delta, double gamma, double vega, double theta, double rho, double d1, double d2, double forward) {}

    /**
     * @param tipo      CALL ou PUT sobre a moeda base
     * @param spot      S, moeda cotada por unidade de moeda base
     * @param strike    K
     * @param prazoAnos T em anos (dias corridos / 365)
     * @param dfDom     fator de desconto na moeda cotada até o vencimento
     * @param dfEst     fator de desconto na moeda base até o vencimento
     * @param vol       volatilidade anual (0,15 = 15%)
     */
    public static Resultado precificar(Tipo tipo, double spot, double strike, double prazoAnos, double dfDom, double dfEst, double vol) {
        if (spot <= 0 || strike <= 0) throw new DomainException("spot e strike devem ser positivos");
        if (prazoAnos <= 0) throw new DomainException("prazo deve ser positivo");
        if (vol <= 0) throw new DomainException("volatilidade deve ser positiva");
        if (dfDom <= 0 || dfDom > 1.5 || dfEst <= 0 || dfEst > 1.5) throw new DomainException("fatores de desconto implausíveis");

        double forward = spot * dfEst / dfDom;
        double rd = -Math.log(dfDom) / prazoAnos;
        double rf = -Math.log(dfEst) / prazoAnos;
        double sqrtT = Math.sqrt(prazoAnos);
        double d1 = (Math.log(forward / strike) + 0.5 * vol * vol * prazoAnos) / (vol * sqrtT);
        double d2 = d1 - vol * sqrtT;

        double preco, delta, theta, rho;
        double nd1 = densidade(d1);
        if (tipo == Tipo.CALL) {
            preco = dfDom * (forward * cdf(d1) - strike * cdf(d2));
            delta = dfEst * cdf(d1);
            theta = -spot * dfEst * nd1 * vol / (2 * sqrtT) + rf * spot * dfEst * cdf(d1) - rd * strike * dfDom * cdf(d2);
            rho = strike * prazoAnos * dfDom * cdf(d2);
        } else {
            preco = dfDom * (strike * cdf(-d2) - forward * cdf(-d1));
            delta = -dfEst * cdf(-d1);
            theta = -spot * dfEst * nd1 * vol / (2 * sqrtT) - rf * spot * dfEst * cdf(-d1) + rd * strike * dfDom * cdf(-d2);
            rho = -strike * prazoAnos * dfDom * cdf(-d2);
        }
        double gamma = dfEst * nd1 / (spot * vol * sqrtT);
        double vega = spot * dfEst * nd1 * sqrtT;
        return new Resultado(preco, delta, gamma, vega, theta, rho, d1, d2, forward);
    }

    /** Payoff no vencimento por unidade de notional. */
    public static double payoff(Tipo tipo, double taxaFinal, double strike) {
        return tipo == Tipo.CALL ? Math.max(0, taxaFinal - strike) : Math.max(0, strike - taxaFinal);
    }

    /** Densidade da normal padrão. */
    static double densidade(double x) { return Math.exp(-0.5 * x * x) / Math.sqrt(2 * Math.PI); }

    /**
     * Função de distribuição acumulada da normal padrão pelo algoritmo de Hart (1968), na forma
     * publicada por West (2005), com precisão de dupla precisão em toda a reta.
     */
    static double cdf(double x) {
        double z = Math.abs(x);
        double resultado;
        if (z > 37) {
            resultado = 0;
        } else {
            double e = Math.exp(-z * z / 2);
            if (z < 7.07106781186547) {
                double num = 3.52624965998911E-02 * z + 0.700383064443688;
                num = num * z + 6.37396220353165;
                num = num * z + 33.912866078383;
                num = num * z + 112.079291497871;
                num = num * z + 221.213596169931;
                num = num * z + 220.206867912376;
                double den = 8.83883476483184E-02 * z + 1.75566716318264;
                den = den * z + 16.064177579207;
                den = den * z + 86.7807322029461;
                den = den * z + 296.564248779674;
                den = den * z + 637.333633378831;
                den = den * z + 793.826512519948;
                den = den * z + 440.413735824752;
                resultado = e * num / den;
            } else {
                double b = z + 0.65;
                b = z + 4 / b;
                b = z + 3 / b;
                b = z + 2 / b;
                b = z + 1 / b;
                resultado = e / b / 2.506628274631;
            }
        }
        return x > 0 ? 1 - resultado : resultado;
    }
}
