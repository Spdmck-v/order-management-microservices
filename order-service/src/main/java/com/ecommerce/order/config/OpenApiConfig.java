package com.ecommerce.order.config;

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
    public OpenAPI orderServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Order Service API")
                        .version("1.0.0")
                        .description("""
                                API de gestión de pedidos del ecosistema e-commerce: creación, consulta, \
                                historial de estados y cancelación. El flujo de creación persiste el pedido \
                                en PENDING y confirma con Inventory Service mediante una reserva atómica de stock.

                                Los endpoints se consumen a través del API Gateway (http://localhost:8080), \
                                que exige un Bearer token JWT emitido por Keycloak (realm orders-realm). \
                                Todas las respuestas llevan el header X-Trace-Id y cualquier error usa el \
                                formato único { timestamp, status, code, message, traceId }."""))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components().addSecuritySchemes(BEARER_AUTH_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Bearer token (JWT) emitido por Keycloak. El API Gateway valida el token contra el JWKS del realm orders-realm antes de enrutar al servicio.")));
    }
}
