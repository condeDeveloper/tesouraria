package br.com.conde.tesouraria.shared.domain;

/** Estado conflitante (duplicidade, versão desatualizada). Vira HTTP 409 na borda da API. */
public class ConflitoException extends DomainException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
