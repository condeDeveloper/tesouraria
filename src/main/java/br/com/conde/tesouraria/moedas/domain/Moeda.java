package br.com.conde.tesouraria.moedas.domain;

import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "moeda")
public class Moeda {

    @Id
    @Column(length = 3)
    private String codigo;

    @Column(nullable = false, length = 60)
    private String nome;

    @Column(name = "casas_decimais", nullable = false)
    private int casasDecimais;

    @Column(nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Moeda() {}

    public Moeda(String codigo, String nome, int casasDecimais) {
        this.codigo = CodigoMoeda.normalizar(codigo);
        renomear(nome);
        if (casasDecimais < 0 || casasDecimais > 6) throw new DomainException("casas decimais fora do intervalo 0..6");
        this.casasDecimais = casasDecimais;
        this.ativa = true;
        this.criadoEm = Instant.now();
    }

    public void renomear(String nome) {
        if (nome == null || nome.isBlank()) throw new DomainException("nome da moeda obrigatório");
        this.nome = nome.trim();
    }

    public void ativar() { this.ativa = true; }
    public void desativar() { this.ativa = false; }

    public String getCodigo() { return codigo; }
    public String getNome() { return nome; }
    public int getCasasDecimais() { return casasDecimais; }
    public boolean isAtiva() { return ativa; }
    public Instant getCriadoEm() { return criadoEm; }
}
