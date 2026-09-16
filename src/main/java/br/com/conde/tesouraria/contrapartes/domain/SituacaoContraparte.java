package br.com.conde.tesouraria.contrapartes.domain;

public enum SituacaoContraparte {
    /** Pode operar normalmente. */
    ATIVA,
    /** Não pode fechar novas operações; as existentes seguem até a liquidação. */
    BLOQUEADA,
    /** Encerrada; só consulta histórica. */
    INATIVA
}
