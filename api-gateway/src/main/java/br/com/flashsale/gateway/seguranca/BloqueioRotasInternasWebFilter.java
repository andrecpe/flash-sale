package br.com.flashsale.gateway.seguranca;

import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;

/**
 * Defesa em profundidade: a API interna dos serviços ({@code /internal/**}) nunca é acessível pela borda.
 * <p>
 * Nenhuma rota do gateway leva a ela, mas este filtro garante o bloqueio mesmo diante de um erro futuro de
 * configuração e de tentativas de contorno (caixa diferente, {@code ..}, {@code %2e%2e}, barras duplas).
 * Responde 404 (não revela que o recurso existe) antes de qualquer roteamento — e depois do filtro de
 * correlação, para que a resposta também leve o {@code X-Correlation-ID} e apareça no access log.
 */
@Component
public class BloqueioRotasInternasWebFilter implements WebFilter, Ordered {

    private static final byte[] CORPO_404 = "{\"mensagem\":\"Recurso não encontrado\"}".getBytes(StandardCharsets.UTF_8);

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (suspeito(exchange.getRequest().getURI().getRawPath())) {
            return naoEncontrado(exchange.getResponse());
        }
        return chain.filter(exchange);
    }

    /** Algum segmento é "internal" ou ".." (após decodificar e ignorar a caixa). */
    static boolean suspeito(String caminhoBruto) {
        if (caminhoBruto == null) {
            return false;
        }
        String caminho = UriUtils.decode(caminhoBruto, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return Arrays.stream(caminho.split("/"))
                .anyMatch(segmento -> segmento.equals("internal") || segmento.equals(".."));
    }

    private static Mono<Void> naoEncontrado(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.NOT_FOUND);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        DataBuffer corpo = response.bufferFactory().wrap(CORPO_404);
        return response.writeWith(Mono.just(corpo));
    }
}

