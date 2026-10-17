package br.com.flashsale.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.config.GlobalCorsProperties;
import org.springframework.cloud.gateway.config.HttpClientProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.cors.CorsConfiguration;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Etapas 2 e 7 (TDD): rotas, rate limit, timeouts e CORS do gateway (configuração carregada do application.yml).
 * Sem Redis/checkout reais: o comportamento 429 de ponta a ponta é verificado no Docker e na Etapa 9.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RotasTest {

    @Autowired
    private RouteLocator routeLocator;
    @Autowired
    private GlobalCorsProperties cors;
    @Autowired
    private HttpClientProperties httpClient;
    @Autowired
    private KeyResolver keyResolver;
    @Autowired
    private WebTestClient web;

    private Route rotaCheckout() {
        List<Route> rotas = routeLocator.getRoutes().collectList().block();
        return rotas.stream().filter(r -> r.getId().equals("checkout")).findFirst().orElseThrow();
    }

    private boolean casa(Route rota, String caminho) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get(caminho).build());
        return Boolean.TRUE.equals(Mono.from(rota.getPredicate().apply(exchange)).block());
    }

    @Test
    @DisplayName("Rota 'checkout' aponta para o checkout-service")
    void rotaCheckoutConfigurada() {
        assertThat(rotaCheckout().getUri()).isEqualTo(URI.create("http://checkout-service:8081"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/v1/orders", "/v1/orders/b819cc65-f6f0-478c-8bb7-69ee1c4f6402",
            "/v1/products/PROD-001/stock"})
    @DisplayName("/v1/orders/** e /v1/products/** vão para o Checkout")
    void rotasPublicas(String caminho) {
        assertThat(casa(rotaCheckout(), caminho)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/internal/v1/orders/1", "/actuator/health", "/v2/orders/1", "/"})
    @DisplayName("Nenhuma rota leva a /internal/** ou a caminhos fora da API pública")
    void rotasNaoPublicas(String caminho) {
        List<Route> rotas = routeLocator.getRoutes().collectList().block();
        assertThat(rotas).noneMatch(rota -> casa(rota, caminho));
    }

    @Test
    @DisplayName("A rota do Checkout aplica o RequestRateLimiter")
    void rotaComRateLimit() {
        assertThat(rotaCheckout().getFilters()).anyMatch(f -> f.toString().contains("RequestRateLimiter"));
    }

    @Test
    @DisplayName("O KeyResolver do rate limit é o de IP do cliente")
    void keyResolverPorIp() {
        assertThat(keyResolver).isInstanceOf(br.com.flashsale.gateway.ratelimit.ClienteIpKeyResolver.class);
    }

    @Test
    @DisplayName("Timeouts para o upstream: conexão 2 s e resposta 10 s")
    void timeouts() {
        assertThat(httpClient.getConnectTimeout()).isEqualTo(2000);
        assertThat(httpClient.getResponseTimeout()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    @DisplayName("CORS da API: expõe X-Correlation-ID e os headers de rate limit ao navegador")
    void corsExpoeHeaders() {
        CorsConfiguration config = cors.getCorsConfigurations().get("/v1/**");

        assertThat(config).isNotNull();
        assertThat(config.getExposedHeaders()).contains("X-Correlation-ID", "X-RateLimit-Remaining");
        assertThat(config.getAllowedMethods()).contains("GET", "POST");
    }

    @Test
    @DisplayName("Preflight CORS em /v1/orders é aceito")
    void preflight() {
        // URI absoluta: o WebTestClient é ligado ao contexto (sem servidor HTTP) e, sem host, o CORS rejeita
        web.options().uri("http://localhost:8080/v1/orders")
                .header(HttpHeaders.ORIGIN, "http://localhost:3001")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, HttpMethod.POST.name())
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type,X-Correlation-ID")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)
                .expectHeader().valueMatches(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, ".*POST.*");
    }

    @Test
    @DisplayName("GET /internal/** pela borda → 404 com JSON e X-Correlation-ID")
    void internaBloqueadaPontaAPonta() {
        web.get().uri("/internal/v1/orders/b819cc65-f6f0-478c-8bb7-69ee1c4f6402").exchange()
                .expectStatus().isNotFound()
                .expectHeader().exists("X-Correlation-ID")
                .expectBody().jsonPath("$.mensagem").isEqualTo("Recurso não encontrado");
    }
}

