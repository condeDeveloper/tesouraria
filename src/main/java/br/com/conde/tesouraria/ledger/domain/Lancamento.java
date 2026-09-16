package br.com.conde.tesouraria.ledger.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.Money;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Lançamento contábil: agregado com duas ou mais partidas que, por moeda, somam
 * débitos iguais a créditos. Imutável depois de criado; correções são por estorno.
 */
@Entity
@Table(name = "lancamento")
public class Lancamento {

    @Id
    private UUID id;

    @Column(name = "chave_idempotencia", nullable = false, length = 80)
    private String chaveIdempotencia;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    @Column(nullable = false, length = 200)
    private String descricao;

    @Column(nullable = false, length = 30)
    private String origem;

    @Column(name = "referencia_id")
    private UUID referenciaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "criado_por", nullable = false, length = 40)
    private String criadoPor;

    @Column(name = "estornado_por")
    private UUID estornadoPor;

    @OneToMany(mappedBy = "lancamento", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("ordem")
    private List<Partida> partidas = new ArrayList<>();

    protected Lancamento() {}

    private Lancamento(String chave, LocalDate data, String descricao, String origem, UUID referenciaId, String criadoPor) {
        if (chave == null || chave.isBlank() || chave.length() > 80) throw new DomainException("chave de idempotência obrigatória (até 80 caracteres)");
        if (data == null) throw new DomainException("data do lançamento obrigatória");
        if (descricao == null || descricao.isBlank()) throw new DomainException("descrição obrigatória");
        if (origem == null || origem.isBlank()) throw new DomainException("origem obrigatória");
        this.id = UUID.randomUUID();
        this.chaveIdempotencia = chave.trim();
        this.dataLancamento = data;
        this.descricao = descricao.trim();
        this.origem = origem.trim().toUpperCase();
        this.referenciaId = referenciaId;
        this.criadoEm = Instant.now();
        this.criadoPor = criadoPor == null ? "sistema" : criadoPor;
    }

    /** Ponto de entrada para montar um lançamento válido. */
    public static Builder novo(String chave, LocalDate data, String descricao, String origem) {
        return new Builder(chave, data, descricao, origem);
    }

    public static final class Builder {
        private final String chave, descricao, origem;
        private final LocalDate data;
        private UUID referenciaId;
        private String criadoPor;
        private final List<Object[]> linhas = new ArrayList<>();

        private Builder(String chave, LocalDate data, String descricao, String origem) {
            this.chave = chave; this.data = data; this.descricao = descricao; this.origem = origem;
        }

        public Builder referencia(UUID id) { this.referenciaId = id; return this; }
        public Builder criadoPor(String login) { this.criadoPor = login; return this; }
        public Builder debito(ContaContabil conta, Money valor) { linhas.add(new Object[]{conta, TipoPartida.DEBITO, valor}); return this; }
        public Builder credito(ContaContabil conta, Money valor) { linhas.add(new Object[]{conta, TipoPartida.CREDITO, valor}); return this; }

        public Lancamento build() {
            Lancamento l = new Lancamento(chave, data, descricao, origem, referenciaId, criadoPor);
            int ordem = 0;
            for (Object[] x : linhas) l.partidas.add(new Partida(l, (ContaContabil) x[0], (TipoPartida) x[1], (Money) x[2], ordem++));
            l.validar();
            return l;
        }
    }

    private void validar() {
        if (partidas.size() < 2) throw new DomainException("lançamento precisa de pelo menos duas partidas");
        Map<String, BigDecimal> saldoPorMoeda = new TreeMap<>();
        for (Partida p : partidas) saldoPorMoeda.merge(p.getMoeda(), p.debitoLiquido(), BigDecimal::add);
        for (var e : saldoPorMoeda.entrySet()) {
            if (e.getValue().signum() != 0) {
                throw new DomainException("lançamento desbalanceado em " + e.getKey() + ": diferença de " + e.getValue().toPlainString());
            }
        }
    }

    /** Cria o lançamento inverso (débitos virando créditos e vice-versa) e marca este como estornado. */
    public Lancamento estornar(String chave, LocalDate data, String criadoPor) {
        if (estornadoPor != null) throw new DomainException("lançamento já estornado");
        Builder b = novo(chave, data, "Estorno: " + descricao, origem).referencia(referenciaId).criadoPor(criadoPor);
        for (Partida p : partidas) {
            if (p.getTipo() == TipoPartida.DEBITO) b.credito(p.getConta(), p.money()); else b.debito(p.getConta(), p.money());
        }
        Lancamento estorno = b.build();
        this.estornadoPor = estorno.id;
        return estorno;
    }

    public Set<String> moedas() {
        Set<String> s = new TreeSet<>();
        partidas.forEach(p -> s.add(p.getMoeda()));
        return s;
    }

    public UUID getId() { return id; }
    public String getChaveIdempotencia() { return chaveIdempotencia; }
    public LocalDate getDataLancamento() { return dataLancamento; }
    public String getDescricao() { return descricao; }
    public String getOrigem() { return origem; }
    public UUID getReferenciaId() { return referenciaId; }
    public Instant getCriadoEm() { return criadoEm; }
    public String getCriadoPor() { return criadoPor; }
    public UUID getEstornadoPor() { return estornadoPor; }
    public boolean isEstornado() { return estornadoPor != null; }
    public List<Partida> getPartidas() { return Collections.unmodifiableList(partidas); }
}
