package br.com.conde.tesouraria.derivativos.web;

import br.com.conde.tesouraria.derivativos.application.NdfService;
import br.com.conde.tesouraria.derivativos.application.OpcaoService;
import br.com.conde.tesouraria.derivativos.domain.*;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
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
@RequestMapping("/api/derivativos/opcoes")
@Tag(name = "Opções", description = "Opções europeias de câmbio: precificação Garman-Kohlhagen, contratação, marcação, exercício")
public class OpcaoController {

    private final OpcaoService service;

    public OpcaoController(OpcaoService service) {
        this.service = service;
    }

    public record NovaOpcao(@NotNull UUID contraparteId, @NotBlank String par, @NotNull GarmanKohlhagen.Tipo tipo, @NotNull PosicaoOpcao posicao,
                            @NotNull @Positive BigDecimal notional, @NotNull @Positive BigDecimal strike, @PositiveOrZero BigDecimal premio,
                            @Positive BigDecimal volatilidade, LocalDate dataNegociacao, @NotNull LocalDate dataVencimento) {}

    public record OpcaoResposta(UUID id, String numero, UUID contraparteId, String par, GarmanKohlhagen.Tipo tipo, PosicaoOpcao posicao,
                                BigDecimal notional, BigDecimal strike, BigDecimal premio, BigDecimal premioTeorico, BigDecimal volatilidade,
                                LocalDate dataNegociacao, LocalDate dataVencimento, LocalDate dataLiquidacao, SituacaoOpcao situacao,
                                BigDecimal taxaFixing, BigDecimal payoff, String criadoPor, Instant criadoEm) {
        static OpcaoResposta de(OpcaoCambio o) {
            return new OpcaoResposta(o.getId(), o.getNumero(), o.getContraparteId(), o.getPar(), o.getTipo(), o.getPosicao(),
                    o.getNotional(), o.getStrike(), o.getPremio(), o.getPremioTeorico(), o.getVolatilidade(),
                    o.getDataNegociacao(), o.getDataVencimento(), o.getDataLiquidacao(), o.getSituacao(),
                    o.getTaxaFixing(), o.getPayoff(), o.getCriadoPor(), o.getCriadoEm());
        }
    }

    public record MarcacaoResposta(LocalDate data, BigDecimal spot, BigDecimal forward, BigDecimal volatilidade, BigDecimal prazoAnos,
                                   BigDecimal valorUnitario, BigDecimal valor, BigDecimal delta, BigDecimal gamma, BigDecimal vega, BigDecimal theta, BigDecimal rho) {
        static MarcacaoResposta de(MarcacaoOpcao m) {
            return new MarcacaoResposta(m.getData(), m.getSpot(), m.getForward(), m.getVolatilidade(), m.getPrazoAnos(), m.getValorUnitario(),
                    m.getValor(), m.getDelta(), m.getGamma(), m.getVega(), m.getTheta(), m.getRho());
        }
    }

    @GetMapping("/precificar")
    @Operation(summary = "Precifica uma opção sem contratar", description = "Devolve prêmio unitário e total, forward, volatilidade usada e gregas por unidade.")
    public OpcaoService.Preco precificar(@RequestParam String par, @RequestParam GarmanKohlhagen.Tipo tipo, @RequestParam BigDecimal strike,
                                         @RequestParam(defaultValue = "1") BigDecimal notional,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data,
                                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimento,
                                         @RequestParam(required = false) BigDecimal volatilidade) {
        return service.precificar(ParMoedas.de(par), tipo, strike, notional, data, vencimento, volatilidade);
    }

    @GetMapping
    public Page<OpcaoResposta> listar(@RequestParam(required = false) SituacaoOpcao situacao, @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(situacao, pageable).map(OpcaoResposta::de);
    }

    @GetMapping("/{id}")
    public OpcaoResposta buscar(@PathVariable UUID id) { return OpcaoResposta.de(service.buscar(id)); }

    @GetMapping("/{id}/marcacoes")
    public List<MarcacaoResposta> marcacoes(@PathVariable UUID id) { return service.marcacoes(id).stream().map(MarcacaoResposta::de).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Contrata uma opção", description = "Sem prêmio, usa o teórico. Sem volatilidade, usa a curva VOL da moeda.")
    public OpcaoResposta contratar(@Valid @RequestBody NovaOpcao c, Authentication auth) {
        var cmd = new OpcaoService.Contratacao(c.contraparteId(), ParMoedas.de(c.par()), c.tipo(), c.posicao(), c.notional(), c.strike(),
                c.premio(), c.volatilidade(), c.dataNegociacao(), c.dataVencimento(), auth.getName());
        return OpcaoResposta.de(service.contratar(cmd));
    }

    @PostMapping("/{id}/marcar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public MarcacaoResposta marcar(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return MarcacaoResposta.de(service.marcar(id, data));
    }

    @PostMapping("/marcacoes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public NdfService.ResultadoLote marcarTodas(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.marcarTodas(data);
    }

    @PostMapping("/{id}/exercer")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Exerce ou expira pela PTAX do vencimento e liquida o payoff")
    public OpcaoResposta exercer(@PathVariable UUID id) { return OpcaoResposta.de(service.exercer(id)); }

    @PostMapping("/exercicios")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public List<OpcaoResposta> exercerVencidas(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.exercerVencidas(ate != null ? ate : LocalDate.now()).stream().map(OpcaoResposta::de).toList();
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public OpcaoResposta cancelar(@PathVariable UUID id, Authentication auth) { return OpcaoResposta.de(service.cancelar(id, auth.getName())); }
}
