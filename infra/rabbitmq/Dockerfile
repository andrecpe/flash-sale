# Imagem do RabbitMQ com configuração e definições (exchange, filas, DLQs, usuário) embutidas.
# Evita bind mount (no Docker for Windows antigo exige "Shared Drives" habilitado).
FROM rabbitmq:4.1-management-alpine
COPY rabbitmq.conf /etc/rabbitmq/rabbitmq.conf
COPY definitions.json /etc/rabbitmq/definitions.json

