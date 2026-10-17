# ADR 001 — Correlation ID de negócio

## Contexto
A solução precisa rastrear a mesma transação de negócio entre gateway, checkout, fila AMQP, order processor, logs e resposta HTTP.

## Decisão
Adotar um **Correlation ID de negócio** separado do `trace_id` técnico do OpenTelemetry.

- Header HTTP: `X-Correlation-ID`
- Header AMQP: `x-correlation-id`
- MDC: `correlationId`
- Formato válido: `^[A-Za-z0-9_-]{8,64}$`
- Se o cliente não enviar um ID válido, o sistema gera um UUID.

## Consequências
- Suporte consegue localizar a transação inteira usando um único identificador.
- O ID sobrevive a publicação em fila, consumo, retry e compensação.
- Os logs permanecem sem dados pessoais; apenas o identificador de negócio é carregado no MDC.

