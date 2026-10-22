package com.ecommerce.gateway.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI apiGatewayOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("API Gateway")
                        .version("1.0.0")
                        .description("""
                                Punto de entrada único del ecosistema e-commerce. Valida el Bearer token JWT \
                                emitido por Keycloak (realm orders-realm), enruta a Order Service e Inventory \
                                Service y genera o propaga el header X-Trace-Id.

                                Esta instancia no expone lógica de negocio: la documentación de Order Service y \
                                Inventory Service se consolida en esta Swagger UI a través de los endpoints \
                                /docs/order-service/v3/api-docs y /docs/inventory-service/v3/api-docs."""))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer token (JWT) emitido por Keycloak. Solicítalo al realm orders-realm en http://localhost:8180/realms/orders-realm/protocol/openid-connect/token.")));
    }
}
