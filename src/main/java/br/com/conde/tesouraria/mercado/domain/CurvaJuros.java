package br.com.conde.tesouraria.mercado.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Curva de juros em uma data de referência. Interpola linearmente a taxa entre vértices
 * e extrapola flat fora deles. Devolve fatores de capitalização e desconto.
 */
@Entity
@Table(name = "curva_juros")
public class CurvaJuros {

    @Id
    private UUID id;

    @Column(nullable = false, length = 20)
    private String nome;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private ConvencaoTaxa convencao;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @OneToMany(mappedBy = "curva", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("prazoDias")
    private List<VerticeCurva> vertices = new ArrayList<>();

    protected CurvaJuros() {}

    public CurvaJuros(String nome, LocalDate dataReferencia, ConvencaoTaxa convencao, Map<Integer, BigDecimal> pontos) {
        if (nome == null || !nome.matches("^[A-Z][A-Z0-9_]{1,19}$")) throw new DomainException("nome de curva inválido: " + nome);
        if (dataReferencia == null) throw new DomainException("data de referência obrigatória");
        if (convencao == null) throw new DomainException("convenção obrigatória");
        if (pontos == null || pontos.size() < 2) throw new DomainException("curva precisa de pelo menos dois vértices");
        this.id = UUID.randomUUID();
        this.nome = nome;
        this.dataReferencia = dataReferencia;
        this.convencao = convencao;
        this.criadoEm = Instant.now();
        new TreeMap<>(pontos).forEach((prazo, taxa) -> vertices.add(new VerticeCurva(this, prazo, taxa)));
    }

    /** Taxa anual interpolada linearmente para o prazo; flat antes do primeiro e depois do último vértice. */
    public BigDecimal taxaPara(int prazoDias) {
        if (prazoDias <= 0) throw new DomainException("prazo deve ser positivo");
        List<VerticeCurva> vs = verticesOrdenados();
        if (prazoDias <= vs.get(0).getPrazoDias()) return vs.get(0).getTaxa();
        VerticeCurva ultimo = vs.get(vs.size() - 1);
        if (prazoDias >= ultimo.getPrazoDias()) return ultimo.getTaxa();
        for (int i = 1; i < vs.size(); i++) {
            VerticeCurva a = vs.get(i - 1), b = vs.get(i);
            if (prazoDias <= b.getPrazoDias()) {
                BigDecimal peso = BigDecimal.valueOf(prazoDias - a.getPrazoDias())
                        .divide(BigDecimal.valueOf(b.getPrazoDias() - a.getPrazoDias()), 12, RoundingMode.HALF_EVEN);
                return a.getTaxa().add(b.getTaxa().subtract(a.getTaxa()).multiply(peso)).setScale(8, RoundingMode.HALF_EVEN);
            }
        }
        return ultimo.getTaxa();
    }

    public BigDecimal fator(int prazoDias) { return convencao.fator(taxaPara(prazoDias), prazoDias); }

    public BigDecimal fatorDesconto(int prazoDias) { return convencao.fatorDesconto(taxaPara(prazoDias), prazoDias); }

    private List<VerticeCurva> verticesOrdenados() {
        List<VerticeCurva> vs = new ArrayList<>(vertices);
        vs.sort(Comparator.comparingInt(VerticeCurva::getPrazoDias));
        return vs;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public LocalDate getDataReferencia() { return dataReferencia; }
    public ConvencaoTaxa getConvencao() { return convencao; }
    public Instant getCriadoEm() { return criadoEm; }
    public List<VerticeCurva> getVertices() { return Collections.unmodifiableList(verticesOrdenados()); }
}
