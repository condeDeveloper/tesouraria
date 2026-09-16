package br.com.conde.tesouraria.shared.domain;

/** Recurso inexistente. Vira HTTP 404 na borda da API. */
public class NaoEncontradoException extends DomainException {

    public NaoEncontradoException(String recurso, Object identificador) {
        super(recurso + " não encontrado: " + identificador);
    }
}
