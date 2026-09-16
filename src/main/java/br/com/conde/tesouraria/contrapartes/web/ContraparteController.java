package br.com.conde.tesouraria.contrapartes.web;

import br.com.conde.tesouraria.contrapartes.application.ContraparteService;
import br.com.conde.tesouraria.contrapartes.domain.Contraparte;
import br.com.conde.tesouraria.contrapartes.domain.SituacaoContraparte;
import br.com.conde.tesouraria.contrapartes.domain.TipoContraparte;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/contrapartes")
@Tag(name = "Contrapartes", description = "Clientes e instituições com quem a tesouraria opera")
public class ContraparteController {

    private final ContraparteService service;

    public ContraparteController(ContraparteService service) {
        this.service = service;
    }

    public record NovaContraparte(@NotBlank String nome, @NotBlank String documento, boolean instituicaoFinanceira, @Email String email) {}

    public record AtualizarContraparte(@NotBlank String nome, @Email String email) {}

    public record ContraparteResposta(UUID id, String nome, String documento, TipoContraparte tipo,
                                      SituacaoContraparte situacao, String email, Instant criadoEm) {
        static ContraparteResposta de(Contraparte c) {
            return new ContraparteResposta(c.getId(), c.getNome(), c.getDocumentoFormatado(), c.getTipo(),
                    c.getSituacao(), c.getEmail(), c.getCriadoEm());
        }
    }

    @GetMapping
    public Page<ContraparteResposta> listar(@RequestParam(required = false) String nome,
                                            @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(nome, pageable).map(ContraparteResposta::de);
    }

    @GetMapping("/{id}")
    public ContraparteResposta buscar(@PathVariable UUID id) {
        return ContraparteResposta.de(service.buscar(id));
    }

    @GetMapping("/documento/{documento}")
    public ContraparteResposta porDocumento(@PathVariable String documento) {
        return ContraparteResposta.de(service.buscarPorDocumento(documento));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ContraparteResposta cadastrar(@Valid @RequestBody NovaContraparte corpo) {
        return ContraparteResposta.de(service.cadastrar(corpo.nome(), corpo.documento(), corpo.instituicaoFinanceira(), corpo.email()));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ContraparteResposta atualizar(@PathVariable UUID id, @Valid @RequestBody AtualizarContraparte corpo) {
        return ContraparteResposta.de(service.atualizar(id, corpo.nome(), corpo.email()));
    }

    @PostMapping("/{id}/bloquear")
    @PreAuthorize("hasRole('ADMIN')")
    public ContraparteResposta bloquear(@PathVariable UUID id) { return ContraparteResposta.de(service.bloquear(id)); }

    @PostMapping("/{id}/reativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ContraparteResposta reativar(@PathVariable UUID id) { return ContraparteResposta.de(service.reativar(id)); }

    @PostMapping("/{id}/inativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ContraparteResposta inativar(@PathVariable UUID id) { return ContraparteResposta.de(service.inativar(id)); }
}
