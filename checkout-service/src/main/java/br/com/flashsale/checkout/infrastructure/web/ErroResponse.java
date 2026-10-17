package br.com.flashsale.checkout.infrastructure.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Corpo padrão de erro: {"mensagem": "..."} (+ "erros" na validação). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErroResponse(String mensagem, List<String> erros) {

    static ErroResponse de(String mensagem) {
        return new ErroResponse(mensagem, null);
    }
}

