# Imagem do PostgreSQL com o script de criação dos databases embutido.
# Evita bind mount (no Docker for Windows antigo exige "Shared Drives" habilitado).
FROM postgres:16-alpine
COPY 01-init-databases.sh /docker-entrypoint-initdb.d/01-init-databases.sh

