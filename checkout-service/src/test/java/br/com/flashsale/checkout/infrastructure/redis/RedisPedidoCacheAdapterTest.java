package br.com.flashsale.checkout.infrastructure.redis;

import br.com.flashsale.checkout.application.PedidoDetalhe;
import br.com.flashsale.checkout.domain.StatusPedido;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Etapas 5 e 6 (TDD): cache de pedidos {@code pedido:{id}} no Redis (JSON + TTL). */
class RedisPedidoCacheAdapterTest {

    private static final UUID ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final String CHAVE = "pedido:b819cc65-f6f0-478c-8bb7-69ee1c4f6402";
    private static final PedidoDetalhe PEDIDO = new PedidoDetalhe(ID, "PROD-001", 2, "50329291076",
            "Fernando Augusto", StatusPedido.CONFIRMED, LocalDateTime.of(2026, 9, 7, 20, 4, 10));

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final RedisPedidoCacheAdapter adapter = new RedisPedidoCacheAdapter(redis, mapper);

    @Test
    @DisplayName("Invalidar: remove a chave pedido:{id}")
    void invalidar() {
        adapter.invalidar(ID);

        verify(redis).delete(CHAVE);
    }

    @Test
    @DisplayName("Gravar: JSON snake_case com data ISO sem fuso e TTL informado")
    void gravar() {
        when(redis.opsForValue()).thenReturn(valores);

        adapter.gravar(PEDIDO, Duration.ofSeconds(300));

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq(CHAVE), json.capture(), eq(Duration.ofSeconds(300)));
        assertThat(json.getValue())
                .contains("\"pedido_id\":\"b819cc65-f6f0-478c-8bb7-69ee1c4f6402\"")
                .contains("\"status\":\"CONFIRMED\"")
                .contains("\"data_hora_atualizacao\":\"2026-09-07T20:04:10\"");
    }

    @Test
    @DisplayName("Buscar: hit converte o JSON gravado (ida e volta); miss → vazio")
    void buscar() {
        when(redis.opsForValue()).thenReturn(valores);
        adapter.gravar(PEDIDO, Duration.ofSeconds(300));
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valores).set(eq(CHAVE), json.capture(), eq(Duration.ofSeconds(300)));
        when(valores.get(CHAVE)).thenReturn(json.getValue());

        assertThat(adapter.buscar(ID)).contains(PEDIDO);
        assertThat(adapter.buscar(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("Buscar: valor corrompido no cache é tratado como miss (e removido)")
    void valorCorrompido() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get(CHAVE)).thenReturn("{ quebrado");

        assertThat(adapter.buscar(ID)).isEmpty();
        verify(redis).delete(CHAVE);
    }
}

