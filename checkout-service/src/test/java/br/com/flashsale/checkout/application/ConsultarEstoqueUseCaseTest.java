package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.EstoquePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConsultarEstoqueUseCaseTest {

    private final EstoquePort estoque = mock(EstoquePort.class);
    private final ConsultarEstoqueUseCase useCase = new ConsultarEstoqueUseCase(estoque);

    @Test
    @DisplayName("Produto com estoque cadastrado: retorna o saldo")
    void produtoExistente() {
        when(estoque.consultar("PROD-001")).thenReturn(Optional.of(100L));

        assertThat(useCase.executar("PROD-001")).contains(100L);
    }

    @Test
    @DisplayName("Produto sem estoque cadastrado: vazio")
    void produtoInexistente() {
        when(estoque.consultar("PROD-999")).thenReturn(Optional.empty());

        assertThat(useCase.executar("PROD-999")).isEmpty();
    }
}

