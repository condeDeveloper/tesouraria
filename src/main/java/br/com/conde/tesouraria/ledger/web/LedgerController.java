package br.com.conde.tesouraria.ledger.web;

import br.com.conde.tesouraria.ledger.application.LedgerService;
import br.com.conde.tesouraria.ledger.domain.*;
import br.com.conde.tesouraria.shared.domain.Money;
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
@RequestMapping("/api/ledger")
@Tag(name = "Ledger", description = "Plano de contas, lançamentos de partidas dobradas, saldos e extratos")
public class LedgerController {

    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    // ---------- DTOs ----------

    public record NovaConta(@NotBlank String codigo, @NotBlank String nome, @NotNull TipoConta tipo, @NotBlank String moeda, UUID contraparteId) {}

    public record ContaResposta(UUID id, String codigo, String nome, TipoConta tipo, NaturezaConta natureza, String moeda, UUID contraparteId, boolean ativa) {
        static ContaResposta de(ContaContabil c) {
            return new ContaResposta(c.getId(), c.getCodigo(), c.getNome(), c.getTipo(), c.getNatureza(), c.getMoeda(), c.getContraparteId(), c.isAtiva());
        }
    }

    public record NovaPartida(UUID contaId, String codigoConta, @NotNull TipoPartida tipo, @NotNull @Positive BigDecimal valor, String moeda) {}

    public record NovoLancamento(@NotBlank @Size(max = 80) String chaveIdempotencia, @NotNull LocalDate data,
                                 @NotBlank String descricao, @NotBlank String origem, UUID referenciaId,
                                 @NotEmpty @Size(min = 2) List<@Valid NovaPartida> partidas) {}

    public record PartidaResposta(String codigoConta, TipoPartida tipo, BigDecimal valor, String moeda) {
        static PartidaResposta de(Partida p) { return new PartidaResposta(p.getConta().getCodigo(), p.getTipo(), p.getValor(), p.getMoeda()); }
    }

    public record LancamentoResposta(UUID id, String chaveIdempotencia, LocalDate data, String descricao, String origem,
                                     UUID referenciaId, String criadoPor, Instant criadoEm, boolean estornado, UUID estornadoPor,
                                     List<PartidaResposta> partidas) {
        static LancamentoResposta de(Lancamento l) {
            return new LancamentoResposta(l.getId(), l.getChaveIdempotencia(), l.getDataLancamento(), l.getDescricao(), l.getOrigem(),
                    l.getReferenciaId(), l.getCriadoPor(), l.getCriadoEm(), l.isEstornado(), l.getEstornadoPor(),
                    l.getPartidas().stream().map(PartidaResposta::de).toList());
        }
    }

    public record SaldoResposta(UUID contaId, String codigo, LocalDate data, BigDecimal saldo, String moeda) {}

    public record Estorno(@NotNull LocalDate data) {}

    // ---------- contas ----------

    @GetMapping("/contas")
    public List<ContaResposta> contas() { return service.listarContas().stream().map(ContaResposta::de).toList(); }

    @GetMapping("/contas/{id}")
    public ContaResposta conta(@PathVariable UUID id) { return ContaResposta.de(service.conta(id)); }

    @PostMapping("/contas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ContaResposta criarConta(@Valid @RequestBody NovaConta c) {
        return ContaResposta.de(service.criarConta(c.codigo(), c.nome(), c.tipo(), c.moeda(), c.contraparteId()));
    }

    @PostMapping("/contas/{id}/desativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ContaResposta desativar(@PathVariable UUID id) { return ContaResposta.de(service.alterarSituacaoConta(id, false)); }

    @PostMapping("/contas/{id}/ativar")
    @PreAuthorize("hasRole('ADMIN')")
    public ContaResposta ativar(@PathVariable UUID id) { return ContaResposta.de(service.alterarSituacaoConta(id, true)); }

    @GetMapping("/contas/{id}/saldo")
    public SaldoResposta saldo(@PathVariable UUID id,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        LocalDate d = data != null ? data : LocalDate.now();
        Money s = service.saldo(id, d);
        return new SaldoResposta(id, service.conta(id).getCodigo(), d, s.quantia(), s.moeda());
    }

    @GetMapping("/contas/{id}/extrato")
    public List<LedgerService.LinhaExtrato> extrato(@PathVariable UUID id,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                                    @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim) {
        return service.extrato(id, inicio, fim);
    }

    @GetMapping("/balancete")
    @Operation(summary = "Saldo de todas as contas na data")
    public List<LedgerService.LinhaBalancete> balancete(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
        return service.balancete(data != null ? data : LocalDate.now());
    }

    // ---------- lançamentos ----------

    @GetMapping("/lancamentos")
    public Page<LancamentoResposta> lancamentos(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate inicio,
                                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fim,
                                                @PageableDefault(size = 50) Pageable pageable) {
        return service.listarLancamentos(inicio, fim, pageable).map(LancamentoResposta::de);
    }

    @GetMapping("/lancamentos/{id}")
    public LancamentoResposta lancamento(@PathVariable UUID id) { return LancamentoResposta.de(service.lancamento(id)); }

    @PostMapping("/lancamentos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    @Operation(summary = "Registra um lançamento manual", description = "Repetir a mesma chave de idempotência devolve o lançamento original sem duplicar.")
    public LancamentoResposta registrar(@Valid @RequestBody NovoLancamento corpo, Authentication auth) {
        var linhas = corpo.partidas().stream()
                .map(p -> new LedgerService.Linha(p.contaId(), p.codigoConta(), p.tipo(), p.valor(), p.moeda())).toList();
        var cmd = new LedgerService.Comando(corpo.chaveIdempotencia(), corpo.data(), corpo.descricao(), corpo.origem(),
                corpo.referenciaId(), auth.getName(), linhas);
        return LancamentoResposta.de(service.registrar(cmd));
    }

    @PostMapping("/lancamentos/{id}/estornar")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','OPERADOR')")
    public LancamentoResposta estornar(@PathVariable UUID id, @Valid @RequestBody Estorno corpo, Authentication auth) {
        return LancamentoResposta.de(service.estornar(id, corpo.data(), auth.getName()));
    }
}
