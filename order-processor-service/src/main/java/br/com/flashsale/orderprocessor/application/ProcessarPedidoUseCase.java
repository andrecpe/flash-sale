package br.com.flashsale.orderprocessor.application;

import br.com.flashsale.orderprocessor.application.port.CompensacaoPublisherPort;
import br.com.flashsale.orderprocessor.application.port.PedidoRepositoryPort;
import br.com.flashsale.orderprocessor.application.port.ProcessamentoMetricasPort;
import br.com.flashsale.orderprocessor.domain.FalhaSimulador;
import br.com.flashsale.orderprocessor.domain.Pedido;
import io.opentelemetry.instrumentation.annotations.WithSpan;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Processa o pedido recebido de order-created (passo local da SAGA coreografada):
 * <ol>
 *   <li>pedido já existente → ignora (idempotência: mensagens podem ser reentregues);</li>
 *   <li>sucesso → grava {@code CONFIRMED};</li>
 *   <li>falha → grava {@code CANCELLED} com motivo e publica em order-compensation
 *       (o Checkout devolve o estoque).</li>
 * </ol>
 * Gravação e publicação rodam na mesma transação (aberta pelo listener): se o broker não confirmar,
 * a gravação é desfeita e a mensagem original vai para a DLQ.
 */
public class ProcessarPedidoUseCase {

    public static final String MOTIVO_FALHA = "Falha no processamento do pedido";

    private final PedidoRepositoryPort repositorio;
    private final CompensacaoPublisherPort publisher;
    private final FalhaSimulador simulador;
    private final ProcessamentoMetricasPort metricas;
    private final Clock clock;

    public ProcessarPedidoUseCase(PedidoRepositoryPort repositorio, CompensacaoPublisherPort publisher,
                                  FalhaSimulador simulador, ProcessamentoMetricasPort metricas, Clock clock) {
        this.repositorio = repositorio;
        this.publisher = publisher;
        this.simulador = simulador;
        this.metricas = metricas;
        this.clock = clock;
    }

    /** Span de negócio "processar-pedido" (continua o trace do checkout via order-created). Sem CPF/nome. */
    @WithSpan("processar-pedido")
    public ResultadoProcessamento executar(ProcessarPedidoCommand cmd) {
        validar(cmd);
        Telemetria.atributo("pedido.id", cmd.pedidoId().toString());
        Telemetria.atributo("produto.id", cmd.produtoId());
        Telemetria.atributo("pedido.quantidade", cmd.quantidade());
        if (repositorio.existe(cmd.pedidoId())) {
            return ResultadoProcessamento.IGNORADO;
        }

        boolean falhou = simulador.deveFalhar(cmd.clienteNome());
        LocalDateTime agora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        LocalDateTime criadoEm = cmd.dataHoraCriacao() != null ? cmd.dataHoraCriacao() : agora;

        Pedido pedido = falhou
                ? Pedido.cancelado(cmd.pedidoId(), cmd.produtoId(), cmd.quantidade(), cmd.clienteCpf(),
                        cmd.clienteNome(), criadoEm, agora, MOTIVO_FALHA, cmd.correlationId())
                : Pedido.confirmado(cmd.pedidoId(), cmd.produtoId(), cmd.quantidade(), cmd.clienteCpf(),
                        cmd.clienteNome(), criadoEm, agora, cmd.correlationId());

        if (!repositorio.salvar(pedido)) {
            return ResultadoProcessamento.IGNORADO;
        }
        if (!falhou) {
            metricas.pedidoConfirmado();
            return ResultadoProcessamento.CONFIRMADO;
        }
        publisher.publicar(new Compensacao(cmd.pedidoId(), cmd.produtoId(), cmd.quantidade(), MOTIVO_FALHA));
        metricas.pedidoCancelado();
        return ResultadoProcessamento.CANCELADO;
    }

    private static void validar(ProcessarPedidoCommand cmd) {
        List<String> erros = new ArrayList<>();
        if (cmd.pedidoId() == null) {
            erros.add("pedido_id é obrigatório");
        }
        if (cmd.produtoId() == null || cmd.produtoId().isBlank()) {
            erros.add("produto_id é obrigatório");
        }
        if (cmd.quantidade() <= 0) {
            erros.add("quantidade deve ser maior que zero");
        }
        if (cmd.clienteCpf() == null || cmd.clienteCpf().isBlank()) {
            erros.add("cliente_cpf é obrigatório");
        }
        if (!erros.isEmpty()) {
            throw new MensagemInvalidaException("Mensagem de pedido inválida: " + String.join(", ", erros));
        }
    }
}

