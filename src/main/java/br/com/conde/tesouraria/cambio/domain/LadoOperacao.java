package br.com.conde.tesouraria.cambio.domain;

/** Lado da tesouraria em relação à moeda base do par. */
public enum LadoOperacao {
    /** Tesouraria compra a moeda base e entrega a cotada. */
    COMPRA,
    /** Tesouraria vende a moeda base e recebe a cotada. */
    VENDA;

    public LadoOperacao inverso() { return this == COMPRA ? VENDA : COMPRA; }
}
