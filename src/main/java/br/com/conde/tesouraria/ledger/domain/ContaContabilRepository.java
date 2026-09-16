package br.com.conde.tesouraria.ledger.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContaContabilRepository extends JpaRepository<ContaContabil, UUID> {

    Optional<ContaContabil> findByCodigo(String codigo);

    boolean existsByCodigo(String codigo);

    List<ContaContabil> findAllByOrderByCodigo();

    List<ContaContabil> findAllByContraparteIdOrderByCodigo(UUID contraparteId);

    Optional<ContaContabil> findByContraparteIdAndTipoAndMoeda(UUID contraparteId, TipoConta tipo, String moeda);
}
