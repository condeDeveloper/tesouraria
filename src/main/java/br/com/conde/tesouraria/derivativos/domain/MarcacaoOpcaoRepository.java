package br.com.conde.tesouraria.derivativos.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcacaoOpcaoRepository extends JpaRepository<MarcacaoOpcao, UUID> {

    Optional<MarcacaoOpcao> findFirstByOpcaoIdOrderByDataDesc(UUID opcaoId);

    Optional<MarcacaoOpcao> findByOpcaoIdAndData(UUID opcaoId, LocalDate data);

    List<MarcacaoOpcao> findAllByOpcaoIdOrderByData(UUID opcaoId);
}
