package com.bancolombia.training.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.context.annotation.Import;

@WebFluxTest(StatusController.class)
@Import(SecurityConfig.class)
class StatusControllerTest {
    @Autowired WebTestClient client;
    @Test void returnsReactiveStatus() {
        client.get().uri("/accounts/status").exchange().expectStatus().isOk().expectBody()
                .jsonPath("$.service").isEqualTo("accounts-service").jsonPath("$.status").isEqualTo("UP");
    }
}
