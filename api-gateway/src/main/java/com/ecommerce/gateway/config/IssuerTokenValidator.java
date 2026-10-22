package com.ecommerce.gateway.config;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/**
 * Validates that the JWT issuer claim equals the expected issuer taken from
 * app.security.expected-issuer. Expiration is validated separately by
 * JwtTimestampValidator.
 */
public class IssuerTokenValidator implements OAuth2TokenValidator<Jwt> {

    private final String expectedIssuer;

    public IssuerTokenValidator(String expectedIssuer) {
        Assert.hasText(expectedIssuer, "expectedIssuer must not be empty");
        this.expectedIssuer = expectedIssuer;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Object issuerClaim = token.getClaims().get("iss");
        if (issuerClaim != null && expectedIssuer.equals(issuerClaim.toString())) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                "invalid_token",
                "Token issuer %s does not match the expected issuer".formatted(
                        issuerClaim == null ? "absent" : issuerClaim),
                null));
    }
}
