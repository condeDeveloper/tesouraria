package br.com.conde.tesouraria;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Sobe a aplicação com H2 em memória, roda as migrações e exercita a API de ponta a ponta. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiIntegracaoTest {

    @Autowired
    TestRestTemplate http;

    private String token;

    private HttpHeaders auth() {
        if (token == null) {
            ResponseEntity<JsonNode> r = http.postForEntity("/api/auth/login", Map.of("login", "admin", "senha", "admin123"), JsonNode.class);
            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
            token = r.getBody().get("token").asText();
        }
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token);
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    @Test
    @Order(1)
    void semTokenRecebe401() {
        ResponseEntity<String> r = http.getForEntity("/api/moedas", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @Order(2)
    void loginInvalidoRecebe401ComProblemDetail() {
        ResponseEntity<JsonNode> r = http.postForEntity("/api/auth/login", Map.of("login", "admin", "senha", "errada"), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(r.getBody().get("title").asText()).isEqualTo("Não autenticado");
    }

    @Test
    @Order(3)
    void listaMoedasSemeadasPelaMigracao() {
        ResponseEntity<JsonNode> r = http.exchange("/api/moedas", HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).extracting(n -> n.get("codigo").asText()).contains("BRL", "USD", "EUR");
    }

    @Test
    @Order(4)
    void cadastraContraparteERecusaDuplicada() {
        var corpo = Map.of("nome", "Empresa Exemplo", "documento", "11.222.333/0001-81", "instituicaoFinanceira", false, "email", "fin@exemplo.com");
        ResponseEntity<JsonNode> r = http.exchange("/api/contrapartes", HttpMethod.POST, new HttpEntity<>(corpo, auth()), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(r.getBody().get("documento").asText()).isEqualTo("11.222.333/0001-81");
        assertThat(r.getBody().get("tipo").asText()).isEqualTo("PJ");

        ResponseEntity<JsonNode> dup = http.exchange("/api/contrapartes", HttpMethod.POST, new HttpEntity<>(corpo, auth()), JsonNode.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @Order(5)
    void documentoInvalidoVira422() {
        var corpo = Map.of("nome", "X", "documento", "111.111.111-11", "instituicaoFinanceira", false);
        ResponseEntity<JsonNode> r = http.exchange("/api/contrapartes", HttpMethod.POST, new HttpEntity<>(corpo, auth()), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(r.getBody().get("detail").asText()).contains("CPF inválido");
    }

    @Test
    @Order(6)
    void calendarioCombinaPracasBrlEUsd() {
        // 20/11/2026 é feriado só no Brasil; para BRL,USD não é dia útil
        ResponseEntity<JsonNode> r = http.exchange("/api/calendario/dia-util?pracas=BRL,USD&data=2026-11-20",
                HttpMethod.GET, new HttpEntity<>(auth()), JsonNode.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody().get("diaUtil").asBoolean()).isFalse();
        assertThat(r.getBody().get("proximoDiaUtil").asText()).isEqualTo("2026-11-23");
    }

    @Test
    @Order(7)
    void docsSaoPublicos() {
        ResponseEntity<String> r = http.getForEntity("/docs/api", String.class);
        assertThat(r.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(r.getBody()).contains("Tesouraria");
    }
}
