package br.com.conde.tesouraria.shared.domain;

import java.util.regex.Pattern;

/** Validação do código ISO 4217 (três letras maiúsculas). */
public final class CodigoMoeda {

    private static final Pattern FORMATO = Pattern.compile("^[A-Z]{3}$");

    private CodigoMoeda() {}

    public static String validar(String codigo) {
        if (codigo == null || !FORMATO.matcher(codigo).matches()) {
            throw new DomainException("código de moeda inválido: " + codigo);
        }
        return codigo;
    }

    public static String normalizar(String codigo) {
        return codigo == null ? null : validar(codigo.trim().toUpperCase());
    }
}
