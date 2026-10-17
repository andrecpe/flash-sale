package br.com.flashsale.checkout.application;

/** O broker não confirmou a publicação do pedido (resulta em 503; a reserva é desfeita). */
public class PublicacaoIndisponivelException extends RuntimeException {

    public PublicacaoIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}

