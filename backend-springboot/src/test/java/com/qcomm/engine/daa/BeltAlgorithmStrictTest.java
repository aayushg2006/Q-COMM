package com.qcomm.engine.daa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.qcomm.engine.config.BeltNetworkCatalog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BeltAlgorithmStrictTest {

    private final SpanningTreeAlgorithm prim = new PrimAlgorithmImpl();
    private final SpanningTreeAlgorithm kruskal = new KruskalAlgorithmImpl();

    @Test
    void eachBeltShouldProduceOptimalMstForBothAlgorithms() {
        BeltNetworkCatalog catalog = new BeltNetworkCatalog();

        for (BeltNetworkCatalog.BeltDescriptor descriptor : catalog.listBelts()) {
            BeltNetworkCatalog.BeltDefinition belt = catalog.getRequired(descriptor.code());
            GraphFixture fixture = buildFixture(belt, Map.of());
            BruteForceResult oracle = bruteForceMst(fixture);

            OptimizationResultDTO primResult = prim.calculateMST(fixture.graph());
            OptimizationResultDTO kruskalResult = kruskal.calculateMST(fixture.graph());

            assertEquals(
                    fixture.vertexCount() - 1,
                    primResult.mstEdges().size(),
                    "Prim edge count mismatch for belt " + belt.code()
            );
            assertEquals(
                    fixture.vertexCount() - 1,
                    kruskalResult.mstEdges().size(),
                    "Kruskal edge count mismatch for belt " + belt.code()
            );

            assertEquals(
                    oracle.minCost(),
                    primResult.totalCost(),
                    "Prim cost mismatch for belt " + belt.code()
            );
            assertEquals(
                    oracle.minCost(),
                    kruskalResult.totalCost(),
                    "Kruskal cost mismatch for belt " + belt.code()
            );

            Set<EdgeKey> primSet = canonicalEdgeSet(primResult.mstEdges());
            Set<EdgeKey> kruskalSet = canonicalEdgeSet(kruskalResult.mstEdges());
            assertTrue(
                    oracle.optimalEdgeSets().contains(primSet),
                    "Prim path is not one of optimal MSTs for belt " + belt.code()
            );
            assertTrue(
                    oracle.optimalEdgeSets().contains(kruskalSet),
                    "Kruskal path is not one of optimal MSTs for belt " + belt.code()
            );
        }
    }

    @Test
    void eachBeltShouldAllowPathChangeWhenScenarioWeightsChange() {
        BeltNetworkCatalog catalog = new BeltNetworkCatalog();

        for (BeltNetworkCatalog.BeltDescriptor descriptor : catalog.listBelts()) {
            BeltNetworkCatalog.BeltDefinition belt = catalog.getRequired(descriptor.code());
            GraphFixture baselineFixture = buildFixture(belt, Map.of());
            OptimizationResultDTO baseline = prim.calculateMST(baselineFixture.graph());
            Set<EdgeKey> baselineEdges = canonicalEdgeSet(baseline.mstEdges());

            boolean changed = false;
            for (Graph.Edge baselineEdge : baseline.mstEdges()) {
                Map<EdgeKey, Integer> overrides = new HashMap<>();
                EdgeKey key = EdgeKey.of(baselineEdge.sourceId(), baselineEdge.targetId());
                overrides.put(key, baselineEdge.weight() + 150);

                GraphFixture perturbedFixture = buildFixture(belt, overrides);
                OptimizationResultDTO perturbed = prim.calculateMST(perturbedFixture.graph());
                Set<EdgeKey> perturbedEdges = canonicalEdgeSet(perturbed.mstEdges());
                if (!baselineEdges.equals(perturbedEdges)) {
                    changed = true;
                    break;
                }
            }

            assertTrue(changed, "No scenario perturbation changed path for belt " + belt.code());
        }
    }

    private GraphFixture buildFixture(
            BeltNetworkCatalog.BeltDefinition definition,
            Map<EdgeKey, Integer> weightOverrides
    ) {
        Graph graph = new Graph();
        Map<String, Long> idByStoreName = new HashMap<>();

        long nextId = 1L;
        for (BeltNetworkCatalog.StoreSeed store : definition.stores()) {
            idByStoreName.put(store.name(), nextId);
            graph.addVertex(nextId);
            nextId++;
        }

        List<WeightedEdge> weightedEdges = new ArrayList<>();
        for (BeltNetworkCatalog.EdgeSeed edge : definition.edges()) {
            Long sourceId = idByStoreName.get(edge.sourceName());
            Long targetId = idByStoreName.get(edge.targetName());
            if (sourceId == null || targetId == null) {
                continue;
            }
            EdgeKey key = EdgeKey.of(sourceId, targetId);
            int weight = weightOverrides.getOrDefault(key, edge.baseWeight());
            graph.addEdge(sourceId, targetId, weight);
            weightedEdges.add(new WeightedEdge(sourceId, targetId, weight));
        }

        return new GraphFixture(graph, definition.stores().size(), weightedEdges);
    }

    private BruteForceResult bruteForceMst(GraphFixture fixture) {
        int targetEdges = fixture.vertexCount() - 1;
        List<WeightedEdge> edges = fixture.weightedEdges();

        int[] bestCost = {Integer.MAX_VALUE};
        Set<Set<EdgeKey>> optimalEdgeSets = new HashSet<>();
        backtrackCombinations(
                edges,
                0,
                targetEdges,
                new ArrayList<>(),
                fixture.vertexCount(),
                bestCost,
                optimalEdgeSets
        );

        return new BruteForceResult(bestCost[0], optimalEdgeSets);
    }

    private void backtrackCombinations(
            List<WeightedEdge> edges,
            int index,
            int remaining,
            List<WeightedEdge> selected,
            int vertexCount,
            int[] bestCost,
            Set<Set<EdgeKey>> optimalEdgeSets
    ) {
        if (remaining == 0) {
            evaluateCandidate(selected, vertexCount, bestCost, optimalEdgeSets);
            return;
        }
        if (index >= edges.size() || edges.size() - index < remaining) {
            return;
        }

        selected.add(edges.get(index));
        backtrackCombinations(edges, index + 1, remaining - 1, selected, vertexCount, bestCost, optimalEdgeSets);
        selected.remove(selected.size() - 1);

        backtrackCombinations(edges, index + 1, remaining, selected, vertexCount, bestCost, optimalEdgeSets);
    }

    private void evaluateCandidate(
            List<WeightedEdge> selected,
            int vertexCount,
            int[] bestCost,
            Set<Set<EdgeKey>> optimalEdgeSets
    ) {
        UnionFind uf = new UnionFind(vertexCount + 1);
        int cost = 0;

        for (WeightedEdge edge : selected) {
            long source = edge.source();
            long target = edge.target();
            if (!uf.union((int) source, (int) target)) {
                return;
            }
            cost += edge.weight();
        }

        int root = uf.find(1);
        for (int vertex = 2; vertex <= vertexCount; vertex++) {
            if (uf.find(vertex) != root) {
                return;
            }
        }

        Set<EdgeKey> edgeSet = new HashSet<>();
        for (WeightedEdge edge : selected) {
            edgeSet.add(EdgeKey.of(edge.source(), edge.target()));
        }

        if (cost < bestCost[0]) {
            bestCost[0] = cost;
            optimalEdgeSets.clear();
            optimalEdgeSets.add(edgeSet);
        }
        else if (cost == bestCost[0]) {
            optimalEdgeSets.add(edgeSet);
        }
    }

    private Set<EdgeKey> canonicalEdgeSet(List<Graph.Edge> edges) {
        Set<EdgeKey> set = new HashSet<>();
        for (Graph.Edge edge : edges) {
            set.add(EdgeKey.of(edge.sourceId(), edge.targetId()));
        }
        return set;
    }

    private record GraphFixture(Graph graph, int vertexCount, List<WeightedEdge> weightedEdges) {
    }

    private record WeightedEdge(long source, long target, int weight) {
    }

    private record BruteForceResult(int minCost, Set<Set<EdgeKey>> optimalEdgeSets) {
    }

    private record EdgeKey(long first, long second) {
        private static EdgeKey of(Long source, Long target) {
            long left = source == null ? 0L : source;
            long right = target == null ? 0L : target;
            return left <= right ? new EdgeKey(left, right) : new EdgeKey(right, left);
        }
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
            }
        }

        private int find(int node) {
            if (parent[node] != node) {
                parent[node] = find(parent[node]);
            }
            return parent[node];
        }

        private boolean union(int first, int second) {
            int rootFirst = find(first);
            int rootSecond = find(second);
            if (rootFirst == rootSecond) {
                return false;
            }

            if (rank[rootFirst] < rank[rootSecond]) {
                parent[rootFirst] = rootSecond;
            }
            else if (rank[rootFirst] > rank[rootSecond]) {
                parent[rootSecond] = rootFirst;
            }
            else {
                parent[rootSecond] = rootFirst;
                rank[rootFirst]++;
            }
            return true;
        }
    }
}

