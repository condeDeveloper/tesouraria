package br.com.conde.tesouraria.derivativos;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class OpcaoIntegracaoTest {

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
        ResponseEntity<JsonNode> cp = post("/api/contrapartes", Map.of("nome", "Fundo Opções", "documento", "60.746.948/0001-12", "instituicaoFinanceira", true));
        if (cp.getStatusCode() == HttpStatus.CONFLICT) cp = get("/api/contrapartes/documento/60746948000112");
        contraparte = UUID.fromString(cp.getBody().get("id").asText());
    }

    private ResponseEntity<JsonNode> post(String url, Object body) { return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class); }
    private ResponseEntity<JsonNode> get(String url) { return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class); }

    private BigDecimal saldo(String codigo, String data) {
        for (JsonNode c : get("/api/ledger/balancete?data=" + data).getBody()) if (c.get("codigo").asText().equals(codigo)) return c.get("saldo").decimalValue();
        throw new AssertionError("conta " + codigo);
    }

    @Test
    void precificaComDadosDeMercado() {
        JsonNode p = get("/api/derivativos/opcoes/precificar?par=USDBRL&tipo=CALL&strike=5.50&notional=1000000&data=2026-09-15&vencimento=2026-12-15").getBody();
        assertThat(p.get("spot").decimalValue()).isEqualByComparingTo("5.308");
        assertThat(p.get("forward").decimalValue()).isGreaterThan(p.get("spot").decimalValue());
        assertThat(p.get("volatilidade").decimalValue()).isBetween(new BigDecimal("0.13"), new BigDecimal("0.15"));
        assertThat(p.get("precoUnitario").decimalValue()).isPositive();
        assertThat(p.get("premioTotal").decimalValue()).isEqualByComparingTo(p.get("precoUnitario").decimalValue().multiply(new BigDecimal("1000000")).setScale(4, RoundingMode.HALF_EVEN));
        assertThat(p.get("delta").decimalValue()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void contrataMarcaEExerceCallComprada() {
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "tipo", "CALL", "posicao", "COMPRADA", "notional", 1000000,
                "strike", 5.40, "dataNegociacao", "2026-09-15", "dataVencimento", "2026-12-15");
        ResponseEntity<JsonNode> r = post("/api/derivativos/opcoes", corpo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode op = r.getBody();
        String id = op.get("id").asText();
        BigDecimal premio = op.get("premio").decimalValue();
        assertThat(op.get("numero").asText()).startsWith("OPC-");
        assertThat(premio).isPositive().isEqualByComparingTo(op.get("premioTeorico").decimalValue());
        assertThat(op.get("dataLiquidacao").asText()).isEqualTo("2026-12-17");

        // prêmio pago sai do caixa e vai para a conta de opções compradas
        assertThat(saldo("1.3.02.BRL", "2026-09-15")).isGreaterThanOrEqualTo(premio.setScale(2, RoundingMode.HALF_EVEN));

        // marcação no dia: valor ≈ prêmio -> variação ≈ 0
        JsonNode m1 = post("/api/derivativos/opcoes/" + id + "/marcar?data=2026-09-15", null).getBody();
        assertThat(m1.get("valor").decimalValue().subtract(premio).abs()).isLessThan(new BigDecimal("1"));
        assertThat(m1.get("delta").decimalValue()).isBetween(BigDecimal.ZERO, new BigDecimal("1000000"));

        // spot sobe: a call comprada ganha valor e o resultado reconhece o ganho
        post("/api/mercado/cotacoes", Map.of("par", "USDBRL", "tipo", "SPOT", "data", "2026-09-16", "taxa", 5.60, "fonte", "TESTE"));
        ResponseEntity<JsonNode> r2 = post("/api/derivativos/opcoes/" + id + "/marcar?data=2026-09-16", null);
        assertThat(r2.getStatusCode()).as("marcação em 16/09: " + r2.getBody()).isEqualTo(HttpStatus.OK);
        JsonNode m2 = r2.getBody();
        assertThat(m2.get("valor").decimalValue()).isGreaterThan(premio);

        // no vencimento: PTAX 5,70 -> payoff = 0,30 x 1.000.000 = 300.000
        post("/api/mercado/cotacoes", Map.of("par", "USDBRL", "tipo", "PTAX", "data", "2026-12-15", "taxa", 5.70, "fonte", "BCB"));
        BigDecimal caixaAntes = saldo("1.1.01.BRL", "2026-12-17");
        JsonNode ex = post("/api/derivativos/opcoes/" + id + "/exercer", null).getBody();
        assertThat(ex.get("situacao").asText()).isEqualTo("EXERCIDA");
        assertThat(ex.get("payoff").decimalValue()).isEqualByComparingTo("300000.0000");
        assertThat(saldo("1.1.01.BRL", "2026-12-17").subtract(caixaAntes)).isEqualByComparingTo("300000.00");
    }

    @Test
    void putLancadaExpiraForaDoDinheiro() {
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "tipo", "PUT", "posicao", "LANCADA", "notional", 200000,
                "strike", 5.00, "premio", 8000, "dataNegociacao", "2026-09-15", "dataVencimento", "2026-11-16");
        JsonNode op = post("/api/derivativos/opcoes", corpo).getBody();
        String id = op.get("id").asText();
        assertThat(op.get("premio").decimalValue()).isEqualByComparingTo("8000");
        post("/api/mercado/cotacoes", Map.of("par", "USDBRL", "tipo", "PTAX", "data", "2026-11-16", "taxa", 5.45, "fonte", "BCB"));
        BigDecimal resultadoAntes = saldo("4.2.01.BRL", "2026-11-18");
        JsonNode ex = post("/api/derivativos/opcoes/" + id + "/exercer", null).getBody();
        assertThat(ex.get("situacao").asText()).isEqualTo("EXPIRADA");
        assertThat(ex.get("payoff").decimalValue()).isZero();
        // prêmio recebido vira resultado
        assertThat(saldo("4.2.01.BRL", "2026-11-18").subtract(resultadoAntes)).isEqualByComparingTo("8000.00");
    }

    @Test
    void cancelarDevolveOPremio() {
        BigDecimal caixaAntes = saldo("1.1.01.BRL", "2026-12-31");
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "tipo", "CALL", "posicao", "COMPRADA", "notional", 100000,
                "strike", 5.30, "premio", 5000, "dataNegociacao", "2026-09-15", "dataVencimento", "2026-10-15");
        String id = post("/api/derivativos/opcoes", corpo).getBody().get("id").asText();
        assertThat(saldo("1.1.01.BRL", "2026-12-31")).isEqualByComparingTo(caixaAntes.subtract(new BigDecimal("5000")));
        post("/api/derivativos/opcoes/" + id + "/cancelar", null);
        assertThat(saldo("1.1.01.BRL", "2026-12-31")).isEqualByComparingTo(caixaAntes);
    }
}
