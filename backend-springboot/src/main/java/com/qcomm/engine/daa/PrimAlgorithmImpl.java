package com.qcomm.engine.daa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PrimAlgorithmImpl implements SpanningTreeAlgorithm {

    @Override
    public OptimizationResultDTO calculateMST(Graph graph) {
        long start = System.nanoTime();

        if (graph == null || graph.isEmpty()) {
            return new OptimizationResultDTO(List.of(), 0, System.nanoTime() - start);
        }

        Set<Long> visited = new HashSet<>();
        List<Graph.Edge> mstEdges = new ArrayList<>();
        PriorityQueue<Graph.Edge> minHeap = new PriorityQueue<>(Comparator.comparingInt(Graph.Edge::weight));
        int totalCost = 0;

        for (Long startVertex : graph.vertices()) {
            if (visited.contains(startVertex)) {
                continue;
            }

            visited.add(startVertex);
            minHeap.addAll(graph.neighbors(startVertex));

            while (!minHeap.isEmpty()) {
                Graph.Edge edge = minHeap.poll();
                Long nextVertex = edge.targetId();
                if (visited.contains(nextVertex)) {
                    continue;
                }

                visited.add(nextVertex);
                mstEdges.add(edge);
                totalCost += edge.weight();

                for (Graph.Edge neighbor : graph.neighbors(nextVertex)) {
                    if (!visited.contains(neighbor.targetId())) {
                        minHeap.offer(neighbor);
                    }
                }
            }
        }

        long executionTimeNs = System.nanoTime() - start;
        return new OptimizationResultDTO(mstEdges, totalCost, executionTimeNs);
    }
}

