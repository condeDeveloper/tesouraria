package br.com.conde.tesouraria.derivativos.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ContratoNdfRepository extends JpaRepository<ContratoNdf, UUID> {

    Page<ContratoNdf> findAllByOrderByDataNegociacaoDesc(Pageable pageable);

    Page<ContratoNdf> findAllBySituacaoOrderByDataNegociacaoDesc(SituacaoContrato situacao, Pageable pageable);

    Page<ContratoNdf> findAllByContraparteIdOrderByDataNegociacaoDesc(UUID contraparteId, Pageable pageable);

    List<ContratoNdf> findAllBySituacao(SituacaoContrato situacao);

    List<ContratoNdf> findAllBySituacaoAndDataFixingLessThanEqual(SituacaoContrato situacao, LocalDate data);

    List<ContratoNdf> findAllBySituacaoAndDataLiquidacaoLessThanEqual(SituacaoContrato situacao, LocalDate data);
}
