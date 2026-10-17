#!/usr/bin/env bash
set -euo pipefail

jav 21 -Silencioso
cd "$(dirname "$0")/.."

BASE_URL="${FLASHSALE_BASE_URL:-http://localhost:8080}"

json_get() {
  curl -s "$1"
}

json_post() {
  local cid="$1"
  local body="$2"
  curl -s -H "Content-Type: application/json" -H "X-Correlation-ID: ${cid}" -X POST "$BASE_URL/v1/orders" --data "$body"
}

extract_id() {
  sed -E 's/.*"pedido_id":"([^"]+)".*/\1/'
}

echo '=== Estoque antes ==='
json_get "$BASE_URL/v1/products/PROD-001/stock"

echo '=== Pedido de sucesso ==='
SUCESSO_JSON=$(json_post 'demo-script-sucesso-0001' '{"produto_id":"PROD-001","quantidade":2,"cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto"}')
PEDIDO_SUCESSO_ID=$(printf '%s' "$SUCESSO_JSON" | extract_id)
sleep 2
json_get "$BASE_URL/v1/orders/$PEDIDO_SUCESSO_ID"

echo '=== Estoque após sucesso ==='
json_get "$BASE_URL/v1/products/PROD-001/stock"

echo '=== Pedido com falha e compensação ==='
FALHA_JSON=$(json_post 'demo-script-falha-0002' '{"produto_id":"PROD-001","quantidade":2,"cliente_cpf":"50329291076","cliente_nome":"Cliente FALHA Demo"}')
PEDIDO_FALHA_ID=$(printf '%s' "$FALHA_JSON" | extract_id)
sleep 3
json_get "$BASE_URL/v1/orders/$PEDIDO_FALHA_ID"

echo '=== Estoque após compensação ==='
json_get "$BASE_URL/v1/products/PROD-001/stock"

echo '=== Requisição inválida (400) ==='
curl -i -X POST "$BASE_URL/v1/orders" -H 'Content-Type: application/json' -H 'X-Correlation-ID: demo-script-invalido-0003' --data '{"produto_id":"PROD-001","quantidade":0,"cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto"}'

echo '=== Estoque insuficiente (409) ==='
curl -i -X POST "$BASE_URL/v1/orders" -H 'Content-Type: application/json' -H 'X-Correlation-ID: demo-script-estoque-0004' --data '{"produto_id":"PROD-002","quantidade":999,"cliente_cpf":"50329291076","cliente_nome":"Fernando Augusto"}'

