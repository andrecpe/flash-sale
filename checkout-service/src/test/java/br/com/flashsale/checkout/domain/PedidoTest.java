package br.com.flashsale.checkout.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PedidoTest {

    // 2026-09-07T20:03:57.123 em São Paulo
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-07T23:03:57.123Z"), ZoneId.of("America/Sao_Paulo"));

    @Test
    @DisplayName("Pedido válido: gera UUID v4, status PROCESSING e data/hora sem frações de segundo")
    void pedidoValido() {
        Pedido pedido = Pedido.criar("PROD-001", 2, "50329291076", "Fernando Augusto", clock);

        assertThat(pedido.id().version()).isEqualTo(4);
        assertThat(pedido.produtoId()).isEqualTo("PROD-001");
        assertThat(pedido.quantidade()).isEqualTo(2);
        assertThat(pedido.cpf().valor()).isEqualTo("50329291076");
        assertThat(pedido.clienteNome()).isEqualTo("Fernando Augusto");
        assertThat(pedido.status()).isEqualTo(StatusPedido.PROCESSING);
        assertThat(pedido.criadoEm()).isEqualTo(LocalDateTime.of(2026, 9, 7, 20, 3, 57));
    }

    @Test
    @DisplayName("Cada pedido recebe um identificador diferente")
    void identificadoresUnicos() {
        Pedido a = Pedido.criar("PROD-001", 1, "50329291076", null, clock);
        Pedido b = Pedido.criar("PROD-001", 1, "50329291076", null, clock);

        assertThat(a.id()).isNotEqualTo(b.id());
    }

    @Test
    @DisplayName("Remove espaços do produto_id e do nome")
    void normalizaTextos() {
        Pedido pedido = Pedido.criar("  PROD-001 ", 1, "50329291076", "  Maria  ", clock);

        assertThat(pedido.produtoId()).isEqualTo("PROD-001");
        assertThat(pedido.clienteNome()).isEqualTo("Maria");
    }

    @Test
    @DisplayName("produto_id é obrigatório")
    void produtoObrigatorio() {
        assertThatThrownBy(() -> Pedido.criar(" ", 1, "50329291076", null, clock))
                .isInstanceOf(PedidoInvalidoException.class)
                .extracting(e -> ((PedidoInvalidoException) e).erros())
                .asList().containsExactly("produto_id é obrigatório");
    }

    @Test
    @DisplayName("quantidade deve ser maior que zero (zero, negativa ou ausente)")
    void quantidadePositiva() {
        for (Integer quantidade : new Integer[]{0, -1, null}) {
            assertThatThrownBy(() -> Pedido.criar("PROD-001", quantidade, "50329291076", null, clock))
                    .isInstanceOf(PedidoInvalidoException.class)
                    .extracting(e -> ((PedidoInvalidoException) e).erros())
                    .asList().containsExactly("quantidade deve ser maior que zero");
        }
    }

    @Test
    @DisplayName("cliente_cpf é obrigatório")
    void cpfObrigatorio() {
        assertThatThrownBy(() -> Pedido.criar("PROD-001", 1, null, null, clock))
                .isInstanceOf(PedidoInvalidoException.class)
                .extracting(e -> ((PedidoInvalidoException) e).erros())
                .asList().containsExactly("cliente_cpf é obrigatório");
    }

    @Test
    @DisplayName("cliente_cpf com dígito verificador errado é inválido")
    void cpfInvalido() {
        assertThatThrownBy(() -> Pedido.criar("PROD-001", 1, "50329291077", null, clock))
                .isInstanceOf(PedidoInvalidoException.class)
                .extracting(e -> ((PedidoInvalidoException) e).erros())
                .asList().containsExactly("cliente_cpf inválido");
    }

    @Test
    @DisplayName("Reúne todos os erros de validação de uma vez")
    void reuneTodosOsErros() {
        assertThatThrownBy(() -> Pedido.criar(null, 0, "", null, clock))
                .isInstanceOf(PedidoInvalidoException.class)
                .extracting(e -> ((PedidoInvalidoException) e).erros())
                .asList().containsExactly(
                        "produto_id é obrigatório",
                        "quantidade deve ser maior que zero",
                        "cliente_cpf é obrigatório");
    }
}

