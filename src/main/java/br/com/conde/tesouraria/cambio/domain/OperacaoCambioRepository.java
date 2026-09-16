package br.com.conde.tesouraria.cambio.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperacaoCambioRepository extends JpaRepository<OperacaoCambio, UUID> {

    Optional<OperacaoCambio> findByNumero(String numero);

    Page<OperacaoCambio> findAllBySituacaoOrderByDataNegociacaoDesc(SituacaoOperacao situacao, Pageable pageable);

    Page<OperacaoCambio> findAllByOrderByDataNegociacaoDesc(Pageable pageable);

    Page<OperacaoCambio> findAllByContraparteIdOrderByDataNegociacaoDesc(UUID contraparteId, Pageable pageable);

    List<OperacaoCambio> findAllBySituacaoAndDataLiquidacaoLessThanEqual(SituacaoOperacao situacao, LocalDate data);
}
