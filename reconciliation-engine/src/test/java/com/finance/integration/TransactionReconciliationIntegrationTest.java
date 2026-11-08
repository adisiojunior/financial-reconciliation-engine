package com.finance.integration;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.dto.LoginRequestRecord;
import com.finance.dto.TransactionRequestRecord;
import com.finance.dto.TransactionResponseRecord;
import com.finance.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransactionReconciliationIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.11-management-alpine");

    static {
        // Inicialização explícita antes de qualquer contexto de aplicação Spring
        postgres.start();
        rabbitmq.start();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.rabbitmq.host", rabbitmq::getHost);
        registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitmq::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitmq::getAdminPassword);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Fluxo Ponta a Ponta: Login JWT -> POST Transacao -> RabbitMQ -> Status RECONCILED")
    void shouldReconcileTransactionEndToEnd() {
        // 1. Obter Token JWT
        LoginRequestRecord loginReq = new LoginRequestRecord("integration-admin", "ROLE_ADMIN");
        ResponseEntity<?> rawLoginResp = restTemplate.postForEntity(
                "/api/v1/auth/login",
                loginReq,
                Map.class
        );

        assertNotNull(rawLoginResp.getBody());
        @SuppressWarnings("unchecked")
        Map<String, Object> loginMap = (Map<String, Object>) rawLoginResp.getBody();
        String token = String.valueOf(loginMap.get("accessToken"));
        assertNotNull(token);

        // 2. Disparar Transação com Bearer Token
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        TransactionRequestRecord payload = new TransactionRequestRecord(
                "TEST-CONTAINER-" + System.currentTimeMillis(),
                new BigDecimal("450.00"),
                "BRL",
                "ACC-CORRENTE-992"
        );

        ResponseEntity<TransactionResponseRecord> postResp = restTemplate.postForEntity(
                "/api/v1/transactions",
                new HttpEntity<>(payload, headers),
                TransactionResponseRecord.class
        );

        assertEquals(HttpStatus.CREATED, postResp.getStatusCode());
        assertNotNull(postResp.getBody());
        assertEquals(TransactionStatus.PENDING, postResp.getBody().status());

        // 3. Asserção assíncrona com margem de 12 segundos para a fila processar
        await()
                .atMost(Duration.ofSeconds(12))
                .pollInterval(Duration.ofMillis(300))
                .untilAsserted(() -> {
                    TransactionEntity entity = transactionRepository.findById(postResp.getBody().id()).orElseThrow();
                    assertEquals(TransactionStatus.RECONCILED, entity.getStatus());
                    assertTrue(entity.getVersion() > 0, "O lock otimista deve ter incrementado a versão");
                });
    }
}