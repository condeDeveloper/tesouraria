package br.com.conde.tesouraria.ledger.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

/** Uma linha de débito ou crédito dentro de um lançamento. Sempre com valor positivo. */
@Entity
@Table(name = "partida")
public class Partida {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lancamento_id")
    private Lancamento lancamento;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "conta_id")
    private ContaContabil conta;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7)
    private TipoPartida tipo;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal valor;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(nullable = false)
    private int ordem;

    protected Partida() {}

    Partida(Lancamento lancamento, ContaContabil conta, TipoPartida tipo, Money valor, int ordem) {
        if (!valor.ehPositivo()) throw new DomainException("partida deve ter valor positivo");
        if (!conta.getMoeda().equals(valor.moeda())) {
            throw new DomainException("conta " + conta.getCodigo() + " é em " + conta.getMoeda() + ", partida em " + valor.moeda());
        }
        conta.exigirAtiva();
        this.id = UUID.randomUUID();
        this.lancamento = lancamento;
        this.conta = conta;
        this.tipo = tipo;
        this.valor = valor.quantia();
        this.moeda = valor.moeda();
        this.ordem = ordem;
    }

    public Money money() { return Money.of(valor, moeda); }

    /** Contribuição para o saldo bruto: +valor em débito, -valor em crédito (natureza aplicada depois). */
    public BigDecimal debitoLiquido() { return tipo == TipoPartida.DEBITO ? valor : valor.negate(); }

    public UUID getId() { return id; }
    public Lancamento getLancamento() { return lancamento; }
    public ContaContabil getConta() { return conta; }
    public TipoPartida getTipo() { return tipo; }
    public BigDecimal getValor() { return valor; }
    public String getMoeda() { return moeda; }
    public int getOrdem() { return ordem; }
}
