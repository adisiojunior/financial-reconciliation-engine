package com.finance.gateway;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ExternalPaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(ExternalPaymentGatewayClient.class);

    @CircuitBreaker(name = "paymentGateway")
    @Retry(name = "paymentGateway")
    public boolean processPayment(String externalReference, BigDecimal amount, String currency) {
        log.info("A contactar gateway externo para a referencia {} no valor de {} {}", externalReference, amount, currency);
        // Simulacao de chamada remota bem-sucedida
        return true;
    }
}