package br.com.conde.tesouraria.derivativos.application;

import br.com.conde.tesouraria.calendario.application.CalendarioService;
import br.com.conde.tesouraria.calendario.domain.Calendario;
import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.derivativos.domain.*;
import br.com.conde.tesouraria.ledger.application.LedgerService;
import br.com.conde.tesouraria.ledger.domain.ContaContabil;
import br.com.conde.tesouraria.ledger.domain.Lancamento;
import br.com.conde.tesouraria.mercado.application.MercadoService;
import br.com.conde.tesouraria.mercado.domain.CurvaJuros;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.mercado.domain.TipoCotacao;
import br.com.conde.tesouraria.moedas.application.MoedaService;
import br.com.conde.tesouraria.shared.application.NumeradorService;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Opções de câmbio: precificação, contratação, marcação a mercado, exercício e cancelamento.
 * <p>
 * Contabilização (conta de opções: 1.3.02 comprada / 2.3.02 lançada):
 * <pre>
 *  prêmio      comprada: D 1.3.02  C Caixa         lançada: D Caixa  C 2.3.02
 *  marcação    lança (valor − prêmio) contra resultado, estornando a marcação anterior
 *  exercício   estorna a última marcação, move o payoff pelo caixa e fecha a conta de opções
 *              contra resultado; o efeito líquido no resultado é payoff − prêmio
 * </pre>
 */
@Service
@Transactional
public class OpcaoService {

    public static final String ORIGEM = "OPCAO";
    static final String CAIXA_BRL = "1.1.01.BRL";
    static final String COMPRADAS = "1.3.02.BRL";
    static final String LANCADAS = "2.3.02.BRL";
    static final String RESULTADO = "4.2.01.BRL";
    static final String PERDA = "5.2.01.BRL";
    public static final String PREFIXO_VOL = "VOL_";

    private final OpcaoCambioRepository opcoes;
    private final MarcacaoOpcaoRepository marcacoes;
    private final ContraparteService contrapartes;
    private final MoedaService moedas;
    private final MercadoService mercado;
    private final CalendarioService calendarios;
    private final LedgerService ledger;
    private final NumeradorService numerador;

    public OpcaoService(OpcaoCambioRepository opcoes, MarcacaoOpcaoRepository marcacoes, ContraparteService contrapartes, MoedaService moedas,
                        MercadoService mercado, CalendarioService calendarios, LedgerService ledger, NumeradorService numerador) {
        this.opcoes = opcoes;
        this.marcacoes = marcacoes;
        this.contrapartes = contrapartes;
        this.moedas = moedas;
        this.mercado = mercado;
        this.calendarios = calendarios;
        this.ledger = ledger;
        this.numerador = numerador;
    }

    // ---------- precificação ----------

    public record Preco(LocalDate data, LocalDate vencimento, BigDecimal spot, BigDecimal forward, BigDecimal volatilidade, BigDecimal prazoAnos,
                        BigDecimal fatorDescontoBrl, BigDecimal fatorDescontoMoeda, BigDecimal precoUnitario, BigDecimal premioTotal,
                        BigDecimal delta, BigDecimal gamma, BigDecimal vega, BigDecimal theta, BigDecimal rho) {}

    /** Precifica uma opção com os dados de mercado da data. Volatilidade nula usa a curva VOL_&lt;moeda&gt;. */
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public Preco precificar(ParMoedas par, GarmanKohlhagen.Tipo tipo, BigDecimal strike, BigDecimal notional, LocalDate data, LocalDate vencimento, BigDecimal vol) {
        if (!par.cotada().equals("BRL")) throw new DomainException("precificação disponível apenas para pares cotados em BRL");
        if (!vencimento.isAfter(data)) throw new DomainException("vencimento deve ser posterior à data");
        int dc = (int) ChronoUnit.DAYS.between(data, vencimento);
        int du = calendarios.calendario("BRL").diasUteisEntre(data, vencimento);
        double prazoAnos = dc / 365.0;
        BigDecimal spot = mercado.taxa(par, TipoCotacao.SPOT, data);
        BigDecimal dfBrl = mercado.curva(MercadoService.CURVA_DI, data).fatorDesconto(Math.max(1, du));
        BigDecimal dfMoeda = mercado.curva(MercadoService.PREFIXO_CUPOM + par.base(), data).fatorDesconto(Math.max(1, dc));
        BigDecimal sigma = vol != null ? vol : volatilidade(par, data, dc);

        GarmanKohlhagen.Resultado r = GarmanKohlhagen.precificar(tipo, spot.doubleValue(), strike.doubleValue(), prazoAnos,
                dfBrl.doubleValue(), dfMoeda.doubleValue(), sigma.doubleValue());
        BigDecimal unit = bd(r.preco(), 8);
        return new Preco(data, vencimento, spot, bd(r.forward(), 8), sigma, bd(prazoAnos, 8), dfBrl, dfMoeda, unit,
                unit.multiply(notional).setScale(4, RoundingMode.HALF_EVEN),
                bd(r.delta(), 8), bd(r.gamma(), 10), bd(r.vega(), 6), bd(r.theta(), 6), bd(r.rho(), 6));
    }

    private BigDecimal volatilidade(ParMoedas par, LocalDate data, int dias) {
        CurvaJuros vol = mercado.curva(PREFIXO_VOL + par.base(), data);
        return vol.taxaPara(Math.max(1, dias));
    }

    private static BigDecimal bd(double v, int escala) { return BigDecimal.valueOf(v).setScale(escala, RoundingMode.HALF_EVEN); }

    // ---------- contratação ----------

    public record Contratacao(UUID contraparteId, ParMoedas par, GarmanKohlhagen.Tipo tipo, PosicaoOpcao posicao, BigDecimal notional,
                              BigDecimal strike, BigDecimal premio, BigDecimal volatilidade, LocalDate dataNegociacao, LocalDate dataVencimento, String operador) {}

    /** Contrata a opção. Sem prêmio, usa o teórico. Liquidação em D+2 úteis após o vencimento. Contabiliza o prêmio no caixa. */
    public OpcaoCambio contratar(Contratacao c) {
        contrapartes.exigirApta(c.contraparteId());
        moedas.exigirAtiva(c.par().base());
        LocalDate d0 = c.dataNegociacao() != null ? c.dataNegociacao() : LocalDate.now();
        Calendario cal = calendarios.calendario("BRL", c.par().base());
        LocalDate vencimento = cal.ajustarModifiedFollowing(c.dataVencimento());
        LocalDate liquidacao = cal.somarDiasUteis(vencimento, 2);
        Preco p = precificar(c.par(), c.tipo(), c.strike(), c.notional(), d0, vencimento, c.volatilidade());
        BigDecimal premio = c.premio() != null ? c.premio() : p.premioTotal();

        OpcaoCambio op = new OpcaoCambio(numerador.proximo("OPC"), c.contraparteId(), c.par(), c.tipo(), c.posicao(), c.notional(), c.strike(),
                premio, p.premioTotal(), p.volatilidade(), d0, vencimento, liquidacao, c.operador());
        opcoes.save(op);

        Money fluxo = op.fluxoPremio();
        if (!fluxo.ehZero()) {
            Lancamento.Builder b = Lancamento.novo("OPC:" + op.getNumero() + ":PREMIO", d0, "Prêmio opção " + op.getNumero(), ORIGEM).referencia(op.getId()).criadoPor(c.operador());
            if (op.getPosicao() == PosicaoOpcao.COMPRADA) b.debito(conta(COMPRADAS), fluxo.absoluto()).credito(conta(CAIXA_BRL), fluxo.absoluto());
            else b.debito(conta(CAIXA_BRL), fluxo).credito(conta(LANCADAS), fluxo);
            ledger.salvar(b.build());
        }
        return op;
    }

    // ---------- marcação ----------

    public MarcacaoOpcao marcar(UUID id, LocalDate data) {
        OpcaoCambio op = buscar(id);
        if (op.getSituacao() != SituacaoOpcao.ABERTA) throw new DomainException("opção " + op.getNumero() + " não está aberta");
        if (!data.isBefore(op.getDataVencimento())) throw new DomainException("no vencimento use o exercício, não a marcação");
        Preco p = precificar(op.parMoedas(), op.getTipo(), op.getStrike(), op.getNotional(), data, op.getDataVencimento(), null);
        int sinal = op.getPosicao().sinal();
        Money valor = op.valorPara(p.precoUnitario().doubleValue());

        marcacoes.findByOpcaoIdAndData(id, data).ifPresent(marcacoes::delete);
        marcacoes.findFirstByOpcaoIdOrderByDataDesc(id).ifPresent(ant -> estornar(ant.getLancamentoId(), data));

        BigDecimal n = op.getNotional().multiply(BigDecimal.valueOf(sinal));
        MarcacaoOpcao m = new MarcacaoOpcao(id, data, p.spot(), p.forward(), p.volatilidade(), p.prazoAnos(), p.precoUnitario(),
                valor.quantia().setScale(4, RoundingMode.HALF_EVEN),
                p.delta().multiply(n).setScale(8, RoundingMode.HALF_EVEN), p.gamma().multiply(n).setScale(10, RoundingMode.HALF_EVEN),
                p.vega().multiply(n).setScale(6, RoundingMode.HALF_EVEN), p.theta().multiply(n).setScale(6, RoundingMode.HALF_EVEN),
                p.rho().multiply(n).setScale(6, RoundingMode.HALF_EVEN));

        // variação frente ao prêmio: valor da posição − fluxo inicial (comprada: −prêmio; lançada: +prêmio)
        Money variacao = valor.mais(op.fluxoPremio());
        if (!variacao.ehZero()) {
            Lancamento l = lancarResultado(op, data, variacao, "MTM:" + op.getNumero() + ":" + data, "MtM opção " + op.getNumero());
            m.vincularLancamento(l.getId());
        }
        return marcacoes.save(m);
    }

    /** Lança uma variação de resultado contra a conta de opções da posição. */
    private Lancamento lancarResultado(OpcaoCambio op, LocalDate data, Money variacao, String chave, String descricao) {
        ContaContabil contaOpcao = conta(op.getPosicao() == PosicaoOpcao.COMPRADA ? COMPRADAS : LANCADAS);
        Lancamento.Builder b = Lancamento.novo(chave, data, descricao, ORIGEM).referencia(op.getId()).criadoPor("sistema");
        if (variacao.ehPositivo()) b.debito(contaOpcao, variacao).credito(conta(RESULTADO), variacao);
        else b.debito(conta(PERDA), variacao.absoluto()).credito(contaOpcao, variacao.absoluto());
        return ledger.salvar(b.build());
    }

    private void estornar(UUID lancamentoId, LocalDate data) {
        if (lancamentoId == null) return;
        Lancamento l = ledger.lancamento(lancamentoId);
        if (!l.isEstornado()) ledger.estornar(l.getId(), data, "sistema");
    }

    public NdfService.ResultadoLote marcarTodas(LocalDate data) {
        int ok = 0;
        List<String> ignoradas = new ArrayList<>();
        for (OpcaoCambio op : opcoes.findAllBySituacao(SituacaoOpcao.ABERTA)) {
            try { marcar(op.getId(), data); ok++; } catch (DomainException e) { ignoradas.add(op.getNumero() + ": " + e.getMessage()); }
        }
        return new NdfService.ResultadoLote(ok, ignoradas);
    }

    // ---------- exercício ----------

    /** No vencimento: fixa pela PTAX, calcula o payoff, liquida pelo caixa e fecha a conta de opções contra resultado. */
    public OpcaoCambio exercer(UUID id) {
        OpcaoCambio op = buscar(id);
        BigDecimal ptax = mercado.ptaxNaData(op.parMoedas(), op.getDataVencimento());
        op.exercerOuExpirar(ptax);
        LocalDate d = op.getDataLiquidacao();
        marcacoes.findFirstByOpcaoIdOrderByDataDesc(id).ifPresent(ant -> estornar(ant.getLancamentoId(), d));

        Money payoff = op.payoffMoney();
        ContaContabil contaOpcao = conta(op.getPosicao() == PosicaoOpcao.COMPRADA ? COMPRADAS : LANCADAS);
        if (!payoff.ehZero()) {
            Lancamento.Builder b = Lancamento.novo("OPC:" + op.getNumero() + ":PAYOFF", d, "Exercício opção " + op.getNumero(), ORIGEM).referencia(id).criadoPor("sistema");
            if (payoff.ehPositivo()) b.debito(conta(CAIXA_BRL), payoff).credito(contaOpcao, payoff);
            else b.debito(contaOpcao, payoff.absoluto()).credito(conta(CAIXA_BRL), payoff.absoluto());
            ledger.salvar(b.build());
        }
        // resultado realizado = payoff − fluxo inicial invertido = payoff + fluxoPremio
        Money resultado = payoff.mais(op.fluxoPremio());
        if (!resultado.ehZero()) lancarResultado(op, d, resultado, "OPC:" + op.getNumero() + ":RESULTADO", "Resultado opção " + op.getNumero());
        return op;
    }

    public List<OpcaoCambio> exercerVencidas(LocalDate ate) {
        List<OpcaoCambio> lista = opcoes.findAllBySituacaoAndDataVencimentoLessThanEqual(SituacaoOpcao.ABERTA, ate);
        lista.forEach(o -> exercer(o.getId()));
        return lista;
    }

    public OpcaoCambio cancelar(UUID id, String operador) {
        OpcaoCambio op = buscar(id);
        op.cancelar();
        for (Lancamento l : ledger.lancamentosDaReferencia(ORIGEM, id)) if (!l.isEstornado()) ledger.estornar(l.getId(), LocalDate.now(), operador);
        return op;
    }

    // ---------- consultas ----------

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public OpcaoCambio buscar(UUID id) { return opcoes.findById(id).orElseThrow(() -> new NaoEncontradoException("opção", id)); }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public Page<OpcaoCambio> listar(SituacaoOpcao situacao, Pageable pageable) {
        return situacao != null ? opcoes.findAllBySituacaoOrderByDataNegociacaoDesc(situacao, pageable) : opcoes.findAllByOrderByDataNegociacaoDesc(pageable);
    }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public List<MarcacaoOpcao> marcacoes(UUID id) { buscar(id); return marcacoes.findAllByOpcaoIdOrderByData(id); }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public List<OpcaoCambio> abertas() { return opcoes.findAllBySituacao(SituacaoOpcao.ABERTA); }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public java.util.Optional<MarcacaoOpcao> ultimaMarcacao(UUID id) { return marcacoes.findFirstByOpcaoIdOrderByDataDesc(id); }

    private ContaContabil conta(String codigo) { return ledger.contaPorCodigo(codigo); }
}
