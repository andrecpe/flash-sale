# Flash Sale — Checkout, Order Processor e Black Friday

Microsserviços Java 21 / Spring Boot 3.5 com Redis, RabbitMQ, PostgreSQL, SAGA, cache-aside, DLQ e OpenTelemetry.

> Status: documentação final pronta; `mvnw verify` validado em cópia limpa (sem `target/`). O planejamento detalhado fica em `PLANEJAMENTO.md` (na raiz do workspace).

## Pré-requisitos

- Docker 18.06+ com **pelo menos 4 GB de memória**.
- JDK 21 apenas para rodar testes e scripts.
- Maven Wrapper (`mvnw` / `mvnw.cmd`).

## Subir o ambiente

```powershell
docker compose up -d --build
# ou, no Docker antigo:
docker-compose up -d --build
```

| Componente | URL | Observação |
|---|---|---|
| API Gateway | http://localhost:8080 | ponto único de entrada |
| Checkout | http://localhost:8081 | exposto com `docker-compose.override.yml` |
| Order Processor | http://localhost:8082 | exposto com `docker-compose.override.yml` |
| RabbitMQ UI | http://localhost:15672 | `flashsale` / `flashsale` |
| Grafana (traces/métricas/logs) | http://localhost:3000 | `admin` / `admin` |

## Testes

### Unitários e integração

```powershell
jav 21 -Silencioso
cd flash-sale
.\mvnw.cmd -q test
.\mvnw.cmd -q verify
```

### Scripts prontos

- `scripts/test.ps1` / `scripts/test.sh` — executam `verify`.
- `scripts/demo.ps1` / `scripts/demo.sh` — demonstram sucesso, falha, compensação e estoque antes/depois.

## Roteiro de demonstração

1. `GET /actuator/health` no gateway.
2. `GET /actuator/health/readiness` no Checkout e no Order Processor.
3. Consultar estoque inicial.
4. Fazer um `POST /v1/orders` aceito (`202`).
5. Consultar o pedido processado.
6. Confirmar que o estoque caiu.
7. Fazer um `POST /v1/orders` com falha no processamento.
8. Consultar o pedido cancelado.
9. Confirmar a publicação de `order-compensation`.
10. Confirmar a devolução do estoque.
11. Enviar payload inválido (`400`).
12. Enviar pedido acima do estoque (`409`).

Os exemplos estão em `http/requests.http`.

## API

### `POST /v1/orders`

Request:

```json
{
  "produto_id": "PROD-001",
  "quantidade": 2,
  "cliente_cpf": "50329291076",
  "cliente_nome": "Fernando Augusto"
}
```

Respostas principais:

- `202 Accepted`: estoque reservado e mensagem publicada em `order-created`.
- `400 Bad Request`: payload inválido.
- `409 Conflict`: estoque insuficiente.
- `503 Service Unavailable`: RabbitMQ não confirmou a publicação; a reserva é desfeita.

### `GET /v1/orders/{pedido_id}`

- Usa **cache-aside** no Redis.
- Faz até 3 tentativas antes de devolver `404`.

### `GET /v1/products/{produto_id}/stock`

Consulta o estoque atual do produto.

## Correlation ID

O `X-Correlation-ID` identifica a transação de negócio de ponta a ponta:

- se o cliente enviar um valor válido, ele é preservado;
- caso contrário, o gateway gera um UUID;
- o valor segue no HTTP e nas mensagens AMQP via `x-correlation-id`;
- os logs exibem o ID junto com `trace_id` / `span_id`.

## Documentação complementar

- `docs/adr/001-correlation-id.md`
- `docs/adr/002-saga-coreografada.md`
- `docs/adr/003-testcontainers-etapa9.md`
- `docs/payloads.md`
- `docs/sequencias.md`

## Estrutura

```text
api-gateway/               Spring Cloud Gateway
checkout-service/          reserva de estoque, cache-aside e compensação
order-processor-service/   consumo de pedidos e publicação de compensação
docs/                      ADRs, payloads e diagramas
http/                      roteiro de requests da demo
scripts/                   testes e demo automatizada
infra/                     imagens do PostgreSQL e do RabbitMQ
```

## Troubleshooting

- Se aparecer `Operation not permitted` nos containers, o Docker é antigo: os containers de teste já usam `seccomp=unconfined`.
- Se o Maven tentar usar Java 17, rode `jav 21` antes de `mvnw`.
- Se o Docker bloquear pull em rede corporativa, use a rede fora do proxy ou a imagem já baixada localmente.
- Para limpar o estado da demo: `docker compose down -v`.

## Checklist MVP — onde está implementado

- Dois microsserviços: `checkout-service` e `order-processor-service`.
- `POST /v1/orders`: `checkout-service`.
- Redis para reservar estoque: `checkout-service`.
- Fila `order-created`: `checkout-service` → `order-processor-service`.
- Persistência no Order Processor: `order-processor-service` + PostgreSQL.
- `order-compensation`: Order Processor → Checkout.
- `GET /v1/orders/{pedido_id}` e cache-aside: `checkout-service`.
- Logs, metrics e tracing: `api-gateway`, `checkout-service`, `order-processor-service`.
- Testes de integração e concorrência: `checkout-service` e `order-processor-service`.
- Documentação e roteiro: `docs/`, `http/`, `scripts/`.

