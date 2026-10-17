package br.com.flashsale.orderprocessor.application;

/** Mensagem sem os dados mínimos para processar o pedido: não adianta reprocessar (vai para a DLQ). */
public class MensagemInvalidaException extends RuntimeException {

    public MensagemInvalidaException(String mensagem) {
        super(mensagem);
    }
}

