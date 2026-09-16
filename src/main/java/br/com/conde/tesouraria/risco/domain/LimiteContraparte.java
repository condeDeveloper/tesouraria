package br.com.conde.tesouraria.risco.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Limite de exposição em BRL por contraparte, somando câmbio a liquidar, NDFs e opções em aberto. */
@Entity
@Table(name = "limite_contraparte")
public class LimiteContraparte {

    @Id
    @Column(name = "contraparte_id")
    private UUID contraparteId;

    @Column(name = "limite_brl", nullable = false, precision = 19, scale = 2)
    private BigDecimal limiteBrl;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "atualizado_por", nullable = false, length = 40)
    private String atualizadoPor;

    protected LimiteContraparte() {}

    public LimiteContraparte(UUID contraparteId, BigDecimal limiteBrl, String por) {
        this.contraparteId = contraparteId;
        definir(limiteBrl, por);
    }

    public void definir(BigDecimal limiteBrl, String por) {
        if (limiteBrl == null || limiteBrl.signum() < 0) throw new DomainException("limite deve ser zero ou positivo");
        this.limiteBrl = limiteBrl.setScale(2, java.math.RoundingMode.HALF_EVEN);
        this.atualizadoEm = Instant.now();
        this.atualizadoPor = por == null ? "sistema" : por;
    }

    public UUID getContraparteId() { return contraparteId; }
    public BigDecimal getLimiteBrl() { return limiteBrl; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public String getAtualizadoPor() { return atualizadoPor; }
}
