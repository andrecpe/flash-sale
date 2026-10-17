package br.com.flashsale.checkout.integration;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Order Processor simulado (API interna {@code GET /internal/v1/orders/{id}}) para o teste do cache-aside:
 * responde o JSON configurado (ou 404) e conta quantas vezes cada pedido foi consultado — é assim que o teste
 * prova que o 2º GET veio do Redis.
 */
final class OrderProcessorStub {

    private static final String PREFIXO = "/internal/v1/orders/";

    private final HttpServer servidor;
    private final Map<UUID, String> respostas = new ConcurrentHashMap<>();
    private final Map<UUID, AtomicInteger> chamadas = new ConcurrentHashMap<>();

    private OrderProcessorStub(HttpServer servidor) {
        this.servidor = servidor;
    }

    static OrderProcessorStub iniciar() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            OrderProcessorStub stub = new OrderProcessorStub(http);
            http.createContext(PREFIXO, troca -> {
                String id = troca.getRequestURI().getPath().substring(PREFIXO.length());
                UUID pedidoId = UUID.fromString(id);
                stub.chamadas.computeIfAbsent(pedidoId, k -> new AtomicInteger()).incrementAndGet();
                String corpo = stub.respostas.get(pedidoId);
                int status = corpo == null ? 404 : 200;
                byte[] bytes = (corpo == null ? "{\"mensagem\":\"Pedido não encontrado\"}" : corpo)
                        .getBytes(StandardCharsets.UTF_8);
                troca.getResponseHeaders().add("Content-Type", "application/json");
                troca.sendResponseHeaders(status, bytes.length);
                troca.getResponseBody().write(bytes);
                troca.close();
            });
            http.start();
            return stub;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao iniciar o stub do Order Processor", e);
        }
    }

    String url() {
        return "http://localhost:" + servidor.getAddress().getPort();
    }

    void responder(UUID pedidoId, String json) {
        respostas.put(pedidoId, json);
    }

    int chamadas(UUID pedidoId) {
        AtomicInteger n = chamadas.get(pedidoId);
        return n == null ? 0 : n.get();
    }
}

