package br.com.flashsale.orderprocessor.infrastructure.web;

import br.com.flashsale.orderprocessor.application.ConsultarPedidoUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * API interna (não roteada pelo gateway): consultada pelo Checkout no cache-miss.
 * O banco do Order Processor nunca é acessado por outro serviço (banco segregado).
 */
@RestController
@RequestMapping("/internal/v1/orders")
public class PedidoInternoController {

    private static final ErroResponse NAO_ENCONTRADO = new ErroResponse("Pedido não encontrado");

    private final ConsultarPedidoUseCase consultarPedido;

    public PedidoInternoController(ConsultarPedidoUseCase consultarPedido) {
        this.consultarPedido = consultarPedido;
    }

    @GetMapping("/{pedidoId}")
    public ResponseEntity<?> buscar(@PathVariable String pedidoId) {
        UUID id;
        try {
            id = UUID.fromString(pedidoId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(NAO_ENCONTRADO);
        }
        return consultarPedido.executar(id)
                .<ResponseEntity<?>>map(pedido -> ResponseEntity.ok(PedidoResponse.de(pedido)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(NAO_ENCONTRADO));
    }
}

