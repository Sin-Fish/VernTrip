package com.vernflow.modules.openclaw.dto;

import jakarta.validation.constraints.Size;

public record OpenClawTourismCatalogRequest(
        @Size(max = 80) String city
) {
}
