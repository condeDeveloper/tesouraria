package br.com.conde.tesouraria.contrapartes.domain;

public enum TipoContraparte {
    /** Pessoa física (CPF). */
    PF,
    /** Pessoa jurídica (CNPJ). */
    PJ,
    /** Instituição financeira, também com CNPJ, mas com tratamento próprio em limites e liquidação. */
    INSTITUICAO
}
