package br.com.conde.tesouraria.contrapartes.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "contraparte")
public class Contraparte {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 14)
    private String documento;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoContraparte tipo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SituacaoContraparte situacao;

    @Column(length = 120)
    private String email;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    protected Contraparte() {}

    public Contraparte(String nome, String documento, boolean instituicaoFinanceira, String email) {
        this.id = UUID.randomUUID();
        this.documento = Documento.normalizar(documento);
        TipoContraparte base = Documento.tipoPara(this.documento);
        if (instituicaoFinanceira && base == TipoContraparte.PF) throw new DomainException("instituição financeira precisa de CNPJ");
        this.tipo = instituicaoFinanceira ? TipoContraparte.INSTITUICAO : base;
        this.situacao = SituacaoContraparte.ATIVA;
        this.criadoEm = Instant.now();
        atualizar(nome, email);
    }

    public void atualizar(String nome, String email) {
        if (nome == null || nome.isBlank()) throw new DomainException("nome da contraparte obrigatório");
        this.nome = nome.trim();
        this.email = email == null || email.isBlank() ? null : email.trim().toLowerCase();
        this.atualizadoEm = Instant.now();
    }

    public void bloquear() { mudarSituacao(SituacaoContraparte.BLOQUEADA); }
    public void reativar() { mudarSituacao(SituacaoContraparte.ATIVA); }
    public void inativar() { mudarSituacao(SituacaoContraparte.INATIVA); }

    private void mudarSituacao(SituacaoContraparte nova) {
        if (situacao == SituacaoContraparte.INATIVA) throw new DomainException("contraparte inativa não pode mudar de situação");
        this.situacao = nova;
        this.atualizadoEm = Instant.now();
    }

    /** Lança exceção se a contraparte não puder fechar novas operações. */
    public void exigirApta() {
        if (situacao != SituacaoContraparte.ATIVA) throw new DomainException("contraparte " + situacao.name().toLowerCase() + ": " + nome);
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getDocumento() { return documento; }
    public String getDocumentoFormatado() { return Documento.formatar(documento); }
    public TipoContraparte getTipo() { return tipo; }
    public SituacaoContraparte getSituacao() { return situacao; }
    public String getEmail() { return email; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
}
