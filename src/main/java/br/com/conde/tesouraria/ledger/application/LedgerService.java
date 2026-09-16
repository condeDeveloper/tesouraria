package br.com.conde.tesouraria.ledger.application;

import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.contrapartes.domain.Contraparte;
import br.com.conde.tesouraria.ledger.domain.*;
import br.com.conde.tesouraria.moedas.application.MoedaService;
import br.com.conde.tesouraria.shared.domain.ConflitoException;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/** Casos de uso do ledger: plano de contas, lançamentos, estornos, saldos, extratos e balancete. */
@Service
@Transactional
public class LedgerService {

    private final ContaContabilRepository contas;
    private final LancamentoRepository lancamentos;
    private final PartidaRepository partidas;
    private final MoedaService moedas;
    private final ContraparteService contrapartes;

    public LedgerService(ContaContabilRepository contas, LancamentoRepository lancamentos, PartidaRepository partidas,
                         MoedaService moedas, ContraparteService contrapartes) {
        this.contas = contas;
        this.lancamentos = lancamentos;
        this.partidas = partidas;
        this.moedas = moedas;
        this.contrapartes = contrapartes;
    }

    // ---------- plano de contas ----------

    @Transactional(readOnly = true)
    public List<ContaContabil> listarContas() { return contas.findAllByOrderByCodigo(); }

    @Transactional(readOnly = true)
    public ContaContabil conta(UUID id) {
        return contas.findById(id).orElseThrow(() -> new NaoEncontradoException("conta", id));
    }

    @Transactional(readOnly = true)
    public ContaContabil contaPorCodigo(String codigo) {
        return contas.findByCodigo(codigo).orElseThrow(() -> new NaoEncontradoException("conta", codigo));
    }

    public ContaContabil criarConta(String codigo, String nome, TipoConta tipo, String moeda, UUID contraparteId) {
        moedas.exigirAtiva(moeda);
        if (contraparteId != null) contrapartes.buscar(contraparteId);
        ContaContabil c = new ContaContabil(codigo, nome, tipo, moeda, contraparteId);
        if (contas.existsByCodigo(c.getCodigo())) throw new ConflitoException("já existe conta com o código " + c.getCodigo());
        return contas.save(c);
    }

    /**
     * Conta de uma contraparte em uma moeda e tipo, criada na primeira necessidade.
     * Código gerado: C.&lt;tipo&gt;.&lt;8 primeiros do id&gt;.&lt;moeda&gt;
     */
    public ContaContabil contaDaContraparte(UUID contraparteId, TipoConta tipo, String moeda) {
        return contas.findByContraparteIdAndTipoAndMoeda(contraparteId, tipo, moeda).orElseGet(() -> {
            Contraparte cp = contrapartes.buscar(contraparteId);
            moedas.exigirAtiva(moeda);
            String codigo = "C." + tipo.name().charAt(0) + "." + cp.getId().toString().substring(0, 8) + "." + moeda;
            String nome = (tipo == TipoConta.ATIVO ? "A receber de " : "A pagar a ") + cp.getNome() + " " + moeda;
            return contas.save(new ContaContabil(codigo, nome, tipo, moeda, contraparteId));
        });
    }

    public ContaContabil alterarSituacaoConta(UUID id, boolean ativa) {
        ContaContabil c = conta(id);
        if (ativa) c.ativar(); else c.desativar();
        return c;
    }

    // ---------- lançamentos ----------

    public record Linha(UUID contaId, String codigoConta, TipoPartida tipo, BigDecimal valor, String moeda) {}

    public record Comando(String chaveIdempotencia, LocalDate data, String descricao, String origem, UUID referenciaId,
                          String criadoPor, List<Linha> linhas) {}

    /**
     * Registra um lançamento. Se a chave de idempotência já existir, devolve o lançamento original
     * sem criar outro (repetição segura de requisições).
     */
    public Lancamento registrar(Comando cmd) {
        Optional<Lancamento> existente = lancamentos.findByChaveIdempotencia(cmd.chaveIdempotencia());
        if (existente.isPresent()) return existente.get();

        Lancamento.Builder b = Lancamento.novo(cmd.chaveIdempotencia(), cmd.data(), cmd.descricao(), cmd.origem())
                .referencia(cmd.referenciaId()).criadoPor(cmd.criadoPor());
        if (cmd.linhas() == null || cmd.linhas().isEmpty()) throw new DomainException("lançamento sem partidas");
        for (Linha l : cmd.linhas()) {
            ContaContabil c = l.contaId() != null ? conta(l.contaId()) : contaPorCodigo(l.codigoConta());
            Money m = Money.of(l.valor(), l.moeda() != null ? l.moeda() : c.getMoeda());
            if (l.tipo() == TipoPartida.DEBITO) b.debito(c, m); else b.credito(c, m);
        }
        return lancamentos.save(b.build());
    }

    /** Atalho para os módulos internos: lançamento simples de duas partidas. */
    public Lancamento registrarSimples(String chave, LocalDate data, String descricao, String origem, UUID referenciaId,
                                       String criadoPor, ContaContabil debito, ContaContabil credito, Money valor) {
        return lancamentos.findByChaveIdempotencia(chave).orElseGet(() ->
                lancamentos.save(Lancamento.novo(chave, data, descricao, origem).referencia(referenciaId).criadoPor(criadoPor)
                        .debito(debito, valor).credito(credito, valor).build()));
    }

    public Lancamento salvar(Lancamento l) {
        return lancamentos.findByChaveIdempotencia(l.getChaveIdempotencia()).orElseGet(() -> lancamentos.save(l));
    }

    @Transactional(readOnly = true)
    public Lancamento lancamento(UUID id) {
        return lancamentos.findById(id).orElseThrow(() -> new NaoEncontradoException("lançamento", id));
    }

    @Transactional(readOnly = true)
    public Page<Lancamento> listarLancamentos(LocalDate inicio, LocalDate fim, Pageable pageable) {
        return lancamentos.findAllByDataLancamentoBetweenOrderByDataLancamentoDescCriadoEmDesc(inicio, fim, pageable);
    }

    @Transactional(readOnly = true)
    public List<Lancamento> lancamentosDaReferencia(String origem, UUID referenciaId) {
        return lancamentos.findAllByOrigemAndReferenciaIdOrderByDataLancamento(origem.toUpperCase(), referenciaId);
    }

    public Lancamento estornar(UUID id, LocalDate data, String criadoPor) {
        Lancamento original = lancamento(id);
        Lancamento estorno = original.estornar("ESTORNO:" + original.getChaveIdempotencia(), data, criadoPor);
        return lancamentos.save(estorno);
    }

    // ---------- saldos ----------

    @Transactional(readOnly = true)
    public Money saldo(UUID contaId, LocalDate ate) {
        ContaContabil c = conta(contaId);
        BigDecimal liquido = partidas.debitoLiquidoAte(contaId, ate);
        return Money.of(aplicarNatureza(c, liquido), c.getMoeda());
    }

    public record LinhaExtrato(LocalDate data, String descricao, TipoPartida tipo, BigDecimal valor, BigDecimal saldo, UUID lancamentoId) {}

    @Transactional(readOnly = true)
    public List<LinhaExtrato> extrato(UUID contaId, LocalDate inicio, LocalDate fim) {
        ContaContabil c = conta(contaId);
        BigDecimal saldo = aplicarNatureza(c, partidas.debitoLiquidoAte(contaId, inicio.minusDays(1)));
        List<LinhaExtrato> out = new ArrayList<>();
        for (Partida p : partidas.extrato(contaId, inicio, fim)) {
            saldo = saldo.add(aplicarNatureza(c, p.debitoLiquido()));
            out.add(new LinhaExtrato(p.getLancamento().getDataLancamento(), p.getLancamento().getDescricao(),
                    p.getTipo(), p.getValor(), saldo, p.getLancamento().getId()));
        }
        return out;
    }

    public record LinhaBalancete(String codigo, String nome, TipoConta tipo, String moeda, BigDecimal saldo) {}

    /** Saldo de todas as contas na data; contas sem movimento aparecem com zero. */
    @Transactional(readOnly = true)
    public List<LinhaBalancete> balancete(LocalDate data) {
        Map<UUID, BigDecimal> liquidos = new HashMap<>();
        for (Object[] row : partidas.debitoLiquidoPorConta(data)) liquidos.put((UUID) row[0], (BigDecimal) row[1]);
        return contas.findAllByOrderByCodigo().stream()
                .map(c -> new LinhaBalancete(c.getCodigo(), c.getNome(), c.getTipo(), c.getMoeda(),
                        aplicarNatureza(c, liquidos.getOrDefault(c.getId(), BigDecimal.ZERO)).setScale(2, Money.ARREDONDAMENTO)))
                .toList();
    }

    private static BigDecimal aplicarNatureza(ContaContabil c, BigDecimal debitoLiquido) {
        return c.getNatureza() == NaturezaConta.DEVEDORA ? debitoLiquido : debitoLiquido.negate();
    }
}
