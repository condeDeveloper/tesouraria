package br.com.conde.tesouraria.derivativos.domain;

/** Posição da tesouraria na opção. */
public enum PosicaoOpcao {
    /** Tesouraria paga o prêmio e detém o direito. */
    COMPRADA(1),
    /** Tesouraria recebe o prêmio e assume a obrigação. */
    LANCADA(-1);

    private final int sinal;

    PosicaoOpcao(int sinal) { this.sinal = sinal; }

    /** +1 para comprada, -1 para lançada: multiplica valor e gregas. */
    public int sinal() { return sinal; }
}
