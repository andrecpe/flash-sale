#!/bin/sh
# Executado pelo entrypoint oficial do PostgreSQL apenas na PRIMEIRA subida (volume vazio).
# Cria um database e um usuário dedicados para cada microsserviço (banco segregado).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER checkout_user WITH PASSWORD '${CHECKOUT_DB_PASSWORD}';
    CREATE DATABASE checkout_db OWNER checkout_user;
    REVOKE ALL ON DATABASE checkout_db FROM PUBLIC;

    CREATE USER order_user WITH PASSWORD '${ORDER_DB_PASSWORD}';
    CREATE DATABASE order_db OWNER order_user;
    REVOKE ALL ON DATABASE order_db FROM PUBLIC;
EOSQL

echo "Databases checkout_db e order_db criados com usuários segregados."

