package br.com.conde.tesouraria.shared.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface NumeradorRepository extends JpaRepository<Numerador, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Numerador> findComBloqueioByChave(String chave);
}
