package com.qcomm.engine.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class BeltNetworkCatalog {

    public static final String DEFAULT_BELT_CODE = "mumbai_borivali_andheri";

    private final Map<String, BeltDefinition> definitionsByCode;

    public BeltNetworkCatalog() {
        Map<String, BeltDefinition> definitions = new LinkedHashMap<>();

        definitions.put(
                "mumbai_borivali_andheri",
                new BeltDefinition(
                        "mumbai_borivali_andheri",
                        "Mumbai Borivali-Andheri Belt",
                        new WarehouseSeed("Mumbai Mega Warehouse", 19.1790, 72.8465),
                        List.of(
                                new StoreSeed("Borivali East Hub", 19.2307, 72.8567),
                                new StoreSeed("Kandivali East Hub", 19.2065, 72.8510),
                                new StoreSeed("Kandivali West Hub", 19.2056, 72.8422),
                                new StoreSeed("Malad East Hub", 19.1865, 72.8604),
                                new StoreSeed("Malad West Hub", 19.1867, 72.8398),
                                new StoreSeed("Goregaon East Hub", 19.1663, 72.8570),
                                new StoreSeed("Jogeshwari East Hub", 19.1361, 72.8485),
                                new StoreSeed("Andheri East Hub", 19.1136, 72.8697)
                        ),
                        List.of(
                                new EdgeSeed("Borivali East Hub", "Kandivali East Hub", 14, 16),
                                new EdgeSeed("Borivali East Hub", "Kandivali West Hub", 16, 18),
                                new EdgeSeed("Kandivali East Hub", "Kandivali West Hub", 8, 9),
                                new EdgeSeed("Kandivali East Hub", "Malad East Hub", 12, 13),
                                new EdgeSeed("Kandivali West Hub", "Malad West Hub", 11, 13),
                                new EdgeSeed("Malad East Hub", "Malad West Hub", 9, 10),
                                new EdgeSeed("Malad East Hub", "Goregaon East Hub", 11, 12),
                                new EdgeSeed("Malad West Hub", "Goregaon East Hub", 14, 17),
                                new EdgeSeed("Goregaon East Hub", "Jogeshwari East Hub", 10, 11),
                                new EdgeSeed("Jogeshwari East Hub", "Andheri East Hub", 9, 11),
                                new EdgeSeed("Goregaon East Hub", "Andheri East Hub", 16, 20),
                                new EdgeSeed("Malad West Hub", "Jogeshwari East Hub", 18, 24),
                                new EdgeSeed("Kandivali East Hub", "Goregaon East Hub", 20, 22)
                        )
                )
        );

        definitions.put(
                "navi_mumbai_vashi_belapur",
                new BeltDefinition(
                        "navi_mumbai_vashi_belapur",
                        "Navi Mumbai Vashi-Belapur Belt",
                        new WarehouseSeed("Navi Mumbai Mega Warehouse", 19.0669, 73.0027),
                        List.of(
                                new StoreSeed("Vashi Hub", 19.0760, 72.9986),
                                new StoreSeed("Sanpada Hub", 19.0670, 73.0110),
                                new StoreSeed("Nerul Hub", 19.0330, 73.0180),
                                new StoreSeed("CBD Belapur Hub", 19.0170, 73.0390),
                                new StoreSeed("Seawoods Hub", 19.0100, 73.0140),
                                new StoreSeed("Kharghar Hub", 19.0460, 73.0660),
                                new StoreSeed("Airoli Hub", 19.1590, 72.9990),
                                new StoreSeed("Ghansoli Hub", 19.1190, 72.9990)
                        ),
                        List.of(
                                new EdgeSeed("Vashi Hub", "Sanpada Hub", 8, 9),
                                new EdgeSeed("Sanpada Hub", "Nerul Hub", 10, 11),
                                new EdgeSeed("Nerul Hub", "Seawoods Hub", 7, 8),
                                new EdgeSeed("Seawoods Hub", "CBD Belapur Hub", 8, 9),
                                new EdgeSeed("CBD Belapur Hub", "Kharghar Hub", 14, 16),
                                new EdgeSeed("Nerul Hub", "Kharghar Hub", 12, 14),
                                new EdgeSeed("Vashi Hub", "Airoli Hub", 13, 15),
                                new EdgeSeed("Airoli Hub", "Ghansoli Hub", 9, 10),
                                new EdgeSeed("Ghansoli Hub", "Vashi Hub", 11, 13),
                                new EdgeSeed("Ghansoli Hub", "Sanpada Hub", 10, 12),
                                new EdgeSeed("Sanpada Hub", "Seawoods Hub", 11, 13),
                                new EdgeSeed("Vashi Hub", "Nerul Hub", 13, 15),
                                new EdgeSeed("Nerul Hub", "CBD Belapur Hub", 9, 11)
                        )
                )
        );

        definitions.put(
                "pune_hinjewadi_baner_kothrud",
                new BeltDefinition(
                        "pune_hinjewadi_baner_kothrud",
                        "Pune Hinjewadi-Baner-Kothrud Belt",
                        new WarehouseSeed("Pune Mega Warehouse", 18.5663, 73.7619),
                        List.of(
                                new StoreSeed("Hinjewadi Phase 1 Hub", 18.5911, 73.7389),
                                new StoreSeed("Wakad Hub", 18.5975, 73.7621),
                                new StoreSeed("Baner Hub", 18.5590, 73.7868),
                                new StoreSeed("Aundh Hub", 18.5600, 73.8070),
                                new StoreSeed("Balewadi Hub", 18.5740, 73.7750),
                                new StoreSeed("Pashan Hub", 18.5445, 73.7940),
                                new StoreSeed("Kothrud Hub", 18.5074, 73.8077),
                                new StoreSeed("Bavdhan Hub", 18.5245, 73.7790)
                        ),
                        List.of(
                                new EdgeSeed("Hinjewadi Phase 1 Hub", "Wakad Hub", 9, 10),
                                new EdgeSeed("Wakad Hub", "Balewadi Hub", 8, 9),
                                new EdgeSeed("Balewadi Hub", "Baner Hub", 7, 8),
                                new EdgeSeed("Baner Hub", "Aundh Hub", 6, 7),
                                new EdgeSeed("Baner Hub", "Pashan Hub", 9, 10),
                                new EdgeSeed("Pashan Hub", "Kothrud Hub", 11, 12),
                                new EdgeSeed("Pashan Hub", "Bavdhan Hub", 8, 9),
                                new EdgeSeed("Bavdhan Hub", "Kothrud Hub", 10, 12),
                                new EdgeSeed("Wakad Hub", "Baner Hub", 10, 11),
                                new EdgeSeed("Hinjewadi Phase 1 Hub", "Balewadi Hub", 12, 14),
                                new EdgeSeed("Aundh Hub", "Kothrud Hub", 12, 14),
                                new EdgeSeed("Balewadi Hub", "Pashan Hub", 10, 11),
                                new EdgeSeed("Baner Hub", "Bavdhan Hub", 11, 13)
                        )
                )
        );

        definitions.put(
                "bengaluru_orr_whitefield",
                new BeltDefinition(
                        "bengaluru_orr_whitefield",
                        "Bengaluru ORR-Whitefield Belt",
                        new WarehouseSeed("Bengaluru Mega Warehouse", 12.9410, 77.7052),
                        List.of(
                                new StoreSeed("Marathahalli Hub", 12.9591, 77.6974),
                                new StoreSeed("Bellandur Hub", 12.9279, 77.6762),
                                new StoreSeed("HSR Layout Hub", 12.9116, 77.6474),
                                new StoreSeed("Sarjapur Road Hub", 12.9080, 77.6830),
                                new StoreSeed("Whitefield Hub", 12.9698, 77.7499),
                                new StoreSeed("Kadugodi Hub", 12.9955, 77.7615),
                                new StoreSeed("Brookefield Hub", 12.9670, 77.7150),
                                new StoreSeed("KR Puram Hub", 13.0077, 77.6953)
                        ),
                        List.of(
                                new EdgeSeed("Marathahalli Hub", "Bellandur Hub", 10, 12),
                                new EdgeSeed("Bellandur Hub", "HSR Layout Hub", 11, 13),
                                new EdgeSeed("Bellandur Hub", "Sarjapur Road Hub", 9, 11),
                                new EdgeSeed("Sarjapur Road Hub", "HSR Layout Hub", 8, 9),
                                new EdgeSeed("Marathahalli Hub", "Brookefield Hub", 8, 9),
                                new EdgeSeed("Brookefield Hub", "Whitefield Hub", 10, 12),
                                new EdgeSeed("Whitefield Hub", "Kadugodi Hub", 9, 10),
                                new EdgeSeed("Marathahalli Hub", "KR Puram Hub", 12, 14),
                                new EdgeSeed("KR Puram Hub", "Whitefield Hub", 11, 13),
                                new EdgeSeed("Brookefield Hub", "KR Puram Hub", 10, 11),
                                new EdgeSeed("Marathahalli Hub", "Whitefield Hub", 14, 16),
                                new EdgeSeed("Bellandur Hub", "Brookefield Hub", 13, 15),
                                new EdgeSeed("Sarjapur Road Hub", "Brookefield Hub", 12, 14)
                        )
                )
        );

        this.definitionsByCode = Map.copyOf(definitions);
    }

    public BeltDefinition getRequired(String rawBeltCode) {
        String normalized = normalize(rawBeltCode);
        BeltDefinition definition = definitionsByCode.get(normalized);
        if (definition == null) {
            throw new IllegalArgumentException(
                    "Unsupported belt: '" + rawBeltCode + "'. Supported values are: "
                            + String.join(", ", definitionsByCode.keySet())
            );
        }
        return definition;
    }

    public List<BeltDescriptor> listBelts() {
        return definitionsByCode.values()
                .stream()
                .map(definition -> new BeltDescriptor(definition.code(), definition.displayName()))
                .toList();
    }

    public String defaultBeltCode() {
        return DEFAULT_BELT_CODE;
    }

    public String normalize(String rawBeltCode) {
        if (rawBeltCode == null || rawBeltCode.isBlank()) {
            return DEFAULT_BELT_CODE;
        }
        return rawBeltCode.trim().toLowerCase(Locale.ROOT);
    }

    public record BeltDefinition(
            String code,
            String displayName,
            WarehouseSeed warehouse,
            List<StoreSeed> stores,
            List<EdgeSeed> edges
    ) {
    }

    public record BeltDescriptor(String code, String label) {
    }

    public record StoreSeed(String name, Double latitude, Double longitude) {
    }

    public record EdgeSeed(String sourceName, String targetName, Integer baseWeight, Integer currentAiWeight) {
    }

    public record WarehouseSeed(String name, Double latitude, Double longitude) {
    }
}
