package br.com.flashsale.gateway.ratelimit;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

/**
 * Chave do rate limit: IP do cliente (um token bucket por IP no Redis).
 * <p>
 * Usa o endereço da conexão, não o {@code X-Forwarded-For}: o gateway é a borda, então esse header viria do
 * próprio cliente e poderia ser forjado a cada requisição para escapar do limite. Se um balanceador for
 * colocado na frente, configurar {@code server.forward-headers-strategy} com proxies confiáveis.
 */
@Component("clienteIpKeyResolver")
public class ClienteIpKeyResolver implements KeyResolver {

    /** Chave usada quando o endereço remoto não está disponível (nunca vazia: chave vazia é negada com 403). */
    static final String DESCONHECIDO = "desconhecido";

    @Override
    public Mono<String> resolve(ServerWebExchange exchange) {
        return Mono.just(Optional.ofNullable(exchange.getRequest().getRemoteAddress())
                .map(InetSocketAddress::getAddress)
                .map(endereco -> endereco.getHostAddress())
                .orElse(DESCONHECIDO));
    }
}

