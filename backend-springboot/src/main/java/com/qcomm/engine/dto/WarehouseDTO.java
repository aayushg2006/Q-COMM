package com.qcomm.engine.dto;

public record WarehouseDTO(
        String name,
        Double lat,
        Double lng,
        boolean custom
) {
}
