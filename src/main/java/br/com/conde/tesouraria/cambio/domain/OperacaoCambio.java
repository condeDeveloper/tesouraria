package br.com.conde.tesouraria.cambio.domain;

import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Operação de câmbio pronto (spot): troca de duas moedas a uma taxa, liquidada em poucos dias úteis. */
@Entity
@Table(name = "operacao_cambio")
public class OperacaoCambio {

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

    @Column(name = "valor_base", nullable = false, precision = 19, scale = 4)
    private BigDecimal valorBase;

    @Column(name = "moeda_base", nullable = false, length = 3)
    private String moedaBase;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal taxa;

    @Column(name = "valor_cotado", nullable = false, precision = 19, scale = 4)
    private BigDecimal valorCotado;

    @Column(name = "moeda_cotada", nullable = false, length = 3)
    private String moedaCotada;

    @Column(name = "taxa_referencia", precision = 19, scale = 8)
    private BigDecimal taxaReferencia;

    @Column(name = "data_negociacao", nullable = false)
    private LocalDate dataNegociacao;

    @Column(name = "data_liquidacao", nullable = false)
    private LocalDate dataLiquidacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoOperacao situacao;

    @Column(name = "criado_por", nullable = false, length = 40)
    private String criadoPor;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "liquidado_em")
    private Instant liquidadoEm;

    @Column(name = "cancelado_em")
    private Instant canceladoEm;

    protected OperacaoCambio() {}

    public OperacaoCambio(String numero, UUID contraparteId, ParMoedas par, LadoOperacao lado, BigDecimal valorBase,
                          BigDecimal taxa, BigDecimal taxaReferencia, LocalDate dataNegociacao, LocalDate dataLiquidacao, String criadoPor) {
        if (numero == null || numero.isBlank()) throw new DomainException("número da operação obrigatório");
        if (contraparteId == null) throw new DomainException("contraparte obrigatória");
        if (lado == null) throw new DomainException("lado da operação obrigatório");
        if (valorBase == null || valorBase.signum() <= 0) throw new DomainException("valor deve ser positivo");
        if (taxa == null || taxa.signum() <= 0) throw new DomainException("taxa deve ser positiva");
        if (dataNegociacao == null || dataLiquidacao == null) throw new DomainException("datas obrigatórias");
        if (dataLiquidacao.isBefore(dataNegociacao)) throw new DomainException("liquidação não pode ser anterior à negociação");
        this.id = UUID.randomUUID();
        this.numero = numero;
        this.contraparteId = contraparteId;
        this.par = par.codigo();
        this.moedaBase = par.base();
        this.moedaCotada = par.cotada();
        this.lado = lado;
        this.valorBase = valorBase.setScale(4, RoundingMode.HALF_EVEN);
        this.taxa = taxa.setScale(8, RoundingMode.HALF_EVEN);
        this.valorCotado = this.valorBase.multiply(this.taxa).setScale(4, RoundingMode.HALF_EVEN);
        this.taxaReferencia = taxaReferencia == null ? null : taxaReferencia.setScale(8, RoundingMode.HALF_EVEN);
        this.dataNegociacao = dataNegociacao;
        this.dataLiquidacao = dataLiquidacao;
        this.situacao = SituacaoOperacao.ABERTA;
        this.criadoPor = criadoPor == null ? "sistema" : criadoPor;
        this.criadoEm = Instant.now();
    }

    /** Moeda que a tesouraria recebe. */
    public Money recebe() { return lado == LadoOperacao.COMPRA ? Money.of(valorBase, moedaBase) : Money.of(valorCotado, moedaCotada); }

    /** Moeda que a tesouraria entrega. */
    public Money entrega() { return lado == LadoOperacao.COMPRA ? Money.of(valorCotado, moedaCotada) : Money.of(valorBase, moedaBase); }

    /**
     * Resultado da operação frente à taxa de referência, em moeda cotada:
     * compramos abaixo da referência ou vendemos acima = ganho.
     */
    public Money resultadoSobreReferencia() {
        if (taxaReferencia == null) return Money.zero(moedaCotada);
        BigDecimal diff = taxaReferencia.subtract(taxa).multiply(valorBase);
        if (lado == LadoOperacao.VENDA) diff = diff.negate();
        return Money.of(diff, moedaCotada);
    }

    public void liquidar(LocalDate data) {
        if (situacao != SituacaoOperacao.ABERTA) throw new DomainException("operação " + numero + " não está aberta");
        if (data.isBefore(dataLiquidacao)) throw new DomainException("operação " + numero + " só liquida em " + dataLiquidacao);
        this.situacao = SituacaoOperacao.LIQUIDADA;
        this.liquidadoEm = Instant.now();
    }

    public void cancelar() {
        if (situacao != SituacaoOperacao.ABERTA) throw new DomainException("só operações abertas podem ser canceladas");
        this.situacao = SituacaoOperacao.CANCELADA;
        this.canceladoEm = Instant.now();
    }

    public ParMoedas parMoedas() { return ParMoedas.de(par); }

    public UUID getId() { return id; }
    public String getNumero() { return numero; }
    public UUID getContraparteId() { return contraparteId; }
    public String getPar() { return par; }
    public LadoOperacao getLado() { return lado; }
    public BigDecimal getValorBase() { return valorBase; }
    public String getMoedaBase() { return moedaBase; }
    public BigDecimal getTaxa() { return taxa; }
    public BigDecimal getValorCotado() { return valorCotado; }
    public String getMoedaCotada() { return moedaCotada; }
    public BigDecimal getTaxaReferencia() { return taxaReferencia; }
    public LocalDate getDataNegociacao() { return dataNegociacao; }
    public LocalDate getDataLiquidacao() { return dataLiquidacao; }
    public SituacaoOperacao getSituacao() { return situacao; }
    public String getCriadoPor() { return criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getLiquidadoEm() { return liquidadoEm; }
    public Instant getCanceladoEm() { return canceladoEm; }
}
