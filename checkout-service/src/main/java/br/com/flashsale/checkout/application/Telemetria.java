package br.com.flashsale.checkout.application;

import io.opentelemetry.api.trace.Span;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Atributos dos spans de negócio (criados pelo agent via {@code @WithSpan}).
 * <p>
 * Regra de privacidade (LGPD): spans carregam apenas identificadores técnicos ({@code pedido.id},
 * {@code produto.id}, quantidade). Chaves que sugerem dados pessoais (cpf, nome, cliente, email) são
 * descartadas — o erro de programação vira atributo ausente, nunca vazamento e nunca exceção no fluxo
 * de negócio. Sem o agent, {@code Span.current()} é um span inválido e a chamada não faz nada.
 */
public final class Telemetria {

    private static final Pattern DADO_PESSOAL = Pattern.compile("cpf|nome|cliente|email|telefone");

    private Telemetria() {
    }

    public static void atributo(String chave, String valor) {
        atributo(Span.current(), chave, valor);
    }

    public static void atributo(String chave, long valor) {
        atributo(Span.current(), chave, valor);
    }

    static void atributo(Span span, String chave, String valor) {
        if (valor != null && permitida(chave)) {
            span.setAttribute(chave, valor);
        }
    }

    static void atributo(Span span, String chave, long valor) {
        if (permitida(chave)) {
            span.setAttribute(chave, valor);
        }
    }

    private static boolean permitida(String chave) {
        return chave != null && !DADO_PESSOAL.matcher(chave.toLowerCase(Locale.ROOT)).find();
    }
}

