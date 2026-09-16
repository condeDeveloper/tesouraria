# Tesouraria

[![CI](https://github.com/condeDeveloper/tesouraria/actions/workflows/ci.yml/badge.svg)](https://github.com/condeDeveloper/tesouraria/actions/workflows/ci.yml)

Back-end de um sistema financeiro de tesouraria em Java 21 e Spring Boot 3. Cobre o ciclo completo de uma mesa de câmbio: cadastros, ledger de partidas dobradas, dados de mercado, câmbio pronto, derivativos cambiais (NDF e opções) com marcação a mercado e risco.

## Rodar

Só precisa de Java 21. O Maven vem pelo wrapper.

```bash
./mvnw spring-boot:run
```

- API: http://localhost:8080
- Documentação interativa (OpenAPI): http://localhost:8080/docs
- Console do banco H2 (dev): http://localhost:8080/h2 · JDBC `jdbc:h2:file:./data/tesouraria` · usuário `sa`

Na primeira subida é criado o usuário `admin` com senha `admin123` (ou o valor de `TESOURARIA_ADMIN_SENHA`).

```bash
TOKEN=$(curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin","senha":"admin123"}' | jq -r .token)

# forward teórico USDBRL para 3 meses, pela paridade DI x cupom cambial
curl -s "localhost:8080/api/mercado/termo/USDBRL?dataReferencia=2026-09-15&vencimento=2026-12-15" -H "Authorization: Bearer $TOKEN"

# precificar uma call de dólar
curl -s "localhost:8080/api/derivativos/opcoes/precificar?par=USDBRL&tipo=CALL&strike=5.50&notional=1000000&data=2026-09-15&vencimento=2026-12-15" -H "Authorization: Bearer $TOKEN"
```

O banco de desenvolvimento já vem com moedas, feriados de BRL/USD/EUR até 2027, plano de contas, cotações, curva DI, cupom cambial e volatilidade de exemplo em 15/09/2026.

## Testes

```bash
./mvnw test
```

Três camadas: unitários sobre o domínio puro (Money, calendário, CPF/CNPJ, lançamentos, curvas, NDF, Garman-Kohlhagen, VaR), regras de arquitetura com ArchUnit e testes de integração que sobem a aplicação com H2 em memória, autenticam e exercitam a API de ponta a ponta, conferindo os saldos contábeis depois de cada operação.

## Arquitetura

Monólito modular. Cada módulo tem `domain` (entidades, regras, portas), `application` (casos de uso) e `web` (controllers e DTOs). Regras verificadas por ArchUnit:

- o domínio não depende da web nem da camada de aplicação
- controllers não acessam repositórios diretamente

Dinheiro é sempre `Money` (BigDecimal + moeda ISO). Datas são `java.time`. Nunca `double` fora dos modelos matemáticos (precificação e VaR), que devolvem BigDecimal na borda.

## Módulos

| Módulo | O que faz |
|--------|-----------|
| `shared` | Money, exceções de domínio, Problem Details (RFC 9457), OpenAPI, numeração sequencial |
| `seguranca` | usuários, papéis (ADMIN, OPERADOR, LEITOR), JWT stateless |
| `moedas` | cadastro ISO 4217 |
| `calendario` | feriados por praça e dias úteis combinando praças (D+2 de câmbio, fixing D-1, modified following) |
| `contrapartes` | clientes e instituições, validação de CPF/CNPJ, bloqueio |
| `ledger` | plano de contas, lançamentos balanceados por moeda, idempotência, estorno, saldo, extrato, balancete |
| `mercado` | cotações spot e PTAX, curvas DI (exp. 252) e cupom cambial (linear 360) com interpolação, forward teórico |
| `cambio` | câmbio pronto com liquidação em D+n úteis, contabilização por posição de câmbio, cancelamento por estorno, posição e resultado não realizado |
| `derivativos` | NDF (fixing PTAX, MtM por reversão, liquidação por ajuste) e opções europeias (Garman-Kohlhagen, gregas, prêmio, MtM, exercício) |
| `risco` | exposição consolidada por moeda (posição + NDF + delta de opções), VaR histórico e paramétrico, limites por contraparte |

## Contabilidade

Todo evento financeiro vira um lançamento de partidas dobradas que fecha em cada moeda. Alguns exemplos:

| Evento | Débito | Crédito |
|--------|--------|---------|
| Compra de USD (fechamento) | Câmbio comprado a liquidar USD / Posição de câmbio BRL | Posição de câmbio USD / Câmbio vendido a liquidar BRL |
| Compra de USD (liquidação) | Caixa USD / Câmbio vendido a liquidar BRL | Câmbio comprado a liquidar USD / Caixa BRL |
| NDF com VP positivo (marcação) | Derivativos ajuste positivo | Resultado com derivativos |
| NDF liquidado com ganho | Caixa BRL | Derivativos ajuste positivo |
| Opção comprada (prêmio) | Opções compradas | Caixa BRL |
| Opção exercida | Caixa BRL (payoff) | Opções compradas, e o resultado realizado (payoff − prêmio) vai a resultado |

Cada nova marcação a mercado estorna a anterior e lança o valor cheio, o que mantém o histórico auditável e os saldos sempre iguais ao último valor.

## Perfis

| Perfil | Banco |
|--------|-------|
| `dev` (padrão) | H2 em arquivo (`./data`) com console |
| `test` | H2 em memória |
| `prod` | PostgreSQL via `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` |

Migrações com Flyway em `src/main/resources/db/migration`, compatíveis com H2 (modo PostgreSQL) e PostgreSQL.

## Licença

MIT
