package br.com.conde.tesouraria.derivativos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Registro diário de marcação a mercado de um contrato, com os insumos usados. */
@Entity
@Table(name = "marcacao_mercado")
public class MarcacaoMercado {

    @Id
    private UUID id;

    @Column(name = "contrato_id", nullable = false)
    private UUID contratoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal spot;

    @Column(name = "forward_teorico", nullable = false, precision = 19, scale = 8)
    private BigDecimal forwardTeorico;

    @Column(name = "fator_desconto", nullable = false, precision = 19, scale = 12)
    private BigDecimal fatorDesconto;

    @Column(name = "valor_presente", nullable = false, precision = 19, scale = 4)
    private BigDecimal valorPresente;

    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected MarcacaoMercado() {}

    public MarcacaoMercado(UUID contratoId, LocalDate data, BigDecimal spot, BigDecimal forwardTeorico, BigDecimal fatorDesconto, BigDecimal valorPresente) {
        this.id = UUID.randomUUID();
        this.contratoId = contratoId;
        this.data = data;
        this.spot = spot;
        this.forwardTeorico = forwardTeorico;
        this.fatorDesconto = fatorDesconto;
        this.valorPresente = valorPresente;
        this.criadoEm = Instant.now();
    }

    public void vincularLancamento(UUID lancamentoId) { this.lancamentoId = lancamentoId; }

    public UUID getId() { return id; }
    public UUID getContratoId() { return contratoId; }
    public LocalDate getData() { return data; }
    public BigDecimal getSpot() { return spot; }
    public BigDecimal getForwardTeorico() { return forwardTeorico; }
    public BigDecimal getFatorDesconto() { return fatorDesconto; }
    public BigDecimal getValorPresente() { return valorPresente; }
    public UUID getLancamentoId() { return lancamentoId; }
    public Instant getCriadoEm() { return criadoEm; }
}
