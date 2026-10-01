# JWT com Claims Customizados

API multi-tenant em que o token, emitido no login, carrega dados próprios do usuário — tenant, departamento e nível de acesso — e esses dados são lidos na abertura do token e usados para decidir cada autorização.

É o passo seguinte ao [projeto 08](../java-spring-jwt-api-auth), que cobre o básico de registro, login, roles e refresh token. Aqui a autorização não é mais por papel, e sim **por atributo**: a mesma rota responde de forma diferente conforme o tenant, o departamento e o nível de acesso de quem chama.

## O token

```json
{
  "sub": "ana@example.com",
  "uid": 1,
  "tenantId": 1,
  "tenantSlug": "acme",
  "department": "FINANCE",
  "clearance": 3,
  "role": "ROLE_USER",
  "sid": "7d5f9301-cdbb-4626-927f-50f4bd4be653"
}
```

Esses claims viram um `Authentication` próprio, com um principal tipado, e as regras de autorização passam a consultá-lo. Documento de outro tenant responde **404, não 403** — informar que o recurso existe já seria vazar informação entre clientes.

O claim `sid` identifica a sessão: o logout registra o identificador no Redis com TTL igual ao tempo restante do token, e o mesmo token deixa de valer antes de expirar.

## Tecnologias e bibliotecas

| | |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.5, Spring Security 6 |
| Tokens | jjwt 0.13 (HMAC-SHA512) |
| Revogação de sessão | Redis 7 via Spring Data Redis |
| Persistência | Spring Data JPA, PostgreSQL 16 |
| Migrations | Flyway |
| Validação | Bean Validation |
| Build | Gradle Kotlin DSL (wrapper `gradlew`) |
| Testes | JUnit 5, Spring Security Test, Testcontainers |
| Apoio | Lombok |

## Pré-requisitos

- JDK 21 ou superior
- Docker

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

A API fica em `http://localhost:8080`. Usuários semeados, todos com a senha `secret123`:

| Tenant | E-mail | Departamento | Clearance | Role |
|---|---|---|---|---|
| acme | ana@example.com | FINANCE | 3 | USER |
| acme | bruno@example.com | ENGINEERING | 1 | USER |
| acme | admin@example.com | FINANCE | 5 | ADMIN |
| globex | ana@example.com | FINANCE | 4 | USER |

O mesmo e-mail aparece nos dois tenants de propósito: isolamento testado só com dados que jamais colidiriam não foi testado.

## Endpoints

| Método | Rota | Regra de acesso |
|---|---|---|
| `POST` | `/auth/login` | Público; emite o token com os claims |
| `GET` | `/auth/me` | Autenticado; mostra os claims decodificados |
| `POST` | `/auth/logout` | Autenticado; revoga a sessão |
| `GET` | `/documents` | Só documentos do tenant do token |
| `GET` | `/documents/{id}` | Mesmo tenant (senão 404), clearance e departamento compatíveis |
| `POST` | `/documents` | Clearance do autor maior ou igual à exigida pelo documento |
| `DELETE` | `/documents/{id}` | Dono do documento ou `ROLE_ADMIN` do mesmo tenant |
| `GET` | `/admin/users` | `ROLE_ADMIN` com clearance 5, e só do próprio tenant |

## Exemplos de uso

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"tenantSlug":"acme","email":"ana@example.com","password":"secret123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")
```

```bash
curl -s localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"
```

```bash
curl -s localhost:8080/documents -H "Authorization: Bearer $TOKEN"
```

```bash
curl -s localhost:8080/documents/1 -H "Authorization: Bearer $TOKEN"
```

Clearance insuficiente responde 403; documento de outro tenant responde 404:

```bash
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/documents/4 -H "Authorization: Bearer $TOKEN"
```

```bash
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/documents/5 -H "Authorization: Bearer $TOKEN"
```

Depois do logout, o mesmo token deixa de ser aceito:

```bash
curl -s -X POST localhost:8080/auth/logout -H "Authorization: Bearer $TOKEN"
```

## O que não colocar em um JWT

O payload é legível por qualquer um que tenha o token — a assinatura garante que ninguém **alterou** os claims, não que ninguém os **leu**. Ficam de fora senhas e hashes, chaves ou tokens de outros serviços, dados pessoais como CPF ou endereço, e conteúdo de negócio. O critério prático é simples: não coloque no token nada que você não escreveria em um log.

## Testes

```bash
./gradlew test
```

33 testes: 15 unitários, sobre emissão e verificação do token e a matriz de permissões, e 18 de integração contra PostgreSQL e Redis reais em containers.
