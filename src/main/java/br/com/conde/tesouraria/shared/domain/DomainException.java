package br.com.conde.tesouraria.shared.domain;

/** Violação de regra de negócio. Vira HTTP 422 na borda da API. */
public class DomainException extends RuntimeException {

    public DomainException(String mensagem) {
        super(mensagem);
    }
}
