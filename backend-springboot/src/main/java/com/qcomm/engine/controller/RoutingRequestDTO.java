package com.qcomm.engine.controller;

import jakarta.validation.constraints.NotBlank;

public record RoutingRequestDTO(
        @NotBlank(message = "algorithm is required")
        String algorithm,
        String event,
        String beltCode,
        String warehouseName,
        Double warehouseLat,
        Double warehouseLng
) {
}
