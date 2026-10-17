package br.com.flashsale.checkout.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.http.HttpResponse;
import java.nio.channels.ClosedChannelException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cenário "Black Friday": 200 clientes disputam 10 unidades ao mesmo tempo, contra Redis e RabbitMQ reais.
 * A reserva atômica (script Lua) garante: exatamente 10 pedidos aceitos, 190 recusas, estoque final 0
 * (nunca negativo) e exatamente 10 mensagens publicadas.
 */
class ConcorrenciaBlackFridayIT extends IntegracaoBase {

    private static final String PRODUTO = "PROD-IT-BF";
    private static final int ESTOQUE = 10;
    private static final int REQUISICOES = 200;

    @BeforeEach
    void limpar() {
        limparFilas();
    }

    @Test
    @DisplayName("Estoque 10 e 200 requisições paralelas: exatamente 10 × 202, 190 × 409 e estoque final 0")
    void semOverselling() throws Exception {
        definirEstoque(PRODUTO, ESTOQUE);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<Integer>> resultados = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < REQUISICOES; i++) {
                resultados.add(executor.submit(() -> {
                    largada.await(); // todas as threads disparam juntas
                    HttpResponse<String> resposta = postarPedidoComRetry();
                    return resposta.statusCode();
                }));
            }
            largada.countDown();

            List<Integer> status = new ArrayList<>();
            for (Future<Integer> resultado : resultados) {
                status.add(resultado.get());
            }

            var porStatus = status.stream().collect(Collectors.groupingBy(s -> s, Collectors.counting()));
            assertThat(porStatus).as("distribuição dos status HTTP")
                    .containsEntry(202, (long) ESTOQUE)
                    .containsEntry(409, (long) (REQUISICOES - ESTOQUE))
                    .hasSize(2);
        }

        assertThat(estoque(PRODUTO)).as("estoque final").isZero();
        assertThat(mensagensNaFila(FILA_PEDIDOS)).as("pedidos publicados em order-created").isEqualTo(ESTOQUE);
    }

    private HttpResponse<String> postarPedidoComRetry() throws Exception {
        IllegalStateException ultimaFalha = null;
        for (int tentativa = 1; tentativa <= 5; tentativa++) {
            try {
                return postarPedido(PRODUTO, 1, null);
            } catch (IllegalStateException e) {
                if (!falhaTransienteDeConexao(e) || tentativa == 5) {
                    throw e;
                }
                ultimaFalha = e;
                TimeUnit.MILLISECONDS.sleep(100L * tentativa);
            }
        }
        throw ultimaFalha;
    }

    private static boolean falhaTransienteDeConexao(Throwable erro) {
        Throwable atual = erro;
        while (atual != null) {
            if (atual instanceof ConnectException || atual instanceof ClosedChannelException) {
                return true;
            }
            atual = atual.getCause();
        }
        return false;
    }
}

