package br.com.flashsale.checkout.infrastructure.config;

import br.com.flashsale.checkout.infrastructure.redis.RedisEstoqueAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Na subida, cadastra o estoque inicial da campanha no Redis (sem sobrescrever saldos existentes). */
@Component
public class CargaEstoqueInicial implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CargaEstoqueInicial.class);

    private final RedisEstoqueAdapter estoque;
    private final String configuracao;

    public CargaEstoqueInicial(RedisEstoqueAdapter estoque, @Value("${estoque.inicial:}") String configuracao) {
        this.estoque = estoque;
        this.configuracao = configuracao;
    }

    @Override
    public void run(ApplicationArguments args) {
        EstoqueInicialParser.parse(configuracao).forEach((produtoId, quantidade) -> {
            if (estoque.definirSeAusente(produtoId, quantidade)) {
                log.info("Estoque inicial cadastrado: produto={} quantidade={}", produtoId, quantidade);
            } else {
                log.info("Estoque já existente mantido: produto={} saldo={}", produtoId,
                        estoque.consultar(produtoId).orElse(null));
            }
        });
    }
}

