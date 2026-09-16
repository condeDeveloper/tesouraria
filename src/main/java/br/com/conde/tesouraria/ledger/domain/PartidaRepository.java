package br.com.conde.tesouraria.ledger.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PartidaRepository extends JpaRepository<Partida, UUID> {

    /** Soma dos débitos menos créditos da conta até a data, inclusive. Natureza é aplicada pelo serviço. */
    @Query("""
            select coalesce(sum(case when p.tipo = br.com.conde.tesouraria.ledger.domain.TipoPartida.DEBITO then p.valor else -p.valor end), 0)
            from Partida p
            where p.conta.id = :contaId and p.lancamento.dataLancamento <= :data
            """)
    BigDecimal debitoLiquidoAte(@Param("contaId") UUID contaId, @Param("data") LocalDate data);

    @Query("""
            select p from Partida p join fetch p.lancamento l
            where p.conta.id = :contaId and l.dataLancamento between :inicio and :fim
            order by l.dataLancamento, l.criadoEm, p.ordem
            """)
    List<Partida> extrato(@Param("contaId") UUID contaId, @Param("inicio") LocalDate inicio, @Param("fim") LocalDate fim);

    /** Débito líquido por conta em uma moeda, para balancete e exposição. */
    @Query("""
            select p.conta.id, coalesce(sum(case when p.tipo = br.com.conde.tesouraria.ledger.domain.TipoPartida.DEBITO then p.valor else -p.valor end), 0)
            from Partida p
            where p.lancamento.dataLancamento <= :data
            group by p.conta.id
            """)
    List<Object[]> debitoLiquidoPorConta(@Param("data") LocalDate data);
}
