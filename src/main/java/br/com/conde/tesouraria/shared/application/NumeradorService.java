package br.com.conde.tesouraria.shared.application;

import br.com.conde.tesouraria.shared.domain.Numerador;
import br.com.conde.tesouraria.shared.domain.NumeradorRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;

@Service
public class NumeradorService {

    private final NumeradorRepository repository;

    public NumeradorService(NumeradorRepository repository) {
        this.repository = repository;
    }

    /** Próximo número no formato PREFIXO-ANO-000001, com bloqueio pessimista na linha do contador. */
    @Transactional(propagation = Propagation.REQUIRED)
    public String proximo(String prefixo) {
        String chave = prefixo + "-" + Year.now().getValue();
        Numerador n = repository.findComBloqueioByChave(chave).orElseGet(() -> repository.saveAndFlush(new Numerador(chave)));
        long seq = n.proximo();
        return chave + "-" + String.format("%06d", seq);
    }
}
