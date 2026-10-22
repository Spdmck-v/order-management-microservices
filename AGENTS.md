\# AGENTS.md — order-management-microservices



Reto técnico backend: API Gateway + Order Service + Inventory Service con OAuth2/JWT.

Estas reglas son obligatorias. Si algo del encargo las contradice, avisa antes de actuar.



\## Stack

\- Java 21, Maven (un pom por servicio, sin multi-módulo padre obligatorio)

\- Spring Boot 3.x estable, WebFlux (reactivo) en los tres servicios

\- API Gateway: Spring Cloud Gateway (versión de Spring Cloud compatible con la de Boot)

\- Seguridad: Spring Security OAuth2 Resource Server (JWT), Keycloak en Docker

\- Persistencia: PostgreSQL con Spring Data R2DBC; esquema inicial con scripts SQL (schema.sql o Flyway), nunca ddl-auto

\- Mapeo: MapStruct. Lombok permitido. Jackson para JSON

\- Logs: Logback con X-Trace-Id en el patrón (vía MDC / contexto Reactor)

\- AOP: un aspecto de logging por servicio (entrada/salida de servicios, duración)

\- Docs: springdoc-openapi (WebFlux) en Order e Inventory

\- Tests: JUnit 5, Mockito, StepVerifier, WebTestClient

\- NO verifiques ni inventes versiones de memoria: consulta la documentación oficial o Maven Central para fijar versiones compatibles.



\## Estructura del repo

```

api-gateway/  order-service/  inventory-service/

docs/diagrams/   keycloak/ (realm export)   docker-compose.yml   README.md

```

Cada servicio: arquitectura por capas (controller, service, repository, domain, dto, mapper, config, exception).



\## Reglas de arquitectura

\- El Gateway es el ÚNICO punto público (junto a Keycloak para pedir token). Order e Inventory NO publican puertos al host.

\- El Gateway NO tiene lógica de negocio ni compone respuestas. Solo: validar JWT, enrutar, generar/propagar X-Trace-Id.

\- Cada servicio tiene su propia base de datos. Prohibido acceder a la BD de otro servicio.

\- Order llama a Inventory solo con WebClient, propagando X-Trace-Id.

\- Aplicar SOLID y patrones de forma justificada: State/Strategy para transiciones de estado, Repository, Mapper, Factory/Builder donde aporte.

\- Nada de Saga, Kafka, Outbox, CQRS, Event Sourcing ni Dispatch Service.



\## Puertos

Gateway 8080 | Order 8081 (interno) | Inventory 8082 (interno) | Keycloak 8180 | Postgres orders 5433 | Postgres inventory 5434



\## X-Trace-Id

\- Header: `X-Trace-Id`. El Gateway lo recibe o genera (UUID). Se propaga a los servicios, va en logs (MDC), en TODAS las respuestas y en todos los errores.



\## Formato de error (único en todos los servicios)

`{ "timestamp": ISO-8601, "status": int, "code": string, "message": string, "traceId": string }`



Códigos: `ORDER\_NOT\_FOUND`(404), `INVALID\_REQUEST`(400), `STOCK\_INSUFFICIENT`(409), `INVALID\_STATE\_TRANSITION`(409), `INVALID\_TOKEN`(401), `PRODUCT\_NOT\_FOUND`(404), `INTERNAL\_ERROR`(500).

Manejo global con un handler por servicio; el 401 del Gateway usa el mismo formato.



\## Estados del pedido

PENDING, CONFIRMED, CANCELLED, FAILED.

\- Crear: se guarda PENDING + historial. Si Inventory reserva → CONFIRMED. Si responde stock insuficiente → FAILED y se responde 409 STOCK\_INSUFFICIENT.

\- Cancelar: solo desde CONFIRMED (y PENDING). Libera stock en Inventory. Desde CANCELLED o FAILED → 409 INVALID\_STATE\_TRANSITION.

\- Cada cambio de estado guarda una fila en order\_status\_history (from, to, reason, trace\_id, changed\_at).



\## Rutas (a través del Gateway)

\- Orders: `POST /api/orders`, `GET /api/orders/{id}`, `GET /api/orders/{id}/history`, `POST /api/orders/{id}/cancel`

\- Inventory: `GET /api/inventory/products`, `GET /api/inventory/products/{id}`, `POST /api/inventory/reserve`, `POST /api/inventory/release`

\- La reserva descuenta stock de forma ATÓMICA (UPDATE condicional en SQL, sin leer-y-escribir) e incluye orderId para idempotencia.



\## Modelo de datos

Ver `docs/diagrams/06-modelo-er.mmd`. order\_items.product\_id es referencia lógica, sin FK entre bases.



\## Docker

\- Dockerfile multi-stage por servicio (build con Maven, runtime con eclipse-temurin 21 JRE).

\- `docker compose up --build` debe levantar TODO desde cero, con healthchecks y depends\_on.

\- Keycloak con realm importado automáticamente desde `keycloak/`. Sin secretos reales en el repo.



\## Forma de trabajar

\- Haz SOLO lo que pide el encargo actual (una subfase a la vez). No adelantes trabajo.

\- Compila y corre los tests antes de decir que terminaste. Reporta comandos y resultados reales.

\- No hagas `git push`. Puedes hacer commits locales solo si se pide.

\- Commits en formato Conventional Commits (feat:, fix:, docs:, chore:).

\- Código y comentarios en inglés; README y documentación en español.

