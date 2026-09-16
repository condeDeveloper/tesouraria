package br.com.conde.tesouraria.contrapartes.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContraparteRepository extends JpaRepository<Contraparte, UUID> {

    Optional<Contraparte> findByDocumento(String documento);

    boolean existsByDocumento(String documento);

    Page<Contraparte> findAllByNomeContainingIgnoreCaseOrderByNome(String nome, Pageable pageable);
}
