package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.ConsultarEstoqueUseCase;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Consulta de saldo (apoio à demonstração: "existência de estoque inicial" e devolução após compensação). */
@RestController
@RequestMapping("/v1/products")
public class ProdutoController {

    private final ConsultarEstoqueUseCase consultarEstoque;

    public ProdutoController(ConsultarEstoqueUseCase consultarEstoque) {
        this.consultarEstoque = consultarEstoque;
    }

    @GetMapping("/{produtoId}/stock")
    public ResponseEntity<?> estoque(@PathVariable String produtoId) {
        return consultarEstoque.executar(produtoId)
                .<ResponseEntity<?>>map(saldo -> ResponseEntity.ok(new EstoqueResponse(produtoId, saldo)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErroResponse.de("Produto não encontrado")));
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record EstoqueResponse(String produtoId, long estoqueDisponivel) {
    }
}

