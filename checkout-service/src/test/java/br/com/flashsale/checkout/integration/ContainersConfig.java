package br.com.flashsale.checkout.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Infraestrutura real dos testes de integração (Etapa 9): PostgreSQL, Redis e RabbitMQ em containers.
 * <p>
 * Com {@code @ServiceConnection} o Spring Boot aponta datasource, Redis e AMQP para os containers — sem
 * configuração manual de portas. O RabbitMQ é construído a partir de {@code infra/rabbitmq} (a mesma
 * imagem do compose): exchange, filas e DLQs reais, definidas em {@code definitions.json}.
 * Os containers sobem uma vez por contexto Spring (o contexto é reaproveitado entre as classes de teste).
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    /** Docker antigo (< 20.10): o seccomp padrão causa "Operation not permitted" (igual ao docker-compose.yml). */
    private static final List<String> SEM_SECCOMP = List.of("seccomp=unconfined");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withSecurityOpts(SEM_SECCOMP))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redis() {
        return new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withSecurityOpts(SEM_SECCOMP))
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStartupTimeout(Duration.ofMinutes(2));
    }

    @Bean
    @ServiceConnection
    RabbitMQContainer rabbitmq() throws Exception {
        Path infra = Path.of("..", "infra", "rabbitmq").toAbsolutePath().normalize();
        String imagem = new ImageFromDockerfile("flash-sale-it/rabbitmq", false)
                .withFileFromPath("Dockerfile", infra.resolve("Dockerfile"))
                .withFileFromPath("rabbitmq.conf", infra.resolve("rabbitmq.conf"))
                .withFileFromPath("definitions.json", infra.resolve("definitions.json"))
                .get();
        // usuário "flashsale" vem de definitions.json (com load_definitions, RABBITMQ_DEFAULT_USER é ignorado)
        return new RabbitMQContainer(DockerImageName.parse(imagem).asCompatibleSubstituteFor("rabbitmq"))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withSecurityOpts(SEM_SECCOMP))
                .withAdminUser("flashsale")
                .withAdminPassword("flashsale")
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}

