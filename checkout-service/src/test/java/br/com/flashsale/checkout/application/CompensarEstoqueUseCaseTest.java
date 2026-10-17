package br.com.flashsale.checkout.application;

import br.com.flashsale.checkout.application.port.CompensacaoRepositoryPort;
import br.com.flashsale.checkout.application.port.EstoquePort;
import br.com.flashsale.checkout.application.port.PedidoCachePort;
import br.com.flashsale.checkout.application.port.PedidoMetricasPort;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Etapa 5 (TDD): compensação da SAGA — devolve o estoque uma única vez por pedido. */
@ExtendWith(MockitoExtension.class)
class CompensarEstoqueUseCaseTest {

    private static final UUID PEDIDO = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");
    private static final String MOTIVO = "Falha no processamento do pedido";

    @Mock
    private EstoquePort estoque;
    @Mock
    private CompensacaoRepositoryPort repositorio;
    @Mock
    private PedidoCachePort cache;
    @Mock
    private PedidoMetricasPort metricas;

    private CompensarEstoqueUseCase useCase;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:04:10.789Z"), ZoneId.of("America/Sao_Paulo"));
        useCase = new CompensarEstoqueUseCase(estoque, repositorio, cache, metricas, clock);
    }

    private static CompensarEstoqueCommand comando(UUID pedidoId, String produtoId, Integer quantidade) {
        return new CompensarEstoqueCommand(pedidoId, produtoId, quantidade, MOTIVO, "demo-etapa5-0001");
    }

    @Test
    @DisplayName("Primeira compensação: devolve o estoque, registra a compensação e invalida pedido:{id}")
    void primeiraCompensacao() {
        when(estoque.devolverCompensacao(PEDIDO, "PROD-001", 2)).thenReturn(true);

        ResultadoCompensacao resultado = useCase.executar(comando(PEDIDO, "PROD-001", 2));

        assertThat(resultado).isEqualTo(ResultadoCompensacao.COMPENSADO);
        InOrder ordem = inOrder(estoque, repositorio, cache);
        ordem.verify(estoque).devolverCompensacao(PEDIDO, "PROD-001", 2);
        ArgumentCaptor<Compensacao> registro = ArgumentCaptor.forClass(Compensacao.class);
        ordem.verify(repositorio).registrar(registro.capture());
        ordem.verify(cache).invalidar(PEDIDO);

        Compensacao c = registro.getValue();
        assertThat(c.pedidoId()).isEqualTo(PEDIDO);
        assertThat(c.produtoId()).isEqualTo("PROD-001");
        assertThat(c.quantidade()).isEqualTo(2);
        assertThat(c.motivo()).isEqualTo(MOTIVO);
        assertThat(c.correlationId()).isEqualTo("demo-etapa5-0001");
        assertThat(c.dataHoraCompensacao()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 4, 10));
        verify(metricas).compensacaoConcluida();
    }

    @Test
    @DisplayName("Compensação repetida: não devolve o estoque de novo (idempotente) e garante o registro")
    void compensacaoRepetida() {
        when(estoque.devolverCompensacao(PEDIDO, "PROD-001", 2)).thenReturn(false);

        ResultadoCompensacao resultado = useCase.executar(comando(PEDIDO, "PROD-001", 2));

        assertThat(resultado).isEqualTo(ResultadoCompensacao.IGNORADO);
        // registro idempotente (ON CONFLICT DO NOTHING): recupera uma execução anterior interrompida
        verify(repositorio).registrar(any());
        verify(cache).invalidar(PEDIDO);
        verifyNoInteractions(metricas); // a repetição não conta como nova compensação
    }

    @Test
    @DisplayName("Mensagem sem produto_id: erro (DLQ) sem tocar no estoque, banco ou cache")
    void semProduto() {
        assertThatThrownBy(() -> useCase.executar(comando(PEDIDO, " ", 2)))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("produto_id");

        verifyNoInteractions(estoque, repositorio, cache);
    }

    @Test
    @DisplayName("Mensagem sem quantidade (ou <= 0): erro (DLQ) sem tocar no estoque")
    void semQuantidade() {
        assertThatThrownBy(() -> useCase.executar(comando(PEDIDO, "PROD-001", null)))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("quantidade");
        assertThatThrownBy(() -> useCase.executar(comando(PEDIDO, "PROD-001", 0)))
                .isInstanceOf(MensagemInvalidaException.class);

        verifyNoInteractions(estoque, repositorio, cache);
    }

    @Test
    @DisplayName("Mensagem sem pedido_id: erro (sem ele não há idempotência)")
    void semPedido() {
        assertThatThrownBy(() -> useCase.executar(comando(null, "PROD-001", 2)))
                .isInstanceOf(MensagemInvalidaException.class)
                .hasMessageContaining("pedido_id");

        verifyNoInteractions(estoque, repositorio, cache);
    }

    @Test
    @DisplayName("Erro ao registrar no banco propaga (mensagem vai para a DLQ; redrive não devolve o estoque de novo)")
    void erroAoRegistrar() {
        when(estoque.devolverCompensacao(PEDIDO, "PROD-001", 2)).thenReturn(true);
        doThrow(new IllegalStateException("banco fora")).when(repositorio).registrar(any());

        assertThatThrownBy(() -> useCase.executar(comando(PEDIDO, "PROD-001", 2)))
                .isInstanceOf(IllegalStateException.class);
    }
}

