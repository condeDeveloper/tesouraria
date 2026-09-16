package br.com.conde.tesouraria.seguranca.domain;

public enum Papel {
    /** Cadastros, usuários, parâmetros e tudo que OPERADOR faz. */
    ADMIN,
    /** Fecha operações, lança no ledger, registra cotações. */
    OPERADOR,
    /** Somente consultas e relatórios. */
    LEITOR
}
