package br.com.flashsale.orderprocessor.infrastructure.correlation;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/orders");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> cidDuranteACadeia = new AtomicReference<>();
    private final FilterChain cadeia = (req, res) -> cidDuranteACadeia.set(MDC.get(CorrelationId.MDC_KEY));

    private ListAppender<ILoggingEvent> logs;

    @BeforeEach
    void capturarLogs() {
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger(CorrelationIdFilter.class)).addAppender(logs);
    }

    @AfterEach
    void limpar() {
        ((Logger) LoggerFactory.getLogger(CorrelationIdFilter.class)).detachAppender(logs);
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    @DisplayName("Header válido: o mesmo ID fica no MDC durante a requisição e volta na resposta")
    void headerValidoEhPropagado() throws Exception {
        request.addHeader(CorrelationId.HTTP_HEADER, "pedido-teste-12345678");

        filter.doFilter(request, response, cadeia);

        assertThat(cidDuranteACadeia.get()).isEqualTo("pedido-teste-12345678");
        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isEqualTo("pedido-teste-12345678");
    }

    @Test
    @DisplayName("Sem header (chamada direta): gera um novo ID válido")
    void semHeaderGeraId() throws Exception {
        filter.doFilter(request, response, cadeia);

        assertThat(CorrelationId.isValid(cidDuranteACadeia.get())).isTrue();
        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isEqualTo(cidDuranteACadeia.get());
    }

    @Test
    @DisplayName("Header inválido (log injection): descarta e gera um novo ID")
    void headerInvalidoEhSubstituido() throws Exception {
        request.addHeader(CorrelationId.HTTP_HEADER, "abc12345\r\nINJETADO");

        filter.doFilter(request, response, cadeia);

        assertThat(cidDuranteACadeia.get()).isNotEqualTo("abc12345\r\nINJETADO");
        assertThat(CorrelationId.isValid(cidDuranteACadeia.get())).isTrue();
    }

    @Test
    @DisplayName("Após a requisição o MDC está limpo (sem vazamento entre threads do pool)")
    void mdcLimpoAposARequisicao() throws Exception {
        request.addHeader(CorrelationId.HTTP_HEADER, "pedido-teste-12345678");

        filter.doFilter(request, response, cadeia);

        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("MDC limpo mesmo quando a cadeia lança exceção")
    void mdcLimpoMesmoComExcecao() {
        FilterChain cadeiaComErro = (req, res) -> {
            throw new ServletException("falha simulada");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, cadeiaComErro))
                .isInstanceOf(ServletException.class);
        assertThat(MDC.get(CorrelationId.MDC_KEY)).isNull();
    }

    @Test
    @DisplayName("Registra access log com o correlationId no MDC do evento")
    void registraAccessLogComCorrelationId() throws Exception {
        request.addHeader(CorrelationId.HTTP_HEADER, "pedido-teste-12345678");

        filter.doFilter(request, response, cadeia);

        assertThat(logs.list).hasSize(1);
        ILoggingEvent evento = logs.list.get(0);
        assertThat(evento.getFormattedMessage()).contains("POST", "/v1/orders", "200");
        assertThat(evento.getMDCPropertyMap()).containsEntry(CorrelationId.MDC_KEY, "pedido-teste-12345678");
    }

    @Test
    @DisplayName("Não registra access log para /actuator (health check do Docker a cada 15 s)")
    void naoRegistraAccessLogDoActuator() throws Exception {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health/readiness");

        filter.doFilter(health, response, cadeia);

        assertThat(logs.list).isEmpty();
        assertThat(response.getHeader(CorrelationId.HTTP_HEADER)).isNotBlank();
    }
}

