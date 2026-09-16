package br.com.conde.tesouraria.derivativos.domain;

import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * NDF (Non-Deliverable Forward): contrato a termo de moeda sem entrega física. No fixing,
 * compara-se a taxa a termo com a PTAX e liquida-se a diferença em BRL.
 * <p>
 * Lado COMPRA = tesouraria compra a moeda base a termo: ganha se a PTAX subir acima da taxa a termo.
 */
@Entity
@Table(name = "contrato_ndf")
public class ContratoNdf {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(name = "contraparte_id", nullable = false)
    private UUID contraparteId;

    @Column(nullable = false, length = 6)
    private String par;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private LadoOperacao lado;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal notional;

    @Column(name = "moeda_base", nullable = false, length = 3)
    private String moedaBase;

    @Column(name = "moeda_liquidacao", nullable = false, length = 3)
    private String moedaLiquidacao;

    @Column(name = "taxa_termo", nullable = false, precision = 19, scale = 8)
    private BigDecimal taxaTermo;

    @Column(name = "forward_referencia", precision = 19, scale = 8)
    private BigDecimal forwardReferencia;

    @Column(name = "data_negociacao", nullable = false)
    private LocalDate dataNegociacao;

    @Column(name = "data_fixing", nullable = false)
    private LocalDate dataFixing;

    @Column(name = "data_liquidacao", nullable = false)
    private LocalDate dataLiquidacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoContrato situacao;

    @Column(name = "taxa_fixing", precision = 19, scale = 8)
    private BigDecimal taxaFixing;

    @Column(precision = 19, scale = 4)
    private BigDecimal ajuste;

    @Column(name = "criado_por", nullable = false, length = 40)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "fixado_em")
    private Instant fixadoEm;

    @Column(name = "liquidado_em")
    private Instant liquidadoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    protected ContratoNdf() {}

    public ContratoNdf(String numero, UUID contraparteId, ParMoedas par, LadoOperacao lado, BigDecimal notional,
                       BigDecimal taxaTermo, BigDecimal forwardReferencia, LocalDate dataNegociacao,
                       LocalDate dataFixing, LocalDate dataLiquidacao, String criadoPor) {
        if (numero == null || numero.isBlank()) throw new DomainException("número do contrato obrigatório");
        if (contraparteId == null) throw new DomainException("contraparte obrigatória");
        if (!par.cotada().equals("BRL")) throw new DomainException("NDF liquida em BRL: o par deve ser cotado em BRL");
        if (lado == null) throw new DomainException("lado obrigatório");
        if (notional == null || notional.signum() <= 0) throw new DomainException("notional deve ser positivo");
        if (taxaTermo == null || taxaTermo.signum() <= 0) throw new DomainException("taxa a termo deve ser positiva");
        if (dataNegociacao == null || dataFixing == null || dataLiquidacao == null) throw new DomainException("datas obrigatórias");
        if (dataFixing.isBefore(dataNegociacao) || dataLiquidacao.isBefore(dataFixing)) throw new DomainException("datas fora de ordem: negociação ≤ fixing ≤ liquidação");
        if (!dataLiquidacao.isAfter(dataNegociacao)) throw new DomainException("liquidação deve ser posterior à negociação");
        this.id = UUID.randomUUID();
        this.numero = numero;
        this.contraparteId = contraparteId;
        this.par = par.codigo();
        this.moedaBase = par.base();
        this.moedaLiquidacao = par.cotada();
        this.lado = lado;
        this.notional = notional.setScale(4, RoundingMode.HALF_EVEN);
        this.taxaTermo = taxaTermo.setScale(8, RoundingMode.HALF_EVEN);
        this.forwardReferencia = forwardReferencia == null ? null : forwardReferencia.setScale(8, RoundingMode.HALF_EVEN);
        this.dataNegociacao = dataNegociacao;
        this.dataFixing = dataFixing;
        this.dataLiquidacao = dataLiquidacao;
        this.situacao = SituacaoContrato.ABERTO;
        this.criadoPor = criadoPor == null ? "sistema" : criadoPor;
        this.criadoEm = Instant.now();
    }

    /** Sinal do payoff: +1 para COMPRA (ganha com alta), -1 para VENDA. */
    private BigDecimal sinal() { return lado == LadoOperacao.COMPRA ? BigDecimal.ONE : BigDecimal.ONE.negate(); }

    /** Ajuste em BRL para uma taxa final: sinal × (taxa − termo) × notional. Positivo = a receber. */
    public Money ajustePara(BigDecimal taxaFinal) {
        return Money.of(sinal().multiply(taxaFinal.subtract(taxaTermo)).multiply(notional), moedaLiquidacao);
    }

    /** Valor presente em BRL: ajuste ao forward teórico atual, descontado até a liquidação. */
    public Money valorPresente(BigDecimal forwardTeorico, BigDecimal fatorDesconto) {
        return ajustePara(forwardTeorico).vezes(fatorDesconto);
    }

    public void fixar(BigDecimal ptax) {
        if (situacao != SituacaoContrato.ABERTO) throw new DomainException("contrato " + numero + " não está aberto");
        if (ptax == null || ptax.signum() <= 0) throw new DomainException("taxa de fixing inválida");
        this.taxaFixing = ptax.setScale(8, RoundingMode.HALF_EVEN);
        this.ajuste = ajustePara(this.taxaFixing).quantia().setScale(4, RoundingMode.HALF_EVEN);
        this.situacao = SituacaoContrato.FIXADO;
        this.fixadoEm = Instant.now();
    }

    public void liquidar(LocalDate data) {
        if (situacao != SituacaoContrato.FIXADO) throw new DomainException("contrato " + numero + " precisa estar fixado para liquidar");
        if (data.isBefore(dataLiquidacao)) throw new DomainException("contrato " + numero + " só liquida em " + dataLiquidacao);
        this.situacao = SituacaoContrato.LIQUIDADO;
        this.liquidadoEm = Instant.now();
    }

    public void cancelar() {
        if (situacao != SituacaoContrato.ABERTO) throw new DomainException("só contratos abertos podem ser cancelados");
        this.situacao = SituacaoContrato.CANCELADO;
        this.canceladoEm = Instant.now();
    }

    public Money ajusteMoney() { return ajuste == null ? Money.zero(moedaLiquidacao) : Money.of(ajuste, moedaLiquidacao); }
    public ParMoedas parMoedas() { return ParMoedas.de(par); }

    public UUID getId() { return id; }
    public String getNumero() { return numero; }
    public UUID getContraparteId() { return contraparteId; }
    public String getPar() { return par; }
    public LadoOperacao getLado() { return lado; }
    public BigDecimal getNotional() { return notional; }
    public String getMoedaBase() { return moedaBase; }
    public String getMoedaLiquidacao() { return moedaLiquidacao; }
    public BigDecimal getTaxaTermo() { return taxaTermo; }
    public BigDecimal getForwardReferencia() { return forwardReferencia; }
    public LocalDate getDataNegociacao() { return dataNegociacao; }
    public LocalDate getDataFixing() { return dataFixing; }
    public LocalDate getDataLiquidacao() { return dataLiquidacao; }
    public SituacaoContrato getSituacao() { return situacao; }
    public BigDecimal getTaxaFixing() { return taxaFixing; }
    public BigDecimal getAjuste() { return ajuste; }
    public String getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getFixadoEm() { return fixadoEm; }
    public Instant getLiquidadoEm() { return liquidadoEm; }
    public Instant getCanceladoEm() { return canceladoEm; }
}
