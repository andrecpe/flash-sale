package br.com.flashsale.orderprocessor.domain;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Pedido processado (agregado do Order Processor).
 * <p>
 * {@code correlationId} é metadado da transação de negócio: persistido para cruzar banco, logs e traces.
 * {@code toString} não expõe CPF nem nome (dados pessoais fora dos logs).
 */
public record Pedido(
        UUID id,
        String produtoId,
        int quantidade,
        String clienteCpf,
        String clienteNome,
        StatusPedido status,
        String motivo,
        LocalDateTime criadoEm,
        LocalDateTime atualizadoEm,
        String correlationId) {

    public static Pedido confirmado(UUID id, String produtoId, int quantidade, String clienteCpf, String clienteNome,
                                    LocalDateTime criadoEm, LocalDateTime atualizadoEm, String correlationId) {
        return new Pedido(id, produtoId, quantidade, clienteCpf, clienteNome,
                StatusPedido.CONFIRMED, null, criadoEm, atualizadoEm, correlationId);
    }

    public static Pedido cancelado(UUID id, String produtoId, int quantidade, String clienteCpf, String clienteNome,
                                   LocalDateTime criadoEm, LocalDateTime atualizadoEm, String motivo,
                                   String correlationId) {
        return new Pedido(id, produtoId, quantidade, clienteCpf, clienteNome,
                StatusPedido.CANCELLED, motivo, criadoEm, atualizadoEm, correlationId);
    }

    @Override
    public String toString() {
        return "Pedido[id=" + id + ", produtoId=" + produtoId + ", quantidade=" + quantidade
                + ", status=" + status + "]";
    }
}

