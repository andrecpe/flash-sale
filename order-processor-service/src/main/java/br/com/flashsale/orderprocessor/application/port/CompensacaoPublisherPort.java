package br.com.flashsale.orderprocessor.application.port;

import br.com.flashsale.orderprocessor.application.Compensacao;

/** Porta de publicação da compensação (fila order-compensation). Lança exceção se o broker não confirmar. */
public interface CompensacaoPublisherPort {

    void publicar(Compensacao compensacao);
}

