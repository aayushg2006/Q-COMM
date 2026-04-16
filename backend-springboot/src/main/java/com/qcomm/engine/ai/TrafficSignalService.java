package com.qcomm.engine.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qcomm.engine.model.DarkStore;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class TrafficSignalService {

    private static final Logger log = LoggerFactory.getLogger(TrafficSignalService.class);
    private static final String DISTANCE_MATRIX_URL = "https://maps.googleapis.com/maps/api/distancematrix/json";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String mapsApiKey;
    private final long cacheTtlMs;
    private final Map<String, CacheEntry> responseCache = new ConcurrentHashMap<>();

    public TrafficSignalService(
            ObjectMapper objectMapper,
            @Value("${google.maps.api-key:}") String mapsApiKey,
            @Value("${google.maps.timeout-ms:1200}") int timeoutMs,
            @Value("${google.maps.cache-ttl-ms:180000}") long cacheTtlMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(300, timeoutMs));
        requestFactory.setReadTimeout(Math.max(300, timeoutMs));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
        this.objectMapper = objectMapper;
        this.mapsApiKey = mapsApiKey == null ? "" : mapsApiKey.trim();
        this.cacheTtlMs = Math.max(30_000L, cacheTtlMs);
    }

    public OptionalInt estimateTravelMinutes(DarkStore source, DarkStore target) {
        if (source == null || target == null || mapsApiKey.isBlank()) {
            return OptionalInt.empty();
        }

        String cacheKey = buildCacheKey(source, target);
        CacheEntry cached = responseCache.get(cacheKey);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMs() > now) {
            return OptionalInt.of(cached.travelMinutes());
        }

        try {
            String uri = UriComponentsBuilder.fromHttpUrl(DISTANCE_MATRIX_URL)
                    .queryParam("origins", formatCoordinates(source))
                    .queryParam("destinations", formatCoordinates(target))
                    .queryParam("mode", "driving")
                    .queryParam("departure_time", "now")
                    .queryParam("traffic_model", "best_guess")
                    .queryParam("key", mapsApiKey)
                    .build()
                    .toUriString();

            String response = restClient.get()
                    .uri(uri)
                    .retrieve()
                    .body(String.class);

            int minutes = extractMinutes(response);
            responseCache.put(cacheKey, new CacheEntry(minutes, now + cacheTtlMs));
            return OptionalInt.of(minutes);
        }
        catch (Exception ex) {
            log.debug("Traffic lookup failed for {} -> {}: {}", source.getName(), target.getName(), ex.getMessage());
            return OptionalInt.empty();
        }
    }

    private int extractMinutes(String rawResponse) throws Exception {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new IllegalStateException("Empty traffic response.");
        }

        JsonNode root = objectMapper.readTree(rawResponse);
        if (!"OK".equalsIgnoreCase(root.path("status").asText())) {
            throw new IllegalStateException("Distance Matrix status not OK: " + root.path("status").asText());
        }

        JsonNode element = root.path("rows").path(0).path("elements").path(0);
        if (!"OK".equalsIgnoreCase(element.path("status").asText())) {
            throw new IllegalStateException("Distance element status not OK: " + element.path("status").asText());
        }

        int durationSec = element.path("duration_in_traffic").path("value").asInt(0);
        if (durationSec < 1) {
            durationSec = element.path("duration").path("value").asInt(0);
        }
        if (durationSec < 1) {
            throw new IllegalStateException("Missing duration values in traffic response.");
        }
        return Math.max(1, (int) Math.ceil(durationSec / 60.0d));
    }

    private String buildCacheKey(DarkStore source, DarkStore target) {
        String first = formatCoordinates(source);
        String second = formatCoordinates(target);
        if (first.compareTo(second) <= 0) {
            return first + "->" + second;
        }
        return second + "->" + first;
    }

    private String formatCoordinates(DarkStore store) {
        return String.format(
                Locale.ROOT,
                "%.5f,%.5f",
                store.getLatitude(),
                store.getLongitude()
        );
    }

    private record CacheEntry(int travelMinutes, long expiresAtEpochMs) {
    }
}
