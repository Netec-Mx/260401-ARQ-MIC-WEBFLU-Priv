# Microservicios Reactivos con WebFlux, R2DBC y WebClient

## Metadatos

| Propiedad | Valor |
|-----------|-------|
| **Duración** | 85 minutos |
| **Complejidad** | Intermedio |
| **Nivel Bloom** | Aplicar |
| **Laboratorio Anterior** | Lab 01: Estructura multi-módulo con API Gateway |
| **Servicios Involucrados** | accounts-service (8081), transactions-service (8082), PostgreSQL (5432, 5433) |

## Descripción General

En este laboratorio implementarás la lógica de negocio reactiva completa en los microservicios `accounts-service` y `transactions-service` del sistema bancario simplificado construido en el Laboratorio 1. Cada servicio expondrá endpoints REST reactivos usando Spring WebFlux con `Mono` y `Flux`, persistencia no-bloqueante con Spring Data R2DBC sobre PostgreSQL, y comunicación inter-servicios mediante `WebClient` reactivo.

El valor práctico de este laboratorio radica en experimentar de primera mano la diferencia entre el modelo bloqueante tradicional (RestTemplate + JPA) y el modelo reactivo no-bloqueante (WebClient + R2DBC): cuando `transactions-service` valida una cuenta antes de registrar una transacción, lo hace sin bloquear ningún hilo del servidor, manteniendo la cadena reactiva intacta desde la base de datos hasta la respuesta HTTP.

## Objetivos de Aprendizaje

Al completar este laboratorio, serás capaz de:

- [ ] Implementar endpoints CRUD reactivos en `accounts-service` usando `@RestController` con retornos `Mono<T>` y `Flux<T>` conectados a R2DBC
- [ ] Configurar Spring Data R2DBC con PostgreSQL para persistencia no-bloqueante, incluyendo esquema de base de datos y datos de prueba
- [ ] Configurar un bean `WebClient` con `baseUrl`, filtros de logging, timeout de conexión (5s) y timeout de lectura (10s)
- [ ] Implementar comunicación reactiva inter-servicios desde `transactions-service` hacia `accounts-service` para validar cuentas
- [ ] Manejar errores reactivos con operadores `onErrorResume`, `onErrorReturn` y excepciones personalizadas sin romper el pipeline reactivo
- [ ] Añadir logging estructurado con SLF4J incluyendo campos `traceId`, `serviceId`, `requestPath` y `durationMs`

## Prerrequisitos

### Conocimiento Requerido

- Laboratorio 1 completado: proyecto multi-módulo Maven con estructura `banking-system/`, `accounts-service`, `transactions-service`, `api-gateway` y `common-lib`
- Comprensión de `Mono<T>` y `Flux<T>`: creación, transformación con `map`, `flatMap`, `filter`
- Familiaridad con anotaciones Spring Boot: `@RestController`, `@Service`, `@Repository`, `@Configuration`
- Conocimiento básico de Spring Data JPA (se migrará el concepto a R2DBC reactivo)
- Docker Desktop funcionando: capacidad de ejecutar `docker compose up`

### Acceso Requerido

- Proyecto `banking-system` del Laboratorio 1 en estado funcional (o código de solución provisto por el instructor)
- Docker Desktop con acceso a Docker Hub para descargar imagen `postgres:15`
- IntelliJ IDEA con el proyecto multi-módulo abierto
- Postman instalado para pruebas de endpoints

### Verificación Rápida del Laboratorio Anterior

Antes de comenzar, ejecuta los siguientes comandos para confirmar que el estado del Lab 1 es correcto:

```bash
cd banking-system
mvn clean compile -q
echo "Compilación exitosa: $?"
```

Si la compilación falla, solicita al instructor el código de solución del Laboratorio 1.

## Entorno de Laboratorio

### Requisitos de Hardware

| Componente | Especificación |
|------------|----------------|
| Procesador | Intel Core i5 / AMD Ryzen 5 (4+ núcleos) |
| RAM | Mínimo 16 GB (recomendado 32 GB) |
| Almacenamiento | 5 GB libres para imágenes Docker y dependencias Maven |
| Red | Acceso a internet para descargar dependencias de Maven Central |

### Requisitos de Software

| Software | Versión | Propósito |
|----------|---------|-----------|
| JDK | 17 LTS o 21 LTS | Compilación y ejecución de microservicios |
| Apache Maven | 3.9.x | Gestión de dependencias y construcción |
| Docker Desktop | 4.25+ | Ejecutar PostgreSQL en contenedores |
| Docker Compose | 2.21+ | Orquestación de contenedores de infraestructura |
| IntelliJ IDEA | 2023.3+ | IDE principal |
| Postman | 10.x+ | Pruebas de endpoints REST |

### Configuración Inicial del Entorno

Verifica que Docker está funcionando y descarga la imagen de PostgreSQL antes de comenzar:

```bash
# Verificar Docker
docker --version
docker compose version

# Descargar imagen PostgreSQL (puede tardar 1-2 minutos)
docker pull postgres:15

# Verificar que el proyecto del Lab 1 existe
ls -la banking-system/
```

**Resultado esperado:**
```
Docker version 24.x.x
Docker Compose version v2.21.x
accounts-service  api-gateway  common-lib  pom.xml  transactions-service
```

## Instrucciones Paso a Paso

### Paso 1: Configurar Docker Compose con PostgreSQL para Ambos Servicios

**Objetivo:** Levantar dos instancias de PostgreSQL (una por servicio) con scripts de inicialización automática que crean esquemas y datos de prueba.

**Instrucciones:**

1. En la raíz del proyecto `banking-system`, crea el directorio de infraestructura y los scripts de inicialización:

   ```bash
   cd banking-system
   mkdir -p docker/postgres-accounts
   mkdir -p docker/postgres-transactions
   ```

2. Crea el script de inicialización para la base de datos de cuentas:

   ```bash
   cat > docker/postgres-accounts/init.sql << 'EOF'
   CREATE TABLE IF NOT EXISTS accounts (
       id SERIAL PRIMARY KEY,
       account_number VARCHAR(20) UNIQUE NOT NULL,
       owner_name VARCHAR(100) NOT NULL,
       balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
       account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
       active BOOLEAN NOT NULL DEFAULT true,
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
   );

   INSERT INTO accounts (account_number, owner_name, balance, account_type) VALUES
       ('ACC-001', 'Alice Johnson', 5000.00, 'CHECKING'),
       ('ACC-002', 'Bob Smith', 12500.50, 'SAVINGS'),
       ('ACC-003', 'Carol White', 750.00, 'CHECKING'),
       ('ACC-004', 'David Brown', 30000.00, 'SAVINGS');

   EOF
   echo "Script de cuentas creado."
   ```

3. Crea el script de inicialización para la base de datos de transacciones:

   ```bash
   cat > docker/postgres-transactions/init.sql << 'EOF'
   CREATE TABLE IF NOT EXISTS transactions (
       id SERIAL PRIMARY KEY,
       account_id BIGINT NOT NULL,
       transaction_type VARCHAR(20) NOT NULL,
       amount DECIMAL(15,2) NOT NULL,
       description VARCHAR(255),
       status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
   );

   INSERT INTO transactions (account_id, transaction_type, amount, description) VALUES
       (1, 'DEPOSIT', 1000.00, 'Depósito inicial'),
       (1, 'WITHDRAWAL', 200.00, 'Retiro cajero'),
       (2, 'DEPOSIT', 5000.00, 'Transferencia recibida');

   EOF
   echo "Script de transacciones creado."
   ```

4. Crea el archivo `docker-compose.yml` en la raíz del proyecto:

   ```bash
   cat > docker-compose.yml << 'EOF'
   version: '3.8'

   services:
     postgres-accounts:
       image: postgres:15
       container_name: postgres-accounts
       environment:
         POSTGRES_DB: accounts_db
         POSTGRES_USER: accounts_user
         POSTGRES_PASSWORD: accounts_pass
       ports:
         - "5432:5432"
       volumes:
         - ./docker/postgres-accounts/init.sql:/docker-entrypoint-initdb.d/init.sql
         - postgres_accounts_data:/var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U accounts_user -d accounts_db"]
         interval: 10s
         timeout: 5s
         retries: 5

     postgres-transactions:
       image: postgres:15
       container_name: postgres-transactions
       environment:
         POSTGRES_DB: transactions_db
         POSTGRES_USER: transactions_user
         POSTGRES_PASSWORD: transactions_pass
       ports:
         - "5433:5432"
       volumes:
         - ./docker/postgres-transactions/init.sql:/docker-entrypoint-initdb.d/init.sql
         - postgres_transactions_data:/var/lib/postgresql/data
       healthcheck:
         test: ["CMD-SHELL", "pg_isready -U transactions_user -d transactions_db"]
         interval: 10s
         timeout: 5s
         retries: 5

   volumes:
     postgres_accounts_data:
     postgres_transactions_data:

   EOF
   echo "docker-compose.yml creado."
   ```

5. Levanta los contenedores de PostgreSQL:

   ```bash
   docker compose up -d postgres-accounts postgres-transactions
   
   # Espera a que estén saludables
   docker compose ps
   ```

**Resultado Esperado:**
```
NAME                    STATUS
postgres-accounts       Up (healthy)
postgres-transactions   Up (healthy)
```

**Verificación:**

```bash
# Verifica que la tabla accounts existe con datos
docker exec postgres-accounts psql -U accounts_user -d accounts_db \
  -c "SELECT account_number, owner_name, balance FROM accounts;"

# Verifica que la tabla transactions existe con datos
docker exec postgres-transactions psql -U transactions_user -d transactions_db \
  -c "SELECT id, account_id, transaction_type, amount FROM transactions;"
```

Debes ver 4 cuentas en el primer comando y 3 transacciones en el segundo.

---

### Paso 2: Actualizar el POM Raíz y Añadir Dependencias R2DBC

**Objetivo:** Centralizar las versiones de dependencias R2DBC y WebFlux en el POM raíz para que todos los módulos las hereden consistentemente.

**Instrucciones:**

1. Abre el archivo `banking-system/pom.xml` (POM raíz) y actualiza la sección `<dependencyManagement>`:

   ```bash
   # Abre el archivo en tu editor o modifícalo directamente
   # El contenido completo del pom.xml raíz debe quedar así:
   cat > pom.xml << 'EOF'
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
            http://maven.apache.org/xsd/maven-4.0.0.xsd">
     <modelVersion>4.0.0</modelVersion>

     <groupId>com.banking</groupId>
     <artifactId>banking-system</artifactId>
     <version>1.0.0</version>
     <packaging>pom</packaging>
     <name>Banking System - Parent</name>

     <modules>
       <module>common-lib</module>
       <module>accounts-service</module>
       <module>transactions-service</module>
       <module>api-gateway</module>
     </modules>

     <properties>
       <java.version>17</java.version>
       <spring-boot.version>3.2.0</spring-boot.version>
       <maven.compiler.source>17</maven.compiler.source>
       <maven.compiler.target>17</maven.compiler.target>
       <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
     </properties>

     <dependencyManagement>
       <dependencies>
         <dependency>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-dependencies</artifactId>
           <version>${spring-boot.version}</version>
           <type>pom</type>
           <scope>import</scope>
         </dependency>
       </dependencies>
     </dependencyManagement>

     <build>
       <pluginManagement>
         <plugins>
           <plugin>
             <groupId>org.springframework.boot</groupId>
             <artifactId>spring-boot-maven-plugin</artifactId>
             <version>${spring-boot.version}</version>
           </plugin>
         </plugins>
       </pluginManagement>
     </build>
   </project>
   EOF
   ```

2. Actualiza el POM de `accounts-service` para incluir las dependencias R2DBC:

   ```bash
   cat > accounts-service/pom.xml << 'EOF'
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
            http://maven.apache.org/xsd/maven-4.0.0.xsd">
     <modelVersion>4.0.0</modelVersion>

     <parent>
       <groupId>com.banking</groupId>
       <artifactId>banking-system</artifactId>
       <version>1.0.0</version>
     </parent>

     <artifactId>accounts-service</artifactId>
     <name>Accounts Service</name>

     <dependencies>
       <!-- WebFlux: servidor reactivo Netty -->
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-webflux</artifactId>
       </dependency>

       <!-- R2DBC: acceso reactivo a base de datos -->
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-data-r2dbc</artifactId>
       </dependency>

       <!-- Driver R2DBC para PostgreSQL -->
       <dependency>
         <groupId>org.postgresql</groupId>
         <artifactId>r2dbc-postgresql</artifactId>
       </dependency>

       <!-- Actuator para health checks -->
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-actuator</artifactId>
       </dependency>

       <!-- Librería común del proyecto -->
       <dependency>
         <groupId>com.banking</groupId>
         <artifactId>common-lib</artifactId>
         <version>${project.version}</version>
       </dependency>

       <!-- Lombok para reducir boilerplate -->
       <dependency>
         <groupId>org.projectlombok</groupId>
         <artifactId>lombok</artifactId>
         <optional>true</optional>
       </dependency>

       <!-- Tests -->
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-test</artifactId>
         <scope>test</scope>
       </dependency>
       <dependency>
         <groupId>io.projectreactor</groupId>
         <artifactId>reactor-test</artifactId>
         <scope>test</scope>
       </dependency>
     </dependencies>

     <build>
       <plugins>
         <plugin>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-maven-plugin</artifactId>
           <configuration>
             <excludes>
               <exclude>
                 <groupId>org.projectlombok</groupId>
                 <artifactId>lombok</artifactId>
               </exclude>
             </excludes>
           </configuration>
         </plugin>
       </plugins>
     </build>
   </project>
   EOF
   ```

3. Actualiza el POM de `transactions-service` con dependencias similares más WebClient:

   ```bash
   cat > transactions-service/pom.xml << 'EOF'
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
            http://maven.apache.org/xsd/maven-4.0.0.xsd">
     <modelVersion>4.0.0</modelVersion>

     <parent>
       <groupId>com.banking</groupId>
       <artifactId>banking-system</artifactId>
       <version>1.0.0</version>
     </parent>

     <artifactId>transactions-service</artifactId>
     <name>Transactions Service</name>

     <dependencies>
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-webflux</artifactId>
       </dependency>
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-data-r2dbc</artifactId>
       </dependency>
       <dependency>
         <groupId>org.postgresql</groupId>
         <artifactId>r2dbc-postgresql</artifactId>
       </dependency>
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-actuator</artifactId>
       </dependency>
       <dependency>
         <groupId>com.banking</groupId>
         <artifactId>common-lib</artifactId>
         <version>${project.version}</version>
       </dependency>
       <dependency>
         <groupId>org.projectlombok</groupId>
         <artifactId>lombok</artifactId>
         <optional>true</optional>
       </dependency>
       <dependency>
         <groupId>org.springframework.boot</groupId>
         <artifactId>spring-boot-starter-test</artifactId>
         <scope>test</scope>
       </dependency>
       <dependency>
         <groupId>io.projectreactor</groupId>
         <artifactId>reactor-test</artifactId>
         <scope>test</scope>
       </dependency>
     </dependencies>

     <build>
       <plugins>
         <plugin>
           <groupId>org.springframework.boot</groupId>
           <artifactId>spring-boot-maven-plugin</artifactId>
           <configuration>
             <excludes>
               <exclude>
                 <groupId>org.projectlombok</groupId>
                 <artifactId>lombok</artifactId>
               </exclude>
             </excludes>
           </configuration>
         </plugin>
       </plugins>
     </build>
   </project>
   EOF
   ```

4. Verifica que el proyecto compila con las nuevas dependencias:

   ```bash
   mvn clean compile -pl accounts-service,transactions-service
   ```

**Resultado Esperado:**
```
[INFO] BUILD SUCCESS
[INFO] accounts-service .......................... SUCCESS
[INFO] transactions-service ...................... SUCCESS
```

**Verificación:**

- Sin errores de compilación en ninguno de los dos módulos
- Maven descarga las dependencias R2DBC de Maven Central (puede tardar 1-2 minutos la primera vez)

---

### Paso 3: Implementar el Modelo y Repositorio Reactivo en accounts-service

**Objetivo:** Crear la entidad `Account` con anotaciones R2DBC y el repositorio que extiende `ReactiveCrudRepository`, entendiendo las diferencias clave con JPA.

**Instrucciones:**

> ⚠️ **Nota importante para estudiantes con background en JPA:** R2DBC usa las mismas anotaciones `@Table` y `@Column` de Spring Data, pero el repositorio extiende `ReactiveCrudRepository<T, ID>` en lugar de `JpaRepository`. Los métodos retornan `Mono<T>` o `Flux<T>` en lugar de objetos directos. No existe `@Entity` de JPA; se usa `@Table` de Spring Data.

1. Crea la estructura de directorios del servicio:

   ```bash
   mkdir -p accounts-service/src/main/java/com/banking/accounts/model
   mkdir -p accounts-service/src/main/java/com/banking/accounts/repository
   mkdir -p accounts-service/src/main/java/com/banking/accounts/service
   mkdir -p accounts-service/src/main/java/com/banking/accounts/controller
   mkdir -p accounts-service/src/main/java/com/banking/accounts/config
   mkdir -p accounts-service/src/main/java/com/banking/accounts/exception
   mkdir -p accounts-service/src/main/resources
   ```

2. Crea la entidad `Account`:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/model/Account.java << 'EOF'
   package com.banking.accounts.model;

   import lombok.AllArgsConstructor;
   import lombok.Builder;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   import org.springframework.data.annotation.CreatedDate;
   import org.springframework.data.annotation.Id;
   import org.springframework.data.relational.core.mapping.Column;
   import org.springframework.data.relational.core.mapping.Table;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   /**
    * Entidad de dominio para cuentas bancarias.
    * Nota R2DBC: @Table y @Column son de Spring Data Relational,
    * NO de JPA. No existe @Entity aquí.
    */
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   @Table("accounts")
   public class Account {

       @Id
       private Long id;

       @Column("account_number")
       private String accountNumber;

       @Column("owner_name")
       private String ownerName;

       @Column("balance")
       private BigDecimal balance;

       @Column("account_type")
       private String accountType;

       @Column("active")
       private Boolean active;

       @Column("created_at")
       @CreatedDate
       private LocalDateTime createdAt;
   }
   EOF
   ```

3. Crea el repositorio reactivo:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/repository/AccountRepository.java << 'EOF'
   package com.banking.accounts.repository;

   import com.banking.accounts.model.Account;
   import org.springframework.data.repository.reactive.ReactiveCrudRepository;
   import org.springframework.stereotype.Repository;
   import reactor.core.publisher.Flux;
   import reactor.core.publisher.Mono;

   /**
    * Repositorio reactivo para cuentas.
    *
    * Diferencia clave con JPA:
    * - JPA:    JpaRepository<Account, Long>  → List<Account> findAll()
    * - R2DBC:  ReactiveCrudRepository<Account, Long> → Flux<Account> findAll()
    *
    * Todos los métodos retornan Mono o Flux, nunca bloquean un hilo.
    */
   @Repository
   public interface AccountRepository extends ReactiveCrudRepository<Account, Long> {

       /**
        * Busca una cuenta por su número único.
        * Spring Data genera la query automáticamente por convención de nombre.
        */
       Mono<Account> findByAccountNumber(String accountNumber);

       /**
        * Busca todas las cuentas activas.
        */
       Flux<Account> findByActiveTrue();

       /**
        * Verifica si existe una cuenta con ese número.
        */
       Mono<Boolean> existsByAccountNumber(String accountNumber);
   }
   EOF
   ```

4. Crea la excepción personalizada para cuenta no encontrada:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/exception/AccountNotFoundException.java << 'EOF'
   package com.banking.accounts.exception;

   /**
    * Excepción lanzada cuando una cuenta no existe en el sistema.
    * Se usa con onErrorResume en el pipeline reactivo para
    * transformarla en una respuesta HTTP 404.
    */
   public class AccountNotFoundException extends RuntimeException {

       private final String accountId;

       public AccountNotFoundException(String accountId) {
           super("Cuenta no encontrada con identificador: " + accountId);
           this.accountId = accountId;
       }

       public String getAccountId() {
           return accountId;
       }
   }
   EOF
   ```

**Resultado Esperado:**

```bash
# Verifica que los archivos se crearon correctamente
find accounts-service/src -name "*.java" | sort
```

```
accounts-service/src/main/java/com/banking/accounts/exception/AccountNotFoundException.java
accounts-service/src/main/java/com/banking/accounts/model/Account.java
accounts-service/src/main/java/com/banking/accounts/repository/AccountRepository.java
```

**Verificación:**

```bash
mvn compile -pl accounts-service -q
echo "Compilación accounts-service: $?"
```

El código debe compilar sin errores.

---

### Paso 4: Implementar el Servicio y Controlador Reactivo de accounts-service

**Objetivo:** Crear la capa de servicio con lógica de negocio reactiva y el controlador REST con endpoints CRUD que retornan `Mono` y `Flux`.

**Instrucciones:**

1. Crea el servicio de cuentas con manejo de errores reactivo:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/service/AccountService.java << 'EOF'
   package com.banking.accounts.service;

   import com.banking.accounts.exception.AccountNotFoundException;
   import com.banking.accounts.model.Account;
   import com.banking.accounts.repository.AccountRepository;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.stereotype.Service;
   import reactor.core.publisher.Flux;
   import reactor.core.publisher.Mono;

   import java.time.LocalDateTime;

   /**
    * Servicio reactivo para operaciones de cuentas bancarias.
    *
    * IMPORTANTE: Todos los métodos retornan Mono/Flux.
    * Nunca usar .block() aquí: rompería el modelo reactivo.
    */
   @Slf4j
   @Service
   @RequiredArgsConstructor
   public class AccountService {

       private final AccountRepository accountRepository;

       /**
        * Obtiene todas las cuentas activas.
        * Flux<Account> = stream de 0..N cuentas, no-bloqueante.
        */
       public Flux<Account> findAllActive() {
           log.info("Consultando todas las cuentas activas");
           return accountRepository.findByActiveTrue()
               .doOnNext(account -> log.debug("Cuenta encontrada: {}", account.getAccountNumber()))
               .doOnComplete(() -> log.info("Consulta de cuentas completada"));
       }

       /**
        * Busca una cuenta por ID.
        * Mono<Account> = 0 o 1 cuenta.
        * switchIfEmpty transforma "vacío" en error semántico del dominio.
        */
       public Mono<Account> findById(Long id) {
           log.info("Buscando cuenta con id: {}", id);
           return accountRepository.findById(id)
               .switchIfEmpty(Mono.error(
                   new AccountNotFoundException(String.valueOf(id))
               ))
               .doOnSuccess(account -> log.info("Cuenta encontrada: {}", account.getAccountNumber()));
       }

       /**
        * Busca una cuenta por número de cuenta.
        * Usado por transactions-service para validar existencia.
        */
       public Mono<Account> findByAccountNumber(String accountNumber) {
           log.info("Buscando cuenta por número: {}", accountNumber);
           return accountRepository.findByAccountNumber(accountNumber)
               .switchIfEmpty(Mono.error(
                   new AccountNotFoundException(accountNumber)
               ));
       }

       /**
        * Crea una nueva cuenta bancaria.
        * flatMap encadena la operación de guardado de forma no-bloqueante.
        */
       public Mono<Account> createAccount(Account account) {
           log.info("Creando nueva cuenta para: {}", account.getOwnerName());
           return accountRepository.existsByAccountNumber(account.getAccountNumber())
               .flatMap(exists -> {
                   if (exists) {
                       return Mono.error(new IllegalArgumentException(
                           "Ya existe una cuenta con número: " + account.getAccountNumber()
                       ));
                   }
                   account.setActive(true);
                   account.setCreatedAt(LocalDateTime.now());
                   return accountRepository.save(account);
               })
               .doOnSuccess(saved -> log.info("Cuenta creada con id: {}", saved.getId()));
       }
   }
   EOF
   ```

2. Crea el controlador REST reactivo:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/controller/AccountController.java << 'EOF'
   package com.banking.accounts.controller;

   import com.banking.accounts.exception.AccountNotFoundException;
   import com.banking.accounts.model.Account;
   import com.banking.accounts.service.AccountService;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.*;
   import reactor.core.publisher.Flux;
   import reactor.core.publisher.Mono;

   /**
    * Controlador REST reactivo para cuentas bancarias.
    *
    * Diferencia clave con Spring MVC:
    * - Spring MVC:   List<Account> getAllAccounts()  → bloquea hilo hasta tener todos
    * - WebFlux:      Flux<Account> getAllAccounts()  → emite cada cuenta conforme llega
    */
   @Slf4j
   @RestController
   @RequestMapping("/api/accounts")
   @RequiredArgsConstructor
   public class AccountController {

       private final AccountService accountService;

       /**
        * GET /api/accounts
        * Retorna todas las cuentas activas como stream reactivo.
        */
       @GetMapping
       public Flux<Account> getAllAccounts() {
           log.info("GET /api/accounts - Solicitando todas las cuentas");
           return accountService.findAllActive();
       }

       /**
        * GET /api/accounts/{id}
        * Retorna una cuenta o 404 si no existe.
        * onErrorResume captura AccountNotFoundException y la convierte en 404.
        */
       @GetMapping("/{id}")
       public Mono<ResponseEntity<Account>> getAccountById(@PathVariable Long id) {
           log.info("GET /api/accounts/{} - Buscando cuenta", id);
           return accountService.findById(id)
               .map(ResponseEntity::ok)
               .onErrorResume(AccountNotFoundException.class, ex -> {
                   log.warn("Cuenta no encontrada: {}", ex.getMessage());
                   return Mono.just(ResponseEntity.notFound().<Account>build());
               });
       }

       /**
        * GET /api/accounts/number/{accountNumber}
        * Endpoint usado por transactions-service para validar cuentas.
        */
       @GetMapping("/number/{accountNumber}")
       public Mono<ResponseEntity<Account>> getAccountByNumber(
               @PathVariable String accountNumber) {
           log.info("GET /api/accounts/number/{} - Validando cuenta", accountNumber);
           return accountService.findByAccountNumber(accountNumber)
               .map(ResponseEntity::ok)
               .onErrorResume(AccountNotFoundException.class, ex -> {
                   log.warn("Cuenta no encontrada por número: {}", accountNumber);
                   return Mono.just(ResponseEntity.notFound().<Account>build());
               });
       }

       /**
        * POST /api/accounts
        * Crea una nueva cuenta y retorna 201 Created.
        * onErrorResume maneja el caso de número duplicado con 400 Bad Request.
        */
       @PostMapping
       public Mono<ResponseEntity<Account>> createAccount(@RequestBody Account account) {
           log.info("POST /api/accounts - Creando cuenta para: {}", account.getOwnerName());
           return accountService.createAccount(account)
               .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
               .onErrorResume(IllegalArgumentException.class, ex -> {
                   log.warn("Error al crear cuenta: {}", ex.getMessage());
                   return Mono.just(ResponseEntity.badRequest().<Account>build());
               });
       }
   }
   EOF
   ```

3. Crea la clase principal del servicio (si no existe del Lab 1):

   ```bash
   mkdir -p accounts-service/src/main/java/com/banking/accounts
   cat > accounts-service/src/main/java/com/banking/accounts/AccountServiceApplication.java << 'EOF'
   package com.banking.accounts;

   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;

   @SpringBootApplication
   public class AccountServiceApplication {
       public static void main(String[] args) {
           SpringApplication.run(AccountServiceApplication.class, args);
       }
   }
   EOF
   ```

4. Crea el archivo de configuración `application.properties` para `accounts-service`:

   ```bash
   cat > accounts-service/src/main/resources/application.properties << 'EOF'
   # Identificación del servicio
   spring.application.name=accounts-service
   server.port=8081

   # Configuración R2DBC para PostgreSQL
   # Perfil local: conecta a PostgreSQL en localhost
   spring.r2dbc.url=r2dbc:postgresql://localhost:5432/accounts_db
   spring.r2dbc.username=accounts_user
   spring.r2dbc.password=accounts_pass

   # Actuator: exponer endpoints de salud y métricas
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=always

   # Logging
   logging.level.com.banking.accounts=DEBUG
   logging.level.org.springframework.data.r2dbc=DEBUG

   # Información del servicio
   info.app.name=Accounts Service
   info.app.version=1.0.0
   info.app.description=Microservicio reactivo para gestión de cuentas bancarias
   EOF
   ```

**Resultado Esperado:**

```bash
mvn compile -pl accounts-service -q
echo "Estado compilación: $?"
```

```
Estado compilación: 0
```

**Verificación:**

```bash
# Lista todos los archivos Java creados en accounts-service
find accounts-service/src/main/java -name "*.java" | sort
```

Debes ver 5 archivos: `AccountServiceApplication.java`, `Account.java`, `AccountRepository.java`, `AccountService.java`, `AccountController.java` y `AccountNotFoundException.java`.

---

### Paso 5: Configurar WebClient en transactions-service

**Objetivo:** Crear un bean `WebClient` configurado con `baseUrl`, filtros de logging de request/response, timeout de conexión de 5 segundos y timeout de lectura de 10 segundos para comunicarse con `accounts-service`.

**Instrucciones:**

1. Crea la estructura de directorios de `transactions-service`:

   ```bash
   mkdir -p transactions-service/src/main/java/com/banking/transactions/model
   mkdir -p transactions-service/src/main/java/com/banking/transactions/repository
   mkdir -p transactions-service/src/main/java/com/banking/transactions/service
   mkdir -p transactions-service/src/main/java/com/banking/transactions/controller
   mkdir -p transactions-service/src/main/java/com/banking/transactions/config
   mkdir -p transactions-service/src/main/java/com/banking/transactions/exception
   mkdir -p transactions-service/src/main/java/com/banking/transactions/client
   mkdir -p transactions-service/src/main/resources
   ```

2. Crea la configuración de `WebClient` con timeouts y filtros de logging:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/config/WebClientConfig.java << 'EOF'
   package com.banking.transactions.config;

   import io.netty.channel.ChannelOption;
   import io.netty.handler.timeout.ReadTimeoutHandler;
   import io.netty.handler.timeout.WriteTimeoutHandler;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.http.client.reactive.ReactorClientHttpConnector;
   import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
   import org.springframework.web.reactive.function.client.WebClient;
   import reactor.core.publisher.Mono;
   import reactor.netty.http.client.HttpClient;

   import java.time.Duration;
   import java.util.concurrent.TimeUnit;

   /**
    * Configuración del WebClient para comunicación con accounts-service.
    *
    * Se configuran:
    * - Timeout de conexión: 5 segundos (tiempo para establecer conexión TCP)
    * - Timeout de lectura: 10 segundos (tiempo para recibir respuesta completa)
    * - Filtros de logging: registra cada request/response para trazabilidad
    */
   @Slf4j
   @Configuration
   public class WebClientConfig {

       @Value("${services.accounts.base-url:http://localhost:8081}")
       private String accountsServiceBaseUrl;

       @Bean
       public WebClient accountsWebClient() {
           // Configurar HttpClient de Reactor Netty con timeouts
           HttpClient httpClient = HttpClient.create()
               // Timeout para establecer la conexión TCP (5 segundos)
               .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
               // Timeout de respuesta total
               .responseTimeout(Duration.ofSeconds(10))
               // Handlers de timeout de lectura y escritura en el pipeline Netty
               .doOnConnected(conn ->
                   conn.addHandlerLast(new ReadTimeoutHandler(10, TimeUnit.SECONDS))
                       .addHandlerLast(new WriteTimeoutHandler(5, TimeUnit.SECONDS))
               );

           return WebClient.builder()
               .baseUrl(accountsServiceBaseUrl)
               .clientConnector(new ReactorClientHttpConnector(httpClient))
               // Filtro de logging de requests salientes
               .filter(logRequest())
               // Filtro de logging de responses recibidas
               .filter(logResponse())
               .build();
       }

       /**
        * Filtro que registra cada request HTTP saliente.
        * ExchangeFilterFunction permite interceptar el pipeline reactivo.
        */
       private ExchangeFilterFunction logRequest() {
           return ExchangeFilterFunction.ofRequestProcessor(request -> {
               log.info("[WebClient] → {} {} | Headers: {}",
                   request.method(),
                   request.url(),
                   request.headers().entrySet());
               return Mono.just(request);
           });
       }

       /**
        * Filtro que registra cada response HTTP recibida.
        */
       private ExchangeFilterFunction logResponse() {
           return ExchangeFilterFunction.ofResponseProcessor(response -> {
               log.info("[WebClient] ← Status: {} | Headers: {}",
                   response.statusCode(),
                   response.headers().asHttpHeaders().entrySet());
               return Mono.just(response);
           });
       }
   }
   EOF
   ```

3. Crea el cliente HTTP para `accounts-service`:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/client/AccountsClient.java << 'EOF'
   package com.banking.transactions.client;

   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.stereotype.Component;
   import org.springframework.web.reactive.function.client.WebClient;
   import org.springframework.web.reactive.function.client.WebClientResponseException;
   import reactor.core.publisher.Mono;

   import java.time.Duration;
   import java.util.Map;

   /**
    * Cliente reactivo para comunicarse con accounts-service.
    *
    * Encapsula todas las llamadas HTTP a accounts-service,
    * incluyendo manejo de errores y timeouts.
    */
   @Slf4j
   @Component
   @RequiredArgsConstructor
   public class AccountsClient {

       private final WebClient accountsWebClient;

       /**
        * Verifica si una cuenta existe en accounts-service.
        *
        * Retorna Mono<Boolean>:
        * - true: la cuenta existe y está activa
        * - false: la cuenta no existe (404) o el servicio falló
        *
        * onErrorResume captura errores del pipeline reactivo sin propagar
        * excepciones fuera del stream, manteniendo el modelo reactivo intacto.
        */
       public Mono<Boolean> accountExists(Long accountId) {
           log.info("Verificando existencia de cuenta con id: {}", accountId);

           return accountsWebClient
               .get()
               .uri("/api/accounts/{id}", accountId)
               .retrieve()
               // retrieve() lanza WebClientResponseException para 4xx y 5xx
               .bodyToMono(Map.class)
               .map(body -> true)
               // Si la cuenta no existe (404), retorna false en lugar de error
               .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                   log.warn("Cuenta {} no encontrada en accounts-service", accountId);
                   return Mono.just(false);
               })
               // Si hay timeout o error de red, retorna false con log de error
               .onErrorResume(Exception.class, ex -> {
                   log.error("Error al verificar cuenta {}: {}", accountId, ex.getMessage());
                   return Mono.just(false);
               })
               // Timeout adicional a nivel de operación (no solo conexión)
               .timeout(Duration.ofSeconds(8), Mono.just(false));
       }

       /**
        * Obtiene los detalles de una cuenta por su ID.
        * Retorna Mono vacío si la cuenta no existe.
        */
       public Mono<Map> getAccountDetails(Long accountId) {
           log.info("Obteniendo detalles de cuenta: {}", accountId);

           return accountsWebClient
               .get()
               .uri("/api/accounts/{id}", accountId)
               .retrieve()
               .bodyToMono(Map.class)
               .onErrorResume(WebClientResponseException.NotFound.class, ex -> {
                   log.warn("Cuenta {} no encontrada", accountId);
                   return Mono.empty();
               })
               .onErrorResume(Exception.class, ex -> {
                   log.error("Error obteniendo cuenta {}: {}", accountId, ex.getMessage());
                   return Mono.empty();
               });
       }
   }
   EOF
   ```

4. Crea el `application.properties` de `transactions-service`:

   ```bash
   cat > transactions-service/src/main/resources/application.properties << 'EOF'
   # Identificación del servicio
   spring.application.name=transactions-service
   server.port=8082

   # Configuración R2DBC para PostgreSQL de transacciones
   spring.r2dbc.url=r2dbc:postgresql://localhost:5433/transactions_db
   spring.r2dbc.username=transactions_user
   spring.r2dbc.password=transactions_pass

   # URL de accounts-service (perfil local)
   services.accounts.base-url=http://localhost:8081

   # Actuator
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=always

   # Logging
   logging.level.com.banking.transactions=DEBUG
   logging.level.org.springframework.data.r2dbc=DEBUG
   logging.level.reactor.netty.http.client=DEBUG

   # Información del servicio
   info.app.name=Transactions Service
   info.app.version=1.0.0
   EOF
   ```

**Resultado Esperado:**

```bash
mvn compile -pl transactions-service -q
echo "Compilación transactions-service: $?"
```

```
Compilación transactions-service: 0
```

**Verificación:**

- El bean `WebClient` se configura con Reactor Netty con timeouts correctos
- Los filtros de logging están registrados como `ExchangeFilterFunction`
- La URL base se lee desde `application.properties` con valor por defecto

---

### Paso 6: Implementar el Modelo, Repositorio, Servicio y Controlador de transactions-service

**Objetivo:** Crear los componentes completos de `transactions-service`, incluyendo la validación reactiva de cuenta usando `WebClient` antes de registrar una transacción.

**Instrucciones:**

1. Crea la entidad `Transaction`:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/model/Transaction.java << 'EOF'
   package com.banking.transactions.model;

   import lombok.AllArgsConstructor;
   import lombok.Builder;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   import org.springframework.data.annotation.Id;
   import org.springframework.data.relational.core.mapping.Column;
   import org.springframework.data.relational.core.mapping.Table;

   import java.math.BigDecimal;
   import java.time.LocalDateTime;

   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   @Table("transactions")
   public class Transaction {

       @Id
       private Long id;

       @Column("account_id")
       private Long accountId;

       @Column("transaction_type")
       private String transactionType;

       @Column("amount")
       private BigDecimal amount;

       @Column("description")
       private String description;

       @Column("status")
       private String status;

       @Column("created_at")
       private LocalDateTime createdAt;
   }
   EOF
   ```

2. Crea el repositorio reactivo de transacciones:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/repository/TransactionRepository.java << 'EOF'
   package com.banking.transactions.repository;

   import com.banking.transactions.model.Transaction;
   import org.springframework.data.repository.reactive.ReactiveCrudRepository;
   import org.springframework.stereotype.Repository;
   import reactor.core.publisher.Flux;

   @Repository
   public interface TransactionRepository extends ReactiveCrudRepository<Transaction, Long> {

       /**
        * Obtiene todas las transacciones de una cuenta específica,
        * ordenadas por fecha de creación descendente.
        */
       Flux<Transaction> findByAccountIdOrderByCreatedAtDesc(Long accountId);
   }
   EOF
   ```

3. Crea la excepción para cuenta inválida:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/exception/InvalidAccountException.java << 'EOF'
   package com.banking.transactions.exception;

   /**
    * Excepción lanzada cuando se intenta crear una transacción
    * para una cuenta que no existe en accounts-service.
    */
   public class InvalidAccountException extends RuntimeException {

       private final Long accountId;

       public InvalidAccountException(Long accountId) {
           super("La cuenta con id " + accountId + " no existe o no está disponible");
           this.accountId = accountId;
       }

       public Long getAccountId() {
           return accountId;
       }
   }
   EOF
   ```

4. Crea el servicio de transacciones con llamada reactiva a `accounts-service`:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/service/TransactionService.java << 'EOF'
   package com.banking.transactions.service;

   import com.banking.transactions.client.AccountsClient;
   import com.banking.transactions.exception.InvalidAccountException;
   import com.banking.transactions.model.Transaction;
   import com.banking.transactions.repository.TransactionRepository;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.stereotype.Service;
   import reactor.core.publisher.Flux;
   import reactor.core.publisher.Mono;

   import java.time.LocalDateTime;

   /**
    * Servicio reactivo para operaciones de transacciones bancarias.
    *
    * Demuestra comunicación inter-servicios reactiva:
    * 1. Verifica que la cuenta existe en accounts-service (WebClient)
    * 2. Si existe, guarda la transacción (R2DBC)
    * 3. Todo en un pipeline reactivo sin bloquear hilos
    *
    * El operador flatMap es clave: encadena operaciones asíncronas
    * de forma que el resultado de una alimenta a la siguiente.
    */
   @Slf4j
   @Service
   @RequiredArgsConstructor
   public class TransactionService {

       private final TransactionRepository transactionRepository;
       private final AccountsClient accountsClient;

       /**
        * Obtiene todas las transacciones de una cuenta.
        */
       public Flux<Transaction> findByAccountId(Long accountId) {
           log.info("Consultando transacciones para cuenta: {}", accountId);
           return transactionRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
               .doOnComplete(() -> log.info("Consulta de transacciones completada para cuenta: {}", accountId));
       }

       /**
        * Busca una transacción por ID.
        */
       public Mono<Transaction> findById(Long id) {
           return transactionRepository.findById(id)
               .switchIfEmpty(Mono.error(
                   new RuntimeException("Transacción no encontrada: " + id)
               ));
       }

       /**
        * Crea una nueva transacción validando primero que la cuenta existe.
        *
        * Flujo reactivo:
        * accountsClient.accountExists(accountId)  ← llamada HTTP reactiva
        *   .flatMap(exists -> ...)                 ← encadena siguiente operación
        *     si existe: transactionRepository.save(transaction)
        *     si no existe: Mono.error(InvalidAccountException)
        *
        * IMPORTANTE: flatMap es necesario (no map) porque la operación
        * interna también retorna un Mono. map sería para transformaciones síncronas.
        */
       public Mono<Transaction> createTransaction(Transaction transaction) {
           log.info("Iniciando creación de transacción para cuenta: {}, tipo: {}, monto: {}",
               transaction.getAccountId(),
               transaction.getTransactionType(),
               transaction.getAmount());

           return accountsClient.accountExists(transaction.getAccountId())
               .flatMap(accountExists -> {
                   if (!accountExists) {
                       log.warn("Cuenta {} no válida para transacción", transaction.getAccountId());
                       return Mono.error(
                           new InvalidAccountException(transaction.getAccountId())
                       );
                   }

                   // La cuenta existe: proceder a guardar la transacción
                   transaction.setStatus("COMPLETED");
                   transaction.setCreatedAt(LocalDateTime.now());

                   log.info("Cuenta {} validada. Guardando transacción...", transaction.getAccountId());
                   return transactionRepository.save(transaction);
               })
               .doOnSuccess(saved ->
                   log.info("Transacción creada exitosamente con id: {}", saved.getId())
               );
       }
   }
   EOF
   ```

5. Crea el controlador REST de transacciones:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/controller/TransactionController.java << 'EOF'
   package com.banking.transactions.controller;

   import com.banking.transactions.exception.InvalidAccountException;
   import com.banking.transactions.model.Transaction;
   import com.banking.transactions.service.TransactionService;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.*;
   import reactor.core.publisher.Flux;
   import reactor.core.publisher.Mono;

   /**
    * Controlador REST reactivo para transacciones bancarias.
    *
    * Demuestra el patrón de manejo de errores reactivo:
    * - onErrorResume: captura error específico y retorna respuesta alternativa
    * - El pipeline reactivo nunca se "rompe" con try-catch bloqueante
    */
   @Slf4j
   @RestController
   @RequestMapping("/api/transactions")
   @RequiredArgsConstructor
   public class TransactionController {

       private final TransactionService transactionService;

       /**
        * GET /api/transactions/account/{accountId}
        * Obtiene todas las transacciones de una cuenta.
        */
       @GetMapping("/account/{accountId}")
       public Flux<Transaction> getTransactionsByAccount(@PathVariable Long accountId) {
           log.info("GET /api/transactions/account/{}", accountId);
           return transactionService.findByAccountId(accountId);
       }

       /**
        * GET /api/transactions/{id}
        * Obtiene una transacción por su ID.
        */
       @GetMapping("/{id}")
       public Mono<ResponseEntity<Transaction>> getTransactionById(@PathVariable Long id) {
           log.info("GET /api/transactions/{}", id);
           return transactionService.findById(id)
               .map(ResponseEntity::ok)
               .onErrorResume(RuntimeException.class, ex ->
                   Mono.just(ResponseEntity.notFound().<Transaction>build())
               );
       }

       /**
        * POST /api/transactions
        * Crea una nueva transacción.
        *
        * Flujo de manejo de errores reactivo:
        * 1. Si la cuenta no existe → 422 Unprocessable Entity
        * 2. Si hay error inesperado → 500 Internal Server Error
        * 3. Si todo va bien → 201 Created
        *
        * NOTA: onErrorResume mantiene el pipeline reactivo intacto.
        * NO usar try-catch aquí, rompería el modelo reactivo.
        */
       @PostMapping
       public Mono<ResponseEntity<Transaction>> createTransaction(
               @RequestBody Transaction transaction) {
           log.info("POST /api/transactions - Cuenta: {}, Tipo: {}, Monto: {}",
               transaction.getAccountId(),
               transaction.getTransactionType(),
               transaction.getAmount());

           return transactionService.createTransaction(transaction)
               .map(saved -> ResponseEntity.status(HttpStatus.CREATED).body(saved))
               .onErrorResume(InvalidAccountException.class, ex -> {
                   log.warn("Cuenta inválida: {}", ex.getMessage());
                   return Mono.just(
                       ResponseEntity.unprocessableEntity().<Transaction>build()
                   );
               })
               .onErrorResume(Exception.class, ex -> {
                   log.error("Error interno al crear transacción: {}", ex.getMessage());
                   return Mono.just(
                       ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                    .<Transaction>build()
                   );
               });
       }
   }
   EOF
   ```

6. Crea la clase principal de `transactions-service`:

   ```bash
   mkdir -p transactions-service/src/main/java/com/banking/transactions
   cat > transactions-service/src/main/java/com/banking/transactions/TransactionServiceApplication.java << 'EOF'
   package com.banking.transactions;

   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;

   @SpringBootApplication
   public class TransactionServiceApplication {
       public static void main(String[] args) {
           SpringApplication.run(TransactionServiceApplication.class, args);
       }
   }
   EOF
   ```

**Resultado Esperado:**

```bash
mvn compile -pl transactions-service -q
echo "Estado: $?"
```

```
Estado: 0
```

**Verificación:**

```bash
find transactions-service/src/main/java -name "*.java" | sort
```

Debes ver 7 archivos Java en `transactions-service`.

---

### Paso 7: Añadir Logging Estructurado con traceId en Ambos Servicios

**Objetivo:** Implementar un filtro WebFlux que genere un `traceId` único por request, lo propague en el contexto reactivo y lo incluya en todos los logs para correlacionar requests entre servicios.

**Instrucciones:**

1. Crea el filtro de trazabilidad en `accounts-service`:

   ```bash
   cat > accounts-service/src/main/java/com/banking/accounts/config/TracingFilter.java << 'EOF'
   package com.banking.accounts.config;

   import lombok.extern.slf4j.Slf4j;
   import org.slf4j.MDC;
   import org.springframework.core.annotation.Order;
   import org.springframework.stereotype.Component;
   import org.springframework.web.server.ServerWebExchange;
   import org.springframework.web.server.WebFilter;
   import org.springframework.web.server.WebFilterChain;
   import reactor.core.publisher.Mono;

   import java.time.Instant;
   import java.util.UUID;

   /**
    * Filtro WebFlux que añade trazabilidad a cada request.
    *
    * Genera un traceId único por request y lo propaga en:
    * - Header de respuesta X-Trace-Id (para que el cliente lo vea)
    * - MDC de SLF4J (para que aparezca en logs)
    * - Log de inicio y fin de cada request con durationMs
    *
    * NOTA IMPORTANTE sobre MDC en contexto reactivo:
    * MDC.put() en WebFlux no funciona como en Spring MVC porque
    * los hilos son compartidos. Aquí se usa para demostración.
    * En producción usar Reactor Context para propagación real.
    */
   @Slf4j
   @Component
   @Order(-100)
   public class TracingFilter implements WebFilter {

       private static final String TRACE_ID_HEADER = "X-Trace-Id";
       private static final String SERVICE_ID = "accounts-service";

       @Override
       public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
           // Generar traceId: reusar el del header si viene del Gateway
           String traceId = exchange.getRequest().getHeaders()
               .getFirst(TRACE_ID_HEADER);
           if (traceId == null || traceId.isEmpty()) {
               traceId = UUID.randomUUID().toString().substring(0, 8);
           }

           final String finalTraceId = traceId;
           final long startTime = Instant.now().toEpochMilli();
           final String requestPath = exchange.getRequest().getPath().value();
           final String method = exchange.getRequest().getMethod().name();

           // Añadir traceId al header de respuesta
           exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

           // Configurar MDC para este request
           MDC.put("traceId", finalTraceId);
           MDC.put("serviceId", SERVICE_ID);
           MDC.put("requestPath", requestPath);

           log.info("[{}] {} {} - Inicio de request | traceId={}",
               SERVICE_ID, method, requestPath, finalTraceId);

           return chain.filter(exchange)
               .doFinally(signalType -> {
                   long durationMs = Instant.now().toEpochMilli() - startTime;
                   int statusCode = exchange.getResponse().getStatusCode() != null
                       ? exchange.getResponse().getStatusCode().value()
                       : 0;

                   log.info("[{}] {} {} - Fin de request | traceId={} | status={} | durationMs={}",
                       SERVICE_ID, method, requestPath,
                       finalTraceId, statusCode, durationMs);

                   MDC.clear();
               });
       }
   }
   EOF
   ```

2. Crea el mismo filtro en `transactions-service`:

   ```bash
   cat > transactions-service/src/main/java/com/banking/transactions/config/TracingFilter.java << 'EOF'
   package com.banking.transactions.config;

   import lombok.extern.slf4j.Slf4j;
   import org.slf4j.MDC;
   import org.springframework.core.annotation.Order;
   import org.springframework.stereotype.Component;
   import org.springframework.web.server.ServerWebExchange;
   import org.springframework.web.server.WebFilter;
   import org.springframework.web.server.WebFilterChain;
   import reactor.core.publisher.Mono;

   import java.time.Instant;
   import java.util.UUID;

   @Slf4j
   @Component
   @Order(-100)
   public class TracingFilter implements WebFilter {

       private static final String TRACE_ID_HEADER = "X-Trace-Id";
       private static final String SERVICE_ID = "transactions-service";

       @Override
       public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
           String traceId = exchange.getRequest().getHeaders()
               .getFirst(TRACE_ID_HEADER);
           if (traceId == null || traceId.isEmpty()) {
               traceId = UUID.randomUUID().toString().substring(0, 8);
           }

           final String finalTraceId = traceId;
           final long startTime = Instant.now().toEpochMilli();
           final String requestPath = exchange.getRequest().getPath().value();
           final String method = exchange.getRequest().getMethod().name();

           exchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

           MDC.put("traceId", finalTraceId);
           MDC.put("serviceId", SERVICE_ID);
           MDC.put("requestPath", requestPath);

           log.info("[{}] {} {} - Inicio de request | traceId={}",
               SERVICE_ID, method, requestPath, finalTraceId);

           return chain.filter(exchange)
               .doFinally(signalType -> {
                   long durationMs = Instant.now().toEpochMilli() - startTime;
                   int statusCode = exchange.getResponse().getStatusCode() != null
                       ? exchange.getResponse().getStatusCode().value()
                       : 0;

                   log.info("[{}] {} {} - Fin | traceId={} | status={} | durationMs={}",
                       SERVICE_ID, method, requestPath,
                       finalTraceId, statusCode, durationMs);

                   MDC.clear();
               });
       }
   }
   EOF
   ```

3. Compila ambos servicios para verificar que el filtro compila correctamente:

   ```bash
   mvn compile -pl accounts-service,transactions-service -q
   echo "Compilación con filtros: $?"
   ```

**Resultado Esperado:**
```
Compilación con filtros: 0
```

**Verificación:**

- El filtro `TracingFilter` implementa `WebFilter` (interfaz de Spring WebFlux, no `javax.servlet.Filter`)
- El `@Order(-100)` garantiza que este filtro se ejecuta antes que otros filtros del sistema
- El header `X-Trace-Id` aparecerá en las respuestas HTTP

---

### Paso 8: Compilar, Empaquetar y Ejecutar los Servicios

**Objetivo:** Compilar el proyecto completo, generar los JARs ejecutables y arrancar ambos microservicios verificando que se conectan correctamente a PostgreSQL.

**Instrucciones:**

1. Confirma que los contenedores de PostgreSQL están corriendo:

   ```bash
   docker compose ps
   ```

   Si no están corriendo, levántalos:

   ```bash
   docker compose up -d postgres-accounts postgres-transactions
   sleep 10  # Espera a que PostgreSQL esté listo
   ```

2. Compila y empaqueta el proyecto completo desde la raíz:

   ```bash
   cd banking-system
   mvn clean package -DskipTests -pl common-lib,accounts-service,transactions-service
   ```

3. Abre **dos terminales separadas** y ejecuta cada servicio:

   **Terminal 1 - accounts-service:**
   ```bash
   cd banking-system
   java -jar accounts-service/target/accounts-service-1.0.0.jar
   ```

   **Terminal 2 - transactions-service:**
   ```bash
   cd banking-system
   java -jar transactions-service/target/transactions-service-1.0.0.jar
   ```

   > 💡 **Alternativa con Maven (para desarrollo):** En lugar de los JARs, puedes usar `mvn spring-boot:run -pl accounts-service` y `mvn spring-boot:run -pl transactions-service` en terminales separadas.

4. Verifica que ambos servicios están saludables usando Actuator:

   ```bash
   # Verificar accounts-service (espera 15-20 segundos para que arranque)
   curl -s http://localhost:8081/actuator/health | python3 -m json.tool

   # Verificar transactions-service
   curl -s http://localhost:8082/actuator/health | python3 -m json.tool
   ```

**Resultado Esperado:**

En la consola de `accounts-service` debes ver:
```
Started AccountServiceApplication in X.XXX seconds
```

La respuesta de health debe ser:
```json
{
    "status": "UP",
    "components": {
        "db": {
            "status": "UP"
        },
        "r2dbc": {
            "status": "UP"
        }
    }
}
```

**Verificación:**

```bash
# Verifica que el puerto 8081 está escuchando
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health
# Debe retornar: 200

# Verifica que el puerto 8082 está escuchando
curl -s -o /dev/null -w "%{http_code}" http://localhost:8082/actuator/health
# Debe retornar: 200
```

---

### Paso 9: Probar los Endpoints con Postman y curl

**Objetivo:** Ejecutar pruebas completas de los flujos end-to-end: obtener cuentas, crear transacciones (con validación reactiva inter-servicios) y verificar el manejo de errores.

**Instrucciones:**

1. **Prueba 1 - Obtener todas las cuentas activas:**

   ```bash
   curl -s http://localhost:8081/api/accounts | python3 -m json.tool
   ```

   **Resultado esperado:**
   ```json
   [
       {
           "id": 1,
           "accountNumber": "ACC-001",
           "ownerName": "Alice Johnson",
           "balance": 5000.00,
           "accountType": "CHECKING",
           "active": true
       },
       ...
   ]
   ```

2. **Prueba 2 - Obtener una cuenta específica:**

   ```bash
   curl -s http://localhost:8081/api/accounts/1 | python3 -m json.tool
   ```

3. **Prueba 3 - Intentar obtener una cuenta inexistente (debe retornar 404):**

   ```bash
   curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
     http://localhost:8081/api/accounts/999
   ```

   **Resultado esperado:**
   ```
   HTTP Status: 404
   ```

4. **Prueba 4 - Crear una nueva cuenta:**

   ```bash
   curl -s -X POST http://localhost:8081/api/accounts \
     -H "Content-Type: application/json" \
     -d '{
       "accountNumber": "ACC-005",
       "ownerName": "Eve Martinez",
       "balance": 2500.00,
       "accountType": "SAVINGS"
     }' | python3 -m json.tool
   ```

   **Resultado esperado:** HTTP 201 con el objeto de cuenta creado incluyendo `id`.

5. **Prueba 5 - Crear una transacción válida (flujo inter-servicios):**

   ```bash
   curl -s -X POST http://localhost:8082/api/transactions \
     -H "Content-Type: application/json" \
     -d '{
       "accountId": 1,
       "transactionType": "DEPOSIT",
       "amount": 500.00,
       "description": "Depósito de prueba Lab 2"
     }' | python3 -m json.tool
   ```

   **Resultado esperado:** HTTP 201. En los logs de `transactions-service` verás:
   ```
   [WebClient] → GET http://localhost:8081/api/accounts/1
   [WebClient] ← Status: 200 OK
   Cuenta 1 validada. Guardando transacción...
   Transacción creada exitosamente con id: 4
   ```

6. **Prueba 6 - Crear transacción con cuenta inexistente (validación reactiva):**

   ```bash
   curl -s -o /dev/null -w "HTTP Status: %{http_code}\n" \
     -X POST http://localhost:8082/api/transactions \
     -H "Content-Type: application/json" \
     -d '{
       "accountId": 9999,
       "transactionType": "DEPOSIT",
       "amount": 100.00,
       "description": "Transacción inválida"
     }'
   ```

   **Resultado esperado:**
   ```
   HTTP Status: 422
   ```

7. **Prueba 7 - Consultar transacciones de una cuenta:**

   ```bash
   curl -s http://localhost:8082/api/transactions/account/1 | python3 -m json.tool
   ```

8. **Prueba 8 - Verificar header de trazabilidad:**

   ```bash
   curl -s -I http://localhost:8081/api/accounts/1 | grep -i "x-trace-id"
   ```

   **Resultado esperado:**
   ```
   X-Trace-Id: a1b2c3d4
   ```

**Resultado Esperado General:**

Todos los endpoints responden con los códigos HTTP correctos. Los logs muestran el `traceId` en cada línea. La comunicación inter-servicios funciona y el manejo de errores reactivo retorna 422 para cuentas inexistentes.

**Verificación:**

```bash
# Prueba rápida de todos los endpoints críticos
echo "=== accounts-service ==="
curl -s -o /dev/null -w "GET /accounts: %{http_code}\n" http://localhost:8081/api/accounts
curl -s -o /dev/null -w "GET /accounts/1: %{http_code}\n" http://localhost:8081/api/accounts/1
curl -s -o /dev/null -w "GET /accounts/999 (no existe): %{http_code}\n" http://localhost:8081/api/accounts/999

echo "=== transactions-service ==="
curl -s -o /dev/null -w "GET /transactions/account/1: %{http_code}\n" http://localhost:8082/api/transactions/account/1
```

## Validación y Pruebas

### Criterios de Éxito

- [ ] `docker compose ps` muestra `postgres-accounts` y `postgres-transactions` con estado `Up (healthy)`
- [ ] `accounts-service` arranca en el puerto 8081 y `/actuator/health` retorna `{"status":"UP"}`
- [ ] `transactions-service` arranca en el puerto 8082 y `/actuator/health` retorna `{"status":"UP"}`
- [ ] `GET /api/accounts` retorna las 4 cuentas de prueba con HTTP 200
- [ ] `GET /api/accounts/999` retorna HTTP 404 (manejo de error reactivo con `onErrorResume`)
- [ ] `POST /api/transactions` con `accountId: 1` retorna HTTP 201 y crea la transacción
- [ ] `POST /api/transactions` con `accountId: 9999` retorna HTTP 422 (cuenta inválida)
- [ ] Los logs de `transactions-service` muestran las llamadas `[WebClient] →` y `[WebClient] ←`
- [ ] Las respuestas HTTP incluyen el header `X-Trace-Id`
- [ ] El proyecto completo compila con `mvn clean compile` sin errores

### Procedimiento de Pruebas

1. **Prueba de compilación completa:**
   ```bash
   cd banking-system
   mvn clean compile -pl common-lib,accounts-service,transactions-service
   ```
   **Resultado esperado:** `BUILD SUCCESS` para todos los módulos

2. **Prueba de health checks:**
   ```bash
   curl -s http://localhost:8081/actuator/health
   curl -s http://localhost:8082/actuator/health
   ```
   **Resultado esperado:** Ambos retornan `{"status":"UP"}`

3. **Prueba del flujo completo de transacción:**
   ```bash
   # Paso 1: Verifica que la cuenta 2 existe
   curl -s http://localhost:8081/api/accounts/2

   # Paso 2: Crea una transacción para esa cuenta
   curl -s -X POST http://localhost:8082/api/transactions \
     -H "Content-Type: application/json" \
     -d '{"accountId":2,"transactionType":"WITHDRAWAL","amount":100.00,"description":"Prueba completa"}'

   # Paso 3: Verifica que la transacción fue creada
   curl -s http://localhost:8082/api/transactions/account/2
   ```
   **Resultado esperado:** La transacción aparece en el listado de la cuenta 2

4. **Prueba de manejo de errores reactivo:**
   ```bash
   # Cuenta inexistente → debe retornar 422, no 500
   curl -v -X POST http://localhost:8082/api/transactions \
     -H "Content-Type: application/json" \
     -d '{"accountId":99999,"transactionType":"DEPOSIT","amount":50.00}' 2>&1 | grep "< HTTP"
   ```
   **Resultado esperado:** `< HTTP/1.1 422 Unprocessable Entity`

5. **Prueba de trazabilidad:**
   ```bash
   # El header X-Trace-Id debe aparecer en la respuesta
   curl -s -D - http://localhost:8081/api/accounts | grep -i "x-trace"
   ```
   **Resultado esperado:** `X-Trace-Id: [valor-uuid-corto]`

## Solución de Problemas

### Problema 1: Error de Conexión R2DBC al Arrancar el Servicio

**Síntomas:**
- El servicio arranca pero el health check muestra `{"status":"DOWN"}`
- Logs muestran: `Unable to connect to localhost:5432` o `Connection refused`
- Error: `io.r2dbc.postgresql.client.ReactorNettyClient$BackendMessageSubscriber`

**Causa:**
Los contenedores de PostgreSQL no están corriendo o aún no han terminado de inicializarse. R2DBC intenta conectarse al arrancar la aplicación.

**Solución:**
```bash
# Verifica el estado de los contenedores
docker compose ps

# Si no están corriendo, levántalos
docker compose up -d postgres-accounts postgres-transactions

# Espera a que estén healthy
docker compose ps

# Verifica conectividad directa
docker exec postgres-accounts pg_isready -U accounts_user -d accounts_db

# Reinicia el servicio Spring Boot después de confirmar que PostgreSQL está listo
# Ctrl+C en la terminal del servicio y vuelve a ejecutarlo
java -jar accounts-service/target/accounts-service-1.0.0.jar
```

---

### Problema 2: Error de Compilación "Cannot find symbol: ReactiveCrudRepository"

**Síntomas:**
- `mvn compile` falla con: `error: cannot find symbol - class ReactiveCrudRepository`
- O: `package org.springframework.data.repository.reactive does not exist`

**Causa:**
La dependencia `spring-boot-starter-data-r2dbc` no está en el `pom.xml` del módulo, o hay un error de tipografía en el `groupId`/`artifactId`.

**Solución:**
```bash
# Verifica que la dependencia está en el pom.xml del servicio
grep -A 4 "r2dbc" accounts-service/pom.xml

# Debe mostrar:
# <dependency>
#   <groupId>org.springframework.boot</groupId>
#   <artifactId>spring-boot-starter-data-r2dbc</artifactId>
# </dependency>

# Si falta, agrega la dependencia y vuelve a compilar
mvn clean compile -pl accounts-service
```

---

### Problema 3: transactions-service Retorna 500 en lugar de 422 para Cuenta Inexistente

**Síntomas:**
- `POST /api/transactions` con `accountId: 9999` retorna HTTP 500
- Logs muestran: `InvalidAccountException` pero no es capturada

**Causa:**
El `onErrorResume` en el controlador no está capturando `InvalidAccountException` porque la excepción se lanza dentro del `flatMap` de `TransactionService`, pero el tipo en el `onErrorResume` del controlador no coincide exactamente.

**Solución:**
```bash
# Verifica que el import de InvalidAccountException es correcto en el controlador
grep "import.*InvalidAccountException" \
  transactions-service/src/main/java/com/banking/transactions/controller/TransactionController.java

# Debe mostrar:
# import com.banking.transactions.exception.InvalidAccountException;

# Verifica que el onErrorResume usa el tipo correcto
grep "onErrorResume" \
  transactions-service/src/main/java/com/banking/transactions/controller/TransactionController.java
```

Si el import es correcto pero el problema persiste, verifica que `InvalidAccountException` extiende `RuntimeException` (no `Exception`):

```bash
grep "extends" \
  transactions-service/src/main/java/com/banking/transactions/exception/InvalidAccountException.java
# Debe mostrar: public class InvalidAccountException extends RuntimeException
```

---

### Problema 4: WebClient No Puede Conectarse a accounts-service

**Síntomas:**
- `POST /api/transactions` retorna HTTP 422 incluso para cuentas que existen
- Logs de `transactions-service` muestran: `Error al verificar cuenta X: Connection refused`
- El `AccountsClient` siempre retorna `false`

**Causa:**
La URL base de `accounts-service` en `application.properties` de `transactions-service` es incorrecta, o `accounts-service` no está corriendo en el puerto 8081.

**Solución:**
```bash
# Verifica que accounts-service está corriendo en 8081
curl -s http://localhost:8081/actuator/health

# Verifica la configuración en application.properties
grep "accounts.base-url" transactions-service/src/main/resources/application.properties
# Debe mostrar: services.accounts.base-url=http://localhost:8081

# Si accounts-service no está corriendo, inícialo primero
java -jar accounts-service/target/accounts-service-1.0.0.jar &
sleep 15
# Luego reinicia transactions-service
java -jar transactions-service/target/transactions-service-1.0.0.jar
```

---

### Problema 5: Error "Table 'accounts' doesn't exist" al Hacer Requests

**Síntomas:**
- Los servicios arrancan correctamente pero las consultas fallan
- Logs muestran: `io.r2dbc.postgresql.ExceptionFactory: relation "accounts" does not exist`

**Causa:**
El script SQL de inicialización no se ejecutó al crear el contenedor. Esto ocurre si el contenedor ya existía previamente sin el volumen de inicialización.

**Solución:**
```bash
# Elimina los contenedores y volúmenes existentes
docker compose down -v

# Vuelve a crear los contenedores (ejecutará init.sql automáticamente)
docker compose up -d postgres-accounts postgres-transactions

# Espera a que estén healthy
sleep 15
docker compose ps

# Verifica que las tablas existen
docker exec postgres-accounts psql -U accounts_user -d accounts_db \
  -c "\dt"
# Debe mostrar la tabla 'accounts'
```

## Limpieza

Cuando termines el laboratorio, puedes detener los servicios y contenedores:

```bash
# Detener los servicios Spring Boot
# Presiona Ctrl+C en cada terminal donde están corriendo

# Detener los contenedores Docker (sin eliminar datos)
docker compose stop postgres-accounts postgres-transactions

# Para eliminar completamente los contenedores y volúmenes (BORRA DATOS)
# Solo si quieres empezar desde cero en el próximo lab
# docker compose down -v
```

> ⚠️ **Advertencia:** No ejecutes `docker compose down -v` si quieres conservar los datos de prueba para el Laboratorio 3. Los volúmenes de Docker persisten los datos entre sesiones de trabajo. Solo usa `docker compose stop` para pausar los contenedores.

> ⚠️ **Seguridad:** Las credenciales de PostgreSQL (`accounts_pass`, `transactions_pass`) usadas en este laboratorio son para entorno de desarrollo local únicamente. En producción, gestiona las credenciales con variables de entorno o un sistema de secretos (HashiCorp Vault, AWS Secrets Manager). Nunca las incluyas en el código fuente ni en repositorios Git.

```bash
# Verificar que todo está detenido correctamente
docker compose ps
# Debe mostrar todos los servicios como "stopped" o no listarlos
```

## Resumen

### Lo que Lograste

- **Infraestructura reactiva:** Configuraste dos instancias de PostgreSQL con Docker Compose, incluyendo scripts de inicialización automática con esquemas y datos de prueba
- **Persistencia no-bloqueante:** Implementaste entidades y repositorios con Spring Data R2DBC, comprendiendo la diferencia clave con JPA: los repositorios retornan `Mono<T>` y `Flux<T>` en lugar de objetos directos
- **Endpoints reactivos CRUD:** Creaste controladores WebFlux completos con `@RestController` que retornan `Mono<ResponseEntity<T>>` y `Flux<T>`, manejando casos de éxito y error sin bloquear hilos
- **WebClient configurado:** Implementaste un bean `WebClient` con Reactor Netty, timeouts de conexión (5s) y lectura (10s), y filtros de logging de request/response
- **Comunicación inter-servicios reactiva:** `transactions-service` valida la existencia de una cuenta en `accounts-service` usando `WebClient`, encadenando la operación con `flatMap` para mantener el pipeline reactivo intacto
- **Manejo de errores reactivo:** Usaste `onErrorResume` para capturar excepciones específicas dentro del pipeline reactivo y transformarlas en respuestas HTTP apropiadas (404, 422), sin usar try-catch bloqueante
- **Trazabilidad:** Implementaste un `WebFilter` que genera y propaga un `traceId` único por request, visible en headers de respuesta y logs

### Conceptos Clave Aprendidos

- **`flatMap` vs `map`:** `map` transforma síncronamente (`T → R`), `flatMap` encadena operaciones asíncronas (`T → Mono<R>`). Usar `flatMap` cuando la transformación retorna `Mono` o `Flux`
- **`switchIfEmpty`:** Convierte un `Mono` vacío en un error semántico del dominio, permitiendo manejar "no encontrado" de forma expresiva en el pipeline reactivo
- **`onErrorResume`:** Captura un error específico en el pipeline y retorna un valor alternativo, manteniendo el stream reactivo vivo sin propagar la excepción
- **R2DBC ≠ JPA:** R2DBC es completamente no-bloqueante; los repositorios retornan `Mono`/`Flux`, no hay `EntityManager`, no hay lazy loading ni sesiones de Hibernate
- **WebFilter en WebFlux:** Equivalente reactivo de `javax.servlet.Filter`, implementa `WebFilter` y retorna `Mono<Void>` para mantener el pipeline no-bloqueante

### Próximos Pasos

- En el **Laboratorio 3**, añadirás seguridad JWT con Spring Security WebFlux: protegerás los endpoints de ambos servicios y configurarás el API Gateway para validar tokens antes de enrutar requests
- Explorarás cómo propagar el token JWT en las llamadas de `WebClient` entre servicios usando `ExchangeFilterFunction`
- Implementarás patrones de resiliencia (retry, circuit breaker) con Resilience4j Reactor sobre los `WebClient` configurados en este laboratorio

## Recursos Adicionales

- [Spring Data R2DBC Reference Documentation](https://docs.spring.io/spring-data/relational/reference/r2dbc.html) - Guía oficial de R2DBC con Spring Data, incluyendo repositorios reactivos y consultas derivadas
- [Spring WebFlux WebClient Documentation](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html) - Referencia completa de WebClient: configuración, filtros, manejo de errores y timeouts
- [Project Reactor Reference Guide - Operators](https://projectreactor.io/docs/core/release/reference/#which-operator) - Guía de referencia para elegir el operador correcto: cuándo usar `flatMap`, `switchIfEmpty`, `onErrorResume`
- [R2DBC PostgreSQL Driver](https://github.com/pgjdbc/r2dbc-postgresql) - Repositorio del driver R2DBC para PostgreSQL con ejemplos de configuración
- [Reactor Netty HTTP Client](https://projectreactor.io/docs/netty/release/reference/index.html#http-client) - Documentación de Reactor Netty para configuración avanzada de timeouts y conexiones en WebClient
