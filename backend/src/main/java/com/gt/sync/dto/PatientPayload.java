package com.gt.sync.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

public record PatientPayload(
        @NotNull UUID id,
        @NotBlank @Pattern(regexp = "^P-[0-9]{5,}$") String patientNumber,
        @NotBlank @Size(min = 2, max = 50) @Pattern(regexp = "^[A-Za-z ]+$") String firstName,
        @Size(max = 50) @Pattern(regexp = "^[A-Za-z ]*$") String lastName,
        @NotBlank @Pattern(regexp = "MALE|FEMALE|OTHER") String gender,
        @NotNull @Min(1) @Max(120) BigInteger age,
        @NotBlank @Pattern(regexp = "^[0-9]{10}$") String mobile,
        @NotBlank @Size(min = 2, max = 255) String address,
        @NotBlank @Pattern(regexp = "DOCTOR|PATIENT|ONLINE|SELF|OTHER") String referredBy,
        @Size(max = 100) String doctorName,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
        @NotNull Instant registrationDate,
        boolean active,
        @NotNull Instant createdAt,
        @NotNull Instant updatedAt,
        Instant deletedAt,
        @Min(1) long recordVersion
) {
}
