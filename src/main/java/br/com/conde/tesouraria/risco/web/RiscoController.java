package br.com.conde.tesouraria.risco.web;

import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import br.com.conde.tesouraria.risco.application.RiscoService;
import br.com.conde.tesouraria.risco.domain.LimiteContraparte;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/risco")
@Tag(name = "Risco", description = "Exposição cambial consolidada, VaR e limites por contraparte")
public class RiscoController {

    private final RiscoService service;

    public RiscoController(RiscoService service) {
        this.service = service;
    }

    public record NovoLimite(@NotNull @PositiveOrZero BigDecimal limiteBrl) {}

    public record LimiteResposta(UUID contraparteId, BigDecimal limiteBrl, Instant atualizadoEm, String atualizadoPor) {
        static LimiteResposta de(LimiteContraparte l) { return new LimiteResposta(l.getContraparteId(), l.getLimiteBrl(), l.getAtualizadoEm(), l.getAtualizadoPor()); }
    }

    @GetMapping("/exposicao")
    @Operation(summary = "Exposição líquida por moeda: posição de câmbio + NDFs + delta das opções, em moeda e em BRL")
    public RiscoService.Exposicao exposicao(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.exposicao(data != null ? data : LocalDate.now());
    }

    @GetMapping("/var")
    @Operation(summary = "VaR histórico (PTAX) e paramétrico (curva de vol) da exposição na moeda do par")
    public RiscoService.Var var(@RequestParam(defaultValue = "USDBRL") String par,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                @RequestParam(defaultValue = "0.95") double confianca,
                                @RequestParam(defaultValue = "1") int horizonteDias,
                                @RequestParam(defaultValue = "500") int janelaDias) {
        return service.var(ParMoedas.de(par), data != null ? data : LocalDate.now(), confianca, horizonteDias, janelaDias);
    }

    @GetMapping("/limites/{contraparteId}")
    public LimiteResposta limite(@PathVariable UUID contraparteId) {
        return service.limite(contraparteId).map(LimiteResposta::de).orElseThrow(() -> new NaoEncontradoException("limite da contraparte", contraparteId));
    }

    @PutMapping("/limites/{contraparteId}")
    @PreAuthorize("hasRole('ADMIN')")
    public LimiteResposta definir(@PathVariable UUID contraparteId, @Valid @RequestBody NovoLimite corpo, Authentication auth) {
        return LimiteResposta.de(service.definirLimite(contraparteId, corpo.limiteBrl(), auth.getName()));
    }

    @GetMapping("/limites/{contraparteId}/utilizacao")
    @Operation(summary = "Quanto do limite a contraparte está usando com operações em aberto")
    public RiscoService.Utilizacao utilizacao(@PathVariable UUID contraparteId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.utilizacao(contraparteId, data != null ? data : LocalDate.now());
    }
}
