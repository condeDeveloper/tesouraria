package br.com.conde.tesouraria.derivativos.domain;

public enum SituacaoOpcao {
    ABERTA,
    /** Vencida dentro do dinheiro; payoff liquidado em BRL. */
    EXERCIDA,
    /** Vencida fora do dinheiro; sem payoff. */
    EXPIRADA,
    CANCELADA
}
