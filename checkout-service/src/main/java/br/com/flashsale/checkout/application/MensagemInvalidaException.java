package br.com.flashsale.checkout.application;

/** Mensagem sem os dados mínimos para compensar: não adianta reprocessar (vai para a DLQ). */
public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}

