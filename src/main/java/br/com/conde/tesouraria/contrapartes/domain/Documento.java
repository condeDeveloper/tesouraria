package br.com.conde.tesouraria.contrapartes.domain;

import br.com.conde.tesouraria.shared.domain.DomainException;

/** CPF ou CNPJ, validado pelos dígitos verificadores e guardado só com números. */
public final class Documento {

    private Documento() {}

    public static String normalizar(String bruto) {
        if (bruto == null) throw new DomainException("documento obrigatório");
        String digitos = bruto.replaceAll("\\D", "");
        if (digitos.length() == 11) { validarCpf(digitos); return digitos; }
        if (digitos.length() == 14) { validarCnpj(digitos); return digitos; }
        throw new DomainException("documento deve ser CPF (11 dígitos) ou CNPJ (14 dígitos)");
    }

    public static TipoContraparte tipoPara(String documentoNormalizado) {
        return documentoNormalizado.length() == 11 ? TipoContraparte.PF : TipoContraparte.PJ;
    }

    public static String formatar(String d) {
        if (d.length() == 11) return d.replaceAll("(\\d{3})(\\d{3})(\\d{3})(\\d{2})", "$1.$2.$3-$4");
        return d.replaceAll("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private static void validarCpf(String cpf) {
        if (cpf.chars().distinct().count() == 1) throw new DomainException("CPF inválido");
        int d1 = digito(cpf, 9, 10);
        int d2 = digito(cpf, 10, 11);
        if (d1 != cpf.charAt(9) - '0' || d2 != cpf.charAt(10) - '0') throw new DomainException("CPF inválido");
    }

    /** Dígito verificador do CPF: pesos decrescentes a partir de `peso` sobre os primeiros `n` dígitos. */
    private static int digito(String cpf, int n, int peso) {
        int soma = 0;
        for (int i = 0; i < n; i++) soma += (cpf.charAt(i) - '0') * (peso - i);
        int resto = (soma * 10) % 11;
        return resto == 10 ? 0 : resto;
    }

    private static void validarCnpj(String cnpj) {
        if (cnpj.chars().distinct().count() == 1) throw new DomainException("CNPJ inválido");
        int[] p1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] p2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int d1 = digitoCnpj(cnpj, p1);
        int d2 = digitoCnpj(cnpj, p2);
        if (d1 != cnpj.charAt(12) - '0' || d2 != cnpj.charAt(13) - '0') throw new DomainException("CNPJ inválido");
    }

    private static int digitoCnpj(String cnpj, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < pesos.length; i++) soma += (cnpj.charAt(i) - '0') * pesos[i];
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
