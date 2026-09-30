package com.vernflow.modules.tourism.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FortuneDrawRequest(
        @Valid @NotNull FortuneProfileRequest preferences,
        @Size(max = 40) String departure,
        @NotNull @FutureOrPresent LocalDate date,
        @NotNull @Min(1) @Max(7) Integer days,
        @NotBlank @Size(max = 40) String travelers,
        @NotNull @DecimalMin("600.00") @DecimalMax("200000.00") BigDecimal budget,
        @NotBlank @Size(max = 40) String pace,
        boolean exploreNew,
        @Size(max = 80) String previousDestination
) { }
