package br.com.conde.tesouraria.contrapartes.application;

import br.com.conde.tesouraria.contrapartes.domain.Contraparte;
import br.com.conde.tesouraria.contrapartes.domain.ContraparteRepository;
import br.com.conde.tesouraria.contrapartes.domain.Documento;
import br.com.conde.tesouraria.shared.domain.ConflitoException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class ContraparteService {

    private final ContraparteRepository repository;

    public ContraparteService(ContraparteRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Page<Contraparte> listar(String nome, Pageable pageable) {
        return repository.findAllByNomeContainingIgnoreCaseOrderByNome(nome == null ? "" : nome, pageable);
    }

    @Transactional(readOnly = true)
    public Contraparte buscar(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NaoEncontradoException("contraparte", id));
    }

    @Transactional(readOnly = true)
    public Contraparte buscarPorDocumento(String documento) {
        String d = Documento.normalizar(documento);
        return repository.findByDocumento(d).orElseThrow(() -> new NaoEncontradoException("contraparte", Documento.formatar(d)));
    }

    /** Busca e garante que pode operar; usado por câmbio e derivativos. */
    @Transactional(readOnly = true)
    public Contraparte exigirApta(UUID id) {
        Contraparte c = buscar(id);
        c.exigirApta();
        return c;
    }

    public Contraparte cadastrar(String nome, String documento, boolean instituicaoFinanceira, String email) {
        Contraparte c = new Contraparte(nome, documento, instituicaoFinanceira, email);
        if (repository.existsByDocumento(c.getDocumento())) {
            throw new ConflitoException("já existe contraparte com o documento " + c.getDocumentoFormatado());
        }
        return repository.save(c);
    }

    public Contraparte atualizar(UUID id, String nome, String email) {
        Contraparte c = buscar(id);
        c.atualizar(nome, email);
        return c;
    }

    public Contraparte bloquear(UUID id) { Contraparte c = buscar(id); c.bloquear(); return c; }
    public Contraparte reativar(UUID id) { Contraparte c = buscar(id); c.reativar(); return c; }
    public Contraparte inativar(UUID id) { Contraparte c = buscar(id); c.inativar(); return c; }
}
