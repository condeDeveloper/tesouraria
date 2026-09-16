package br.com.conde.tesouraria.cambio;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CambioIntegracaoTest {

    @Autowired
    TestRestTemplate http;

    HttpHeaders h;
    UUID contraparte;

    @BeforeEach
    void setup() {
        JsonNode r = http.postForEntity("/api/auth/login", Map.of("login", "admin", "senha", "admin123"), JsonNode.class).getBody();
        h = new HttpHeaders();
        h.setBearerAuth(r.get("token").asText());
        h.setContentType(MediaType.APPLICATION_JSON);
        // contraparte de teste (documento válido distinto por execução não é necessário: busca se já existir)
        ResponseEntity<JsonNode> cp = post("/api/contrapartes", Map.of("nome", "Exportadora Teste", "documento", "45.723.174/0001-10", "instituicaoFinanceira", false));
        if (cp.getStatusCode() == HttpStatus.CONFLICT) cp = get("/api/contrapartes/documento/45723174000110");
        contraparte = UUID.fromString(cp.getBody().get("id").asText());
    }

    private ResponseEntity<JsonNode> post(String url, Object body) { return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class); }
    private ResponseEntity<JsonNode> get(String url) { return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class); }

    private BigDecimal saldo(String codigo, String data) {
        for (JsonNode c : get("/api/ledger/balancete?data=" + data).getBody()) if (c.get("codigo").asText().equals(codigo)) return c.get("saldo").decimalValue();
        throw new AssertionError("conta " + codigo);
    }

    @Test
    void fechaLiquidaEContabilizaCompraDeDolar() {
        BigDecimal caixaUsdAntes = saldo("1.1.01.USD", "2026-09-30"), caixaBrlAntes = saldo("1.1.01.BRL", "2026-09-30");
        BigDecimal aLiquidarUsdAntes = saldo("1.2.01.USD", "2026-09-15");

        // 15/09/2026 (terça): D+2 úteis em BRL e USD = 17/09
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "lado", "COMPRA", "valorBase", 100000,
                "taxa", 5.3050, "dataNegociacao", "2026-09-15");
        ResponseEntity<JsonNode> r = post("/api/cambio/operacoes", corpo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode op = r.getBody();
        assertThat(op.get("numero").asText()).startsWith("CAM-");
        assertThat(op.get("dataLiquidacao").asText()).isEqualTo("2026-09-17");
        assertThat(op.get("valorCotado").decimalValue()).isEqualByComparingTo("530500");
        assertThat(op.get("taxaReferencia").decimalValue()).isEqualByComparingTo("5.308"); // spot semeado
        assertThat(op.get("resultadoSobreReferencia").decimalValue()).isEqualByComparingTo("300");

        // fechamento: comprado a liquidar USD sobe 100.000; caixa ainda não mexe
        assertThat(saldo("1.2.01.USD", "2026-09-15").subtract(aLiquidarUsdAntes)).isEqualByComparingTo("100000");
        assertThat(saldo("1.1.01.USD", "2026-09-16")).isEqualByComparingTo(caixaUsdAntes);

        // liquidar antes da data é recusado
        String id = op.get("id").asText();
        assertThat(post("/api/cambio/operacoes/" + id + "/liquidar", Map.of("data", "2026-09-16")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);

        // liquida na data: caixa USD +100.000, caixa BRL -530.500
        ResponseEntity<JsonNode> liq = post("/api/cambio/operacoes/" + id + "/liquidar", new HashMap<>());
        assertThat(liq.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(liq.getBody().get("situacao").asText()).isEqualTo("LIQUIDADA");
        assertThat(saldo("1.1.01.USD", "2026-09-30").subtract(caixaUsdAntes)).isEqualByComparingTo("100000");
        assertThat(saldo("1.1.01.BRL", "2026-09-30").subtract(caixaBrlAntes)).isEqualByComparingTo("-530500");
        assertThat(saldo("1.2.01.USD", "2026-09-30")).isEqualByComparingTo(aLiquidarUsdAntes);

        // posição: comprado em USD ao spot 5,308 vale 530.800; vendido em BRL 530.500 -> resultado não realizado +300
        JsonNode pos = get("/api/cambio/posicao?data=2026-09-30").getBody();
        BigDecimal usd = BigDecimal.ZERO;
        for (JsonNode m : pos.get("moedas")) if (m.get("moeda").asText().equals("USD")) usd = m.get("posicao").decimalValue();
        assertThat(usd).isGreaterThanOrEqualTo(new BigDecimal("100000")); // comprado
        assertThat(pos.get("resultadoNaoRealizadoBrl").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("300"));
    }

    @Test
    void cancelarEstornaOsLancamentos() {
        BigDecimal antes = saldo("1.2.01.EUR", "2026-12-31");
        var corpo = Map.of("contraparteId", contraparte, "par", "EURBRL", "lado", "COMPRA", "valorBase", 50000, "dataNegociacao", "2026-09-15");
        JsonNode op = post("/api/cambio/operacoes", corpo).getBody();
        assertThat(op.get("taxa").decimalValue()).isEqualByComparingTo("6.219"); // sem taxa: usa spot semeado
        assertThat(saldo("1.2.01.EUR", "2026-12-31").subtract(antes)).isEqualByComparingTo("50000");

        ResponseEntity<JsonNode> c = post("/api/cambio/operacoes/" + op.get("id").asText() + "/cancelar", null);
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(c.getBody().get("situacao").asText()).isEqualTo("CANCELADA");
        assertThat(saldo("1.2.01.EUR", "2026-12-31")).isEqualByComparingTo(antes);
        assertThat(post("/api/cambio/operacoes/" + op.get("id").asText() + "/cancelar", null).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void contraparteBloqueadaNaoOpera() {
        JsonNode cp = post("/api/contrapartes", Map.of("nome", "Bloqueada", "documento", "07.526.557/0001-00", "instituicaoFinanceira", false)).getBody();
        if (cp.has("id")) post("/api/contrapartes/" + cp.get("id").asText() + "/bloquear", null);
        else cp = get("/api/contrapartes/documento/07526557000100").getBody();
        var corpo = Map.of("contraparteId", cp.get("id").asText(), "par", "USDBRL", "lado", "VENDA", "valorBase", 1000, "taxa", 5.3);
        ResponseEntity<JsonNode> r = post("/api/cambio/operacoes", corpo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("detail").asText()).contains("bloqueada");
    }
}
