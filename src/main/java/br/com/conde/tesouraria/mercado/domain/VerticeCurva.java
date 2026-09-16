package br.com.conde.tesouraria.mercado.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Um ponto da curva: prazo em dias (úteis ou corridos conforme a convenção) e taxa anual. */
@Entity
@Table(name = "vertice_curva")
public class VerticeCurva {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "curva_id")
    private CurvaJuros curva;

    @Column(name = "prazo_dias", nullable = false)
    private int prazoDias;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal taxa;

    protected VerticeCurva() {}

    VerticeCurva(CurvaJuros curva, int prazoDias, BigDecimal taxa) {
        if (prazoDias <= 0) throw new DomainException("prazo do vértice deve ser positivo");
        if (taxa == null || taxa.compareTo(new BigDecimal("-0.05")) < 0 || taxa.compareTo(new BigDecimal("2")) > 0) {
            throw new DomainException("taxa do vértice fora do intervalo plausível: " + taxa);
        }
        this.id = UUID.randomUUID();
        this.curva = curva;
        this.prazoDias = prazoDias;
        this.taxa = taxa.setScale(8, java.math.RoundingMode.HALF_EVEN);
    }

    public int getPrazoDias() { return prazoDias; }
    public BigDecimal getTaxa() { return taxa; }
}
