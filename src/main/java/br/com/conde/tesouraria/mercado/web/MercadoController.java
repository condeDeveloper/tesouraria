package br.com.conde.tesouraria.mercado.web;

import br.com.conde.tesouraria.mercado.application.MercadoService;
import br.com.conde.tesouraria.mercado.domain.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mercado")
@Tag(name = "Mercado", description = "Cotações spot e PTAX, curvas de juros e taxa a termo teórica")
public class MercadoController {

    private final MercadoService service;

    public MercadoController(MercadoService service) {
        this.service = service;
    }

    public record NovaCotacao(@NotBlank String par, @NotNull TipoCotacao tipo, @NotNull LocalDate data,
                              @NotNull @Positive BigDecimal taxa, @NotBlank String fonte) {}

    public record CotacaoResposta(String par, TipoCotacao tipo, LocalDate data, BigDecimal taxa, String fonte) {
        static CotacaoResposta de(Cotacao c) { return new CotacaoResposta(c.getPar(), c.getTipo(), c.getData(), c.getTaxa(), c.getFonte()); }
    }

    public record NovaCurva(@NotBlank String nome, @NotNull LocalDate dataReferencia, @NotNull ConvencaoTaxa convencao,
                            @NotEmpty @Size(min = 2) Map<@Positive Integer, @NotNull BigDecimal> vertices) {}

    public record VerticeResposta(int prazoDias, BigDecimal taxa) {}

    public record CurvaResposta(String nome, LocalDate dataReferencia, ConvencaoTaxa convencao, List<VerticeResposta> vertices) {
        static CurvaResposta de(CurvaJuros c) {
            return new CurvaResposta(c.getNome(), c.getDataReferencia(), c.getConvencao(),
                    c.getVertices().stream().map(v -> new VerticeResposta(v.getPrazoDias(), v.getTaxa())).toList());
        }
    }

    public record TaxaResposta(String par, TipoCotacao tipo, LocalDate ate, BigDecimal taxa) {}

    public record PontoCurva(String nome, LocalDate dataReferencia, int prazoDias, BigDecimal taxa, BigDecimal fator, BigDecimal fatorDesconto) {}

    @GetMapping("/cotacoes/{par}")
    public List<CotacaoResposta> historico(@PathVariable String par, @RequestParam(defaultValue = "PTAX") TipoCotacao tipo,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.historico(ParMoedas.de(par), tipo, inicio, fim).stream().map(CotacaoResposta::de).toList();
    }

    @GetMapping("/cotacoes/{par}/atual")
    @Operation(summary = "Taxa mais recente até a data, aceitando o par invertido")
    public TaxaResposta atual(@PathVariable String par, @RequestParam(defaultValue = "SPOT") TipoCotacao tipo,
                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        LocalDate d = ate != null ? ate : LocalDate.now();
        return new TaxaResposta(ParMoedas.de(par).codigo(), tipo, d, service.taxa(ParMoedas.de(par), tipo, d));
    }

    @PostMapping("/cotacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CotacaoResposta registrar(@Valid @RequestBody NovaCotacao c) {
        return CotacaoResposta.de(service.registrarCotacao(ParMoedas.de(c.par()), c.tipo(), c.data(), c.taxa(), c.fonte()));
    }

    @GetMapping("/curvas/{nome}")
    public CurvaResposta curva(@PathVariable String nome,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return CurvaResposta.de(service.curva(nome.toUpperCase(), ate != null ? ate : LocalDate.now()));
    }

    @GetMapping("/curvas/{nome}/ponto")
    @Operation(summary = "Taxa interpolada e fatores para um prazo em dias")
    public PontoCurva ponto(@PathVariable String nome, @RequestParam @Positive int prazoDias,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        CurvaJuros c = service.curva(nome.toUpperCase(), ate != null ? ate : LocalDate.now());
        return new PontoCurva(c.getNome(), c.getDataReferencia(), prazoDias, c.taxaPara(prazoDias), c.fator(prazoDias), c.fatorDesconto(prazoDias));
    }

    @PostMapping("/curvas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public CurvaResposta registrarCurva(@Valid @RequestBody NovaCurva c) {
        return CurvaResposta.de(service.registrarCurva(c.nome().toUpperCase(), c.dataReferencia(), c.convencao(), c.vertices()));
    }

    @GetMapping("/termo/{par}")
    @Operation(summary = "Taxa a termo teórica por paridade coberta de juros (DI × cupom cambial)")
    public MercadoService.Termo termo(@PathVariable String par,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataReferencia,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimento) {
        return service.forwardTeorico(ParMoedas.de(par), dataReferencia, vencimento);
    }
}
