package com.finance.integration;

import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.dto.TransactionRequestRecord;
import com.finance.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TransactionFullIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Container
    static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.11-management-alpine");

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
    @DisplayName("Fluxo completo: POST na API -> Envio RabbitMQ -> Processamento Assíncrono -> RECONCILED no Postgres")
    void shouldExecuteFullReconciliationPipeline() {
        TransactionRequestRecord request = new TransactionRequestRecord(
                "INTEG-REF-999",
                new BigDecimal("180.50"),
                "BRL",
                "ACC-DEV"
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<TransactionRequestRecord> entity = new HttpEntity<>(request, headers);

        ResponseEntity<TransactionEntity> response = restTemplate.postForEntity(
                "/api/v1/transactions",
                entity,
                TransactionEntity.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(TransactionStatus.PENDING, response.getBody().getStatus());

        // Aguarda até 5 segundos para o consumidor RabbitMQ processar e atualizar a entidade
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            TransactionEntity updated = transactionRepository.findById(response.getBody().getId()).orElseThrow();
            assertEquals(TransactionStatus.RECONCILED, updated.getStatus());
            assertTrue(updated.getVersion() > 0, "O número de versão deve incrementar com o lock otimista");
        });
    }
}