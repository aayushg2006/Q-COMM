package com.qcomm.engine.controller;

public record CompareRequestDTO(
        String event,
        String beltCode,
        String warehouseName,
        Double warehouseLat,
        Double warehouseLng
) {
}
