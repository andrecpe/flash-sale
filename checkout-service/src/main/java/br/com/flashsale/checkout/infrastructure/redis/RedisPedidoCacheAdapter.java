package br.com.flashsale.checkout.infrastructure.redis;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.domain.StatusPedido;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache de pedidos no Redis. Chave: {@code pedido:{pedido_id}}; valor: JSON no formato do enunciado; TTL na gravação.
 * Valor ilegível (ex.: formato antigo após deploy) é tratado como miss e removido.
 */
@Component
public class RedisPedidoCacheAdapter implements PedidoCachePort {

    private static final Logger log = LoggerFactory.getLogger(RedisPedidoCacheAdapter.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisPedidoCacheAdapter(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    static String chave(UUID pedidoId) {
        return "pedido:" + pedidoId;
    }

    @Override
    public Optional<PedidoDetalhe> buscar(UUID pedidoId) {
        String json = redis.opsForValue().get(chave(pedidoId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, PedidoCacheado.class).paraDetalhe());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Valor ilegível no cache para o pedido {}; removido", pedidoId);
            redis.delete(chave(pedidoId));
            return Optional.empty();
        }
    }

    @Override
    public void gravar(PedidoDetalhe pedido, Duration ttl) {
        try {
            redis.opsForValue().set(chave(pedido.pedidoId()),
                    objectMapper.writeValueAsString(PedidoCacheado.de(pedido)), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o pedido para o cache", e);
        }
    }

    @Override
    public void invalidar(UUID pedidoId) {
        redis.delete(chave(pedidoId));
    }

    /** Formato do valor no Redis (independe do modelo da aplicação). */
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    @JsonIgnoreProperties(ignoreUnknown = true)
    record PedidoCacheado(
            UUID pedidoId,
            String produtoId,
            int quantidade,
            String clienteCpf,
            String clienteNome,
            StatusPedido status,
            @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime dataHoraAtualizacao) {

        static PedidoCacheado de(PedidoDetalhe p) {
            return new PedidoCacheado(p.pedidoId(), p.produtoId(), p.quantidade(), p.clienteCpf(),
                    p.clienteNome(), p.status(), p.dataHoraAtualizacao());
        }

        PedidoDetalhe paraDetalhe() {
            return new PedidoDetalhe(pedidoId, produtoId, quantidade, clienteCpf, clienteNome, status,
                    dataHoraAtualizacao);
        }
    }
}

