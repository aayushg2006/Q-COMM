package com.qcomm.engine.dto;

public record BeltDescriptorDTO(
        String code,
        String label,
        String warehouseName,
        Double warehouseLat,
        Double warehouseLng
) {
}
