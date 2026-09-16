package br.com.conde.tesouraria.risco.application;

import br.com.conde.tesouraria.cambio.application.CambioService;
import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.cambio.domain.OperacaoCambio;
import br.com.conde.tesouraria.cambio.domain.SituacaoOperacao;
import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.derivativos.application.NdfService;
import br.com.conde.tesouraria.derivativos.application.OpcaoService;
import br.com.conde.tesouraria.derivativos.domain.ContratoNdf;
import br.com.conde.tesouraria.derivativos.domain.OpcaoCambio;
import br.com.conde.tesouraria.mercado.application.MercadoService;
import br.com.conde.tesouraria.mercado.domain.Cotacao;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.mercado.domain.TipoCotacao;
import br.com.conde.tesouraria.risco.domain.LimiteContraparte;
import br.com.conde.tesouraria.risco.domain.LimiteContraparteRepository;
import br.com.conde.tesouraria.risco.domain.ValorEmRisco;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Exposição cambial consolidada, limites por contraparte e VaR. */
@Service
@Transactional(readOnly = true)
public class RiscoService {

    private final CambioService cambio;
    private final NdfService ndf;
    private final OpcaoService opcoes;
    private final MercadoService mercado;
    private final ContraparteService contrapartes;
    private final LimiteContraparteRepository limites;

    public RiscoService(CambioService cambio, NdfService ndf, OpcaoService opcoes, MercadoService mercado,
                        ContraparteService contrapartes, LimiteContraparteRepository limites) {
        this.cambio = cambio;
        this.ndf = ndf;
        this.opcoes = opcoes;
        this.mercado = mercado;
        this.contrapartes = contrapartes;
        this.limites = limites;
    }

    // ---------- exposição ----------

    public record ExposicaoMoeda(String moeda, BigDecimal posicaoCambio, BigDecimal ndf, BigDecimal deltaOpcoes, BigDecimal total, BigDecimal spot, BigDecimal totalBrl) {}

    public record Exposicao(LocalDate data, List<ExposicaoMoeda> moedas, BigDecimal totalBrl, BigDecimal totalAbsolutoBrl) {}

    /**
     * Exposição líquida por moeda na data: posição de câmbio (ledger) + notional dos NDFs abertos
     * (compra positiva) + delta das opções abertas. Convertida a BRL pelo spot.
     */
    public Exposicao exposicao(LocalDate data) {
        Map<String, BigDecimal[]> acc = new TreeMap<>(); // moeda -> [posicao, ndf, delta]
        for (var pm : cambio.posicao(data).moedas()) {
            if (pm.moeda().equals("BRL")) continue;
            acc.computeIfAbsent(pm.moeda(), k -> zeros())[0] = pm.posicao();
        }
        for (ContratoNdf c : ndf.abertos()) {
            BigDecimal sinal = c.getLado() == LadoOperacao.COMPRA ? BigDecimal.ONE : BigDecimal.ONE.negate();
            BigDecimal[] a = acc.computeIfAbsent(c.getMoedaBase(), k -> zeros());
            a[1] = a[1].add(c.getNotional().multiply(sinal));
        }
        for (OpcaoCambio o : opcoes.abertas()) {
            if (!o.getDataVencimento().isAfter(data)) continue;
            try {
                var p = opcoes.precificar(o.parMoedas(), o.getTipo(), o.getStrike(), o.getNotional(), data, o.getDataVencimento(), null);
                BigDecimal delta = p.delta().multiply(o.getNotional()).multiply(BigDecimal.valueOf(o.getPosicao().sinal()));
                BigDecimal[] a = acc.computeIfAbsent(o.parMoedas().base(), k -> zeros());
                a[2] = a[2].add(delta);
            } catch (DomainException ignorada) { /* sem dados de mercado para esta opção na data */ }
        }
        List<ExposicaoMoeda> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO, totalAbs = BigDecimal.ZERO;
        for (var e : acc.entrySet()) {
            BigDecimal[] a = e.getValue();
            BigDecimal soma = a[0].add(a[1]).add(a[2]).setScale(2, RoundingMode.HALF_EVEN);
            BigDecimal spot = spotSeguro(e.getKey(), data);
            BigDecimal brl = spot == null ? null : soma.multiply(spot).setScale(2, RoundingMode.HALF_EVEN);
            lista.add(new ExposicaoMoeda(e.getKey(), a[0], a[1], a[2].setScale(2, RoundingMode.HALF_EVEN), soma, spot, brl));
            if (brl != null) { total = total.add(brl); totalAbs = totalAbs.add(brl.abs()); }
        }
        return new Exposicao(data, lista, total, totalAbs);
    }

    private static BigDecimal[] zeros() { return new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO}; }

    private BigDecimal spotSeguro(String moeda, LocalDate data) {
        try { return mercado.taxa(new ParMoedas(moeda, "BRL"), TipoCotacao.SPOT, data); } catch (NaoEncontradoException e) { return null; }
    }

    // ---------- VaR ----------

    public record Var(String par, LocalDate data, double confianca, int horizonteDias, int observacoes,
                      BigDecimal exposicaoBrl, BigDecimal varHistoricoUnitario, BigDecimal varHistoricoBrl,
                      BigDecimal volAnualizadaHistorica, BigDecimal volCurva, BigDecimal varParametricoBrl) {}

    /**
     * VaR da exposição na moeda base do par. Histórico usa a série PTAX até a data (percentil dos
     * retornos); paramétrico usa a volatilidade da curva VOL da moeda quando existir, senão a histórica.
     */
    public Var var(ParMoedas par, LocalDate data, double confianca, int horizonteDias, int janelaDias) {
        List<Cotacao> serie = mercado.historico(par, TipoCotacao.PTAX, data.minusDays(janelaDias), data);
        List<Double> precos = serie.stream().map(c -> c.getTaxa().doubleValue()).toList();
        double[] retornos = ValorEmRisco.retornosLog(precos);

        BigDecimal exposicao = exposicao(data).moedas().stream().filter(m -> m.moeda().equals(par.base()))
                .map(ExposicaoMoeda::totalBrl).filter(Objects::nonNull).findFirst().orElse(BigDecimal.ZERO);
        BigDecimal expAbs = exposicao.abs();

        BigDecimal varHistUnit = null, varHistBrl = null, volHist = null;
        if (retornos.length >= ValorEmRisco.MINIMO_OBSERVACOES) {
            double h = ValorEmRisco.historico(retornos, confianca) * Math.sqrt(horizonteDias);
            varHistUnit = bd(h, 8);
            varHistBrl = expAbs.multiply(varHistUnit).setScale(2, RoundingMode.HALF_EVEN);
            volHist = bd(ValorEmRisco.volatilidadeAnualizada(retornos), 8);
        }

        BigDecimal volCurva = null, varParam = null;
        try {
            volCurva = mercado.curva(OpcaoService.PREFIXO_VOL + par.base(), data).taxaPara(horizonteDias);
        } catch (NaoEncontradoException ignorada) { /* sem curva de vol */ }
        BigDecimal volParaParametrico = volCurva != null ? volCurva : volHist;
        if (volParaParametrico != null) {
            varParam = expAbs.multiply(bd(ValorEmRisco.parametrico(volParaParametrico.doubleValue(), confianca, horizonteDias), 8)).setScale(2, RoundingMode.HALF_EVEN);
        }
        if (varHistBrl == null && varParam == null) {
            throw new DomainException("sem dados suficientes: " + retornos.length + " retornos PTAX e nenhuma curva de volatilidade para " + par.base());
        }
        return new Var(par.codigo(), data, confianca, horizonteDias, retornos.length, exposicao, varHistUnit, varHistBrl, volHist, volCurva, varParam);
    }

    private static BigDecimal bd(double v, int escala) { return BigDecimal.valueOf(v).setScale(escala, RoundingMode.HALF_EVEN); }

    // ---------- limites ----------

    public record Utilizacao(UUID contraparteId, String contraparte, LocalDate data, BigDecimal limiteBrl, BigDecimal utilizadoBrl,
                             BigDecimal disponivelBrl, BigDecimal percentual, boolean excedido,
                             BigDecimal cambioBrl, BigDecimal ndfBrl, BigDecimal opcoesBrl) {}

    @Transactional
    public LimiteContraparte definirLimite(UUID contraparteId, BigDecimal limiteBrl, String por) {
        contrapartes.buscar(contraparteId);
        return limites.findById(contraparteId).map(l -> { l.definir(limiteBrl, por); return l; })
                .orElseGet(() -> limites.save(new LimiteContraparte(contraparteId, limiteBrl, por)));
    }

    public Optional<LimiteContraparte> limite(UUID contraparteId) { return limites.findById(contraparteId); }

    /** Utilização do limite: notional em BRL de tudo que está em aberto com a contraparte. */
    public Utilizacao utilizacao(UUID contraparteId, LocalDate data) {
        var cp = contrapartes.buscar(contraparteId);
        BigDecimal cambioBrl = BigDecimal.ZERO, ndfBrl = BigDecimal.ZERO, opcBrl = BigDecimal.ZERO;

        for (OperacaoCambio op : cambio.listar(null, contraparteId, Pageable.unpaged())) {
            if (op.getSituacao() != SituacaoOperacao.ABERTA) continue;
            cambioBrl = cambioBrl.add(emBrl(op.getMoedaCotada(), op.getValorCotado(), op.getMoedaBase(), op.getValorBase(), data));
        }
        for (ContratoNdf c : ndf.abertos()) {
            if (c.getContraparteId().equals(contraparteId)) ndfBrl = ndfBrl.add(c.getNotional().multiply(c.getTaxaTermo()));
        }
        for (OpcaoCambio o : opcoes.abertas()) {
            if (o.getContraparteId().equals(contraparteId)) opcBrl = opcBrl.add(o.getNotional().multiply(o.getStrike()));
        }
        BigDecimal utilizado = cambioBrl.add(ndfBrl).add(opcBrl).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal limite = limites.findById(contraparteId).map(LimiteContraparte::getLimiteBrl).orElse(null);
        BigDecimal disponivel = limite == null ? null : limite.subtract(utilizado);
        BigDecimal pct = limite == null || limite.signum() == 0 ? null : utilizado.multiply(BigDecimal.valueOf(100)).divide(limite, 2, RoundingMode.HALF_EVEN);
        return new Utilizacao(contraparteId, cp.getNome(), data, limite, utilizado, disponivel, pct, limite != null && utilizado.compareTo(limite) > 0,
                cambioBrl.setScale(2, RoundingMode.HALF_EVEN), ndfBrl.setScale(2, RoundingMode.HALF_EVEN), opcBrl.setScale(2, RoundingMode.HALF_EVEN));
    }

    private BigDecimal emBrl(String moedaCotada, BigDecimal valorCotado, String moedaBase, BigDecimal valorBase, LocalDate data) {
        if (moedaCotada.equals("BRL")) return valorCotado;
        if (moedaBase.equals("BRL")) return valorBase;
        BigDecimal spot = spotSeguro(moedaCotada, data);
        return spot == null ? BigDecimal.ZERO : valorCotado.multiply(spot);
    }
}
