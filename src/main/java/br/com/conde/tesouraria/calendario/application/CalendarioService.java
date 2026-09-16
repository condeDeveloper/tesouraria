package br.com.conde.tesouraria.calendario.application;

import br.com.conde.tesouraria.calendario.domain.Calendario;
import br.com.conde.tesouraria.calendario.domain.Feriado;
import br.com.conde.tesouraria.calendario.domain.FeriadoRepository;
import br.com.conde.tesouraria.shared.domain.CodigoMoeda;
import br.com.conde.tesouraria.shared.domain.ConflitoException;
import br.com.conde.tesouraria.shared.domain.NaoEncontradoException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class CalendarioService {

    private final FeriadoRepository repository;

    public CalendarioService(FeriadoRepository repository) {
        this.repository = repository;
    }

    /** Calendário combinado das praças informadas: um dia só é útil se for útil em todas. */
    @Transactional(readOnly = true)
    public Calendario calendario(String... pracas) {
        Set<LocalDate> datas = new HashSet<>();
        Arrays.stream(pracas).map(CodigoMoeda::normalizar).distinct()
                .forEach(p -> repository.findAllByPracaOrderByData(p).forEach(f -> datas.add(f.getData())));
        return new Calendario(datas);
    }

    @Transactional(readOnly = true)
    public List<Feriado> listar(String praca, LocalDate inicio, LocalDate fim) {
        String p = CodigoMoeda.normalizar(praca);
        return inicio != null && fim != null
                ? repository.findAllByPracaAndDataBetweenOrderByData(p, inicio, fim)
                : repository.findAllByPracaOrderByData(p);
    }

    public Feriado cadastrar(String praca, LocalDate data, String descricao) {
        Feriado f = new Feriado(praca, data, descricao);
        if (repository.existsByPracaAndData(f.getPraca(), f.getData())) {
            throw new ConflitoException("feriado já cadastrado em " + f.getPraca() + " para " + data);
        }
        return repository.save(f);
    }

    public void remover(UUID id) {
        Feriado f = repository.findById(id).orElseThrow(() -> new NaoEncontradoException("feriado", id));
        repository.delete(f);
    }
}
