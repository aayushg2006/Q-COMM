package com.qcomm.engine.daa;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KruskalAlgorithmImpl implements SpanningTreeAlgorithm {

    @Override
    public OptimizationResultDTO calculateMST(Graph graph) {
        long start = System.nanoTime();

        if (graph == null || graph.isEmpty()) {
            return new OptimizationResultDTO(List.of(), 0, System.nanoTime() - start);
        }

        List<Long> vertices = new ArrayList<>(graph.vertices());
        Map<Long, Integer> indexByVertex = new HashMap<>(vertices.size());
        for (int i = 0; i < vertices.size(); i++) {
            indexByVertex.put(vertices.get(i), i);
        }

        List<Graph.Edge> sortedEdges = graph.edges().stream()
                .sorted(Comparator.comparingInt(Graph.Edge::weight))
                .toList();

        UnionFind unionFind = new UnionFind(vertices.size());
        List<Graph.Edge> mstEdges = new ArrayList<>();
        int totalCost = 0;

        for (Graph.Edge edge : sortedEdges) {
            Integer sourceIndex = indexByVertex.get(edge.sourceId());
            Integer targetIndex = indexByVertex.get(edge.targetId());
            if (sourceIndex == null || targetIndex == null) {
                continue;
            }

            if (unionFind.union(sourceIndex, targetIndex)) {
                mstEdges.add(edge);
                totalCost += edge.weight();

                if (mstEdges.size() == vertices.size() - 1) {
                    break;
                }
            }
        }

        long executionTimeNs = System.nanoTime() - start;
        return new OptimizationResultDTO(mstEdges, totalCost, executionTimeNs);
    }

    private static final class UnionFind {
        private final int[] parent;
        private final int[] rank;

        private UnionFind(int size) {
            this.parent = new int[size];
            this.rank = new int[size];
            for (int i = 0; i < size; i++) {
                parent[i] = i;
                rank[i] = 0;
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

