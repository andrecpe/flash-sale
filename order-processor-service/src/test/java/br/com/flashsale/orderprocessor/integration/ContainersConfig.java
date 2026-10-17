package br.com.flashsale.orderprocessor.integration;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

/**
 * Infraestrutura real dos testes de integração do Order Processor: PostgreSQL e RabbitMQ em containers.
 * O Docker local antigo exige {@code seccomp=unconfined}, como no {@code docker-compose.yml}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfig {

    private static final List<String> SEM_SECCOMP = List.of("seccomp=unconfined");

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgres() {
        return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withSecurityOpts(SEM_SECCOMP))
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
        return new RabbitMQContainer(DockerImageName.parse(imagem).asCompatibleSubstituteFor("rabbitmq"))
                .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withSecurityOpts(SEM_SECCOMP))
                .withAdminUser("flashsale")
                .withAdminPassword("flashsale")
                .withStartupTimeout(Duration.ofMinutes(2));
    }
}

