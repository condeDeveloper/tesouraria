package br.com.conde.tesouraria.risco.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LimiteContraparteRepository extends JpaRepository<LimiteContraparte, UUID> {
}
