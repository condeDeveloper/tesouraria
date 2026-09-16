package br.com.conde.tesouraria.risco;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RiscoIntegracaoTest {

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
        ResponseEntity<JsonNode> cp = post("/api/contrapartes", Map.of("nome", "Cliente Risco", "documento", "02.558.157/0001-62", "instituicaoFinanceira", false));
        if (cp.getStatusCode() == HttpStatus.CONFLICT) cp = get("/api/contrapartes/documento/02558157000162");
        contraparte = UUID.fromString(cp.getBody().get("id").asText());
    }

    private ResponseEntity<JsonNode> post(String url, Object body) { return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class); }
    private ResponseEntity<JsonNode> put(String url, Object body) { return http.exchange(url, HttpMethod.PUT, new HttpEntity<>(body, h), JsonNode.class); }
    private ResponseEntity<JsonNode> get(String url) { return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class); }

    @Test
    void limiteEUtilizacaoSomamOperacoesEmAberto() {
        assertThat(get("/api/risco/limites/" + contraparte).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(put("/api/risco/limites/" + contraparte, Map.of("limiteBrl", 1000000)).getBody().get("limiteBrl").decimalValue()).isEqualByComparingTo("1000000.00");

        // NDF de 100.000 USD a 5,40 = 540.000 BRL de utilização
        post("/api/derivativos/ndf", Map.of("contraparteId", contraparte, "par", "USDBRL", "lado", "COMPRA", "notional", 100000,
                "taxaTermo", 5.40, "dataNegociacao", "2026-09-15", "dataLiquidacao", "2026-12-15"));
        // câmbio spot de 50.000 USD a 5,30 = 265.000 BRL
        post("/api/cambio/operacoes", Map.of("contraparteId", contraparte, "par", "USDBRL", "lado", "VENDA", "valorBase", 50000, "taxa", 5.30, "dataNegociacao", "2026-09-15"));

        JsonNode u = get("/api/risco/limites/" + contraparte + "/utilizacao?data=2026-09-15").getBody();
        assertThat(u.get("ndfBrl").decimalValue()).isEqualByComparingTo("540000.00");
        assertThat(u.get("cambioBrl").decimalValue()).isEqualByComparingTo("265000.00");
        assertThat(u.get("utilizadoBrl").decimalValue()).isEqualByComparingTo("805000.00");
        assertThat(u.get("disponivelBrl").decimalValue()).isEqualByComparingTo("195000.00");
        assertThat(u.get("excedido").asBoolean()).isFalse();

        // reduz o limite: fica excedido
        put("/api/risco/limites/" + contraparte, Map.of("limiteBrl", 500000));
        assertThat(get("/api/risco/limites/" + contraparte + "/utilizacao?data=2026-09-15").getBody().get("excedido").asBoolean()).isTrue();
    }

    @Test
    void exposicaoConsolidaCambioNdfEOpcoes() {
        JsonNode e = get("/api/risco/exposicao?data=2026-09-15").getBody();
        assertThat(e.get("data").asText()).isEqualTo("2026-09-15");
        assertThat(e.get("moedas").isArray()).isTrue();
        for (JsonNode m : e.get("moedas")) {
            assertThat(m.get("moeda").asText()).isNotEqualTo("BRL");
            assertThat(m.get("total").decimalValue()).isEqualByComparingTo(
                    m.get("posicaoCambio").decimalValue().add(m.get("ndf").decimalValue()).add(m.get("deltaOpcoes").decimalValue()).setScale(2, java.math.RoundingMode.HALF_EVEN));
        }
        assertThat(e.get("totalAbsolutoBrl").decimalValue()).isGreaterThanOrEqualTo(e.get("totalBrl").decimalValue().abs());
    }

    @Test
    void varParametricoFuncionaSemHistoricoLongo() {
        JsonNode v = get("/api/risco/var?par=USDBRL&data=2026-09-15&confianca=0.95&horizonteDias=1").getBody();
        assertThat(v.get("observacoes").asInt()).isLessThan(30);
        assertThat(v.has("varHistoricoBrl")).isFalse(); // sem série suficiente
        assertThat(v.get("volCurva").decimalValue()).isBetween(new BigDecimal("0.10"), new BigDecimal("0.20"));
        assertThat(v.get("varParametricoBrl").decimalValue()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    void varHistoricoComSerieSuficiente() {
        // registra 40 PTAX diárias de EURBRL com pequena oscilação
        double taxa = 6.20;
        for (int i = 0; i < 40; i++) {
            taxa *= 1 + (i % 3 == 0 ? -0.006 : 0.004);
            post("/api/mercado/cotacoes", Map.of("par", "EURBRL", "tipo", "PTAX", "data", java.time.LocalDate.of(2026, 7, 1).plusDays(i).toString(),
                    "taxa", BigDecimal.valueOf(taxa).setScale(6, java.math.RoundingMode.HALF_EVEN), "fonte", "TESTE"));
        }
        JsonNode v = get("/api/risco/var?par=EURBRL&data=2026-08-15&confianca=0.95&janelaDias=60").getBody();
        assertThat(v.get("observacoes").asInt()).isGreaterThanOrEqualTo(30);
        assertThat(v.get("varHistoricoUnitario").decimalValue()).isGreaterThan(BigDecimal.ZERO);
        assertThat(v.get("volAnualizadaHistorica").decimalValue()).isGreaterThan(BigDecimal.ZERO);
    }
}
