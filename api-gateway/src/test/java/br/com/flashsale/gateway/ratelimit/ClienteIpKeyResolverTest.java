package br.com.flashsale.gateway.ratelimit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;

/** Etapa 7 (TDD): chave do rate limit = IP do cliente (token bucket por cliente no Redis). */
class ClienteIpKeyResolverTest {

    private final ClienteIpKeyResolver resolver = new ClienteIpKeyResolver();

    @Test
    @DisplayName("Retorna o endereço IP do cliente")
    void ipDoCliente() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/orders/1")
                .remoteAddress(new InetSocketAddress("203.0.113.10", 54321)).build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Ignora X-Forwarded-For (o gateway é a borda: o header seria forjável para burlar o limite)")
    void ignoraXForwardedFor() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/orders/1")
                .remoteAddress(new InetSocketAddress("203.0.113.10", 54321))
                .header("X-Forwarded-For", "198.51.100.99").build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo("203.0.113.10");
    }

    @Test
    @DisplayName("Sem endereço remoto: chave fixa (não vazia — chave vazia seria negada com 403)")
    void semEnderecoRemoto() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/v1/orders/1").build());

        assertThat(resolver.resolve(exchange).block()).isEqualTo(ClienteIpKeyResolver.DESCONHECIDO);
    }
}

