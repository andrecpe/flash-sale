package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoConsultaPort;
import br.com.flashsale.checkout.domain.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Etapa 6 (TDD): GET /v1/orders/{id} com cache-aside (Redis) e 3 tentativas antes do 404. */
@ExtendWith(MockitoExtension.class)
class ConsultarPedidoUseCaseTest {

    private static final UUID ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final Duration TTL = Duration.ofSeconds(300);
    private static final PedidoDetalhe PEDIDO = new PedidoDetalhe(ID, "PROD-001", 2, "50329291076",
            "Fernando Augusto", StatusPedido.CONFIRMED, LocalDateTime.of(2026, 9, 7, 20, 4, 10));

    @Mock
    private PedidoCachePort cache;
    @Mock
    private PedidoConsultaPort consulta;

    private final List<Duration> pausas = new ArrayList<>();
    private ConsultarPedidoUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConsultarPedidoUseCase(cache, consulta,
                new PoliticaConsulta(3, Duration.ofMillis(200), TTL), pausas::add);
    }

    @Test
    @DisplayName("Cache hit: retorna do Redis sem chamar o Order Processor")
    void cacheHit() {
        when(cache.buscar(ID)).thenReturn(Optional.of(PEDIDO));

        assertThat(useCase.executar(ID)).isEqualTo(PEDIDO);

        verifyNoInteractions(consulta);
        verify(cache, never()).gravar(any(), any());
    }

    @Test
    @DisplayName("Cache miss + encontrado: retorna e grava no cache com TTL de 300 s")
    void cacheMissEncontrado() {
        when(cache.buscar(ID)).thenReturn(Optional.empty());
        when(consulta.buscar(ID)).thenReturn(Optional.of(PEDIDO));

        assertThat(useCase.executar(ID)).isEqualTo(PEDIDO);

        verify(cache).gravar(PEDIDO, TTL);
        assertThat(pausas).isEmpty();
    }

    @Test
    @DisplayName("Não encontrado: exatamente 3 tentativas (backoff 200 ms, 400 ms) e PedidoNaoEncontradoException")
    void naoEncontrado() {
        when(cache.buscar(ID)).thenReturn(Optional.empty());
        when(consulta.buscar(ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.executar(ID))
                .isInstanceOf(PedidoNaoEncontradoException.class)
                .hasMessage("Pedido não encontrado");

        verify(consulta, times(3)).buscar(ID);
        assertThat(pausas).containsExactly(Duration.ofMillis(200), Duration.ofMillis(400));
        verify(cache, never()).gravar(any(), any());
    }

    @Test
    @DisplayName("Encontrado na 2ª tentativa (pedido ainda na fila): retorna e grava no cache")
    void encontradoNaSegundaTentativa() {
        when(cache.buscar(ID)).thenReturn(Optional.empty());
        when(consulta.buscar(ID)).thenReturn(Optional.empty(), Optional.of(PEDIDO));

        assertThat(useCase.executar(ID)).isEqualTo(PEDIDO);

        verify(consulta, times(2)).buscar(ID);
        assertThat(pausas).containsExactly(Duration.ofMillis(200));
        verify(cache).gravar(PEDIDO, TTL);
    }

    @Test
    @DisplayName("Order Processor indisponível (circuito aberto/timeout): ServicoIndisponivelException sem retentar")
    void indisponivel() {
        when(cache.buscar(ID)).thenReturn(Optional.empty());
        when(consulta.buscar(ID)).thenThrow(new ServicoIndisponivelException("circuito aberto", null));

        assertThatThrownBy(() -> useCase.executar(ID)).isInstanceOf(ServicoIndisponivelException.class);

        verify(consulta, times(1)).buscar(ID);
        assertThat(pausas).isEmpty();
        verify(cache, never()).gravar(any(), any());
    }

    @Test
    @DisplayName("Redis fora na leitura: o cache é otimização — consulta o Order Processor e responde")
    void redisForaNaLeitura() {
        when(cache.buscar(ID)).thenThrow(new IllegalStateException("redis fora"));
        when(consulta.buscar(ID)).thenReturn(Optional.of(PEDIDO));

        assertThat(useCase.executar(ID)).isEqualTo(PEDIDO);
    }

    @Test
    @DisplayName("Redis fora na gravação: responde mesmo assim")
    void redisForaNaGravacao() {
        when(cache.buscar(ID)).thenReturn(Optional.empty());
        when(consulta.buscar(ID)).thenReturn(Optional.of(PEDIDO));
        doThrow(new IllegalStateException("redis fora")).when(cache).gravar(PEDIDO, TTL);

        assertThat(useCase.executar(ID)).isEqualTo(PEDIDO);
    }

    @Test
    @DisplayName("toString do pedido não expõe CPF nem nome (dados pessoais fora dos logs)")
    void toStringSemDadosPessoais() {
        assertThat(PEDIDO.toString()).doesNotContain("50329291076").doesNotContain("Fernando");
    }

    @Test
    @DisplayName("Política inválida (tentativas < 1) falha na configuração")
    void politicaInvalida() {
        assertThatThrownBy(() -> new PoliticaConsulta(0, Duration.ofMillis(200), TTL))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

