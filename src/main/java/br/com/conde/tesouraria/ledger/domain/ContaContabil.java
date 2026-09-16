package br.com.conde.tesouraria.ledger.domain;

import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** Conta do plano de contas. Cada conta tem uma única moeda; não há conta multimoeda. */
@Entity
@Table(name = "conta_contabil")
public class ContaContabil {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 120)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoConta tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private NaturezaConta natureza;

    @Column(nullable = false, length = 3)
    private String moeda;

    @Column(name = "contraparte_id")
    private UUID contraparteId;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected ContaContabil() {}

    public ContaContabil(String codigo, String nome, TipoConta tipo, String moeda, UUID contraparteId) {
        if (codigo == null || !codigo.matches("^[0-9A-Z][0-9A-Za-z.\\-]{1,19}$")) throw new DomainException("código de conta inválido: " + codigo);
        if (nome == null || nome.isBlank()) throw new DomainException("nome da conta obrigatório");
        if (tipo == null) throw new DomainException("tipo da conta obrigatório");
        this.id = UUID.randomUUID();
        this.codigo = codigo;
        this.nome = nome.trim();
        this.tipo = tipo;
        this.natureza = tipo.naturezaPadrao();
        this.moeda = CodigoMoeda.normalizar(moeda);
        this.contraparteId = contraparteId;
        this.ativa = true;
        this.criadoEm = Instant.now();
    }

    public void desativar() { this.ativa = false; }
    public void ativar() { this.ativa = true; }

    public void exigirAtiva() {
        if (!ativa) throw new DomainException("conta inativa: " + codigo);
    }

    public UUID getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public TipoConta getTipo() { return tipo; }
    public NaturezaConta getNatureza() { return natureza; }
    public String getMoeda() { return moeda; }
    public UUID getContraparteId() { return contraparteId; }
    public boolean isAtiva() { return ativa; }
    public Instant getCriadoEm() { return criadoEm; }
}
