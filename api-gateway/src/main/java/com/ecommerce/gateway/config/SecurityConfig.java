package com.ecommerce.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

/**
 * Reactive resource server security. Every /api/** path requires a valid
 * JWT; only GET /actuator/health is public (used by the Docker healthcheck).
 * Tokens are decoded from the Keycloak JWKS endpoint reachable inside the
 * Docker network, while the expected issuer stays configurable because
 * tokens are requested from outside the network (Postman) and therefore
 * carry http://localhost:8180/... as issuer.
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public OAuth2TokenValidator<Jwt> tokenValidator(
            @Value("${app.security.expected-issuer:http://localhost:8180/realms/orders-realm}") String expectedIssuer) {
        return new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(),
                new IssuerTokenValidator(expectedIssuer));
    }

    @Bean
    public ReactiveJwtDecoder jwtDecoder(
            @Value("${app.security.jwk-set-uri:http://keycloak:8080/realms/orders-realm/protocol/openid-connect/certs}") String jwkSetUri,
            OAuth2TokenValidator<Jwt> tokenValidator) {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(tokenValidator);
        return decoder;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder,
            ServerAuthenticationEntryPoint authenticationEntryPoint,
            ServerAccessDeniedHandler accessDeniedHandler) {
        http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/api/**").authenticated()
                        .anyExchange().denyAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder)))
                .exceptionHandling(exceptions ->
                        exceptions.authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }
}
