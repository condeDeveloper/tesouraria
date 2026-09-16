package br.com.conde.tesouraria.mercado.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurvaJurosRepository extends JpaRepository<CurvaJuros, UUID> {

    Optional<CurvaJuros> findByNomeAndDataReferencia(String nome, LocalDate data);

    Optional<CurvaJuros> findFirstByNomeAndDataReferenciaLessThanEqualOrderByDataReferenciaDesc(String nome, LocalDate data);

    List<CurvaJuros> findAllByNomeOrderByDataReferenciaDesc(String nome);
}
