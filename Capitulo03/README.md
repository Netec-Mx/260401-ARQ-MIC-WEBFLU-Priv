# Integración de JWT y Protección de APIs en Microservicios Reactivos

## Metadatos

| Propiedad | Valor |
|-----------|-------|
| **Duración** | 110 minutos |
| **Complejidad** | Avanzado |
| **Nivel Bloom** | Crear |
| **Módulo** | 3 — Seguridad en Arquitecturas Reactivas |

## Descripción General

En este laboratorio construirás un sistema de seguridad completo basado en JWT para el sistema bancario simplificado desarrollado en los laboratorios anteriores. Crearás un nuevo módulo `auth-service` que genera tokens JWT firmados con HS256, configurarás filtros globales reactivos en el API Gateway para validar y propagar claims como headers internos, y protegerás los endpoints de `accounts-service` y `transactions-service` mediante reglas de autorización basadas en roles.

Este laboratorio representa el escenario más cercano a un sistema de producción real: la seguridad no es un componente aislado sino una capa transversal que atraviesa toda la arquitectura. Al finalizar, comprenderás cómo implementar el patrón de seguridad en capas donde el Gateway actúa como guardián perimetral y cada microservicio aplica autorización granular basándose en los claims propagados.

## Objetivos de Aprendizaje

Al completar este laboratorio, serás capaz de:

- [ ] Implementar un `auth-service` con Spring WebFlux que genere tokens JWT firmados con HS256 usando la librería JJWT 0.12.x
- [ ] Configurar un filtro global reactivo (`GlobalFilter`) en el API Gateway que valide tokens JWT y propague claims como headers internos (`X-User-Id`, `X-User-Roles`)
- [ ] Proteger endpoints por roles usando `SecurityWebFilterChain` reactiva y anotaciones `@PreAuthorize` con SpEL
- [ ] Implementar propagación de tokens JWT entre microservicios en llamadas `WebClient` para mantener la cadena de autenticación
- [ ] Manejar excepciones de seguridad reactivas con respuestas JSON apropiadas (401, 403)
- [ ] Validar todos los escenarios de seguridad con Postman: token válido, expirado, inválido, ausente y rol insuficiente

## Prerrequisitos

### Conocimiento Requerido

- Laboratorios 1 y 2 completados con comunicación inter-servicios funcionando correctamente
- Comprensión de los conceptos de autenticación vs. autorización y tokens Bearer
- Conocimiento básico de Spring Security (se adaptará a WebFlux en este laboratorio)
- Familiaridad con el modelo reactivo de Spring WebFlux: `Mono`, `Flux`, operadores `map`, `flatMap`, `filter`
- Postman instalado con conocimiento de configuración de headers de autorización

### Acceso Requerido

- Proyecto multi-módulo Maven del curso con `api-gateway`, `accounts-service` y `transactions-service` funcionando
- Docker Desktop en ejecución con los contenedores de PostgreSQL activos
- IntelliJ IDEA con el proyecto del curso abierto
- Acceso a internet para descargar dependencias Maven (JJWT, Spring Security)

## Entorno de Laboratorio

### Requisitos de Hardware

| Componente | Especificación |
|------------|----------------|
| Procesador | Intel Core i5 / AMD Ryzen 5 (8ª gen o superior, mínimo 4 núcleos) |
| RAM | Mínimo 16 GB (recomendado 32 GB) |
| Almacenamiento | Mínimo 5 GB libres adicionales |
| Red | Banda ancha estable (mínimo 10 Mbps) |

### Requisitos de Software

| Software | Versión | Propósito |
|----------|---------|-----------|
| JDK | 17 LTS o 21 LTS | Compilación y ejecución de microservicios |
| Apache Maven | 3.9.x | Gestión de dependencias y construcción |
| IntelliJ IDEA | 2023.3 o superior | IDE principal de desarrollo |
| Docker Desktop | 4.25 o superior | Ejecución de PostgreSQL y servicios de infraestructura |
| Postman | 10.x o superior | Pruebas de endpoints y flujos de autenticación |
| Spring Boot | 3.2.x | Framework base de todos los microservicios |
| JJWT | 0.12.x | Generación y validación de tokens JWT |
| Spring Security WebFlux | 6.x | Filtros reactivos de seguridad |

### Configuración Inicial

Antes de comenzar, verifica que el entorno del laboratorio anterior esté funcionando correctamente:

```bash
# Verificar que los contenedores de infraestructura estén activos
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

# Verificar que los servicios existentes respondan
curl -s http://localhost:8080/actuator/health | python3 -m json.tool
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
curl -s http://localhost:8082/actuator/health | python3 -m json.tool
```

Si algún servicio no responde, inicia los contenedores de Docker Compose:

```bash
# Desde el directorio raíz del proyecto
cd ~/banking-system
docker-compose up -d postgres-accounts postgres-transactions
```

Verifica la estructura actual del proyecto:

```bash
ls -la ~/banking-system/
# Debes ver: api-gateway/, accounts-service/, transactions-service/, docker-compose.yml, pom.xml
```

## Instrucciones Paso a Paso

### Paso 1: Agregar Dependencias JWT al POM Raíz y Crear el Módulo auth-service

**Objetivo:** Configurar las dependencias de JJWT en el POM padre del proyecto multi-módulo y crear la estructura del nuevo módulo `auth-service`.

**Instrucciones:**

1. Abre el archivo `pom.xml` raíz del proyecto en IntelliJ IDEA y agrega las dependencias de JJWT en la sección `<dependencyManagement>`:

   ```xml
   <!-- En ~/banking-system/pom.xml, dentro de <dependencyManagement><dependencies> -->
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-api</artifactId>
       <version>0.12.3</version>
   </dependency>
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-impl</artifactId>
       <version>0.12.3</version>
       <scope>runtime</scope>
   </dependency>
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-jackson</artifactId>
       <version>0.12.3</version>
       <scope>runtime</scope>
   </dependency>
   ```

2. Agrega el módulo `auth-service` en la sección `<modules>` del POM raíz:

   ```xml
   <!-- En ~/banking-system/pom.xml, dentro de <modules> -->
   <modules>
       <module>api-gateway</module>
       <module>accounts-service</module>
       <module>transactions-service</module>
       <module>auth-service</module>
   </modules>
   ```

3. Crea la estructura de directorios para el nuevo módulo:

   ```bash
   mkdir -p ~/banking-system/auth-service/src/main/java/com/banking/auth/{controller,service,model,config,util}
   mkdir -p ~/banking-system/auth-service/src/main/resources
   mkdir -p ~/banking-system/auth-service/src/test/java/com/banking/auth
   ```

4. Crea el archivo `pom.xml` del módulo `auth-service`:

   ```bash
   cat > ~/banking-system/auth-service/pom.xml << 'EOF'
   <?xml version="1.0" encoding="UTF-8"?>
   <project xmlns="http://maven.apache.org/POM/4.0.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
       <modelVersion>4.0.0</modelVersion>
   
       <parent>
           <groupId>com.banking</groupId>
           <artifactId>banking-system</artifactId>
           <version>1.0.0-SNAPSHOT</version>
       </parent>
   
       <artifactId>auth-service</artifactId>
       <name>auth-service</name>
       <description>Servicio de autenticación JWT para el sistema bancario</description>
   
       <dependencies>
           <!-- Spring Boot WebFlux -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-webflux</artifactId>
           </dependency>
   
           <!-- Spring Security WebFlux -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-security</artifactId>
           </dependency>
   
           <!-- Spring Boot Actuator -->
           <dependency>
               <groupId>org.springframework.boot</groupId>
               <artifactId>spring-boot-starter-actuator</artifactId>
           </dependency>
   
           <!-- JJWT para generación y validación de tokens -->
           <dependency>
               <groupId>io.jsonwebtoken</groupId>
               <artifactId>jjwt-api</artifactId>
           </dependency>
           <dependency>
               <groupId>io.jsonwebtoken</groupId>
               <artifactId>jjwt-impl</artifactId>
           </dependency>
           <dependency>
               <groupId>io.jsonwebtoken</groupId>
               <artifactId>jjwt-jackson</artifactId>
           </dependency>
   
           <!-- Lombok para reducir boilerplate -->
           <dependency>
               <groupId>org.projectlombok</groupId>
               <artifactId>lombok</artifactId>
               <optional>true</optional>
           </dependency>
   
           <!-- Test -->
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

**Salida Esperada:**

```
~/banking-system/auth-service/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/banking/auth/
    │   │   ├── controller/
    │   │   ├── service/
    │   │   ├── model/
    │   │   ├── config/
    │   │   └── util/
    │   └── resources/
    └── test/
        └── java/com/banking/auth/
```

**Verificación:**

```bash
# Verificar que Maven reconoce el nuevo módulo
cd ~/banking-system
mvn validate -q
echo "Exit code: $?"
# Debe mostrar "Exit code: 0" sin errores
```

---

### Paso 2: Implementar los Modelos y la Utilidad JWT

**Objetivo:** Crear las clases de modelo para las solicitudes/respuestas de autenticación y la utilidad `JwtUtil` que encapsula toda la lógica de generación y validación de tokens.

**Instrucciones:**

1. Crea el modelo de usuario con sus roles:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/model/User.java << 'EOF'
   package com.banking.auth.model;
   
   import lombok.AllArgsConstructor;
   import lombok.Builder;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   
   import java.util.List;
   
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class User {
       private String userId;
       private String username;
       private String password; // Almacenada con BCrypt en producción
       private List<String> roles;
       private boolean active;
   }
   EOF
   ```

2. Crea las clases de request y response para el endpoint de login:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/model/LoginRequest.java << 'EOF'
   package com.banking.auth.model;
   
   import lombok.AllArgsConstructor;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   
   @Data
   @NoArgsConstructor
   @AllArgsConstructor
   public class LoginRequest {
       private String username;
       private String password;
   }
   EOF
   
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/model/TokenResponse.java << 'EOF'
   package com.banking.auth.model;
   
   import lombok.AllArgsConstructor;
   import lombok.Builder;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class TokenResponse {
       private String token;
       private String tokenType;
       private long expiresIn;
       private String userId;
       private java.util.List<String> roles;
   }
   EOF
   
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/model/ValidateRequest.java << 'EOF'
   package com.banking.auth.model;
   
   import lombok.AllArgsConstructor;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   
   @Data
   @NoArgsConstructor
   @AllArgsConstructor
   public class ValidateRequest {
       private String token;
   }
   EOF
   
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/model/ValidateResponse.java << 'EOF'
   package com.banking.auth.model;
   
   import lombok.AllArgsConstructor;
   import lombok.Builder;
   import lombok.Data;
   import lombok.NoArgsConstructor;
   
   import java.util.List;
   
   @Data
   @Builder
   @NoArgsConstructor
   @AllArgsConstructor
   public class ValidateResponse {
       private boolean valid;
       private String userId;
       private String username;
       private List<String> roles;
       private String message;
   }
   EOF
   ```

3. Crea la utilidad `JwtUtil` con soporte completo para JJWT 0.12.x:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/util/JwtUtil.java << 'EOF'
   package com.banking.auth.util;
   
   import io.jsonwebtoken.Claims;
   import io.jsonwebtoken.JwtException;
   import io.jsonwebtoken.Jwts;
   import io.jsonwebtoken.security.Keys;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.stereotype.Component;
   
   import javax.crypto.SecretKey;
   import java.nio.charset.StandardCharsets;
   import java.util.Date;
   import java.util.List;
   
   @Slf4j
   @Component
   public class JwtUtil {
   
       private final SecretKey secretKey;
       private final long expirationMs;
   
       public JwtUtil(
               @Value("${jwt.secret}") String secret,
               @Value("${jwt.expiration-ms:3600000}") long expirationMs) {
           // La clave debe tener al menos 256 bits (32 caracteres) para HS256
           this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
           this.expirationMs = expirationMs;
           log.info("JwtUtil inicializado. Expiración de token: {} ms ({} horas)",
                   expirationMs, expirationMs / 3600000.0);
       }
   
       /**
        * Genera un token JWT firmado con HS256.
        * Incluye los claims: sub (userId), username, roles, iat, exp.
        */
       public String generateToken(String userId, String username, List<String> roles) {
           Date now = new Date();
           Date expiration = new Date(now.getTime() + expirationMs);
   
           String token = Jwts.builder()
                   .subject(userId)
                   .claim("username", username)
                   .claim("roles", roles)
                   .issuedAt(now)
                   .expiration(expiration)
                   .signWith(secretKey)
                   .compact();
   
           log.debug("Token generado para usuario: {} con roles: {}", username, roles);
           return token;
       }
   
       /**
        * Valida el token y retorna los claims si es válido.
        * Lanza JwtException si el token es inválido o está expirado.
        */
       public Claims validateAndExtractClaims(String token) {
           return Jwts.parser()
                   .verifyWith(secretKey)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
       }
   
       /**
        * Retorna true si el token es válido, false en caso contrario.
        * No lanza excepciones — útil para filtros reactivos.
        */
       public boolean isTokenValid(String token) {
           try {
               validateAndExtractClaims(token);
               return true;
           } catch (JwtException | IllegalArgumentException e) {
               log.warn("Token inválido: {}", e.getMessage());
               return false;
           }
       }
   
       public String extractUserId(String token) {
           return validateAndExtractClaims(token).getSubject();
       }
   
       public String extractUsername(String token) {
           return validateAndExtractClaims(token).get("username", String.class);
       }
   
       @SuppressWarnings("unchecked")
       public List<String> extractRoles(String token) {
           return validateAndExtractClaims(token).get("roles", List.class);
       }
   
       public long getExpirationMs() {
           return expirationMs;
       }
   }
   EOF
   ```

**Salida Esperada:** Los archivos Java deben crearse sin errores de sintaxis. IntelliJ IDEA mostrará las clases en el árbol del proyecto.

**Verificación:**

```bash
# Compilar solo el módulo auth-service para verificar que no hay errores
cd ~/banking-system
mvn compile -pl auth-service -q
echo "Compilación exitosa: $?"
```

---

### Paso 3: Implementar el Servicio de Usuarios y el Controlador de Autenticación

**Objetivo:** Crear el servicio que gestiona los usuarios en memoria (para este laboratorio) y el controlador reactivo que expone los endpoints `/auth/login` y `/auth/validate`.

**Instrucciones:**

1. Crea el servicio de usuarios con datos de prueba precargados:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/service/UserService.java << 'EOF'
   package com.banking.auth.service;
   
   import com.banking.auth.model.User;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
   import org.springframework.stereotype.Service;
   import reactor.core.publisher.Mono;
   
   import java.util.List;
   import java.util.Map;
   import java.util.concurrent.ConcurrentHashMap;
   
   @Slf4j
   @Service
   public class UserService {
   
       private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
       private final Map<String, User> userStore = new ConcurrentHashMap<>();
   
       public UserService() {
           // Usuarios de prueba precargados
           // ADVERTENCIA: En producción, los usuarios deben persistirse en base de datos
           // y las contraseñas NUNCA deben estar hardcodeadas en el código fuente
           initializeTestUsers();
       }
   
       private void initializeTestUsers() {
           User admin = User.builder()
                   .userId("usr-001")
                   .username("admin")
                   .password(passwordEncoder.encode("password123"))
                   .roles(List.of("ROLE_USER", "ROLE_ADMIN"))
                   .active(true)
                   .build();
   
           User regularUser = User.builder()
                   .userId("usr-002")
                   .username("user")
                   .password(passwordEncoder.encode("password123"))
                   .roles(List.of("ROLE_USER"))
                   .active(true)
                   .build();
   
           userStore.put(admin.getUsername(), admin);
           userStore.put(regularUser.getUsername(), regularUser);
   
           log.info("Usuarios de prueba inicializados: admin (ROLE_USER, ROLE_ADMIN), user (ROLE_USER)");
       }
   
       /**
        * Busca un usuario por nombre de usuario.
        * Retorna Mono.empty() si no existe.
        */
       public Mono<User> findByUsername(String username) {
           User user = userStore.get(username);
           if (user == null) {
               log.debug("Usuario no encontrado: {}", username);
               return Mono.empty();
           }
           return Mono.just(user);
       }
   
       /**
        * Verifica si la contraseña en texto plano coincide con el hash almacenado.
        */
       public boolean verifyPassword(String rawPassword, String encodedPassword) {
           return passwordEncoder.matches(rawPassword, encodedPassword);
       }
   }
   EOF
   ```

2. Crea el servicio de autenticación que orquesta el flujo de login:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/service/AuthService.java << 'EOF'
   package com.banking.auth.service;
   
   import com.banking.auth.model.*;
   import com.banking.auth.util.JwtUtil;
   import io.jsonwebtoken.Claims;
   import io.jsonwebtoken.JwtException;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.stereotype.Service;
   import reactor.core.publisher.Mono;
   
   @Slf4j
   @Service
   @RequiredArgsConstructor
   public class AuthService {
   
       private final UserService userService;
       private final JwtUtil jwtUtil;
   
       /**
        * Autentica al usuario y genera un JWT si las credenciales son válidas.
        * Retorna Mono.empty() si las credenciales son incorrectas.
        */
       public Mono<TokenResponse> login(LoginRequest request) {
           log.info("Intento de login para usuario: {}", request.getUsername());
   
           return userService.findByUsername(request.getUsername())
                   .filter(user -> user.isActive())
                   .filter(user -> userService.verifyPassword(request.getPassword(), user.getPassword()))
                   .map(user -> {
                       String token = jwtUtil.generateToken(
                               user.getUserId(),
                               user.getUsername(),
                               user.getRoles()
                       );
   
                       log.info("Login exitoso para usuario: {} con roles: {}",
                               user.getUsername(), user.getRoles());
   
                       return TokenResponse.builder()
                               .token(token)
                               .tokenType("Bearer")
                               .expiresIn(jwtUtil.getExpirationMs() / 1000)
                               .userId(user.getUserId())
                               .roles(user.getRoles())
                               .build();
                   })
                   .doOnEmpty(() -> log.warn("Login fallido para usuario: {}", request.getUsername()));
       }
   
       /**
        * Valida un token JWT y retorna los claims si es válido.
        */
       public Mono<ValidateResponse> validate(String token) {
           return Mono.fromCallable(() -> {
               try {
                   Claims claims = jwtUtil.validateAndExtractClaims(token);
                   java.util.List<String> roles = claims.get("roles", java.util.List.class);
   
                   log.debug("Token válido para userId: {}", claims.getSubject());
   
                   return ValidateResponse.builder()
                           .valid(true)
                           .userId(claims.getSubject())
                           .username(claims.get("username", String.class))
                           .roles(roles)
                           .message("Token válido")
                           .build();
               } catch (JwtException e) {
                   log.warn("Token inválido durante validación: {}", e.getMessage());
                   return ValidateResponse.builder()
                           .valid(false)
                           .message("Token inválido: " + e.getMessage())
                           .build();
               }
           });
       }
   }
   EOF
   ```

3. Crea el controlador reactivo de autenticación:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/controller/AuthController.java << 'EOF'
   package com.banking.auth.controller;
   
   import com.banking.auth.model.*;
   import com.banking.auth.service.AuthService;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.ResponseEntity;
   import org.springframework.web.bind.annotation.*;
   import reactor.core.publisher.Mono;
   
   @Slf4j
   @RestController
   @RequestMapping("/auth")
   @RequiredArgsConstructor
   public class AuthController {
   
       private final AuthService authService;
   
       /**
        * POST /auth/login
        * Autentica al usuario y retorna un JWT.
        * Retorna 401 si las credenciales son inválidas.
        */
       @PostMapping("/login")
       public Mono<ResponseEntity<TokenResponse>> login(@RequestBody LoginRequest request) {
           return authService.login(request)
                   .map(tokenResponse -> ResponseEntity.ok(tokenResponse))
                   .defaultIfEmpty(ResponseEntity
                           .status(HttpStatus.UNAUTHORIZED)
                           .<TokenResponse>build());
       }
   
       /**
        * POST /auth/validate
        * Valida un token JWT y retorna los claims.
        * Usado internamente por otros microservicios.
        */
       @PostMapping("/validate")
       public Mono<ResponseEntity<ValidateResponse>> validate(@RequestBody ValidateRequest request) {
           return authService.validate(request.getToken())
                   .map(response -> {
                       if (response.isValid()) {
                           return ResponseEntity.ok(response);
                       } else {
                           return ResponseEntity
                                   .status(HttpStatus.UNAUTHORIZED)
                                   .body(response);
                       }
                   });
       }
   
       /**
        * GET /auth/health
        * Endpoint de health check simple para el auth-service.
        */
       @GetMapping("/health")
       public Mono<ResponseEntity<String>> health() {
           return Mono.just(ResponseEntity.ok("auth-service: UP"));
       }
   }
   EOF
   ```

**Salida Esperada:** Las tres clases se crean correctamente. IntelliJ IDEA no debe mostrar errores de compilación.

**Verificación:**

```bash
cd ~/banking-system
mvn compile -pl auth-service -q && echo "✅ Compilación exitosa" || echo "❌ Error de compilación"
```

---

### Paso 4: Configurar Spring Security y application.properties del auth-service

**Objetivo:** Configurar Spring Security WebFlux para permitir el acceso público a los endpoints de autenticación y crear el archivo de configuración con los parámetros JWT.

**Instrucciones:**

1. Crea la configuración de seguridad del `auth-service`:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/config/SecurityConfig.java << 'EOF'
   package com.banking.auth.config;
   
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
   import org.springframework.security.config.web.server.ServerHttpSecurity;
   import org.springframework.security.web.server.SecurityWebFilterChain;
   
   @Configuration
   @EnableWebFluxSecurity
   public class SecurityConfig {
   
       @Bean
       public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
           return http
                   .csrf(ServerHttpSecurity.CsrfSpec::disable)
                   .authorizeExchange(exchanges -> exchanges
                           // Los endpoints de auth son públicos
                           .pathMatchers("/auth/**").permitAll()
                           .pathMatchers("/actuator/**").permitAll()
                           // Cualquier otro endpoint requiere autenticación
                           .anyExchange().authenticated()
                   )
                   .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                   .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                   .build();
       }
   }
   EOF
   ```

2. Crea la clase principal de la aplicación:

   ```bash
   cat > ~/banking-system/auth-service/src/main/java/com/banking/auth/AuthServiceApplication.java << 'EOF'
   package com.banking.auth;
   
   import org.springframework.boot.SpringApplication;
   import org.springframework.boot.autoconfigure.SpringBootApplication;
   
   @SpringBootApplication
   public class AuthServiceApplication {
       public static void main(String[] args) {
           SpringApplication.run(AuthServiceApplication.class, args);
       }
   }
   EOF
   ```

3. Crea el archivo `application.properties` del `auth-service`:

   ```bash
   cat > ~/banking-system/auth-service/src/main/resources/application.properties << 'EOF'
   # =====================================================
   # auth-service - Configuración principal
   # Puerto: 8083 (ver README del proyecto raíz)
   # =====================================================
   spring.application.name=auth-service
   server.port=8083
   
   # =====================================================
   # Configuración JWT
   # ADVERTENCIA DE SEGURIDAD: En producción, el secreto
   # JWT DEBE gestionarse con variables de entorno o
   # un vault (HashiCorp Vault, AWS Secrets Manager).
   # NUNCA commitear secretos al repositorio de código.
   # =====================================================
   # Clave de mínimo 256 bits (32 caracteres) para HS256
   jwt.secret=banking-system-super-secret-key-for-jwt-signing-minimum-256-bits
   jwt.expiration-ms=3600000
   
   # =====================================================
   # Actuator - Health checks y métricas
   # =====================================================
   management.endpoints.web.exposure.include=health,info,metrics
   management.endpoint.health.show-details=always
   
   # =====================================================
   # Logging
   # =====================================================
   logging.level.com.banking.auth=DEBUG
   logging.level.org.springframework.security=INFO
   EOF
   ```

**Salida Esperada:** Los archivos de configuración se crean correctamente.

**Verificación:**

```bash
# Construir y ejecutar el auth-service para verificar arranque
cd ~/banking-system
mvn spring-boot:run -pl auth-service &
AUTH_PID=$!
sleep 15

# Verificar que el servicio arrancó correctamente
curl -s http://localhost:8083/auth/health
echo ""

# Detener el servicio
kill $AUTH_PID 2>/dev/null
wait $AUTH_PID 2>/dev/null
echo "✅ auth-service verificado y detenido"
```

---

### Paso 5: Implementar el Filtro JWT Global en el API Gateway

**Objetivo:** Crear un filtro global reactivo (`GlobalFilter`) en el API Gateway que intercepte todas las solicitudes entrantes, valide el token JWT del header `Authorization`, y propague los claims del usuario como headers internos hacia los microservicios downstream.

**Instrucciones:**

1. Agrega las dependencias necesarias al `pom.xml` del `api-gateway`:

   ```bash
   # Agrega estas dependencias en el pom.xml del api-gateway
   # Abre el archivo y agrega dentro de <dependencies>
   cat >> /dev/null << 'INSTRUCTION'
   Agrega en ~/banking-system/api-gateway/pom.xml dentro de <dependencies>:
   
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-api</artifactId>
   </dependency>
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-impl</artifactId>
   </dependency>
   <dependency>
       <groupId>io.jsonwebtoken</groupId>
       <artifactId>jjwt-jackson</artifactId>
   </dependency>
   INSTRUCTION
   
   # Usar sed para insertar las dependencias antes del cierre de </dependencies>
   sed -i 's|</dependencies>|    <dependency>\n            <groupId>io.jsonwebtoken</groupId>\n            <artifactId>jjwt-api</artifactId>\n        </dependency>\n        <dependency>\n            <groupId>io.jsonwebtoken</groupId>\n            <artifactId>jjwt-impl</artifactId>\n        </dependency>\n        <dependency>\n            <groupId>io.jsonwebtoken</groupId>\n            <artifactId>jjwt-jackson</artifactId>\n        </dependency>\n    </dependencies>|' ~/banking-system/api-gateway/pom.xml
   ```

   > **Nota:** Si el comando `sed` anterior no funciona correctamente en tu sistema, abre el archivo `~/banking-system/api-gateway/pom.xml` en IntelliJ IDEA y agrega manualmente las tres dependencias de JJWT dentro de la sección `<dependencies>`.

2. Crea la utilidad JWT compartida en el Gateway (copia de `JwtUtil` adaptada):

   ```bash
   mkdir -p ~/banking-system/api-gateway/src/main/java/com/banking/gateway/{filter,config,util}
   
   cat > ~/banking-system/api-gateway/src/main/java/com/banking/gateway/util/JwtUtil.java << 'EOF'
   package com.banking.gateway.util;
   
   import io.jsonwebtoken.Claims;
   import io.jsonwebtoken.JwtException;
   import io.jsonwebtoken.Jwts;
   import io.jsonwebtoken.security.Keys;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.stereotype.Component;
   
   import javax.crypto.SecretKey;
   import java.nio.charset.StandardCharsets;
   import java.util.List;
   
   @Slf4j
   @Component
   public class JwtUtil {
   
       private final SecretKey secretKey;
   
       public JwtUtil(@Value("${jwt.secret}") String secret) {
           this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
       }
   
       public boolean isTokenValid(String token) {
           try {
               extractClaims(token);
               return true;
           } catch (JwtException | IllegalArgumentException e) {
               log.warn("Token inválido en Gateway: {}", e.getMessage());
               return false;
           }
       }
   
       public Claims extractClaims(String token) {
           return Jwts.parser()
                   .verifyWith(secretKey)
                   .build()
                   .parseSignedClaims(token)
                   .getPayload();
       }
   
       public String extractUserId(String token) {
           return extractClaims(token).getSubject();
       }
   
       @SuppressWarnings("unchecked")
       public String extractRolesAsString(String token) {
           List<String> roles = extractClaims(token).get("roles", List.class);
           return roles != null ? String.join(",", roles) : "";
       }
   }
   EOF
   ```

3. Crea el filtro JWT global del Gateway:

   ```bash
   cat > ~/banking-system/api-gateway/src/main/java/com/banking/gateway/filter/JwtAuthenticationFilter.java << 'EOF'
   package com.banking.gateway.filter;
   
   import com.banking.gateway.util.JwtUtil;
   import com.fasterxml.jackson.databind.ObjectMapper;
   import lombok.RequiredArgsConstructor;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.cloud.gateway.filter.GatewayFilterChain;
   import org.springframework.cloud.gateway.filter.GlobalFilter;
   import org.springframework.core.Ordered;
   import org.springframework.core.io.buffer.DataBuffer;
   import org.springframework.http.HttpHeaders;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.MediaType;
   import org.springframework.http.server.reactive.ServerHttpRequest;
   import org.springframework.http.server.reactive.ServerHttpResponse;
   import org.springframework.stereotype.Component;
   import org.springframework.web.server.ServerWebExchange;
   import reactor.core.publisher.Mono;
   
   import java.nio.charset.StandardCharsets;
   import java.util.List;
   import java.util.Map;
   import java.util.UUID;
   
   @Slf4j
   @Component
   @RequiredArgsConstructor
   public class JwtAuthenticationFilter implements GlobalFilter, Ordered {
   
       private final JwtUtil jwtUtil;
       private final ObjectMapper objectMapper;
   
       // Rutas que no requieren autenticación
       private static final List<String> PUBLIC_PATHS = List.of(
               "/auth/login",
               "/auth/validate",
               "/auth/health",
               "/actuator"
       );
   
       @Override
       public int getOrder() {
           // Orden -1 para ejecutarse antes que otros filtros
           return -1;
       }
   
       @Override
       public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
           ServerHttpRequest request = exchange.getRequest();
           String path = request.getPath().value();
           String traceId = UUID.randomUUID().toString().substring(0, 8);
   
           log.debug("[{}] Procesando request: {} {}", traceId, request.getMethod(), path);
   
           // Verificar si la ruta es pública
           if (isPublicPath(path)) {
               log.debug("[{}] Ruta pública, omitiendo validación JWT: {}", traceId, path);
               return chain.filter(exchange.mutate()
                       .request(addTraceHeader(request, traceId))
                       .build());
           }
   
           // Extraer el token del header Authorization
           String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
   
           if (authHeader == null || !authHeader.startsWith("Bearer ")) {
               log.warn("[{}] Request sin token JWT para ruta protegida: {}", traceId, path);
               return writeErrorResponse(exchange.getResponse(),
                       HttpStatus.UNAUTHORIZED,
                       "Token de autenticación requerido",
                       traceId);
           }
   
           String token = authHeader.substring(7); // Remover "Bearer "
   
           // Validar el token
           if (!jwtUtil.isTokenValid(token)) {
               log.warn("[{}] Token JWT inválido o expirado para ruta: {}", traceId, path);
               return writeErrorResponse(exchange.getResponse(),
                       HttpStatus.UNAUTHORIZED,
                       "Token inválido o expirado",
                       traceId);
           }
   
           // Extraer claims y propagar como headers internos
           String userId = jwtUtil.extractUserId(token);
           String roles = jwtUtil.extractRolesAsString(token);
   
           log.info("[{}] Token válido para userId: {}, roles: {}, ruta: {}",
                   traceId, userId, roles, path);
   
           // Construir request mutado con headers internos de seguridad
           ServerHttpRequest mutatedRequest = request.mutate()
                   .header("X-User-Id", userId)
                   .header("X-User-Roles", roles)
                   .header("X-Trace-Id", traceId)
                   // Remover el header Authorization original para evitar
                   // que llegue a los microservicios internos
                   .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION))
                   .build();
   
           return chain.filter(exchange.mutate().request(mutatedRequest).build());
       }
   
       private boolean isPublicPath(String path) {
           return PUBLIC_PATHS.stream().anyMatch(path::startsWith);
       }
   
       private ServerHttpRequest addTraceHeader(ServerHttpRequest request, String traceId) {
           return request.mutate()
                   .header("X-Trace-Id", traceId)
                   .build();
       }
   
       private Mono<Void> writeErrorResponse(ServerHttpResponse response,
                                              HttpStatus status,
                                              String message,
                                              String traceId) {
           response.setStatusCode(status);
           response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
   
           Map<String, Object> errorBody = Map.of(
                   "status", status.value(),
                   "error", status.getReasonPhrase(),
                   "message", message,
                   "traceId", traceId
           );
   
           try {
               byte[] bytes = objectMapper.writeValueAsBytes(errorBody);
               DataBuffer buffer = response.bufferFactory().wrap(bytes);
               return response.writeWith(Mono.just(buffer));
           } catch (Exception e) {
               log.error("Error al escribir respuesta de error", e);
               return response.setComplete();
           }
       }
   }
   EOF
   ```

4. Agrega la configuración del Bean `ObjectMapper` si no existe en el Gateway:

   ```bash
   cat > ~/banking-system/api-gateway/src/main/java/com/banking/gateway/config/GatewayConfig.java << 'EOF'
   package com.banking.gateway.config;
   
   import com.fasterxml.jackson.databind.ObjectMapper;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   
   @Configuration
   public class GatewayConfig {
   
       @Bean
       public ObjectMapper objectMapper() {
           return new ObjectMapper();
       }
   }
   EOF
   ```

5. Actualiza el `application.properties` del `api-gateway` para agregar la configuración JWT y la ruta al `auth-service`:

   ```bash
   # Agregar al final del application.properties del api-gateway
   cat >> ~/banking-system/api-gateway/src/main/resources/application.properties << 'EOF'
   
   # =====================================================
   # Configuración JWT (debe coincidir con auth-service)
   # =====================================================
   jwt.secret=banking-system-super-secret-key-for-jwt-signing-minimum-256-bits
   
   # =====================================================
   # Ruta hacia auth-service
   # =====================================================
   spring.cloud.gateway.routes[2].id=auth-service
   spring.cloud.gateway.routes[2].uri=http://localhost:8083
   spring.cloud.gateway.routes[2].predicates[0]=Path=/auth/**
   EOF
   ```

**Salida Esperada:** El filtro JWT global está implementado y configurado en el Gateway.

**Verificación:**

```bash
cd ~/banking-system
mvn compile -pl api-gateway -q && echo "✅ api-gateway compilado exitosamente" || echo "❌ Error de compilación en api-gateway"
```

---

### Paso 6: Configurar Spring Security WebFlux en accounts-service y transactions-service

**Objetivo:** Agregar Spring Security WebFlux a los microservicios de cuentas y transacciones, configurando `SecurityWebFilterChain` para autorizar endpoints por roles basándose en los headers propagados por el Gateway.

**Instrucciones:**

1. Agrega la dependencia de Spring Security al `pom.xml` de `accounts-service` (y repite para `transactions-service`):

   ```bash
   # Para accounts-service - agregar Spring Security
   # Abre ~/banking-system/accounts-service/pom.xml y agrega dentro de <dependencies>:
   # <dependency>
   #     <groupId>org.springframework.boot</groupId>
   #     <artifactId>spring-boot-starter-security</artifactId>
   # </dependency>
   
   # Automatizado con sed:
   for service in accounts-service transactions-service; do
       if ! grep -q "spring-boot-starter-security" ~/banking-system/$service/pom.xml; then
           sed -i 's|</dependencies>|    <dependency>\n            <groupId>org.springframework.boot</groupId>\n            <artifactId>spring-boot-starter-security</artifactId>\n        </dependency>\n    </dependencies>|' ~/banking-system/$service/pom.xml
           echo "✅ Spring Security agregado a $service"
       else
           echo "ℹ️  Spring Security ya existe en $service"
       fi
   done
   ```

2. Crea el filtro de autenticación basado en headers internos para `accounts-service`:

   ```bash
   mkdir -p ~/banking-system/accounts-service/src/main/java/com/banking/accounts/{security,config}
   
   cat > ~/banking-system/accounts-service/src/main/java/com/banking/accounts/security/HeaderAuthenticationFilter.java << 'EOF'
   package com.banking.accounts.security;
   
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.http.server.reactive.ServerHttpRequest;
   import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
   import org.springframework.security.core.authority.SimpleGrantedAuthority;
   import org.springframework.security.core.context.ReactiveSecurityContextHolder;
   import org.springframework.web.server.ServerWebExchange;
   import org.springframework.web.server.WebFilter;
   import org.springframework.web.server.WebFilterChain;
   import reactor.core.publisher.Mono;
   
   import java.util.Arrays;
   import java.util.List;
   import java.util.stream.Collectors;
   
   /**
    * Filtro que lee los headers internos propagados por el API Gateway
    * (X-User-Id, X-User-Roles) y construye el contexto de seguridad reactivo.
    *
    * Este patrón (seguridad en capas) asume que solo el Gateway puede
    * establecer estos headers. En producción, se debe verificar que las
    * solicitudes provengan únicamente del Gateway (por IP, mTLS, etc.).
    */
   @Slf4j
   public class HeaderAuthenticationFilter implements WebFilter {
   
       private static final String USER_ID_HEADER = "X-User-Id";
       private static final String USER_ROLES_HEADER = "X-User-Roles";
   
       @Override
       public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
           ServerHttpRequest request = exchange.getRequest();
           String userId = request.getHeaders().getFirst(USER_ID_HEADER);
           String rolesHeader = request.getHeaders().getFirst(USER_ROLES_HEADER);
           String traceId = request.getHeaders().getFirst("X-Trace-Id");
   
           if (userId != null && rolesHeader != null) {
               log.debug("[{}] Autenticando request para userId: {} con roles: {}",
                       traceId, userId, rolesHeader);
   
               List<SimpleGrantedAuthority> authorities = Arrays.stream(rolesHeader.split(","))
                       .map(String::trim)
                       .filter(role -> !role.isEmpty())
                       .map(SimpleGrantedAuthority::new)
                       .collect(Collectors.toList());
   
               UsernamePasswordAuthenticationToken authentication =
                       new UsernamePasswordAuthenticationToken(userId, null, authorities);
   
               return chain.filter(exchange)
                       .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
           }
   
           log.debug("[{}] Request sin headers de autenticación: {}",
                   traceId, request.getPath().value());
           return chain.filter(exchange);
       }
   }
   EOF
   ```

3. Crea la configuración de seguridad del `accounts-service`:

   ```bash
   cat > ~/banking-system/accounts-service/src/main/java/com/banking/accounts/config/SecurityConfig.java << 'EOF'
   package com.banking.accounts.config;
   
   import com.banking.accounts.security.HeaderAuthenticationFilter;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.http.HttpMethod;
   import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
   import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
   import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
   import org.springframework.security.config.web.server.ServerHttpSecurity;
   import org.springframework.security.web.server.SecurityWebFilterChain;
   import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
   
   @Configuration
   @EnableWebFluxSecurity
   @EnableReactiveMethodSecurity
   public class SecurityConfig {
   
       @Bean
       public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
           return http
                   .csrf(ServerHttpSecurity.CsrfSpec::disable)
                   // Sin estado - no usar sesiones
                   .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                   .authorizeExchange(exchanges -> exchanges
                           // Actuator es público
                           .pathMatchers("/actuator/**").permitAll()
                           // GET /accounts es accesible para ROLE_USER y ROLE_ADMIN
                           .pathMatchers(HttpMethod.GET, "/accounts/**").hasAnyRole("USER", "ADMIN")
                           // POST /accounts es accesible para ROLE_USER y ROLE_ADMIN
                           .pathMatchers(HttpMethod.POST, "/accounts/**").hasAnyRole("USER", "ADMIN")
                           // DELETE /accounts solo para ROLE_ADMIN
                           .pathMatchers(HttpMethod.DELETE, "/accounts/**").hasRole("ADMIN")
                           // PUT /accounts solo para ROLE_ADMIN
                           .pathMatchers(HttpMethod.PUT, "/accounts/**").hasRole("ADMIN")
                           // Cualquier otro endpoint requiere autenticación
                           .anyExchange().authenticated()
                   )
                   .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                   .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                   // Agregar nuestro filtro de autenticación por headers
                   .addFilterAt(new HeaderAuthenticationFilter(),
                           SecurityWebFiltersOrder.AUTHENTICATION)
                   .build();
       }
   }
   EOF
   ```

4. Repite el mismo proceso para `transactions-service`:

   ```bash
   mkdir -p ~/banking-system/transactions-service/src/main/java/com/banking/transactions/{security,config}
   
   # Copiar y adaptar el filtro para transactions-service
   sed 's/com.banking.accounts/com.banking.transactions/g' \
       ~/banking-system/accounts-service/src/main/java/com/banking/accounts/security/HeaderAuthenticationFilter.java \
       > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/security/HeaderAuthenticationFilter.java
   
   # Crear SecurityConfig para transactions-service
   cat > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/config/SecurityConfig.java << 'EOF'
   package com.banking.transactions.config;
   
   import com.banking.transactions.security.HeaderAuthenticationFilter;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.http.HttpMethod;
   import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
   import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
   import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
   import org.springframework.security.config.web.server.ServerHttpSecurity;
   import org.springframework.security.web.server.SecurityWebFilterChain;
   import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
   
   @Configuration
   @EnableWebFluxSecurity
   @EnableReactiveMethodSecurity
   public class SecurityConfig {
   
       @Bean
       public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
           return http
                   .csrf(ServerHttpSecurity.CsrfSpec::disable)
                   .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                   .authorizeExchange(exchanges -> exchanges
                           .pathMatchers("/actuator/**").permitAll()
                           // GET /transactions accesible para ROLE_USER y ROLE_ADMIN
                           .pathMatchers(HttpMethod.GET, "/transactions/**").hasAnyRole("USER", "ADMIN")
                           // POST /transactions accesible para ROLE_USER y ROLE_ADMIN
                           .pathMatchers(HttpMethod.POST, "/transactions/**").hasAnyRole("USER", "ADMIN")
                           // DELETE solo para ROLE_ADMIN
                           .pathMatchers(HttpMethod.DELETE, "/transactions/**").hasRole("ADMIN")
                           .anyExchange().authenticated()
                   )
                   .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                   .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                   .addFilterAt(new HeaderAuthenticationFilter(),
                           SecurityWebFiltersOrder.AUTHENTICATION)
                   .build();
       }
   }
   EOF
   ```

**Salida Esperada:** Ambos microservicios tienen configurada la cadena de seguridad reactiva con autorización por roles.

**Verificación:**

```bash
cd ~/banking-system
mvn compile -pl accounts-service,transactions-service -q && \
    echo "✅ accounts-service y transactions-service compilados exitosamente" || \
    echo "❌ Error de compilación"
```

---

### Paso 7: Implementar Manejadores de Excepciones de Seguridad

**Objetivo:** Configurar manejadores de errores de seguridad reactivos que retornen respuestas JSON apropiadas en lugar de páginas HTML por defecto para los errores 401 (no autenticado) y 403 (acceso denegado).

**Instrucciones:**

1. Crea el manejador de errores de seguridad para `accounts-service`:

   ```bash
   cat > ~/banking-system/accounts-service/src/main/java/com/banking/accounts/security/SecurityExceptionHandler.java << 'EOF'
   package com.banking.accounts.security;
   
   import com.fasterxml.jackson.databind.ObjectMapper;
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.core.io.buffer.DataBuffer;
   import org.springframework.http.HttpStatus;
   import org.springframework.http.MediaType;
   import org.springframework.security.access.AccessDeniedException;
   import org.springframework.security.core.AuthenticationException;
   import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
   import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
   import org.springframework.web.server.ServerWebExchange;
   import reactor.core.publisher.Mono;
   
   import java.util.Map;
   
   /**
    * Manejador de excepciones de seguridad reactivo.
    * Retorna respuestas JSON en lugar de páginas HTML por defecto.
    *
    * - AuthenticationEntryPoint: maneja 401 (no autenticado)
    * - AccessDeniedHandler: maneja 403 (acceso denegado)
    */
   @Slf4j
   public class SecurityExceptionHandler
           implements ServerAuthenticationEntryPoint, ServerAccessDeniedHandler {
   
       private final ObjectMapper objectMapper = new ObjectMapper();
   
       @Override
       public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
           log.warn("Acceso no autenticado a: {} - {}",
                   exchange.getRequest().getPath().value(), ex.getMessage());
           return writeErrorResponse(exchange, HttpStatus.UNAUTHORIZED,
                   "Autenticación requerida: " + ex.getMessage());
       }
   
       @Override
       public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException ex) {
           log.warn("Acceso denegado a: {} - {}",
                   exchange.getRequest().getPath().value(), ex.getMessage());
           return writeErrorResponse(exchange, HttpStatus.FORBIDDEN,
                   "Acceso denegado: permisos insuficientes");
       }
   
       private Mono<Void> writeErrorResponse(ServerWebExchange exchange,
                                              HttpStatus status,
                                              String message) {
           var response = exchange.getResponse();
           response.setStatusCode(status);
           response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
   
           Map<String, Object> body = Map.of(
                   "status", status.value(),
                   "error", status.getReasonPhrase(),
                   "message", message,
                   "path", exchange.getRequest().getPath().value()
           );
   
           try {
               byte[] bytes = objectMapper.writeValueAsBytes(body);
               DataBuffer buffer = response.bufferFactory().wrap(bytes);
               return response.writeWith(Mono.just(buffer));
           } catch (Exception e) {
               return response.setComplete();
           }
       }
   }
   EOF
   ```

2. Actualiza `SecurityConfig` en `accounts-service` para usar el manejador de excepciones:

   ```bash
   cat > ~/banking-system/accounts-service/src/main/java/com/banking/accounts/config/SecurityConfig.java << 'EOF'
   package com.banking.accounts.config;
   
   import com.banking.accounts.security.HeaderAuthenticationFilter;
   import com.banking.accounts.security.SecurityExceptionHandler;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.http.HttpMethod;
   import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
   import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
   import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
   import org.springframework.security.config.web.server.ServerHttpSecurity;
   import org.springframework.security.web.server.SecurityWebFilterChain;
   import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
   
   @Configuration
   @EnableWebFluxSecurity
   @EnableReactiveMethodSecurity
   public class SecurityConfig {
   
       @Bean
       public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
           SecurityExceptionHandler exceptionHandler = new SecurityExceptionHandler();
   
           return http
                   .csrf(ServerHttpSecurity.CsrfSpec::disable)
                   .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                   .authorizeExchange(exchanges -> exchanges
                           .pathMatchers("/actuator/**").permitAll()
                           .pathMatchers(HttpMethod.GET, "/accounts/**").hasAnyRole("USER", "ADMIN")
                           .pathMatchers(HttpMethod.POST, "/accounts/**").hasAnyRole("USER", "ADMIN")
                           .pathMatchers(HttpMethod.DELETE, "/accounts/**").hasRole("ADMIN")
                           .pathMatchers(HttpMethod.PUT, "/accounts/**").hasRole("ADMIN")
                           .anyExchange().authenticated()
                   )
                   .exceptionHandling(handling -> handling
                           .authenticationEntryPoint(exceptionHandler)
                           .accessDeniedHandler(exceptionHandler)
                   )
                   .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                   .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                   .addFilterAt(new HeaderAuthenticationFilter(),
                           SecurityWebFiltersOrder.AUTHENTICATION)
                   .build();
       }
   }
   EOF
   ```

3. Aplica el mismo patrón al `transactions-service`:

   ```bash
   # Copiar y adaptar SecurityExceptionHandler para transactions-service
   sed 's/com.banking.accounts/com.banking.transactions/g' \
       ~/banking-system/accounts-service/src/main/java/com/banking/accounts/security/SecurityExceptionHandler.java \
       > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/security/SecurityExceptionHandler.java
   
   # Actualizar SecurityConfig de transactions-service para incluir el manejador
   cat > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/config/SecurityConfig.java << 'EOF'
   package com.banking.transactions.config;
   
   import com.banking.transactions.security.HeaderAuthenticationFilter;
   import com.banking.transactions.security.SecurityExceptionHandler;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.http.HttpMethod;
   import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
   import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
   import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
   import org.springframework.security.config.web.server.ServerHttpSecurity;
   import org.springframework.security.web.server.SecurityWebFilterChain;
   import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
   
   @Configuration
   @EnableWebFluxSecurity
   @EnableReactiveMethodSecurity
   public class SecurityConfig {
   
       @Bean
       public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
           SecurityExceptionHandler exceptionHandler = new SecurityExceptionHandler();
   
           return http
                   .csrf(ServerHttpSecurity.CsrfSpec::disable)
                   .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                   .authorizeExchange(exchanges -> exchanges
                           .pathMatchers("/actuator/**").permitAll()
                           .pathMatchers(HttpMethod.GET, "/transactions/**").hasAnyRole("USER", "ADMIN")
                           .pathMatchers(HttpMethod.POST, "/transactions/**").hasAnyRole("USER", "ADMIN")
                           .pathMatchers(HttpMethod.DELETE, "/transactions/**").hasRole("ADMIN")
                           .anyExchange().authenticated()
                   )
                   .exceptionHandling(handling -> handling
                           .authenticationEntryPoint(exceptionHandler)
                           .accessDeniedHandler(exceptionHandler)
                   )
                   .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                   .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                   .addFilterAt(new HeaderAuthenticationFilter(),
                           SecurityWebFiltersOrder.AUTHENTICATION)
                   .build();
       }
   }
   EOF
   ```

**Salida Esperada:** Los manejadores de excepciones están configurados en ambos microservicios.

**Verificación:**

```bash
cd ~/banking-system
mvn compile -pl accounts-service,transactions-service -q && \
    echo "✅ Compilación con SecurityExceptionHandler exitosa" || \
    echo "❌ Error de compilación"
```

---

### Paso 8: Implementar Propagación de JWT en Llamadas WebClient Inter-Servicios

**Objetivo:** Configurar el `WebClient` en `transactions-service` para propagar los headers de autenticación (`X-User-Id`, `X-User-Roles`, `X-Trace-Id`) cuando realiza llamadas a `accounts-service`, manteniendo la cadena de autenticación en comunicación inter-servicios.

**Instrucciones:**

1. Crea o actualiza la configuración del `WebClient` en `transactions-service` para propagar headers de seguridad:

   ```bash
   cat > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/config/WebClientConfig.java << 'EOF'
   package com.banking.transactions.config;
   
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.beans.factory.annotation.Value;
   import org.springframework.context.annotation.Bean;
   import org.springframework.context.annotation.Configuration;
   import org.springframework.web.reactive.function.client.ClientRequest;
   import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
   import org.springframework.web.reactive.function.client.WebClient;
   import org.springframework.web.server.ServerWebExchange;
   import reactor.core.publisher.Mono;
   
   @Slf4j
   @Configuration
   public class WebClientConfig {
   
       @Value("${services.accounts.url:http://localhost:8081}")
       private String accountsServiceUrl;
   
       /**
        * WebClient configurado para propagar headers de seguridad
        * en llamadas al accounts-service.
        *
        * Propaga: X-User-Id, X-User-Roles, X-Trace-Id
        */
       @Bean
       public WebClient accountsWebClient() {
           return WebClient.builder()
                   .baseUrl(accountsServiceUrl)
                   .filter(propagateSecurityHeaders())
                   .filter(logRequestResponse())
                   .build();
       }
   
       /**
        * Filtro que propaga headers de seguridad desde el contexto reactivo.
        * Lee los headers del request entrante y los reenvía al request saliente.
        */
       private ExchangeFilterFunction propagateSecurityHeaders() {
           return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
               // Obtener el exchange del contexto reactivo
               return Mono.deferContextual(ctx -> {
                   ClientRequest.Builder requestBuilder = ClientRequest.from(clientRequest);
   
                   // Propagar X-Trace-Id si está disponible en el contexto
                   ctx.getOrEmpty("X-Trace-Id").ifPresent(traceId ->
                           requestBuilder.header("X-Trace-Id", traceId.toString()));
   
                   ctx.getOrEmpty("X-User-Id").ifPresent(userId ->
                           requestBuilder.header("X-User-Id", userId.toString()));
   
                   ctx.getOrEmpty("X-User-Roles").ifPresent(roles ->
                           requestBuilder.header("X-User-Roles", roles.toString()));
   
                   return Mono.just(requestBuilder.build());
               });
           });
       }
   
       /**
        * Filtro de logging para depuración de llamadas inter-servicios.
        */
       private ExchangeFilterFunction logRequestResponse() {
           return ExchangeFilterFunction.ofRequestProcessor(request -> {
               log.debug("WebClient → {} {}", request.method(), request.url());
               return Mono.just(request);
           });
       }
   }
   EOF
   ```

2. Crea un interceptor de contexto reactivo en `transactions-service` para poblar el contexto con los headers del request entrante:

   ```bash
   cat > ~/banking-system/transactions-service/src/main/java/com/banking/transactions/security/ReactiveContextFilter.java << 'EOF'
   package com.banking.transactions.security;
   
   import lombok.extern.slf4j.Slf4j;
   import org.springframework.stereotype.Component;
   import org.springframework.web.server.ServerWebExchange;
   import org.springframework.web.server.WebFilter;
   import org.springframework.web.server.WebFilterChain;
   import reactor.core.publisher.Mono;
   import reactor.util.context.Context;
   
   /**
    * Filtro que extrae los headers de seguridad del request entrante
    * y los almacena en el contexto reactivo de Reactor.
    *
    * Esto permite que el WebClient acceda a estos valores
    * durante la propagación de headers en llamadas inter-servicios.
    */
   @Slf4j
   @Component
   public class ReactiveContextFilter implements WebFilter {
   
       @Override
       public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
           var headers = exchange.getRequest().getHeaders();
   
           String traceId = headers.getFirst("X-Trace-Id");
           String userId = headers.getFirst("X-User-Id");
           String userRoles = headers.getFirst("X-User-Roles");
   
           Context context = Context.empty();
   
           if (traceId != null) context = context.put("X-Trace-Id", traceId);
           if (userId != null) context = context.put("X-User-Id", userId);
           if (userRoles != null) context = context.put("X-User-Roles", userRoles);
   
           final Context finalContext = context;
   
           return chain.filter(exchange)
                   .contextWrite(finalContext);
       }
   }
   EOF
   ```

**Salida Esperada:** El `WebClient` está configurado para propagar automáticamente los headers de seguridad en llamadas inter-servicios.

**Verificación:**

```bash
cd ~/banking-system
mvn compile -pl transactions-service -q && \
    echo "✅ transactions-service con propagación de JWT compilado exitosamente" || \
    echo "❌ Error de compilación"
```

---

### Paso 9: Construir y Arrancar Todos los Servicios

**Objetivo:** Compilar el proyecto completo, arrancar todos los microservicios y verificar que el sistema integrado funciona correctamente antes de las pruebas de seguridad.

**Instrucciones:**

1. Construye el proyecto completo:

   ```bash
   cd ~/banking-system
   mvn clean package -DskipTests -q
   echo "Build completo: $?"
   ```

2. Asegúrate de que los contenedores de infraestructura están corriendo:

   ```bash
   docker-compose up -d postgres-accounts postgres-transactions
   sleep 5
   docker ps --format "table {{.Names}}\t{{.Status}}"
   ```

3. Inicia los servicios en el orden correcto (auth-service primero, luego los demás):

   ```bash
   # Abrir cuatro terminales o usar tmux
   # Terminal 1: auth-service (puerto 8083)
   cd ~/banking-system && mvn spring-boot:run -pl auth-service > /tmp/auth-service.log 2>&1 &
   AUTH_PID=$!
   echo "auth-service PID: $AUTH_PID"
   sleep 12
   
   # Terminal 2: accounts-service (puerto 8081)
   cd ~/banking-system && mvn spring-boot:run -pl accounts-service > /tmp/accounts-service.log 2>&1 &
   ACCOUNTS_PID=$!
   echo "accounts-service PID: $ACCOUNTS_PID"
   sleep 12
   
   # Terminal 3: transactions-service (puerto 8082)
   cd ~/banking-system && mvn spring-boot:run -pl transactions-service > /tmp/transactions-service.log 2>&1 &
   TRANSACTIONS_PID=$!
   echo "transactions-service PID: $TRANSACTIONS_PID"
   sleep 12
   
   # Terminal 4: api-gateway (puerto 8080)
   cd ~/banking-system && mvn spring-boot:run -pl api-gateway > /tmp/api-gateway.log 2>&1 &
   GATEWAY_PID=$!
   echo "api-gateway PID: $GATEWAY_PID"
   sleep 15
   ```

4. Verifica que todos los servicios están activos:

   ```bash
   echo "=== Verificando health de todos los servicios ==="
   
   services=("8083/auth/health" "8081/actuator/health" "8082/actuator/health" "8080/actuator/health")
   names=("auth-service" "accounts-service" "transactions-service" "api-gateway")
   
   for i in "${!services[@]}"; do
       response=$(curl -s -o /dev/null -w "%{http_code}" http://localhost:${services[$i]})
       if [ "$response" = "200" ]; then
           echo "✅ ${names[$i]}: UP (HTTP $response)"
       else
           echo "❌ ${names[$i]}: DOWN (HTTP $response)"
           echo "   Ver logs: tail -50 /tmp/${names[$i]}.log"
       fi
   done
   ```

**Salida Esperada:**

```
=== Verificando health de todos los servicios ===
✅ auth-service: UP (HTTP 200)
✅ accounts-service: UP (HTTP 200)
✅ transactions-service: UP (HTTP 200)
✅ api-gateway: UP (HTTP 200)
```

**Verificación:**

```bash
# Verificar que el auth-service responde correctamente
curl -s http://localhost:8083/auth/health
# Debe retornar: auth-service: UP
```

---

### Paso 10: Pruebas de Seguridad con Postman y curl

**Objetivo:** Validar todos los escenarios de seguridad implementados: login exitoso, acceso con token válido, acceso sin token, token expirado/inválido y acceso con rol insuficiente.

**Instrucciones:**

1. **Escenario 1: Login exitoso con usuario admin**

   ```bash
   echo "=== ESCENARIO 1: Login exitoso (admin) ==="
   ADMIN_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username": "admin", "password": "password123"}' | \
       python3 -c "import sys,json; data=json.load(sys.stdin); print(data.get('token','ERROR'))")
   
   echo "Token obtenido: ${ADMIN_TOKEN:0:50}..."
   
   if [ "$ADMIN_TOKEN" != "ERROR" ] && [ -n "$ADMIN_TOKEN" ]; then
       echo "✅ Login exitoso - Token JWT obtenido"
   else
       echo "❌ Login fallido"
       curl -v -X POST http://localhost:8080/auth/login \
           -H "Content-Type: application/json" \
           -d '{"username": "admin", "password": "password123"}'
   fi
   ```

2. **Escenario 2: Login exitoso con usuario regular**

   ```bash
   echo "=== ESCENARIO 2: Login exitoso (user regular) ==="
   USER_TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username": "user", "password": "password123"}' | \
       python3 -c "import sys,json; data=json.load(sys.stdin); print(data.get('token','ERROR'))")
   
   echo "Token de usuario regular obtenido: ${USER_TOKEN:0:50}..."
   echo "✅ Login de usuario regular exitoso"
   ```

3. **Escenario 3: Acceso a endpoint protegido con token válido (ROLE_USER)**

   ```bash
   echo "=== ESCENARIO 3: Acceso con token válido (GET /accounts) ==="
   RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
       -H "Authorization: Bearer $USER_TOKEN" \
       http://localhost:8080/accounts)
   
   HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
   BODY=$(echo "$RESPONSE" | grep -v "HTTP_STATUS")
   
   echo "HTTP Status: $HTTP_STATUS"
   echo "Respuesta: $BODY"
   
   if [ "$HTTP_STATUS" = "200" ]; then
       echo "✅ Acceso con ROLE_USER a GET /accounts: PERMITIDO"
   else
       echo "❌ Error inesperado: HTTP $HTTP_STATUS"
   fi
   ```

4. **Escenario 4: Acceso sin token (debe retornar 401)**

   ```bash
   echo "=== ESCENARIO 4: Acceso sin token (debe ser 401) ==="
   RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" http://localhost:8080/accounts)
   HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
   BODY=$(echo "$RESPONSE" | grep -v "HTTP_STATUS")
   
   echo "HTTP Status: $HTTP_STATUS"
   echo "Respuesta: $BODY"
   
   if [ "$HTTP_STATUS" = "401" ]; then
       echo "✅ Sin token → 401 Unauthorized: CORRECTO"
   else
       echo "❌ Se esperaba 401, se obtuvo: $HTTP_STATUS"
   fi
   ```

5. **Escenario 5: Token inválido (debe retornar 401)**

   ```bash
   echo "=== ESCENARIO 5: Token inválido (debe ser 401) ==="
   RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
       -H "Authorization: Bearer esto.no.es.un.jwt.valido" \
       http://localhost:8080/accounts)
   HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
   
   echo "HTTP Status: $HTTP_STATUS"
   
   if [ "$HTTP_STATUS" = "401" ]; then
       echo "✅ Token inválido → 401 Unauthorized: CORRECTO"
   else
       echo "❌ Se esperaba 401, se obtuvo: $HTTP_STATUS"
   fi
   ```

6. **Escenario 6: Acceso con rol insuficiente (DELETE con ROLE_USER, debe retornar 403)**

   ```bash
   echo "=== ESCENARIO 6: Rol insuficiente (DELETE /accounts con ROLE_USER, debe ser 403) ==="
   RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
       -X DELETE \
       -H "Authorization: Bearer $USER_TOKEN" \
       http://localhost:8080/accounts/1)
   HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
   BODY=$(echo "$RESPONSE" | grep -v "HTTP_STATUS")
   
   echo "HTTP Status: $HTTP_STATUS"
   echo "Respuesta: $BODY"
   
   if [ "$HTTP_STATUS" = "403" ]; then
       echo "✅ ROLE_USER intentando DELETE → 403 Forbidden: CORRECTO"
   else
       echo "❌ Se esperaba 403, se obtuvo: $HTTP_STATUS"
   fi
   ```

7. **Escenario 7: DELETE con ROLE_ADMIN (debe ser permitido)**

   ```bash
   echo "=== ESCENARIO 7: DELETE con ROLE_ADMIN (debe ser 200 o 404) ==="
   RESPONSE=$(curl -s -w "\nHTTP_STATUS:%{http_code}" \
       -X DELETE \
       -H "Authorization: Bearer $ADMIN_TOKEN" \
       http://localhost:8080/accounts/999)
   HTTP_STATUS=$(echo "$RESPONSE" | grep "HTTP_STATUS" | cut -d: -f2)
   
   echo "HTTP Status: $HTTP_STATUS"
   
   if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "404" ]; then
       echo "✅ ROLE_ADMIN intentando DELETE → $HTTP_STATUS (acceso permitido por seguridad): CORRECTO"
   else
       echo "❌ Se esperaba 200 o 404, se obtuvo: $HTTP_STATUS"
   fi
   ```

8. **Escenario 8: Validación de token via endpoint /auth/validate**

   ```bash
   echo "=== ESCENARIO 8: Validación de token ==="
   curl -s -X POST http://localhost:8080/auth/validate \
       -H "Content-Type: application/json" \
       -d "{\"token\": \"$ADMIN_TOKEN\"}" | python3 -m json.tool
   ```

**Salida Esperada:**

```
=== ESCENARIO 1: Login exitoso (admin) ===
Token obtenido: eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ1c3ItMDAxIi...
✅ Login exitoso - Token JWT obtenido

=== ESCENARIO 4: Acceso sin token (debe ser 401) ===
HTTP Status: 401
✅ Sin token → 401 Unauthorized: CORRECTO

=== ESCENARIO 6: Rol insuficiente (DELETE /accounts con ROLE_USER, debe ser 403) ===
HTTP Status: 403
✅ ROLE_USER intentando DELETE → 403 Forbidden: CORRECTO
```

**Verificación:**

```bash
# Resumen de pruebas
echo "=== RESUMEN DE PRUEBAS DE SEGURIDAD ==="
echo "Todos los escenarios deben mostrar ✅"
```

## Validación y Pruebas

### Criterios de Éxito

- [ ] El `auth-service` arranca en el puerto 8083 y responde en `/auth/health`
- [ ] `POST /auth/login` con credenciales válidas retorna un JWT con estructura correcta (header.payload.signature)
- [ ] `POST /auth/login` con credenciales inválidas retorna HTTP 401 sin detalles del error
- [ ] El API Gateway valida el JWT en el filtro global y retorna 401 para tokens ausentes o inválidos
- [ ] `GET /accounts` con token de `ROLE_USER` retorna HTTP 200
- [ ] `DELETE /accounts/{id}` con token de `ROLE_USER` retorna HTTP 403
- [ ] `DELETE /accounts/{id}` con token de `ROLE_ADMIN` retorna HTTP 200 o 404 (no 401 ni 403)
- [ ] Los errores de seguridad retornan JSON estructurado (no HTML)
- [ ] Los headers `X-User-Id` y `X-User-Roles` son propagados correctamente del Gateway a los microservicios
- [ ] El `X-Trace-Id` aparece en los logs de todos los servicios para la misma request

### Procedimiento de Pruebas

1. Verificar la estructura del token JWT en jwt.io:

   ```bash
   # Obtener un token y decodificar su payload
   TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username": "admin", "password": "password123"}' | \
       python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
   
   # Decodificar el payload (segunda parte del JWT)
   echo $TOKEN | cut -d. -f2 | base64 -d 2>/dev/null | python3 -m json.tool
   ```
   
   **Resultado Esperado:**
   ```json
   {
       "sub": "usr-001",
       "username": "admin",
       "roles": ["ROLE_USER", "ROLE_ADMIN"],
       "iat": 1700000000,
       "exp": 1700003600
   }
   ```

2. Verificar que el Gateway propaga los headers correctos:

   ```bash
   # Crear un endpoint de debug temporal en accounts-service (solo para pruebas)
   TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username": "admin", "password": "password123"}' | \
       python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
   
   # Verificar en los logs del accounts-service que X-User-Id llega correctamente
   grep "X-User-Id\|Autenticando request" /tmp/accounts-service.log | tail -5
   ```
   
   **Resultado Esperado:**
   ```
   Autenticando request para userId: usr-001 con roles: ROLE_USER,ROLE_ADMIN
   ```

3. Probar el flujo completo de transacciones con autenticación:

   ```bash
   TOKEN=$(curl -s -X POST http://localhost:8080/auth/login \
       -H "Content-Type: application/json" \
       -d '{"username": "user", "password": "password123"}' | \
       python3 -c "import sys,json; print(json.load(sys.stdin)['token'])")
   
   # Consultar transacciones con token válido
   curl -s -w "\nHTTP: %{http_code}\n" \
       -H "Authorization: Bearer $TOKEN" \
       http://localhost:8080/transactions
   ```
   
   **Resultado Esperado:** HTTP 200 con lista de transacciones (puede estar vacía)

4. Verificar el manejo de errores JSON:

   ```bash
   # Verificar que los errores de seguridad retornan JSON
   curl -s http://localhost:8080/accounts | python3 -m json.tool
   ```
   
   **Resultado Esperado:**
   ```json
   {
       "status": 401,
       "error": "Unauthorized",
       "message": "Token de autenticación requerido",
       "traceId": "a1b2c3d4"
   }
   ```

## Solución de Problemas

### Problema 1: Error "Illegal key size" al generar el token JWT

**Síntomas:**
- El `auth-service` lanza `WeakKeyException: The signing key's size is 128 bits which is not secure enough for the HS256 algorithm`
- El endpoint `/auth/login` retorna HTTP 500

**Causa:**
La clave secreta configurada en `application.properties` tiene menos de 256 bits (32 caracteres). JJWT 0.12.x aplica validación estricta de longitud de clave para HS256.

**Solución:**

```bash
# Verificar la longitud de la clave actual
grep "jwt.secret" ~/banking-system/auth-service/src/main/resources/application.properties

# La clave debe tener al menos 32 caracteres (256 bits)
# Contar caracteres:
echo -n "banking-system-super-secret-key-for-jwt-signing-minimum-256-bits" | wc -c
# Debe mostrar >= 32

# Si es menor, actualizar la clave:
sed -i 's/jwt.secret=.*/jwt.secret=banking-system-super-secret-key-for-jwt-signing-minimum-256-bits-2024/' \
    ~/banking-system/auth-service/src/main/resources/application.properties

# Asegurarse de que el api-gateway usa la misma clave
sed -i 's/jwt.secret=.*/jwt.secret=banking-system-super-secret-key-for-jwt-signing-minimum-256-bits-2024/' \
    ~/banking-system/api-gateway/src/main/resources/application.properties
```

---

### Problema 2: Todos los requests retornan 401 aunque el token sea válido

**Síntomas:**
- El login funciona y se obtiene un token
- Cualquier request con `Authorization: Bearer <token>` retorna 401
- Los logs del Gateway muestran "Token JWT inválido o expirado"

**Causa:**
Las claves secretas JWT del `auth-service` y del `api-gateway` no coinciden. El Gateway no puede verificar tokens generados por el `auth-service` si usan claves diferentes.

**Solución:**

```bash
# Verificar que ambas claves son idénticas
echo "=== Clave en auth-service ==="
grep "jwt.secret" ~/banking-system/auth-service/src/main/resources/application.properties

echo "=== Clave en api-gateway ==="
grep "jwt.secret" ~/banking-system/api-gateway/src/main/resources/application.properties

# Si son diferentes, sincronizarlas:
SECRET=$(grep "jwt.secret" ~/banking-system/auth-service/src/main/resources/application.properties | cut -d= -f2)
sed -i "s/jwt.secret=.*/jwt.secret=$SECRET/" \
    ~/banking-system/api-gateway/src/main/resources/application.properties

echo "✅ Claves sincronizadas. Reinicia ambos servicios."
```

---

### Problema 3: Spring Security bloquea el endpoint /auth/login con 401

**Síntomas:**
- `POST /auth/login` retorna HTTP 401 antes de llegar al controlador
- Los logs muestran que Spring Security intercepta la request

**Causa:**
La configuración de `SecurityWebFilterChain` en el `auth-service` no permite acceso público a `/auth/**`, o hay un auto-configure de Spring Security que sobreescribe la configuración personalizada.

**Solución:**

```bash
# Verificar la configuración de seguridad del auth-service
cat ~/banking-system/auth-service/src/main/java/com/banking/auth/config/SecurityConfig.java | grep -A 10 "authorizeExchange"

# Verificar que la anotación @EnableWebFluxSecurity está presente
grep "@EnableWebFluxSecurity" ~/banking-system/auth-service/src/main/java/com/banking/auth/config/SecurityConfig.java

# Si el problema persiste, agregar explícitamente al application.properties:
echo "spring.security.user.password=disabled-default-user" >> \
    ~/banking-system/auth-service/src/main/resources/application.properties

# Reiniciar el auth-service
kill $(lsof -ti:8083) 2>/dev/null
sleep 2
cd ~/banking-system && mvn spring-boot:run -pl auth-service > /tmp/auth-service.log 2>&1 &
sleep 12
curl -s http://localhost:8083/auth/health
```

---

### Problema 4: Error 403 al acceder con ROLE_ADMIN a endpoints que deberían permitirse

**Síntomas:**
- El login con `admin` retorna un token con `"roles": ["ROLE_USER", "ROLE_ADMIN"]`
- `DELETE /accounts/{id}` con ese token retorna HTTP 403

**Causa:**
Spring Security WebFlux usa `hasRole("ADMIN")` que internamente agrega el prefijo `ROLE_`. Si el claim del JWT ya incluye `ROLE_ADMIN`, se crea una autoridad `ROLE_ROLE_ADMIN`, lo que no coincide con la regla de autorización.

**Solución:**

```bash
# Opción 1: Usar hasAuthority() en lugar de hasRole() en SecurityConfig
# hasAuthority("ROLE_ADMIN") busca exactamente "ROLE_ADMIN"
# hasRole("ADMIN") busca "ROLE_ADMIN" automáticamente

# Verificar qué autoridades se están creando en HeaderAuthenticationFilter
grep "SimpleGrantedAuthority" \
    ~/banking-system/accounts-service/src/main/java/com/banking/accounts/security/HeaderAuthenticationFilter.java

# El filtro debe crear autoridades con el nombre EXACTO del rol del JWT
# Si el JWT tiene "ROLE_ADMIN", la autoridad debe ser "ROLE_ADMIN"
# Y en SecurityConfig usar hasRole("ADMIN") (que agrega ROLE_ automáticamente)
# O usar hasAuthority("ROLE_ADMIN") (que busca el nombre exacto)

# Si los roles en el JWT NO tienen prefijo ROLE_:
# Cambiar en SecurityConfig:
# .hasRole("ADMIN")  →  .hasAuthority("ADMIN")
```

---

### Problema 5: El `auth-service` no arranca porque el puerto 8083 está ocupado

**Síntomas:**
- Error: `Web server failed to start. Port 8083 was already in use`

**Causa:**
Una instancia anterior del `auth-service` sigue en ejecución, o algún otro proceso usa el puerto 8083.

**Solución:**

```bash
# Identificar el proceso que usa el puerto 8083
lsof -ti:8083

# Terminar el proceso
kill -9 $(lsof -ti:8083) 2>/dev/null
sleep 2

# Verificar que el puerto está libre
lsof -ti:8083 && echo "Puerto aún ocupado" || echo "✅ Puerto 8083 libre"

# Reiniciar el servicio
cd ~/banking-system && mvn spring-boot:run -pl auth-service > /tmp/auth-service.log 2>&1 &
sleep 12
curl -s http://localhost:8083/auth/health
```

## Limpieza

Una vez completadas todas las pruebas, detén los servicios y libera los recursos:

```bash
# Detener todos los microservicios Spring Boot
echo "Deteniendo microservicios..."
kill $(lsof -ti:8080) 2>/dev/null && echo "✅ api-gateway (8080) detenido"
kill $(lsof -ti:8081) 2>/dev/null && echo "✅ accounts-service (8081) detenido"
kill $(lsof -ti:8082) 2>/dev/null && echo "✅ transactions-service (8082) detenido"
kill $(lsof -ti:8083) 2>/dev/null && echo "✅ auth-service (8083) detenido"

sleep 3

# Verificar que todos los puertos están libres
for port in 8080 8081 8082 8083; do
    if lsof -ti:$port > /dev/null 2>&1; then
        echo "⚠️  Puerto $port aún en uso"
    else
        echo "✅ Puerto $port libre"
    fi
done

# Detener contenedores Docker (opcional - mantener si se usarán en el siguiente lab)
# docker-compose stop postgres-accounts postgres-transactions

# Limpiar archivos de log temporales (opcional)
# rm -f /tmp/auth-service.log /tmp/accounts-service.log /tmp/transactions-service.log /tmp/api-gateway.log

echo ""
echo "=== Limpieza completada ==="
echo "Los contenedores de PostgreSQL siguen activos para el siguiente laboratorio."
echo "Para detenerlos: docker-compose stop"
```

> ⚠️ **Advertencia:** No detengas los contenedores de PostgreSQL si planeas continuar con el Laboratorio 4 inmediatamente. Los datos de prueba se perderán si detienes y eliminas los contenedores. Para preservar los datos, usa `docker-compose stop` (no `docker-compose down`).

> ⚠️ **Recordatorio de Seguridad:** La clave JWT `banking-system-super-secret-key-for-jwt-signing-minimum-256-bits` está hardcodeada en los archivos de configuración para fines de laboratorio. **En un entorno de producción real, esta clave DEBE gestionarse mediante variables de entorno, AWS Secrets Manager, HashiCorp Vault u otro sistema de gestión de secretos. NUNCA commitees secretos al repositorio de código fuente.**

## Resumen

### Lo que Lograste

- Construiste un `auth-service` completo con Spring WebFlux que genera tokens JWT firmados con HS256 usando JJWT 0.12.x, con usuarios precargados y contraseñas hasheadas con BCrypt
- Implementaste un filtro global reactivo (`GlobalFilter`) en el API Gateway que actúa como guardián perimetral: valida el JWT, extrae los claims y los propaga como headers internos (`X-User-Id`, `X-User-Roles`, `X-Trace-Id`) a los microservicios downstream
- Configuraste `SecurityWebFilterChain` reactiva en `accounts-service` y `transactions-service` con reglas de autorización granulares por roles: `ROLE_USER` para operaciones de lectura y escritura básica, `ROLE_ADMIN` para operaciones destructivas
- Implementaste manejadores de excepciones de seguridad reactivos (`ServerAuthenticationEntryPoint`, `ServerAccessDeniedHandler`) que retornan respuestas JSON estructuradas para errores 401 y 403
- Configuraste propagación automática de headers de seguridad en llamadas `WebClient` inter-servicios mediante filtros de contexto reactivo
- Validaste exhaustivamente todos los escenarios de seguridad: login exitoso, token válido, token inválido, token ausente, rol insuficiente y operaciones administrativas

### Conceptos Clave Aprendidos

- **Separación de responsabilidades en seguridad distribuida**: el Gateway autentica (valida JWT), cada microservicio autoriza (verifica roles) — ningún servicio gestiona contraseñas directamente
- **Seguridad sin estado con JWT**: los microservicios no necesitan consultar una base de datos ni el `auth-service` para cada request; la firma criptográfica garantiza la integridad del token
- **Patrón de seguridad en capas**: el Gateway actúa como perímetro de seguridad y propaga claims como headers internos de confianza; los microservicios internos confían en esos headers para construir el contexto de seguridad reactivo
- **Manejo reactivo de errores de seguridad**: en WebFlux, los errores de seguridad se manejan en el pipeline reactivo, no con try-catch bloqueante; `onErrorResume` y los handlers de Spring Security son los mecanismos correctos
- **Trazabilidad con X-Trace-Id**: el header de traza generado en el Gateway y propagado a todos los servicios permite correlacionar logs de una misma request a través de múltiples microservicios
- **La clave JWT debe ser compartida y secreta**: todos los componentes que validan tokens (Gateway, microservicios si validan directamente) deben usar exactamente la misma clave secreta; en producción, gestionarla con un vault

### Próximos Pasos

- En el **Laboratorio 4**, integrarás Resilience4j para agregar patrones de resiliencia (circuit breaker, retry, timeout) a las llamadas inter-servicios que ya están protegidas con JWT
- Explora la herramienta interactiva [jwt.io](https://jwt.io) para decodificar y analizar los tokens generados en este laboratorio
- Considera implementar refresh tokens para extender la sesión sin requerir nuevas credenciales — un patrón común en sistemas bancarios reales
- Investiga el uso de RS256 (clave asimétrica RSA) en lugar de HS256: permite que los microservicios validen tokens usando solo la clave pública, sin necesidad de compartir el secreto

## Recursos Adicionales

- **JJWT GitHub Repository** — Documentación oficial y ejemplos de uso de la librería JJWT 0.12.x para Java: [https://github.com/jwtk/jjwt](https://github.com/jwtk/jjwt)
- **jwt.io** — Herramienta interactiva para decodificar, verificar y depurar tokens JWT. Esencial para validar los claims generados en este laboratorio: [https://jwt.io](https://jwt.io)
- **Spring Security WebFlux Reference** — Documentación oficial de Spring Security para aplicaciones reactivas, incluyendo `SecurityWebFilterChain` y `ReactiveSecurityContextHolder`: [https://docs.spring.io/spring-security/reference/reactive/index.html](https://docs.spring.io/spring-security/reference/reactive/index.html)
- **RFC 7519 – JSON Web Token** — Especificación oficial del estándar JWT con todos los claims registrados y reglas de validación: [https://datatracker.ietf.org/doc/html/rfc7519](https://datatracker.ietf.org/doc/html/rfc7519)
- **Spring Cloud Gateway — Global Filters** — Documentación sobre cómo implementar `GlobalFilter` para interceptar y modificar requests en Spring Cloud Gateway: [https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#global-filters](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/#global-filters)
- **BCrypt Password Encoder** — Guía sobre el uso de BCrypt para hash de contraseñas en Spring Security: [https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
