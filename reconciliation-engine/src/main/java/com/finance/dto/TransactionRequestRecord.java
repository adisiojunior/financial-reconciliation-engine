package com.finance.dto;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

public record TransactionRequestRecord(
        @NotBlank(message = "Referência externa obrigatória")
        String externalReference,

        @NotNull(message = "Valor é obrigatório")
        @DecimalMin(value = "0.01", message = "O valor deve ser superior a zero")
        BigDecimal amount,

        @NotBlank(message = "Moeda obrigatória (ex: BRL)")
        String currency,

        @NotBlank(message = "Identificador da conta obrigatório")
        String accountId
) {}