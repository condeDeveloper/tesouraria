# Tesouraria

Back-end de um sistema financeiro de tesouraria em Java 21 e Spring Boot 3: cadastros, ledger de partidas dobradas, dados de mercado, câmbio spot e derivativos cambiais (NDF e opções) com marcação a mercado e risco.

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
curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"login":"admin","senha":"admin123"}'
```

Use o token devolvido em `Authorization: Bearer <token>`.

## Testes

```bash
./mvnw test
```

Unitários (domínio puro), de arquitetura (ArchUnit) e de integração (sobe a aplicação com H2 em memória e exercita a API).

## Arquitetura

Monólito modular. Cada módulo tem `domain` (entidades, regras, portas), `application` (casos de uso) e `web` (controllers e DTOs). Regras verificadas por ArchUnit:

- o domínio não depende da web nem da camada de aplicação
- controllers não acessam repositórios diretamente

Dinheiro é sempre `Money` (BigDecimal + moeda ISO). Datas são `java.time`. Nunca `double`.

## Módulos

| Módulo | O que faz |
|--------|-----------|
| `shared` | Money, exceções de domínio, Problem Details (RFC 9457), OpenAPI |
| `seguranca` | usuários, papéis (ADMIN, OPERADOR, LEITOR), JWT |
| `moedas` | cadastro ISO 4217 |
| `calendario` | feriados por praça e cálculo de dias úteis combinando praças |
| `contrapartes` | clientes e instituições, validação de CPF/CNPJ, situação |

## Perfis

| Perfil | Banco |
|--------|-------|
| `dev` (padrão) | H2 em arquivo (`./data`) com console |
| `test` | H2 em memória |
| `prod` | PostgreSQL via `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` |

Migrações com Flyway em `src/main/resources/db/migration`, compatíveis com H2 (modo PostgreSQL) e PostgreSQL.

## Licença

MIT
