package br.com.conde.tesouraria.moedas.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MoedaRepository extends JpaRepository<Moeda, String> {

    List<Moeda> findAllByOrderByCodigo();

    List<Moeda> findAllByAtivaTrueOrderByCodigo();
}
