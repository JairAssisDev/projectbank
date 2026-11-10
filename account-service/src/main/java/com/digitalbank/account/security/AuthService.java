package com.digitalbank.account.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.digitalbank.account.domain.Account;
import com.digitalbank.account.repository.AccountRepository;

/** Login: confere documento + senha e emite um JWT cujo "sub" é o ID da conta. */
@Service
public class AuthService {

    public record IssuedToken(String value, Instant expiresAt, Account account) {
    }

    private final AccountRepository accounts;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public AuthService(AccountRepository accounts, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder,
                       Clock clock, @Value("${bank.security.token-ttl-minutes:60}") long ttlMinutes) {
        this.accounts = accounts;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @Transactional(readOnly = true)
    public IssuedToken login(String document, String rawPassword) {
        // Mesma exceção para "conta não existe" e "senha errada": não revela quais documentos têm conta.
        Account account = accounts.findByDocument(document.trim())
                .filter(a -> passwordEncoder.matches(rawPassword, a.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);

        Instant now = clock.instant();
        Instant expiresAt = now.plus(ttl);
        var claims = JwtClaimsSet.builder()
                .issuer("account-service")
                .subject(account.getId().toString())
                .claim("name", account.getOwnerName())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .build();
        var header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt, account);
    }
}
