package br.com.conde.tesouraria.calendario.web;

import br.com.conde.tesouraria.calendario.application.CalendarioService;
import br.com.conde.tesouraria.calendario.domain.Calendario;
import br.com.conde.tesouraria.calendario.domain.Feriado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/calendario")
@Tag(name = "Calendário", description = "Feriados por praça e cálculo de dias úteis")
public class CalendarioController {

    private final CalendarioService service;

    public CalendarioController(CalendarioService service) {
        this.service = service;
    }

    public record NovoFeriado(@NotBlank String praca, @NotNull LocalDate data, @NotBlank String descricao) {}

    public record FeriadoResposta(UUID id, String praca, LocalDate data, String descricao) {
        static FeriadoResposta de(Feriado f) { return new FeriadoResposta(f.getId(), f.getPraca(), f.getData(), f.getDescricao()); }
    }

    public record DiaUtilResposta(LocalDate data, boolean diaUtil, LocalDate proximoDiaUtil, LocalDate diaUtilAnterior) {}

    public record SomaResposta(LocalDate data, int dias, LocalDate resultado) {}

    @GetMapping("/{praca}/feriados")
    public List<FeriadoResposta> feriados(@PathVariable String praca,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.listar(praca, inicio, fim).stream().map(FeriadoResposta::de).toList();
    }

    @PostMapping("/feriados")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public FeriadoResposta cadastrar(@Valid @RequestBody NovoFeriado corpo) {
        return FeriadoResposta.de(service.cadastrar(corpo.praca(), corpo.data(), corpo.descricao()));
    }

    @DeleteMapping("/feriados/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void remover(@PathVariable UUID id) {
        service.remover(id);
    }

    @GetMapping("/dia-util")
    @Operation(summary = "Verifica se a data é dia útil nas praças informadas (separadas por vírgula)")
    public DiaUtilResposta diaUtil(@RequestParam String pracas,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        Calendario c = service.calendario(pracas.split(","));
        return new DiaUtilResposta(data, c.ehDiaUtil(data), c.proximoDiaUtil(data), c.diaUtilAnterior(data));
    }

    @GetMapping("/somar")
    @Operation(summary = "Soma dias úteis a uma data nas praças informadas")
    public SomaResposta somar(@RequestParam String pracas,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                              @RequestParam int dias) {
        Calendario c = service.calendario(pracas.split(","));
        return new SomaResposta(data, dias, c.somarDiasUteis(data, dias));
    }
}
