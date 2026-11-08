package com.finance.controller;

import com.finance.config.RabbitMQConfig;
import com.finance.domain.TransactionEntity;
import com.finance.domain.TransactionStatus;
import com.finance.dto.TransactionRequestRecord;
import com.finance.dto.TransactionResponseRecord;
import com.finance.repository.TransactionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.api.annotations.ParameterObject;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transações", description = "Endpoints de criação e consulta do motor de conciliação")
public class TransactionController {

    private final TransactionRepository transactionRepository;
    private final RabbitTemplate rabbitTemplate;

    public TransactionController(TransactionRepository transactionRepository, RabbitTemplate rabbitTemplate) {
        this.transactionRepository = transactionRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Criar e despachar transação", description = "Registra a transação de forma idempotente e envia para fila de conciliação assíncrona")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transação aceita e enfileirada com sucesso"),
            @ApiResponse(responseCode = "200", description = "Transação já existente (comportamento idempotente)"),
            @ApiResponse(responseCode = "400", description = "Payload inválido"),
            @ApiResponse(responseCode = "401", description = "Não autenticado")
    })
    public ResponseEntity<TransactionResponseRecord> createTransaction(@Valid @RequestBody TransactionRequestRecord request) {
        // Idempotência performática via banco de dados
        var existing = transactionRepository.findByExternalReference(request.externalReference());
        if (existing.isPresent()) {
            return ResponseEntity.ok(TransactionResponseRecord.fromEntity(existing.get()));
        }

        TransactionEntity entity = new TransactionEntity();
        entity.setExternalReference(request.externalReference());
        entity.setAmount(request.amount());
        entity.setCurrency(request.currency());
        entity.setStatus(TransactionStatus.PENDING);

        TransactionEntity saved = transactionRepository.save(entity);

        // Disparo assíncrono alinhado com a fila real do RabbitMQ
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE_NAME,
                RabbitMQConfig.ROUTING_KEY,
                saved.getId().toString()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(TransactionResponseRecord.fromEntity(saved));
    }

    @GetMapping
    @Operation(summary = "Listar transações paginadas", description = "Consulta paginada e segura das transações conciliadas")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "401", description = "Acesso negado - Requer Bearer Token")
    })
    public ResponseEntity<Page<TransactionResponseRecord>> listTransactions(@ParameterObject Pageable pageable) {
        Page<TransactionResponseRecord> response = transactionRepository.findAll(pageable)
                .map(TransactionResponseRecord::fromEntity);
        return ResponseEntity.ok(response);
    }
}