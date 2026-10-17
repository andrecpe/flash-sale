package br.com.flashsale.checkout.infrastructure.redis;

import br.com.flashsale.checkout.application.port.EstoquePort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Estoque no Redis. Chave: {@code estoque:{produto_id}} (valor inteiro).
 * A reserva usa script Lua: leitura + validação + decremento numa única operação atômica,
 * o que impede overselling mesmo com milhares de requisições simultâneas.
 */
@Component
public class RedisEstoqueAdapter implements EstoquePort {

    private static final RedisScript<Long> RESERVAR =
            RedisScript.of(new ClassPathResource("scripts/reserve_stock.lua"), Long.class);
    private static final RedisScript<Long> DEVOLVER_COMPENSACAO =
            RedisScript.of(new ClassPathResource("scripts/release_stock.lua"), Long.class);

    /** Validade da marcação {@code compensacao:{pedido_id}} (7 dias): cobre reentregas e redrive da DLQ. */
    static final long TTL_COMPENSACAO_SEGUNDOS = 7L * 24 * 60 * 60;

    private final StringRedisTemplate redis;

    public RedisEstoqueAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    static String chave(String produtoId) {
        return "estoque:" + produtoId;
    }

    static String chaveCompensacao(UUID pedidoId) {
        return "compensacao:" + pedidoId;
    }

    @Override
    public boolean reservar(String produtoId, int quantidade) {
        Long resultado = redis.execute(RESERVAR, List.of(chave(produtoId)), String.valueOf(quantidade));
        return resultado != null && resultado >= 0;
    }

    @Override
    public void liberar(String produtoId, int quantidade) {
        redis.opsForValue().increment(chave(produtoId), quantidade);
    }

    @Override
    public boolean devolverCompensacao(UUID pedidoId, String produtoId, int quantidade) {
        Long resultado = redis.execute(DEVOLVER_COMPENSACAO,
                List.of(chaveCompensacao(pedidoId), chave(produtoId)),
                String.valueOf(quantidade), String.valueOf(TTL_COMPENSACAO_SEGUNDOS));
        return resultado != null && resultado >= 0;
    }

    @Override
    public Optional<Long> consultar(String produtoId) {
        return Optional.ofNullable(redis.opsForValue().get(chave(produtoId))).map(Long::valueOf);
    }

    /** Carga inicial: SET NX — não sobrescreve o saldo de um estoque já em uso (ex.: restart do serviço). */
    public boolean definirSeAusente(String produtoId, long quantidade) {
        return Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(chave(produtoId), String.valueOf(quantidade)));
    }
}

