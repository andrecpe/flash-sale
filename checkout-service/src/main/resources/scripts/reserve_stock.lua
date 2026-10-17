-- Reserva atômica de estoque (Redis executa o script inteiro sem intercalar outros comandos).
-- KEYS[1] = estoque:{produto_id}
-- ARGV[1] = quantidade a reservar
-- Retorno: novo saldo (>= 0) | -1 produto sem estoque cadastrado | -2 saldo insuficiente
local saldo = redis.call('GET', KEYS[1])
if not saldo then
    return -1
end
local quantidade = tonumber(ARGV[1])
if tonumber(saldo) < quantidade then
    return -2
end
return redis.call('DECRBY', KEYS[1], quantidade)

