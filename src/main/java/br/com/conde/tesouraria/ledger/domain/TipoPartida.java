package br.com.conde.tesouraria.ledger.domain;

public enum TipoPartida {
    DEBITO,
    CREDITO;

    public TipoPartida inverso() { return this == DEBITO ? CREDITO : DEBITO; }
}
