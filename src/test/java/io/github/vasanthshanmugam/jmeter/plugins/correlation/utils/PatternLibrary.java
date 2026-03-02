package io.github.vasanthshanmugam.jmeter.plugins.correlation.utils;

import io.github.vasanthshanmugam.jmeter.plugins.correlation.model.CorrelationType;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

/**
 * Library of correlation patterns for different frameworks and technologies.
 * This class provides categorized patterns for easy management and extension.
 */
public class PatternLibrary {

    /**
     * Pattern definition with metadata
     */
    public static class PatternDefinition {
        private final Pattern pattern;
        private final CorrelationType type;
        private final String description;
        private final double baseConfidence;

        public PatternDefinition(Pattern pattern, CorrelationType type, String description, double baseConfidence) {
            this.pattern = pattern;
            this.type = type;
            this.description = description;
            this.baseConfidence = baseConfidence;
        }

        public Pattern getPattern() { return pattern; }
        public CorrelationType getType() { return type; }
        public String getDescription() { return description; }
        public double getBaseConfidence() { return baseConfidence; }
    }

    // Framework-specific patterns
    private static final Map<String, PatternDefinition> FRAMEWORK_PATTERNS = new HashMap<>();

    static {
        // ASP.NET Patterns
        FRAMEWORK_PATTERNS.put("ASPNET_VIEWSTATE", new PatternDefinition(
            Pattern.compile("\"?(__VIEWSTATE|__EVENTVALIDATION)\"?\\s+value\\s*=\\s*\"([A-Za-z0-9+/=]{20,})\"", Pattern.CASE_INSENSITIVE),
            CorrelationType.VIEWSTATE,
            "ASP.NET ViewState and EventValidation",
            0.95
        ));

        FRAMEWORK_PATTERNS.put("ASPNET_ANTIFORGERY", new PatternDefinition(
            Pattern.compile("\"?(__RequestVerificationToken)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{20,256})\"", Pattern.CASE_INSENSITIVE),
            CorrelationType.CSRF_TOKEN,
            "ASP.NET Anti-Forgery Token",
            0.90
        ));

        // Spring Framework Patterns
        FRAMEWORK_PATTERNS.put("SPRING_CSRF", new PatternDefinition(
            Pattern.compile("\"?(_csrf|X-CSRF-TOKEN)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{20,128})\"", Pattern.CASE_INSENSITIVE),
            CorrelationType.CSRF_TOKEN,
            "Spring Security CSRF Token",
            0.90
        ));

        // WordPress Patterns
        FRAMEWORK_PATTERNS.put("WORDPRESS_NONCE", new PatternDefinition(
            Pattern.compile("\"?(_wpnonce|security)\"?\\s*[:=]\\s*\"([A-Za-z0-9]{10,20})\"", Pattern.CASE_INSENSITIVE),
            CorrelationType.NONCE,
            "WordPress Nonce",
            0.85
        ));

        // Ruby on Rails Patterns
        FRAMEWORK_PATTERNS.put("RAILS_CSRF", new PatternDefinition(
            Pattern.compile("\"?(authenticity_token)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{20,128})\"", Pattern.CASE_INSENSITIVE),
            CorrelationType.CSRF_TOKEN,
            "Ruby on Rails Authenticity Token",
            0.90
        ));
    }

    /**
     * Get pattern description for logging/display
     */
    public static String getPatternDescription(String patternKey) {
        PatternDefinition def = FRAMEWORK_PATTERNS.get(patternKey);
        return def != null ? def.getDescription() : "Unknown pattern";
    }

    /**
     * Get base confidence for a pattern
     */
    public static double getBaseConfidence(String patternKey) {
        PatternDefinition def = FRAMEWORK_PATTERNS.get(patternKey);
        return def != null ? def.getBaseConfidence() : 0.5;
    }

    /**
     * Check if a value looks like a specific type based on format
     */
    public static boolean looksLikeJWT(String value) {
        return value != null && value.matches("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+");
    }

    public static boolean looksLikeUUID(String value) {
        return value != null && value.matches("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    }

    public static boolean looksLikeBase64(String value) {
        return value != null && value.matches("[A-Za-z0-9+/=]{20,}");
    }

    public static boolean looksLikeHex(String value) {
        return value != null && value.matches("[a-fA-F0-9]{16,}");
    }

    public static boolean looksLikeTimestamp(String value) {
        if (value == null) return false;
        try {
            long timestamp = Long.parseLong(value);
            // Unix timestamp (seconds) or milliseconds
            return (timestamp >= 1000000000 && timestamp <= 9999999999L) ||
                   (timestamp >= 1000000000000L && timestamp <= 9999999999999L);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Calculate entropy (randomness) of a string
     * Higher entropy = more random = more likely to be dynamic
     */
    public static double calculateEntropy(String value) {
        if (value == null || value.isEmpty()) return 0.0;

        Map<Character, Integer> frequencies = new HashMap<>();
        for (char c : value.toCharArray()) {
            frequencies.put(c, frequencies.getOrDefault(c, 0) + 1);
        }

        double entropy = 0.0;
        int length = value.length();
        for (int freq : frequencies.values()) {
            double probability = (double) freq / length;
            entropy -= probability * (Math.log(probability) / Math.log(2));
        }

        return entropy;
    }

    /**
     * Estimate value type based on format analysis
     */
    public static CorrelationType estimateType(String paramName, String value) {
        if (looksLikeJWT(value)) return CorrelationType.AUTH_TOKEN;
        if (looksLikeUUID(value)) return CorrelationType.CORRELATION_ID;
        if (looksLikeTimestamp(value)) return CorrelationType.TIMESTAMP;

        // Based on parameter name
        String lower = paramName.toLowerCase();
        if (lower.contains("session")) return CorrelationType.SESSION_ID;
        if (lower.contains("csrf") || lower.contains("token")) return CorrelationType.CSRF_TOKEN;
        if (lower.contains("nonce")) return CorrelationType.NONCE;
        if (lower.contains("key") || lower.contains("auth")) return CorrelationType.AUTH_TOKEN;

        return CorrelationType.CUSTOM;
    }
}
