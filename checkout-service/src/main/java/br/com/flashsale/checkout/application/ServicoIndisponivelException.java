package br.com.flashsale.checkout.application;

/** Dependência (Order Processor) indisponível: circuito aberto, timeout, erro de rede ou 5xx. */
public class ServicoIndisponivelException extends RuntimeException {

    public ServicoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

