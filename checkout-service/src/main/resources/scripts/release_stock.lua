-- Compensação idempotente (Redis executa o script inteiro sem intercalar outros comandos).
-- KEYS[1] = compensacao:{pedido_id}  (marcação de idempotência)
-- KEYS[2] = estoque:{produto_id}
-- ARGV[1] = quantidade a devolver
-- ARGV[2] = TTL da marcação, em segundos
-- Retorno: novo saldo (>= 0) | -1 pedido já compensado (nada devolvido)
if not redis.call('SET', KEYS[1], ARGV[1], 'NX', 'EX', tonumber(ARGV[2])) then
    return -1
end
return redis.call('INCRBY', KEYS[2], tonumber(ARGV[1]))

