package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.ConsultarPedidoUseCase;
import br.com.flashsale.checkout.application.CriarPedidoUseCase;
import br.com.flashsale.checkout.application.PedidoNaoEncontradoException;
import br.com.flashsale.checkout.domain.Pedido;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/orders")
public class PedidoController {

    private final CriarPedidoUseCase criarPedido;
    private final ConsultarPedidoUseCase consultarPedido;

    public PedidoController(CriarPedidoUseCase criarPedido, ConsultarPedidoUseCase consultarPedido) {
        this.criarPedido = criarPedido;
        this.consultarPedido = consultarPedido;
    }

    /** 202 Accepted: o pedido foi aceito e será processado de forma assíncrona. */
    @PostMapping
    public ResponseEntity<PedidoAceitoResponse> criar(@RequestBody CriarPedidoRequest request) {
        Pedido pedido = criarPedido.executar(request.paraComando());
        return ResponseEntity.accepted().body(PedidoAceitoResponse.de(pedido));
    }

    /** 200 com o pedido (cache-aside); 404 após 3 tentativas; 503 se o Order Processor estiver indisponível. */
    @GetMapping("/{pedidoId}")
    public PedidoResponse consultar(@PathVariable String pedidoId) {
        UUID id;
        try {
            id = UUID.fromString(pedidoId);
        } catch (IllegalArgumentException e) {
            throw new PedidoNaoEncontradoException(); // id fora do formato: não existe pedido com ele
        }
        return PedidoResponse.de(consultarPedido.executar(id));
    }
}

