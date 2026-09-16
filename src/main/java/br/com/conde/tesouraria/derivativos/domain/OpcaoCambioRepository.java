package br.com.conde.tesouraria.derivativos.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface OpcaoCambioRepository extends JpaRepository<OpcaoCambio, UUID> {

    Page<OpcaoCambio> findAllByOrderByDataNegociacaoDesc(Pageable pageable);

    Page<OpcaoCambio> findAllBySituacaoOrderByDataNegociacaoDesc(SituacaoOpcao situacao, Pageable pageable);

    List<OpcaoCambio> findAllBySituacao(SituacaoOpcao situacao);

    List<OpcaoCambio> findAllBySituacaoAndDataVencimentoLessThanEqual(SituacaoOpcao situacao, LocalDate data);
}
