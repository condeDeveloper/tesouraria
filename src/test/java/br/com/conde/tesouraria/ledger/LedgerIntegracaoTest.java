package br.com.conde.tesouraria.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class LedgerIntegracaoTest {

    @Autowired
    TestRestTemplate http;

    HttpHeaders h;

    @BeforeEach
    void login() {
        JsonNode r = http.postForEntity("/api/auth/login", Map.of("login", "admin", "senha", "admin123"), JsonNode.class).getBody();
        h = new HttpHeaders();
        h.setBearerAuth(r.get("token").asText());
        h.setContentType(MediaType.APPLICATION_JSON);
    }

    private ResponseEntity<JsonNode> post(String url, Object body) {
        return http.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), JsonNode.class);
    }

    private ResponseEntity<JsonNode> get(String url) {
        return http.exchange(url, HttpMethod.GET, new HttpEntity<>(h), JsonNode.class);
    }

    private UUID contaId(String codigo) {
        for (JsonNode c : get("/api/ledger/contas").getBody()) if (c.get("codigo").asText().equals(codigo)) return UUID.fromString(c.get("id").asText());
        throw new AssertionError("conta não encontrada: " + codigo);
    }

    @Test
    void registraLancamentoIdempotenteEAtualizaSaldoEExtrato() {
        String chave = "TESTE-" + UUID.randomUUID();
        var corpo = Map.of(
                "chaveIdempotencia", chave, "data", "2026-09-15", "descricao", "Aporte inicial", "origem", "MANUAL",
                "partidas", List.of(
                        Map.of("codigoConta", "1.1.01.BRL", "tipo", "DEBITO", "valor", 1000),
                        Map.of("codigoConta", "3.1.01.BRL", "tipo", "CREDITO", "valor", 1000)));

        ResponseEntity<JsonNode> r1 = post("/api/ledger/lancamentos", corpo);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String id1 = r1.getBody().get("id").asText();

        // mesma chave: devolve o mesmo lançamento, não duplica
        ResponseEntity<JsonNode> r2 = post("/api/ledger/lancamentos", corpo);
        assertThat(r2.getBody().get("id").asText()).isEqualTo(id1);

        UUID caixa = contaId("1.1.01.BRL"), patrimonio = contaId("3.1.01.BRL");
        assertThat(get("/api/ledger/contas/" + caixa + "/saldo?data=2026-09-15").getBody().get("saldo").decimalValue()).isEqualByComparingTo("1000.00");
        assertThat(get("/api/ledger/contas/" + patrimonio + "/saldo?data=2026-09-15").getBody().get("saldo").decimalValue()).isEqualByComparingTo("1000.00");
        // antes da data o saldo é zero
        assertThat(get("/api/ledger/contas/" + caixa + "/saldo?data=2026-09-14").getBody().get("saldo").decimalValue()).isEqualByComparingTo("0.00");

        JsonNode extrato = get("/api/ledger/contas/" + caixa + "/extrato?inicio=2026-09-01&fim=2026-09-30").getBody();
        assertThat(extrato.size()).isGreaterThanOrEqualTo(1);
        assertThat(extrato.get(extrato.size() - 1).get("saldo").decimalValue()).isEqualByComparingTo(
                get("/api/ledger/contas/" + caixa + "/saldo?data=2026-09-30").getBody().get("saldo").decimalValue());
    }

    @Test
    void recusaLancamentoDesbalanceadoCom422() {
        var corpo = Map.of(
                "chaveIdempotencia", "TESTE-" + UUID.randomUUID(), "data", "2026-09-15", "descricao", "Errado", "origem", "MANUAL",
                "partidas", List.of(
                        Map.of("codigoConta", "1.1.01.BRL", "tipo", "DEBITO", "valor", 100),
                        Map.of("codigoConta", "3.1.01.BRL", "tipo", "CREDITO", "valor", 99)));
        ResponseEntity<JsonNode> r = post("/api/ledger/lancamentos", corpo);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("detail").asText()).contains("desbalanceado");
    }

    @Test
    void estornoZeraOEfeitoNoSaldo() {
        String chave = "TESTE-" + UUID.randomUUID();
        var corpo = Map.of(
                "chaveIdempotencia", chave, "data", "2026-10-01", "descricao", "Receita a estornar", "origem", "MANUAL",
                "partidas", List.of(
                        Map.of("codigoConta", "1.1.01.USD", "tipo", "DEBITO", "valor", 250),
                        Map.of("codigoConta", "2.1.01.USD", "tipo", "CREDITO", "valor", 250)));
        String id = post("/api/ledger/lancamentos", corpo).getBody().get("id").asText();
        UUID caixaUsd = contaId("1.1.01.USD");
        var antes = get("/api/ledger/contas/" + caixaUsd + "/saldo?data=2026-10-01").getBody().get("saldo").decimalValue();

        ResponseEntity<JsonNode> e = post("/api/ledger/lancamentos/" + id + "/estornar", Map.of("data", "2026-10-02"));
        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(e.getBody().get("descricao").asText()).startsWith("Estorno:");

        var depois = get("/api/ledger/contas/" + caixaUsd + "/saldo?data=2026-10-02").getBody().get("saldo").decimalValue();
        assertThat(depois).isEqualByComparingTo(antes.subtract(new java.math.BigDecimal("250")));
        assertThat(get("/api/ledger/lancamentos/" + id).getBody().get("estornado").asBoolean()).isTrue();

        // segundo estorno é recusado
        assertThat(post("/api/ledger/lancamentos/" + id + "/estornar", Map.of("data", "2026-10-03")).getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void balanceteListaTodasAsContas() {
        JsonNode b = get("/api/ledger/balancete?data=2026-12-31").getBody();
        assertThat(b.size()).isGreaterThanOrEqualTo(16);
        assertThat(b.get(0).has("saldo")).isTrue();
    }
}
