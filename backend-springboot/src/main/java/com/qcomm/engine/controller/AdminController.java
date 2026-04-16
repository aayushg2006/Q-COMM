package com.qcomm.engine.controller;

import com.qcomm.engine.config.BeltNetworkCatalog;
import com.qcomm.engine.config.MumbaiBeltDataSeeder;
import com.qcomm.engine.dto.AdminActionResponseDTO;
import com.qcomm.engine.dto.BeltDescriptorDTO;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final MumbaiBeltDataSeeder mumbaiBeltDataSeeder;
    private final BeltNetworkCatalog beltNetworkCatalog;

    public AdminController(
            MumbaiBeltDataSeeder mumbaiBeltDataSeeder,
            BeltNetworkCatalog beltNetworkCatalog
    ) {
        this.mumbaiBeltDataSeeder = mumbaiBeltDataSeeder;
        this.beltNetworkCatalog = beltNetworkCatalog;
    }

    @GetMapping("/belts")
    public ResponseEntity<List<BeltDescriptorDTO>> getBelts() {
        List<BeltDescriptorDTO> belts = mumbaiBeltDataSeeder.getSupportedBelts()
                .stream()
                .map(belt -> {
                    BeltNetworkCatalog.BeltDefinition definition = beltNetworkCatalog.getRequired(belt.code());
                    BeltNetworkCatalog.WarehouseSeed warehouse = definition.warehouse();
                    return new BeltDescriptorDTO(
                            belt.code(),
                            belt.label(),
                            warehouse.name(),
                            warehouse.latitude(),
                            warehouse.longitude()
                    );
                })
                .toList();
        return ResponseEntity.ok(belts);
    }

    @PostMapping("/seed/{beltCode}")
    public ResponseEntity<AdminActionResponseDTO> seedBelt(@PathVariable String beltCode) {
        MumbaiBeltDataSeeder.SeedOutcome outcome = mumbaiBeltDataSeeder.seedBelt(beltCode);
        return ResponseEntity.ok(new AdminActionResponseDTO(
                outcome.beltName() + " seed ensured.",
                mumbaiBeltDataSeeder.countActiveStores(outcome.beltCode()),
                mumbaiBeltDataSeeder.countEdges(outcome.beltCode())
        ));
    }

    @PostMapping("/reset-and-seed/{beltCode}")
    public ResponseEntity<AdminActionResponseDTO> resetAndSeedBelt(@PathVariable String beltCode) {
        MumbaiBeltDataSeeder.SeedOutcome outcome = mumbaiBeltDataSeeder.resetAndSeedBelt(beltCode, true);
        return ResponseEntity.ok(new AdminActionResponseDTO(
                "Belt reset + reseed completed for " + outcome.beltName() + ".",
                mumbaiBeltDataSeeder.countActiveStores(outcome.beltCode()),
                mumbaiBeltDataSeeder.countEdges(outcome.beltCode())
        ));
    }

    @PostMapping("/seed/mumbai")
    public ResponseEntity<AdminActionResponseDTO> seedMumbaiBelt() {
        return seedBelt(BeltNetworkCatalog.DEFAULT_BELT_CODE);
    }

    @PostMapping("/reset-and-seed/mumbai")
    public ResponseEntity<AdminActionResponseDTO> resetAndSeedMumbaiBelt() {
        return resetAndSeedBelt(BeltNetworkCatalog.DEFAULT_BELT_CODE);
    }

    @PostMapping("/seed-all")
    public ResponseEntity<AdminActionResponseDTO> seedAllBelts() {
        List<MumbaiBeltDataSeeder.SeedOutcome> outcomes = mumbaiBeltDataSeeder.seedAllBelts();
        return ResponseEntity.ok(new AdminActionResponseDTO(
                "Seed ensured for " + outcomes.size() + " belts.",
                mumbaiBeltDataSeeder.countActiveStores(),
                mumbaiBeltDataSeeder.countEdges()
        ));
    }

    @PostMapping("/reset-and-seed-all")
    public ResponseEntity<AdminActionResponseDTO> resetAndSeedAllBelts() {
        List<MumbaiBeltDataSeeder.SeedOutcome> outcomes = mumbaiBeltDataSeeder.resetAndSeedAllBelts(true);
        return ResponseEntity.ok(new AdminActionResponseDTO(
                "Reset + reseed completed for " + outcomes.size() + " belts.",
                mumbaiBeltDataSeeder.countActiveStores(),
                mumbaiBeltDataSeeder.countEdges()
        ));
    }

    @GetMapping("/alerts/status")
    public ResponseEntity<Map<String, String>> getAlertsStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "STANDBY",
                "message", "Alerting is configured but currently disabled."
        ));
    }
}
