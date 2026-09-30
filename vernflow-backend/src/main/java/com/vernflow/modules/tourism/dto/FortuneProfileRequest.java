package com.vernflow.modules.tourism.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record FortuneProfileRequest(
        @NotEmpty @Size(max = 12) List<@NotBlank @Size(max = 30) String> interests,
        @NotNull @Size(max = 40) List<@NotBlank @Size(max = 40) String> visitedCities
) { }
