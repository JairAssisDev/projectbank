package com.digitalbank.account.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.digitalbank.account.domain.Account;
import com.digitalbank.account.repository.AccountRepository;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final SecretKeySpec KEY = new SecretKeySpec(
            "test-secret-test-secret-test-secret-0123".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock
    AccountRepository accounts;

    final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    AuthService service;
    Account ana;

    @BeforeEach
    void setUp() {
        service = new AuthService(accounts, passwordEncoder, new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                Clock.systemUTC(), 60);
        ana = Account.open("Ana", "111", passwordEncoder.encode("senha123"), BigDecimal.TEN, Instant.now());
    }

    @Test
    void loginIssuesTokenWhoseSubjectIsTheAccountId() {
        when(accounts.findByDocument("111")).thenReturn(Optional.of(ana));

        var token = service.login(" 111 ", "senha123");

        // o mesmo segredo que os outros serviços usam para validar o token
        var jwt = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build().decode(token.value());
        assertThat(jwt.getSubject()).isEqualTo(ana.getId().toString());
        assertThat(jwt.getClaimAsString("name")).isEqualTo("Ana");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(60));
    }

    @Test
    void loginRejectsWrongPassword() {
        when(accounts.findByDocument("111")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> service.login("111", "errada"))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void loginRejectsUnknownDocumentWithTheSameError() {
        when(accounts.findByDocument("999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("999", "senha123"))
                .isInstanceOf(InvalidCredentialsException.class);
    }
}
