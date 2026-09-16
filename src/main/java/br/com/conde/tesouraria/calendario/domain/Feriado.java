package br.com.conde.tesouraria.calendario.domain;

import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** Feriado de uma praça. A praça é identificada pela moeda (BRL = Brasil, USD = Nova York, EUR = TARGET). */
@Entity
@Table(name = "feriado")
public class Feriado {

    @Id
    private UUID id;

    @Column(nullable = false, length = 3)
    private String praca;

    @Column(nullable = false)
    private LocalDate data;

    @Column(nullable = false, length = 80)
    private String descricao;

    protected Feriado() {}

    public Feriado(String praca, LocalDate data, String descricao) {
        if (data == null) throw new DomainException("data do feriado obrigatória");
        if (descricao == null || descricao.isBlank()) throw new DomainException("descrição do feriado obrigatória");
        this.id = UUID.randomUUID();
        this.praca = CodigoMoeda.normalizar(praca);
        this.data = data;
        this.descricao = descricao.trim();
    }

    public UUID getId() { return id; }
    public String getPraca() { return praca; }
    public LocalDate getData() { return data; }
    public String getDescricao() { return descricao; }
}
