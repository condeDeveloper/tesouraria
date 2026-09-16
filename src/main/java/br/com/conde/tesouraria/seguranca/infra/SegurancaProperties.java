package br.com.conde.tesouraria.seguranca.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tesouraria.security")
public record SegurancaProperties(String jwtSecret, long jwtExpirationMinutes) {}
