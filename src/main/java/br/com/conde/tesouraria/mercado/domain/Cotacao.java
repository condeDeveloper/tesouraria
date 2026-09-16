package br.com.conde.tesouraria.mercado.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "cotacao")
public class Cotacao {

    @Id
    private UUID id;

    @Column(nullable = false, length = 6)
    private String par;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private TipoCotacao tipo;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal taxa;

    @Column(nullable = false, length = 40)
    private String fonte;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Cotacao() {}

    public Cotacao(ParMoedas par, TipoCotacao tipo, LocalDate data, BigDecimal taxa, String fonte) {
        if (tipo == null) throw new DomainException("tipo da cotação obrigatório");
        if (data == null) throw new DomainException("data da cotação obrigatória");
        if (taxa == null || taxa.signum() <= 0) throw new DomainException("taxa deve ser positiva");
        if (fonte == null || fonte.isBlank()) throw new DomainException("fonte da cotação obrigatória");
        this.id = UUID.randomUUID();
        this.par = par.codigo();
        this.tipo = tipo;
        this.data = data;
        this.taxa = taxa.setScale(8, java.math.RoundingMode.HALF_EVEN);
        this.fonte = fonte.trim();
        this.criadoEm = Instant.now();
    }

    public void atualizar(BigDecimal taxa, String fonte) {
        if (taxa == null || taxa.signum() <= 0) throw new DomainException("taxa deve ser positiva");
        this.taxa = taxa.setScale(8, java.math.RoundingMode.HALF_EVEN);
        if (fonte != null && !fonte.isBlank()) this.fonte = fonte.trim();
    }

    public ParMoedas parMoedas() { return ParMoedas.de(par); }

    /** Taxa do par invertido (1/taxa) com 8 casas. */
    public BigDecimal taxaInversa() { return BigDecimal.ONE.divide(taxa, 8, java.math.RoundingMode.HALF_EVEN); }

    public UUID getId() { return id; }
    public String getPar() { return par; }
    public TipoCotacao getTipo() { return tipo; }
    public LocalDate getData() { return data; }
    public BigDecimal getTaxa() { return taxa; }
    public String getFonte() { return fonte; }
    public Instant getCriadoEm() { return criadoEm; }
}
