package br.com.flashsale.orderprocessor.application;

import br.com.flashsale.orderprocessor.application.port.CompensacaoPublisherPort;
import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.application.port.ProcessamentoMetricasPort;
import br.com.flashsale.orderprocessor.domain.FalhaSimulador;
import br.com.flashsale.orderprocessor.domain.Pedido;
import br.com.flashsale.orderprocessor.domain.StatusPedido;
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
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Etapa 4 (TDD): processamento do pedido consumido de order-created. */
@ExtendWith(MockitoExtension.class)
class ProcessarPedidoUseCaseTest {

    private static final UUID PEDIDO_ID = UUID.fromString("b819cc65-f6f0-478c-8bb7-69ee1c4f6402");

    @Mock
    private PedidoRepositoryPort repositorio;
    @Mock
    private CompensacaoPublisherPort publisher;
    @Mock
    private FalhaSimulador simulador;
    @Mock
    private ProcessamentoMetricasPort metricas;

    private ProcessarPedidoUseCase useCase;

    private final ProcessarPedidoCommand comando = new ProcessarPedidoCommand(
            PEDIDO_ID, "PROD-001", 2, "50329291076", "Fernando Augusto",
            LocalDateTime.parse("2026-09-07T20:03:57"), "demo-etapa4-0001");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:04:10.123Z"), ZoneId.of("America/Sao_Paulo"));
        useCase = new ProcessarPedidoUseCase(repositorio, publisher, simulador, metricas, clock);
    }

    @Test
    @DisplayName("Sucesso: salva CONFIRMED, não publica compensação e conta pedido confirmado")
    void sucesso() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(false);
        when(simulador.deveFalhar("Fernando Augusto")).thenReturn(false);
        when(repositorio.salvar(any())).thenReturn(true);

        ResultadoProcessamento resultado = useCase.executar(comando);

        assertThat(resultado).isEqualTo(ResultadoProcessamento.CONFIRMADO);
        verify(metricas).pedidoConfirmado();
        verify(metricas, never()).pedidoCancelado();
        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        verify(repositorio).salvar(salvo.capture());
        Pedido pedido = salvo.getValue();
        assertThat(pedido.id()).isEqualTo(PEDIDO_ID);
        assertThat(pedido.produtoId()).isEqualTo("PROD-001");
        assertThat(pedido.quantidade()).isEqualTo(2);
        assertThat(pedido.clienteCpf()).isEqualTo("50329291076");
        assertThat(pedido.clienteNome()).isEqualTo("Fernando Augusto");
        assertThat(pedido.status()).isEqualTo(StatusPedido.CONFIRMED);
        assertThat(pedido.motivo()).isNull();
        assertThat(pedido.criadoEm()).isEqualTo(LocalDateTime.parse("2026-09-07T20:03:57"));
        assertThat(pedido.atualizadoEm()).isEqualTo(LocalDateTime.parse("2026-09-07T20:04:10"));
        assertThat(pedido.correlationId()).isEqualTo("demo-etapa4-0001");
        verifyNoInteractions(publisher);
    }

    @Test
    @DisplayName("Falha: salva CANCELLED com motivo e publica a compensação")
    void falha() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(false);
        when(simulador.deveFalhar("Fernando Augusto")).thenReturn(true);
        when(repositorio.salvar(any())).thenReturn(true);

        ResultadoProcessamento resultado = useCase.executar(comando);

        assertThat(resultado).isEqualTo(ResultadoProcessamento.CANCELADO);
        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        InOrder ordem = inOrder(repositorio, publisher);
        ordem.verify(repositorio).salvar(salvo.capture());
        ordem.verify(publisher).publicar(new Compensacao(
                PEDIDO_ID, "PROD-001", 2, "Falha no processamento do pedido"));
        assertThat(salvo.getValue().status()).isEqualTo(StatusPedido.CANCELLED);
        assertThat(salvo.getValue().motivo()).isEqualTo("Falha no processamento do pedido");
        verify(metricas).pedidoCancelado();
        verify(metricas, never()).pedidoConfirmado();
    }

    @Test
    @DisplayName("Pedido já processado: ignora (idempotência), sem salvar, publicar nem contar métrica")
    void pedidoJaExistente() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(true);

        ResultadoProcessamento resultado = useCase.executar(comando);

        assertThat(resultado).isEqualTo(ResultadoProcessamento.IGNORADO);
        verify(repositorio, never()).salvar(any());
        verifyNoInteractions(publisher, simulador, metricas);
    }

    @Test
    @DisplayName("Concorrência: outro consumidor gravou primeiro → ignora, não publica compensação nem conta métrica")
    void gravadoPorOutroConsumidor() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(false);
        when(simulador.deveFalhar("Fernando Augusto")).thenReturn(true);
        when(repositorio.salvar(any())).thenReturn(false);

        ResultadoProcessamento resultado = useCase.executar(comando);

        assertThat(resultado).isEqualTo(ResultadoProcessamento.IGNORADO);
        verifyNoInteractions(publisher, metricas);
    }

    @Test
    @DisplayName("Erro ao publicar a compensação propaga (transação desfeita, mensagem vai para a DLQ) e não conta cancelamento")
    void erroAoPublicar() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(false);
        when(simulador.deveFalhar("Fernando Augusto")).thenReturn(true);
        when(repositorio.salvar(any())).thenReturn(true);
        org.mockito.Mockito.doThrow(new IllegalStateException("broker fora")).when(publisher).publicar(any());

        assertThatThrownBy(() -> useCase.executar(comando)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(metricas);
    }

    @Test
    @DisplayName("Mensagem incompleta (sem pedido_id, produto_id ou quantidade) é rejeitada sem tocar no banco")
    void comandoInvalido() {
        ProcessarPedidoCommand semProduto = new ProcessarPedidoCommand(
                PEDIDO_ID, " ", 2, "50329291076", "Fernando", LocalDateTime.now(), null);
        ProcessarPedidoCommand semId = new ProcessarPedidoCommand(
                null, "PROD-001", 2, "50329291076", "Fernando", LocalDateTime.now(), null);
        ProcessarPedidoCommand quantidadeZero = new ProcessarPedidoCommand(
                PEDIDO_ID, "PROD-001", 0, "50329291076", "Fernando", LocalDateTime.now(), null);

        assertThatThrownBy(() -> useCase.executar(semProduto)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> useCase.executar(semId)).isInstanceOf(MensagemInvalidaException.class);
        assertThatThrownBy(() -> useCase.executar(quantidadeZero)).isInstanceOf(MensagemInvalidaException.class);
        verifyNoInteractions(repositorio, publisher, simulador);
    }

    @Test
    @DisplayName("Pedido não expõe CPF nem nome no toString (logs)")
    void toStringSemDadosPessoais() {
        when(repositorio.existe(PEDIDO_ID)).thenReturn(false);
        when(simulador.deveFalhar("Fernando Augusto")).thenReturn(false);
        when(repositorio.salvar(any())).thenReturn(true);

        useCase.executar(comando);

        ArgumentCaptor<Pedido> salvo = ArgumentCaptor.forClass(Pedido.class);
        verify(repositorio).salvar(salvo.capture());
        assertThat(salvo.getValue().toString()).doesNotContain("50329291076", "Fernando");
    }
}

