package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.EstoquePort;
import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
import br.com.flashsale.checkout.application.port.PedidoPublisherPort;
import br.com.flashsale.checkout.domain.Pedido;
import br.com.flashsale.checkout.domain.PedidoInvalidoException;
import br.com.flashsale.checkout.domain.StatusPedido;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CriarPedidoUseCaseTest {

    @Mock
    private EstoquePort estoque;
    @Mock
    private PedidoPublisherPort publisher;
    @Mock
    private PedidoMetricasPort metricas;

    private CriarPedidoUseCase useCase;

    private final CriarPedidoCommand comando =
            new CriarPedidoCommand("PROD-001", 2, "50329291076", "Fernando Augusto");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:03:57Z"), ZoneId.of("America/Sao_Paulo"));
        useCase = new CriarPedidoUseCase(estoque, publisher, metricas, clock);
    }

    @Test
    @DisplayName("Estoque suficiente: reserva, publica o pedido e retorna PROCESSING")
    void estoqueSuficiente() {
        when(estoque.reservar("PROD-001", 2)).thenReturn(true);

        Pedido pedido = useCase.executar(comando);

        assertThat(pedido.status()).isEqualTo(StatusPedido.PROCESSING);
        ArgumentCaptor<Pedido> publicado = ArgumentCaptor.forClass(Pedido.class);
        InOrder ordem = inOrder(estoque, publisher);
        ordem.verify(estoque).reservar("PROD-001", 2);
        ordem.verify(publisher).publicarPedidoCriado(publicado.capture());
        assertThat(publicado.getValue()).isSameAs(pedido);
        verify(estoque, never()).liberar(anyString(), anyInt());
        verify(metricas).pedidoCriado();
        verify(metricas, never()).pedidoRejeitadoPorEstoque();
    }

    @Test
    @DisplayName("Estoque insuficiente: lança EstoqueInsuficienteException, NÃO publica e conta a rejeição")
    void estoqueInsuficiente() {
        when(estoque.reservar("PROD-001", 2)).thenReturn(false);

        assertThatThrownBy(() -> useCase.executar(comando))
                .isInstanceOf(EstoqueInsuficienteException.class)
                .hasMessage("Estoque insuficiente para o produto PROD-001");
        verifyNoInteractions(publisher);
        verify(metricas).pedidoRejeitadoPorEstoque();
        verify(metricas, never()).pedidoCriado();
    }

    @Test
    @DisplayName("Falha ao publicar: devolve o estoque reservado, lança PublicacaoIndisponivelException e não conta pedido criado")
    void falhaAoPublicarDevolveEstoque() {
        when(estoque.reservar("PROD-001", 2)).thenReturn(true);
        doThrow(new PublicacaoIndisponivelException("broker fora", null)).when(publisher).publicarPedidoCriado(any());

        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(PublicacaoIndisponivelException.class);
        verify(estoque).liberar("PROD-001", 2);
        verifyNoInteractions(metricas);
    }

    @Test
    @DisplayName("Erro inesperado ao publicar: também devolve o estoque (nenhuma reserva fica órfã)")
    void erroInesperadoDevolveEstoque() {
        when(estoque.reservar("PROD-001", 2)).thenReturn(true);
        doThrow(new IllegalStateException("inesperado")).when(publisher).publicarPedidoCriado(any());

        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(PublicacaoIndisponivelException.class);
        verify(estoque).liberar("PROD-001", 2);
    }

    @Test
    @DisplayName("Dados inválidos: não toca no estoque nem publica")
    void dadosInvalidos() {
        CriarPedidoCommand invalido = new CriarPedidoCommand("PROD-001", 0, "50329291076", null);

        assertThatThrownBy(() -> useCase.executar(invalido)).isInstanceOf(PedidoInvalidoException.class);
        verifyNoInteractions(estoque, publisher, metricas);
    }
}

