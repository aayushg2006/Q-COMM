package com.qcomm.engine.ai;

import jakarta.annotation.PreDestroy;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SLMOrchestrator {

    private static final Pattern INTEGER_PATTERN = Pattern.compile("-?\\d+");

    private final ChatModel chatModel;
    private final long timeoutMs;
    private final ExecutorService executor;
    private final Map<String, Integer> riskCache = new ConcurrentHashMap<>();
    private final Map<String, String> reasonCache = new ConcurrentHashMap<>();

    public SLMOrchestrator(
            ChatModel chatModel,
            @Value("${engine.ai.risk.timeout-ms:700}") long timeoutMs
    ) {
        this.chatModel = chatModel;
        this.timeoutMs = Math.max(200L, timeoutMs);
        this.executor = Executors.newFixedThreadPool(2);
    }

    @PreDestroy
    void shutdownExecutor() {
        executor.shutdownNow();
    }

    public int calculateRiskWeight(String sourceName, String targetName, int baseWeight, String event) {
        return calculateRiskWeight(sourceName, targetName, baseWeight, event, null);
    }

    public int calculateRiskWeight(
            String sourceName,
            String targetName,
            int baseWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        int safeBaseWeight = Math.max(1, baseWeight);
        if (event == null || event.isBlank()) {
            if (observedTrafficWeight == null) {
                return safeBaseWeight;
            }
            return Math.max(1, observedTrafficWeight);
        }

        String cacheKey = buildCacheKey(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        Integer cached = riskCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        int resolved = resolveWeightWithTimeout(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        riskCache.put(cacheKey, resolved);
        return resolved;
    }

    public String explainRiskReason(
            String sourceName,
            String targetName,
            int baseWeight,
            int adjustedWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        if (event == null || event.isBlank()) {
            return null;
        }

        int safeBaseWeight = Math.max(1, baseWeight);
        int safeAdjustedWeight = Math.max(1, adjustedWeight);
        String cacheKey = buildReasonCacheKey(
                sourceName,
                targetName,
                safeBaseWeight,
                safeAdjustedWeight,
                event,
                observedTrafficWeight
        );
        String cached = reasonCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        String resolved = resolveReasonWithTimeout(
                sourceName,
                targetName,
                safeBaseWeight,
                safeAdjustedWeight,
                event,
                observedTrafficWeight
        );
        reasonCache.put(cacheKey, resolved);
        return resolved;
    }

    private int resolveWeightWithTimeout(
            String sourceName,
            String targetName,
            int safeBaseWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        Future<Integer> future = executor.submit(
                () -> resolveWeightWithModel(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight)
        );

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ex) {
            future.cancel(true);
            return heuristicWeight(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return heuristicWeight(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        }
        catch (ExecutionException ex) {
            return heuristicWeight(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        }
    }

    private String resolveReasonWithTimeout(
            String sourceName,
            String targetName,
            int safeBaseWeight,
            int safeAdjustedWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        Future<String> future = executor.submit(
                () -> resolveReasonWithModel(
                        sourceName,
                        targetName,
                        safeBaseWeight,
                        safeAdjustedWeight,
                        event,
                        observedTrafficWeight
                )
        );

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException ex) {
            future.cancel(true);
            return heuristicRiskReason(sourceName, targetName, safeBaseWeight, safeAdjustedWeight, event, observedTrafficWeight);
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            return heuristicRiskReason(sourceName, targetName, safeBaseWeight, safeAdjustedWeight, event, observedTrafficWeight);
        }
        catch (ExecutionException ex) {
            return heuristicRiskReason(sourceName, targetName, safeBaseWeight, safeAdjustedWeight, event, observedTrafficWeight);
        }
    }

    private int resolveWeightWithModel(
            String sourceName,
            String targetName,
            int safeBaseWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String trafficHint = observedTrafficWeight == null
                ? "No live traffic estimate available."
                : "Live traffic estimate (minutes): " + Math.max(1, observedTrafficWeight);

        String prompt = """
            You are a traffic risk analyzer for a quick-commerce restocking system.

            Analyze this route and event:
            - Source dark store: %s
            - Target dark store: %s
            - Baseline travel time: %d
            - Event: %s
            - %s

            Return only ONE integer representing adjusted travel time.
            Strict output rules:
            1) Output digits only.
            2) Do not output words, punctuation, units, or explanations.
            3) The integer must be >= 1.
            """.formatted(sourceName, targetName, safeBaseWeight, event, trafficHint);

        try {
            String rawResponse = chatModel.call(prompt);
            int parsed = parseStrictInteger(rawResponse);
            if (observedTrafficWeight == null) {
                return parsed;
            }
            int safeTraffic = Math.max(1, observedTrafficWeight);
            return Math.max(parsed, (int) Math.round(safeTraffic * 0.9d));
        }
        catch (NumberFormatException ex) {
            return heuristicWeight(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        }
        catch (Exception ex) {
            return heuristicWeight(sourceName, targetName, safeBaseWeight, event, observedTrafficWeight);
        }
    }

    private String resolveReasonWithModel(
            String sourceName,
            String targetName,
            int safeBaseWeight,
            int safeAdjustedWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String trafficHint = observedTrafficWeight == null
                ? "No live traffic estimate available."
                : "Live traffic estimate (minutes): " + Math.max(1, observedTrafficWeight);

        String prompt = """
            You are a traffic operations analyst for quick-commerce routing.

            Explain why this route was marked as risk-adjusted:
            - Source dark store: %s
            - Target dark store: %s
            - Baseline travel time: %d
            - Adjusted travel time: %d
            - Event: %s
            - %s

            Output format rules:
            1) Return exactly one short sentence.
            2) Max 18 words.
            3) Mention only the likely disruption cause and corridor context.
            4) Do not mention AI, model, penalty, or confidence scores.
            """.formatted(sourceName, targetName, safeBaseWeight, safeAdjustedWeight, event, trafficHint);

        try {
            String rawResponse = chatModel.call(prompt);
            String sanitized = sanitizeReason(rawResponse);
            if (!sanitized.isBlank()) {
                return sanitized;
            }
        }
        catch (Exception ignored) {
            // Fall back to deterministic reason when model output is unavailable.
        }

        return heuristicRiskReason(sourceName, targetName, safeBaseWeight, safeAdjustedWeight, event, observedTrafficWeight);
    }

    private int heuristicWeight(
            String sourceName,
            String targetName,
            int baseWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String normalizedEvent = event.toLowerCase(Locale.ROOT);
        String source = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        String target = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);

        double multiplier = 1.0;
        if (normalizedEvent.contains("heavy rain") || normalizedEvent.contains("waterlogging")) {
            multiplier += 0.38;
            multiplier += keywordImpact(source, target, "andheri", 0.14);
            multiplier += keywordImpact(source, target, "whitefield", 0.22);
            multiplier += keywordImpact(source, target, "marathahalli", 0.22);
            multiplier += keywordImpact(source, target, "bellandur", 0.16);
            multiplier += keywordImpact(source, target, "vashi", 0.13);
            multiplier += keywordImpact(source, target, "belapur", 0.14);
            multiplier += keywordImpact(source, target, "hinjewadi", 0.14);
            multiplier += keywordImpact(source, target, "wakad", 0.12);
        }
        else if (normalizedEvent.contains("rain")) {
            multiplier += 0.24;
            multiplier += keywordImpact(source, target, "whitefield", 0.14);
            multiplier += keywordImpact(source, target, "vashi", 0.1);
        }
        else if (normalizedEvent.contains("festival") || normalizedEvent.contains("crowd")) {
            multiplier += 0.28;
            multiplier += keywordImpact(source, target, "bandra", 0.14);
            multiplier += keywordImpact(source, target, "sarjapur", 0.12);
            multiplier += keywordImpact(source, target, "baner", 0.11);
            multiplier += keywordImpact(source, target, "kharghar", 0.12);
        }
        else if (normalizedEvent.contains("roadwork") || normalizedEvent.contains("lane")) {
            multiplier += 0.22;
            multiplier += keywordImpact(source, target, "sion", 0.18);
            multiplier += keywordImpact(source, target, "sarjapur", 0.16);
            multiplier += keywordImpact(source, target, "hinjewadi", 0.15);
            multiplier += keywordImpact(source, target, "belapur", 0.13);
        }
        else if (normalizedEvent.contains("clear")) {
            multiplier += 0.0;
        }
        else {
            multiplier += 0.14;
        }

        // Corridor-sensitive boosts across all configured belts.
        multiplier += corridorBoost(normalizedEvent, source, target, "andheri", 0.14);
        multiplier += corridorBoost(normalizedEvent, source, target, "bandra", 0.12);
        multiplier += corridorBoost(normalizedEvent, source, target, "sion", 0.12);
        multiplier += corridorBoost(normalizedEvent, source, target, "goregaon", 0.08);
        multiplier += corridorBoost(normalizedEvent, source, target, "malad", 0.06);

        multiplier += corridorBoost(normalizedEvent, source, target, "vashi", 0.09);
        multiplier += corridorBoost(normalizedEvent, source, target, "belapur", 0.11);
        multiplier += corridorBoost(normalizedEvent, source, target, "kharghar", 0.08);
        multiplier += corridorBoost(normalizedEvent, source, target, "ghansoli", 0.07);

        multiplier += corridorBoost(normalizedEvent, source, target, "hinjewadi", 0.11);
        multiplier += corridorBoost(normalizedEvent, source, target, "wakad", 0.08);
        multiplier += corridorBoost(normalizedEvent, source, target, "baner", 0.07);
        multiplier += corridorBoost(normalizedEvent, source, target, "kothrud", 0.06);

        multiplier += corridorBoost(normalizedEvent, source, target, "whitefield", 0.12);
        multiplier += corridorBoost(normalizedEvent, source, target, "sarjapur", 0.10);
        multiplier += corridorBoost(normalizedEvent, source, target, "marathahalli", 0.10);
        multiplier += corridorBoost(normalizedEvent, source, target, "bellandur", 0.09);

        // Deterministic per-edge event variance so different testcases can produce different MST paths.
        multiplier += edgeScenarioVariance(source, target, normalizedEvent);

        int weighted = (int) Math.round(baseWeight * multiplier);
        if (observedTrafficWeight == null) {
            return Math.max(1, weighted);
        }

        int safeTrafficWeight = Math.max(1, observedTrafficWeight);
        int blended = (int) Math.round((weighted * 0.65d) + (safeTrafficWeight * 0.35d));
        return Math.max(1, blended);
    }

    private String heuristicRiskReason(
            String sourceName,
            String targetName,
            int safeBaseWeight,
            int safeAdjustedWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String normalizedEvent = event == null ? "" : event.toLowerCase(Locale.ROOT);
        String source = sourceName == null ? "" : sourceName;
        String target = targetName == null ? "" : targetName;

        String cause = "Unexpected traffic load";
        if (normalizedEvent.contains("heavy rain") || normalizedEvent.contains("waterlogging")) {
            cause = "Heavy rain and waterlogging";
        }
        else if (normalizedEvent.contains("peak") || normalizedEvent.contains("congestion")) {
            cause = "Peak-hour congestion";
        }
        else if (normalizedEvent.contains("roadwork") || normalizedEvent.contains("lane")) {
            cause = "Roadwork and lane closures";
        }
        else if (normalizedEvent.contains("festival") || normalizedEvent.contains("crowd")) {
            cause = "Festival crowd movement";
        }

        String corridor = inferCorridor(source, target);
        int delta = Math.max(1, safeAdjustedWeight - safeBaseWeight);
        String trafficContext = observedTrafficWeight == null
                ? ""
                : " with live traffic near " + Math.max(1, observedTrafficWeight) + " minutes";
        return sanitizeReason(
                cause + " around " + corridor + " adds about " + delta + " minutes" + trafficContext + "."
        );
    }

    private boolean touchesCorridor(String normalizedEvent, String source, String target, String keyword) {
        return normalizedEvent.contains(keyword) && (source.contains(keyword) || target.contains(keyword));
    }

    private double corridorBoost(
            String normalizedEvent,
            String source,
            String target,
            String keyword,
            double boost
    ) {
        return touchesCorridor(normalizedEvent, source, target, keyword) ? boost : 0.0d;
    }

    private double edgeScenarioVariance(String source, String target, String normalizedEvent) {
        String stableKey = source + "|" + target + "|" + normalizedEvent;
        int bucket = Math.floorMod(stableKey.hashCode(), 8); // 0..7
        if (normalizedEvent.contains("clear")) {
            return (bucket - 4) * 0.008d; // about +/-3.2%
        }
        return bucket * 0.045d; // 0..31.5% uplift for event scenarios
    }

    private double keywordImpact(String source, String target, String keyword, double impact) {
        return (source.contains(keyword) || target.contains(keyword)) ? impact : 0.0d;
    }

    private String inferCorridor(String sourceName, String targetName) {
        String[] corridors = {
                "andheri", "borivali", "goregaon", "malad", "bandra", "sion",
                "vashi", "belapur", "kharghar", "ghansoli", "airoli",
                "whitefield", "marathahalli", "bellandur", "sarjapur",
                "hinjewadi", "wakad", "baner", "kothrud"
        };
        String source = sourceName == null ? "" : sourceName.toLowerCase(Locale.ROOT);
        String target = targetName == null ? "" : targetName.toLowerCase(Locale.ROOT);
        for (String corridor : corridors) {
            if (source.contains(corridor) || target.contains(corridor)) {
                return capitalize(corridor) + " corridor";
            }
        }
        return "this corridor";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
    }

    private String buildCacheKey(
            String sourceName,
            String targetName,
            int baseWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String safeSource = sourceName == null ? "" : sourceName.trim().toLowerCase(Locale.ROOT);
        String safeTarget = targetName == null ? "" : targetName.trim().toLowerCase(Locale.ROOT);
        String safeEvent = event == null ? "" : event.trim().toLowerCase(Locale.ROOT);
        int safeTraffic = observedTrafficWeight == null ? -1 : Math.max(1, observedTrafficWeight);
        return safeSource + "|" + safeTarget + "|" + baseWeight + "|" + safeEvent + "|" + safeTraffic;
    }

    private String buildReasonCacheKey(
            String sourceName,
            String targetName,
            int baseWeight,
            int adjustedWeight,
            String event,
            Integer observedTrafficWeight
    ) {
        String safeSource = sourceName == null ? "" : sourceName.trim().toLowerCase(Locale.ROOT);
        String safeTarget = targetName == null ? "" : targetName.trim().toLowerCase(Locale.ROOT);
        String safeEvent = event == null ? "" : event.trim().toLowerCase(Locale.ROOT);
        int safeTraffic = observedTrafficWeight == null ? -1 : Math.max(1, observedTrafficWeight);
        return safeSource + "|" + safeTarget + "|" + baseWeight + "|" + adjustedWeight + "|" + safeEvent + "|" + safeTraffic;
    }

    private String sanitizeReason(String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return "";
        }
        String sanitized = rawReason
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replaceAll("\\s+", " ")
                .replace("\"", "")
                .trim();
        if (sanitized.length() > 180) {
            sanitized = sanitized.substring(0, 180).trim();
        }
        if (!sanitized.endsWith(".")) {
            sanitized = sanitized + ".";
        }
        return sanitized;
    }

    private int parseStrictInteger(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new NumberFormatException("Empty model output.");
        }

        Matcher matcher = INTEGER_PATTERN.matcher(rawResponse.strip());
        if (!matcher.find()) {
            throw new NumberFormatException("No integer found in model output.");
        }

        int parsed = Integer.parseInt(matcher.group());
        if (parsed < 1) {
            throw new NumberFormatException("Parsed integer must be >= 1.");
        }
        return parsed;
    }
}
