package br.com.conde.tesouraria.seguranca.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    private UUID id;

    @Column(nullable = false, length = 40)
    private String login;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Papel papel;

    @Column(nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    protected Usuario() {}

    public Usuario(String login, String nome, String senhaHash, Papel papel) {
        if (login == null || !login.matches("^[a-z0-9._-]{3,40}$")) throw new DomainException("login inválido (3 a 40 caracteres: letras minúsculas, números, . _ -)");
        if (nome == null || nome.isBlank()) throw new DomainException("nome obrigatório");
        if (senhaHash == null || senhaHash.isBlank()) throw new DomainException("senha obrigatória");
        this.id = UUID.randomUUID();
        this.login = login;
        this.nome = nome.trim();
        this.senhaHash = senhaHash;
        this.papel = papel;
        this.ativo = true;
        this.criadoEm = Instant.now();
    }

    public void trocarSenha(String novoHash) { this.senhaHash = novoHash; }
    public void alterarPapel(Papel novo) { this.papel = novo; }
    public void desativar() { this.ativo = false; }
    public void ativar() { this.ativo = true; }

    public UUID getId() { return id; }
    public String getLogin() { return login; }
    public String getNome() { return nome; }
    public String getSenhaHash() { return senhaHash; }
    public Papel getPapel() { return papel; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
}
