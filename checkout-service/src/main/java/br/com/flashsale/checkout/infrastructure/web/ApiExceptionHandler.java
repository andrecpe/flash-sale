package br.com.flashsale.checkout.infrastructure.web;

import br.com.flashsale.checkout.application.EstoqueInsuficienteException;
import br.com.flashsale.checkout.application.PedidoNaoEncontradoException;
import br.com.flashsale.checkout.application.PublicacaoIndisponivelException;
import br.com.flashsale.checkout.application.ServicoIndisponivelException;
import br.com.flashsale.checkout.domain.PedidoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduz exceções de negócio/infra para respostas HTTP no formato {"mensagem": ...}. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PedidoInvalidoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErroResponse pedidoInvalido(PedidoInvalidoException e) {
        return new ErroResponse("Dados do pedido inválidos", e.erros());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErroResponse corpoInvalido() {
        return ErroResponse.de("Corpo da requisição inválido");
    }

    @ExceptionHandler(EstoqueInsuficienteException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErroResponse estoqueInsuficiente(EstoqueInsuficienteException e) {
        return ErroResponse.de(e.getMessage());
    }

    @ExceptionHandler(PublicacaoIndisponivelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErroResponse indisponivel() {
        return ErroResponse.de("Serviço temporariamente indisponível. Tente novamente.");
    }

    @ExceptionHandler(PedidoNaoEncontradoException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErroResponse pedidoNaoEncontrado() {
        return ErroResponse.de("Pedido não encontrado");
    }

    @ExceptionHandler(ServicoIndisponivelException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    ErroResponse dependenciaIndisponivel(ServicoIndisponivelException e) {
        log.warn("Consulta indisponível: {}", e.getMessage());
        return ErroResponse.de("Serviço temporariamente indisponível. Tente novamente.");
    }
}

