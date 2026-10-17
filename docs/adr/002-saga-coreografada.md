# ADR 002 — SAGA coreografada com compensação

## Contexto
A reserva de estoque acontece no Checkout, enquanto a confirmação/cancelamento do pedido acontece no Order Processor. Em caso de falha, o estoque precisa ser devolvido.

## Decisão
Usar **SAGA coreografada** com duas filas principais:

- `order-created`: pedido criado no Checkout e consumido pelo Order Processor.
- `order-compensation`: compensação enviada pelo Order Processor quando o pedido falha.

Fluxo resumido:
1. Checkout reserva o estoque no Redis.
2. Checkout publica `order-created`.
3. Order Processor persiste o pedido no PostgreSQL.
4. Em caso de falha, Order Processor publica `order-compensation`.
5. Checkout consome a compensação e devolve o estoque.

## Consequências
- Baixo acoplamento entre os serviços.
- Consistência eventual, com necessidade de idempotência nos consumidores.
- Falhas de infraestrutura vão para DLQ para evitar loop infinito de reentrega.

