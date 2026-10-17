# ADR 003 — Testes de integração com Testcontainers

## Contexto
Os fluxos críticos precisam ser validados com Redis, RabbitMQ e PostgreSQL reais, sem depender de mocks.

## Decisão
Executar os testes de integração da Etapa 9 com **Testcontainers** e `@ServiceConnection`.

- PostgreSQL real para os dois serviços
- RabbitMQ real com filas, bindings e DLQs
- Redis real para reserva/cache-aside
- Execução no `verify`, mantendo `test` rápido para os unitários
- `seccomp=unconfined` nos containers de teste por causa do Docker 18.06 local

## Consequências
- A suíte de integração fica mais fiel ao ambiente real.
- O build exige Docker em execução.
- Os testes ficam mais lentos, mas cobrem o contrato end-to-end da solução.

