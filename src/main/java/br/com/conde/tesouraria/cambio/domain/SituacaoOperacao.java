package br.com.conde.tesouraria.cambio.domain;

public enum SituacaoOperacao {
    /** Fechada, aguardando a data de liquidação. */
    ABERTA,
    /** Moedas entregues; caixa movimentado. */
    LIQUIDADA,
    /** Cancelada antes da liquidação; lançamentos estornados. */
    CANCELADA
}
