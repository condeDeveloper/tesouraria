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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class NdfIntegracaoTest {

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
        ResponseEntity<JsonNode> cp = post("/api/contrapartes", Map.of("nome", "Importadora NDF", "documento", "33.000.167/0001-01", "instituicaoFinanceira", false));
        if (cp.getStatusCode() == HttpStatus.CONFLICT) cp = get("/api/contrapartes/documento/33000167000101");
        contraparte = UUID.fromString(cp.getBody().get("id").asText());
    }

    private ResponseEntity<JsonNode> post(String url, Object body) { return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class); }
    private ResponseEntity<JsonNode> get(String url) { return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class); }

    private BigDecimal saldo(String codigo, String data) {
        for (JsonNode c : get("/api/ledger/balancete?data=" + data).getBody()) if (c.get("codigo").asText().equals(codigo)) return c.get("saldo").decimalValue();
        throw new AssertionError("conta " + codigo);
    }

    @Test
    void contrataMarcaFixaELiquidaComPtax() {
        // 15/09/2026 com curvas e spot semeados; liquidação 15/12/2026 (terça), fixing 14/12
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "lado", "COMPRA", "notional", 1000000,
                "dataNegociacao", "2026-09-15", "dataLiquidacao", "2026-12-15");
        ResponseEntity<JsonNode> r = post("/api/derivativos/ndf", corpo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        JsonNode c = r.getBody();
        String id = c.get("id").asText();
        assertThat(c.get("numero").asText()).startsWith("NDF-");
        assertThat(c.get("dataFixing").asText()).isEqualTo("2026-12-14");
        // sem taxa informada: termo = forward teórico > spot (juros BRL > cupom USD)
        BigDecimal termo = c.get("taxaTermo").decimalValue();
        assertThat(termo).isGreaterThan(new BigDecimal("5.308"));
        assertThat(c.get("forwardReferencia").decimalValue()).isEqualByComparingTo(termo);

        // marcação no mesmo dia: VP praticamente zero (termo = forward)
        ResponseEntity<JsonNode> m1 = post("/api/derivativos/ndf/" + id + "/marcar?data=2026-09-15", null);
        assertThat(m1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(m1.getBody().get("valorPresente").decimalValue().abs()).isLessThan(new BigDecimal("1"));

        // spot sobe para 5,50 em 16/09: contrato comprado passa a valer positivo
        post("/api/mercado/cotacoes", Map.of("par", "USDBRL", "tipo", "SPOT", "data", "2026-09-16", "taxa", 5.50, "fonte", "TESTE"));
        JsonNode m2 = post("/api/derivativos/ndf/" + id + "/marcar?data=2026-09-16", null).getBody();
        BigDecimal vp = m2.get("valorPresente").decimalValue();
        assertThat(vp).isGreaterThan(new BigDecimal("100000"));
        assertThat(saldo("1.3.01.BRL", "2026-09-16")).isGreaterThanOrEqualTo(vp.setScale(2, java.math.RoundingMode.HALF_EVEN));

        // duas marcações registradas
        assertThat(get("/api/derivativos/ndf/" + id + "/marcacoes").getBody().size()).isEqualTo(2);

        // fixing: sem PTAX em 14/12 -> 404; registra PTAX 5,60 e fixa: ajuste = (5,60 - termo) x 1.000.000
        assertThat(post("/api/derivativos/ndf/" + id + "/fixar", null).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        post("/api/mercado/cotacoes", Map.of("par", "USDBRL", "tipo", "PTAX", "data", "2026-12-14", "taxa", 5.60, "fonte", "BCB"));
        JsonNode fixado = post("/api/derivativos/ndf/" + id + "/fixar", null).getBody();
        assertThat(fixado.get("situacao").asText()).isEqualTo("FIXADO");
        BigDecimal ajusteEsperado = new BigDecimal("5.60").subtract(termo).multiply(new BigDecimal("1000000"));
        assertThat(fixado.get("ajuste").decimalValue()).isEqualByComparingTo(ajusteEsperado.setScale(4, java.math.RoundingMode.HALF_EVEN));

        // liquidação: caixa BRL recebe o ajuste, direito é baixado
        BigDecimal caixaAntes = saldo("1.1.01.BRL", "2026-12-15");
        JsonNode liq = post("/api/derivativos/ndf/" + id + "/liquidar", new HashMap<>()).getBody();
        assertThat(liq.get("situacao").asText()).isEqualTo("LIQUIDADO");
        assertThat(saldo("1.1.01.BRL", "2026-12-15").subtract(caixaAntes)).isEqualByComparingTo(ajusteEsperado.setScale(2, java.math.RoundingMode.HALF_EVEN));
    }

    @Test
    void cancelarEstornaMarcacoes() {
        var corpo = Map.of("contraparteId", contraparte, "par", "USDBRL", "lado", "VENDA", "notional", 500000,
                "taxaTermo", 5.30, "dataNegociacao", "2026-09-15", "dataLiquidacao", "2026-10-15");
        String id = post("/api/derivativos/ndf", corpo).getBody().get("id").asText();
        BigDecimal antesPos = saldo("1.3.01.BRL", "2026-12-31"), antesNeg = saldo("2.3.01.BRL", "2026-12-31");
        post("/api/derivativos/ndf/" + id + "/marcar?data=2026-09-15", null);
        ResponseEntity<JsonNode> c = post("/api/derivativos/ndf/" + id + "/cancelar", null);
        assertThat(c.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(c.getBody().get("situacao").asText()).isEqualTo("CANCELADO");
        assertThat(saldo("1.3.01.BRL", "2026-12-31")).isEqualByComparingTo(antesPos);
        assertThat(saldo("2.3.01.BRL", "2026-12-31")).isEqualByComparingTo(antesNeg);
    }

    @Test
    void recusaParNaoCotadoEmBrl() {
        var corpo = Map.of("contraparteId", contraparte, "par", "EURUSD", "lado", "COMPRA", "notional", 1000,
                "taxaTermo", 1.2, "dataNegociacao", "2026-09-15", "dataLiquidacao", "2026-10-15");
        assertThat(post("/api/derivativos/ndf", corpo).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
