package br.com.conde.tesouraria.moedas.web;

import br.com.conde.tesouraria.moedas.application.MoedaService;
import br.com.conde.tesouraria.moedas.domain.Moeda;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/moedas")
@Tag(name = "Moedas", description = "Cadastro de moedas ISO 4217")
public class MoedaController {

    private final MoedaService service;

    public MoedaController(MoedaService service) {
        this.service = service;
    }

    public record NovaMoeda(
            @NotBlank @Pattern(regexp = "^[A-Za-z]{3}$", message = "código deve ter 3 letras") String codigo,
            @NotBlank String nome,
            @Min(0) @Max(6) int casasDecimais) {}

    public record RenomearMoeda(@NotBlank String nome) {}

    public record MoedaResposta(String codigo, String nome, int casasDecimais, boolean ativa) {
        static MoedaResposta de(Moeda m) { return new MoedaResposta(m.getCodigo(), m.getNome(), m.getCasasDecimais(), m.isAtiva()); }
    }

    @GetMapping
    @Operation(summary = "Lista moedas", description = "Por padrão só as ativas; use ?todas=true para incluir inativas")
    public List<MoedaResposta> listar(@RequestParam(defaultValue = "false") boolean todas) {
        return service.listar(!todas).stream().map(MoedaResposta::de).toList();
    }

    @GetMapping("/{codigo}")
    public MoedaResposta buscar(@PathVariable String codigo) {
        return MoedaResposta.de(service.buscar(codigo));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public MoedaResposta cadastrar(@Valid @RequestBody NovaMoeda corpo) {
        return MoedaResposta.de(service.cadastrar(corpo.codigo(), corpo.nome(), corpo.casasDecimais()));
    }

    @PatchMapping("/{codigo}")
    @PreAuthorize("hasRole('ADMIN')")
    public MoedaResposta renomear(@PathVariable String codigo, @Valid @RequestBody RenomearMoeda corpo) {
        return MoedaResposta.de(service.renomear(codigo, corpo.nome()));
    }

    @PostMapping("/{codigo}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public MoedaResposta ativar(@PathVariable String codigo) {
        return MoedaResposta.de(service.alterarSituacao(codigo, true));
    }

    @PostMapping("/{codigo}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public MoedaResposta desativar(@PathVariable String codigo) {
        return MoedaResposta.de(service.alterarSituacao(codigo, false));
    }
}
