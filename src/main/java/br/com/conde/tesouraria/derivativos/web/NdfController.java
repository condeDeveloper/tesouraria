package br.com.conde.tesouraria.derivativos.web;

import br.com.conde.tesouraria.cambio.domain.LadoOperacao;
import br.com.conde.tesouraria.derivativos.application.NdfService;
import br.com.conde.tesouraria.derivativos.domain.ContratoNdf;
import br.com.conde.tesouraria.derivativos.domain.MarcacaoMercado;
import br.com.conde.tesouraria.derivativos.domain.SituacaoContrato;
import br.com.conde.tesouraria.mercado.domain.ParMoedas;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
@RequestMapping("/api/derivativos/ndf")
@Tag(name = "NDF", description = "Contratos a termo de moeda sem entrega: contratação, marcação a mercado, fixing e liquidação")
public class NdfController {

    private final NdfService service;

    public NdfController(NdfService service) {
        this.service = service;
    }

    public record NovoContrato(@NotNull UUID contraparteId, @NotBlank String par, @NotNull LadoOperacao lado,
                               @NotNull @Positive BigDecimal notional, @Positive BigDecimal taxaTermo,
                               LocalDate dataNegociacao, @NotNull LocalDate dataLiquidacao, LocalDate dataFixing) {}

    public record Liquidacao(LocalDate data) {}

    public record ContratoResposta(UUID id, String numero, UUID contraparteId, String par, LadoOperacao lado, BigDecimal notional,
                                   String moedaBase, String moedaLiquidacao, BigDecimal taxaTermo, BigDecimal forwardReferencia,
                                   LocalDate dataNegociacao, LocalDate dataFixing, LocalDate dataLiquidacao, SituacaoContrato situacao,
                                   BigDecimal taxaFixing, BigDecimal ajuste, String criadoPor, Instant criadoEm) {
        static ContratoResposta de(ContratoNdf c) {
            return new ContratoResposta(c.getId(), c.getNumero(), c.getContraparteId(), c.getPar(), c.getLado(), c.getNotional(),
                    c.getMoedaBase(), c.getMoedaLiquidacao(), c.getTaxaTermo(), c.getForwardReferencia(),
                    c.getDataNegociacao(), c.getDataFixing(), c.getDataLiquidacao(), c.getSituacao(),
                    c.getTaxaFixing(), c.getAjuste(), c.getCriadoPor(), c.getCriadoEm());
        }
    }

    public record MarcacaoResposta(LocalDate data, BigDecimal spot, BigDecimal forwardTeorico, BigDecimal fatorDesconto, BigDecimal valorPresente, UUID lancamentoId) {
        static MarcacaoResposta de(MarcacaoMercado m) {
            return new MarcacaoResposta(m.getData(), m.getSpot(), m.getForwardTeorico(), m.getFatorDesconto(), m.getValorPresente(), m.getLancamentoId());
        }
    }

    @GetMapping
    public Page<ContratoResposta> listar(@RequestParam(required = false) SituacaoContrato situacao,
                                         @RequestParam(required = false) UUID contraparteId,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return service.listar(situacao, contraparteId, pageable).map(ContratoResposta::de);
    }

    @GetMapping("/{id}")
    public ContratoResposta buscar(@PathVariable UUID id) { return ContratoResposta.de(service.buscar(id)); }

    @GetMapping("/{id}/marcacoes")
    public List<MarcacaoResposta> marcacoes(@PathVariable UUID id) { return service.marcacoes(id).stream().map(MarcacaoResposta::de).toList(); }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Contrata um NDF", description = "Sem taxa a termo, usa o forward teórico. Sem data de fixing, usa o dia útil anterior à liquidação.")
    public ContratoResposta contratar(@Valid @RequestBody NovoContrato c, Authentication auth) {
        var cmd = new NdfService.Contratacao(c.contraparteId(), ParMoedas.de(c.par()), c.lado(), c.notional(), c.taxaTermo(),
                c.dataNegociacao(), c.dataLiquidacao(), c.dataFixing(), auth.getName());
        return ContratoResposta.de(service.contratar(cmd));
    }

    @PostMapping("/{id}/marcar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Marca o contrato a mercado na data")
    public MarcacaoResposta marcar(@PathVariable UUID id, @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return MarcacaoResposta.de(service.marcar(id, data));
    }

    @PostMapping("/marcacoes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Marca a mercado todos os contratos abertos")
    public NdfService.ResultadoLote marcarTodos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.marcarTodos(data);
    }

    @PostMapping("/{id}/fixar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Fixa o contrato pela PTAX da data de fixing")
    public ContratoResposta fixar(@PathVariable UUID id) { return ContratoResposta.de(service.fixar(id)); }

    @PostMapping("/{id}/liquidar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ContratoResposta liquidar(@PathVariable UUID id, @RequestBody(required = false) Liquidacao corpo) {
        return ContratoResposta.de(service.liquidar(id, corpo != null ? corpo.data() : null));
    }

    @PostMapping("/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public ContratoResposta cancelar(@PathVariable UUID id, Authentication auth) { return ContratoResposta.de(service.cancelar(id, auth.getName())); }

    @PostMapping("/fixings")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Fixa em lote os contratos com data de fixing até a data")
    public List<ContratoResposta> fixarVencidos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.fixarVencidos(ate != null ? ate : LocalDate.now()).stream().map(ContratoResposta::de).toList();
    }

    @PostMapping("/liquidacoes")
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Liquida em lote os contratos fixados com liquidação até a data")
    public List<ContratoResposta> liquidarVencidos(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return service.liquidarVencidos(ate != null ? ate : LocalDate.now()).stream().map(ContratoResposta::de).toList();
    }
}
