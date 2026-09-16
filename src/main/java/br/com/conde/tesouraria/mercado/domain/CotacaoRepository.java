package br.com.conde.tesouraria.mercado.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CotacaoRepository extends JpaRepository<Cotacao, UUID> {

    Optional<Cotacao> findByParAndTipoAndData(String par, TipoCotacao tipo, LocalDate data);

    /** Cotação mais recente até a data, inclusive. */
    Optional<Cotacao> findFirstByParAndTipoAndDataLessThanEqualOrderByDataDesc(String par, TipoCotacao tipo, LocalDate data);

    List<Cotacao> findAllByParAndTipoAndDataBetweenOrderByData(String par, TipoCotacao tipo, LocalDate inicio, LocalDate fim);
}
