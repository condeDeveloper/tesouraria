package br.com.conde.tesouraria.derivativos.application;

import br.com.conde.tesouraria.calendario.application.CalendarioService;
import br.com.conde.tesouraria.calendario.domain.Calendario;
import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.derivativos.domain.*;
import br.com.conde.tesouraria.ledger.application.LedgerService;
import br.com.conde.tesouraria.ledger.domain.ContaContabil;
import br.com.conde.tesouraria.ledger.domain.Lancamento;
import br.com.conde.tesouraria.mercado.application.MercadoService;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * NDF: contratação, marcação a mercado, fixing e liquidação.
 * <p>
 * Contabilização:
 * <pre>
 *  marcação   VP &gt; 0: D Derivativos ajuste positivo (1.3.01)   C Resultado com derivativos (4.2.01)
 *             VP &lt; 0: D Perda com derivativos (5.2.01)         C Derivativos ajuste negativo (2.3.01)
 *             cada nova marcação estorna a anterior e lança o VP cheio
 *  liquidação estorna a última marcação e reconhece o ajuste realizado contra o caixa BRL
 * </pre>
 */
@Service
@Transactional
public class NdfService {

    public static final String ORIGEM = "NDF";
    static final String CAIXA_BRL = "1.1.01.BRL";
    static final String AJUSTE_POSITIVO = "1.3.01.BRL";
    static final String AJUSTE_NEGATIVO = "2.3.01.BRL";
    static final String RESULTADO = "4.2.01.BRL";
    static final String PERDA = "5.2.01.BRL";

    private final ContratoNdfRepository contratos;
    private final MarcacaoMercadoRepository marcacoes;
    private final ContraparteService contrapartes;
    private final MoedaService moedas;
    private final MercadoService mercado;
    private final CalendarioService calendarios;
    private final LedgerService ledger;
    private final NumeradorService numerador;

    public NdfService(ContratoNdfRepository contratos, MarcacaoMercadoRepository marcacoes, ContraparteService contrapartes,
                      MoedaService moedas, MercadoService mercado, CalendarioService calendarios, LedgerService ledger, NumeradorService numerador) {
        this.contratos = contratos;
        this.marcacoes = marcacoes;
        this.contrapartes = contrapartes;
        this.moedas = moedas;
        this.mercado = mercado;
        this.calendarios = calendarios;
        this.ledger = ledger;
        this.numerador = numerador;
    }

    public record Contratacao(UUID contraparteId, ParMoedas par, LadoOperacao lado, BigDecimal notional, BigDecimal taxaTermo,
                              LocalDate dataNegociacao, LocalDate dataLiquidacao, LocalDate dataFixing, String operador) {}

    /**
     * Contrata um NDF. Sem taxa a termo, usa o forward teórico. Sem data de fixing, usa o dia útil
     * anterior à liquidação (PTAX de D-1), convenção de mercado. A liquidação é ajustada para dia útil.
     */
    public ContratoNdf contratar(Contratacao c) {
        contrapartes.exigirApta(c.contraparteId());
        moedas.exigirAtiva(c.par().base());
        LocalDate d0 = c.dataNegociacao() != null ? c.dataNegociacao() : LocalDate.now();
        Calendario cal = calendarios.calendario("BRL", c.par().base());
        LocalDate liquidacao = cal.ajustarModifiedFollowing(c.dataLiquidacao());
        LocalDate fixing = c.dataFixing() != null ? c.dataFixing() : calendarios.calendario("BRL").somarDiasUteis(liquidacao, -1);
        BigDecimal forward = tentarForward(c.par(), d0, liquidacao);
        BigDecimal termo = c.taxaTermo() != null ? c.taxaTermo() : forward;
        if (termo == null) throw new DomainException("sem taxa a termo informada e sem curvas para calcular o forward teórico");
        ContratoNdf ndf = new ContratoNdf(numerador.proximo("NDF"), c.contraparteId(), c.par(), c.lado(), c.notional(), termo, forward,
                d0, fixing, liquidacao, c.operador());
        return contratos.save(ndf);
    }

    private BigDecimal tentarForward(ParMoedas par, LocalDate data, LocalDate vencimento) {
        try { return mercado.forwardTeorico(par, data, vencimento).forward(); } catch (DomainException e) { return null; }
    }

    // ---------- marcação a mercado ----------

    /** Marca o contrato na data: calcula o VP, estorna a marcação anterior e lança a nova. */
    public MarcacaoMercado marcar(UUID id, LocalDate data) {
        ContratoNdf ndf = buscar(id);
        if (ndf.getSituacao() != SituacaoContrato.ABERTO) throw new DomainException("contrato " + ndf.getNumero() + " não está aberto para marcação");
        if (data.isAfter(ndf.getDataFixing())) throw new DomainException("data de marcação posterior ao fixing; use o fixing");

        MercadoService.Termo termo = mercado.forwardTeorico(ndf.parMoedas(), data, ndf.getDataLiquidacao());
        int du = calendarios.calendario("BRL").diasUteisEntre(data, ndf.getDataLiquidacao());
        BigDecimal df = mercado.curva(MercadoService.CURVA_DI, data).fatorDesconto(Math.max(1, du));
        Money vp = ndf.valorPresente(termo.forward(), df);

        marcacoes.findByContratoIdAndData(id, data).ifPresent(marcacoes::delete);
        marcacoes.findFirstByContratoIdOrderByDataDesc(id).ifPresent(ant -> estornarMarcacao(ant, data));

        MarcacaoMercado m = new MarcacaoMercado(id, data, termo.spot(), termo.forward(), df, vp.quantia().setScale(4, Money.ARREDONDAMENTO));
        if (!vp.ehZero()) {
            Lancamento l = lancamentoMtm(ndf, data, vp, "MTM:" + ndf.getNumero() + ":" + data);
            m.vincularLancamento(l.getId());
        }
        return marcacoes.save(m);
    }

    private Lancamento lancamentoMtm(ContratoNdf ndf, LocalDate data, Money vp, String chave) {
        Lancamento.Builder b = Lancamento.novo(chave, data, "MtM NDF " + ndf.getNumero(), ORIGEM).referencia(ndf.getId()).criadoPor("sistema");
        if (vp.ehPositivo()) b.debito(conta(AJUSTE_POSITIVO), vp).credito(conta(RESULTADO), vp);
        else b.debito(conta(PERDA), vp.absoluto()).credito(conta(AJUSTE_NEGATIVO), vp.absoluto());
        return ledger.salvar(b.build());
    }

    private void estornarMarcacao(MarcacaoMercado anterior, LocalDate data) {
        if (anterior.getLancamentoId() == null) return;
        Lancamento l = ledger.lancamento(anterior.getLancamentoId());
        if (!l.isEstornado()) ledger.estornar(l.getId(), data, "sistema");
    }

    /** Marca todos os contratos abertos na data. Contratos sem curva/cotação são ignorados e reportados. */
    public record ResultadoLote(int marcados, List<String> ignorados) {}

    public ResultadoLote marcarTodos(LocalDate data) {
        int ok = 0;
        List<String> ignorados = new ArrayList<>();
        for (ContratoNdf ndf : contratos.findAllBySituacao(SituacaoContrato.ABERTO)) {
            try { marcar(ndf.getId(), data); ok++; }
            catch (DomainException e) { ignorados.add(ndf.getNumero() + ": " + e.getMessage()); }
        }
        return new ResultadoLote(ok, ignorados);
    }

    // ---------- fixing e liquidação ----------

    /** Fixa pela PTAX da data de fixing. Estorna a última marcação e lança o ajuste a receber/pagar. */
    public ContratoNdf fixar(UUID id) {
        ContratoNdf ndf = buscar(id);
        BigDecimal ptax = mercado.ptaxNaData(ndf.parMoedas(), ndf.getDataFixing());
        ndf.fixar(ptax);
        marcacoes.findFirstByContratoIdOrderByDataDesc(id).ifPresent(ant -> estornarMarcacao(ant, ndf.getDataFixing()));
        Money ajuste = ndf.ajusteMoney();
        if (!ajuste.ehZero()) lancamentoMtm(ndf, ndf.getDataFixing(), ajuste, "FIXING:" + ndf.getNumero());
        return ndf;
    }

    /** Liquida o ajuste contra o caixa BRL, baixando o direito/obrigação reconhecido no fixing. */
    public ContratoNdf liquidar(UUID id, LocalDate data) {
        ContratoNdf ndf = buscar(id);
        LocalDate d = data != null ? data : ndf.getDataLiquidacao();
        ndf.liquidar(d);
        Money ajuste = ndf.ajusteMoney();
        if (!ajuste.ehZero()) {
            Lancamento.Builder b = Lancamento.novo("LIQ:" + ndf.getNumero(), d, "Liquidação NDF " + ndf.getNumero(), ORIGEM).referencia(id).criadoPor("sistema");
            if (ajuste.ehPositivo()) b.debito(conta(CAIXA_BRL), ajuste).credito(conta(AJUSTE_POSITIVO), ajuste);
            else b.debito(conta(AJUSTE_NEGATIVO), ajuste.absoluto()).credito(conta(CAIXA_BRL), ajuste.absoluto());
            ledger.salvar(b.build());
        }
        return ndf;
    }

    public List<ContratoNdf> fixarVencidos(LocalDate ate) {
        List<ContratoNdf> lista = contratos.findAllBySituacaoAndDataFixingLessThanEqual(SituacaoContrato.ABERTO, ate);
        lista.forEach(n -> fixar(n.getId()));
        return lista;
    }

    public List<ContratoNdf> liquidarVencidos(LocalDate ate) {
        List<ContratoNdf> lista = contratos.findAllBySituacaoAndDataLiquidacaoLessThanEqual(SituacaoContrato.FIXADO, ate);
        lista.forEach(n -> liquidar(n.getId(), ate));
        return lista;
    }

    public ContratoNdf cancelar(UUID id, String operador) {
        ContratoNdf ndf = buscar(id);
        ndf.cancelar();
        for (Lancamento l : ledger.lancamentosDaReferencia(ORIGEM, id)) if (!l.isEstornado()) ledger.estornar(l.getId(), LocalDate.now(), operador);
        return ndf;
    }

    // ---------- consultas ----------

    @Transactional(readOnly = true)
    public ContratoNdf buscar(UUID id) { return contratos.findById(id).orElseThrow(() -> new NaoEncontradoException("contrato NDF", id)); }

    @Transactional(readOnly = true)
    public Page<ContratoNdf> listar(SituacaoContrato situacao, UUID contraparteId, Pageable pageable) {
        if (contraparteId != null) return contratos.findAllByContraparteIdOrderByDataNegociacaoDesc(contraparteId, pageable);
        if (situacao != null) return contratos.findAllBySituacaoOrderByDataNegociacaoDesc(situacao, pageable);
        return contratos.findAllByOrderByDataNegociacaoDesc(pageable);
    }

    @Transactional(readOnly = true)
    public List<MarcacaoMercado> marcacoes(UUID id) { buscar(id); return marcacoes.findAllByContratoIdOrderByData(id); }

    @Transactional(readOnly = true)
    public List<ContratoNdf> abertos() { return contratos.findAllBySituacao(SituacaoContrato.ABERTO); }

    private ContaContabil conta(String codigo) { return ledger.contaPorCodigo(codigo); }
}
