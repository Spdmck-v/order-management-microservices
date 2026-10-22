package com.ecommerce.gateway.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the issuer rule enforced on bearer tokens: the token issuer
 * must equal app.security.expected-issuer and expiration is checked.
 */
class IssuerTokenValidatorTests {

    private final JwtTimestampValidator timestampValidator = new JwtTimestampValidator();

    @Test
    void shouldAcceptTokenWithExpectedIssuer() {
        IssuerTokenValidator validator = new IssuerTokenValidator("http://localhost:8180/realms/orders-realm");
        OAuth2TokenValidatorResult result = validator.validate(jwt("http://localhost:8180/realms/orders-realm"));
        assertThat(result.getErrors()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://keycloak:8080/realms/orders-realm",
            "https://localhost:8180/realms/orders-realm",
            "http://localhost:8180/realms/other-realm",
            ""
    })
    void shouldRejectTokenWithUnexpectedIssuer(String issuer) {
        IssuerTokenValidator validator = new IssuerTokenValidator("http://localhost:8180/realms/orders-realm");
        OAuth2TokenValidatorResult result = validator.validate(jwt(issuer));
        assertThat(result.getErrors()).isNotEmpty();
    }

    @Test
    void shouldRejectTokenWithoutIssuerClaim() {
        IssuerTokenValidator validator = new IssuerTokenValidator("http://localhost:8180/realms/orders-realm");
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("testuser")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThat(validator.validate(token).getErrors()).isNotEmpty();
    }

    @Test
    void shouldRejectExpiredToken() {
        OAuth2TokenValidatorResult result = timestampValidator.validate(jwtExpired());
        assertThat(result.getErrors()).isNotEmpty();
    }

    private Jwt jwtExpired() {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("testuser")
                .issuedAt(Instant.now().minusSeconds(600))
                .expiresAt(Instant.now().minusSeconds(120))
                .build();
    }

    private Jwt jwt(String issuer) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("testuser")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(300));
        if (issuer != null) {
            builder.claim("iss", issuer);
        }
        return builder.build();
    }
}
