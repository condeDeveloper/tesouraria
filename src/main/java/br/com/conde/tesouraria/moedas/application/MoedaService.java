package br.com.conde.tesouraria.moedas.application;

import br.com.conde.tesouraria.moedas.domain.Moeda;
import br.com.conde.tesouraria.moedas.domain.MoedaRepository;
import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.ConflitoException;
import br.com.conde.tesouraria.shared.domain.DomainException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class MoedaService {

    private final MoedaRepository repository;

    public MoedaService(MoedaRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Moeda> listar(boolean somenteAtivas) {
        return somenteAtivas ? repository.findAllByAtivaTrueOrderByCodigo() : repository.findAllByOrderByCodigo();
    }

    @Transactional(readOnly = true)
    public Moeda buscar(String codigo) {
        return repository.findById(CodigoMoeda.normalizar(codigo))
                .orElseThrow(() -> new NaoEncontradoException("moeda", codigo));
    }

    /** Garante que a moeda existe e está ativa; usado pelos outros módulos. */
    @Transactional(readOnly = true)
    public Moeda exigirAtiva(String codigo) {
        Moeda m = buscar(codigo);
        if (!m.isAtiva()) throw new DomainException("moeda inativa: " + m.getCodigo());
        return m;
    }

    public Moeda cadastrar(String codigo, String nome, int casasDecimais) {
        Moeda nova = new Moeda(codigo, nome, casasDecimais);
        if (repository.existsById(nova.getCodigo())) throw new ConflitoException("moeda já cadastrada: " + nova.getCodigo());
        return repository.save(nova);
    }

    public Moeda renomear(String codigo, String nome) {
        Moeda m = buscar(codigo);
        m.renomear(nome);
        return m;
    }

    public Moeda alterarSituacao(String codigo, boolean ativa) {
        Moeda m = buscar(codigo);
        if (ativa) m.ativar(); else m.desativar();
        return m;
    }
}
