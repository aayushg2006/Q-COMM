package com.qcomm.engine.service;

import com.qcomm.engine.ai.SLMOrchestrator;
import com.qcomm.engine.ai.TrafficSignalService;
import com.qcomm.engine.config.BeltNetworkCatalog;
import com.qcomm.engine.daa.Graph;
import com.qcomm.engine.daa.KruskalAlgorithmImpl;
import com.qcomm.engine.daa.OptimizationResultDTO;
import com.qcomm.engine.daa.PrimAlgorithmImpl;
import com.qcomm.engine.daa.SpanningTreeAlgorithm;
import com.qcomm.engine.dto.AlgorithmComparisonDetailsDTO;
import com.qcomm.engine.dto.AlgorithmAnalyticsDTO;
import com.qcomm.engine.dto.CompareResponseDTO;
import com.qcomm.engine.dto.DarkStoreDTO;
import com.qcomm.engine.dto.DispatchStepDTO;
import com.qcomm.engine.dto.HistorySummaryDTO;
import com.qcomm.engine.dto.RouteEdgeDTO;
import com.qcomm.engine.dto.RoutingHistoryDTO;
import com.qcomm.engine.dto.RoutingResponseDTO;
import com.qcomm.engine.dto.WarehouseDTO;
import com.qcomm.engine.model.DarkStore;
import com.qcomm.engine.model.RouteEdge;
import com.qcomm.engine.model.RoutingHistory;
import com.qcomm.engine.repository.DarkStoreRepository;
import com.qcomm.engine.repository.RouteEdgeRepository;
import com.qcomm.engine.repository.RoutingHistoryRepository;
import jakarta.transaction.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class NetworkOptimizationService {

    private static final String DEFAULT_CUSTOM_WAREHOUSE_NAME = "Custom Mega Warehouse";
    private static final double RISK_MIN_DELTA_RATIO = 0.10d;
    private static final int RISK_MIN_DELTA_ABSOLUTE = 2;

    private final DarkStoreRepository darkStoreRepository;
    private final RouteEdgeRepository routeEdgeRepository;
    private final RoutingHistoryRepository routingHistoryRepository;
    private final SLMOrchestrator slmOrchestrator;
    private final TrafficSignalService trafficSignalService;
    private final BeltNetworkCatalog beltNetworkCatalog;
    private final KruskalAlgorithmImpl kruskalAlgorithm;
    private final PrimAlgorithmImpl primAlgorithm;

    public NetworkOptimizationService(
            DarkStoreRepository darkStoreRepository,
            RouteEdgeRepository routeEdgeRepository,
            RoutingHistoryRepository routingHistoryRepository,
            SLMOrchestrator slmOrchestrator,
            TrafficSignalService trafficSignalService,
            BeltNetworkCatalog beltNetworkCatalog,
            KruskalAlgorithmImpl kruskalAlgorithm,
            PrimAlgorithmImpl primAlgorithm
    ) {
        this.darkStoreRepository = darkStoreRepository;
        this.routeEdgeRepository = routeEdgeRepository;
        this.routingHistoryRepository = routingHistoryRepository;
        this.slmOrchestrator = slmOrchestrator;
        this.trafficSignalService = trafficSignalService;
        this.beltNetworkCatalog = beltNetworkCatalog;
        this.kruskalAlgorithm = kruskalAlgorithm;
        this.primAlgorithm = primAlgorithm;
    }

    @Transactional
    public List<DarkStoreDTO> getActiveStores(String beltCode) {
        return findActiveStores(beltCode)
                .stream()
                .map(store -> new DarkStoreDTO(
                        store.getId(),
                        store.getName(),
                        store.getLatitude(),
                        store.getLongitude()
                ))
                .toList();
    }

    @Transactional
    public List<RouteEdgeDTO> getActiveEdges(String beltCode) {
        Set<Long> activeStoreIds = new HashSet<>();
        for (DarkStore store : findActiveStores(beltCode)) {
            activeStoreIds.add(store.getId());
        }

        List<RouteEdgeDTO> edges = new ArrayList<>();
        for (RouteEdge edge : routeEdgeRepository.findAllWithStores()) {
            Long sourceId = edge.getSourceStore().getId();
            Long targetId = edge.getTargetStore().getId();
            if (!activeStoreIds.contains(sourceId) || !activeStoreIds.contains(targetId)) {
                continue;
            }

            int baseWeight = defaultPositive(edge.getBaseWeight(), 1);
            int aiWeight = defaultPositive(edge.getCurrentAiWeight(), baseWeight);
            edges.add(new RouteEdgeDTO(sourceId, targetId, aiWeight, false, null));
        }
        return edges;
    }

    @Transactional
    public RoutingResponseDTO optimize(
            String algorithm,
            String event,
            String beltCode,
            String warehouseName,
            Double warehouseLat,
            Double warehouseLng
    ) {
        PreparedGraph preparedGraph = prepareGraph(event, beltCode, warehouseName, warehouseLat, warehouseLng);
        return runAlgorithm(algorithm, preparedGraph, true);
    }

    @Transactional
    public CompareResponseDTO compareAlgorithms(
            String event,
            String beltCode,
            String warehouseName,
            Double warehouseLat,
            Double warehouseLng
    ) {
        PreparedGraph preparedGraph = prepareGraph(event, beltCode, warehouseName, warehouseLat, warehouseLng);
        RoutingResponseDTO prim = runAlgorithm("prim", preparedGraph, true);
        RoutingResponseDTO kruskal = runAlgorithm("kruskal", preparedGraph, true);

        String recommended;
        String reason;
        if (prim.totalCost() < kruskal.totalCost()) {
            recommended = "PRIM";
            reason = "Prim produced lower total MST cost for this scenario.";
        }
        else if (kruskal.totalCost() < prim.totalCost()) {
            recommended = "KRUSKAL";
            reason = "Kruskal produced lower total MST cost for this scenario.";
        }
        else if (prim.executionTimeMs() <= kruskal.executionTimeMs()) {
            recommended = "PRIM";
            reason = "Both produced the same MST cost; Prim executed faster.";
        }
        else {
            recommended = "KRUSKAL";
            reason = "Both produced the same MST cost; Kruskal executed faster.";
        }

        AlgorithmComparisonDetailsDTO details = buildComparisonDetails(prim, kruskal);
        return new CompareResponseDTO(prim, kruskal, recommended, reason, details);
    }

    @Transactional
    public List<RoutingHistoryDTO> getRecentHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        return routingHistoryRepository.findTop100ByOrderByTimestampDesc()
                .stream()
                .limit(safeLimit)
                .map(history -> new RoutingHistoryDTO(
                        history.getId(),
                        history.getTimestamp(),
                        history.getAlgorithmUsed(),
                        history.getExecutionTimeMs(),
                        history.getTotalCost()
                ))
                .toList();
    }

    @Transactional
    public HistorySummaryDTO getHistorySummary() {
        List<RoutingHistory> recent = routingHistoryRepository.findTop100ByOrderByTimestampDesc();
        RoutingHistory latest = recent.isEmpty() ? null : recent.get(0);

        List<AlgorithmAnalyticsDTO> breakdown = routingHistoryRepository.summarizeByAlgorithm()
                .stream()
                .map(projection -> new AlgorithmAnalyticsDTO(
                        projection.getAlgorithm(),
                        projection.getRunCount(),
                        round2(projection.getAvgExecutionTimeMs()),
                        round2(projection.getAvgTotalCost())
                ))
                .sorted((left, right) -> left.algorithm().compareToIgnoreCase(right.algorithm()))
                .toList();

        return new HistorySummaryDTO(
                routingHistoryRepository.count(),
                latest == null ? null : latest.getExecutionTimeMs(),
                latest == null ? null : latest.getTotalCost(),
                breakdown
        );
    }

    @Transactional
    public String exportHistoryAsCsv(int limit) {
        int safeLimit = Math.max(1, Math.min(1000, limit));
        StringBuilder csv = new StringBuilder("id,timestamp,algorithm_used,execution_time_ms,total_cost\n");
        for (RoutingHistory history : routingHistoryRepository.findTop100ByOrderByTimestampDesc().stream().limit(safeLimit).toList()) {
            csv.append(history.getId()).append(',')
                    .append(history.getTimestamp()).append(',')
                    .append(escapeCsv(history.getAlgorithmUsed())).append(',')
                    .append(history.getExecutionTimeMs()).append(',')
                    .append(history.getTotalCost()).append('\n');
        }
        return csv.toString();
    }

    private PreparedGraph prepareGraph(
            String event,
            String beltCode,
            String warehouseName,
            Double warehouseLat,
            Double warehouseLng
    ) {
        String normalizedBeltCode = resolveBeltCode(beltCode);
        BeltNetworkCatalog.BeltDefinition beltDefinition = beltNetworkCatalog.getRequired(normalizedBeltCode);
        WarehouseNode warehouse = resolveWarehouse(beltDefinition, warehouseName, warehouseLat, warehouseLng);

        List<DarkStore> activeStores = findActiveStores(normalizedBeltCode);
        Set<Long> activeStoreIds = new HashSet<>();
        Graph graph = new Graph();
        Map<Long, DarkStore> storesById = new HashMap<>();
        Map<EdgeKey, EdgeMetadata> edgeMetadataByKey = new HashMap<>();

        for (DarkStore store : activeStores) {
            activeStoreIds.add(store.getId());
            storesById.put(store.getId(), store);
            graph.addVertex(store.getId());
        }

        List<RouteEdge> allEdges = routeEdgeRepository.findAllWithStores();
        boolean hasEvent = event != null && !event.isBlank();

        for (RouteEdge edge : allEdges) {
            Long sourceId = edge.getSourceStore().getId();
            Long targetId = edge.getTargetStore().getId();

            if (!activeStoreIds.contains(sourceId) || !activeStoreIds.contains(targetId)) {
                continue;
            }

            WeightDecision decision = selectEffectiveWeight(edge, hasEvent, event);
            int chosenWeight = decision.weight();
            graph.addEdge(sourceId, targetId, chosenWeight);
            edgeMetadataByKey.put(
                    EdgeKey.of(sourceId, targetId),
                    new EdgeMetadata(chosenWeight, decision.riskFlagged(), decision.riskReason())
            );
        }

        return new PreparedGraph(graph, edgeMetadataByKey, storesById, warehouse);
    }

    private List<DarkStore> findActiveStores(String rawBeltCode) {
        String normalized = resolveBeltCode(rawBeltCode);
        return darkStoreRepository.findByIsActiveTrueAndBeltCodeIgnoreCase(normalized);
    }

    private String resolveBeltCode(String rawBeltCode) {
        String candidate = rawBeltCode == null || rawBeltCode.isBlank()
                ? beltNetworkCatalog.defaultBeltCode()
                : rawBeltCode;
        return beltNetworkCatalog.getRequired(candidate).code();
    }

    private WarehouseNode resolveWarehouse(
            BeltNetworkCatalog.BeltDefinition beltDefinition,
            String warehouseName,
            Double warehouseLat,
            Double warehouseLng
    ) {
        boolean hasCustomLatitude = warehouseLat != null;
        boolean hasCustomLongitude = warehouseLng != null;

        if (hasCustomLatitude ^ hasCustomLongitude) {
            throw new IllegalArgumentException("Both warehouseLat and warehouseLng must be provided for custom warehouse.");
        }

        if (hasCustomLatitude) {
            validateLatitude(warehouseLat);
            validateLongitude(warehouseLng);
            String label = warehouseName == null || warehouseName.isBlank()
                    ? DEFAULT_CUSTOM_WAREHOUSE_NAME
                    : warehouseName.trim();
            return new WarehouseNode(label, warehouseLat, warehouseLng, true);
        }

        BeltNetworkCatalog.WarehouseSeed defaultWarehouse = beltDefinition.warehouse();
        String label = warehouseName == null || warehouseName.isBlank()
                ? defaultWarehouse.name()
                : warehouseName.trim();
        return new WarehouseNode(label, defaultWarehouse.latitude(), defaultWarehouse.longitude(), false);
    }

    private void validateLatitude(Double latitude) {
        if (latitude == null || latitude < -90.0d || latitude > 90.0d) {
            throw new IllegalArgumentException("warehouseLat must be in range [-90, 90].");
        }
    }

    private void validateLongitude(Double longitude) {
        if (longitude == null || longitude < -180.0d || longitude > 180.0d) {
            throw new IllegalArgumentException("warehouseLng must be in range [-180, 180].");
        }
    }

    private RoutingResponseDTO runAlgorithm(String algorithm, PreparedGraph preparedGraph, boolean persistHistory) {
        AlgorithmSelection selection = resolveAlgorithm(algorithm);
        OptimizationResultDTO result = selection.algorithm().calculateMST(preparedGraph.graph());
        long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(result.executionTimeNs());

        if (persistHistory) {
            RoutingHistory history = RoutingHistory.builder()
                    .timestamp(LocalDateTime.now())
                    .algorithmUsed(selection.label())
                    .executionTimeMs(executionTimeMs)
                    .totalCost(result.totalCost())
                    .build();
            routingHistoryRepository.save(history);
        }

        List<RouteEdgeDTO> mstEdges = result.mstEdges()
                .stream()
                .map(edge -> mapMstEdge(edge, preparedGraph.edgeMetadataByKey()))
                .toList();

        DispatchPlan dispatchPlan = buildDispatchPlan(result.mstEdges(), preparedGraph);

        return new RoutingResponseDTO(
                mstEdges,
                result.totalCost(),
                executionTimeMs,
                selection.label(),
                new WarehouseDTO(
                        preparedGraph.warehouse().name(),
                        preparedGraph.warehouse().lat(),
                        preparedGraph.warehouse().lng(),
                        preparedGraph.warehouse().custom()
                ),
                dispatchPlan.steps(),
                dispatchPlan.totalCost()
        );
    }

    private DispatchPlan buildDispatchPlan(List<Graph.Edge> mstEdges, PreparedGraph preparedGraph) {
        Map<Long, DarkStore> storesById = preparedGraph.storesById();
        if (storesById.isEmpty()) {
            return new DispatchPlan(List.of(), 0);
        }

        Map<Long, List<TraversalCandidate>> adjacency = new HashMap<>();
        for (Graph.Edge edge : mstEdges) {
            EdgeMetadata metadata = preparedGraph.edgeMetadataByKey().get(EdgeKey.of(edge.sourceId(), edge.targetId()));
            int weight = metadata == null ? Math.max(1, edge.weight()) : Math.max(1, metadata.weight());
            boolean riskFlagged = metadata != null && metadata.riskFlagged();
            String riskReason = metadata == null ? null : metadata.riskReason();

            adjacency.computeIfAbsent(edge.sourceId(), ignored -> new ArrayList<>())
                    .add(new TraversalCandidate(edge.sourceId(), edge.targetId(), weight, riskFlagged, riskReason));
            adjacency.computeIfAbsent(edge.targetId(), ignored -> new ArrayList<>())
                    .add(new TraversalCandidate(edge.targetId(), edge.sourceId(), weight, riskFlagged, riskReason));
        }

        Comparator<TraversalCandidate> traversalComparator = Comparator
                .comparingInt(TraversalCandidate::weight)
                .thenComparing(TraversalCandidate::to)
                .thenComparing(TraversalCandidate::from);

        for (List<TraversalCandidate> candidates : adjacency.values()) {
            candidates.sort(traversalComparator);
        }

        Set<Long> visited = new HashSet<>();
        List<DispatchStepDTO> steps = new ArrayList<>();
        PriorityQueue<TraversalCandidate> frontier = new PriorityQueue<>(traversalComparator);

        int sequence = 1;
        int cumulativeCost = 0;

        while (visited.size() < storesById.size()) {
            Long nextRootId = findNearestStoreToWarehouse(storesById, preparedGraph.warehouse(), visited);
            if (nextRootId == null) {
                break;
            }

            DarkStore nextRoot = storesById.get(nextRootId);
            if (nextRoot == null) {
                visited.add(nextRootId);
                continue;
            }

            int warehouseLegCost = estimateWarehouseLegCost(preparedGraph.warehouse(), nextRoot);
            cumulativeCost += warehouseLegCost;
            steps.add(new DispatchStepDTO(
                    sequence++,
                    preparedGraph.warehouse().name(),
                    preparedGraph.warehouse().lat(),
                    preparedGraph.warehouse().lng(),
                    nextRoot.getId(),
                    nextRoot.getName(),
                    nextRoot.getLatitude(),
                    nextRoot.getLongitude(),
                    warehouseLegCost,
                    cumulativeCost,
                    false,
                    null,
                    true
            ));

            visited.add(nextRootId);
            frontier.clear();
            addFrontierEdges(nextRootId, adjacency, visited, frontier);

            while (!frontier.isEmpty()) {
                TraversalCandidate candidate = frontier.poll();
                if (visited.contains(candidate.to())) {
                    continue;
                }

                DarkStore fromStore = storesById.get(candidate.from());
                DarkStore toStore = storesById.get(candidate.to());
                if (fromStore == null || toStore == null) {
                    continue;
                }

                cumulativeCost += candidate.weight();
                steps.add(new DispatchStepDTO(
                        sequence++,
                        fromStore.getName(),
                        fromStore.getLatitude(),
                        fromStore.getLongitude(),
                        toStore.getId(),
                        toStore.getName(),
                        toStore.getLatitude(),
                        toStore.getLongitude(),
                        candidate.weight(),
                        cumulativeCost,
                        candidate.riskFlagged(),
                        candidate.riskReason(),
                        false
                ));

                visited.add(candidate.to());
                addFrontierEdges(candidate.to(), adjacency, visited, frontier);
            }
        }

        return new DispatchPlan(steps, cumulativeCost);
    }

    private void addFrontierEdges(
            Long storeId,
            Map<Long, List<TraversalCandidate>> adjacency,
            Set<Long> visited,
            PriorityQueue<TraversalCandidate> frontier
    ) {
        for (TraversalCandidate candidate : adjacency.getOrDefault(storeId, List.of())) {
            if (!visited.contains(candidate.to())) {
                frontier.offer(candidate);
            }
        }
    }

    private Long findNearestStoreToWarehouse(
            Map<Long, DarkStore> storesById,
            WarehouseNode warehouse,
            Set<Long> excludedStoreIds
    ) {
        Long bestId = null;
        double bestDistanceKm = Double.MAX_VALUE;

        for (Map.Entry<Long, DarkStore> entry : storesById.entrySet()) {
            if (excludedStoreIds.contains(entry.getKey())) {
                continue;
            }

            DarkStore store = entry.getValue();
            double distanceKm = haversineKm(
                    warehouse.lat(),
                    warehouse.lng(),
                    store.getLatitude(),
                    store.getLongitude()
            );

            if (distanceKm < bestDistanceKm) {
                bestDistanceKm = distanceKm;
                bestId = entry.getKey();
            }
            else if (distanceKm == bestDistanceKm && bestId != null && entry.getKey() < bestId) {
                bestId = entry.getKey();
            }
        }

        return bestId;
    }

    private int estimateWarehouseLegCost(WarehouseNode warehouse, DarkStore targetStore) {
        DarkStore warehouseAsStore = DarkStore.builder()
                .name(warehouse.name())
                .latitude(warehouse.lat())
                .longitude(warehouse.lng())
                .isActive(true)
                .build();

        var trafficEstimate = trafficSignalService.estimateTravelMinutes(warehouseAsStore, targetStore);
        if (trafficEstimate.isPresent()) {
            return Math.max(1, trafficEstimate.getAsInt());
        }

        double km = haversineKm(warehouse.lat(), warehouse.lng(), targetStore.getLatitude(), targetStore.getLongitude());
        int estimatedMinutes = (int) Math.round((km / 30.0d) * 60.0d);
        return Math.max(1, estimatedMinutes);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final double earthRadiusKm = 6371.0d;
        double latDistance = Math.toRadians(lat2 - lat1);
        double lngDistance = Math.toRadians(lng2 - lng1);
        double sinLat = Math.sin(latDistance / 2.0d);
        double sinLng = Math.sin(lngDistance / 2.0d);
        double a = (sinLat * sinLat)
                + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2))
                * (sinLng * sinLng);
        double c = 2.0d * Math.atan2(Math.sqrt(a), Math.sqrt(1.0d - a));
        return earthRadiusKm * c;
    }

    private WeightDecision selectEffectiveWeight(RouteEdge edge, boolean hasEvent, String event) {
        int baseWeight = defaultPositive(edge.getBaseWeight(), 1);
        int currentAiWeight = defaultPositive(edge.getCurrentAiWeight(), baseWeight);
        var liveTrafficEstimate = trafficSignalService.estimateTravelMinutes(edge.getSourceStore(), edge.getTargetStore());
        Integer liveTrafficWeight = liveTrafficEstimate.isPresent() ? liveTrafficEstimate.getAsInt() : null;
        int baselineOperationalWeight = liveTrafficWeight == null
                ? currentAiWeight
                : Math.max(1, liveTrafficWeight);

        if (!hasEvent) {
            int selected = baselineOperationalWeight;
            String riskReason = buildBaselineRiskReason(
                    edge,
                    baseWeight,
                    selected,
                    liveTrafficWeight,
                    currentAiWeight
            );
            boolean riskFlagged = riskReason != null;
            return new WeightDecision(selected, riskFlagged, riskReason);
        }

        int aiSuggestedWeight = slmOrchestrator.calculateRiskWeight(
                edge.getSourceStore().getName(),
                edge.getTargetStore().getName(),
                baseWeight,
                event,
                liveTrafficWeight
        );

        // Adverse scenarios should not make travel cost cheaper than baseline.
        int eventFloor = Math.max(
                baseWeight + 1,
                baselineOperationalWeight + 1
        );
        int finalWeight = Math.max(aiSuggestedWeight, eventFloor);
        boolean riskFlagged = isMaterialRiskIncrease(baseWeight, finalWeight)
                || isMaterialRiskIncrease(baselineOperationalWeight, finalWeight);
        String riskReason = riskFlagged
                ? slmOrchestrator.explainRiskReason(
                edge.getSourceStore().getName(),
                edge.getTargetStore().getName(),
                baselineOperationalWeight,
                finalWeight,
                event,
                liveTrafficWeight
        )
                : null;

        if (riskFlagged && (riskReason == null || riskReason.isBlank())) {
            riskReason = buildBaselineRiskReason(
                    edge,
                    baselineOperationalWeight,
                    finalWeight,
                    liveTrafficWeight,
                    currentAiWeight
            );
        }
        return new WeightDecision(finalWeight, riskFlagged && riskReason != null, riskReason);
    }

    private boolean isMaterialRiskIncrease(int baselineWeight, int adjustedWeight) {
        int safeBaseline = Math.max(1, baselineWeight);
        int safeAdjusted = Math.max(1, adjustedWeight);
        if (safeAdjusted <= safeBaseline) {
            return false;
        }

        int delta = safeAdjusted - safeBaseline;
        int ratioThreshold = (int) Math.ceil(safeBaseline * RISK_MIN_DELTA_RATIO);
        int requiredDelta = Math.max(RISK_MIN_DELTA_ABSOLUTE, ratioThreshold);
        return delta >= requiredDelta;
    }

    private String buildBaselineRiskReason(
            RouteEdge edge,
            int baselineWeight,
            int selectedWeight,
            Integer liveTrafficWeight,
            int currentAiWeight
    ) {
        if (!isMaterialRiskIncrease(baselineWeight, selectedWeight)) {
            return null;
        }

        int delta = Math.max(1, selectedWeight - baselineWeight);
        if (liveTrafficWeight != null && selectedWeight >= liveTrafficWeight) {
            return "Live traffic indicates +" + delta + " minutes delay on this corridor.";
        }
        if (selectedWeight >= currentAiWeight) {
            return "Current corridor conditions show sustained congestion, adding about +" + delta + " minutes.";
        }
        return "Detected corridor slowdown adds approximately +" + delta + " minutes.";
    }

    private RouteEdgeDTO mapMstEdge(Graph.Edge edge, Map<EdgeKey, EdgeMetadata> edgeMetadataByKey) {
        EdgeMetadata metadata = edgeMetadataByKey.get(EdgeKey.of(edge.sourceId(), edge.targetId()));
        if (metadata == null) {
            return new RouteEdgeDTO(edge.sourceId(), edge.targetId(), edge.weight(), false, null);
        }
        return new RouteEdgeDTO(
                edge.sourceId(),
                edge.targetId(),
                metadata.weight(),
                metadata.riskFlagged(),
                metadata.riskReason()
        );
    }

    private int defaultPositive(Integer value, int fallback) {
        if (value == null || value < 1) {
            return fallback;
        }
        return value;
    }

    private AlgorithmSelection resolveAlgorithm(String algorithm) {
        String normalized = algorithm == null ? "" : algorithm.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "kruskal", "kruskals", "kruskal's" -> new AlgorithmSelection("KRUSKAL", kruskalAlgorithm);
            case "prim", "prims", "prim's" -> new AlgorithmSelection("PRIM", primAlgorithm);
            default -> throw new IllegalArgumentException(
                    "Unsupported algorithm: '" + algorithm + "'. Supported values are: kruskal, prim."
            );
        };
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (!value.contains(",") && !value.contains("\"")) {
            return value;
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private Double round2(Double value) {
        if (value == null) {
            return null;
        }
        return Math.round(value * 100.0d) / 100.0d;
    }

    private double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }

    private AlgorithmComparisonDetailsDTO buildComparisonDetails(
            RoutingResponseDTO prim,
            RoutingResponseDTO kruskal
    ) {
        Map<EdgeKey, RouteEdgeDTO> primByKey = new HashMap<>();
        for (RouteEdgeDTO edge : prim.mstEdges()) {
            primByKey.put(EdgeKey.of(edge.source(), edge.target()), edge);
        }

        Map<EdgeKey, RouteEdgeDTO> kruskalByKey = new HashMap<>();
        for (RouteEdgeDTO edge : kruskal.mstEdges()) {
            kruskalByKey.put(EdgeKey.of(edge.source(), edge.target()), edge);
        }

        List<RouteEdgeDTO> primOnly = new ArrayList<>();
        for (Map.Entry<EdgeKey, RouteEdgeDTO> entry : primByKey.entrySet()) {
            if (!kruskalByKey.containsKey(entry.getKey())) {
                primOnly.add(entry.getValue());
            }
        }

        List<RouteEdgeDTO> kruskalOnly = new ArrayList<>();
        for (Map.Entry<EdgeKey, RouteEdgeDTO> entry : kruskalByKey.entrySet()) {
            if (!primByKey.containsKey(entry.getKey())) {
                kruskalOnly.add(entry.getValue());
            }
        }

        Comparator<RouteEdgeDTO> edgeComparator = Comparator
                .comparing(RouteEdgeDTO::source, Comparator.nullsLast(Long::compareTo))
                .thenComparing(RouteEdgeDTO::target, Comparator.nullsLast(Long::compareTo))
                .thenComparing(RouteEdgeDTO::weight, Comparator.nullsLast(Integer::compareTo));
        primOnly.sort(edgeComparator);
        kruskalOnly.sort(edgeComparator);

        int primCost = prim.totalCost() == null ? 0 : prim.totalCost();
        int kruskalCost = kruskal.totalCost() == null ? 0 : kruskal.totalCost();
        long primExecutionMs = prim.executionTimeMs() == null ? 0L : prim.executionTimeMs();
        long kruskalExecutionMs = kruskal.executionTimeMs() == null ? 0L : kruskal.executionTimeMs();

        boolean sameCost = primCost == kruskalCost;
        boolean sameEdgeSet = primOnly.isEmpty() && kruskalOnly.isEmpty();
        int costDelta = primCost - kruskalCost;
        long executionDelta = primExecutionMs - kruskalExecutionMs;

        int sharedEdgeCount = 0;
        for (EdgeKey key : primByKey.keySet()) {
            if (kruskalByKey.containsKey(key)) {
                sharedEdgeCount++;
            }
        }
        int denominator = Math.max(1, Math.max(primByKey.size(), kruskalByKey.size()));
        double overlapPercent = round2((sharedEdgeCount * 100.0d) / denominator);

        return new AlgorithmComparisonDetailsDTO(
                sameCost,
                sameEdgeSet,
                costDelta,
                executionDelta,
                prim.mstEdges().size(),
                kruskal.mstEdges().size(),
                sharedEdgeCount,
                overlapPercent,
                prim.dispatchPlan().size(),
                kruskal.dispatchPlan().size(),
                primOnly,
                kruskalOnly
        );
    }

    private record WeightDecision(int weight, boolean riskFlagged, String riskReason) {
    }

    private record EdgeMetadata(int weight, boolean riskFlagged, String riskReason) {
    }

    private record EdgeKey(long first, long second) {
        private static EdgeKey of(Long source, Long target) {
            long safeSource = source == null ? 0L : source;
            long safeTarget = target == null ? 0L : target;
            return safeSource <= safeTarget
                    ? new EdgeKey(safeSource, safeTarget)
                    : new EdgeKey(safeTarget, safeSource);
        }
    }

    private record TraversalCandidate(Long from, Long to, int weight, boolean riskFlagged, String riskReason) {
    }

    private record WarehouseNode(String name, double lat, double lng, boolean custom) {
    }

    private record DispatchPlan(List<DispatchStepDTO> steps, int totalCost) {
    }

    private record AlgorithmSelection(String label, SpanningTreeAlgorithm algorithm) {
    }

    private record PreparedGraph(
            Graph graph,
            Map<EdgeKey, EdgeMetadata> edgeMetadataByKey,
            Map<Long, DarkStore> storesById,
            WarehouseNode warehouse
    ) {
    }
}
