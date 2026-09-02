package com.bancolombia.training.accounts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.test.web.reactive.server.WebTestClient;

@WebFluxTest(AccountController.class)
class AccountControllerTest {
    @Autowired WebTestClient client;
    @Test void findsExistingAccount() {
        client.get().uri("/accounts/1").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.number").isEqualTo("ACC-001");
    }
    @Test void returns404ForMissingAccount() {
        client.get().uri("/accounts/999").exchange().expectStatus().isNotFound();
    }
}
