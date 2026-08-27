package com.bancolombia.training.auth;

import java.time.Instant;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@RestController
@RequestMapping("/auth")
class TokenController {
    private final JwtEncoder encoder;
    TokenController(@Value("${jwt.secret}") String secret) {
        SecretKey key = new SecretKeySpec(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(key));
    }

    @PostMapping("/token")
    Mono<TokenResponse> token(@RequestBody Login login) {
        if (!List.of("USER", "ADMIN").contains(login.role())) return Mono.error(new IllegalArgumentException("role"));
        Instant now=Instant.now();
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer("course-auth").subject(login.username())
                .issuedAt(now).expiresAt(now.plusSeconds(1800)).claim("scope", "ROLE_"+login.role()).build();
        String value=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
        return Mono.just(new TokenResponse(value));
    }
}
record Login(String username,String role) {}
record TokenResponse(String accessToken) {}
