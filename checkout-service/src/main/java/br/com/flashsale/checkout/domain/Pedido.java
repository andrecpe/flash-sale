package br.com.flashsale.checkout.domain;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pedido do ponto de vista do Checkout: nasce em PROCESSING após validação das regras de negócio.
 * Imutável; a fonte da verdade do status final (CONFIRMED/CANCELLED) é o Order Processor.
 */
public final class Pedido {

    private final UUID id;
    private final String produtoId;
    private final int quantidade;
    private final Cpf cpf;
    private final String clienteNome;
    private final StatusPedido status;
    private final LocalDateTime criadoEm;

    private Pedido(UUID id, String produtoId, int quantidade, Cpf cpf, String clienteNome,
                   StatusPedido status, LocalDateTime criadoEm) {
        this.id = id;
        this.produtoId = produtoId;
        this.quantidade = quantidade;
        this.cpf = cpf;
        this.clienteNome = clienteNome;
        this.status = status;
        this.criadoEm = criadoEm;
    }

    /**
     * Regras: produto_id obrigatório; quantidade &gt; 0; CPF obrigatório e válido.
     *
     * @throws PedidoInvalidoException com todos os erros encontrados
     */
    public static Pedido criar(String produtoId, Integer quantidade, String cpf, String clienteNome, Clock clock) {
        List<String> erros = new ArrayList<>();
        if (produtoId == null || produtoId.isBlank()) {
            erros.add("produto_id é obrigatório");
        }
        if (quantidade == null || quantidade <= 0) {
            erros.add("quantidade deve ser maior que zero");
        }
        if (cpf == null || cpf.isBlank()) {
            erros.add("cliente_cpf é obrigatório");
        } else if (!Cpf.isValido(cpf)) {
            erros.add("cliente_cpf inválido");
        }
        if (!erros.isEmpty()) {
            throw new PedidoInvalidoException(erros);
        }
        return new Pedido(
                UUID.randomUUID(), // UUID versão 4 (aleatório)
                produtoId.trim(),
                quantidade,
                Cpf.de(cpf),
                clienteNome == null || clienteNome.isBlank() ? null : clienteNome.trim(),
                StatusPedido.PROCESSING,
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    public UUID id() {
        return id;
    }

    public String produtoId() {
        return produtoId;
    }

    public int quantidade() {
        return quantidade;
    }

    public Cpf cpf() {
        return cpf;
    }

    public String clienteNome() {
        return clienteNome;
    }

    public StatusPedido status() {
        return status;
    }

    public LocalDateTime criadoEm() {
        return criadoEm;
    }

    @Override
    public String toString() {
        return "Pedido[id=" + id + ", produtoId=" + produtoId + ", quantidade=" + quantidade
                + ", cpf=" + cpf + ", status=" + status + "]";
    }
}

