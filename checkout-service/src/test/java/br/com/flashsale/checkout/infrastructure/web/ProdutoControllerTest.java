package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.ConsultarEstoqueUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProdutoController.class)
class ProdutoControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ConsultarEstoqueUseCase consultarEstoque;

    @Test
    @DisplayName("200 com o saldo disponível do produto")
    void estoqueDoProduto() throws Exception {
        when(consultarEstoque.executar("PROD-001")).thenReturn(Optional.of(100L));

        mvc.perform(get("/v1/products/PROD-001/stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.produto_id").value("PROD-001"))
                .andExpect(jsonPath("$.estoque_disponivel").value(100));
    }

    @Test
    @DisplayName("404 quando o produto não tem estoque cadastrado")
    void produtoInexistente() throws Exception {
        when(consultarEstoque.executar("PROD-999")).thenReturn(Optional.empty());

        mvc.perform(get("/v1/products/PROD-999/stock"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").value("Produto não encontrado"));
    }
}

