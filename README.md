# JWT com claims customizados na autorização

Autenticação e autorização em que o token, **no momento do login**, é enriquecido com dados particulares do usuário — tenant, departamento e nível de acesso — e esses dados são lidos na abertura do token e **usados na decisão de autorização**.

Diferente do [projeto 08](../java-spring-jwt-api-auth), que cobre o básico (registro, login, roles, refresh token). Aqui o foco é a camada acima: **autorização baseada em atributo** (ABAC), isolamento multi-tenant, e as armadilhas de colocar dado de negócio dentro de um token.

## Status

✅ Implementado, testado e validado manualmente ponta a ponta.

## Stack

- Java 21 + Spring Boot 3.5 + Spring Security 6
- jjwt 0.13 (HMAC-SHA512)
- PostgreSQL 16 + JPA + Flyway
- Redis para a lista de revogação de sessão
- Gradle (Kotlin DSL) + wrapper `gradlew`
- Lombok nas entidades; DTOs como records
- Testcontainers + Spring Security Test + JUnit 5

## O token

```json
{
  "iss": "gkcontas-tenant-auth",
  "sub": "ana@example.com",
  "uid": 1,
  "tenantId": 1,
  "tenantSlug": "acme",
  "department": "FINANCE",
  "clearance": 3,
  "role": "ROLE_USER",
  "sid": "7d5f9301-cdbb-4626-927f-50f4bd4be653",
  "iat": 1790684271,
  "exp": 1790685171
}
```

Esse payload foi extraído **sem verificar a assinatura** — só decodificando Base64. É o ponto que abre a lista de armadilhas abaixo.

## A matriz de autorização, medida

Saída real, com quatro usuários em dois tenants:

```
CENARIO                                              HTTP
ana(FIN,3) le doc1 (FIN, req 3)                      200
ana le doc5 (do Globex)                              404  <- outro tenant: 404, nao 403
ana le doc99999 (inexistente)                        404
ana le doc4 (req 5)                                  403  <- clearance insuficiente
bruno(ENG,1) le doc1 (FIN)                           403  <- departamento errado
bruno le doc3 (ENG, req 1)                           200
bruno le doc2 (sem departamento)                     200
admin(FIN,5) le doc3 (ENG) - cruza depto             200
admin le doc5 (Globex)                               404  <- admin nao cruza tenant
ana acessa /admin/users                              403
admin acessa /admin/users                            200
sem token                                            401

LISTAGENS    acme: [1, 2, 3, 4]    globex: [5, 6]
```

Nenhuma dessas decisões veio de uma role. Todas vieram de comparar um **atributo do chamador** com um **atributo do recurso** — que é o que diferencia ABAC de RBAC. Um modelo baseado em role precisaria de uma role por par (departamento, clearance), que é como sistemas chegam a quatrocentas roles que ninguém consegue auditar.

## 404 e não 403: a decisão mais importante do projeto

Pedir um documento de outro tenant responde **404**, idêntico a pedir um id que nunca existiu.

Responder 403 confirmaria que o documento existe — e essa confirmação **é o vazamento**. Percorrer a faixa de ids anotando quais respondem 403 em vez de 404 mapeia os dados de um concorrente sem ler um único documento.

A distinção é feita em camadas diferentes, de propósito:

- **Consulta escopada por tenant** → não encontrou → `404`. O chamador não fica sabendo que existe.
- **`@PostAuthorize` sobre o documento já carregado** → `403`. Seguro, porque a essa altura o documento comprovadamente está no tenant do chamador: ele já sabe que existe e pode pedir acesso.

O mesmo vale para `DELETE`: apagar documento de outro tenant é 404, não 403.

## As armadilhas, todas cobertas por teste

**1. JWT é assinado, não criptografado.** Qualquer um decodifica o payload em Base64 e lê todos os claims. O teste `shouldLeaveThePayloadReadableToAnyoneHoldingTheToken` demonstra isso em vez de avisar. É por isso que não há nome, CPF, e-mail de terceiros nem conteúdo de documento em claim nenhum — só identificadores e atributos de autorização.

**2. Claim é um retrato do passado.** O token diz o que era verdade no login e continua dizendo até expirar. Se o clearance da Ana for rebaixado agora, o token antigo continua valendo. É o trade central: performance (não consultar o banco a cada requisição) contra atualidade (dado obsoleto). Mitigação em duas frentes — TTL de 15 minutos e revogação por sessão.

**3. Isolamento não pode depender de alguém lembrar.** `DocumentRepository` **não tem** um `findById` próprio: todo finder recebe o tenant e tem o tenant no nome. O `findById` herdado do `JpaRepository` não pode ser removido, mas nomear os métodos seguros assim torna a chamada insegura visivelmente diferente no ponto de uso. A defesa correta é sistemática, não por disciplina.

**4. `ThreadLocal` não atravessa thread.** `TenantContext` usa `ThreadLocal`, e isso tem custo declarado: um método `@Async` ou um stream paralelo começa com contexto vazio. Pior, o `remove()` no `finally` do filtro **não é opcional** — valor deixado numa thread de pool é servido para quem pegar aquela thread depois, o que é vazamento entre tenants por uma linha de limpeza faltando.

**5. "JWT não dá para invalidar".** Dá para parar de honrar. Verificado rodando:

```
sessao 1 antes do logout : 200
sessao 2 antes do logout : 200
--- logout na sessao 1 ---
sessao 1 depois          : 401  <- mesmo token, assinatura valida, expiracao futura
sessao 2 depois          : 200  <- outra sessao do mesmo usuario, intacta
```

A revogação é **por sessão**, não por usuário: sair no notebook não desconecta o celular. E a entrada no Redis expira junto com o token (`TTL: 898 segundos`), então a lista se limpa sozinha — sem isso ela cresce para sempre, cheia de sessões cujos tokens expiraram há meses.

O custo precisa ser dito: isso reintroduz uma consulta por requisição, que é exatamente a statelessness pela qual se escolheu JWT. Um `GET` no Redis é barato e o TTL curto mantém a lista pequena, mas é uma troca deliberada, não um almoço grátis.

**6. Tamanho do token.** Todo claim viaja em **toda** requisição, num header. Claims demais estouram limites de header de proxy (8 KB é comum) e custam banda. Os sete claims aqui somam poucas centenas de bytes; uma lista de permissões por recurso não caberia.

**7. `alg: none` e confusão de algoritmo.** O parsing fixa o algoritmo e o emissor. `verifyWith` sobre uma `SecretKey` restringe a verificação a HMAC, e a jjwt recusa `none` de saída. Dois testes cobrem: token forjado com header `alg:none`, e token assinado com outra chave.

**8. `@PreAuthorize` não funciona em chamada interna.** Método anotado chamado de dentro da mesma classe não passa pelo proxy — a anotação é simplesmente ignorada, sem erro. É o mesmo mecanismo de `@Transactional`, e está demonstrado no [catálogo de padrões](../java-spring-design-patterns-catalogo/structural/proxy).

## `@PreAuthorize` x `AuthorizationManager`

Os dois estão no projeto, e a escolha entre eles tem um critério:

- **`@PostAuthorize("hasPermission(returnObject, 'read')")`** — a expressão fica curta porque a regra mora numa classe. O `DocumentPermissionEvaluator` tem 8 testes unitários; nada disso seria alcançável por teste se estivesse dentro da anotação.
- **`AdminAccessAuthorizationManager`** — a regra é `ROLE_ADMIN` **e** clearance ≥ 5. O SpEL equivalente (`hasRole('ADMIN') and principal.clearanceLevel() >= 5`) funciona, e é o momento de parar: string SpEL não é verificada por tipo, não é refatorável, não é depurável e não é testável. Cada condição acrescentada torna a regra mais difícil de verificar exatamente onde errar é mais caro.

A regra de `DELETE` fica em Java puro no serviço, por um terceiro motivo: precisa carregar o documento para saber quem é o dono, **e** precisa distinguir "não é seu" de "não existe" — duas coisas que uma anotação não expressa sem carregar a linha duas vezes ou colapsar as duas respostas numa só.

## Como rodar

```bash
docker compose up -d
```

```bash
./gradlew bootRun
```

Usuários semeados, todos com a senha `secret123`:

| Tenant | E-mail | Departamento | Clearance | Role |
|---|---|---|---|---|
| acme | ana@example.com | FINANCE | 3 | USER |
| acme | bruno@example.com | ENGINEERING | 1 | USER |
| acme | admin@example.com | FINANCE | 5 | ADMIN |
| globex | ana@example.com | FINANCE | 4 | USER |

O mesmo e-mail existe nos dois tenants de propósito — isolamento testado só contra dados que não poderiam colidir não foi testado.

## Como rodar os testes

```bash
./gradlew test
```

33 testes: 15 unitários (emissão e verificação do token, matriz de permissões) e 18 de integração contra PostgreSQL e Redis reais via Testcontainers.

### Nota sobre Testcontainers e Docker Engine recente

Se os testes falharem com `client version 1.32 is too old. Minimum supported API version is 1.40`, a causa é o `docker-java` embutido no Testcontainers negociar a API 1.32, abaixo do mínimo aceito pelo Docker Engine 29+. Correção global, de uma linha:

```bash
echo 'api.version=1.44' > ~/.docker-java.properties
```

## Endpoints

| Método | Rota | Regra |
|--------|------|-------|
| POST | `/auth/login` | pública — emite o token com os claims |
| GET | `/auth/me` | autenticado — mostra os claims decodificados |
| POST | `/auth/logout` | autenticado — revoga a sessão |
| GET | `/documents` | só o tenant do token |
| GET | `/documents/{id}` | mesmo tenant (senão 404) + clearance + departamento |
| POST | `/documents` | clearance do autor ≥ clearance exigida do documento |
| DELETE | `/documents/{id}` | dono ou `ROLE_ADMIN` do mesmo tenant |
| GET | `/admin/users` | `ROLE_ADMIN` **e** clearance 5, e só do próprio tenant |

## Exemplo de uso

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H "Content-Type: application/json" \
  -d '{"tenantSlug":"acme","email":"ana@example.com","password":"secret123"}' \
  | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

# Os claims que o token carrega
curl -s localhost:8080/auth/me -H "Authorization: Bearer $TOKEN"

# Leitura permitida
curl -s localhost:8080/documents/1 -H "Authorization: Bearer $TOKEN"

# Clearance insuficiente -> 403
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/documents/4 -H "Authorization: Bearer $TOKEN"

# Documento de outro tenant -> 404, nao 403
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/documents/5 -H "Authorization: Bearer $TOKEN"

# Logout e o mesmo token deixa de valer
curl -s -X POST localhost:8080/auth/logout -H "Authorization: Bearer $TOKEN"
curl -s -o /dev/null -w "%{http_code}\n" localhost:8080/documents -H "Authorization: Bearer $TOKEN"
```

## O que nunca colocar num JWT

- Senha, hash de senha, chave ou token de outro serviço.
- CPF, cartão, endereço, telefone — dado pessoal em geral.
- Conteúdo de negócio (o documento, o pedido, o valor).
- Qualquer coisa que você não escreveria num log.

O critério é simples: o payload é legível por qualquer um que tenha o token, incluindo o próprio usuário e qualquer proxy no caminho. A assinatura garante que ninguém **alterou** os claims; não garante que ninguém os **leu**.

## Comparação com o projeto 08

| | [08 — jwt-api-auth](../java-spring-jwt-api-auth) | 16 — este |
|---|---|---|
| Foco | registro, login, refresh token | claims próprios governando autorização |
| Autorização | por role (`hasAuthority`) | por atributo (tenant, departamento, clearance) |
| Multi-tenant | não | sim, com 404 em vez de 403 |
| Logout | não (só expiração) | revogação por sessão via Redis |
| Regras | anotação | `PermissionEvaluator` + `AuthorizationManager` + código |

Os dois juntos cobrem o assunto: o 08 mostra como emitir e validar um token; este mostra o que fazer com o que está dentro dele.
