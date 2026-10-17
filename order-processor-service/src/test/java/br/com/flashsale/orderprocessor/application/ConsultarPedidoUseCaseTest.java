package br.com.flashsale.orderprocessor.application;

import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.domain.Pedido;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsultarPedidoUseCaseTest {

    @Mock
    private PedidoRepositoryPort repositorio;

    @Test
    @DisplayName("Retorna o pedido quando existe")
    void encontrado() {
        UUID id = UUID.randomUUID();
        Pedido pedido = Pedido.confirmado(id, "PROD-001", 2, "50329291076", "Fernando",
                LocalDateTime.parse("2026-09-07T20:03:57"), LocalDateTime.parse("2026-09-07T20:04:10"), "cid-12345678");
        when(repositorio.buscar(id)).thenReturn(Optional.of(pedido));

        assertThat(new ConsultarPedidoUseCase(repositorio).executar(id)).contains(pedido);
    }

    @Test
    @DisplayName("Vazio quando o pedido não existe")
    void naoEncontrado() {
        UUID id = UUID.randomUUID();
        when(repositorio.buscar(id)).thenReturn(Optional.empty());

        assertThat(new ConsultarPedidoUseCase(repositorio).executar(id)).isEmpty();
    }
}

