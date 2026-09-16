package br.com.conde.tesouraria.mercado.domain;

public enum TipoCotacao {
    /** Cotação negociável da mesa, intradiária ou de fechamento. */
    SPOT,
    /** Taxa oficial de referência do Banco Central, usada como fixing de NDF. */
    PTAX
}
