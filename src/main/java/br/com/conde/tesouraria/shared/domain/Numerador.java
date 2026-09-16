package br.com.conde.tesouraria.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Contador por prefixo para numerar operações de forma legível (CAM-2026-000001). */
@Entity
@Table(name = "numerador")
public class Numerador {

    @Id
    @Column(length = 20)
    private String chave;

    @Column(nullable = false)
    private long ultimo;

    protected Numerador() {}

    public Numerador(String chave) { this.chave = chave; this.ultimo = 0; }

    public long proximo() { return ++ultimo; }

    public String getChave() { return chave; }
    public long getUltimo() { return ultimo; }
}
