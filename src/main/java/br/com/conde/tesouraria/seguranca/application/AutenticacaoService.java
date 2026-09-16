package br.com.conde.tesouraria.seguranca.application;

import br.com.conde.tesouraria.seguranca.domain.Papel;
import br.com.conde.tesouraria.seguranca.domain.Usuario;
import br.com.conde.tesouraria.seguranca.domain.UsuarioRepository;
import br.com.conde.tesouraria.seguranca.infra.JwtService;
import br.com.conde.tesouraria.shared.domain.ConflitoException;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class AutenticacaoService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AutenticacaoService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    public record Sessao(String token, java.time.Instant expiraEm, String login, String nome, Papel papel) {}

    @Transactional(readOnly = true)
    public Sessao autenticar(String login, String senha) {
        Usuario u = usuarios.findByLogin(login == null ? "" : login.trim().toLowerCase())
                .filter(Usuario::isAtivo)
                .filter(x -> encoder.matches(senha, x.getSenhaHash()))
                .orElseThrow(() -> new BadCredentialsException("login ou senha inválidos"));
        JwtService.Token t = jwt.emitir(u);
        return new Sessao(t.valor(), t.expiraEm(), u.getLogin(), u.getNome(), u.getPapel());
    }

    public Usuario criarUsuario(String login, String nome, String senha, Papel papel) {
        validarSenha(senha);
        String l = login == null ? "" : login.trim().toLowerCase();
        if (usuarios.existsByLogin(l)) throw new ConflitoException("login já em uso: " + l);
        return usuarios.save(new Usuario(l, nome, encoder.encode(senha), papel));
    }

    public void trocarSenha(String login, String senhaAtual, String novaSenha) {
        Usuario u = usuarios.findByLogin(login).orElseThrow(() -> new NaoEncontradoException("usuário", login));
        if (!encoder.matches(senhaAtual, u.getSenhaHash())) throw new BadCredentialsException("senha atual incorreta");
        validarSenha(novaSenha);
        u.trocarSenha(encoder.encode(novaSenha));
    }

    public Usuario alterarPapel(UUID id, Papel papel) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("usuário", id));
        u.alterarPapel(papel);
        return u;
    }

    public Usuario alterarSituacao(UUID id, boolean ativo) {
        Usuario u = usuarios.findById(id).orElseThrow(() -> new NaoEncontradoException("usuário", id));
        if (ativo) u.ativar(); else u.desativar();
        return u;
    }

    @Transactional(readOnly = true)
    public List<Usuario> listar() { return usuarios.findAll(); }

    @Transactional(readOnly = true)
    public boolean semUsuarios() { return usuarios.count() == 0; }

    private static void validarSenha(String senha) {
        if (senha == null || senha.length() < 8) throw new DomainException("senha deve ter pelo menos 8 caracteres");
    }
}
