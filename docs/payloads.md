# Payloads e contratos

## `POST /v1/orders`

### Request
```json
{
  "produto_id": "PROD-001",
  "quantidade": 2,
  "cliente_cpf": "50329291076",
  "cliente_nome": "Fernando Augusto"
}
```

### Resposta `202 Accepted`
```json
{
  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
  "status": "PROCESSING",
  "data_hora_requisicao": "2026-09-07T20:03:57"
}
```

### Resposta `400 Bad Request`
```json
{
  "mensagem": "Dados do pedido inválidos",
  "erros": [
    "produto_id é obrigatório",
    "quantidade deve ser maior que zero"
  ]
}
```

### Resposta `409 Conflict`
```json
{
  "mensagem": "Estoque insuficiente para o produto PROD-001"
}
```

## `GET /v1/orders/{pedido_id}`

### Resposta `200 OK`
```json
{
  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
  "produto_id": "PROD-001",
  "quantidade": 2,
  "cliente_cpf": "50329291076",
  "cliente_nome": "Fernando Augusto",
  "status": "CONFIRMED",
  "data_hora_atualizacao": "2026-09-07T20:04:10"
}
```

### Resposta `404 Not Found`
```json
{
  "mensagem": "Pedido não encontrado"
}
```

## Mensagem `order-created`
```json
{
  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
  "produto_id": "PROD-001",
  "quantidade": 2,
  "cliente_cpf": "50329291076",
  "cliente_nome": "Fernando Augusto",
  "data_hora_criacao": "2026-09-07T20:03:57"
}
```

## Mensagem `order-compensation`
```json
{
  "pedido_id": "b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
  "produto_id": "PROD-001",
  "quantidade": 2,
  "motivo": "Falha no processamento do pedido"
}
```

## Correlation ID

O `X-Correlation-ID` deve viajar no HTTP e no header AMQP `x-correlation-id`.
Se o cliente não enviar um valor válido, o sistema gera um UUID.

