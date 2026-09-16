package br.com.conde.tesouraria.cambio.web;

import br.com.conde.tesouraria.cambio.application.CambioService;
import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.cambio.domain.OperacaoCambio;
import br.com.conde.tesouraria.cambio.domain.SituacaoOperacao;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/cambio")
@Tag(name = "Câmbio", description = "Operações de câmbio pronto, liquidação e posição")
public class CambioController {

    private final CambioService service;

    public CambioController(CambioService service) {
        this.service = service;
    }

    public record NovaOperacao(@NotNull UUID contraparteId, @NotBlank String par, @NotNull LadoOperacao lado,
                               @NotNull @Positive BigDecimal valorBase, @Positive BigDecimal taxa,
                               LocalDate dataNegociacao, @Min(0) @Max(5) Integer prazoDiasUteis) {}

    public record Liquidacao(LocalDate data) {}

    public record OperacaoResposta(UUID id, String numero, UUID contraparteId, String par, LadoOperacao lado,
                                   BigDecimal valorBase, String moedaBase, BigDecimal taxa, BigDecimal valorCotado, String moedaCotada,
                                   BigDecimal taxaReferencia, BigDecimal resultadoSobreReferencia,
                                   LocalDate dataNegociacao, LocalDate dataLiquidacao, SituacaoOperacao situacao,
                                   String criadoPor, Instant criadoEm, Instant liquidadoEm, Instant canceladoEm) {
        static OperacaoResposta de(OperacaoCambio o) {
            return new OperacaoResposta(o.getId(), o.getNumero(), o.getContraparteId(), o.getPar(), o.getLado(),
                    o.getValorBase(), o.getMoedaBase(), o.getTaxa(), o.getValorCotado(), o.getMoedaCotada(),
                    o.getTaxaReferencia(), o.resultadoSobreReferencia().quantia(),
                    o.getDataNegociacao(), o.getDataLiquidacao(), o.getSituacao(),
                    o.getCriadoPor(), o.getCriadoEm(), o.getLiquidadoEm(), o.getCanceladoEm());
        }
    }

    @GetMapping("/operacoes")
    public Page<OperacaoResposta> listar(@RequestParam(required = false) SituacaoOperacao situacao,
                                         @RequestParam(required = false) UUID contraparteId,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(situacao, contraparteId, pageable).map(OperacaoResposta::de);
    }

    @GetMapping("/operacoes/{id}")
    public OperacaoResposta buscar(@PathVariable UUID id) { return OperacaoResposta.de(service.buscar(id)); }

    @PostMapping("/operacoes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Fecha uma operação de câmbio pronto", description = "Sem taxa, usa o spot mais recente. Liquidação em D+2 dias úteis por padrão.")
    public OperacaoResposta fechar(@Valid @RequestBody NovaOperacao c, Authentication auth) {
        var f = new CambioService.Fechamento(c.contraparteId(), ParMoedas.de(c.par()), c.lado(), c.valorBase(), c.taxa(),
                c.dataNegociacao(), c.prazoDiasUteis(), auth.getName());
        return OperacaoResposta.de(service.fechar(f));
    }

    @PostMapping("/operacoes/{id}/liquidar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public OperacaoResposta liquidar(@PathVariable UUID id, @RequestBody(required = false) Liquidacao corpo) {
        return OperacaoResposta.de(service.liquidar(id, corpo != null ? corpo.data() : null));
    }

    @PostMapping("/operacoes/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public OperacaoResposta cancelar(@PathVariable UUID id, Authentication auth) {
        return OperacaoResposta.de(service.cancelar(id, auth.getName()));
    }

    @PostMapping("/liquidacoes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Liquida em lote todas as operações abertas vencidas até a data")
    public List<OperacaoResposta> liquidarVencidas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.liquidarVencidas(ate != null ? ate : LocalDate.now()).stream().map(OperacaoResposta::de).toList();
    }

    @GetMapping("/posicao")
    @Operation(summary = "Posição de câmbio por moeda e resultado não realizado em BRL ao spot")
    public CambioService.Posicao posicao(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.posicao(data != null ? data : LocalDate.now());
    }
}
