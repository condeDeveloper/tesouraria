package br.com.conde.tesouraria.derivativos.domain;

public enum SituacaoContrato {
    /** Vigente, marcado a mercado diariamente. */
    ABERTO,
    /** Taxa de fixing conhecida; ajuste calculado, aguardando pagamento. */
    FIXADO,
    /** Ajuste pago ou recebido em caixa. */
    LIQUIDADO,
    /** Cancelado antes do fixing; marcações estornadas. */
    CANCELADO
}
