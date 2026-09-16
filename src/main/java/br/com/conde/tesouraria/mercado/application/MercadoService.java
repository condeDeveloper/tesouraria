package br.com.conde.tesouraria.mercado.application;

import br.com.conde.tesouraria.calendario.application.CalendarioService;
import br.com.conde.tesouraria.calendario.domain.Calendario;
import br.com.conde.tesouraria.mercado.domain.*;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Cotações, PTAX, curvas de juros e taxa a termo teórica. */
@Service
@Transactional
public class MercadoService {

    public static final String CURVA_DI = "DI";
    public static final String PREFIXO_CUPOM = "CUPOM_";

    private final CotacaoRepository cotacoes;
    private final CurvaJurosRepository curvas;
    private final CalendarioService calendarios;

    public MercadoService(CotacaoRepository cotacoes, CurvaJurosRepository curvas, CalendarioService calendarios) {
        this.cotacoes = cotacoes;
        this.curvas = curvas;
        this.calendarios = calendarios;
    }

    // ---------- cotações ----------

    /** Registra ou atualiza a cotação do par/tipo/data (upsert). */
    public Cotacao registrarCotacao(ParMoedas par, TipoCotacao tipo, LocalDate data, BigDecimal taxa, String fonte) {
        return cotacoes.findByParAndTipoAndData(par.codigo(), tipo, data)
                .map(c -> { c.atualizar(taxa, fonte); return c; })
                .orElseGet(() -> cotacoes.save(new Cotacao(par, tipo, data, taxa, fonte)));
    }

    @Transactional(readOnly = true)
    public List<Cotacao> historico(ParMoedas par, TipoCotacao tipo, LocalDate inicio, LocalDate fim) {
        return cotacoes.findAllByParAndTipoAndDataBetweenOrderByData(par.codigo(), tipo, inicio, fim);
    }

    /** Cotação mais recente até a data. Aceita o par invertido e devolve 1/taxa. */
    @Transactional(readOnly = true)
    public Cotacao cotacaoMaisRecente(ParMoedas par, TipoCotacao tipo, LocalDate ate) {
        return cotacoes.findFirstByParAndTipoAndDataLessThanEqualOrderByDataDesc(par.codigo(), tipo, ate)
                .orElseThrow(() -> new NaoEncontradoException("cotação " + tipo + " " + par, "até " + ate));
    }

    /** Taxa do par (direta ou via par invertido) mais recente até a data. */
    @Transactional(readOnly = true)
    public BigDecimal taxa(ParMoedas par, TipoCotacao tipo, LocalDate ate) {
        var direta = cotacoes.findFirstByParAndTipoAndDataLessThanEqualOrderByDataDesc(par.codigo(), tipo, ate);
        if (direta.isPresent()) return direta.get().getTaxa();
        var inversa = cotacoes.findFirstByParAndTipoAndDataLessThanEqualOrderByDataDesc(par.inverso().codigo(), tipo, ate);
        return inversa.map(Cotacao::taxaInversa)
                .orElseThrow(() -> new NaoEncontradoException("cotação " + tipo + " " + par, "até " + ate));
    }

    /** PTAX exatamente na data (fixing de NDF não aceita data aproximada). */
    @Transactional(readOnly = true)
    public BigDecimal ptaxNaData(ParMoedas par, LocalDate data) {
        return cotacoes.findByParAndTipoAndData(par.codigo(), TipoCotacao.PTAX, data).map(Cotacao::getTaxa)
                .or(() -> cotacoes.findByParAndTipoAndData(par.inverso().codigo(), TipoCotacao.PTAX, data).map(Cotacao::taxaInversa))
                .orElseThrow(() -> new NaoEncontradoException("PTAX " + par, data));
    }

    // ---------- curvas ----------

    public CurvaJuros registrarCurva(String nome, LocalDate dataReferencia, ConvencaoTaxa convencao, Map<Integer, BigDecimal> pontos) {
        curvas.findByNomeAndDataReferencia(nome, dataReferencia).ifPresent(curvas::delete);
        curvas.flush();
        return curvas.save(new CurvaJuros(nome, dataReferencia, convencao, pontos));
    }

    @Transactional(readOnly = true)
    public CurvaJuros curva(String nome, LocalDate ate) {
        return curvas.findFirstByNomeAndDataReferenciaLessThanEqualOrderByDataReferenciaDesc(nome, ate)
                .orElseThrow(() -> new NaoEncontradoException("curva " + nome, "até " + ate));
    }

    @Transactional(readOnly = true)
    public List<CurvaJuros> datasDaCurva(String nome) { return curvas.findAllByNomeOrderByDataReferenciaDesc(nome); }

    // ---------- termo ----------

    public record Termo(LocalDate dataReferencia, LocalDate vencimento, int diasUteis, int diasCorridos,
                        BigDecimal spot, BigDecimal fatorPre, BigDecimal fatorCupom, BigDecimal forward) {}

    /**
     * Taxa a termo teórica de MOEDA/BRL por paridade coberta de juros:
     * F = S × fatorDI(du) / fatorCupom(dc). Só faz sentido para pares cotados em BRL.
     */
    @Transactional(readOnly = true)
    public Termo forwardTeorico(ParMoedas par, LocalDate dataReferencia, LocalDate vencimento) {
        if (!par.cotada().equals("BRL")) throw new DomainException("forward teórico disponível apenas para pares cotados em BRL");
        if (!vencimento.isAfter(dataReferencia)) throw new DomainException("vencimento deve ser posterior à data de referência");
        Calendario cal = calendarios.calendario("BRL");
        int du = cal.diasUteisEntre(dataReferencia, vencimento);
        int dc = (int) ChronoUnit.DAYS.between(dataReferencia, vencimento);
        BigDecimal spot = taxa(par, TipoCotacao.SPOT, dataReferencia);
        BigDecimal fPre = curva(CURVA_DI, dataReferencia).fator(Math.max(1, du));
        BigDecimal fCupom = curva(PREFIXO_CUPOM + par.base(), dataReferencia).fator(Math.max(1, dc));
        BigDecimal forward = spot.multiply(fPre).divide(fCupom, 8, RoundingMode.HALF_EVEN);
        return new Termo(dataReferencia, vencimento, du, dc, spot, fPre, fCupom, forward);
    }
}
