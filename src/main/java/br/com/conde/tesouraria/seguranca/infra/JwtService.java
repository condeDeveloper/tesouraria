package br.com.conde.tesouraria.seguranca.infra;

import br.com.conde.tesouraria.seguranca.domain.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/** Emite e valida tokens JWT assinados com HMAC-SHA256. */
@Component
public class JwtService {

    private final SecretKey chave;
    private final Duration validade;

    public JwtService(SegurancaProperties props) {
        this.chave = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
        this.validade = Duration.ofMinutes(props.jwtExpirationMinutes());
    }

    public record Token(String valor, Instant expiraEm) {}

    public Token emitir(Usuario u) {
        Instant agora = Instant.now();
        Instant exp = agora.plus(validade);
        String jwt = Jwts.builder()
                .subject(u.getLogin())
                .claim("nome", u.getNome())
                .claim("papel", u.getPapel().name())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(exp))
                .signWith(chave)
                .compact();
        return new Token(jwt, exp);
    }

    /** Devolve as claims se o token for válido e não expirado; vazio caso contrário. */
    public Optional<Claims> validar(String jwt) {
        try {
            return Optional.of(Jwts.parser().verifyWith(chave).build().parseSignedClaims(jwt).getPayload());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
