---LAB_START---
LAB_ID: 05-00-01
---MARKDOWN---
# Laboratorio 5 - Demo Funcional Completa: Sistema Bancario Reactivo

## Metadatos

| Propiedad | Valor |
|-----------|-------|
| **Duración** | 35 minutos |
| **Complejidad** | Difícil |
| **Nivel Bloom** | Crear |
| **Modalidad** | Integración y Validación End-to-End |

## Descripción General

Este laboratorio integrador valida el sistema completo de microservicios reactivos desarrollado a lo largo del curso, simulando una plataforma bancaria simplificada con dos servicios de dominio independientes: `account-service` (gestión de cuentas) y `transaction-service` (procesamiento de transacciones). El estudiante arrancará toda la infraestructura con Docker Compose, verificará la comunicación reactiva entre servicios a través del API Gateway con autenticación JWT, y ejecutará flujos end-to-end que demuestran la integración de todos los componentes: seguridad stateless, persistencia reactiva, resiliencia con Resilience4j y observabilidad con Prometheus y Grafana.

La relevancia práctica de este laboratorio es máxima: representa el escenario real de un equipo de ingeniería validando un release antes de despliegue a producción, donde cada componente debe funcionar correctamente tanto de forma aislada como en conjunto.

> ⏱️ **Distribución del tiempo:** 10 minutos para arranque y verificación de infraestructura → 15 minutos para ejecución de flujos end-to-end → 10 minutos para validación de métricas y documentación de resultados.

## Objetivos de Aprendizaje

Al completar este laboratorio, serás capaz de:

- [ ] Diseñar el modelo de dominio reactivo para cuentas bancarias y transacciones financieras con entidades, enums y DTOs (records Java)
- [ ] Implementar dos microservicios reactivos independientes con Spring WebFlux y persistencia R2DBC sobre PostgreSQL
- [ ] Configurar el API Gateway como punto de entrada único con enrutamiento hacia `account-service` y `transaction-service`
- [ ] Validar el flujo end-to-end completo: autenticación JWT → creación de cuenta → registro de transacción → consulta de historial
- [ ] Verificar la integración de métricas Micrometer/Prometheus y visualizar el estado del sistema en Grafana
- [ ] Aplicar y demostrar el manejo de errores reactivos con `onErrorResume` y `onErrorMap` en pipelines WebFlux

## Prerrequisitos

### Conocimiento Requerido

- Comprensión de programación reactiva con Project Reactor (`Mono`, `Flux`, operadores `flatMap`, `zipWith`, `onErrorResume`)
- Experiencia con Spring Boot 3.2.x y configuración con `application.properties` o `application.yml`
- Conocimiento de Spring WebFlux con anotaciones `@RestController` reactivas y `WebClient`
- Familiaridad con Docker y Docker Compose para orquestación de servicios de infraestructura
- Conceptos de autenticación JWT: generación, validación y propagación de tokens en headers
- Conocimiento básico de PostgreSQL y R2DBC para persistencia reactiva no-bloqueante
- Laboratorios 1 al 4 completados (o código de solución disponible como punto de partida)

### Acceso Requerido

- Docker Desktop 4.25+ en ejecución con WSL2 habilitado (Windows) o Docker Engine (Linux/macOS)
- Repositorio del curso clonado localmente con el código de los laboratorios anteriores
- Puertos disponibles: 8080, 8081, 8082, 8083, 5432, 5433, 9090, 3000

## Entorno de Laboratorio

### Requisitos de Hardware

| Componente | Especificación |
|------------|----------------|
| Procesador | Intel Core i5 / AMD Ryzen 5 (8ª gen+), mínimo 4 núcleos físicos |
| RAM | Mínimo 16 GB (recomendado 32 GB para 4+ servicios Spring Boot + Docker) |
| Almacenamiento | Mínimo 20 GB libres en SSD para imágenes Docker y artefactos Maven |
| Red | Banda ancha estable (mínimo 10 Mbps) |
| Pantalla | Resolución mínima 1920x1080 |

### Requisitos de Software

| Software | Versión | Propósito |
|----------|---------|-----------|
| JDK | 17 LTS o 21 LTS | Compilación y ejecución de microservicios Spring Boot |
| Apache Maven | 3.9.x | Gestión de dependencias y construcción multi-módulo |
| Docker Desktop | 4.25+ | Contenerización de infraestructura (PostgreSQL, Redis, Prometheus, Grafana) |
| Docker Compose | 2.21+ | Orquestación del entorno multi-contenedor |
| IntelliJ IDEA | 2023.3+ | IDE principal para desarrollo y depuración |
| Postman | 10.x+ | Pruebas manuales de APIs REST y validación JWT |
| curl | 7.x+ | Pruebas rápidas desde terminal |
| Git | 2.40+ | Control de versiones |

### Configuración Inicial

```bash
# 1. Verificar que Docker Desktop está en ejecución
docker --version
docker compose version

# 2. Verificar que los puertos necesarios están libres
# Linux/macOS:
lsof -i :8080 -i :8081 -i :8082 -i :8083 -i :5432 -i :5433 -i :9090 -i :3000

# Windows (PowerShell):
# netstat -ano | findstr "8080 8081 8082 8083 5432 5433 9090 3000"

# 3. Navegar al directorio raíz del proyecto del curso
cd ~/curso-microservicios-reactivos

# 4. Verificar la estructura del proyecto
ls -la
```

**Salida esperada de la estructura del proyecto:**
```
drwxr-xr-x  account-service/
drwxr-xr-x  transaction-service/
drwxr-xr-x  api-gateway/
drwxr-xr-x  auth-service/
drwxr-xr-x  docker/
-rw-r--r--  docker-compose.yml
-rw-r--r--  pom.xml
-rw-r--r--  README.md
```

## Instrucciones Paso a Paso

### Paso 1: Revisión del Modelo de Dominio y Estructura del Proyecto

**Objetivo:** Verificar que el modelo de dominio (entidades, enums y DTOs) está correctamente implementado en ambos servicios antes de arrancar la infraestructura.

**Instrucciones:**

1. Abrir el proyecto en IntelliJ IDEA y navegar a la estructura del `account-service`:

   ```bash
   # Verificar la estructura de directorios del account-service
   find account-service/src -name "*.java" | sort
   ```

2. Si el `account-service` no tiene las clases de dominio, crearlas ahora. Crear el enum `AccountStatus`:

   ```bash
   mkdir -p account-service/src/main/java/com/curso/banking/account/domain
   cat > account-service/src/main/java/com/curso/banking/account/domain/AccountStatus.java << 'EOF'
   package com.curso.banking.account.domain;

   public enum AccountStatus {
       ACTIVE,
       SUSPENDED,
       CLOSED
   }
   EOF
   ```

3. Crear la entidad `Account` con anotaciones R2DBC:

   ```bash
   cat > account-service/src/main/java/com/curso/banking/account/domain/Account.java << 'EOF'
   package com.curso.banking.account.domain;

   import org.springframework.data.annotation.Id;
   import org.springframework.data.relational.core.mapping.Column;
   import org.springframework.data.relational.core.mapping.Table;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   @Table("accounts")
   public class Account {

       @Id
       private Long id;

       @Column("owner_id")
       private String ownerId;

       @Column("owner_name")
       private String ownerName;

       @Column("account_number")
       private String accountNumber;

       @Column("balance")
       private BigDecimal balance;

       @Column("status")
       private String status;

       @Column("created_at")
       private LocalDateTime createdAt;

       @Column("updated_at")
       private LocalDateTime updatedAt;

       // Constructor sin argumentos requerido por R2DBC
       public Account() {}

       public Account(String ownerId, String ownerName, String accountNumber,
                      BigDecimal balance, String status) {
           this.ownerId = ownerId;
           this.ownerName = ownerName;
           this.accountNumber = accountNumber;
           this.balance = balance;
           this.status = status;
           this.createdAt = LocalDateTime.now();
           this.updatedAt = LocalDateTime.now();
       }

       // Getters y Setters
       public Long getId() { return id; }
       public void setId(Long id) { this.id = id; }
       public String getOwnerId() { return ownerId; }
       public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
       public String getOwnerName() { return ownerName; }
       public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
       public String getAccountNumber() { return accountNumber; }
       public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
       public BigDecimal getBalance() { return balance; }
       public void setBalance(BigDecimal balance) { this.balance = balance; }
       public String getStatus() { return status; }
       public void setStatus(String status) { this.status = status; }
       public LocalDateTime getCreatedAt() { return createdAt; }
       public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
       public LocalDateTime getUpdatedAt() { return updatedAt; }
       public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
   }
   EOF
   ```

4. Crear los DTOs del `account-service` como Java Records:

   ```bash
   mkdir -p account-service/src/main/java/com/curso/banking/account/dto
   
   cat > account-service/src/main/java/com/curso/banking/account/dto/CreateAccountRequest.java << 'EOF'
   package com.curso.banking.account.dto;

   import java.math.BigDecimal;

   public record CreateAccountRequest(
       String ownerId,
       String ownerName,
       BigDecimal initialBalance
   ) {}
   EOF

   cat > account-service/src/main/java/com/curso/banking/account/dto/AccountResponse.java << 'EOF'
   package com.curso.banking.account.dto;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   public record AccountResponse(
       Long id,
       String accountNumber,
       String ownerId,
       String ownerName,
       BigDecimal balance,
       String status,
       LocalDateTime createdAt
   ) {}
   EOF
   ```

5. Crear los enums y DTOs del `transaction-service`:

   ```bash
   mkdir -p transaction-service/src/main/java/com/curso/banking/transaction/domain
   mkdir -p transaction-service/src/main/java/com/curso/banking/transaction/dto

   cat > transaction-service/src/main/java/com/curso/banking/transaction/domain/TransactionType.java << 'EOF'
   package com.curso.banking.transaction.domain;

   public enum TransactionType {
       DEPOSIT,
       WITHDRAWAL,
       TRANSFER
   }
   EOF

   cat > transaction-service/src/main/java/com/curso/banking/transaction/domain/TransactionStatus.java << 'EOF'
   package com.curso.banking.transaction.domain;

   public enum TransactionStatus {
       PENDING,
       COMPLETED,
       FAILED
   }
   EOF

   cat > transaction-service/src/main/java/com/curso/banking/transaction/domain/Transaction.java << 'EOF'
   package com.curso.banking.transaction.domain;

   import org.springframework.data.annotation.Id;
   import org.springframework.data.relational.core.mapping.Column;
   import org.springframework.data.relational.core.mapping.Table;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   @Table("transactions")
   public class Transaction {

       @Id
       private Long id;

       @Column("account_id")
       private Long accountId;

       @Column("type")
       private String type;

       @Column("amount")
       private BigDecimal amount;

       @Column("description")
       private String description;

       @Column("status")
       private String status;

       @Column("created_at")
       private LocalDateTime createdAt;

       public Transaction() {}

       public Transaction(Long accountId, String type, BigDecimal amount,
                          String description, String status) {
           this.accountId = accountId;
           this.type = type;
           this.amount = amount;
           this.description = description;
           this.status = status;
           this.createdAt = LocalDateTime.now();
       }

       // Getters y Setters
       public Long getId() { return id; }
       public void setId(Long id) { this.id = id; }
       public Long getAccountId() { return accountId; }
       public void setAccountId(Long accountId) { this.accountId = accountId; }
       public String getType() { return type; }
       public void setType(String type) { this.type = type; }
       public BigDecimal getAmount() { return amount; }
       public void setAmount(BigDecimal amount) { this.amount = amount; }
       public String getDescription() { return description; }
       public void setDescription(String description) { this.description = description; }
       public String getStatus() { return status; }
       public void setStatus(String status) { this.status = status; }
       public LocalDateTime getCreatedAt() { return createdAt; }
       public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
   }
   EOF

   cat > transaction-service/src/main/java/com/curso/banking/transaction/dto/CreateTransactionRequest.java << 'EOF'
   package com.curso.banking.transaction.dto;

   import java.math.BigDecimal;

   public record CreateTransactionRequest(
       Long accountId,
       String type,
       BigDecimal amount,
       String description
   ) {}
   EOF

   cat > transaction-service/src/main/java/com/curso/banking/transaction/dto/TransactionResponse.java << 'EOF'
   package com.curso.banking.transaction.dto;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   public record TransactionResponse(
       Long id,
       Long accountId,
       String type,
       BigDecimal amount,
       String description,
       String status,
       LocalDateTime createdAt
   ) {}
   EOF
   ```

**Salida Esperada:**

```
account-service/src/main/java/com/curso/banking/account/
├── domain/
│   ├── Account.java
│   └── AccountStatus.java
└── dto/
    ├── AccountResponse.java
    └── CreateAccountRequest.java

transaction-service/src/main/java/com/curso/banking/transaction/
├── domain/
│   ├── Transaction.java
│   ├── TransactionStatus.java
│   └── TransactionType.java
└── dto/
    ├── CreateTransactionRequest.java
    └── TransactionResponse.java
```

**Verificación:**

- Confirmar que todas las clases compilan sin errores ejecutando `mvn compile` en cada módulo
- Verificar que los records Java usan la sintaxis correcta (requiere Java 16+)

---

### Paso 2: Verificar Scripts SQL de Inicialización y Configuración Docker Compose

**Objetivo:** Asegurar que los scripts de inicialización de PostgreSQL y el archivo `docker-compose.yml` están correctamente configurados para arrancar toda la infraestructura del sistema bancario.

**Instrucciones:**

1. Crear el directorio de scripts SQL si no existe:

   ```bash
   mkdir -p docker/init-scripts
   ```

2. Crear el script de inicialización para la base de datos de cuentas:

   ```bash
   cat > docker/init-scripts/01-accounts-init.sql << 'EOF'
   -- Base de datos del Account Service
   CREATE DATABASE accounts_db;

   \c accounts_db;

   CREATE TABLE IF NOT EXISTS accounts (
       id          BIGSERIAL PRIMARY KEY,
       owner_id    VARCHAR(100) NOT NULL,
       owner_name  VARCHAR(200) NOT NULL,
       account_number VARCHAR(50) UNIQUE NOT NULL,
       balance     DECIMAL(19, 4) NOT NULL DEFAULT 0.0000,
       status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
       created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
       updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
   );

   -- Datos de prueba: cuentas de ejemplo
   INSERT INTO accounts (owner_id, owner_name, account_number, balance, status)
   VALUES
       ('user-001', 'Ana García López',    'ACC-2024-001', 15000.0000, 'ACTIVE'),
       ('user-002', 'Carlos Martínez Ruiz','ACC-2024-002',  8500.5000, 'ACTIVE'),
       ('admin-001','Administrador Sistema','ACC-2024-003', 50000.0000, 'ACTIVE'),
       ('user-003', 'María Rodríguez Vega','ACC-2024-004',  2300.7500, 'SUSPENDED');

   -- Índices para consultas frecuentes
   CREATE INDEX idx_accounts_owner_id ON accounts(owner_id);
   CREATE INDEX idx_accounts_status   ON accounts(status);

   GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO postgres;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
   EOF
   ```

3. Crear el script de inicialización para la base de datos de transacciones:

   ```bash
   cat > docker/init-scripts/02-transactions-init.sql << 'EOF'
   -- Base de datos del Transaction Service
   CREATE DATABASE transactions_db;

   \c transactions_db;

   CREATE TABLE IF NOT EXISTS transactions (
       id          BIGSERIAL PRIMARY KEY,
       account_id  BIGINT NOT NULL,
       type        VARCHAR(20) NOT NULL,
       amount      DECIMAL(19, 4) NOT NULL,
       description VARCHAR(500),
       status      VARCHAR(20) NOT NULL DEFAULT 'PENDING',
       created_at  TIMESTAMP NOT NULL DEFAULT NOW()
   );

   -- Datos de prueba: historial de transacciones
   INSERT INTO transactions (account_id, type, amount, description, status)
   VALUES
       (1, 'DEPOSIT',    5000.0000, 'Depósito inicial de apertura',  'COMPLETED'),
       (1, 'WITHDRAWAL', 1200.0000, 'Pago de servicios',             'COMPLETED'),
       (2, 'DEPOSIT',    3000.0000, 'Transferencia recibida',        'COMPLETED'),
       (1, 'TRANSFER',    500.0000, 'Transferencia a cuenta ACC-002','COMPLETED'),
       (3, 'DEPOSIT',   10000.0000, 'Abono corporativo mensual',     'COMPLETED');

   -- Índices para consultas frecuentes
   CREATE INDEX idx_transactions_account_id ON transactions(account_id);
   CREATE INDEX idx_transactions_type       ON transactions(type);
   CREATE INDEX idx_transactions_status     ON transactions(status);
   CREATE INDEX idx_transactions_created_at ON transactions(created_at DESC);

   GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA public TO postgres;
   GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO postgres;
   EOF
   ```

4. Verificar el archivo `docker-compose.yml` principal. Si no existe o está incompleto, crear uno completo:

   ```bash
   cat > docker-compose.yml << 'EOF'
   version: '3.8'

   networks:
     banking-network:
       driver: bridge

   volumes:
     postgres-accounts-data:
     postgres-transactions-data:
     prometheus-data:
     grafana-data:

   services:

     # ─────────────────────────────────────────────
     # PostgreSQL para Account Service (puerto 5432)
     # ─────────────────────────────────────────────
     postgres-accounts:
       image: postgres:15-alpine
       container_name: postgres-accounts
       environment:
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres123
         POSTGRES_DB: accounts_db
       ports:
         - "5432:5432"
       volumes:
         - postgres-accounts-data:/var/lib/postgresql/data
         - ./docker/init-scripts/01-accounts-init.sql:/docker-entrypoint-initdb.d/01-init.sql
       networks:
         - banking-network
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U postgres -d accounts_db"]
         interval: 10s
         timeout: 5s
         retries: 5

     # ──────────────────────────────────────────────────
     # PostgreSQL para Transaction Service (puerto 5433)
     # ──────────────────────────────────────────────────
     postgres-transactions:
       image: postgres:15-alpine
       container_name: postgres-transactions
       environment:
         POSTGRES_USER: postgres
         POSTGRES_PASSWORD: postgres123
         POSTGRES_DB: transactions_db
       ports:
         - "5433:5432"
       volumes:
         - postgres-transactions-data:/var/lib/postgresql/data
         - ./docker/init-scripts/02-transactions-init.sql:/docker-entrypoint-initdb.d/02-init.sql
       networks:
         - banking-network
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U postgres -d transactions_db"]
         interval: 10s
         timeout: 5s
         retries: 5

     # ─────────────────────────────────────
     # Redis para blacklisting JWT (puerto 6379)
     # ─────────────────────────────────────
     redis:
       image: redis:7-alpine
       container_name: redis-banking
       ports:
         - "6379:6379"
       networks:
         - banking-network
       healthcheck:
         test: ["CMD", "redis-cli", "ping"]
         interval: 10s
         timeout: 3s
         retries: 5

     # ─────────────────────────────────────
     # Prometheus (puerto 9090)
     # ─────────────────────────────────────
     prometheus:
       image: prom/prometheus:v2.47.0
       container_name: prometheus-banking
       ports:
         - "9090:9090"
       volumes:
         - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
         - prometheus-data:/prometheus
       networks:
         - banking-network
       command:
         - '--config.file=/etc/prometheus/prometheus.yml'
         - '--storage.tsdb.path=/prometheus'
         - '--web.enable-lifecycle'

     # ─────────────────────────────────────
     # Grafana (puerto 3000)
     # ─────────────────────────────────────
     grafana:
       image: grafana/grafana:10.2.0
       container_name: grafana-banking
       ports:
         - "3000:3000"
       environment:
         GF_SECURITY_ADMIN_USER: admin
         GF_SECURITY_ADMIN_PASSWORD: admin123
         GF_USERS_ALLOW_SIGN_UP: "false"
       volumes:
         - grafana-data:/var/lib/grafana
         - ./docker/grafana/provisioning:/etc/grafana/provisioning
       networks:
         - banking-network
       depends_on:
         - prometheus
   EOF
   ```

5. Crear la configuración de Prometheus:

   ```bash
   mkdir -p docker/prometheus
   cat > docker/prometheus/prometheus.yml << 'EOF'
   global:
     scrape_interval: 15s
     evaluation_interval: 15s

   scrape_configs:
     - job_name: 'prometheus'
       static_configs:
         - targets: ['localhost:9090']

     - job_name: 'account-service'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8081']
       scrape_interval: 10s

     - job_name: 'transaction-service'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8082']
       scrape_interval: 10s

     - job_name: 'auth-service'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8083']
       scrape_interval: 10s

     - job_name: 'api-gateway'
       metrics_path: '/actuator/prometheus'
       static_configs:
         - targets: ['host.docker.internal:8080']
       scrape_interval: 10s
   EOF
   ```

6. Crear directorio de provisioning de Grafana:

   ```bash
   mkdir -p docker/grafana/provisioning/datasources
   cat > docker/grafana/provisioning/datasources/prometheus.yml << 'EOF'
   apiVersion: 1

   datasources:
     - name: Prometheus
       type: prometheus
       access: proxy
       url: http://prometheus:9090
       isDefault: true
       editable: true
   EOF
   ```

**Salida Esperada:**

```
docker/
├── init-scripts/
│   ├── 01-accounts-init.sql
│   └── 02-transactions-init.sql
├── prometheus/
│   └── prometheus.yml
└── grafana/
    └── provisioning/
        └── datasources/
            └── prometheus.yml
docker-compose.yml
```

**Verificación:**

- Ejecutar `docker compose config` para validar la sintaxis del archivo `docker-compose.yml`
- Confirmar que los scripts SQL no tienen errores de sintaxis revisando visualmente

---

### Paso 3: Verificar la Configuración de application.properties de Ambos Servicios

**Objetivo:** Confirmar que `account-service` y `transaction-service` tienen configuración correcta para R2DBC, Actuator y el perfil local.

**Instrucciones:**

1. Verificar y actualizar la configuración del `account-service`:

   ```bash
   cat > account-service/src/main/resources/application.properties << 'EOF'
   # ─────────────────────────────────────────────
   # Account Service - Configuración Principal
   # ─────────────────────────────────────────────
   spring.application.name=account-service
   server.port=8081

   # R2DBC - PostgreSQL Accounts (perfil local)
   spring.r2dbc.url=r2dbc:postgresql://localhost:5432/accounts_db
   spring.r2dbc.username=postgres
   spring.r2dbc.password=postgres123
   spring.r2dbc.pool.initial-size=5
   spring.r2dbc.pool.max-size=20

   # Flyway o inicialización SQL (deshabilitado en favor de scripts Docker)
   spring.sql.init.mode=never

   # Actuator - exponer todos los endpoints para observabilidad
   management.endpoints.web.exposure.include=health,info,metrics,prometheus
   management.endpoint.health.show-details=always
   management.metrics.export.prometheus.enabled=true
   management.metrics.tags.application=account-service

   # Logging
   logging.level.com.curso.banking=DEBUG
   logging.level.org.springframework.r2dbc=INFO
   logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

   # JWT (para validación en el servicio - clave compartida con auth-service)
   jwt.secret=curso-microservicios-reactivos-jwt-secret-key-256bits-segura-2024
   jwt.expiration=86400000
   EOF
   ```

2. Verificar y actualizar la configuración del `transaction-service`:

   ```bash
   cat > transaction-service/src/main/resources/application.properties << 'EOF'
   # ─────────────────────────────────────────────
   # Transaction Service - Configuración Principal
   # ─────────────────────────────────────────────
   spring.application.name=transaction-service
   server.port=8082

   # R2DBC - PostgreSQL Transactions (perfil local)
   spring.r2dbc.url=r2dbc:postgresql://localhost:5433/transactions_db
   spring.r2dbc.username=postgres
   spring.r2dbc.password=postgres123
   spring.r2dbc.pool.initial-size=5
   spring.r2dbc.pool.max-size=20

   spring.sql.init.mode=never

   # Actuator
   management.endpoints.web.exposure.include=health,info,metrics,prometheus
   management.endpoint.health.show-details=always
   management.metrics.export.prometheus.enabled=true
   management.metrics.tags.application=transaction-service

   # WebClient - URL del account-service (perfil local)
   services.account-service.url=http://localhost:8081

   # Resilience4j - Circuit Breaker para llamadas al account-service
   resilience4j.circuitbreaker.instances.accountService.registerHealthIndicator=true
   resilience4j.circuitbreaker.instances.accountService.slidingWindowSize=10
   resilience4j.circuitbreaker.instances.accountService.minimumNumberOfCalls=5
   resilience4j.circuitbreaker.instances.accountService.permittedNumberOfCallsInHalfOpenState=3
   resilience4j.circuitbreaker.instances.accountService.automaticTransitionFromOpenToHalfOpenEnabled=true
   resilience4j.circuitbreaker.instances.accountService.waitDurationInOpenState=10s
   resilience4j.circuitbreaker.instances.accountService.failureRateThreshold=50

   # Logging
   logging.level.com.curso.banking=DEBUG
   logging.level.org.springframework.r2dbc=INFO
   logging.pattern.console=%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n

   # JWT
   jwt.secret=curso-microservicios-reactivos-jwt-secret-key-256bits-segura-2024
   jwt.expiration=86400000
   EOF
   ```

3. Verificar la configuración del `api-gateway`:

   ```bash
   cat > api-gateway/src/main/resources/application.yml << 'EOF'
   spring:
     application:
       name: api-gateway
     cloud:
       gateway:
         routes:
           # Rutas del Account Service
           - id: account-service
             uri: http://localhost:8081
             predicates:
               - Path=/api/accounts/**
             filters:
               - name: RequestRateLimiter
                 args:
                   redis-rate-limiter.replenishRate: 10
                   redis-rate-limiter.burstCapacity: 20
                   redis-rate-limiter.requestedTokens: 1

           # Rutas del Transaction Service
           - id: transaction-service
             uri: http://localhost:8082
             predicates:
               - Path=/api/transactions/**
             filters:
               - name: RequestRateLimiter
                 args:
                   redis-rate-limiter.replenishRate: 10
                   redis-rate-limiter.burstCapacity: 20
                   redis-rate-limiter.requestedTokens: 1

           # Rutas del Auth Service
           - id: auth-service
             uri: http://localhost:8083
             predicates:
               - Path=/api/auth/**

         default-filters:
           - AddResponseHeader=X-Gateway-Version, 1.0
           - AddResponseHeader=X-Response-Time, ${spring.application.name}

     data:
       redis:
         host: localhost
         port: 6379

   server:
     port: 8080

   management:
     endpoints:
       web:
         exposure:
           include: health,info,metrics,prometheus,gateway
     endpoint:
       health:
         show-details: always
     metrics:
       export:
         prometheus:
           enabled: true
       tags:
         application: api-gateway

   jwt:
     secret: curso-microservicios-reactivos-jwt-secret-key-256bits-segura-2024

   logging:
     level:
       org.springframework.cloud.gateway: DEBUG
       com.curso.banking: DEBUG
     pattern:
       console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
   EOF
   ```

> ⚠️ **Advertencia de Seguridad:** El secreto JWT `curso-microservicios-reactivos-jwt-secret-key-256bits-segura-2024` es solo para uso en laboratorio. En producción, **NUNCA** hardcodear secretos en archivos de configuración. Usar variables de entorno o un vault como HashiCorp Vault o AWS Secrets Manager.

**Salida Esperada:**

Al ejecutar `mvn validate` en el directorio raíz:
```
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: X.XXX s
```

**Verificación:**

- Confirmar que todos los `application.properties` / `application.yml` tienen las propiedades de R2DBC correctas
- Verificar que los puertos asignados no entran en conflicto (8080 Gateway, 8081 Accounts, 8082 Transactions, 8083 Auth)

---

### Paso 4: Arrancar la Infraestructura con Docker Compose

**Objetivo:** Levantar todos los servicios de infraestructura (PostgreSQL x2, Redis, Prometheus, Grafana) y verificar que están saludables antes de arrancar los microservicios Spring Boot.

**Instrucciones:**

1. Detener cualquier contenedor previo que pueda estar en ejecución:

   ```bash
   docker compose down --remove-orphans
   ```

2. Arrancar la infraestructura completa en modo detached:

   ```bash
   docker compose up -d postgres-accounts postgres-transactions redis prometheus grafana
   ```

3. Monitorear el arranque de los contenedores:

   ```bash
   docker compose ps
   ```

4. Esperar a que los health checks estén en estado `healthy` (puede tomar 30-60 segundos):

   ```bash
   # Verificar estado cada 5 segundos hasta que todos estén healthy
   watch -n 5 'docker compose ps'
   
   # Windows (PowerShell) - alternativa:
   # while ($true) { docker compose ps; Start-Sleep 5; Clear-Host }
   ```

5. Verificar que PostgreSQL de cuentas está accesible y los datos de prueba se cargaron:

   ```bash
   docker exec -it postgres-accounts psql -U postgres -d accounts_db -c "SELECT id, owner_name, account_number, balance, status FROM accounts;"
   ```

6. Verificar que PostgreSQL de transacciones está accesible:

   ```bash
   docker exec -it postgres-transactions psql -U postgres -d transactions_db -c "SELECT id, account_id, type, amount, status FROM transactions;"
   ```

7. Verificar que Redis responde:

   ```bash
   docker exec -it redis-banking redis-cli ping
   ```

8. Verificar que Prometheus está accesible:

   ```bash
   curl -s http://localhost:9090/-/healthy
   ```

**Salida Esperada:**

```
# docker compose ps
NAME                    IMAGE                   STATUS
postgres-accounts       postgres:15-alpine      Up (healthy)
postgres-transactions   postgres:15-alpine      Up (healthy)
redis-banking           redis:7-alpine          Up (healthy)
prometheus-banking      prom/prometheus:v2.47.0 Up
grafana-banking         grafana/grafana:10.2.0  Up

# Datos de prueba en accounts_db
 id | owner_name              | account_number | balance   | status
----+-------------------------+----------------+-----------+-----------
  1 | Ana García López        | ACC-2024-001   | 15000.0000| ACTIVE
  2 | Carlos Martínez Ruiz    | ACC-2024-002   |  8500.5000| ACTIVE
  3 | Administrador Sistema   | ACC-2024-003   | 50000.0000| ACTIVE
  4 | María Rodríguez Vega    | ACC-2024-004   |  2300.7500| SUSPENDED

# Redis
PONG

# Prometheus
Prometheus Server is Ready.
```

**Verificación:**

- Todos los contenedores deben mostrar estado `Up` o `Up (healthy)`
- Los datos de prueba deben aparecer en ambas bases de datos
- Redis debe responder `PONG`
- Prometheus debe responder `Prometheus Server is Ready.`

---

### Paso 5: Compilar y Arrancar los Microservicios Spring Boot

**Objetivo:** Compilar todos los módulos del proyecto y arrancar los cuatro microservicios Spring Boot en terminales separadas, verificando que cada uno arranca correctamente.

**Instrucciones:**

1. Compilar el proyecto completo desde el directorio raíz:

   ```bash
   cd ~/curso-microservicios-reactivos
   mvn clean package -DskipTests
   ```

2. Abrir cuatro terminales separadas. En la **Terminal 1**, arrancar el `auth-service`:

   ```bash
   # Terminal 1: Auth Service (puerto 8083)
   cd ~/curso-microservicios-reactivos/auth-service
   mvn spring-boot:run
   ```

3. En la **Terminal 2**, arrancar el `account-service`:

   ```bash
   # Terminal 2: Account Service (puerto 8081)
   cd ~/curso-microservicios-reactivos/account-service
   mvn spring-boot:run
   ```

4. En la **Terminal 3**, arrancar el `transaction-service`:

   ```bash
   # Terminal 3: Transaction Service (puerto 8082)
   cd ~/curso-microservicios-reactivos/transaction-service
   mvn spring-boot:run
   ```

5. En la **Terminal 4**, arrancar el `api-gateway`:

   ```bash
   # Terminal 4: API Gateway (puerto 8080)
   cd ~/curso-microservicios-reactivos/api-gateway
   mvn spring-boot:run
   ```

6. Verificar que todos los servicios están activos con sus health checks:

   ```bash
   # Verificar health de todos los servicios
   echo "=== Auth Service ===" && curl -s http://localhost:8083/actuator/health | python3 -m json.tool
   echo "=== Account Service ===" && curl -s http://localhost:8081/actuator/health | python3 -m json.tool
   echo "=== Transaction Service ===" && curl -s http://localhost:8082/actuator/health | python3 -m json.tool
   echo "=== API Gateway ===" && curl -s http://localhost:8080/actuator/health | python3 -m json.tool
   ```

**Salida Esperada:**

```
=== Auth Service ===
{
    "status": "UP",
    "components": {
        "db": { "status": "UP" },
        "redis": { "status": "UP" }
    }
}
=== Account Service ===
{
    "status": "UP",
    "components": {
        "r2dbc": { "status": "UP" }
    }
}
=== Transaction Service ===
{
    "status": "UP",
    "components": {
        "r2dbc": { "status": "UP" },
        "circuitBreakers": {
            "accountService": { "status": "UP" }
        }
    }
}
=== API Gateway ===
{
    "status": "UP",
    "components": {
        "redis": { "status": "UP" },
        "gateway": { "status": "UP" }
    }
}
```

**Verificación:**

- Los cuatro servicios deben mostrar `"status": "UP"`
- El `account-service` debe mostrar el componente `r2dbc` en estado UP
- El `transaction-service` debe mostrar el circuit breaker `accountService` en estado UP
- El `api-gateway` debe mostrar Redis y gateway en estado UP

---

### Paso 6: Ejecutar el Flujo End-to-End Completo

**Objetivo:** Validar el flujo completo del sistema bancario: autenticación → creación de cuenta → registro de transacción → consulta de historial, todo a través del API Gateway.

**Instrucciones:**

1. **Paso 6.1 - Autenticación:** Obtener un token JWT para el usuario de prueba:

   ```bash
   # Autenticarse con el usuario admin de prueba
   AUTH_RESPONSE=$(curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "password123"}')
   
   echo $AUTH_RESPONSE | python3 -m json.tool
   
   # Extraer el token JWT para usarlo en siguientes requests
   JWT_TOKEN=$(echo $AUTH_RESPONSE | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")
   echo "Token obtenido: ${JWT_TOKEN:0:50}..."
   ```

2. **Paso 6.2 - Consultar cuentas existentes** a través del Gateway:

   ```bash
   # Consultar todas las cuentas (datos de prueba pre-cargados)
   curl -s -X GET http://localhost:8080/api/accounts \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" | python3 -m json.tool
   ```

3. **Paso 6.3 - Crear una nueva cuenta bancaria:**

   ```bash
   # Crear una nueva cuenta para usuario de prueba
   NEW_ACCOUNT=$(curl -s -X POST http://localhost:8080/api/accounts \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d '{
       "ownerId": "user-test-lab5",
       "ownerName": "Estudiante Laboratorio 5",
       "initialBalance": 5000.00
     }')
   
   echo $NEW_ACCOUNT | python3 -m json.tool
   
   # Extraer el ID de la cuenta creada
   ACCOUNT_ID=$(echo $NEW_ACCOUNT | python3 -c "import sys, json; print(json.load(sys.stdin)['id'])")
   echo "Cuenta creada con ID: $ACCOUNT_ID"
   ```

4. **Paso 6.4 - Consultar la cuenta por ID:**

   ```bash
   curl -s -X GET http://localhost:8080/api/accounts/$ACCOUNT_ID \
     -H "Authorization: Bearer $JWT_TOKEN" | python3 -m json.tool
   ```

5. **Paso 6.5 - Registrar un depósito:**

   ```bash
   # Registrar un depósito en la cuenta recién creada
   DEPOSIT=$(curl -s -X POST http://localhost:8080/api/transactions \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
       \"accountId\": $ACCOUNT_ID,
       \"type\": \"DEPOSIT\",
       \"amount\": 2500.00,
       \"description\": \"Depósito de prueba Lab 5\"
     }")
   
   echo $DEPOSIT | python3 -m json.tool
   ```

6. **Paso 6.6 - Registrar un retiro:**

   ```bash
   # Registrar un retiro
   WITHDRAWAL=$(curl -s -X POST http://localhost:8080/api/transactions \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
       \"accountId\": $ACCOUNT_ID,
       \"type\": \"WITHDRAWAL\",
       \"amount\": 750.00,
       \"description\": \"Retiro de prueba Lab 5\"
     }")
   
   echo $WITHDRAWAL | python3 -m json.tool
   ```

7. **Paso 6.7 - Consultar el historial de transacciones de la cuenta:**

   ```bash
   # Consultar todas las transacciones de la cuenta
   curl -s -X GET "http://localhost:8080/api/transactions/account/$ACCOUNT_ID" \
     -H "Authorization: Bearer $JWT_TOKEN" | python3 -m json.tool
   ```

8. **Paso 6.8 - Probar acceso sin token (debe fallar con 401):**

   ```bash
   # Verificar que los endpoints están protegidos
   curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
     http://localhost:8080/api/accounts
   ```

9. **Paso 6.9 - Probar resiliencia: simular fallo del account-service:**

   ```bash
   # Detener el account-service temporalmente (en la Terminal 2, presionar Ctrl+C)
   # Luego intentar crear una transacción (debe retornar error controlado, no 500)
   curl -s -X POST http://localhost:8080/api/transactions \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "Content-Type: application/json" \
     -d "{
       \"accountId\": 999,
       \"type\": \"DEPOSIT\",
       \"amount\": 100.00,
       \"description\": \"Prueba de resiliencia\"
     }" | python3 -m json.tool
   
   # Reiniciar el account-service después de la prueba
   # (volver a la Terminal 2 y ejecutar: mvn spring-boot:run)
   ```

**Salida Esperada:**

```json
// Paso 6.1 - Login exitoso
{
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "username": "admin"
}

// Paso 6.3 - Cuenta creada
{
    "id": 5,
    "accountNumber": "ACC-2024-005",
    "ownerId": "user-test-lab5",
    "ownerName": "Estudiante Laboratorio 5",
    "balance": 5000.00,
    "status": "ACTIVE",
    "createdAt": "2024-01-15T14:30:00"
}

// Paso 6.5 - Depósito registrado
{
    "id": 6,
    "accountId": 5,
    "type": "DEPOSIT",
    "amount": 2500.00,
    "description": "Depósito de prueba Lab 5",
    "status": "COMPLETED",
    "createdAt": "2024-01-15T14:30:15"
}

// Paso 6.8 - Acceso sin token
HTTP Status: 401

// Paso 6.9 - Error controlado por resiliencia
{
    "error": "Service temporarily unavailable",
    "message": "El servicio de cuentas no está disponible en este momento",
    "status": 503
}
```

**Verificación:**

- El login debe retornar un token JWT válido (no vacío)
- La creación de cuenta debe asignar un `accountNumber` automático
- Las transacciones deben tener estado `COMPLETED`
- El acceso sin token debe retornar `HTTP 401`
- El error de resiliencia debe retornar `HTTP 503` con mensaje descriptivo (no un stacktrace)

---

### Paso 7: Validar Métricas en Prometheus y Grafana

**Objetivo:** Verificar que las métricas de los microservicios están siendo recolectadas por Prometheus y visualizarlas en Grafana.

**Instrucciones:**

1. Verificar que Prometheus está recolectando métricas de los servicios:

   ```bash
   # Verificar targets activos en Prometheus
   curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep -A 5 '"health"'
   ```

2. Consultar métricas específicas del `account-service` directamente:

   ```bash
   # Ver métricas de JVM del account-service
   curl -s http://localhost:8081/actuator/prometheus | grep "jvm_memory_used_bytes" | head -5
   
   # Ver métricas HTTP del account-service
   curl -s http://localhost:8081/actuator/prometheus | grep "http_server_requests" | head -10
   ```

3. Consultar métricas del `transaction-service`:

   ```bash
   # Ver métricas del circuit breaker
   curl -s http://localhost:8082/actuator/prometheus | grep "resilience4j" | head -10
   ```

4. Consultar una métrica específica en Prometheus mediante PromQL:

   ```bash
   # Contar total de requests HTTP en el account-service
   curl -s "http://localhost:9090/api/v1/query?query=http_server_requests_seconds_count%7Bapplication%3D%22account-service%22%7D" \
     | python3 -m json.tool
   ```

5. Acceder a Grafana y configurar un dashboard básico:

   ```bash
   # Verificar que Grafana está accesible
   curl -s -o /dev/null -w "Grafana HTTP Status: %{http_code}\n" http://localhost:3000
   
   echo "Acceder a Grafana en: http://localhost:3000"
   echo "Usuario: admin | Contraseña: admin123"
   ```

6. En el navegador, acceder a `http://localhost:3000` y crear un panel básico:
   - Hacer clic en **"+"** → **"New Dashboard"**
   - Hacer clic en **"Add visualization"**
   - Seleccionar la fuente de datos **"Prometheus"**
   - En el campo de query, ingresar:
     ```
     rate(http_server_requests_seconds_count{application="account-service"}[1m])
     ```
   - Hacer clic en **"Run queries"** para ver las métricas en tiempo real
   - Guardar el dashboard con el nombre **"Banking System - Lab 5"**

7. Verificar métricas del API Gateway:

   ```bash
   # Ver rutas activas del Gateway
   curl -s http://localhost:8080/actuator/gateway/routes | python3 -m json.tool
   ```

**Salida Esperada:**

```bash
# Métricas de Prometheus (extracto)
jvm_memory_used_bytes{area="heap",id="G1 Eden Space",application="account-service"} 1.5728640E7

http_server_requests_seconds_count{application="account-service",
  exception="None",method="GET",outcome="SUCCESS",status="200",
  uri="/api/accounts"} 3.0

# Circuit breaker del transaction-service
resilience4j_circuitbreaker_state{application="transaction-service",
  name="accountService",state="closed"} 1.0

# Grafana accesible
Grafana HTTP Status: 200
```

**Verificación:**

- Prometheus debe mostrar targets de `account-service` y `transaction-service` como `UP`
- Las métricas HTTP deben reflejar las llamadas realizadas en el Paso 6
- El circuit breaker debe aparecer en estado `closed` (normal)
- Grafana debe mostrar datos en el panel creado

---

### Paso 8: Documentar Resultados y Reflexión Final

**Objetivo:** Documentar el estado final del sistema, capturar evidencia de los flujos validados y reflexionar sobre los patrones implementados.

**Instrucciones:**

1. Generar un reporte de estado completo del sistema:

   ```bash
   echo "════════════════════════════════════════════"
   echo "   REPORTE DE ESTADO - SISTEMA BANCARIO"
   echo "   Laboratorio 5 - $(date '+%Y-%m-%d %H:%M:%S')"
   echo "════════════════════════════════════════════"
   
   echo ""
   echo "── Infraestructura Docker ──"
   docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
   
   echo ""
   echo "── Health Checks de Microservicios ──"
   for service in "auth-service:8083" "account-service:8081" "transaction-service:8082" "api-gateway:8080"; do
     name=$(echo $service | cut -d: -f1)
     port=$(echo $service | cut -d: -f2)
     status=$(curl -s http://localhost:$port/actuator/health | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','UNKNOWN'))" 2>/dev/null || echo "UNREACHABLE")
     echo "  $name (puerto $port): $status"
   done
   
   echo ""
   echo "── Datos en Base de Datos ──"
   echo "  Cuentas registradas:"
   docker exec postgres-accounts psql -U postgres -d accounts_db -t -c "SELECT COUNT(*) FROM accounts;" 2>/dev/null | tr -d ' '
   
   echo "  Transacciones registradas:"
   docker exec postgres-transactions psql -U postgres -d transactions_db -t -c "SELECT COUNT(*) FROM transactions;" 2>/dev/null | tr -d ' '
   
   echo ""
   echo "── Métricas Prometheus ──"
   echo "  Targets activos:"
   curl -s "http://localhost:9090/api/v1/targets" | python3 -c "
   import sys, json
   data = json.load(sys.stdin)
   targets = data.get('data', {}).get('activeTargets', [])
   for t in targets:
       print(f\"  {t['labels'].get('job', 'unknown')}: {t['health']}\")
   " 2>/dev/null
   
   echo ""
   echo "════════════════════════════════════════════"
   ```

2. Guardar el reporte en un archivo:

   ```bash
   bash -c 'echo "════════════════════════════════════════════
   REPORTE DE ESTADO - SISTEMA BANCARIO
   Laboratorio 5 - $(date +\"%Y-%m-%d %H:%M:%S\")
   ════════════════════════════════════════════" > reporte-lab5.txt'
   
   docker compose ps >> reporte-lab5.txt
   echo "Reporte guardado en: reporte-lab5.txt"
   cat reporte-lab5.txt
   ```

3. Verificar la propagación del header `X-Trace-Id` a través del Gateway:

   ```bash
   # Verificar que el Gateway propaga el header de trazabilidad
   curl -v http://localhost:8080/api/accounts \
     -H "Authorization: Bearer $JWT_TOKEN" \
     -H "X-Trace-Id: lab5-trace-$(date +%s)" 2>&1 | grep -E "X-Trace-Id|X-Gateway"
   ```

4. Reflexión: Listar los patrones implementados en el sistema:

   ```bash
   cat << 'EOF'
   ╔══════════════════════════════════════════════════════════════╗
   ║         PATRONES IMPLEMENTADOS EN EL SISTEMA                ║
   ╠══════════════════════════════════════════════════════════════╣
   ║ ✅ Bounded Contexts     → account-service / transaction-service ║
   ║ ✅ API Gateway          → Punto de entrada único (puerto 8080) ║
   ║ ✅ JWT Stateless Auth   → Tokens sin estado en Redis blacklist ║
   ║ ✅ Reactive Persistence → R2DBC + ReactiveCrudRepository      ║
   ║ ✅ WebClient Reactivo   → Comunicación entre servicios        ║
   ║ ✅ Circuit Breaker      → Resilience4j en transaction-service ║
   ║ ✅ Health Checks        → Actuator /health en todos los svcs  ║
   ║ ✅ Métricas Prometheus  → /actuator/prometheus en todos       ║
   ║ ✅ Observabilidad       → Grafana dashboards operacionales    ║
   ║ ✅ Rate Limiting        → RequestRateLimiter en Gateway       ║
   ║ ✅ Error Handling       → onErrorResume en pipelines reactivos║
   ╚══════════════════════════════════════════════════════════════╝
   EOF
   ```

**Salida Esperada:**

```
════════════════════════════════════════════
   REPORTE DE ESTADO - SISTEMA BANCARIO
   Laboratorio 5 - 2024-01-15 14:45:00
════════════════════════════════════════════

── Health Checks de Microservicios ──
  auth-service (puerto 8083): UP
  account-service (puerto 8081): UP
  transaction-service (puerto 8082): UP
  api-gateway (puerto 8080): UP

── Datos en Base de Datos ──
  Cuentas registradas: 5
  Transacciones registradas: 7

── Métricas Prometheus ──
  account-service: up
  transaction-service: up
  auth-service: up
  api-gateway: up
```

**Verificación:**

- El reporte debe mostrar los cuatro servicios en estado `UP`
- La base de datos de cuentas debe tener al menos 5 registros (4 de prueba + 1 creado en el lab)
- La base de datos de transacciones debe tener al menos 7 registros (5 de prueba + 2 creados en el lab)
- Prometheus debe reportar todos los targets como `up`

## Validación y Pruebas

### Criterios de Éxito

- [ ] Los cuatro microservicios (auth, account, transaction, gateway) arrancan sin errores y responden `UP` en `/actuator/health`
- [ ] La autenticación JWT funciona correctamente: login retorna token válido, endpoints sin token retornan 401
- [ ] La creación de cuenta genera un `accountNumber` único con formato `ACC-YYYY-NNN`
- [ ] Las transacciones (depósito y retiro) se registran con estado `COMPLETED` cuando la cuenta existe y está activa
- [ ] El circuit breaker del `transaction-service` actúa correctamente cuando el `account-service` no está disponible
- [ ] Prometheus recolecta métricas de todos los servicios (targets en estado `up`)
- [ ] Grafana muestra datos en el dashboard con la query PromQL de requests HTTP
- [ ] El header `X-Trace-Id` se propaga a través del API Gateway

### Procedimiento de Prueba

1. **Prueba de autenticación completa:**
   ```bash
   # Login exitoso
   curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "user", "password": "password123"}' \
     | python3 -c "import sys,json; d=json.load(sys.stdin); print('OK' if 'token' in d else 'FAIL')"
   ```
   **Resultado Esperado:** `OK`

2. **Prueba de protección de endpoints:**
   ```bash
   # Sin token - debe retornar 401
   curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/accounts
   ```
   **Resultado Esperado:** `401`

3. **Prueba de flujo completo de cuenta y transacción:**
   ```bash
   # Obtener token
   TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username": "admin", "password": "password123"}' \
     | python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
   
   # Crear cuenta
   ACC_ID=$(curl -s -X POST http://localhost:8080/api/accounts \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d '{"ownerId":"test-001","ownerName":"Test User","initialBalance":1000.00}' \
     | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
   
   # Crear transacción
   TX_STATUS=$(curl -s -X POST http://localhost:8080/api/transactions \
     -H "Authorization: Bearer $TOKEN" \
     -H "Content-Type: application/json" \
     -d "{\"accountId\":$ACC_ID,\"type\":\"DEPOSIT\",\"amount\":500.00,\"description\":\"Test\"}" \
     | python3 -c "import sys,json; print(json.load(sys.stdin)['status'])")
   
   echo "Estado de transacción: $TX_STATUS"
   ```
   **Resultado Esperado:** `Estado de transacción: COMPLETED`

4. **Prueba de métricas Prometheus:**
   ```bash
   # Verificar que account-service expone métricas
   curl -s http://localhost:8081/actuator/prometheus | grep -c "^#" | xargs echo "Métricas disponibles:"
   ```
   **Resultado Esperado:** `Métricas disponibles: [número > 50]`

5. **Prueba de circuit breaker:**
   ```bash
   # Verificar estado del circuit breaker (debe estar CLOSED en condiciones normales)
   curl -s http://localhost:8082/actuator/health \
     | python3 -c "import sys,json; d=json.load(sys.stdin); cb=d.get('components',{}).get('circuitBreakers',{}); print('Circuit Breaker:', cb.get('status','N/A'))"
   ```
   **Resultado Esperado:** `Circuit Breaker: UP`

## Solución de Problemas

### Problema 1: Los contenedores Docker no arrancan o están en estado "unhealthy"

**Síntomas:**
- `docker compose ps` muestra contenedores en estado `Exit` o `unhealthy`
- Los microservicios Spring Boot no pueden conectarse a PostgreSQL
- Error en logs: `Connection refused` o `FATAL: database does not exist`

**Causa:**
Los scripts de inicialización SQL pueden haber fallado, o los volúmenes de Docker tienen datos corruptos de ejecuciones anteriores.

**Solución:**
```bash
# 1. Detener y eliminar todos los contenedores y volúmenes
docker compose down -v --remove-orphans

# 2. Limpiar imágenes con problemas (opcional)
docker system prune -f

# 3. Verificar que los scripts SQL son válidos
cat docker/init-scripts/01-accounts-init.sql | head -20

# 4. Volver a arrancar desde cero
docker compose up -d postgres-accounts postgres-transactions redis

# 5. Esperar 30 segundos y verificar health
sleep 30 && docker compose ps

# 6. Verificar logs del contenedor con problemas
docker logs postgres-accounts --tail 50
```

---

### Problema 2: El microservicio Spring Boot falla al conectar con R2DBC

**Síntomas:**
- Error en el arranque: `io.r2dbc.spi.R2dbcNonTransientResourceException: Unable to connect to localhost`
- El servicio muestra `status: DOWN` en el health check
- Logs muestran: `Connection to localhost:5432 refused`

**Causa:**
El microservicio intenta conectarse antes de que PostgreSQL esté completamente listo, o la URL de R2DBC tiene un puerto incorrecto.

**Solución:**
```bash
# 1. Verificar que PostgreSQL está escuchando en el puerto correcto
docker exec postgres-accounts pg_isready -U postgres -d accounts_db

# 2. Verificar la URL de R2DBC en application.properties
grep "spring.r2dbc" account-service/src/main/resources/application.properties

# 3. Para account-service debe ser puerto 5432
# Para transaction-service debe ser puerto 5433
# Corregir si es necesario:
sed -i 's/r2dbc:postgresql:\/\/localhost:5432/r2dbc:postgresql:\/\/localhost:5432/' \
  account-service/src/main/resources/application.properties

# 4. Reintentar el arranque del servicio
cd account-service && mvn spring-boot:run
```

---

### Problema 3: El API Gateway retorna 404 para rutas de account-service o transaction-service

**Síntomas:**
- `curl http://localhost:8080/api/accounts` retorna `404 Not Found`
- El gateway arranca correctamente pero no enruta las peticiones
- Los servicios individuales responden bien en sus puertos directos (8081, 8082)

**Causa:**
Las rutas del `application.yml` del gateway pueden tener predicados incorrectos o los URIs apuntan a URLs erróneas.

**Solución:**
```bash
# 1. Verificar rutas activas del Gateway
curl -s http://localhost:8080/actuator/gateway/routes | python3 -m json.tool

# 2. Verificar que los URIs en application.yml son correctos
grep -A 3 "uri:" api-gateway/src/main/resources/application.yml

# 3. Probar conectividad directa desde el gateway al account-service
# (verificar que no hay firewall bloqueando puertos locales)
curl -v http://localhost:8081/api/accounts -H "Authorization: Bearer $JWT_TOKEN"

# 4. Revisar logs del gateway para ver el error de routing
# (en la Terminal 4 donde corre el gateway)
# Buscar líneas con: "RoutePredicateHandlerMapping" o "404"

# 5. Si el problema persiste, reiniciar el gateway
cd api-gateway && mvn spring-boot:run
```

---

### Problema 4: El token JWT retorna 401 en endpoints del Gateway aunque se obtuvo correctamente

**Síntomas:**
- El login en `/api/auth/login` retorna un token
- Al usar ese token en `/api/accounts` se obtiene `401 Unauthorized`
- El header `Authorization: Bearer <token>` está correctamente formateado

**Causa:**
El secreto JWT configurado en el `api-gateway` no coincide con el secreto usado en el `auth-service` para firmar el token, o el filtro de autenticación del gateway tiene un error de configuración.

**Solución:**
```bash
# 1. Verificar que el secreto JWT es idéntico en todos los servicios
echo "=== Auth Service ===" && grep "jwt.secret" auth-service/src/main/resources/application.properties
echo "=== API Gateway ===" && grep "jwt.secret" api-gateway/src/main/resources/application.yml
echo "=== Account Service ===" && grep "jwt.secret" account-service/src/main/resources/application.properties

# 2. Si los secretos difieren, unificarlos con el mismo valor
# El secreto correcto es:
# curso-microservicios-reactivos-jwt-secret-key-256bits-segura-2024

# 3. Decodificar el token manualmente para verificar su validez
# (usar https://jwt.io o el siguiente comando)
echo $JWT_TOKEN | cut -d'.' -f2 | base64 -d 2>/dev/null | python3 -m json.tool

# 4. Verificar que el token no ha expirado (campo "exp" en el payload)
# 5. Reiniciar gateway y auth-service después de corregir configuración
```

---

### Problema 5: Prometheus no muestra métricas de los microservicios (targets DOWN)

**Síntomas:**
- En `http://localhost:9090/targets` los jobs de los microservicios aparecen como `DOWN`
- Error en Prometheus: `Get "http://host.docker.internal:8081/actuator/prometheus": dial tcp: connection refused`
- Los endpoints `/actuator/prometheus` funcionan cuando se acceden directamente

**Causa:**
En Linux, `host.docker.internal` no está disponible por defecto. En Windows/macOS funciona automáticamente, pero en Linux se necesita la IP del host.

**Solución:**
```bash
# 1. Obtener la IP del host Docker en Linux
HOST_IP=$(docker network inspect bridge --format='{{(index .IPAM.Config 0).Gateway}}')
echo "IP del host Docker: $HOST_IP"

# 2. Actualizar prometheus.yml reemplazando host.docker.internal con la IP real
sed -i "s/host.docker.internal/$HOST_IP/g" docker/prometheus/prometheus.yml

# 3. Recargar la configuración de Prometheus sin reiniciar
curl -s -X POST http://localhost:9090/-/reload
echo "Prometheus recargado"

# 4. Verificar targets después de 15 segundos
sleep 15
curl -s "http://localhost:9090/api/v1/targets" | python3 -c "
import sys, json
data = json.load(sys.stdin)
for t in data['data']['activeTargets']:
    print(f\"{t['labels']['job']}: {t['health']}\")
"

# Alternativa para macOS/Windows (si host.docker.internal no funciona):
# Agregar al docker-compose.yml en el servicio prometheus:
# extra_hosts:
#   - "host.docker.internal:host-gateway"
```

## Limpieza

```bash
# ─────────────────────────────────────────────────────────────
# LIMPIEZA COMPLETA DEL LABORATORIO 5
# ─────────────────────────────────────────────────────────────

# 1. Detener los microservicios Spring Boot
# (presionar Ctrl+C en cada terminal donde estén corriendo)
echo "Detener manualmente los microservicios Spring Boot en sus terminales (Ctrl+C)"

# 2. Detener y eliminar contenedores Docker (preservar volúmenes de datos)
docker compose down

# 3. Para limpieza COMPLETA incluyendo datos de base de datos (⚠️ irreversible):
# docker compose down -v --remove-orphans

# 4. Verificar que no quedan contenedores del curso en ejecución
docker ps --filter "name=postgres-accounts" \
          --filter "name=postgres-transactions" \
          --filter "name=redis-banking" \
          --filter "name=prometheus-banking" \
          --filter "name=grafana-banking"

# 5. Liberar puertos verificando que están libres
lsof -i :8080 -i :8081 -i :8082 -i :8083 2>/dev/null | grep LISTEN || echo "Puertos liberados correctamente"

# 6. Limpiar artefactos de compilación Maven (opcional, libera espacio)
cd ~/curso-microservicios-reactivos
mvn clean

# 7. Guardar el reporte final antes de limpiar
cp reporte-lab5.txt ~/reporte-lab5-final.txt
echo "Reporte guardado en: ~/reporte-lab5-final.txt"
```

> ⚠️ **Advertencia:** Ejecutar `docker compose down -v` eliminará **todos los datos** almacenados en PostgreSQL. Si necesitas conservar los datos para referencia futura, exporta las bases de datos antes de limpiar:
> ```bash
> docker exec postgres-accounts pg_dump -U postgres accounts_db > backup-accounts.sql
> docker exec postgres-transactions pg_dump -U postgres transactions_db > backup-transactions.sql
> ```

## Resumen

### Lo Que Lograste

- **Diseñaste e implementaste** el modelo de dominio completo de un sistema bancario reactivo con dos bounded contexts: `account-service` y `transaction-service`, aplicando entidades R2DBC, enums de estado y DTOs como Java Records
- **Arrancaste y validaste** toda la infraestructura del sistema: cuatro microservicios Spring Boot + PostgreSQL (x2) + Redis + Prometheus + Grafana, verificando la salud de cada componente
- **Ejecutaste el flujo end-to-end completo**: autenticación JWT → creación de cuenta → registro de transacciones → consulta de historial, todo a través del API Gateway con seguridad stateless
- **Verificaste los patrones de resiliencia**: circuit breaker con Resilience4j actuando correctamente ante fallos del `account-service`
- **Validaste la observabilidad**: métricas Micrometer/Prometheus disponibles en todos los servicios y visualizadas en un dashboard Grafana

### Conceptos Clave Aprendidos

- Los **bounded contexts** de DDD son el fundamento para dividir un sistema en microservicios con responsabilidades claras y no superpuestas
- Los **DTOs como Java Records** (inmutables) desacoplan la API pública del modelo interno, permitiendo evolucionar cada capa independientemente
- El **contrato entre servicios** (`GET /api/accounts/{id}`) debe definirse antes de implementar para permitir desarrollo paralelo
- En WebFlux, los errores deben manejarse **dentro del pipeline reactivo** con `onErrorResume`/`onErrorMap`, nunca con try-catch bloqueante
- La **propagación del header `X-Trace-Id`** a través del Gateway es fundamental para correlacionar logs y trazar requests en sistemas distribuidos
- Los **scripts SQL de inicialización** en Docker Compose garantizan datos de prueba consistentes en cada arranque del entorno

### Próximos Pasos

- Explorar **Spring Cloud Sleuth + Zipkin** para trazabilidad distribuida automatizada entre microservicios
- Implementar **Server-Sent Events (SSE)** para streaming reactivo de actualizaciones de saldo en tiempo real
- Agregar **pruebas de integración reactivas** con `WebTestClient` y `StepVerifier` para automatizar la validación del flujo end-to-end
- Estudiar **CQRS (Command Query Responsibility Segregation)** como evolución natural del modelo de dominio implementado
- Profundizar en **backpressure** con `Flux.limitRate()` para controlar el flujo de transacciones en escenarios de alta carga

## Recursos Adicionales

- **Domain-Driven Design Reference** (Eric Evans) — Referencia fundamental sobre bounded contexts, agregados y modelado del dominio: https://domainlanguage.com/ddd/reference/
- **Spring Data R2DBC Documentation** — Guía oficial para persistencia reactiva no-bloqueante con PostgreSQL: https://docs.spring.io/spring-data/r2dbc/docs/current/reference/html/
- **Spring Cloud Gateway Documentation** — Configuración avanzada de rutas, filtros y rate limiting: https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/
- **Resilience4j User Guide** — Implementación de circuit breaker, retry y timeout con Project Reactor: https://resilience4j.readme.io/docs/getting-started
- **Micrometer Documentation** — Instrumentación de métricas personalizadas con Counters, Timers y Gauges: https://micrometer.io/docs
- **Project Reactor Reference Guide** — Operadores avanzados, backpressure y testing con StepVerifier: https://projectreactor.io/docs/core/release/reference/
- **JWT.io** — Herramienta online para decodificar y validar tokens JWT durante el debugging: https://jwt.io
- **Prometheus Query Language (PromQL)** — Guía de queries para análisis de métricas: https://prometheus.io/docs/prometheus/latest/querying/basics/

---
LAB_END---
