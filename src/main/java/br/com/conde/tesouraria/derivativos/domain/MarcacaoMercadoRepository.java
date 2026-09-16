package br.com.conde.tesouraria.derivativos.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MarcacaoMercadoRepository extends JpaRepository<MarcacaoMercado, UUID> {

    Optional<MarcacaoMercado> findFirstByContratoIdOrderByDataDesc(UUID contratoId);

    Optional<MarcacaoMercado> findByContratoIdAndData(UUID contratoId, LocalDate data);

    List<MarcacaoMercado> findAllByContratoIdOrderByData(UUID contratoId);
}
