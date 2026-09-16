package br.com.conde.tesouraria.seguranca.web;

import br.com.conde.tesouraria.seguranca.application.AutenticacaoService;
import br.com.conde.tesouraria.seguranca.domain.Papel;
import br.com.conde.tesouraria.seguranca.domain.Usuario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Login com JWT e gestão de usuários")
public class AutenticacaoController {

    private final AutenticacaoService service;

    public AutenticacaoController(AutenticacaoService service) {
        this.service = service;
    }

    public record Login(@NotBlank String login, @NotBlank String senha) {}
    public record NovoUsuario(@NotBlank String login, @NotBlank String nome, @NotBlank String senha, @NotNull Papel papel) {}
    public record TrocaSenha(@NotBlank String senhaAtual, @NotBlank String novaSenha) {}
    public record AlterarPapel(@NotNull Papel papel) {}

    public record UsuarioResposta(UUID id, String login, String nome, Papel papel, boolean ativo, Instant criadoEm) {
        static UsuarioResposta de(Usuario u) { return new UsuarioResposta(u.getId(), u.getLogin(), u.getNome(), u.getPapel(), u.isAtivo(), u.getCriadoEm()); }
    }

    @PostMapping("/login")
    @Operation(summary = "Autentica e devolve um JWT", description = "Use o token no header Authorization: Bearer <token>")
    public AutenticacaoService.Sessao login(@Valid @RequestBody Login corpo) {
        return service.autenticar(corpo.login(), corpo.senha());
    }

    @GetMapping("/eu")
    public Object eu(Authentication auth) {
        return new Object() {
            public final String login = auth.getName();
            public final List<String> autoridades = auth.getAuthorities().stream().map(Object::toString).toList();
        };
    }

    @PostMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void trocarSenha(Authentication auth, @Valid @RequestBody TrocaSenha corpo) {
        service.trocarSenha(auth.getName(), corpo.senhaAtual(), corpo.novaSenha());
    }

    @GetMapping("/usuarios")
    @PreAuthorize("hasRole('ADMIN')")
    public List<UsuarioResposta> usuarios() {
        return service.listar().stream().map(UsuarioResposta::de).toList();
    }

    @PostMapping("/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResposta criar(@Valid @RequestBody NovoUsuario corpo) {
        return UsuarioResposta.de(service.criarUsuario(corpo.login(), corpo.nome(), corpo.senha(), corpo.papel()));
    }

    @PatchMapping("/usuarios/{id}/papel")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResposta papel(@PathVariable UUID id, @Valid @RequestBody AlterarPapel corpo) {
        return UsuarioResposta.de(service.alterarPapel(id, corpo.papel()));
    }

    @PostMapping("/usuarios/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResposta desativar(@PathVariable UUID id) { return UsuarioResposta.de(service.alterarSituacao(id, false)); }

    @PostMapping("/usuarios/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public UsuarioResposta ativar(@PathVariable UUID id) { return UsuarioResposta.de(service.alterarSituacao(id, true)); }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail credenciais(BadCredentialsException e) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        p.setTitle("Não autenticado");
        return p;
    }
}
