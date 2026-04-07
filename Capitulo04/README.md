# Laboratorio 4: Métricas + Pruebas de Resiliencia

## Metadatos

| Propiedad | Valor |
|-----------|-------|
| **Duración** | 85 minutos |
| **Complejidad** | Intermedio |
| **Nivel Bloom** | Aplicar |

## Descripción General

En este laboratorio transformarás el sistema bancario distribuido en un sistema **observable y resiliente**. Configurarás Spring Boot Actuator en todos los microservicios para exponer health checks detallados y métricas operacionales, integrarás Micrometer con Prometheus para recolección de métricas de negocio personalizadas, e implementarás patrones de resiliencia con Resilience4j (retry, timeout y circuit breaker) en las llamadas WebClient entre servicios.

Al finalizar, tendrás un sistema que no solo funciona correctamente, sino que también puede **sobrevivir fallos parciales** y **reportar su propio estado de salud** en tiempo real, características esenciales en cualquier arquitectura de microservicios de producción.

## Objetivos de Aprendizaje

Al completar este laboratorio, serás capaz de:

- [ ] Configurar Spring Boot Actuator en cada microservicio con exposición selectiva de endpoints (`/actuator/health`, `/actuator/metrics`, `/actuator/prometheus`, `/actuator/info`)
- [ ] Implementar health indicators personalizados para verificar conexiones R2DBC y disponibilidad de servicios externos
- [ ] Registrar métricas de negocio con Micrometer: `Counter` para transacciones, `Timer` para latencia y `Gauge` para cuentas activas
- [ ] Implementar patrones de resiliencia con Resilience4j: retry con backoff exponencial, timeout de 5 segundos y circuit breaker en `transactions-service`
- [ ] Levantar Prometheus y Grafana via Docker Compose y verificar el scraping de métricas de cada servicio
- [ ] Validar el comportamiento del circuit breaker simulando la caída de `accounts-service`

## Prerrequisitos

### Conocimientos Requeridos

- Laboratorios 1, 2 y 3 completados con el sistema bancario funcional y con autenticación JWT operativa
- Comprensión básica de qué son métricas de aplicación (contadores, temporizadores, gauges) y su importancia en producción
- Conocimiento básico del formato de métricas Prometheus (labels, counters, histogramas)
- Familiaridad con Docker Compose para levantar servicios de infraestructura
- Comprensión del pipeline reactivo de WebFlux y operadores como `transformDeferred`

### Acceso Requerido

- Docker Desktop 4.25+ corriendo con capacidad de levantar al menos 5 contenedores simultáneamente
- Puertos disponibles: 8080 (Gateway), 8081 (Accounts), 8082 (Transactions), 8083 (Auth), 9090 (Prometheus), 3000 (Grafana)
- Acceso a Internet para descargar imágenes Docker de Prometheus y Grafana
- IntelliJ IDEA con los proyectos de los laboratorios anteriores abiertos

## Entorno de Laboratorio

### Requisitos de Hardware

| Componente | Especificación |
|-----------|----------------|
| Procesador | Intel Core i5 / AMD Ryzen 5 de 8ª gen o superior (mínimo 4 núcleos) |
| RAM | Mínimo 16 GB (recomendado 32 GB con todos los servicios activos) |
| Almacenamiento | Mínimo 5 GB libres adicionales para imágenes Prometheus y Grafana |
| Red | Conexión estable para descarga de imágenes Docker Hub |

### Requisitos de Software

| Software | Versión | Propósito |
|----------|---------|-----------|
| Java JDK | 17 LTS o 21 LTS | Compilación y ejecución de microservicios |
| Apache Maven | 3.9.x | Gestión de dependencias y construcción |
| Spring Boot | 3.2.x | Framework base de todos los microservicios |
| Spring Boot Actuator | 3.2.x | Endpoints de monitoreo y salud |
| Micrometer | 1.12.x | Instrumentación de métricas |
| Micrometer Registry Prometheus | 1.12.x | Exportación de métricas en formato Prometheus |
| Resilience4j | 2.x (módulo reactor) | Patrones de resiliencia reactivos |
| Docker Desktop | 4.25+ | Contenerización de infraestructura |
| Prometheus | 2.47 (vía Docker) | Recolección y almacenamiento de métricas |
| Grafana | 10.x (vía Docker) | Visualización de dashboards de métricas |
| Postman / curl | 10.x / 7.x+ | Pruebas de endpoints y validación |

### Configuración Inicial

Antes de comenzar, verifica que el sistema del laboratorio anterior esté funcionando correctamente:

```bash
# Verificar que los contenedores de base de datos están corriendo
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Verificar que los servicios responden (ajusta el token JWT si es necesario)
curl -s http://localhost:8083/auth/health
curl -s http://localhost:8081/actuator/health 2>/dev/null || echo "Actuator no configurado aún - esto es esperado"
curl -s http://localhost:8082/actuator/health 2>/dev/null || echo "Actuator no configurado aún - esto es esperado"

# Verificar estructura de directorios del proyecto
ls -la
# Deberías ver: api-gateway/, auth-service/, accounts-service/, transactions-service/, docker-compose.yml
```

Si algún servicio no responde, consulta la sección de **Solución de Problemas** al final de este laboratorio o solicita el código de solución del Lab 3 al instructor.

## Instrucciones Paso a Paso

### Paso 1: Agregar Dependencias de Actuator y Micrometer en Todos los Servicios

**Objetivo:** Incluir las dependencias necesarias de Spring Boot Actuator, Micrometer y Resilience4j en los `pom.xml` de cada microservicio.

**Instrucciones:**

1. Abre el `pom.xml` de **`accounts-service`** y agrega las siguientes dependencias dentro del bloque `<dependencies>`:

   ```xml
   <!-- accounts-service/pom.xml -->
   
   <!-- Spring Boot Actuator para endpoints de monitoreo -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   
   <!-- Micrometer Registry Prometheus para exportar métricas en formato Prometheus -->
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

2. Abre el `pom.xml` de **`transactions-service`** y agrega las dependencias de Actuator, Micrometer y Resilience4j:

   ```xml
   <!-- transactions-service/pom.xml -->
   
   <!-- Spring Boot Actuator -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   
   <!-- Micrometer Registry Prometheus -->
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   
   <!-- Resilience4j Spring Boot Starter (incluye autoconfiguración) -->
   <dependency>
       <groupId>io.github.resilience4j</groupId>
       <artifactId>resilience4j-spring-boot3</artifactId>
       <version>2.1.0</version>
   </dependency>
   
   <!-- Módulo de Resilience4j específico para Project Reactor (OBLIGATORIO para WebFlux) -->
   <dependency>
       <groupId>io.github.resilience4j</groupId>
       <artifactId>resilience4j-reactor</artifactId>
       <version>2.1.0</version>
   </dependency>
   
   <!-- AOP necesario para anotaciones de Resilience4j -->
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-aop</artifactId>
   </dependency>
   ```

3. Agrega también Actuator al **`auth-service`**:

   ```xml
   <!-- auth-service/pom.xml -->
   
   <dependency>
       <groupId>org.springframework.boot</groupId>
       <artifactId>spring-boot-starter-actuator</artifactId>
   </dependency>
   
   <dependency>
       <groupId>io.micrometer</groupId>
       <artifactId>micrometer-registry-prometheus</artifactId>
   </dependency>
   ```

4. Verifica que las dependencias se resuelven correctamente compilando cada módulo:

   ```bash
   # Desde el directorio raíz del proyecto
   cd accounts-service && mvn dependency:resolve -q && echo "accounts-service OK" && cd ..
   cd transactions-service && mvn dependency:resolve -q && echo "transactions-service OK" && cd ..
   cd auth-service && mvn dependency:resolve -q && echo "auth-service OK" && cd ..
   ```

**Salida Esperada:**

```
accounts-service OK
transactions-service OK
auth-service OK
```

**Verificación:**

- Sin errores de resolución de dependencias en ningún módulo
- Maven descarga correctamente los artefactos de `resilience4j-spring-boot3` y `resilience4j-reactor`

---

### Paso 2: Configurar Actuator en application.properties de Cada Servicio

**Objetivo:** Configurar qué endpoints de Actuator se exponen en cada servicio, con separación del puerto de gestión y habilitación de detalles de health.

**Instrucciones:**

1. Abre `accounts-service/src/main/resources/application.properties` y agrega la configuración de Actuator al final del archivo:

   ```properties
   # ============================================================
   # SPRING BOOT ACTUATOR - accounts-service
   # ============================================================
   
   # Puerto de gestión separado del puerto de aplicación (buena práctica de seguridad)
   management.server.port=8091
   
   # Endpoints expuestos via HTTP (solo los necesarios, nunca exponer 'shutdown' en producción)
   management.endpoints.web.exposure.include=health,info,metrics,prometheus,env
   
   # Mostrar detalles completos del health check (útil en desarrollo; en producción usar 'when-authorized')
   management.endpoint.health.show-details=always
   
   # Mostrar componentes individuales del health check
   management.endpoint.health.show-components=always
   
   # Habilitar endpoint de Prometheus
   management.endpoint.prometheus.enabled=true
   
   # Información del servicio expuesta en /actuator/info
   management.info.env.enabled=true
   info.app.name=Accounts Service
   info.app.version=1.0.0
   info.app.description=Microservicio de gestión de cuentas bancarias
   info.app.java.version=17
   
   # Etiquetas comunes para todas las métricas de este servicio (útil en Prometheus para filtrar por servicio)
   management.metrics.tags.application=accounts-service
   management.metrics.tags.environment=development
   ```

2. Abre `transactions-service/src/main/resources/application.properties` y agrega:

   ```properties
   # ============================================================
   # SPRING BOOT ACTUATOR - transactions-service
   # ============================================================
   
   management.server.port=8092
   management.endpoints.web.exposure.include=health,info,metrics,prometheus,env
   management.endpoint.health.show-details=always
   management.endpoint.health.show-components=always
   management.endpoint.prometheus.enabled=true
   management.info.env.enabled=true
   
   info.app.name=Transactions Service
   info.app.version=1.0.0
   info.app.description=Microservicio de gestión de transacciones bancarias
   info.app.java.version=17
   
   management.metrics.tags.application=transactions-service
   management.metrics.tags.environment=development
   
   # ============================================================
   # RESILIENCE4J - Configuración de Circuit Breaker, Retry y Timeout
   # ============================================================
   
   # --- Circuit Breaker para llamadas a accounts-service ---
   resilience4j.circuitbreaker.instances.accountsService.sliding-window-type=COUNT_BASED
   resilience4j.circuitbreaker.instances.accountsService.sliding-window-size=5
   resilience4j.circuitbreaker.instances.accountsService.failure-rate-threshold=60
   resilience4j.circuitbreaker.instances.accountsService.wait-duration-in-open-state=30s
   resilience4j.circuitbreaker.instances.accountsService.permitted-number-of-calls-in-half-open-state=2
   resilience4j.circuitbreaker.instances.accountsService.automatic-transition-from-open-to-half-open-enabled=true
   resilience4j.circuitbreaker.instances.accountsService.register-health-indicator=true
   
   # --- Retry para llamadas a accounts-service ---
   resilience4j.retry.instances.accountsService.max-attempts=3
   resilience4j.retry.instances.accountsService.wait-duration=500ms
   resilience4j.retry.instances.accountsService.enable-exponential-backoff=true
   resilience4j.retry.instances.accountsService.exponential-backoff-multiplier=2
   resilience4j.retry.instances.accountsService.retry-exceptions=java.io.IOException,java.util.concurrent.TimeoutException,org.springframework.web.reactive.function.client.WebClientRequestException
   
   # --- Timeout para llamadas a accounts-service ---
   resilience4j.timelimiter.instances.accountsService.timeout-duration=5s
   resilience4j.timelimiter.instances.accountsService.cancel-running-future=true
   ```

3. Abre `auth-service/src/main/resources/application.properties` y agrega:

   ```properties
   # ============================================================
   # SPRING BOOT ACTUATOR - auth-service
   # ============================================================
   
   management.server.port=8093
   management.endpoints.web.exposure.include=health,info,metrics,prometheus
   management.endpoint.health.show-details=always
   management.endpoint.prometheus.enabled=true
   management.info.env.enabled=true
   
   info.app.name=Auth Service
   info.app.version=1.0.0
   info.app.description=Microservicio de autenticación JWT
   
   management.metrics.tags.application=auth-service
   management.metrics.tags.environment=development
   ```

4. Reinicia los servicios y verifica que los endpoints de Actuator responden:

   ```bash
   # Verificar health de accounts-service (puerto de gestión 8091)
   curl -s http://localhost:8091/actuator/health | python3 -m json.tool
   
   # Verificar health de transactions-service (puerto de gestión 8092)
   curl -s http://localhost:8092/actuator/health | python3 -m json.tool
   
   # Verificar health de auth-service (puerto de gestión 8093)
   curl -s http://localhost:8093/actuator/health | python3 -m json.tool
   ```

**Salida Esperada:**

```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "validationQuery": "isValid()"
            }
        },
        "diskSpace": {
            "status": "UP"
        },
        "ping": {
            "status": "UP"
        }
    }
}
```

**Verificación:**

- El campo `"status"` debe ser `"UP"` en los tres servicios
- El componente `"db"` debe aparecer con estado `"UP"` en `accounts-service` y `transactions-service`
- Los endpoints responden en los puertos de gestión (8091, 8092, 8093), no en los puertos de aplicación

---

### Paso 3: Implementar Health Indicators Personalizados

**Objetivo:** Crear health indicators que verifiquen la conexión R2DBC y la disponibilidad de servicios externos, proporcionando información detallada sobre las dependencias del servicio.

**Instrucciones:**

1. Crea el archivo `DatabaseHealthIndicator.java` en `accounts-service`:

   ```bash
   mkdir -p accounts-service/src/main/java/com/banco/accounts/health
   touch accounts-service/src/main/java/com/banco/accounts/health/DatabaseHealthIndicator.java
   ```

2. Implementa el `DatabaseHealthIndicator` en `accounts-service`:

   ```java
   // accounts-service/src/main/java/com/banco/accounts/health/DatabaseHealthIndicator.java
   package com.banco.accounts.health;
   
   import io.r2dbc.spi.ConnectionFactory;
   import org.springframework.boot.actuate.health.Health;
   import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
   import org.springframework.r2dbc.core.DatabaseClient;
   import org.springframework.stereotype.Component;
   import reactor.core.publisher.Mono;
   
   @Component("r2dbcDatabase")
   public class DatabaseHealthIndicator implements ReactiveHealthIndicator {
   
       private final DatabaseClient databaseClient;
   
       public DatabaseHealthIndicator(DatabaseClient databaseClient) {
           this.databaseClient = databaseClient;
       }
   
       @Override
       public Mono<Health> health() {
           return checkDatabaseConnection()
               .map(result -> Health.up()
                   .withDetail("database", "PostgreSQL")
                   .withDetail("query", "SELECT 1")
                   .withDetail("result", result)
                   .withDetail("service", "accounts-service")
                   .build())
               .onErrorResume(ex -> Mono.just(
                   Health.down()
                       .withDetail("database", "PostgreSQL")
                       .withDetail("error", ex.getMessage())
                       .withException(ex)
                       .build()
               ));
       }
   
       private Mono<String> checkDatabaseConnection() {
           return databaseClient.sql("SELECT 1 AS health_check")
               .map(row -> row.get("health_check", Integer.class))
               .one()
               .map(result -> "OK - resultado: " + result)
               .timeout(java.time.Duration.ofSeconds(3));
       }
   }
   ```

3. Crea el directorio y archivo para el health indicator de `transactions-service`:

   ```bash
   mkdir -p transactions-service/src/main/java/com/banco/transactions/health
   touch transactions-service/src/main/java/com/banco/transactions/health/DatabaseHealthIndicator.java
   touch transactions-service/src/main/java/com/banco/transactions/health/AccountsServiceHealthIndicator.java
   ```

4. Implementa el `DatabaseHealthIndicator` en `transactions-service`:

   ```java
   // transactions-service/src/main/java/com/banco/transactions/health/DatabaseHealthIndicator.java
   package com.banco.transactions.health;
   
   import org.springframework.boot.actuate.health.Health;
   import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
   import org.springframework.r2dbc.core.DatabaseClient;
   import org.springframework.stereotype.Component;
   import reactor.core.publisher.Mono;
   
   @Component("r2dbcDatabase")
   public class DatabaseHealthIndicator implements ReactiveHealthIndicator {
   
       private final DatabaseClient databaseClient;
   
       public DatabaseHealthIndicator(DatabaseClient databaseClient) {
           this.databaseClient = databaseClient;
       }
   
       @Override
       public Mono<Health> health() {
           return databaseClient.sql("SELECT 1 AS health_check")
               .map(row -> row.get("health_check", Integer.class))
               .one()
               .map(result -> Health.up()
                   .withDetail("database", "PostgreSQL")
                   .withDetail("service", "transactions-service")
                   .withDetail("status", "Conexión activa")
                   .build())
               .onErrorResume(ex -> Mono.just(
                   Health.down()
                       .withDetail("database", "PostgreSQL")
                       .withDetail("error", ex.getMessage())
                       .build()
               ))
               .timeout(java.time.Duration.ofSeconds(3));
       }
   }
   ```

5. Implementa el `AccountsServiceHealthIndicator` en `transactions-service` para verificar la disponibilidad del servicio externo:

   ```java
   // transactions-service/src/main/java/com/banco/transactions/health/AccountsServiceHealthIndicator.java
   package com.banco.transactions.health;
   
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.boot.actuate.health.Health;
   import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
   import org.springframework.stereotype.Component;
   import org.springframework.web.reactive.function.client.WebClient;
   import reactor.core.publisher.Mono;
   
   import java.time.Duration;
   
   @Component("accountsService")
   public class AccountsServiceHealthIndicator implements ReactiveHealthIndicator {
   
       private final WebClient webClient;
       private final String accountsServiceUrl;
   
       public AccountsServiceHealthIndicator(
               WebClient.Builder webClientBuilder,
               @Value("${services.accounts.url:http://localhost:8091}") String accountsServiceUrl) {
           this.accountsServiceUrl = accountsServiceUrl;
           this.webClient = webClientBuilder
               .baseUrl(accountsServiceUrl)
               .build();
       }
   
       @Override
       public Mono<Health> health() {
           return webClient.get()
               .uri("/actuator/health")
               .retrieve()
               .bodyToMono(String.class)
               .timeout(Duration.ofSeconds(3))
               .map(response -> Health.up()
                   .withDetail("accountsService", "Disponible")
                   .withDetail("url", accountsServiceUrl + "/actuator/health")
                   .build())
               .onErrorResume(ex -> Mono.just(
                   Health.down()
                       .withDetail("accountsService", "No disponible")
                       .withDetail("url", accountsServiceUrl + "/actuator/health")
                       .withDetail("error", ex.getMessage())
                       .build()
               ));
       }
   }
   ```

6. Agrega la propiedad de URL del servicio de cuentas en `transactions-service/src/main/resources/application.properties`:

   ```properties
   # URL del accounts-service (puerto de gestión para health check)
   # Perfil local: usa localhost
   services.accounts.url=http://localhost:8091
   # Perfil Docker: usar nombre del servicio Docker
   # services.accounts.url=http://accounts-service:8091
   ```

7. Reinicia `accounts-service` y `transactions-service`, luego verifica los health checks detallados:

   ```bash
   # Health check completo de accounts-service
   curl -s http://localhost:8091/actuator/health | python3 -m json.tool
   
   # Health check completo de transactions-service (incluye verificación de accounts-service)
   curl -s http://localhost:8092/actuator/health | python3 -m json.tool
   ```

**Salida Esperada para `transactions-service`:**

```json
{
    "status": "UP",
    "components": {
        "accountsService": {
            "status": "UP",
            "details": {
                "accountsService": "Disponible",
                "url": "http://localhost:8091/actuator/health"
            }
        },
        "r2dbcDatabase": {
            "status": "UP",
            "details": {
                "database": "PostgreSQL",
                "service": "transactions-service",
                "status": "Conexión activa"
            }
        },
        "diskSpace": {
            "status": "UP"
        },
        "ping": {
            "status": "UP"
        }
    }
}
```

**Verificación:**

- El componente `"accountsService"` aparece en el health de `transactions-service`
- El componente `"r2dbcDatabase"` muestra detalles de la conexión PostgreSQL
- Todos los componentes tienen estado `"UP"`

---

### Paso 4: Implementar Métricas de Negocio con Micrometer

**Objetivo:** Registrar métricas personalizadas de negocio en `accounts-service` y `transactions-service` usando Counter, Timer y Gauge de Micrometer.

**Instrucciones:**

1. Crea el archivo de métricas para `accounts-service`:

   ```bash
   touch accounts-service/src/main/java/com/banco/accounts/metrics/AccountsMetrics.java
   ```

2. Implementa la clase de métricas en `accounts-service`:

   ```java
   // accounts-service/src/main/java/com/banco/accounts/metrics/AccountsMetrics.java
   package com.banco.accounts.metrics;
   
   import io.micrometer.core.instrument.Counter;
   import io.micrometer.core.instrument.Gauge;
   import io.micrometer.core.instrument.MeterRegistry;
   import io.micrometer.core.instrument.Timer;
   import org.springframework.stereotype.Component;
   
   import java.util.concurrent.atomic.AtomicInteger;
   
   @Component
   public class AccountsMetrics {
   
       // Counter: total de cuentas creadas desde que inició el servicio
       private final Counter cuentasCreadas;
   
       // Counter: total de consultas de saldo realizadas
       private final Counter consultasSaldo;
   
       // Counter: total de errores al procesar operaciones de cuentas
       private final Counter erroresCuentas;
   
       // Timer: duración de las consultas de saldo (mide latencia)
       private final Timer timerConsultaSaldo;
   
       // Gauge: número actual de cuentas activas (valor que sube y baja)
       private final AtomicInteger cuentasActivas;
   
       public AccountsMetrics(MeterRegistry registry) {
           // Registrar counter de cuentas creadas
           this.cuentasCreadas = Counter.builder("banco.cuentas.creadas.total")
               .description("Número total de cuentas bancarias creadas")
               .tag("servicio", "accounts-service")
               .register(registry);
   
           // Registrar counter de consultas de saldo
           this.consultasSaldo = Counter.builder("banco.cuentas.consultas.saldo.total")
               .description("Número total de consultas de saldo realizadas")
               .tag("servicio", "accounts-service")
               .register(registry);
   
           // Registrar counter de errores
           this.erroresCuentas = Counter.builder("banco.cuentas.errores.total")
               .description("Número total de errores en operaciones de cuentas")
               .tag("servicio", "accounts-service")
               .register(registry);
   
           // Registrar timer de consulta de saldo
           this.timerConsultaSaldo = Timer.builder("banco.cuentas.consulta.saldo.duracion")
               .description("Duración de las consultas de saldo en milisegundos")
               .tag("servicio", "accounts-service")
               .publishPercentiles(0.5, 0.95, 0.99)
               .register(registry);
   
           // Registrar gauge de cuentas activas
           this.cuentasActivas = new AtomicInteger(0);
           Gauge.builder("banco.cuentas.activas", cuentasActivas, AtomicInteger::get)
               .description("Número actual de cuentas bancarias activas")
               .tag("servicio", "accounts-service")
               .register(registry);
       }
   
       public void incrementarCuentasCreadas() {
           cuentasCreadas.increment();
       }
   
       public void incrementarConsultasSaldo() {
           consultasSaldo.increment();
       }
   
       public void incrementarErrores() {
           erroresCuentas.increment();
       }
   
       public Timer getTimerConsultaSaldo() {
           return timerConsultaSaldo;
       }
   
       public void setCuentasActivas(int cantidad) {
           cuentasActivas.set(cantidad);
       }
   
       public void incrementarCuentasActivas() {
           cuentasActivas.incrementAndGet();
       }
   }
   ```

3. Crea el archivo de métricas para `transactions-service`:

   ```bash
   touch transactions-service/src/main/java/com/banco/transactions/metrics/TransactionsMetrics.java
   ```

4. Implementa la clase de métricas en `transactions-service`:

   ```java
   // transactions-service/src/main/java/com/banco/transactions/metrics/TransactionsMetrics.java
   package com.banco.transactions.metrics;
   
   import io.micrometer.core.instrument.Counter;
   import io.micrometer.core.instrument.MeterRegistry;
   import io.micrometer.core.instrument.Timer;
   import org.springframework.stereotype.Component;
   
   @Component
   public class TransactionsMetrics {
   
       // Counter: total de transacciones procesadas por tipo
       private final Counter transaccionesDeposito;
       private final Counter transaccionesRetiro;
       private final Counter transaccionesTransferencia;
   
       // Counter: transacciones fallidas
       private final Counter transaccionesFallidas;
   
       // Timer: latencia de llamadas al accounts-service
       private final Timer timerLlamadaAccountsService;
   
       // Counter: intentos de retry al accounts-service
       private final Counter reintentosAccountsService;
   
       // Counter: veces que el circuit breaker se abrió
       private final Counter circuitBreakerAbierto;
   
       public TransactionsMetrics(MeterRegistry registry) {
           this.transaccionesDeposito = Counter.builder("banco.transacciones.total")
               .description("Total de transacciones procesadas")
               .tag("tipo", "deposito")
               .tag("servicio", "transactions-service")
               .register(registry);
   
           this.transaccionesRetiro = Counter.builder("banco.transacciones.total")
               .description("Total de transacciones procesadas")
               .tag("tipo", "retiro")
               .tag("servicio", "transactions-service")
               .register(registry);
   
           this.transaccionesTransferencia = Counter.builder("banco.transacciones.total")
               .description("Total de transacciones procesadas")
               .tag("tipo", "transferencia")
               .tag("servicio", "transactions-service")
               .register(registry);
   
           this.transaccionesFallidas = Counter.builder("banco.transacciones.fallidas.total")
               .description("Total de transacciones que fallaron")
               .tag("servicio", "transactions-service")
               .register(registry);
   
           this.timerLlamadaAccountsService = Timer.builder("banco.transactions.accounts.llamada.duracion")
               .description("Duración de llamadas WebClient al accounts-service")
               .tag("servicio", "transactions-service")
               .publishPercentiles(0.5, 0.95, 0.99)
               .register(registry);
   
           this.reintentosAccountsService = Counter.builder("banco.resilience.reintentos.total")
               .description("Número de reintentos al accounts-service")
               .tag("servicio", "transactions-service")
               .tag("patron", "retry")
               .register(registry);
   
           this.circuitBreakerAbierto = Counter.builder("banco.resilience.circuit.breaker.abierto.total")
               .description("Número de veces que el circuit breaker se abrió")
               .tag("servicio", "transactions-service")
               .tag("patron", "circuit-breaker")
               .register(registry);
       }
   
       public void registrarDeposito() { transaccionesDeposito.increment(); }
       public void registrarRetiro() { transaccionesRetiro.increment(); }
       public void registrarTransferencia() { transaccionesTransferencia.increment(); }
       public void registrarTransaccionFallida() { transaccionesFallidas.increment(); }
       public void registrarReintento() { reintentosAccountsService.increment(); }
       public void registrarCircuitBreakerAbierto() { circuitBreakerAbierto.increment(); }
       public Timer getTimerLlamadaAccountsService() { return timerLlamadaAccountsService; }
   }
   ```

5. Integra las métricas en el servicio de cuentas. Localiza la clase de servicio principal en `accounts-service` (probablemente `AccountService.java` o `CuentaService.java`) y modifícala para inyectar y usar `AccountsMetrics`:

   ```java
   // Fragmento a agregar/modificar en accounts-service/.../service/AccountService.java
   // (Adapta el nombre de la clase y paquete según tu implementación del Lab 2)
   
   import com.banco.accounts.metrics.AccountsMetrics;
   import reactor.core.publisher.Mono;
   
   // En el constructor o mediante @Autowired:
   private final AccountsMetrics metrics;
   
   // Ejemplo de uso en el método de crear cuenta:
   public Mono<Account> crearCuenta(Account cuenta) {
       return accountRepository.save(cuenta)
           .doOnSuccess(saved -> {
               metrics.incrementarCuentasCreadas();
               metrics.incrementarCuentasActivas();
           })
           .doOnError(ex -> metrics.incrementarErrores());
   }
   
   // Ejemplo de uso en consulta de saldo con Timer:
   public Mono<Account> consultarCuenta(String id) {
       return metrics.getTimerConsultaSaldo().recordCallable(() ->
           accountRepository.findById(id)
               .doOnSuccess(acc -> metrics.incrementarConsultasSaldo())
               .doOnError(ex -> metrics.incrementarErrores())
       );
   }
   ```

   > **Nota:** El fragmento anterior es orientativo. Adapta los nombres de métodos y repositorios a tu implementación específica del Lab 2. Lo importante es que `AccountsMetrics` sea inyectado y sus métodos sean llamados en los puntos correctos del flujo reactivo.

6. Verifica que las métricas personalizadas aparecen en el endpoint de Actuator:

   ```bash
   # Listar todas las métricas disponibles en accounts-service
   curl -s http://localhost:8091/actuator/metrics | python3 -m json.tool | grep "banco"
   
   # Consultar el valor de la métrica de cuentas creadas
   curl -s "http://localhost:8091/actuator/metrics/banco.cuentas.creadas.total" | python3 -m json.tool
   
   # Consultar el gauge de cuentas activas
   curl -s "http://localhost:8091/actuator/metrics/banco.cuentas.activas" | python3 -m json.tool
   ```

**Salida Esperada:**

```json
{
    "name": "banco.cuentas.creadas.total",
    "description": "Número total de cuentas bancarias creadas",
    "baseUnit": null,
    "measurements": [
        {
            "statistic": "COUNT",
            "value": 0.0
        }
    ],
    "availableTags": [
        {
            "tag": "servicio",
            "values": ["accounts-service"]
        }
    ]
}
```

**Verificación:**

- Las métricas `banco.cuentas.creadas.total`, `banco.cuentas.activas` y `banco.cuentas.consultas.saldo.total` aparecen en el listado
- El endpoint `/actuator/metrics/{nombre}` retorna la estructura correcta con `measurements`

---

### Paso 5: Implementar Resiliencia con Resilience4j en transactions-service

**Objetivo:** Aplicar patrones de retry con backoff exponencial, timeout y circuit breaker en el `WebClient` de `transactions-service` para las llamadas a `accounts-service`.

**Instrucciones:**

1. Localiza o crea la clase de cliente WebClient en `transactions-service`. Crea el archivo si no existe:

   ```bash
   mkdir -p transactions-service/src/main/java/com/banco/transactions/client
   touch transactions-service/src/main/java/com/banco/transactions/client/AccountsServiceClient.java
   ```

2. Implementa el cliente con Resilience4j integrado en el pipeline reactivo:

   ```java
   // transactions-service/src/main/java/com/banco/transactions/client/AccountsServiceClient.java
   package com.banco.transactions.client;
   
   import com.banco.transactions.metrics.TransactionsMetrics;
   import io.github.resilience4j.circuitbreaker.CircuitBreaker;
   import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
   import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
   import io.github.resilience4j.reactor.retry.RetryOperator;
   import io.github.resilience4j.reactor.timelimiter.TimeLimiterOperator;
   import io.github.resilience4j.retry.Retry;
   import io.github.resilience4j.retry.RetryRegistry;
   import io.github.resilience4j.timelimiter.TimeLimiter;
   import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
   import org.slf4j.Logger;
   import org.slf4j.LoggerFactory;
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.http.HttpHeaders;
   import org.springframework.stereotype.Component;
   import org.springframework.web.reactive.function.client.WebClient;
   import reactor.core.publisher.Mono;
   
   import java.util.Map;
   
   @Component
   public class AccountsServiceClient {
   
       private static final Logger log = LoggerFactory.getLogger(AccountsServiceClient.class);
       private static final String INSTANCE_NAME = "accountsService";
   
       private final WebClient webClient;
       private final CircuitBreaker circuitBreaker;
       private final Retry retry;
       private final TimeLimiter timeLimiter;
       private final TransactionsMetrics metrics;
   
       public AccountsServiceClient(
               WebClient.Builder webClientBuilder,
               CircuitBreakerRegistry circuitBreakerRegistry,
               RetryRegistry retryRegistry,
               TimeLimiterRegistry timeLimiterRegistry,
               TransactionsMetrics metrics,
               @Value("${services.accounts.app.url:http://localhost:8081}") String accountsServiceUrl) {
   
           this.webClient = webClientBuilder
               .baseUrl(accountsServiceUrl)
               .build();
   
           this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
           this.retry = retryRegistry.retry(INSTANCE_NAME);
           this.timeLimiter = timeLimiterRegistry.timeLimiter(INSTANCE_NAME);
           this.metrics = metrics;
   
           // Registrar listeners para el circuit breaker
           this.circuitBreaker.getEventPublisher()
               .onStateTransition(event -> {
                   log.warn("Circuit Breaker '{}' cambió de estado: {} -> {}",
                       INSTANCE_NAME,
                       event.getStateTransition().getFromState(),
                       event.getStateTransition().getToState());
                   if (event.getStateTransition().getToState() ==
                           io.github.resilience4j.circuitbreaker.CircuitBreaker.State.OPEN) {
                       metrics.registrarCircuitBreakerAbierto();
                   }
               });
   
           // Registrar listeners para retry
           this.retry.getEventPublisher()
               .onRetry(event -> {
                   log.warn("Reintento #{} al accounts-service. Causa: {}",
                       event.getNumberOfRetryAttempts(),
                       event.getLastThrowable() != null ? event.getLastThrowable().getMessage() : "desconocida");
                   metrics.registrarReintento();
               });
       }
   
       /**
        * Verifica si una cuenta existe en accounts-service.
        * Aplica el pipeline completo de resiliencia: TimeLimiter -> Retry -> CircuitBreaker
        */
       public Mono<Boolean> verificarCuentaExiste(String cuentaId, String authToken) {
           log.debug("Verificando existencia de cuenta: {} en accounts-service", cuentaId);
   
           return webClient.get()
               .uri("/api/accounts/{id}", cuentaId)
               .header(HttpHeaders.AUTHORIZATION, authToken)
               .retrieve()
               .bodyToMono(Map.class)
               .map(response -> true)
               .onErrorReturn(
                   ex -> ex instanceof org.springframework.web.reactive.function.client.WebClientResponseException.NotFound,
                   false
               )
               .transformDeferred(TimeLimiterOperator.of(timeLimiter))
               .transformDeferred(RetryOperator.of(retry))
               .transformDeferred(CircuitBreakerOperator.of(circuitBreaker))
               .doOnSuccess(result -> log.debug("Verificación de cuenta {} completada: {}", cuentaId, result))
               .doOnError(ex -> log.error("Error verificando cuenta {}: {}", cuentaId, ex.getMessage()))
               .onErrorResume(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class,
                   ex -> {
                       log.error("Circuit Breaker ABIERTO: llamada a accounts-service bloqueada para cuenta {}", cuentaId);
                       return Mono.error(new RuntimeException(
                           "El servicio de cuentas no está disponible temporalmente. " +
                           "Por favor intente más tarde.", ex));
                   });
       }
   
       /**
        * Obtiene el estado del circuit breaker para monitoreo
        */
       public String getCircuitBreakerState() {
           return circuitBreaker.getState().name();
       }
   }
   ```

3. Agrega la propiedad de URL de la aplicación (no del puerto de gestión) en `transactions-service/src/main/resources/application.properties`:

   ```properties
   # URL de la aplicación de accounts-service (puerto 8081, no el de gestión 8091)
   services.accounts.app.url=http://localhost:8081
   # Para perfil Docker:
   # services.accounts.app.url=http://accounts-service:8081
   ```

4. Crea un endpoint de diagnóstico en `transactions-service` para consultar el estado del circuit breaker. Crea o modifica el controlador:

   ```bash
   touch transactions-service/src/main/java/com/banco/transactions/controller/DiagnosticsController.java
   ```

   ```java
   // transactions-service/src/main/java/com/banco/transactions/controller/DiagnosticsController.java
   package com.banco.transactions.controller;
   
   import com.banco.transactions.client.AccountsServiceClient;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.GetMapping;
   import org.springframework.web.bind.annotation.RequestMapping;
   import org.springframework.web.bind.annotation.RestController;
   import reactor.core.publisher.Mono;
   
   import java.util.Map;
   
   @RestController
   @RequestMapping("/api/diagnostics")
   public class DiagnosticsController {
   
       private final AccountsServiceClient accountsServiceClient;
   
       public DiagnosticsController(AccountsServiceClient accountsServiceClient) {
           this.accountsServiceClient = accountsServiceClient;
       }
   
       @GetMapping("/circuit-breaker")
       public Mono<ResponseEntity<Map<String, String>>> getCircuitBreakerStatus() {
           return Mono.just(ResponseEntity.ok(Map.of(
               "circuitBreaker", "accountsService",
               "estado", accountsServiceClient.getCircuitBreakerState(),
               "descripcion", getDescripcionEstado(accountsServiceClient.getCircuitBreakerState())
           )));
       }
   
       private String getDescripcionEstado(String estado) {
           return switch (estado) {
               case "CLOSED" -> "Normal: Las llamadas pasan directamente al servicio";
               case "OPEN" -> "Abierto: Las llamadas son bloqueadas para proteger el sistema";
               case "HALF_OPEN" -> "Semi-abierto: Probando si el servicio se recuperó";
               default -> "Estado desconocido";
           };
       }
   }
   ```

5. Compila y reinicia `transactions-service`:

   ```bash
   cd transactions-service && mvn clean compile -q && echo "Compilación exitosa" && cd ..
   ```

6. Verifica el estado del circuit breaker:

   ```bash
   # Consultar estado del circuit breaker (sin autenticación en endpoint de diagnóstico)
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
   ```

**Salida Esperada:**

```json
{
    "circuitBreaker": "accountsService",
    "estado": "CLOSED",
    "descripcion": "Normal: Las llamadas pasan directamente al servicio"
}
```

**Verificación:**

- El endpoint `/api/diagnostics/circuit-breaker` responde con estado `"CLOSED"`
- La aplicación compila sin errores con las dependencias de Resilience4j
- Los logs muestran la inicialización de los registros de Resilience4j

---

### Paso 6: Configurar Prometheus y Grafana con Docker Compose

**Objetivo:** Levantar Prometheus y Grafana como contenedores Docker, configurar Prometheus para hacer scraping de los endpoints `/actuator/prometheus` de cada microservicio, y verificar que las métricas son recolectadas correctamente.

**Instrucciones:**

1. Crea el directorio de configuración de Prometheus:

   ```bash
   mkdir -p monitoring/prometheus
   mkdir -p monitoring/grafana/dashboards
   mkdir -p monitoring/grafana/provisioning/datasources
   mkdir -p monitoring/grafana/provisioning/dashboards
   ```

2. Crea el archivo de configuración de Prometheus:

   ```bash
   cat > monitoring/prometheus/prometheus.yml << 'EOF'
   # monitoring/prometheus/prometheus.yml
   global:
     scrape_interval: 15s
     evaluation_interval: 15s
     external_labels:
       monitor: 'banco-microservicios'
       environment: 'development'
   
   scrape_configs:
     # Prometheus se monitorea a sí mismo
     - job_name: 'prometheus'
       static_configs:
         - targets: ['localhost:9090']
   
     # Scraping de accounts-service (puerto de gestión 8091)
     - job_name: 'accounts-service'
       metrics_path: '/actuator/prometheus'
       scrape_interval: 15s
       static_configs:
         - targets: ['host.docker.internal:8091']
       relabel_configs:
         - source_labels: [__address__]
           target_label: instance
           replacement: 'accounts-service:8091'
   
     # Scraping de transactions-service (puerto de gestión 8092)
     - job_name: 'transactions-service'
       metrics_path: '/actuator/prometheus'
       scrape_interval: 15s
       static_configs:
         - targets: ['host.docker.internal:8092']
       relabel_configs:
         - source_labels: [__address__]
           target_label: instance
           replacement: 'transactions-service:8092'
   
     # Scraping de auth-service (puerto de gestión 8093)
     - job_name: 'auth-service'
       metrics_path: '/actuator/prometheus'
       scrape_interval: 15s
       static_configs:
         - targets: ['host.docker.internal:8093']
       relabel_configs:
         - source_labels: [__address__]
           target_label: instance
           replacement: 'auth-service:8093'
   EOF
   ```

   > **Nota para Linux:** En Linux, `host.docker.internal` puede no estar disponible por defecto. Si usas Linux, reemplaza `host.docker.internal` por la IP del host Docker (`172.17.0.1` normalmente) o usa la opción `--add-host=host.docker.internal:host-gateway` en el docker-compose.

3. Crea el archivo de datasource de Grafana:

   ```bash
   cat > monitoring/grafana/provisioning/datasources/prometheus.yml << 'EOF'
   # monitoring/grafana/provisioning/datasources/prometheus.yml
   apiVersion: 1
   
   datasources:
     - name: Prometheus
       type: prometheus
       access: proxy
       url: http://prometheus:9090
       isDefault: true
       editable: true
       jsonData:
         timeInterval: "15s"
   EOF
   ```

4. Crea el archivo de provisioning de dashboards de Grafana:

   ```bash
   cat > monitoring/grafana/provisioning/dashboards/dashboards.yml << 'EOF'
   # monitoring/grafana/provisioning/dashboards/dashboards.yml
   apiVersion: 1
   
   providers:
     - name: 'Banco Microservicios'
       orgId: 1
       folder: 'Banco'
       type: file
       disableDeletion: false
       updateIntervalSeconds: 30
       options:
         path: /etc/grafana/dashboards
   EOF
   ```

5. Crea un dashboard básico de Grafana en formato JSON:

   ```bash
   cat > monitoring/grafana/dashboards/banco-overview.json << 'EOF'
   {
     "id": null,
     "title": "Banco Microservicios - Overview",
     "tags": ["banco", "microservicios"],
     "timezone": "browser",
     "refresh": "30s",
     "time": { "from": "now-30m", "to": "now" },
     "panels": [
       {
         "id": 1,
         "title": "Total Transacciones por Tipo",
         "type": "stat",
         "gridPos": { "h": 4, "w": 6, "x": 0, "y": 0 },
         "targets": [
           {
             "expr": "sum(banco_transacciones_total) by (tipo)",
             "legendFormat": "{{tipo}}"
           }
         ]
       },
       {
         "id": 2,
         "title": "Estado del Circuit Breaker",
         "type": "stat",
         "gridPos": { "h": 4, "w": 6, "x": 6, "y": 0 },
         "targets": [
           {
             "expr": "resilience4j_circuitbreaker_state{name=\"accountsService\"}",
             "legendFormat": "Estado CB"
           }
         ]
       },
       {
         "id": 3,
         "title": "Latencia Consulta Saldo (p95)",
         "type": "gauge",
         "gridPos": { "h": 4, "w": 6, "x": 12, "y": 0 },
         "targets": [
           {
             "expr": "histogram_quantile(0.95, rate(banco_cuentas_consulta_saldo_duracion_seconds_bucket[5m]))",
             "legendFormat": "p95 latencia"
           }
         ]
       },
       {
         "id": 4,
         "title": "Reintentos al Accounts Service",
         "type": "timeseries",
         "gridPos": { "h": 6, "w": 12, "x": 0, "y": 4 },
         "targets": [
           {
             "expr": "rate(banco_resilience_reintentos_total[5m])",
             "legendFormat": "Reintentos/seg"
           }
         ]
       },
       {
         "id": 5,
         "title": "JVM Memory Usage",
         "type": "timeseries",
         "gridPos": { "h": 6, "w": 12, "x": 12, "y": 4 },
         "targets": [
           {
             "expr": "jvm_memory_used_bytes{area=\"heap\"}",
             "legendFormat": "{{application}} - Heap"
           }
         ]
       }
     ],
     "schemaVersion": 38,
     "version": 1
   }
   EOF
   ```

6. Actualiza el `docker-compose.yml` del proyecto raíz para agregar Prometheus y Grafana. Abre el archivo y agrega los servicios al final (antes del cierre de `services:`):

   ```yaml
   # Agregar al docker-compose.yml existente, dentro de la sección services:
   
     prometheus:
       image: prom/prometheus:v2.47.0
       container_name: banco-prometheus
       ports:
         - "9090:9090"
       volumes:
         - ./monitoring/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
         - prometheus_data:/prometheus
       command:
         - '--config.file=/etc/prometheus/prometheus.yml'
         - '--storage.tsdb.path=/prometheus'
         - '--web.console.libraries=/etc/prometheus/console_libraries'
         - '--web.console.templates=/etc/prometheus/consoles'
         - '--storage.tsdb.retention.time=7d'
         - '--web.enable-lifecycle'
       networks:
         - banco-network
       restart: unless-stopped
   
     grafana:
       image: grafana/grafana:10.2.0
       container_name: banco-grafana
       ports:
         - "3000:3000"
       environment:
         - GF_SECURITY_ADMIN_USER=admin
         - GF_SECURITY_ADMIN_PASSWORD=admin123
         - GF_USERS_ALLOW_SIGN_UP=false
         - GF_DASHBOARDS_DEFAULT_HOME_DASHBOARD_PATH=/etc/grafana/dashboards/banco-overview.json
       volumes:
         - grafana_data:/var/lib/grafana
         - ./monitoring/grafana/provisioning:/etc/grafana/provisioning:ro
         - ./monitoring/grafana/dashboards:/etc/grafana/dashboards:ro
       networks:
         - banco-network
       depends_on:
         - prometheus
       restart: unless-stopped
   
   # Agregar al final del docker-compose.yml, en la sección volumes:
   # volumes:
   #   prometheus_data:
   #   grafana_data:
   ```

   > **Nota:** Si tu `docker-compose.yml` ya tiene una sección `volumes:` y `networks:`, agrega solo los nuevos volúmenes y no dupliques la sección. Asegúrate de que `banco-network` sea la misma red que usan los demás servicios.

7. Levanta los nuevos contenedores de monitoreo:

   ```bash
   # Levantar solo Prometheus y Grafana (los demás ya deberían estar corriendo)
   docker-compose up -d prometheus grafana
   
   # Verificar que los contenedores están corriendo
   docker-compose ps prometheus grafana
   
   # Ver logs de Prometheus para confirmar configuración
   docker-compose logs --tail=20 prometheus
   ```

8. Verifica que Prometheus hace scraping de los servicios:

   ```bash
   # Esperar 20 segundos para que Prometheus haga el primer scrape
   sleep 20
   
   # Verificar targets en Prometheus via API
   curl -s "http://localhost:9090/api/v1/targets" | python3 -m json.tool | grep -A 3 '"health"'
   ```

**Salida Esperada:**

```
"health": "up",
"health": "up",
"health": "up",
```

**Verificación:**

- Prometheus está accesible en `http://localhost:9090`
- En la UI de Prometheus (`http://localhost:9090/targets`), los tres servicios aparecen con estado `UP`
- Grafana está accesible en `http://localhost:3000` (usuario: `admin`, contraseña: `admin123`)
- El dashboard "Banco Microservicios - Overview" aparece en la carpeta "Banco" de Grafana

---

### Paso 7: Verificar Métricas en Formato Prometheus

**Objetivo:** Confirmar que los endpoints `/actuator/prometheus` exponen métricas en el formato correcto que Prometheus puede recolectar, incluyendo las métricas de negocio personalizadas.

**Instrucciones:**

1. Consulta el endpoint de Prometheus de `accounts-service` y filtra las métricas de negocio:

   ```bash
   # Ver todas las métricas en formato Prometheus de accounts-service
   curl -s http://localhost:8091/actuator/prometheus | grep "banco_"
   ```

2. Consulta las métricas de Resilience4j expuestas por `transactions-service`:

   ```bash
   # Métricas del circuit breaker en formato Prometheus
   curl -s http://localhost:8092/actuator/prometheus | grep "resilience4j_circuitbreaker"
   
   # Métricas de retry
   curl -s http://localhost:8092/actuator/prometheus | grep "resilience4j_retry"
   
   # Métricas de negocio personalizadas
   curl -s http://localhost:8092/actuator/prometheus | grep "banco_"
   ```

3. Genera algo de tráfico para que las métricas tengan valores distintos de cero. Primero obtén un token JWT:

   ```bash
   # Obtener token JWT
   TOKEN=$(curl -s -X POST http://localhost:8083/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"password123"}' \
     | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
   
   echo "Token obtenido: ${TOKEN:0:50}..."
   
   # Realizar algunas consultas a accounts-service para generar métricas
   for i in {1..5}; do
     curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/accounts > /dev/null
     echo "Consulta $i realizada"
     sleep 1
   done
   ```

4. Verifica las métricas actualizadas:

   ```bash
   # Ver métricas de cuentas actualizadas
   curl -s "http://localhost:8091/actuator/metrics/banco.cuentas.consultas.saldo.total" | python3 -m json.tool
   
   # Ver métricas en formato Prometheus con valores
   curl -s http://localhost:8091/actuator/prometheus | grep "banco_cuentas"
   ```

**Salida Esperada:**

```
# HELP banco_cuentas_creadas_total Número total de cuentas bancarias creadas
# TYPE banco_cuentas_creadas_total counter
banco_cuentas_creadas_total{application="accounts-service",environment="development",servicio="accounts-service",} 0.0

# HELP banco_cuentas_consultas_saldo_total Número total de consultas de saldo realizadas
# TYPE banco_cuentas_consultas_saldo_total counter
banco_cuentas_consultas_saldo_total{application="accounts-service",environment="development",servicio="accounts-service",} 5.0

# HELP banco_cuentas_activas Número actual de cuentas bancarias activas
# TYPE banco_cuentas_activas gauge
banco_cuentas_activas{application="accounts-service",environment="development",servicio="accounts-service",} 0.0
```

**Verificación:**

- Las métricas `banco_cuentas_*` aparecen en el endpoint `/actuator/prometheus`
- El formato incluye comentarios `# HELP` y `# TYPE` correctos
- Los labels `application` y `environment` están presentes en todas las métricas
- Los valores de los counters aumentan con cada operación realizada

---

### Paso 8: Prueba de Resiliencia - Simulación de Caída de accounts-service

**Objetivo:** Verificar el comportamiento del circuit breaker simulando la caída de `accounts-service` y observar cómo `transactions-service` maneja los fallos con retry, timeout y finalmente abriendo el circuit breaker.

**Instrucciones:**

1. Verifica el estado inicial del circuit breaker (debe estar CLOSED):

   ```bash
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
   ```

2. Verifica las métricas del circuit breaker en Prometheus antes de la prueba:

   ```bash
   curl -s http://localhost:8092/actuator/prometheus | grep "resilience4j_circuitbreaker_state"
   ```

3. **Simula la caída de `accounts-service`** deteniendo el proceso (o si está en Docker, el contenedor):

   ```bash
   # Opción A: Si accounts-service corre como proceso Java local
   # Detén el proceso desde IntelliJ IDEA (botón Stop) o con:
   # kill $(lsof -ti:8081)
   
   # Opción B: Si accounts-service está en Docker
   docker-compose stop accounts-service
   echo "accounts-service detenido"
   
   # Verificar que ya no responde
   curl -s --connect-timeout 2 http://localhost:8081/api/accounts && echo "STILL UP" || echo "accounts-service DOWN - correcto"
   ```

4. Genera llamadas fallidas al `transactions-service` para activar el retry y el circuit breaker. Obtén un token primero:

   ```bash
   # Obtener token (auth-service debe seguir corriendo)
   TOKEN=$(curl -s -X POST http://localhost:8083/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"password123"}' \
     | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
   
   # Enviar múltiples solicitudes que requieren validar cuenta en accounts-service
   # Esto activará el retry (3 intentos por solicitud) y eventualmente el circuit breaker
   echo "=== Iniciando prueba de resiliencia ==="
   for i in {1..8}; do
     echo "--- Solicitud $i ---"
     RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
       -X POST http://localhost:8082/api/transactions \
       -H "Authorization: Bearer $TOKEN" \
       -H "Content-Type: application/json" \
       -d "{\"cuentaOrigenId\":\"cuenta-001\",\"monto\":100.00,\"tipo\":\"RETIRO\"}" \
       2>&1)
     echo "$RESPONSE"
     sleep 2
   done
   ```

5. Verifica el estado del circuit breaker después de los fallos:

   ```bash
   # El circuit breaker debería estar OPEN después de 5 fallos (según configuración)
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
   
   # Verificar métricas de Prometheus
   echo "=== Estado del Circuit Breaker en Prometheus ==="
   curl -s http://localhost:8092/actuator/prometheus | grep "resilience4j_circuitbreaker"
   
   echo "=== Reintentos registrados ==="
   curl -s "http://localhost:8092/actuator/metrics/banco.resilience.reintentos.total" | python3 -m json.tool
   
   echo "=== Circuit Breakers abiertos ==="
   curl -s "http://localhost:8092/actuator/metrics/banco.resilience.circuit.breaker.abierto.total" | python3 -m json.tool
   ```

6. **Restaura `accounts-service`** y observa la transición del circuit breaker a HALF_OPEN y luego a CLOSED:

   ```bash
   # Opción A: Reinicia el proceso desde IntelliJ IDEA
   
   # Opción B: Si estaba en Docker
   docker-compose start accounts-service
   echo "accounts-service reiniciado"
   
   # Esperar a que el circuit breaker pase a HALF_OPEN (30 segundos según configuración)
   echo "Esperando 35 segundos para que el circuit breaker intente recuperarse..."
   sleep 35
   
   # Verificar estado - debería ser HALF_OPEN o CLOSED
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
   
   # Enviar una solicitud de prueba para que el circuit breaker evalúe la recuperación
   curl -s -X POST http://localhost:8082/api/transactions \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"cuentaOrigenId":"cuenta-001","monto":50.00,"tipo":"RETIRO"}' | python3 -m json.tool
   
   # Verificar estado final - debería ser CLOSED
   sleep 5
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
   ```

7. Revisa los logs de `transactions-service` para ver el registro de eventos del circuit breaker:

   ```bash
   # Si está en Docker:
   docker-compose logs --tail=50 transactions-service | grep -E "Circuit Breaker|Reintento|OPEN|CLOSED|HALF_OPEN"
   
   # Si está como proceso local, revisa la consola de IntelliJ IDEA
   ```

**Salida Esperada durante la prueba:**

```json
{
    "circuitBreaker": "accountsService",
    "estado": "OPEN",
    "descripcion": "Abierto: Las llamadas son bloqueadas para proteger el sistema"
}
```

**Salida Esperada en logs:**

```
WARN  Circuit Breaker 'accountsService' cambió de estado: CLOSED -> OPEN
WARN  Reintento #1 al accounts-service. Causa: Connection refused
WARN  Reintento #2 al accounts-service. Causa: Connection refused
WARN  Reintento #3 al accounts-service. Causa: Connection refused
WARN  Circuit Breaker 'accountsService' cambió de estado: OPEN -> HALF_OPEN
WARN  Circuit Breaker 'accountsService' cambió de estado: HALF_OPEN -> CLOSED
```

**Verificación:**

- El circuit breaker transiciona a `OPEN` después de los fallos configurados
- Los reintentos se registran en los logs con el número de intento
- La métrica `banco.resilience.reintentos.total` aumenta con cada reintento
- La métrica `banco.resilience.circuit.breaker.abierto.total` aumenta cuando el CB se abre
- El circuit breaker regresa a `CLOSED` después de que `accounts-service` se recupera

---

### Paso 9: Consultar Métricas en Prometheus UI y Grafana

**Objetivo:** Navegar por la interfaz de Prometheus para ejecutar queries PromQL y verificar el dashboard de Grafana con las métricas recolectadas.

**Instrucciones:**

1. Abre la interfaz de Prometheus en tu navegador:

   ```
   http://localhost:9090
   ```

2. Ve a **Status > Targets** (`http://localhost:9090/targets`) y verifica que los tres servicios aparecen con estado `UP`.

3. Ejecuta las siguientes queries PromQL en la interfaz de Prometheus (pestaña **Graph**):

   ```promql
   # Query 1: Total de transacciones por tipo
   sum(banco_transacciones_total) by (tipo)
   ```

   ```promql
   # Query 2: Estado del circuit breaker (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
   resilience4j_circuitbreaker_state{name="accountsService"}
   ```

   ```promql
   # Query 3: Tasa de reintentos por segundo en los últimos 5 minutos
   rate(banco_resilience_reintentos_total[5m])
   ```

   ```promql
   # Query 4: Uso de memoria heap de la JVM por servicio
   jvm_memory_used_bytes{area="heap"}
   ```

   ```promql
   # Query 5: Latencia p95 de consulta de saldo
   histogram_quantile(0.95, rate(banco_cuentas_consulta_saldo_duracion_seconds_bucket[5m]))
   ```

4. Abre Grafana en tu navegador:

   ```
   http://localhost:3000
   ```

   Credenciales: `admin` / `admin123`

5. Navega a **Dashboards > Banco > Banco Microservicios - Overview** y verifica que los paneles muestran datos.

6. Verifica las métricas via API de Prometheus desde terminal:

   ```bash
   # Query PromQL via API REST de Prometheus
   curl -s "http://localhost:9090/api/v1/query?query=up" | python3 -m json.tool | grep -A 5 '"metric"'
   
   # Query de métricas de negocio
   curl -s "http://localhost:9090/api/v1/query?query=banco_transacciones_total" | python3 -m json.tool
   
   # Verificar que Prometheus tiene datos de los 3 servicios
   curl -s "http://localhost:9090/api/v1/query?query=up" | \
     python3 -c "import sys,json; data=json.load(sys.stdin); [print(r['metric'].get('job','?'), '-', r['value'][1]) for r in data['data']['result']]"
   ```

**Salida Esperada:**

```
accounts-service - 1
transactions-service - 1
auth-service - 1
```

**Verificación:**

- Los tres servicios aparecen con valor `1` (UP) en la query `up`
- Los paneles del dashboard de Grafana muestran datos (aunque algunos pueden estar en 0 si no hubo tráfico)
- Las queries PromQL de métricas de negocio retornan resultados

## Validación y Pruebas

### Criterios de Éxito

- [ ] Los tres servicios exponen `/actuator/health` con estado `UP` y detalles de componentes en sus puertos de gestión (8091, 8092, 8093)
- [ ] El health check de `transactions-service` incluye el componente `accountsService` que verifica disponibilidad del servicio externo
- [ ] Las métricas de negocio `banco.cuentas.creadas.total`, `banco.cuentas.activas`, `banco.transacciones.total` aparecen en `/actuator/metrics`
- [ ] El endpoint `/actuator/prometheus` expone métricas en formato correcto con labels `application` y `environment`
- [ ] Prometheus hace scraping exitoso de los tres servicios (estado `UP` en `/targets`)
- [ ] Grafana está accesible y muestra el dashboard "Banco Microservicios - Overview"
- [ ] El circuit breaker transiciona a `OPEN` cuando `accounts-service` falla repetidamente
- [ ] Los reintentos con backoff exponencial se registran en los logs de `transactions-service`
- [ ] El circuit breaker regresa a `CLOSED` después de que `accounts-service` se recupera

### Procedimiento de Pruebas

1. **Prueba de health checks:**

   ```bash
   # Verificar los tres servicios
   for PORT in 8091 8092 8093; do
     STATUS=$(curl -s http://localhost:$PORT/actuator/health | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
     echo "Puerto $PORT: $STATUS"
   done
   ```
   **Resultado Esperado:** `Puerto 8091: UP`, `Puerto 8092: UP`, `Puerto 8093: UP`

2. **Prueba de métricas en formato Prometheus:**

   ```bash
   # Verificar que el endpoint prometheus responde y contiene métricas JVM
   curl -s http://localhost:8091/actuator/prometheus | grep "jvm_memory_used_bytes" | head -3
   ```
   **Resultado Esperado:** Líneas con `jvm_memory_used_bytes{...} <valor>`

3. **Prueba de métricas personalizadas:**

   ```bash
   # Verificar presencia de métricas de negocio
   curl -s http://localhost:8091/actuator/prometheus | grep -c "banco_"
   ```
   **Resultado Esperado:** Número mayor a 0 (al menos 3 métricas de negocio)

4. **Prueba de Prometheus scraping:**

   ```bash
   # Verificar que Prometheus tiene datos de los servicios
   curl -s "http://localhost:9090/api/v1/query?query=up{job=~'accounts-service|transactions-service|auth-service'}" | \
     python3 -c "import sys,json; data=json.load(sys.stdin); print('Servicios monitoreados:', len(data['data']['result']))"
   ```
   **Resultado Esperado:** `Servicios monitoreados: 3`

5. **Prueba del circuit breaker:**

   ```bash
   # Estado inicial debe ser CLOSED
   curl -s http://localhost:8082/api/diagnostics/circuit-breaker | \
     python3 -c "import sys,json; print('Estado CB:', json.load(sys.stdin)['estado'])"
   ```
   **Resultado Esperado:** `Estado CB: CLOSED`

6. **Prueba de endpoint info:**

   ```bash
   # Verificar información del servicio
   curl -s http://localhost:8091/actuator/info | python3 -m json.tool
   ```
   **Resultado Esperado:** JSON con `info.app.name: "Accounts Service"` y versión

## Solución de Problemas

### Problema 1: `host.docker.internal` no Resuelve en Linux

**Síntomas:**
- Los targets en Prometheus aparecen con estado `DOWN`
- Error en logs de Prometheus: `dial tcp: lookup host.docker.internal: no such host`

**Causa:**
`host.docker.internal` es un hostname especial de Docker Desktop disponible en macOS y Windows. En Linux con Docker Engine nativo, no está disponible por defecto.

**Solución:**

```bash
# Opción 1: Agregar extra_hosts al docker-compose.yml para Prometheus
# En el servicio prometheus, agregar:
# extra_hosts:
#   - "host.docker.internal:host-gateway"

# Opción 2: Obtener la IP del bridge de Docker y usarla en prometheus.yml
DOCKER_HOST_IP=$(ip route show default | awk '/default/ {print $3}')
echo "IP del host Docker: $DOCKER_HOST_IP"

# Reemplazar host.docker.internal por la IP en prometheus.yml
sed -i "s/host.docker.internal/$DOCKER_HOST_IP/g" monitoring/prometheus/prometheus.yml

# Reiniciar Prometheus para aplicar cambios
docker-compose restart prometheus
```

---

### Problema 2: Error de Compilación con Resilience4j - `CallNotPermittedException` no Encontrada

**Síntomas:**
- Error Maven: `cannot find symbol: class CallNotPermittedException`
- Error: `package io.github.resilience4j.circuitbreaker does not exist`

**Causa:**
La dependencia `resilience4j-spring-boot3` o `resilience4j-reactor` no está correctamente declarada, o se está usando la versión de Spring Boot 2 (`resilience4j-spring-boot2`).

**Solución:**

```xml
<!-- Verificar que el pom.xml de transactions-service tiene EXACTAMENTE estas dependencias -->
<!-- NO usar resilience4j-spring-boot2 con Spring Boot 3 -->

<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-reactor</artifactId>
    <version>2.1.0</version>
</dependency>
```

```bash
# Limpiar caché de Maven y recompilar
cd transactions-service
mvn clean dependency:purge-local-repository -DreResolve=false
mvn clean compile
```

---

### Problema 3: El Endpoint `/actuator/prometheus` Retorna 404

**Síntomas:**
- `curl http://localhost:8091/actuator/prometheus` retorna `404 Not Found`
- El endpoint no aparece en la lista de `/actuator`

**Causa:**
Falta la dependencia `micrometer-registry-prometheus` en el `pom.xml`, o el endpoint no está incluido en `management.endpoints.web.exposure.include`.

**Solución:**

```bash
# Verificar que la dependencia está en pom.xml
grep -n "micrometer-registry-prometheus" accounts-service/pom.xml

# Si no aparece, agregar al pom.xml:
# <dependency>
#     <groupId>io.micrometer</groupId>
#     <artifactId>micrometer-registry-prometheus</artifactId>
# </dependency>

# Verificar configuración en application.properties
grep "prometheus" accounts-service/src/main/resources/application.properties

# Debe contener:
# management.endpoints.web.exposure.include=health,info,metrics,prometheus
# management.endpoint.prometheus.enabled=true
```

---

### Problema 4: Circuit Breaker No Abre Después de los Fallos

**Síntomas:**
- El circuit breaker permanece en estado `CLOSED` aunque `accounts-service` está caído
- No se registran eventos de transición en los logs

**Causa:**
Las excepciones lanzadas por WebClient pueden no coincidir con las configuradas en `retry-exceptions`, o el número de llamadas fallidas no alcanza el umbral de `sliding-window-size`.

**Solución:**

```bash
# Verificar la configuración del circuit breaker
grep "resilience4j" transactions-service/src/main/resources/application.properties

# Verificar que el sliding-window-size es 5 y failure-rate-threshold es 60
# Con 5 llamadas y 60% de fallo, necesitas al menos 3 fallos en 5 llamadas

# Aumentar temporalmente el número de llamadas de prueba
for i in {1..10}; do
  curl -s -X POST http://localhost:8082/api/transactions \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"cuentaOrigenId":"cuenta-001","monto":100.00,"tipo":"RETIRO"}' > /dev/null
  echo "Llamada $i enviada"
  sleep 1
done

# Verificar estado después de más llamadas
curl -s http://localhost:8082/api/diagnostics/circuit-breaker | python3 -m json.tool
```

---

### Problema 5: Grafana no Muestra Datos en el Dashboard

**Síntomas:**
- Los paneles del dashboard muestran "No data" o están vacíos
- El datasource de Prometheus está configurado pero sin datos

**Causa:**
El datasource de Prometheus en Grafana apunta a una URL incorrecta, o las métricas aún no tienen datos porque no se ha generado tráfico.

**Solución:**

```bash
# Verificar que Prometheus es accesible desde el contenedor de Grafana
docker exec banco-grafana wget -qO- http://prometheus:9090/api/v1/query?query=up | head -100

# Si falla, verificar que ambos están en la misma red Docker
docker network inspect banco-network | grep -A 3 '"Name"'

# Generar tráfico para que las métricas tengan valores
TOKEN=$(curl -s -X POST http://localhost:8083/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}' \
  | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

for i in {1..10}; do
  curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8081/api/accounts > /dev/null
  sleep 1
done

# Esperar 30 segundos para el próximo scrape de Prometheus
sleep 30

# Refrescar el dashboard en Grafana
```

## Limpieza

```bash
# Detener los contenedores de monitoreo (Prometheus y Grafana)
docker-compose stop prometheus grafana

# Si deseas eliminar los contenedores y volúmenes de monitoreo (CUIDADO: borra datos históricos)
docker-compose rm -f prometheus grafana
docker volume rm $(docker volume ls -q | grep -E "prometheus_data|grafana_data") 2>/dev/null || true

# Para detener TODOS los servicios del laboratorio
docker-compose down

# Para detener y eliminar todos los volúmenes (DESTRUCTIVO - borra bases de datos)
# docker-compose down -v

# Verificar que los puertos de monitoreo están libres
lsof -i :9090 2>/dev/null || echo "Puerto 9090 libre"
lsof -i :3000 2>/dev/null || echo "Puerto 3000 libre"
```

> ⚠️ **Advertencia:** No ejecutes `docker-compose down -v` a menos que quieras eliminar también las bases de datos PostgreSQL con todos los datos de prueba. Para el próximo laboratorio necesitarás los datos intactos. Usa `docker-compose stop` para pausar los servicios sin eliminar datos.

> ⚠️ **Nota de Seguridad:** Las configuraciones de Actuator en este laboratorio están diseñadas para entornos de desarrollo. En producción, nunca expongas `management.endpoint.health.show-details=always` públicamente y usa `management.endpoint.health.show-details=when-authorized`. El secreto JWT y las contraseñas de Grafana deben gestionarse con variables de entorno o un vault, nunca hardcodeados en archivos de configuración.

## Resumen

### Lo que Lograste

- **Configuraste Spring Boot Actuator** en los tres microservicios del sistema bancario con exposición selectiva de endpoints y separación del puerto de gestión, siguiendo buenas prácticas de seguridad
- **Implementaste health indicators personalizados** que verifican la conexión R2DBC a PostgreSQL y la disponibilidad del `accounts-service` como dependencia externa de `transactions-service`
- **Registraste métricas de negocio con Micrometer**: contadores para transacciones y consultas, un gauge para cuentas activas y timers para medir latencia de operaciones críticas
- **Implementaste patrones de resiliencia con Resilience4j** en el pipeline reactivo de `transactions-service`: retry con backoff exponencial (3 intentos: 500ms, 1s, 2s), timeout de 5 segundos y circuit breaker con ventana de 5 llamadas
- **Levantaste Prometheus y Grafana** via Docker Compose con configuración automática de scraping cada 15 segundos y un dashboard preconfigurado para visualización de métricas
- **Validaste el comportamiento del circuit breaker** simulando la caída de `accounts-service` y observando las transiciones de estado CLOSED → OPEN → HALF_OPEN → CLOSED

### Conceptos Clave

- **Observabilidad vs. Monitoreo:** La observabilidad va más allá del monitoreo tradicional. No solo sabes que algo falló, sino que puedes entender *por qué* falló gracias a los datos que el sistema expone sobre sí mismo.
- **Puerto de Gestión Separado:** Exponer Actuator en un puerto diferente al de la aplicación es una práctica de seguridad esencial para evitar que endpoints de administración queden accesibles al tráfico público.
- **Micrometer como Abstracción:** La misma instrumentación con Micrometer puede enviar métricas a Prometheus hoy y a Datadog mañana, simplemente cambiando la dependencia del registry.
- **Resilience4j y Reactor:** Los operadores `transformDeferred(CircuitBreakerOperator.of(...))` son la forma correcta de integrar Resilience4j en un pipeline reactivo. Nunca usar los operadores síncronos de Resilience4j con WebFlux.
- **Backpressure Implícito en Resiliencia:** El circuit breaker actúa como un mecanismo de backpressure a nivel de servicio: cuando un downstream está saturado o caído, protege al sistema completo bloqueando llamadas en lugar de acumularlas.

### Próximos Pasos

- **Laboratorio 5 (Integración Final):** Con el sistema ahora observable y resiliente, el Lab 5 se enfocará en la validación end-to-end del sistema bancario completo, ejecutando flujos de negocio completos y documentando el comportamiento del sistema bajo condiciones normales y de fallo.
- **Explorar Alertas en Grafana:** Configura alertas en Grafana para que notifiquen cuando el circuit breaker se abra o cuando la latencia supere umbrales definidos.
- **Agregar Trazabilidad Distribuida:** Como siguiente nivel de observabilidad, considera integrar Spring Cloud Sleuth o Micrometer Tracing con Zipkin para correlacionar requests entre servicios usando trace IDs.

## Recursos Adicionales

- [Spring Boot Actuator - Documentación Oficial](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html) - Referencia completa de todos los endpoints, configuración de seguridad y personalización de health indicators
- [Micrometer - Documentación Oficial](https://micrometer.io/docs) - Guía de tipos de métricas, registries disponibles y patrones de instrumentación para aplicaciones Java
- [Resilience4j - Guía de Spring Boot](https://resilience4j.readme.io/docs/getting-started-3) - Documentación específica para Spring Boot 3 con ejemplos de configuración y uso con WebFlux
- [Resilience4j Reactor Module](https://resilience4j.readme.io/docs/reactor) - Referencia del módulo de integración con Project Reactor, incluyendo `CircuitBreakerOperator`, `RetryOperator` y `TimeLimiterOperator`
- [Prometheus - Documentación de PromQL](https://prometheus.io/docs/prometheus/latest/querying/basics/) - Referencia del lenguaje de queries de Prometheus para construir dashboards y alertas
- [Grafana - Guía de Dashboards](https://grafana.com/docs/grafana/latest/dashboards/) - Documentación para crear y configurar dashboards de visualización de métricas
- [Baeldung - Spring Boot Actuator](https://www.baeldung.com/spring-boot-actuators) - Tutorial práctico con ejemplos de configuración y personalización de endpoints de Actuator
- [Baeldung - Resilience4j con Spring Boot](https://www.baeldung.com/resilience4j) - Tutorial completo de patrones de resiliencia con ejemplos de código para Spring Boot 3
