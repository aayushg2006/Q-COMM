package com.qcomm.engine.daa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Graph {

    private final Set<Long> vertices = new LinkedHashSet<>();
    private final List<Edge> edges = new ArrayList<>();
    private final Map<Long, List<Edge>> adjacency = new LinkedHashMap<>();

    public void addVertex(Long vertexId) {
        if (vertexId == null) {
            throw new IllegalArgumentException("Vertex id cannot be null.");
        }
        vertices.add(vertexId);
        adjacency.computeIfAbsent(vertexId, ignored -> new ArrayList<>());
    }

    public void addEdge(Long sourceId, Long targetId, int weight) {
        if (sourceId == null || targetId == null) {
            throw new IllegalArgumentException("Source and target ids are required.");
        }
        if (sourceId.equals(targetId)) {
            throw new IllegalArgumentException("Self-loops are not supported in MST.");
        }

        int safeWeight = Math.max(1, weight);

        addVertex(sourceId);
        addVertex(targetId);

        Edge forward = new Edge(sourceId, targetId, safeWeight);
        Edge reverse = new Edge(targetId, sourceId, safeWeight);

        edges.add(forward);
        adjacency.get(sourceId).add(forward);
        adjacency.get(targetId).add(reverse);
    }

    public Set<Long> vertices() {
        return Collections.unmodifiableSet(vertices);
    }

    public List<Edge> edges() {
        return Collections.unmodifiableList(edges);
    }

    public List<Edge> neighbors(Long vertexId) {
        List<Edge> neighbors = adjacency.get(vertexId);
        if (neighbors == null) {
            return List.of();
        }
        return Collections.unmodifiableList(neighbors);
    }

    public boolean isEmpty() {
        return vertices.isEmpty();
    }

    public int vertexCount() {
        return vertices.size();
    }

    public record Edge(Long sourceId, Long targetId, int weight) {
    }
}

