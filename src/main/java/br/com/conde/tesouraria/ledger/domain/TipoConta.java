package br.com.conde.tesouraria.ledger.domain;

public enum TipoConta {
    ATIVO(NaturezaConta.DEVEDORA),
    PASSIVO(NaturezaConta.CREDORA),
    PATRIMONIO(NaturezaConta.CREDORA),
    RECEITA(NaturezaConta.CREDORA),
    DESPESA(NaturezaConta.DEVEDORA);

    private final NaturezaConta naturezaPadrao;

    TipoConta(NaturezaConta naturezaPadrao) { this.naturezaPadrao = naturezaPadrao; }

    public NaturezaConta naturezaPadrao() { return naturezaPadrao; }
}
