package br.com.conde.tesouraria.derivativos.domain;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Marcação diária de uma opção: valor da posição e gregas já multiplicadas pelo notional e pelo sinal da posição. */
@Entity
@Table(name = "marcacao_opcao")
public class MarcacaoOpcao {

    @Id
    private UUID id;

    @Column(name = "opcao_id", nullable = false)
    private UUID opcaoId;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal spot;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal forward;

    @Column(nullable = false, precision = 12, scale = 8)
    private BigDecimal volatilidade;

    @Column(name = "prazo_anos", nullable = false, precision = 12, scale = 8)
    private BigDecimal prazoAnos;

    @Column(name = "valor_unitario", nullable = false, precision = 19, scale = 8)
    private BigDecimal valorUnitario;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valor;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal delta;

    @Column(nullable = false, precision = 19, scale = 10)
    private BigDecimal gamma;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal vega;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal theta;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal rho;

    @Column(name = "lancamento_id")
    private UUID lancamentoId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected MarcacaoOpcao() {}

    public MarcacaoOpcao(UUID opcaoId, LocalDate data, BigDecimal spot, BigDecimal forward, BigDecimal volatilidade, BigDecimal prazoAnos,
                         BigDecimal valorUnitario, BigDecimal valor, BigDecimal delta, BigDecimal gamma, BigDecimal vega, BigDecimal theta, BigDecimal rho) {
        this.id = UUID.randomUUID();
        this.opcaoId = opcaoId; this.data = data; this.spot = spot; this.forward = forward; this.volatilidade = volatilidade;
        this.prazoAnos = prazoAnos; this.valorUnitario = valorUnitario; this.valor = valor;
        this.delta = delta; this.gamma = gamma; this.vega = vega; this.theta = theta; this.rho = rho;
        this.criadoEm = Instant.now();
    }

    public void vincularLancamento(UUID id) { this.lancamentoId = id; }

    public UUID getId() { return id; }
    public UUID getOpcaoId() { return opcaoId; }
    public LocalDate getData() { return data; }
    public BigDecimal getSpot() { return spot; }
    public BigDecimal getForward() { return forward; }
    public BigDecimal getVolatilidade() { return volatilidade; }
    public BigDecimal getPrazoAnos() { return prazoAnos; }
    public BigDecimal getValorUnitario() { return valorUnitario; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getDelta() { return delta; }
    public BigDecimal getGamma() { return gamma; }
    public BigDecimal getVega() { return vega; }
    public BigDecimal getTheta() { return theta; }
    public BigDecimal getRho() { return rho; }
    public UUID getLancamentoId() { return lancamentoId; }
}
