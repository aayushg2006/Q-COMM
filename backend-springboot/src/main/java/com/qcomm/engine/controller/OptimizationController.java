package com.qcomm.engine.controller;

import com.qcomm.engine.dto.CompareResponseDTO;
import com.qcomm.engine.dto.DarkStoreDTO;
import com.qcomm.engine.dto.HistorySummaryDTO;
import com.qcomm.engine.dto.RouteEdgeDTO;
import com.qcomm.engine.dto.RoutingHistoryDTO;
import com.qcomm.engine.dto.RoutingResponseDTO;
import com.qcomm.engine.service.NetworkOptimizationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/network")
public class OptimizationController {

    private final NetworkOptimizationService networkOptimizationService;

    public OptimizationController(NetworkOptimizationService networkOptimizationService) {
        this.networkOptimizationService = networkOptimizationService;
    }

    @GetMapping("/stores")
    public ResponseEntity<List<DarkStoreDTO>> getStores(
            @RequestParam(name = "belt", required = false) String beltCode
    ) {
        return ResponseEntity.ok(networkOptimizationService.getActiveStores(beltCode));
    }

    @GetMapping("/edges")
    public ResponseEntity<List<RouteEdgeDTO>> getEdges(
            @RequestParam(name = "belt", required = false) String beltCode
    ) {
        return ResponseEntity.ok(networkOptimizationService.getActiveEdges(beltCode));
    }

    @PostMapping("/optimize")
    public ResponseEntity<RoutingResponseDTO> optimize(@Valid @RequestBody RoutingRequestDTO request) {
        RoutingResponseDTO result = networkOptimizationService.optimize(
                request.algorithm(),
                request.event(),
                request.beltCode(),
                request.warehouseName(),
                request.warehouseLat(),
                request.warehouseLng()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/compare")
    public ResponseEntity<CompareResponseDTO> compare(@RequestBody(required = false) CompareRequestDTO request) {
        String event = request == null ? null : request.event();
        String beltCode = request == null ? null : request.beltCode();
        String warehouseName = request == null ? null : request.warehouseName();
        Double warehouseLat = request == null ? null : request.warehouseLat();
        Double warehouseLng = request == null ? null : request.warehouseLng();
        return ResponseEntity.ok(
                networkOptimizationService.compareAlgorithms(
                        event,
                        beltCode,
                        warehouseName,
                        warehouseLat,
                        warehouseLng
                )
        );
    }

    @GetMapping("/history")
    public ResponseEntity<List<RoutingHistoryDTO>> getHistory(
            @RequestParam(name = "limit", defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(networkOptimizationService.getRecentHistory(limit));
    }

    @GetMapping("/history/summary")
    public ResponseEntity<HistorySummaryDTO> getHistorySummary() {
        return ResponseEntity.ok(networkOptimizationService.getHistorySummary());
    }

    @GetMapping(value = "/history/export.csv", produces = "text/csv")
    public ResponseEntity<String> exportHistory(
            @RequestParam(name = "limit", defaultValue = "100") int limit
    ) {
        String csv = networkOptimizationService.exportHistoryAsCsv(limit);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"routing_history.csv\"")
                .body(csv);
    }
}
