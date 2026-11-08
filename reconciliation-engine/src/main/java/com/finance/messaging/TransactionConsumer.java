package com.finance.messaging;

import com.finance.service.ReconciliationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TransactionConsumer {

    private static final Logger log = LoggerFactory.getLogger(TransactionConsumer.class);

    private final ReconciliationService reconciliationService;

    public TransactionConsumer(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @RabbitListener(queues = "financial.transaction.reconcile")
    public void consumeTransaction(UUID transactionId) {
        log.info("Mensagem recebida da fila RabbitMQ para a transacao ID: {}", transactionId);
        try {
            reconciliationService.reconcile(transactionId);
        } catch (Exception ex) {
            log.error("Erro no processamento assincrono da transacao {}: {}", transactionId, ex.getMessage());
            throw ex; // Reencaminha a excecao para acionar o mecanismo de retry/DLQ do RabbitMQ
        }
    }
}