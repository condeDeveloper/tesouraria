package br.com.conde.tesouraria.derivativos.domain;

import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Opção europeia de câmbio (CALL ou PUT sobre a moeda base), liquidada financeiramente em BRL pela PTAX do vencimento. */
@Entity
@Table(name = "opcao_cambio")
public class OpcaoCambio {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(name = "contraparte_id", nullable = false)
    private UUID contraparteId;

    @Column(nullable = false, length = 6)
    private String par;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 4)
    private GarmanKohlhagen.Tipo tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private PosicaoOpcao posicao;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal notional;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal strike;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal premio;

    @Column(name = "premio_teorico", precision = 19, scale = 4)
    private BigDecimal premioTeorico;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal volatilidade;

    @Column(name = "data_negociacao", nullable = false)
    private LocalDate dataNegociacao;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Column(name = "data_liquidacao", nullable = false)
    private LocalDate dataLiquidacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoOpcao situacao;

    @Column(name = "taxa_fixing", precision = 19, scale = 8)
    private BigDecimal taxaFixing;

    @Column(precision = 19, scale = 4)
    private BigDecimal payoff;

    @Column(name = "criado_por", nullable = false, length = 40)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "encerrado_em")
    private Instant encerradoEm;

    protected OpcaoCambio() {}

    public OpcaoCambio(String numero, UUID contraparteId, ParMoedas par, GarmanKohlhagen.Tipo tipo, PosicaoOpcao posicao,
                       BigDecimal notional, BigDecimal strike, BigDecimal premio, BigDecimal premioTeorico, BigDecimal volatilidade,
                       LocalDate dataNegociacao, LocalDate dataVencimento, LocalDate dataLiquidacao, String criadoPor) {
        if (numero == null || numero.isBlank()) throw new DomainException("número obrigatório");
        if (contraparteId == null) throw new DomainException("contraparte obrigatória");
        if (!par.cotada().equals("BRL")) throw new DomainException("opção liquida em BRL: o par deve ser cotado em BRL");
        if (tipo == null || posicao == null) throw new DomainException("tipo e posição obrigatórios");
        if (notional == null || notional.signum() <= 0) throw new DomainException("notional deve ser positivo");
        if (strike == null || strike.signum() <= 0) throw new DomainException("strike deve ser positivo");
        if (premio == null || premio.signum() < 0) throw new DomainException("prêmio não pode ser negativo");
        if (volatilidade == null || volatilidade.signum() <= 0) throw new DomainException("volatilidade deve ser positiva");
        if (dataNegociacao == null || dataVencimento == null || dataLiquidacao == null) throw new DomainException("datas obrigatórias");
        if (!dataVencimento.isAfter(dataNegociacao)) throw new DomainException("vencimento deve ser posterior à negociação");
        if (dataLiquidacao.isBefore(dataVencimento)) throw new DomainException("liquidação não pode ser anterior ao vencimento");
        this.id = UUID.randomUUID();
        this.numero = numero;
        this.contraparteId = contraparteId;
        this.par = par.codigo();
        this.tipo = tipo;
        this.posicao = posicao;
        this.notional = notional.setScale(4, RoundingMode.HALF_EVEN);
        this.strike = strike.setScale(8, RoundingMode.HALF_EVEN);
        this.premio = premio.setScale(4, RoundingMode.HALF_EVEN);
        this.premioTeorico = premioTeorico == null ? null : premioTeorico.setScale(4, RoundingMode.HALF_EVEN);
        this.volatilidade = volatilidade.setScale(8, RoundingMode.HALF_EVEN);
        this.dataNegociacao = dataNegociacao;
        this.dataVencimento = dataVencimento;
        this.dataLiquidacao = dataLiquidacao;
        this.situacao = SituacaoOpcao.ABERTA;
        this.criadoPor = criadoPor == null ? "sistema" : criadoPor;
        this.criadoEm = Instant.now();
    }

    /** Payoff em BRL do ponto de vista da tesouraria (negativo se lançada e exercida contra). */
    public Money payoffPara(BigDecimal taxaFinal) {
        double unitario = GarmanKohlhagen.payoff(tipo, taxaFinal.doubleValue(), strike.doubleValue());
        return Money.of(BigDecimal.valueOf(unitario).multiply(notional).multiply(BigDecimal.valueOf(posicao.sinal())), "BRL");
    }

    /** Valor da posição em BRL a partir do preço unitário do modelo. */
    public Money valorPara(double precoUnitario) {
        return Money.of(BigDecimal.valueOf(precoUnitario).multiply(notional).multiply(BigDecimal.valueOf(posicao.sinal())), "BRL");
    }

    /** Prêmio como fluxo de caixa da tesouraria: negativo se comprada (paga), positivo se lançada (recebe). */
    public Money fluxoPremio() { return Money.of(premio.multiply(BigDecimal.valueOf(-posicao.sinal())), "BRL"); }

    public void exercerOuExpirar(BigDecimal taxaFinal) {
        if (situacao != SituacaoOpcao.ABERTA) throw new DomainException("opção " + numero + " não está aberta");
        if (taxaFinal == null || taxaFinal.signum() <= 0) throw new DomainException("taxa de fixing inválida");
        this.taxaFixing = taxaFinal.setScale(8, RoundingMode.HALF_EVEN);
        Money p = payoffPara(this.taxaFixing);
        this.payoff = p.quantia().setScale(4, RoundingMode.HALF_EVEN);
        this.situacao = p.ehZero() ? SituacaoOpcao.EXPIRADA : SituacaoOpcao.EXERCIDA;
        this.encerradoEm = Instant.now();
    }

    public void cancelar() {
        if (situacao != SituacaoOpcao.ABERTA) throw new DomainException("só opções abertas podem ser canceladas");
        this.situacao = SituacaoOpcao.CANCELADA;
        this.encerradoEm = Instant.now();
    }

    public Money payoffMoney() { return payoff == null ? Money.zero("BRL") : Money.of(payoff, "BRL"); }
    public Money premioMoney() { return Money.of(premio, "BRL"); }
    public ParMoedas parMoedas() { return ParMoedas.de(par); }

    public UUID getId() { return id; }
    public String getNumero() { return numero; }
    public UUID getContraparteId() { return contraparteId; }
    public String getPar() { return par; }
    public GarmanKohlhagen.Tipo getTipo() { return tipo; }
    public PosicaoOpcao getPosicao() { return posicao; }
    public BigDecimal getNotional() { return notional; }
    public BigDecimal getStrike() { return strike; }
    public BigDecimal getPremio() { return premio; }
    public BigDecimal getPremioTeorico() { return premioTeorico; }
    public BigDecimal getVolatilidade() { return volatilidade; }
    public LocalDate getDataNegociacao() { return dataNegociacao; }
    public LocalDate getDataVencimento() { return dataVencimento; }
    public LocalDate getDataLiquidacao() { return dataLiquidacao; }
    public SituacaoOpcao getSituacao() { return situacao; }
    public BigDecimal getTaxaFixing() { return taxaFixing; }
    public BigDecimal getPayoff() { return payoff; }
    public String getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getEncerradoEm() { return encerradoEm; }
}
