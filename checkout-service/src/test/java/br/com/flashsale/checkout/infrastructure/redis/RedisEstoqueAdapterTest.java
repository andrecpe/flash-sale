package br.com.flashsale.checkout.infrastructure.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unitário do adaptador (Redis mockado). O script Lua em si é validado contra um Redis real
 * no teste de integração/concorrência (Etapa 9).
 */
class RedisEstoqueAdapterTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valores = mock(ValueOperations.class);
    private final RedisEstoqueAdapter adapter = new RedisEstoqueAdapter(redis);

    @SuppressWarnings("unchecked")
    private void scriptRetorna(Long resultado) {
        when(redis.execute(any(RedisScript.class), eq(List.of("estoque:PROD-001")), eq("2"))).thenReturn(resultado);
    }

    @Test
    @DisplayName("Reserva: script retorna o novo saldo (>= 0) → reservado")
    void reservaComSucesso() {
        scriptRetorna(98L);

        assertThat(adapter.reservar("PROD-001", 2)).isTrue();
    }

    @Test
    @DisplayName("Reserva: saldo insuficiente (-2) → não reservado")
    void reservaSaldoInsuficiente() {
        scriptRetorna(-2L);

        assertThat(adapter.reservar("PROD-001", 2)).isFalse();
    }

    @Test
    @DisplayName("Reserva: produto sem estoque cadastrado (-1) → não reservado")
    void reservaProdutoInexistente() {
        scriptRetorna(-1L);

        assertThat(adapter.reservar("PROD-001", 2)).isFalse();
    }

    @Test
    @DisplayName("Liberação: devolve a quantidade com INCRBY")
    void liberar() {
        when(redis.opsForValue()).thenReturn(valores);

        adapter.liberar("PROD-001", 2);

        verify(valores).increment("estoque:PROD-001", 2);
    }

    @Test
    @DisplayName("Consulta: converte o saldo; produto inexistente → vazio")
    void consultar() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.get("estoque:PROD-001")).thenReturn("100");

        assertThat(adapter.consultar("PROD-001")).contains(100L);
        assertThat(adapter.consultar("PROD-999")).isEmpty();
    }

    @Test
    @DisplayName("Carga inicial: grava só se a chave não existir (SET NX) — não sobrescreve estoque em uso")
    void definirSeAusente() {
        when(redis.opsForValue()).thenReturn(valores);
        when(valores.setIfAbsent("estoque:PROD-001", "100")).thenReturn(true);

        assertThat(adapter.definirSeAusente("PROD-001", 100)).isTrue();
    }

    @SuppressWarnings("unchecked")
    private void scriptCompensacaoRetorna(Long resultado) {
        when(redis.execute(any(RedisScript.class),
                eq(List.of("compensacao:b819cc65-f6f0-478c-8bb7-69ee1c4f6402", "estoque:PROD-001")),
                eq("2"), eq(String.valueOf(RedisEstoqueAdapter.TTL_COMPENSACAO_SEGUNDOS))))
                .thenReturn(resultado);
    }

    @Test
    @DisplayName("Compensação (Etapa 5): script marcou e devolveu (novo saldo) → true")
    void devolverCompensacaoPrimeiraVez() {
        scriptCompensacaoRetorna(100L);

        assertThat(adapter.devolverCompensacao(
                java.util.UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402"), "PROD-001", 2)).isTrue();
    }

    @Test
    @DisplayName("Compensação (Etapa 5): pedido já compensado (-1) → false, nada devolvido")
    void devolverCompensacaoRepetida() {
        scriptCompensacaoRetorna(-1L);

        assertThat(adapter.devolverCompensacao(
                java.util.UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402"), "PROD-001", 2)).isFalse();
    }
}

