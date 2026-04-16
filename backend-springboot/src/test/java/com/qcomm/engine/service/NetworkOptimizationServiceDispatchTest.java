package com.qcomm.engine.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.qcomm.engine.ai.SLMOrchestrator;
import com.qcomm.engine.ai.TrafficSignalService;
import com.qcomm.engine.config.BeltNetworkCatalog;
import com.qcomm.engine.daa.KruskalAlgorithmImpl;
import com.qcomm.engine.daa.PrimAlgorithmImpl;
import com.qcomm.engine.dto.DispatchStepDTO;
import com.qcomm.engine.dto.RoutingResponseDTO;
import com.qcomm.engine.model.DarkStore;
import com.qcomm.engine.model.RouteEdge;
import com.qcomm.engine.model.RoutingHistory;
import com.qcomm.engine.repository.DarkStoreRepository;
import com.qcomm.engine.repository.RouteEdgeRepository;
import com.qcomm.engine.repository.RoutingHistoryRepository;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NetworkOptimizationServiceDispatchTest {

    @Mock
    private DarkStoreRepository darkStoreRepository;

    @Mock
    private RouteEdgeRepository routeEdgeRepository;

    @Mock
    private RoutingHistoryRepository routingHistoryRepository;

    @Mock
    private SLMOrchestrator slmOrchestrator;

    @Mock
    private TrafficSignalService trafficSignalService;

    private NetworkOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new NetworkOptimizationService(
                darkStoreRepository,
                routeEdgeRepository,
                routingHistoryRepository,
                slmOrchestrator,
                trafficSignalService,
                new BeltNetworkCatalog(),
                new KruskalAlgorithmImpl(),
                new PrimAlgorithmImpl()
        );

        when(trafficSignalService.estimateTravelMinutes(any(DarkStore.class), any(DarkStore.class)))
                .thenReturn(OptionalInt.empty());
        when(routingHistoryRepository.save(any(RoutingHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void dispatchPlanShouldStartFromWarehouseAndCoverAllStores() {
        DarkStore alpha = store(101L, "Alpha Hub", 19.1000, 72.8700, "mumbai_borivali_andheri");
        DarkStore beta = store(102L, "Beta Hub", 19.1200, 72.8800, "mumbai_borivali_andheri");
        DarkStore gamma = store(103L, "Gamma Hub", 19.1450, 72.9000, "mumbai_borivali_andheri");
        DarkStore delta = store(104L, "Delta Hub", 19.1700, 72.9100, "mumbai_borivali_andheri");

        when(darkStoreRepository.findByIsActiveTrueAndBeltCodeIgnoreCase("mumbai_borivali_andheri"))
                .thenReturn(List.of(alpha, beta, gamma, delta));
        when(routeEdgeRepository.findAllWithStores())
                .thenReturn(List.of(
                        edge(alpha, beta, 6),
                        edge(beta, gamma, 5),
                        edge(gamma, delta, 4),
                        edge(alpha, gamma, 20),
                        edge(beta, delta, 18)
                ));

        RoutingResponseDTO response = service.optimize(
                "prim",
                null,
                "mumbai_borivali_andheri",
                "Test Warehouse",
                19.0980,
                72.8680
        );

        assertEquals(3, response.mstEdges().size(), "MST should contain V-1 edges.");
        assertEquals(4, response.dispatchPlan().size(), "Dispatch should include warehouse hop plus each store.");
        assertTrue(response.dispatchCost() >= response.totalCost(), "Dispatch cost should include warehouse-first cost.");

        DispatchStepDTO firstStep = response.dispatchPlan().get(0);
        assertTrue(firstStep.fromWarehouse(), "First step must originate from warehouse.");
        assertEquals("Alpha Hub", firstStep.toStoreName(), "Nearest store to the warehouse should be first.");

        Set<Long> visitedStores = response.dispatchPlan()
                .stream()
                .map(DispatchStepDTO::toStoreId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(101L, 102L, 103L, 104L), visitedStores, "Dispatch plan must cover all stores exactly once.");

        List<DispatchStepDTO> nonWarehouseSteps = response.dispatchPlan().stream()
                .filter(step -> !step.fromWarehouse())
                .toList();
        assertFalse(nonWarehouseSteps.isEmpty(), "Connected graph should contain intra-network dispatch steps.");
    }

    private DarkStore store(Long id, String name, double lat, double lng, String beltCode) {
        DarkStore store = new DarkStore();
        store.setId(id);
        store.setName(name);
        store.setLatitude(lat);
        store.setLongitude(lng);
        store.setIsActive(true);
        store.setBeltCode(beltCode);
        return store;
    }

    private RouteEdge edge(DarkStore source, DarkStore target, int weight) {
        RouteEdge edge = new RouteEdge();
        edge.setSourceStore(source);
        edge.setTargetStore(target);
        edge.setBaseWeight(weight);
        edge.setCurrentAiWeight(weight);
        return edge;
    }
}
