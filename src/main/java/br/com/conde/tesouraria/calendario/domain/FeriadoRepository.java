package br.com.conde.tesouraria.calendario.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FeriadoRepository extends JpaRepository<Feriado, java.util.UUID> {

    List<Feriado> findAllByPracaOrderByData(String praca);

    List<Feriado> findAllByPracaAndDataBetweenOrderByData(String praca, LocalDate inicio, LocalDate fim);

    Optional<Feriado> findByPracaAndData(String praca, LocalDate data);

    boolean existsByPracaAndData(String praca, LocalDate data);
}
