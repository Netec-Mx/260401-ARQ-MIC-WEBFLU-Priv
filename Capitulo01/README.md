# Diseño de Arquitectura Base (Diagrama + Proyecto)

## Metadatos

| Propiedad | Valor |
|-----------|-------|
| **Duración** | 95 minutos |
| **Complejidad** | Intermedio |
| **Nivel Bloom** | Crear |

## Descripción General

En este laboratorio diseñarás la arquitectura base de un sistema bancario reactivo de microservicios, comenzando con un diagrama arquitectónico detallado que identifica los bounded contexts, los canales de comunicación y los componentes de infraestructura. Posteriormente materializarás ese diseño creando un proyecto multi-módulo Maven con Spring Boot 3.x y WebFlux, configurando un API Gateway con Spring Cloud Gateway y un Config Server centralizado, y levantando los servicios de infraestructura con Docker Compose.

Este laboratorio establece el andamiaje completo del sistema que evolucionará a lo largo de todo el curso. Comprender la arquitectura desde el inicio es fundamental para tomar decisiones de diseño correctas y evitar el antipatrón del monolito distribuido.

## Objetivos de Aprendizaje

Al completar este laboratorio, serás capaz de:

- [ ] Diseñar un diagrama de arquitectura completo que represente un sistema de microservicios reactivos con al menos tres servicios de dominio, un API Gateway y un Config Server
- [ ] Identificar y documentar las fronteras de dominio (bounded contexts) de cada microservicio aplicando el principio de responsabilidad única
- [ ] Diferenciar visualmente en el diagrama los canales de comunicación síncrona (REST/WebClient) y reactiva (WebFlux) entre servicios
- [ ] Generar la estructura base de un proyecto multi-módulo Maven con Spring Boot 3.x y WebFlux configurado correctamente
- [ ] Configurar un API Gateway básico con Spring Cloud Gateway que enrute peticiones a los microservicios diseñados
- [ ] Implementar una estrategia de gestión de configuración externalizada usando Spring Cloud Config Server con perfiles por ambiente (dev, staging, prod)

## Prerrequisitos

### Conocimiento Requerido

- Conocimiento básico de Spring Boot: crear proyectos, configurar `application.properties`/`yml`, entender el ciclo de vida de beans
- Familiaridad con conceptos de programación reactiva: `Mono`, `Flux`, y el modelo publicador-suscriptor (Reactive Streams)
- Comprensión básica de REST y HTTP: verbos, códigos de estado, headers
- Manejo básico de Maven: estructura de `pom.xml`, dependencias, módulos
- Docker básico: ejecutar `docker run` y `docker compose up`, entender imágenes y contenedores
- Git básico: `init`, `add`, `commit`, estructura de un repositorio

### Acceso Requerido

- Acceso a Internet para descargar dependencias de Maven Central y Spring Initializr
- Permisos de administrador local para instalar software y ejecutar Docker Desktop
- Cuenta en GitHub o GitLab (opcional, para repositorio remoto)

## Entorno de Laboratorio

### Requisitos de Hardware

| Componente | Especificación |
|------------|----------------|
| Procesador | Intel Core i5 / AMD Ryzen 5 (8ª gen o superior, mínimo 4 núcleos físicos) |
| RAM | Mínimo 16 GB (recomendado 32 GB) |
| Almacenamiento | Mínimo 20 GB libres en SSD |
| Pantalla | Resolución mínima 1920x1080 |
| Red | Banda ancha estable (mínimo 10 Mbps) |

### Requisitos de Software

| Software | Versión | Propósito |
|----------|---------|-----------|
| JDK | 17 LTS o 21 LTS | Compilación y ejecución de microservicios Spring Boot |
| Apache Maven | 3.9.x | Gestión de dependencias y construcción multi-módulo |
| IntelliJ IDEA | 2023.3 o superior | IDE principal para desarrollo y navegación |
| Docker Desktop | 4.25 o superior | Contenerización de servicios de infraestructura |
| Docker Compose | 2.21 o superior | Orquestación de entorno multi-contenedor |
| Git | 2.40 o superior | Control de versiones del proyecto |
| curl | 7.x o superior | Pruebas rápidas de endpoints desde terminal |
| draw.io (web) | N/A (servicio web) | Creación del diagrama de arquitectura |

### Configuración Inicial

Verifica que todas las herramientas estén correctamente instaladas ejecutando los siguientes comandos en tu terminal:

```bash
# Verificar Java
java -version

# Verificar Maven
mvn -version

# Verificar Docker
docker --version
docker compose version

# Verificar Git
git --version

# Verificar curl
curl --version
```

Crea el directorio raíz del proyecto donde trabajarás durante todo el curso:

```bash
mkdir -p ~/banking-reactive-system
cd ~/banking-reactive-system
git init
```

## Instrucciones Paso a Paso

### Paso 1: Diseño del Diagrama de Arquitectura en draw.io

**Objetivo:** Crear un diagrama arquitectónico completo que represente el sistema bancario reactivo, identificando bounded contexts, servicios, bases de datos y patrones de comunicación antes de escribir una sola línea de código.

**Instrucciones:**

1. Abre tu navegador y navega a [https://app.diagrams.net](https://app.diagrams.net). Selecciona **"Decide later"** cuando pregunte dónde guardar el archivo.

2. Crea un nuevo diagrama en blanco. Ve a **File → New** y selecciona **Blank Diagram**.

3. Diseña el diagrama siguiendo esta estructura de capas (de arriba hacia abajo):

   **Capa 1 — Cliente:**
   - Añade un rectángulo etiquetado `Cliente / Postman` (representa el consumidor de la API)

   **Capa 2 — Edge Layer:**
   - Añade un rectángulo con fondo azul etiquetado `API Gateway` con el subtítulo `Spring Cloud Gateway :8080`
   - Añade un rectángulo con fondo verde etiquetado `Config Server` con el subtítulo `Spring Cloud Config :8888`

   **Capa 3 — Domain Services (Bounded Contexts):**
   - Rectángulo con fondo naranja: `Auth Service` / `Spring Security + JWT :8083`
   - Rectángulo con fondo naranja: `Accounts Service` / `Spring WebFlux + R2DBC :8081`
   - Rectángulo con fondo naranja: `Transactions Service` / `Spring WebFlux + R2DBC :8082`

   **Capa 4 — Data Layer:**
   - Cilindro (base de datos): `PostgreSQL Accounts :5432`
   - Cilindro (base de datos): `PostgreSQL Transactions :5433`

   **Capa 5 — Observability:**
   - Rectángulo con fondo gris: `Prometheus :9090`
   - Rectángulo con fondo gris: `Grafana :3000`

4. Añade las conexiones con los siguientes estilos diferenciados:

   - **Línea sólida gruesa (azul):** Comunicación síncrona REST/WebClient entre servicios
     - Cliente → API Gateway
     - API Gateway → Auth Service
     - API Gateway → Accounts Service
     - API Gateway → Transactions Service
     - Transactions Service → Accounts Service (WebClient reactivo)

   - **Línea punteada (verde):** Conexión al Config Server (configuración externalizada)
     - Config Server → API Gateway
     - Config Server → Auth Service
     - Config Server → Accounts Service
     - Config Server → Transactions Service

   - **Línea sólida delgada (gris):** Conexión a bases de datos
     - Accounts Service → PostgreSQL Accounts
     - Transactions Service → PostgreSQL Transactions

   - **Línea punteada (naranja):** Métricas/Observabilidad
     - Prometheus → API Gateway (scrape)
     - Prometheus → Accounts Service (scrape)
     - Prometheus → Transactions Service (scrape)
     - Grafana → Prometheus

5. Añade una leyenda en la esquina inferior izquierda explicando el significado de cada tipo de línea.

6. Añade etiquetas en los conectores principales:
   - Entre API Gateway y Accounts Service: `GET /api/accounts/**`
   - Entre API Gateway y Transactions Service: `POST /api/transactions/**`
   - Entre Transactions Service y Accounts Service: `WebClient (Reactive)`

7. Guarda el diagrama: **File → Save As → Device** con el nombre `architecture-diagram.drawio`.

**Salida Esperada:**

Un archivo `architecture-diagram.drawio` con un diagrama de 5 capas que muestra claramente:
- Los 3 bounded contexts (Auth, Accounts, Transactions)
- El API Gateway como punto de entrada único
- El Config Server centralizado
- Las bases de datos independientes por servicio (desacoplamiento de datos)
- Los diferentes tipos de comunicación diferenciados visualmente
- La capa de observabilidad (Prometheus + Grafana)

**Verificación:**

- El diagrama debe mostrar que **ningún servicio comparte base de datos** con otro (principio de desacoplamiento de datos)
- Debe existir una única entrada al sistema (API Gateway en el puerto 8080)
- Los bounded contexts deben estar claramente delimitados visualmente (color o borde)
- La leyenda debe explicar los tipos de comunicación

---

### Paso 2: Documentación de Bounded Contexts

**Objetivo:** Crear un documento `ARCHITECTURE.md` que formalice las decisiones de diseño del diagrama, identificando las responsabilidades y límites de cada servicio.

**Instrucciones:**

1. Desde el directorio raíz del proyecto, crea el archivo de documentación:

```bash
cd ~/banking-reactive-system
cat > ARCHITECTURE.md << 'EOF'
# Sistema Bancario Reactivo - Documentación de Arquitectura

## Asignación de Puertos

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| API Gateway | 8080 | Punto de entrada único del sistema |
| Auth Service | 8083 | Autenticación y emisión de tokens JWT |
| Accounts Service | 8081 | Gestión de cuentas bancarias |
| Transactions Service | 8082 | Registro de transacciones financieras |
| Config Server | 8888 | Configuración centralizada externalizada |
| PostgreSQL Accounts | 5432 | Base de datos del servicio de cuentas |
| PostgreSQL Transactions | 5433 | Base de datos del servicio de transacciones |
| Prometheus | 9090 | Recolección de métricas |
| Grafana | 3000 | Visualización de métricas |

## Bounded Contexts

### 1. Auth Service (Contexto: Autenticación y Autorización)
- **Responsabilidad única:** Autenticar usuarios y emitir/validar tokens JWT
- **Datos propios:** Usuarios, credenciales, roles
- **Expone:** POST /auth/login, POST /auth/validate
- **NO gestiona:** Información de cuentas ni transacciones

### 2. Accounts Service (Contexto: Gestión de Cuentas)
- **Responsabilidad única:** Ciclo de vida de cuentas bancarias
- **Datos propios:** Cuentas, saldos, titulares
- **Expone:** GET /accounts, POST /accounts, GET /accounts/{id}, PUT /accounts/{id}
- **NO gestiona:** Transacciones ni autenticación

### 3. Transactions Service (Contexto: Transacciones Financieras)
- **Responsabilidad única:** Registrar y consultar movimientos financieros
- **Datos propios:** Transacciones, tipos de operación, montos
- **Expone:** POST /transactions, GET /transactions/{accountId}
- **Consume:** Accounts Service (validación de cuenta via WebClient reactivo)
- **NO gestiona:** Saldos directamente (consulta a Accounts Service)

## Principios de Diseño Aplicados

1. **Desacoplamiento de datos:** Cada servicio tiene su propia base de datos
2. **Desacoplamiento de despliegue:** Cada servicio puede desplegarse independientemente
3. **Single Responsibility:** Cada servicio tiene una única razón para cambiar
4. **API-First:** Los servicios se comunican SOLO a través de sus APIs públicas

## Antipatrón Evitado

❌ NO se comparte base de datos entre servicios (monolito distribuido)
❌ NO se accede directamente a tablas de otro servicio
✅ Toda comunicación inter-servicio pasa por APIs REST reactivas
EOF
```

2. Copia el archivo del diagrama al directorio del proyecto:

```bash
mkdir -p ~/banking-reactive-system/docs
cp ~/Downloads/architecture-diagram.drawio ~/banking-reactive-system/docs/
```

> **Nota:** Si descargaste el diagrama en una ubicación diferente, ajusta la ruta del comando `cp` según corresponda.

3. Realiza el primer commit del proyecto:

```bash
cd ~/banking-reactive-system
git add ARCHITECTURE.md docs/architecture-diagram.drawio
git commit -m "feat: add architecture diagram and bounded context documentation"
```

**Salida Esperada:**

```
[main (root-commit) abc1234] feat: add architecture diagram and bounded context documentation
 2 files changed, 45 insertions(+)
 create mode 100644 ARCHITECTURE.md
 create mode 100644 docs/architecture-diagram.drawio
```

**Verificación:**

```bash
# Verificar que los archivos están en el repositorio
git log --oneline
git status
ls -la docs/
```

- El comando `git log` debe mostrar el commit inicial
- `git status` debe mostrar "nothing to commit, working tree clean"

---

### Paso 3: Creación del Proyecto Multi-Módulo Maven

**Objetivo:** Establecer la estructura del proyecto padre Maven que contendrá todos los microservicios como módulos hijos, con gestión centralizada de versiones mediante Spring Boot BOM y Spring Cloud BOM.

**Instrucciones:**

1. Crea el `pom.xml` raíz del proyecto multi-módulo:

```bash
cd ~/banking-reactive-system
cat > pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.banking</groupId>
    <artifactId>banking-reactive-system</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>
    <name>Banking Reactive System - Parent</name>
    <description>Sistema bancario reactivo con Spring WebFlux y microservicios</description>

    <modules>
        <module>config-server</module>
        <module>api-gateway</module>
        <module>auth-service</module>
        <module>accounts-service</module>
        <module>transactions-service</module>
    </modules>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.2.5</spring-boot.version>
        <spring-cloud.version>2023.0.1</spring-cloud.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <!-- Spring Boot BOM -->
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <!-- Spring Cloud BOM -->
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
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
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.11.0</version>
                    <configuration>
                        <source>${java.version}</source>
                        <target>${java.version}</target>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
    </build>
</project>
EOF
```

2. Crea los directorios base para cada módulo:

```bash
mkdir -p config-server/src/main/java/com/banking/configserver
mkdir -p config-server/src/main/resources
mkdir -p api-gateway/src/main/java/com/banking/gateway
mkdir -p api-gateway/src/main/resources
mkdir -p auth-service/src/main/java/com/banking/auth
mkdir -p auth-service/src/main/resources
mkdir -p accounts-service/src/main/java/com/banking/accounts
mkdir -p accounts-service/src/main/resources
mkdir -p transactions-service/src/main/java/com/banking/transactions
mkdir -p transactions-service/src/main/resources
```

3. Verifica la estructura creada:

```bash
find ~/banking-reactive-system -type d | head -30
```

**Salida Esperada:**

```
/home/usuario/banking-reactive-system
/home/usuario/banking-reactive-system/config-server
/home/usuario/banking-reactive-system/config-server/src
/home/usuario/banking-reactive-system/config-server/src/main
/home/usuario/banking-reactive-system/config-server/src/main/java
/home/usuario/banking-reactive-system/config-server/src/main/java/com/banking/configserver
/home/usuario/banking-reactive-system/config-server/src/main/resources
/home/usuario/banking-reactive-system/api-gateway
...
```

**Verificación:**

```bash
# Verificar que el pom.xml raíz es válido
mvn validate -f pom.xml
```

El comando debe terminar con `BUILD SUCCESS` sin errores.

---

### Paso 4: Configuración del Config Server

**Objetivo:** Implementar el Config Server de Spring Cloud que centralizará la configuración de todos los microservicios, soportando perfiles por ambiente (dev, prod).

**Instrucciones:**

1. Crea el `pom.xml` del módulo `config-server`:

```bash
cat > ~/banking-reactive-system/config-server/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.banking</groupId>
        <artifactId>banking-reactive-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>config-server</artifactId>
    <name>Config Server</name>
    <description>Servidor de configuración centralizada Spring Cloud Config</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-config-server</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

2. Crea la clase principal del Config Server:

```bash
cat > ~/banking-reactive-system/config-server/src/main/java/com/banking/configserver/ConfigServerApplication.java << 'EOF'
package com.banking.configserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Config Server - Servidor de configuración centralizada.
 *
 * Este servicio provee configuración externalizada a todos los microservicios
 * del sistema bancario reactivo. Soporta perfiles por ambiente (dev, prod).
 *
 * Puerto: 8888
 */
@SpringBootApplication
@EnableConfigServer
public class ConfigServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ConfigServerApplication.class, args);
    }
}
EOF
```

3. Crea la configuración principal del Config Server:

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/application.yml << 'EOF'
server:
  port: 8888

spring:
  application:
    name: config-server
  profiles:
    active: native
  cloud:
    config:
      server:
        native:
          search-locations: classpath:/config-repo
        default-label: main

management:
  endpoints:
    web:
      exposure:
        include: health,info,env
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.springframework.cloud.config: DEBUG
EOF
```

4. Crea el directorio del repositorio de configuraciones nativas y los archivos de configuración por servicio:

```bash
mkdir -p ~/banking-reactive-system/config-server/src/main/resources/config-repo
```

5. Crea la configuración del API Gateway para el Config Server:

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/api-gateway.yml << 'EOF'
# Configuración del API Gateway - Perfil por defecto (dev)
server:
  port: 8080

spring:
  application:
    name: api-gateway

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
EOF
```

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/api-gateway-prod.yml << 'EOF'
# Configuración del API Gateway - Perfil producción
logging:
  level:
    org.springframework.cloud.gateway: WARN
    root: WARN
EOF
```

6. Crea la configuración del Accounts Service:

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/accounts-service.yml << 'EOF'
# Configuración del Accounts Service - Perfil por defecto (dev)
server:
  port: 8081

spring:
  application:
    name: accounts-service
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/accounts_db
    username: accounts_user
    password: accounts_pass
  sql:
    init:
      mode: always

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.banking.accounts: DEBUG
EOF
```

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/accounts-service-prod.yml << 'EOF'
# Configuración del Accounts Service - Perfil producción
spring:
  r2dbc:
    url: r2dbc:postgresql://postgres-accounts:5432/accounts_db

logging:
  level:
    com.banking.accounts: INFO
    root: WARN
EOF
```

7. Crea la configuración del Transactions Service:

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/transactions-service.yml << 'EOF'
# Configuración del Transactions Service - Perfil por defecto (dev)
server:
  port: 8082

spring:
  application:
    name: transactions-service
  r2dbc:
    url: r2dbc:postgresql://localhost:5433/transactions_db
    username: transactions_user
    password: transactions_pass

services:
  accounts:
    base-url: http://localhost:8081

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.banking.transactions: DEBUG
EOF
```

```bash
cat > ~/banking-reactive-system/config-server/src/main/resources/config-repo/transactions-service-prod.yml << 'EOF'
# Configuración del Transactions Service - Perfil producción
spring:
  r2dbc:
    url: r2dbc:postgresql://postgres-transactions:5433/transactions_db

services:
  accounts:
    base-url: http://accounts-service:8081

logging:
  level:
    com.banking.transactions: INFO
    root: WARN
EOF
```

**Salida Esperada:**

La estructura de archivos debe verse así:

```
config-server/
├── pom.xml
└── src/main/
    ├── java/com/banking/configserver/
    │   └── ConfigServerApplication.java
    └── resources/
        ├── application.yml
        └── config-repo/
            ├── api-gateway.yml
            ├── api-gateway-prod.yml
            ├── accounts-service.yml
            ├── accounts-service-prod.yml
            ├── transactions-service.yml
            └── transactions-service-prod.yml
```

**Verificación:**

```bash
# Verificar estructura del config-server
find ~/banking-reactive-system/config-server -type f | sort

# Compilar solo el módulo config-server
cd ~/banking-reactive-system
mvn compile -pl config-server
```

El comando `mvn compile` debe terminar con `BUILD SUCCESS`.

---

### Paso 5: Configuración del API Gateway

**Objetivo:** Implementar el API Gateway con Spring Cloud Gateway que actúe como punto de entrada único del sistema, enrutando las peticiones a los microservicios correspondientes.

**Instrucciones:**

1. Crea el `pom.xml` del módulo `api-gateway`:

```bash
cat > ~/banking-reactive-system/api-gateway/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.banking</groupId>
        <artifactId>banking-reactive-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>api-gateway</artifactId>
    <name>API Gateway</name>
    <description>Punto de entrada único del sistema bancario reactivo</description>

    <dependencies>
        <!-- Spring Cloud Gateway (incluye WebFlux internamente) -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>
        <!-- Spring Cloud Config Client -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-config</artifactId>
        </dependency>
        <!-- Actuator para health checks y métricas -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <!-- Micrometer Prometheus para métricas -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

2. Crea la clase principal del API Gateway:

```bash
cat > ~/banking-reactive-system/api-gateway/src/main/java/com/banking/gateway/ApiGatewayApplication.java << 'EOF'
package com.banking.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway - Punto de entrada único del sistema bancario reactivo.
 *
 * Responsabilidades:
 * - Enrutamiento de peticiones a microservicios de dominio
 * - Propagación del header X-Trace-Id para trazabilidad
 * - (Laboratorios posteriores) Validación de tokens JWT
 * - (Laboratorios posteriores) Rate limiting y circuit breaker
 *
 * Puerto: 8080
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
EOF
```

3. Crea el filtro global para propagación del header de trazabilidad `X-Trace-Id`:

```bash
cat > ~/banking-reactive-system/api-gateway/src/main/java/com/banking/gateway/filter/TraceIdGlobalFilter.java << 'EOF'
package com.banking.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Filtro global que genera y propaga un X-Trace-Id único por request.
 *
 * Este header permite correlacionar logs entre todos los microservicios
 * para un mismo request entrante. Aunque no integra Zipkin/Jaeger en este
 * curso, establece la base para trazabilidad distribuida.
 */
@Component
public class TraceIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(TraceIdGlobalFilter.class);
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Reutilizar el trace ID si ya viene en el request, o generar uno nuevo
        String traceId = exchange.getRequest().getHeaders()
                .getFirst(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }

        final String finalTraceId = traceId;
        log.info("[TraceId: {}] Incoming request: {} {}",
                finalTraceId,
                exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath());

        // Propagar el trace ID al microservicio destino
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .header(TRACE_ID_HEADER, finalTraceId)
                .build();

        // Añadir el trace ID a la respuesta también
        ServerWebExchange mutatedExchange = exchange.mutate()
                .request(mutatedRequest)
                .build();

        mutatedExchange.getResponse().getHeaders().add(TRACE_ID_HEADER, finalTraceId);

        return chain.filter(mutatedExchange);
    }

    @Override
    public int getOrder() {
        // Ejecutar antes que otros filtros (orden más bajo = mayor prioridad)
        return -1;
    }
}
EOF
```

4. Crea la configuración del API Gateway con las rutas hacia los microservicios:

```bash
cat > ~/banking-reactive-system/api-gateway/src/main/resources/application.yml << 'EOF'
server:
  port: 8080

spring:
  application:
    name: api-gateway
  config:
    import: "optional:configserver:http://localhost:8888"
  cloud:
    gateway:
      routes:
        # Ruta hacia Auth Service
        - id: auth-service-route
          uri: http://localhost:8083
          predicates:
            - Path=/api/auth/**
          filters:
            - RewritePath=/api/auth/(?<segment>.*), /auth/${segment}
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10
                redis-rate-limiter.burstCapacity: 20

        # Ruta hacia Accounts Service
        - id: accounts-service-route
          uri: http://localhost:8081
          predicates:
            - Path=/api/accounts/**
          filters:
            - RewritePath=/api/accounts/(?<segment>.*), /accounts/${segment}

        # Ruta hacia Transactions Service
        - id: transactions-service-route
          uri: http://localhost:8082
          predicates:
            - Path=/api/transactions/**
          filters:
            - RewritePath=/api/transactions/(?<segment>.*), /transactions/${segment}

      # Configuración global del gateway
      default-filters:
        - AddResponseHeader=X-Gateway-Version, 1.0.0
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: "*"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,gateway
  endpoint:
    health:
      show-details: always
    gateway:
      enabled: true

logging:
  level:
    org.springframework.cloud.gateway: DEBUG
    com.banking.gateway: DEBUG
EOF
```

5. Crea el perfil de configuración para Docker:

```bash
cat > ~/banking-reactive-system/api-gateway/src/main/resources/application-docker.yml << 'EOF'
# Perfil Docker: los servicios se comunican por nombre de contenedor
spring:
  config:
    import: "optional:configserver:http://config-server:8888"
  cloud:
    gateway:
      routes:
        - id: auth-service-route
          uri: http://auth-service:8083
          predicates:
            - Path=/api/auth/**
          filters:
            - RewritePath=/api/auth/(?<segment>.*), /auth/${segment}

        - id: accounts-service-route
          uri: http://accounts-service:8081
          predicates:
            - Path=/api/accounts/**
          filters:
            - RewritePath=/api/accounts/(?<segment>.*), /accounts/${segment}

        - id: transactions-service-route
          uri: http://transactions-service:8082
          predicates:
            - Path=/api/transactions/**
          filters:
            - RewritePath=/api/transactions/(?<segment>.*), /transactions/${segment}

logging:
  level:
    org.springframework.cloud.gateway: WARN
    com.banking.gateway: INFO
EOF
```

**Salida Esperada:**

```
api-gateway/
├── pom.xml
└── src/main/
    ├── java/com/banking/gateway/
    │   ├── ApiGatewayApplication.java
    │   └── filter/
    │       └── TraceIdGlobalFilter.java
    └── resources/
        ├── application.yml
        └── application-docker.yml
```

**Verificación:**

```bash
cd ~/banking-reactive-system
mvn compile -pl api-gateway
```

---

### Paso 6: Creación de los Esqueletos de los Microservicios de Dominio

**Objetivo:** Generar los proyectos base para `auth-service`, `accounts-service` y `transactions-service` con Spring WebFlux configurado, incluyendo un endpoint de health check básico en cada uno.

**Instrucciones:**

1. Crea el `pom.xml` del `auth-service`:

```bash
cat > ~/banking-reactive-system/auth-service/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.banking</groupId>
        <artifactId>banking-reactive-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>auth-service</artifactId>
    <name>Auth Service</name>
    <description>Servicio de autenticación y autorización JWT</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

2. Crea la clase principal del `auth-service`:

```bash
cat > ~/banking-reactive-system/auth-service/src/main/java/com/banking/auth/AuthServiceApplication.java << 'EOF'
package com.banking.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Auth Service - Servicio de autenticación y autorización.
 *
 * Bounded Context: Autenticación y Autorización
 * Responsabilidad: Emitir y validar tokens JWT para el sistema bancario.
 *
 * NOTA: La implementación completa de JWT se realizará en el Laboratorio 3.
 * Este esqueleto establece la estructura base del servicio.
 *
 * Puerto: 8083
 */
@SpringBootApplication
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
EOF
```

3. Crea un controlador básico de health check reactivo para `auth-service`:

```bash
cat > ~/banking-reactive-system/auth-service/src/main/java/com/banking/auth/controller/AuthController.java << 'EOF'
package com.banking.auth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controlador base del Auth Service.
 *
 * NOTA: Este es un esqueleto inicial. La implementación completa de
 * autenticación JWT se desarrollará en el Laboratorio 3.
 * Los endpoints de este laboratorio sirven para verificar que el
 * servicio arranca correctamente y es enrutable desde el API Gateway.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    /**
     * Endpoint de estado del servicio.
     * Utiliza Mono<ResponseEntity> para mantener el modelo reactivo no bloqueante.
     */
    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, String>>> getStatus() {
        return Mono.just(
            ResponseEntity.ok(Map.of(
                "service", "auth-service",
                "status", "UP",
                "message", "Auth Service operativo - JWT implementation pending (Lab 3)"
            ))
        );
    }
}
EOF
```

4. Crea la configuración del `auth-service`:

```bash
cat > ~/banking-reactive-system/auth-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8083

spring:
  application:
    name: auth-service
  config:
    import: "optional:configserver:http://localhost:8888"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.banking.auth: DEBUG
EOF
```

5. Crea el `pom.xml` del `accounts-service`:

```bash
cat > ~/banking-reactive-system/accounts-service/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.banking</groupId>
        <artifactId>banking-reactive-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>accounts-service</artifactId>
    <name>Accounts Service</name>
    <description>Servicio de gestión de cuentas bancarias reactivo</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <!-- R2DBC para persistencia reactiva (se usará en Lab 2) -->
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
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

6. Crea la clase principal del `accounts-service`:

```bash
cat > ~/banking-reactive-system/accounts-service/src/main/java/com/banking/accounts/AccountsServiceApplication.java << 'EOF'
package com.banking.accounts;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Accounts Service - Servicio de gestión de cuentas bancarias.
 *
 * Bounded Context: Gestión de Cuentas
 * Responsabilidad: Crear, consultar y actualizar cuentas bancarias.
 *
 * IMPORTANTE - Principio de Desacoplamiento:
 * Este servicio es el ÚNICO dueño de los datos de cuentas.
 * Ningún otro servicio accede directamente a su base de datos.
 * El Transactions Service consulta datos de cuentas SOLO a través
 * de la API REST de este servicio (via WebClient reactivo).
 *
 * Puerto: 8081
 */
@SpringBootApplication
public class AccountsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AccountsServiceApplication.class, args);
    }
}
EOF
```

7. Crea un controlador básico para `accounts-service`:

```bash
cat > ~/banking-reactive-system/accounts-service/src/main/java/com/banking/accounts/controller/AccountsController.java << 'EOF'
package com.banking.accounts.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controlador base del Accounts Service.
 *
 * NOTA: Este es un esqueleto inicial. La implementación completa con R2DBC
 * y lógica de negocio se desarrollará en el Laboratorio 2.
 */
@RestController
@RequestMapping("/accounts")
public class AccountsController {

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, String>>> getStatus() {
        return Mono.just(
            ResponseEntity.ok(Map.of(
                "service", "accounts-service",
                "status", "UP",
                "message", "Accounts Service operativo - R2DBC implementation pending (Lab 2)"
            ))
        );
    }
}
EOF
```

8. Crea la configuración del `accounts-service`:

```bash
cat > ~/banking-reactive-system/accounts-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8081

spring:
  application:
    name: accounts-service
  config:
    import: "optional:configserver:http://localhost:8888"
  # R2DBC se configurará en Lab 2. Por ahora se deshabilita auto-config
  # para que el servicio arranque sin base de datos.
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
      - org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.banking.accounts: DEBUG
EOF
```

9. Crea el `pom.xml` del `transactions-service`:

```bash
cat > ~/banking-reactive-system/transactions-service/pom.xml << 'EOF'
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.banking</groupId>
        <artifactId>banking-reactive-system</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>transactions-service</artifactId>
    <name>Transactions Service</name>
    <description>Servicio de registro de transacciones financieras reactivo</description>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
        <!-- R2DBC para persistencia reactiva (se usará en Lab 2) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-r2dbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>r2dbc-postgresql</artifactId>
        </dependency>
        <!-- WebClient para comunicación reactiva con Accounts Service -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
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
            </plugin>
        </plugins>
    </build>
</project>
EOF
```

10. Crea la clase principal del `transactions-service`:

```bash
cat > ~/banking-reactive-system/transactions-service/src/main/java/com/banking/transactions/TransactionsServiceApplication.java << 'EOF'
package com.banking.transactions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Transactions Service - Servicio de transacciones financieras.
 *
 * Bounded Context: Transacciones Financieras
 * Responsabilidad: Registrar depósitos, retiros y transferencias.
 *
 * IMPORTANTE - Comunicación Inter-Servicio:
 * Este servicio consulta al Accounts Service para validar cuentas.
 * La comunicación se realiza EXCLUSIVAMENTE via WebClient reactivo,
 * NUNCA accediendo directamente a la base de datos de Accounts.
 * Este es el principio de desacoplamiento de datos en acción.
 *
 * Puerto: 8082
 */
@SpringBootApplication
public class TransactionsServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionsServiceApplication.class, args);
    }
}
EOF
```

11. Crea un controlador básico para `transactions-service`:

```bash
cat > ~/banking-reactive-system/transactions-service/src/main/java/com/banking/transactions/controller/TransactionsController.java << 'EOF'
package com.banking.transactions.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controlador base del Transactions Service.
 *
 * NOTA: Este es un esqueleto inicial. La implementación completa con R2DBC
 * y WebClient reactivo hacia Accounts Service se desarrollará en Lab 2.
 */
@RestController
@RequestMapping("/transactions")
public class TransactionsController {

    @GetMapping("/status")
    public Mono<ResponseEntity<Map<String, String>>> getStatus() {
        return Mono.just(
            ResponseEntity.ok(Map.of(
                "service", "transactions-service",
                "status", "UP",
                "message", "Transactions Service operativo - R2DBC + WebClient pending (Lab 2)"
            ))
        );
    }
}
EOF
```

12. Crea la configuración del `transactions-service`:

```bash
cat > ~/banking-reactive-system/transactions-service/src/main/resources/application.yml << 'EOF'
server:
  port: 8082

spring:
  application:
    name: transactions-service
  config:
    import: "optional:configserver:http://localhost:8888"
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration
      - org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration

services:
  accounts:
    base-url: http://localhost:8081

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always

logging:
  level:
    com.banking.transactions: DEBUG
EOF
```

**Salida Esperada:**

```bash
# Compilar todos los módulos desde el directorio raíz
cd ~/banking-reactive-system
mvn compile
```

```
[INFO] Reactor Summary for Banking Reactive System - Parent 1.0.0-SNAPSHOT:
[INFO] Banking Reactive System - Parent ................. SUCCESS
[INFO] Config Server .................................... SUCCESS
[INFO] API Gateway ...................................... SUCCESS
[INFO] Auth Service ..................................... SUCCESS
[INFO] Accounts Service ................................. SUCCESS
[INFO] Transactions Service ............................. SUCCESS
[INFO] BUILD SUCCESS
```

**Verificación:**

```bash
# Verificar que todos los módulos compilan
cd ~/banking-reactive-system
mvn compile

# Verificar la estructura completa del proyecto
find . -name "*.java" | sort
find . -name "*.yml" | grep -v target | sort
```

---

### Paso 7: Configuración de Docker Compose para Infraestructura

**Objetivo:** Crear el archivo `docker-compose.yml` que levante los servicios de infraestructura (PostgreSQL para cada servicio, Prometheus y Grafana) necesarios para el sistema bancario reactivo.

**Instrucciones:**

1. Crea el archivo `docker-compose.yml` en el directorio raíz del proyecto:

```bash
cat > ~/banking-reactive-system/docker-compose.yml << 'EOF'
version: '3.8'

# ============================================================
# SISTEMA BANCARIO REACTIVO - Infraestructura Docker Compose
# ============================================================
# Servicios incluidos:
#   - PostgreSQL para Accounts Service (puerto 5432)
#   - PostgreSQL para Transactions Service (puerto 5433)
#   - Prometheus para métricas (puerto 9090)
#   - Grafana para visualización (puerto 3000)
#
# NOTA: Los microservicios Spring Boot se ejecutan localmente
# durante el desarrollo. Solo la infraestructura corre en Docker.
#
# Iniciar: docker compose up -d
# Detener: docker compose down
# Limpiar todo: docker compose down -v
# ============================================================

networks:
  banking-network:
    driver: bridge
    name: banking-network

volumes:
  postgres-accounts-data:
  postgres-transactions-data:
  prometheus-data:
  grafana-data:

services:

  # ----------------------------------------------------------
  # PostgreSQL - Base de datos del Accounts Service
  # Principio de desacoplamiento: base de datos dedicada
  # ----------------------------------------------------------
  postgres-accounts:
    image: postgres:15-alpine
    container_name: postgres-accounts
    environment:
      POSTGRES_DB: accounts_db
      POSTGRES_USER: accounts_user
      POSTGRES_PASSWORD: accounts_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres-accounts-data:/var/lib/postgresql/data
      - ./infrastructure/init-scripts/accounts-init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - banking-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U accounts_user -d accounts_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ----------------------------------------------------------
  # PostgreSQL - Base de datos del Transactions Service
  # Principio de desacoplamiento: base de datos dedicada
  # ----------------------------------------------------------
  postgres-transactions:
    image: postgres:15-alpine
    container_name: postgres-transactions
    environment:
      POSTGRES_DB: transactions_db
      POSTGRES_USER: transactions_user
      POSTGRES_PASSWORD: transactions_pass
    ports:
      - "5433:5432"
    volumes:
      - postgres-transactions-data:/var/lib/postgresql/data
      - ./infrastructure/init-scripts/transactions-init.sql:/docker-entrypoint-initdb.d/init.sql
    networks:
      - banking-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U transactions_user -d transactions_db"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ----------------------------------------------------------
  # Prometheus - Recolección de métricas
  # Hace scrape de los endpoints /actuator/prometheus de cada servicio
  # ----------------------------------------------------------
  prometheus:
    image: prom/prometheus:v2.47.0
    container_name: prometheus
    ports:
      - "9090:9090"
    volumes:
      - ./infrastructure/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus-data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.console.libraries=/etc/prometheus/console_libraries'
      - '--web.console.templates=/etc/prometheus/consoles'
      - '--web.enable-lifecycle'
    networks:
      - banking-network
    depends_on:
      - postgres-accounts
      - postgres-transactions

  # ----------------------------------------------------------
  # Grafana - Visualización de métricas
  # Dashboard preconfigurado para el sistema bancario
  # ----------------------------------------------------------
  grafana:
    image: grafana/grafana:10.1.0
    container_name: grafana
    ports:
      - "3000:3000"
    environment:
      GF_SECURITY_ADMIN_USER: admin
      GF_SECURITY_ADMIN_PASSWORD: admin123
      GF_USERS_ALLOW_SIGN_UP: "false"
    volumes:
      - grafana-data:/var/lib/grafana
      - ./infrastructure/grafana/provisioning:/etc/grafana/provisioning
    networks:
      - banking-network
    depends_on:
      - prometheus
EOF
```

2. Crea los directorios de infraestructura:

```bash
mkdir -p ~/banking-reactive-system/infrastructure/init-scripts
mkdir -p ~/banking-reactive-system/infrastructure/prometheus
mkdir -p ~/banking-reactive-system/infrastructure/grafana/provisioning/datasources
mkdir -p ~/banking-reactive-system/infrastructure/grafana/provisioning/dashboards
```

3. Crea los scripts de inicialización SQL para cada base de datos:

```bash
cat > ~/banking-reactive-system/infrastructure/init-scripts/accounts-init.sql << 'EOF'
-- Script de inicialización para la base de datos del Accounts Service
-- Este script se ejecuta automáticamente al crear el contenedor PostgreSQL

-- Tabla de cuentas bancarias
CREATE TABLE IF NOT EXISTS accounts (
    id BIGSERIAL PRIMARY KEY,
    account_number VARCHAR(20) UNIQUE NOT NULL,
    owner_name VARCHAR(100) NOT NULL,
    balance DECIMAL(15, 2) NOT NULL DEFAULT 0.00,
    account_type VARCHAR(20) NOT NULL DEFAULT 'CHECKING',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Datos de prueba para el laboratorio
INSERT INTO accounts (account_number, owner_name, balance, account_type, status)
VALUES
    ('ACC-001-2024', 'Juan Pérez García', 5000.00, 'CHECKING', 'ACTIVE'),
    ('ACC-002-2024', 'María López Rodríguez', 12500.50, 'SAVINGS', 'ACTIVE'),
    ('ACC-003-2024', 'Carlos Martínez Silva', 750.25, 'CHECKING', 'ACTIVE')
ON CONFLICT (account_number) DO NOTHING;

-- Índice para búsquedas frecuentes
CREATE INDEX IF NOT EXISTS idx_accounts_account_number ON accounts(account_number);
CREATE INDEX IF NOT EXISTS idx_accounts_status ON accounts(status);

-- Log de inicialización
DO $$
BEGIN
    RAISE NOTICE 'Accounts DB inicializada correctamente con % registros de prueba',
        (SELECT COUNT(*) FROM accounts);
END $$;
EOF
```

```bash
cat > ~/banking-reactive-system/infrastructure/init-scripts/transactions-init.sql << 'EOF'
-- Script de inicialización para la base de datos del Transactions Service
-- Este script se ejecuta automáticamente al crear el contenedor PostgreSQL

-- Tabla de transacciones financieras
CREATE TABLE IF NOT EXISTS transactions (
    id BIGSERIAL PRIMARY KEY,
    transaction_id VARCHAR(36) UNIQUE NOT NULL DEFAULT gen_random_uuid()::VARCHAR,
    account_id BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    description VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- NOTA: No existe foreign key hacia accounts.id porque este servicio
-- NO tiene acceso directo a la base de datos de Accounts Service.
-- La validación de cuentas se realiza via API (WebClient reactivo).
-- Esto implementa el principio de desacoplamiento de datos.

-- Índices para consultas frecuentes
CREATE INDEX IF NOT EXISTS idx_transactions_account_id ON transactions(account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(transaction_type);

-- Log de inicialización
DO $$
BEGIN
    RAISE NOTICE 'Transactions DB inicializada correctamente';
END $$;
EOF
```

4. Crea la configuración de Prometheus:

```bash
cat > ~/banking-reactive-system/infrastructure/prometheus/prometheus.yml << 'EOF'
# Configuración de Prometheus para el Sistema Bancario Reactivo
# Hace scrape de los endpoints /actuator/prometheus de cada microservicio

global:
  scrape_interval: 15s
  evaluation_interval: 15s
  external_labels:
    monitor: 'banking-reactive-system'

scrape_configs:
  # API Gateway
  - job_name: 'api-gateway'
    static_configs:
      - targets: ['host.docker.internal:8080']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  # Auth Service
  - job_name: 'auth-service'
    static_configs:
      - targets: ['host.docker.internal:8083']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  # Accounts Service
  - job_name: 'accounts-service'
    static_configs:
      - targets: ['host.docker.internal:8081']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  # Transactions Service
  - job_name: 'transactions-service'
    static_configs:
      - targets: ['host.docker.internal:8082']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 10s

  # Prometheus se monitorea a sí mismo
  - job_name: 'prometheus'
    static_configs:
      - targets: ['localhost:9090']
EOF
```

5. Crea la configuración del datasource de Grafana:

```bash
cat > ~/banking-reactive-system/infrastructure/grafana/provisioning/datasources/prometheus.yml << 'EOF'
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

6. Levanta los servicios de infraestructura:

```bash
cd ~/banking-reactive-system
docker compose up -d
```

7. Verifica que todos los contenedores estén corriendo:

```bash
docker compose ps
```

**Salida Esperada:**

```
NAME                    IMAGE                       COMMAND                  SERVICE                 CREATED         STATUS                   PORTS
grafana                 grafana/grafana:10.1.0      "/run.sh"                grafana                 30 seconds ago  Up 28 seconds            0.0.0.0:3000->3000/tcp
postgres-accounts       postgres:15-alpine          "docker-entrypoint.s…"   postgres-accounts       30 seconds ago  Up 28 seconds (healthy)  0.0.0.0:5432->5432/tcp
postgres-transactions   postgres:15-alpine          "docker-entrypoint.s…"   postgres-transactions   30 seconds ago  Up 28 seconds (healthy)  0.0.0.0:5433->5432/tcp
prometheus              prom/prometheus:v2.47.0     "/bin/prometheus --c…"   prometheus              30 seconds ago  Up 28 seconds            0.0.0.0:9090->9090/tcp
```

**Verificación:**

```bash
# Verificar que PostgreSQL de Accounts responde
docker exec postgres-accounts psql -U accounts_user -d accounts_db -c "SELECT COUNT(*) FROM accounts;"

# Verificar que PostgreSQL de Transactions responde
docker exec postgres-transactions psql -U transactions_user -d transactions_db -c "\dt"

# Verificar Prometheus en el navegador
curl -s http://localhost:9090/-/healthy

# Verificar Grafana
curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/api/health
```

Los comandos deben retornar:
- PostgreSQL Accounts: `count = 3` (registros de prueba)
- PostgreSQL Transactions: lista de tablas vacía
- Prometheus: `Prometheus Server is Healthy.`
- Grafana: código HTTP `200`

---

### Paso 8: Arranque y Validación de los Microservicios

**Objetivo:** Compilar, arrancar y verificar que los cinco microservicios (Config Server, API Gateway, Auth Service, Accounts Service, Transactions Service) estén operativos y se comuniquen correctamente.

**Instrucciones:**

1. Compila el proyecto completo:

```bash
cd ~/banking-reactive-system
mvn clean package -DskipTests
```

2. Abre **cinco terminales separadas** (o usa tmux/pantallas divididas en IntelliJ). Arranca los servicios en este orden estricto, esperando que cada uno esté listo antes de iniciar el siguiente:

   **Terminal 1 — Config Server (debe ser el primero):**
   ```bash
   cd ~/banking-reactive-system
   java -jar config-server/target/config-server-1.0.0-SNAPSHOT.jar
   ```

   Espera hasta ver en los logs:
   ```
   Started ConfigServerApplication in X.XXX seconds
   ```

   **Terminal 2 — API Gateway:**
   ```bash
   cd ~/banking-reactive-system
   java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
   ```

   **Terminal 3 — Auth Service:**
   ```bash
   cd ~/banking-reactive-system
   java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar
   ```

   **Terminal 4 — Accounts Service:**
   ```bash
   cd ~/banking-reactive-system
   java -jar accounts-service/target/accounts-service-1.0.0-SNAPSHOT.jar
   ```

   **Terminal 5 — Transactions Service:**
   ```bash
   cd ~/banking-reactive-system
   java -jar transactions-service/target/transactions-service-1.0.0-SNAPSHOT.jar
   ```

3. Verifica que todos los servicios están UP usando sus endpoints de health:

```bash
# Config Server
curl -s http://localhost:8888/actuator/health | python3 -m json.tool

# API Gateway
curl -s http://localhost:8080/actuator/health | python3 -m json.tool

# Auth Service (directo)
curl -s http://localhost:8083/actuator/health | python3 -m json.tool

# Accounts Service (directo)
curl -s http://localhost:8081/actuator/health | python3 -m json.tool

# Transactions Service (directo)
curl -s http://localhost:8082/actuator/health | python3 -m json.tool
```

4. Verifica el enrutamiento del API Gateway:

```bash
# Verificar que el Gateway enruta correctamente al Auth Service
curl -s http://localhost:8080/api/auth/status | python3 -m json.tool

# Verificar que el Gateway enruta correctamente al Accounts Service
curl -s http://localhost:8080/api/accounts/status | python3 -m json.tool

# Verificar que el Gateway enruta correctamente al Transactions Service
curl -s http://localhost:8080/api/transactions/status | python3 -m json.tool
```

5. Verifica la propagación del header `X-Trace-Id`:

```bash
# El header X-Trace-Id debe aparecer en la respuesta
curl -v http://localhost:8080/api/accounts/status 2>&1 | grep -i "x-trace-id"
```

6. Verifica que el Config Server sirve configuración a los servicios:

```bash
# El Config Server debe servir la configuración del accounts-service
curl -s http://localhost:8888/accounts-service/default | python3 -m json.tool

# Verificar perfil de producción
curl -s http://localhost:8888/accounts-service/prod | python3 -m json.tool
```

**Salida Esperada para el enrutamiento del Gateway:**

```json
{
    "service": "accounts-service",
    "status": "UP",
    "message": "Accounts Service operativo - R2DBC implementation pending (Lab 2)"
}
```

**Salida Esperada para el X-Trace-Id:**

```
< X-Trace-Id: a3f7b2c1-d4e5-4f6a-b7c8-d9e0f1a2b3c4
< X-Gateway-Version: 1.0.0
```

**Verificación:**

- Los 5 servicios deben responder con `"status": "UP"` en sus health checks
- El API Gateway debe enrutar correctamente a los 3 servicios de dominio
- El header `X-Trace-Id` debe aparecer en todas las respuestas del Gateway
- El Config Server debe servir configuración para cada servicio

---

### Paso 9: Commit Final y Documentación del README

**Objetivo:** Consolidar todo el trabajo realizado en un commit final con un README completo que documente la arquitectura, los puertos y las instrucciones de arranque.

**Instrucciones:**

1. Crea el archivo `README.md` principal del proyecto:

```bash
cat > ~/banking-reactive-system/README.md << 'EOF'
# Sistema Bancario Reactivo con Spring WebFlux

## Descripción

Sistema bancario reactivo implementado con microservicios Spring Boot 3.x,
Spring WebFlux y Spring Cloud. Desarrollado como proyecto integrador del
curso de Microservicios Reactivos.

## Arquitectura

Ver [docs/architecture-diagram.drawio](docs/architecture-diagram.drawio) para
el diagrama completo de arquitectura.

## Asignación de Puertos (FIJA - No modificar)

| Servicio | Puerto | Descripción |
|----------|--------|-------------|
| API Gateway | **8080** | Punto de entrada único |
| Accounts Service | **8081** | Gestión de cuentas bancarias |
| Transactions Service | **8082** | Transacciones financieras |
| Auth Service | **8083** | Autenticación JWT |
| Config Server | **8888** | Configuración centralizada |
| PostgreSQL Accounts | **5432** | BD exclusiva de Accounts Service |
| PostgreSQL Transactions | **5433** | BD exclusiva de Transactions Service |
| Prometheus | **9090** | Métricas |
| Grafana | **3000** | Dashboards (admin/admin123) |

## Inicio Rápido

### 1. Prerrequisitos
- Java 17 o 21
- Maven 3.9.x
- Docker Desktop 4.25+

### 2. Levantar infraestructura
```bash
docker compose up -d
```

### 3. Compilar el proyecto
```bash
mvn clean package -DskipTests
```

### 4. Arrancar servicios (en orden)
```bash
# Terminal 1
java -jar config-server/target/config-server-1.0.0-SNAPSHOT.jar

# Terminal 2
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar

# Terminal 3
java -jar auth-service/target/auth-service-1.0.0-SNAPSHOT.jar

# Terminal 4
java -jar accounts-service/target/accounts-service-1.0.0-SNAPSHOT.jar

# Terminal 5
java -jar transactions-service/target/transactions-service-1.0.0-SNAPSHOT.jar
```

### 5. Verificar el sistema
```bash
curl http://localhost:8080/api/accounts/status
curl http://localhost:8080/api/transactions/status
curl http://localhost:8080/api/auth/status
```

## Módulos del Proyecto

| Módulo | Descripción | Bounded Context |
|--------|-------------|-----------------|
| `config-server` | Configuración centralizada | Infraestructura |
| `api-gateway` | Enrutamiento y filtros globales | Edge Layer |
| `auth-service` | Autenticación JWT | Autenticación |
| `accounts-service` | Cuentas bancarias | Gestión de Cuentas |
| `transactions-service` | Transacciones financieras | Transacciones |

## Principios de Diseño

- **Desacoplamiento de datos**: Cada servicio tiene su propia base de datos
- **Single Responsibility**: Cada servicio tiene una única razón para cambiar
- **API-First**: Comunicación exclusivamente a través de APIs REST reactivas
- **Trazabilidad**: Header `X-Trace-Id` propagado en todos los requests

## Estado de Implementación por Laboratorio

- [x] **Lab 1**: Arquitectura base, API Gateway, Config Server (este lab)
- [ ] **Lab 2**: Persistencia reactiva con R2DBC
- [ ] **Lab 3**: Autenticación JWT con Spring Security WebFlux
- [ ] **Lab 4**: Resiliencia con Resilience4j y observabilidad
- [ ] **Lab 5**: Integración y validación end-to-end
EOF
```

2. Realiza el commit final del laboratorio:

```bash
cd ~/banking-reactive-system
git add .
git commit -m "feat(lab-01): complete base architecture with multi-module Maven project

- Add Maven multi-module parent POM with Spring Boot 3.2.5 and Spring Cloud 2023.0.1 BOM
- Add Config Server with native profile and per-service configuration files
- Add API Gateway with Spring Cloud Gateway routes and TraceId global filter
- Add skeleton services: auth-service, accounts-service, transactions-service
- Add Docker Compose for infrastructure: PostgreSQL x2, Prometheus, Grafana
- Add SQL init scripts with test data for accounts database
- Add Prometheus configuration for metrics scraping
- Add ARCHITECTURE.md documenting bounded contexts and design decisions
- Add README.md with port assignments and quick start guide

Port assignments:
  - API Gateway: 8080
  - Accounts Service: 8081
  - Transactions Service: 8082
  - Auth Service: 8083
  - Config Server: 8888
  - PostgreSQL Accounts: 5432
  - PostgreSQL Transactions: 5433
  - Prometheus: 9090
  - Grafana: 3000"
```

3. Verifica el historial de commits:

```bash
git log --oneline
```

**Salida Esperada:**

```
abc5678 feat(lab-01): complete base architecture with multi-module Maven project
abc1234 feat: add architecture diagram and bounded context documentation
```

**Verificación:**

```bash
# Verificar que no hay archivos sin trackear
git status

# Verificar la estructura completa del repositorio
git ls-files | sort
```

`git status` debe mostrar: `nothing to commit, working tree clean`

## Validación y Pruebas

### Criterios de Éxito

- [ ] El diagrama de arquitectura en draw.io muestra los 5 componentes (3 servicios + Gateway + Config Server) con bounded contexts claramente delimitados
- [ ] El proyecto multi-módulo Maven compila con `BUILD SUCCESS` ejecutando `mvn compile` desde el directorio raíz
- [ ] Los 4 contenedores Docker (postgres-accounts, postgres-transactions, prometheus, grafana) están en estado `healthy`
- [ ] El Config Server responde en `http://localhost:8888/accounts-service/default` con la configuración correcta
- [ ] El API Gateway enruta correctamente a los 3 servicios: `/api/auth/**`, `/api/accounts/**`, `/api/transactions/**`
- [ ] El header `X-Trace-Id` aparece en todas las respuestas del API Gateway
- [ ] La base de datos de cuentas contiene 3 registros de prueba
- [ ] El repositorio Git tiene al menos 2 commits con mensajes descriptivos

### Procedimiento de Pruebas

1. **Prueba de compilación del proyecto multi-módulo:**
   ```bash
   cd ~/banking-reactive-system
   mvn clean compile
   ```
   **Resultado Esperado:** `BUILD SUCCESS` para los 5 módulos

2. **Prueba de infraestructura Docker:**
   ```bash
   docker compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
   ```
   **Resultado Esperado:** Los 4 servicios en estado `Up (healthy)`

3. **Prueba del Config Server:**
   ```bash
   curl -s http://localhost:8888/accounts-service/default | python3 -m json.tool
   ```
   **Resultado Esperado:** JSON con la configuración del `accounts-service` incluyendo el puerto 8081

4. **Prueba de enrutamiento del API Gateway:**
   ```bash
   for service in auth accounts transactions; do
     echo "=== Testing /api/$service/status ==="
     curl -s http://localhost:8080/api/$service/status
     echo ""
   done
   ```
   **Resultado Esperado:** Respuesta JSON de cada servicio con `"status": "UP"`

5. **Prueba del header X-Trace-Id:**
   ```bash
   curl -I http://localhost:8080/api/accounts/status 2>&1 | grep -i "x-trace"
   ```
   **Resultado Esperado:** `X-Trace-Id: [UUID generado automáticamente]`

6. **Prueba de datos de prueba en PostgreSQL:**
   ```bash
   docker exec postgres-accounts psql -U accounts_user -d accounts_db \
     -c "SELECT account_number, owner_name, balance FROM accounts ORDER BY id;"
   ```
   **Resultado Esperado:**
   ```
    account_number |       owner_name        | balance
   ----------------+-------------------------+---------
    ACC-001-2024   | Juan Pérez García       | 5000.00
    ACC-002-2024   | María López Rodríguez   | 12500.50
    ACC-003-2024   | Carlos Martínez Silva   |  750.25
   ```

7. **Prueba de Prometheus:**
   ```bash
   curl -s http://localhost:9090/api/v1/targets | python3 -m json.tool | grep '"health"'
   ```
   **Resultado Esperado:** Líneas con `"health": "up"` para los targets configurados

## Solución de Problemas

### Problema 1: Error "Port already in use" al arrancar un servicio

**Síntomas:**
- El servicio Spring Boot falla al iniciar con `Address already in use: bind`
- El log muestra `Web server failed to start. Port XXXX was already in use.`

**Causa:**
Otro proceso está usando el puerto asignado al microservicio. Puede ser una instancia anterior del mismo servicio que no se cerró correctamente.

**Solución:**
```bash
# Identificar qué proceso usa el puerto (ejemplo: puerto 8080)
lsof -i :8080

# Terminar el proceso por PID (reemplazar 12345 con el PID real)
kill -9 12345

# Alternativa en Windows PowerShell:
# netstat -ano | findstr :8080
# taskkill /PID 12345 /F
```

---

### Problema 2: `mvn compile` falla con "Cannot resolve symbol"

**Síntomas:**
- Error de compilación Maven con `package org.springframework.cloud does not exist`
- El build falla con `BUILD FAILURE` y errores de dependencias no encontradas

**Causa:**
Las dependencias de Spring Cloud no se descargaron correctamente, o hay un problema de conectividad con Maven Central.

**Solución:**
```bash
# Limpiar el caché local de Maven y forzar re-descarga
cd ~/banking-reactive-system
mvn clean compile -U

# Si el problema persiste, limpiar el repositorio local de Maven
rm -rf ~/.m2/repository/org/springframework/cloud
mvn clean compile

# Verificar conectividad con Maven Central
curl -s https://repo1.maven.org/maven2/ | head -5
```

---

### Problema 3: Los contenedores Docker no pasan el healthcheck

**Síntomas:**
- `docker compose ps` muestra el estado `unhealthy` o `starting` después de 60 segundos
- Los logs del contenedor muestran errores de inicialización de PostgreSQL

**Causa:**
PostgreSQL puede tardar más de lo esperado en inicializarse, especialmente en la primera ejecución cuando crea los volúmenes y ejecuta los scripts de inicialización.

**Solución:**
```bash
# Ver los logs del contenedor problemático
docker logs postgres-accounts --tail 50

# Esperar manualmente y verificar el estado
docker compose ps

# Si el script SQL tiene errores, reiniciar el contenedor con volumen limpio
docker compose down -v
docker compose up -d

# Verificar que el script SQL no tiene errores de sintaxis
docker exec postgres-accounts psql -U accounts_user -d accounts_db \
  -f /docker-entrypoint-initdb.d/init.sql
```

---

### Problema 4: El API Gateway responde 404 al enrutar a un microservicio

**Síntomas:**
- `curl http://localhost:8080/api/accounts/status` retorna `{"timestamp":"...","path":"/api/accounts/status","status":404}`
- El accounts-service está UP y responde directamente en `http://localhost:8081/accounts/status`

**Causa:**
El filtro `RewritePath` del Gateway puede no estar reescribiendo correctamente la URL, o el microservicio no está escuchando en el path esperado.

**Solución:**
```bash
# 1. Verificar que el servicio destino responde directamente (sin Gateway)
curl -v http://localhost:8081/accounts/status

# 2. Verificar las rutas configuradas en el Gateway
curl -s http://localhost:8080/actuator/gateway/routes | python3 -m json.tool

# 3. Habilitar logs de debug del Gateway para ver el enrutamiento
# En api-gateway/src/main/resources/application.yml, verificar:
# logging:
#   level:
#     org.springframework.cloud.gateway: DEBUG

# 4. Reiniciar el API Gateway y observar los logs de enrutamiento
```

---

### Problema 5: El Config Server no sirve la configuración (404 en /accounts-service/default)

**Síntomas:**
- `curl http://localhost:8888/accounts-service/default` retorna 404
- Los microservicios arrancan pero no obtienen configuración del Config Server

**Causa:**
Los archivos de configuración no están en la ubicación correcta dentro del classpath, o el perfil `native` no está activado correctamente.

**Solución:**
```bash
# Verificar que los archivos de configuración existen
find ~/banking-reactive-system/config-server/src/main/resources/config-repo -name "*.yml"

# Verificar que el Config Server está usando el perfil native
curl -s http://localhost:8888/actuator/env | python3 -m json.tool | grep -A2 "spring.profiles.active"

# Si los archivos existen pero el Config Server no los encuentra,
# verificar la propiedad search-locations en application.yml:
# spring.cloud.config.server.native.search-locations: classpath:/config-repo

# Recompilar y reiniciar el Config Server
cd ~/banking-reactive-system
mvn package -pl config-server -DskipTests
java -jar config-server/target/config-server-1.0.0-SNAPSHOT.jar
```

---

### Problema 6: Error en Windows con paths de Docker Compose volumes

**Síntomas:**
- En Windows, `docker compose up` falla con errores de montaje de volúmenes
- Los scripts SQL de inicialización no se ejecutan

**Causa:**
En Windows con Docker Desktop, los paths de volúmenes deben usar formato Unix cuando se ejecuta desde WSL2, o formato Windows cuando se ejecuta desde PowerShell.

**Solución:**
```bash
# Si usas WSL2 (recomendado), asegúrate de estar dentro del filesystem de WSL2
# NO en /mnt/c/... sino en ~/banking-reactive-system

# Verificar que estás en el filesystem correcto
pwd
# Debe mostrar: /home/usuario/banking-reactive-system (NO /mnt/c/...)

# Si el proyecto está en Windows filesystem, muévelo a WSL2
cp -r /mnt/c/Users/TuUsuario/banking-reactive-system ~/banking-reactive-system
cd ~/banking-reactive-system
docker compose up -d
```

## Limpieza

Para detener todos los servicios y limpiar los recursos del laboratorio:

```bash
# 1. Detener los microservicios Spring Boot
# Presiona Ctrl+C en cada terminal donde están corriendo los servicios

# 2. Detener los contenedores Docker (preservando los datos)
cd ~/banking-reactive-system
docker compose down

# 3. Para limpiar COMPLETAMENTE (incluyendo datos de bases de datos)
# ⚠️ ADVERTENCIA: Esto elimina todos los datos de PostgreSQL
docker compose down -v

# 4. Verificar que no quedan contenedores del proyecto
docker ps | grep -E "postgres|prometheus|grafana"

# 5. Limpiar artefactos de compilación Maven (opcional)
mvn clean

# 6. Verificar el estado final del repositorio Git
git status
git log --oneline
```

> ⚠️ **Advertencia:** El comando `docker compose down -v` elimina los volúmenes de datos de PostgreSQL. Si quieres preservar los datos entre sesiones de laboratorio, usa solo `docker compose down` sin la flag `-v`. Los scripts de inicialización SQL solo se ejecutan la primera vez que se crea el volumen.

> ⚠️ **Advertencia de Seguridad:** El archivo `application.yml` del Config Server contiene configuraciones con credenciales de base de datos en texto plano. Esto es aceptable para entornos de laboratorio, pero en producción estas credenciales DEBEN gestionarse con variables de entorno o un sistema de gestión de secretos (HashiCorp Vault, AWS Secrets Manager, etc.). **Nunca commitees credenciales reales a un repositorio Git.**

## Resumen

### Lo que Lograste

- **Diseñaste** un diagrama de arquitectura completo en draw.io que identifica los bounded contexts, los canales de comunicación y los componentes de infraestructura del sistema bancario reactivo
- **Documentaste** las fronteras de dominio de cada microservicio en `ARCHITECTURE.md`, aplicando los principios de DDD y desacoplamiento aprendidos en la lección teórica
- **Creaste** un proyecto multi-módulo Maven con gestión centralizada de versiones mediante Spring Boot BOM y Spring Cloud BOM, evitando conflictos de versiones entre dependencias
- **Implementaste** un Config Server con Spring Cloud Config que sirve configuración externalizada con soporte para perfiles por ambiente (dev, prod)
- **Configuraste** un API Gateway con Spring Cloud Gateway que enruta peticiones a los tres servicios de dominio y propaga el header `X-Trace-Id` para trazabilidad distribuida
- **Levantaste** la infraestructura completa con Docker Compose: dos instancias independientes de PostgreSQL (una por servicio), Prometheus y Grafana
- **Verificaste** que el sistema arranca correctamente end-to-end con todos los servicios respondiendo a health checks

### Conceptos Clave Aprendidos

- **Bounded Context en práctica**: La separación de bases de datos entre `accounts-service` y `transactions-service` no es solo una decisión técnica, es la materialización del principio de desacoplamiento de DDD. Cambiar el esquema de `accounts_db` no afecta a `transactions_db`.
- **Monolito distribuido evitado**: Al no compartir base de datos y comunicarse solo por APIs, evitamos el antipatrón más común en arquitecturas de microservicios.
- **Configuración externalizada**: El Config Server permite cambiar la configuración de cualquier servicio sin recompilarlo, y soportar múltiples ambientes con perfiles.
- **Trazabilidad desde el inicio**: El filtro `TraceIdGlobalFilter` del Gateway establece la base para correlacionar logs entre servicios, un requisito crítico en sistemas distribuidos.
- **Stack reactivo consistente**: Todos los controladores usan `Mono<ResponseEntity<T>>` en lugar de `ResponseEntity<T>`, manteniendo el modelo no bloqueante de principio a fin.

### Próximos Pasos

- **Laboratorio 2:** Implementarás persistencia reactiva con R2DBC en `accounts-service` y `transactions-service`, conectándolos a las bases de datos PostgreSQL que ya están corriendo. Aprenderás la diferencia entre `ReactiveCrudRepository` y `JpaRepository`, y por qué las operaciones de base de datos deben permanecer en el pipeline reactivo.
- **Laboratorio 3:** Implementarás autenticación JWT con Spring Security WebFlux en el `auth-service` y protegerás los endpoints de los servicios de dominio.

## Recursos Adicionales

- [Spring Cloud Gateway - Documentación oficial](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/) - Referencia completa de predicados, filtros y configuración del Gateway
- [Spring Cloud Config - Documentación oficial](https://docs.spring.io/spring-cloud-config/docs/current/reference/html/) - Guía completa del Config Server incluyendo backends Git, Vault y nativo
- [Project Reactor - Referencia](https://projectreactor.io/docs/core/release/reference/) - Documentación de `Mono`, `Flux` y operadores del pipeline reactivo
- [draw.io - Guía de inicio](https://www.diagrams.net/doc/) - Tutoriales para crear diagramas de arquitectura profesionales
- [Docker Compose - Referencia de healthcheck](https://docs.docker.com/compose/compose-file/05-services/#healthcheck) - Configuración avanzada de health checks para contenedores
- [Martin Fowler - Microservices](https://martinfowler.com/articles/microservices.html) - Artículo fundacional sobre arquitectura de microservicios con diagramas de referencia
