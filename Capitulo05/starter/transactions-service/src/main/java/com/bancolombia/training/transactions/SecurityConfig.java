package com.bancolombia.training.transactions;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.server.SecurityWebFilterChain;
@Configuration class SecurityConfig {
 @Bean ReactiveJwtDecoder jwtDecoder(@Value("${jwt.secret}") String s){ return NimbusReactiveJwtDecoder.withSecretKey(new SecretKeySpec(s.getBytes(java.nio.charset.StandardCharsets.UTF_8),"HmacSHA256")).macAlgorithm(MacAlgorithm.HS256).build(); }
 @Bean SecurityWebFilterChain security(ServerHttpSecurity http){ return http.csrf(ServerHttpSecurity.CsrfSpec::disable).authorizeExchange(a->a.pathMatchers("/transactions/status","/actuator/health","/actuator/health/**").permitAll().anyExchange().authenticated()).oauth2ResourceServer(o->o.jwt(j->{})).build(); }
}
