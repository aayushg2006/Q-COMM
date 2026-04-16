package com.qcomm.engine.config;

import com.qcomm.engine.model.DarkStore;
import com.qcomm.engine.model.RouteEdge;
import com.qcomm.engine.repository.DarkStoreRepository;
import com.qcomm.engine.repository.RouteEdgeRepository;
import com.qcomm.engine.repository.RoutingHistoryRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MumbaiBeltDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MumbaiBeltDataSeeder.class);

    private final DarkStoreRepository darkStoreRepository;
    private final RouteEdgeRepository routeEdgeRepository;
    private final RoutingHistoryRepository routingHistoryRepository;
    private final BeltNetworkCatalog beltNetworkCatalog;

    public MumbaiBeltDataSeeder(
            DarkStoreRepository darkStoreRepository,
            RouteEdgeRepository routeEdgeRepository,
            RoutingHistoryRepository routingHistoryRepository,
            BeltNetworkCatalog beltNetworkCatalog
    ) {
        this.darkStoreRepository = darkStoreRepository;
        this.routeEdgeRepository = routeEdgeRepository;
        this.routingHistoryRepository = routingHistoryRepository;
        this.beltNetworkCatalog = beltNetworkCatalog;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seedAllBelts();
    }

    @Transactional
    public SeedOutcome seedMumbaiBelt() {
        return seedBelt(beltNetworkCatalog.defaultBeltCode());
    }

    @Transactional
    public List<SeedOutcome> seedAllBelts() {
        List<SeedOutcome> outcomes = new ArrayList<>();
        for (BeltNetworkCatalog.BeltDescriptor descriptor : beltNetworkCatalog.listBelts()) {
            outcomes.add(seedBelt(descriptor.code()));
        }
        return outcomes;
    }

    @Transactional
    public SeedOutcome seedBelt(String rawBeltCode) {
        BeltNetworkCatalog.BeltDefinition belt = beltNetworkCatalog.getRequired(rawBeltCode);

        Map<String, DarkStore> existingByName = new HashMap<>();
        for (DarkStore store : darkStoreRepository.findAll()) {
            existingByName.put(store.getName(), store);
        }

        List<DarkStore> storesToPersist = new ArrayList<>();
        for (BeltNetworkCatalog.StoreSeed seed : belt.stores()) {
            DarkStore store = existingByName.get(seed.name());
            if (store == null) {
                store = DarkStore.builder()
                        .name(seed.name())
                        .latitude(seed.latitude())
                        .longitude(seed.longitude())
                        .isActive(true)
                        .beltCode(belt.code())
                        .build();
            }
            else {
                store.setLatitude(seed.latitude());
                store.setLongitude(seed.longitude());
                store.setIsActive(true);
                store.setBeltCode(belt.code());
            }
            storesToPersist.add(store);
        }

        List<DarkStore> persistedStores = darkStoreRepository.saveAll(storesToPersist);
        Map<String, DarkStore> persistedByName = new HashMap<>();
        for (DarkStore store : persistedStores) {
            persistedByName.put(store.getName(), store);
        }

        Set<EdgeKey> existingEdgeKeys = new HashSet<>();
        for (RouteEdge edge : routeEdgeRepository.findAllWithStores()) {
            existingEdgeKeys.add(EdgeKey.of(edge.getSourceStore().getId(), edge.getTargetStore().getId()));
        }

        List<RouteEdge> newEdges = new ArrayList<>();
        for (BeltNetworkCatalog.EdgeSeed seed : belt.edges()) {
            DarkStore source = persistedByName.get(seed.sourceName());
            DarkStore target = persistedByName.get(seed.targetName());
            if (source == null || target == null) {
                continue;
            }

            EdgeKey key = EdgeKey.of(source.getId(), target.getId());
            if (existingEdgeKeys.contains(key)) {
                continue;
            }

            RouteEdge edge = RouteEdge.builder()
                    .sourceStore(source)
                    .targetStore(target)
                    .baseWeight(seed.baseWeight())
                    .currentAiWeight(seed.currentAiWeight())
                    .build();
            newEdges.add(edge);
            existingEdgeKeys.add(key);
        }

        routeEdgeRepository.saveAll(newEdges);
        log.info(
                "Belt seed ensured: {} [{}], {} stores active, {} new edges inserted.",
                belt.displayName(),
                belt.code(),
                persistedStores.size(),
                newEdges.size()
        );
        return new SeedOutcome(
                belt.code(),
                belt.displayName(),
                persistedStores.size(),
                newEdges.size(),
                false
        );
    }

    @Transactional
    public SeedOutcome resetAndSeedMumbaiBelt(boolean clearHistory) {
        return resetAndSeedBelt(beltNetworkCatalog.defaultBeltCode(), clearHistory);
    }

    @Transactional
    public List<SeedOutcome> resetAndSeedAllBelts(boolean clearHistory) {
        if (clearHistory) {
            routingHistoryRepository.deleteAllInBatch();
        }
        List<SeedOutcome> outcomes = new ArrayList<>();
        for (BeltNetworkCatalog.BeltDescriptor descriptor : beltNetworkCatalog.listBelts()) {
            outcomes.add(resetAndSeedBelt(descriptor.code(), false));
        }
        return outcomes;
    }

    @Transactional
    public SeedOutcome resetAndSeedBelt(String rawBeltCode, boolean clearHistory) {
        BeltNetworkCatalog.BeltDefinition belt = beltNetworkCatalog.getRequired(rawBeltCode);
        List<DarkStore> beltStores = darkStoreRepository.findByBeltCodeIgnoreCase(belt.code());

        if (!beltStores.isEmpty()) {
            Set<Long> beltStoreIds = new HashSet<>();
            for (DarkStore store : beltStores) {
                beltStoreIds.add(store.getId());
            }

            List<RouteEdge> edgesToDelete = new ArrayList<>();
            for (RouteEdge edge : routeEdgeRepository.findAllWithStores()) {
                long sourceId = edge.getSourceStore().getId();
                long targetId = edge.getTargetStore().getId();
                if (beltStoreIds.contains(sourceId) || beltStoreIds.contains(targetId)) {
                    edgesToDelete.add(edge);
                }
            }

            if (!edgesToDelete.isEmpty()) {
                routeEdgeRepository.deleteAllInBatch(edgesToDelete);
            }
            darkStoreRepository.deleteAllInBatch(beltStores);
        }

        if (clearHistory) {
            routingHistoryRepository.deleteAllInBatch();
        }

        SeedOutcome outcome = seedBelt(belt.code());
        return new SeedOutcome(
                outcome.beltCode(),
                outcome.beltName(),
                outcome.activeStores(),
                outcome.newEdgesInserted(),
                true
        );
    }

    public long countActiveStores() {
        return darkStoreRepository.findByIsActiveTrue().size();
    }

    public long countActiveStores(String rawBeltCode) {
        String normalized = beltNetworkCatalog.normalize(rawBeltCode);
        return darkStoreRepository.findByIsActiveTrueAndBeltCodeIgnoreCase(normalized).size();
    }

    public long countEdges() {
        return routeEdgeRepository.count();
    }

    public long countEdges(String rawBeltCode) {
        String normalized = beltNetworkCatalog.normalize(rawBeltCode);
        long count = 0L;
        for (RouteEdge edge : routeEdgeRepository.findAllWithStores()) {
            String sourceBelt = edge.getSourceStore().getBeltCode();
            String targetBelt = edge.getTargetStore().getBeltCode();
            if (normalized.equalsIgnoreCase(sourceBelt == null ? "" : sourceBelt)
                    && normalized.equalsIgnoreCase(targetBelt == null ? "" : targetBelt)) {
                count++;
            }
        }
        return count;
    }

    public List<BeltNetworkCatalog.BeltDescriptor> getSupportedBelts() {
        return beltNetworkCatalog.listBelts();
    }

    public String getDefaultBeltCode() {
        return beltNetworkCatalog.defaultBeltCode();
    }

    private record EdgeKey(long first, long second) {
        private static EdgeKey of(Long source, Long target) {
            long left = source == null ? 0L : source;
            long right = target == null ? 0L : target;
            return left <= right ? new EdgeKey(left, right) : new EdgeKey(right, left);
        }
    }

    public record SeedOutcome(
            String beltCode,
            String beltName,
            int activeStores,
            int newEdgesInserted,
            boolean resetApplied
    ) {
    }
}
