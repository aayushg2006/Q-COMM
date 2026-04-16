package com.qcomm.engine.daa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpanningTreeAlgorithmTest {

    private static final int EXPECTED_MINIMUM_COST = 14;

    @Test
    void kruskalAndPrimShouldReturnSameMinimumCostForCompleteFiveNodeGraph() {
        Graph graph = createCompleteGraphWithFiveNodes();

        SpanningTreeAlgorithm kruskal = new KruskalAlgorithmImpl();
        SpanningTreeAlgorithm prim = new PrimAlgorithmImpl();

        OptimizationResultDTO kruskalResult = kruskal.calculateMST(graph);
        OptimizationResultDTO primResult = prim.calculateMST(graph);

        assertEquals(EXPECTED_MINIMUM_COST, kruskalResult.totalCost());
        assertEquals(EXPECTED_MINIMUM_COST, primResult.totalCost());
        assertEquals(kruskalResult.totalCost(), primResult.totalCost());

        assertEquals(4, kruskalResult.mstEdges().size());
        assertEquals(4, primResult.mstEdges().size());

        assertTrue(kruskalResult.executionTimeNs() >= 0);
        assertTrue(primResult.executionTimeNs() >= 0);
    }

    private Graph createCompleteGraphWithFiveNodes() {
        Graph graph = new Graph();

        // Complete graph K5 with deterministic positive weights.
        graph.addEdge(1L, 2L, 2);
        graph.addEdge(1L, 3L, 3);
        graph.addEdge(1L, 4L, 6);
        graph.addEdge(1L, 5L, 8);
        graph.addEdge(2L, 3L, 1);
        graph.addEdge(2L, 4L, 4);
        graph.addEdge(2L, 5L, 7);
        graph.addEdge(3L, 4L, 5);
        graph.addEdge(3L, 5L, 9);
        graph.addEdge(4L, 5L, 10);

        return graph;
    }
}

