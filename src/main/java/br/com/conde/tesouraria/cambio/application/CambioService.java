package br.com.conde.tesouraria.cambio.application;

import br.com.conde.tesouraria.calendario.application.CalendarioService;
import br.com.conde.tesouraria.cambio.domain.*;
import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.ledger.application.LedgerService;
import br.com.conde.tesouraria.ledger.domain.ContaContabil;
import br.com.conde.tesouraria.ledger.domain.Lancamento;
import br.com.conde.tesouraria.mercado.application.MercadoService;
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
import java.util.*;

/**
 * Câmbio pronto. Contabilização por posição de câmbio:
 * <pre>
 *  fechamento  moeda comprada:  D Câmbio comprado a liquidar   C Posição de câmbio
 *              moeda vendida:   D Posição de câmbio            C Câmbio vendido a liquidar
 *  liquidação  moeda comprada:  D Caixa                        C Câmbio comprado a liquidar
 *              moeda vendida:   D Câmbio vendido a liquidar    C Caixa
 * </pre>
 * Cada lançamento fecha na própria moeda; o resultado em BRL aparece na reavaliação da posição.
 */
@Service
@Transactional
public class CambioService {

    public static final String ORIGEM = "CAMBIO";
    public static final int PRAZO_PADRAO_DIAS_UTEIS = 2;

    static final String CAIXA = "1.1.01.";
    static final String COMPRADO_A_LIQUIDAR = "1.2.01.";
    static final String VENDIDO_A_LIQUIDAR = "2.1.01.";
    static final String POSICAO = "2.2.01.";

    private final OperacaoCambioRepository operacoes;
    private final ContraparteService contrapartes;
    private final MoedaService moedas;
    private final MercadoService mercado;
    private final CalendarioService calendarios;
    private final LedgerService ledger;
    private final NumeradorService numerador;

    public CambioService(OperacaoCambioRepository operacoes, ContraparteService contrapartes, MoedaService moedas,
                         MercadoService mercado, CalendarioService calendarios, LedgerService ledger, NumeradorService numerador) {
        this.operacoes = operacoes;
        this.contrapartes = contrapartes;
        this.moedas = moedas;
        this.mercado = mercado;
        this.calendarios = calendarios;
        this.ledger = ledger;
        this.numerador = numerador;
    }

    public record Fechamento(UUID contraparteId, ParMoedas par, LadoOperacao lado, BigDecimal valorBase,
                             BigDecimal taxa, LocalDate dataNegociacao, Integer prazoDiasUteis, String operador) {}

    /**
     * Fecha uma operação. Se a taxa vier nula, usa o spot mais recente. A data de liquidação é
     * D+prazo em dias úteis nas praças das duas moedas.
     */
    public OperacaoCambio fechar(Fechamento f) {
        contrapartes.exigirApta(f.contraparteId());
        moedas.exigirAtiva(f.par().base());
        moedas.exigirAtiva(f.par().cotada());
        LocalDate d0 = f.dataNegociacao() != null ? f.dataNegociacao() : LocalDate.now();
        BigDecimal referencia = tentarReferencia(f.par(), d0);
        BigDecimal taxa = f.taxa() != null ? f.taxa() : referencia;
        if (taxa == null) throw new DomainException("sem taxa informada e sem cotação spot para " + f.par());
        int prazo = f.prazoDiasUteis() != null ? f.prazoDiasUteis() : PRAZO_PADRAO_DIAS_UTEIS;
        if (prazo < 0 || prazo > 5) throw new DomainException("prazo de liquidação deve estar entre D+0 e D+5");
        var cal = calendarios.calendario(f.par().base(), f.par().cotada());
        LocalDate liquidacao = cal.somarDiasUteis(cal.proximoDiaUtil(d0), prazo);

        OperacaoCambio op = new OperacaoCambio(numerador.proximo("CAM"), f.contraparteId(), f.par(), f.lado(),
                f.valorBase(), taxa, referencia, d0, liquidacao, f.operador());
        operacoes.save(op);
        contabilizarFechamento(op);
        return op;
    }

    private BigDecimal tentarReferencia(ParMoedas par, LocalDate data) {
        try { return mercado.taxa(par, TipoCotacao.SPOT, data); } catch (NaoEncontradoException e) { return null; }
    }

    private void contabilizarFechamento(OperacaoCambio op) {
        Money recebe = op.recebe(), entrega = op.entrega();
        Lancamento l = Lancamento.novo("CAM:" + op.getNumero() + ":FECHAMENTO", op.getDataNegociacao(),
                        "Câmbio " + op.getNumero() + " " + op.getLado() + " " + op.getPar() + " @ " + op.getTaxa().stripTrailingZeros().toPlainString(), ORIGEM)
                .referencia(op.getId()).criadoPor(op.getCriadoPor())
                .debito(conta(COMPRADO_A_LIQUIDAR, recebe.moeda()), recebe).credito(conta(POSICAO, recebe.moeda()), recebe)
                .debito(conta(POSICAO, entrega.moeda()), entrega).credito(conta(VENDIDO_A_LIQUIDAR, entrega.moeda()), entrega)
                .build();
        ledger.salvar(l);
    }

    public OperacaoCambio liquidar(UUID id, LocalDate data) {
        OperacaoCambio op = buscar(id);
        LocalDate d = data != null ? data : op.getDataLiquidacao();
        op.liquidar(d);
        Money recebe = op.recebe(), entrega = op.entrega();
        Lancamento l = Lancamento.novo("CAM:" + op.getNumero() + ":LIQUIDACAO", d, "Liquidação câmbio " + op.getNumero(), ORIGEM)
                .referencia(op.getId()).criadoPor(op.getCriadoPor())
                .debito(conta(CAIXA, recebe.moeda()), recebe).credito(conta(COMPRADO_A_LIQUIDAR, recebe.moeda()), recebe)
                .debito(conta(VENDIDO_A_LIQUIDAR, entrega.moeda()), entrega).credito(conta(CAIXA, entrega.moeda()), entrega)
                .build();
        ledger.salvar(l);
        return op;
    }

    /** Liquida todas as operações abertas com data de liquidação até a data. */
    public List<OperacaoCambio> liquidarVencidas(LocalDate ate) {
        List<OperacaoCambio> lista = operacoes.findAllBySituacaoAndDataLiquidacaoLessThanEqual(SituacaoOperacao.ABERTA, ate);
        lista.forEach(op -> liquidar(op.getId(), ate));
        return lista;
    }

    public OperacaoCambio cancelar(UUID id, String operador) {
        OperacaoCambio op = buscar(id);
        op.cancelar();
        for (Lancamento l : ledger.lancamentosDaReferencia(ORIGEM, op.getId())) {
            if (!l.isEstornado()) ledger.estornar(l.getId(), LocalDate.now(), operador);
        }
        return op;
    }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public OperacaoCambio buscar(UUID id) {
        return operacoes.findById(id).orElseThrow(() -> new NaoEncontradoException("operação de câmbio", id));
    }

    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public Page<OperacaoCambio> listar(SituacaoOperacao situacao, UUID contraparteId, Pageable pageable) {
        if (contraparteId != null) return operacoes.findAllByContraparteIdOrderByDataNegociacaoDesc(contraparteId, pageable);
        if (situacao != null) return operacoes.findAllBySituacaoOrderByDataNegociacaoDesc(situacao, pageable);
        return operacoes.findAllByOrderByDataNegociacaoDesc(pageable);
    }

    public record PosicaoMoeda(String moeda, BigDecimal posicao, BigDecimal spot, BigDecimal equivalenteBrl) {}

    public record Posicao(LocalDate data, List<PosicaoMoeda> moedas, BigDecimal resultadoNaoRealizadoBrl) {}

    /**
     * Posição de câmbio por moeda na data (saldo das contas 2.2.01.*, sinal invertido: posição
     * comprada positiva) e o equivalente em BRL ao spot. A soma dos equivalentes é o resultado
     * não realizado das operações de câmbio, já que cada operação nasce com equivalente zero
     * à taxa negociada.
     */
    @Transactional(readOnly = true, noRollbackFor = DomainException.class)
    public Posicao posicao(LocalDate data) {
        List<PosicaoMoeda> lista = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ContaContabil c : ledger.listarContas()) {
            if (!c.getCodigo().startsWith(POSICAO)) continue;
            BigDecimal posicao = ledger.saldo(c.getId(), data).quantia(); // conta credora: saldo positivo = moeda comprada
            if (posicao.signum() == 0) continue;
            BigDecimal spot = spotSeguro(c.getMoeda(), data);
            BigDecimal equiv = spot == null ? null : posicao.multiply(spot).setScale(2, RoundingMode.HALF_EVEN);
            lista.add(new PosicaoMoeda(c.getMoeda(), posicao, spot, equiv));
            if (equiv != null) total = total.add(equiv);
        }
        return new Posicao(data, lista, total);
    }

    /** Spot da moeda contra BRL na data, ou null se não houver cotação (posição fica sem equivalente). */
    private BigDecimal spotSeguro(String moeda, LocalDate data) {
        if (moeda.equals("BRL")) return BigDecimal.ONE;
        try { return mercado.taxa(new ParMoedas(moeda, "BRL"), TipoCotacao.SPOT, data); } catch (NaoEncontradoException e) { return null; }
    }

    private ContaContabil conta(String prefixo, String moeda) {
        return ledger.contaPorCodigo(prefixo + moeda);
    }
}
