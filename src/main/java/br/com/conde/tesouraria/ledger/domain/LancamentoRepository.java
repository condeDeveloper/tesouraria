package br.com.conde.tesouraria.ledger.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoRepository extends JpaRepository<Lancamento, UUID> {

    Optional<Lancamento> findByChaveIdempotencia(String chave);

    List<Lancamento> findAllByOrigemAndReferenciaIdOrderByDataLancamento(String origem, UUID referenciaId);

    Page<Lancamento> findAllByDataLancamentoBetweenOrderByDataLancamentoDescCriadoEmDesc(LocalDate inicio, LocalDate fim, Pageable pageable);
}
