package org.apache.jmeter.plugins.correlation.detection;

import org.apache.jmeter.plugins.correlation.model.CorrelationCandidate;
import org.apache.jmeter.plugins.correlation.model.CorrelationType;
import org.apache.jmeter.plugins.correlation.model.ExtractorType;
import org.apache.jmeter.samplers.AbstractSampler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Main correlation detection engine.
 * Analyzes JMeter test plans to find dynamic values that need correlation.
 */
public class CorrelationDetector {
    
   private static final Logger log = LoggerFactory.getLogger(CorrelationDetector.class);
    
    // Common session ID patterns (JSON/query-param format with quoted value)
    // Matches: sessionId: "value", sessionId="value", sessionid='value', etc.
    private static final Pattern SESSION_ID_PATTERN = Pattern.compile(
        "\"?(sessionid|session_id|jsessionid|phpsessid|aspsessionid|sid)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{6,128})\"",
        Pattern.CASE_INSENSITIVE
    );

    // Set-Cookie / Cookie header format: JSESSIONID=VALUE; (unquoted, terminated by ; or whitespace)
    // Matches: Set-Cookie: JSESSIONID=ABC123; Path=/   or   Cookie: JSESSIONID=ABC123
    private static final Pattern SET_COOKIE_SESSION_PATTERN = Pattern.compile(
        "\\b(jsessionid|phpsessid|aspsessionid|sessionid|session_id|sid)=([A-Za-z0-9_-]{16,128})(?:[;\\s\"']|$)",
        Pattern.CASE_INSENSITIVE
    );

    // URL-embedded session ID (Java EE URL rewriting, e.g., ;jsessionid=HEXVALUE in hrefs)
    // The value is unquoted, ends at ", >, ?, &, ; or whitespace
    private static final Pattern URL_SESSION_ID_PATTERN = Pattern.compile(
        ";(jsessionid)=([A-Fa-f0-9]{16,64})(?=[\"'>?&;\\s]|$)",
        Pattern.CASE_INSENSITIVE
    );
    
    // Common CSRF token patterns
    // Matches both:
    // - JSON: "csrf_token": "value"
    // - HTML: name="csrf_token" value="xyz" OR csrf_token="xyz"
    private static final Pattern CSRF_TOKEN_PATTERN = Pattern.compile(
        "(?:name=)?\"?(csrf|_csrf|csrf_token|_token|authenticity_token|__RequestVerificationToken)\"?\\s*(?:value\\s*=|[:=])\\s*\"([A-Za-z0-9_-]{6,128})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // ASP.NET ViewState pattern
    // Matches: name="__VIEWSTATE" value="xyz", __VIEWSTATE="xyz"
    private static final Pattern VIEWSTATE_PATTERN = Pattern.compile(
        "\"?(__VIEWSTATE|__EVENTVALIDATION)\"?\\s+value\\s*=\\s*\"([A-Za-z0-9+/=]{20,})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // JWT Token pattern (JSON Web Token)
    // Format: eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
    private static final Pattern JWT_TOKEN_PATTERN = Pattern.compile(
        "\"?(token|access_token|id_token|auth_token|jwt|bearer)\"?\\s*[:=]\\s*\"?(eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)\"?",
        Pattern.CASE_INSENSITIVE
    );
    
    // OAuth Access Token pattern.
    // The \\\\? prefix on each quote handles BOTH normal JSON  ("access_token": "val")
    // AND escaped JSON  (\"access_token\": \"val\") as found in postman-echo-style
    // responses that embed the raw request body inside a "data" string field.
    private static final Pattern OAUTH_TOKEN_PATTERN = Pattern.compile(
        "\\\\?\"?(access_token|refresh_token|bearer_token)\\\\?\"?\\s*[:=]\\s*\\\\?\"([A-Za-z0-9_.-]{8,512})\\\\?\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Correlation ID / Request ID / Trace ID patterns
    private static final Pattern CORRELATION_ID_PATTERN = Pattern.compile(
        "\"?(x-correlation-id|correlation-id|correlationid|request-id|requestid|trace-id|traceid|x-request-id|x-trace-id)\"?\\s*[:=]\\s*\"([a-f0-9-]{20,128})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Nonce pattern (number used once)
    private static final Pattern NONCE_PATTERN = Pattern.compile(
        "\"?(nonce|_nonce|wp_nonce)\"?\\s*[:=]\\s*\"([A-Za-z0-9]{8,64})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Timestamp patterns (Unix timestamp, milliseconds)
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
        "\"?(timestamp|_timestamp|ts|_ts|time)\"?\\s*[:=]\\s*\"?(\\d{10,13})\"?",
        Pattern.CASE_INSENSITIVE
    );
    
    // API Key pattern
    private static final Pattern API_KEY_PATTERN = Pattern.compile(
        "\"?(api_key|apikey|key|x-api-key)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{16,128})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Transaction ID / Order ID patterns
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile(
        "\"?(transaction_id|transactionid|order_id|orderid|invoice_id)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{8,64})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // SAML Token pattern
    private static final Pattern SAML_TOKEN_PATTERN = Pattern.compile(
        "\"?(SAMLResponse|SAMLRequest|RelayState)\"?\\s*[:=]\\s*\"([A-Za-z0-9+/=]{50,})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Spring Security patterns
    private static final Pattern SPRING_SECURITY_PATTERN = Pattern.compile(
        "(?:name=)?\"?(_csrf|X-CSRF-TOKEN)\"?\\s*(?:content\\s*=|value\\s*=|[:=])\\s*\"([A-Za-z0-9_-]{20,128})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // WordPress nonce pattern
    private static final Pattern WORDPRESS_NONCE_PATTERN = Pattern.compile(
        "\"?(_wpnonce|security)\"?\\s*[:=]\\s*\"([A-Za-z0-9]{10,20})\"",
        Pattern.CASE_INSENSITIVE
    );
    
    // Anti-forgery token (various frameworks)
    private static final Pattern ANTIFORGERY_PATTERN = Pattern.compile(
        "\"?(__RequestVerificationToken|AntiForgeryToken|VerificationToken)\"?\\s*[:=]\\s*\"([A-Za-z0-9_-]{20,256})\"",
        Pattern.CASE_INSENSITIVE
    );

    // HTML <input> tag scanner (matches self-closing and normal closing)
    private static final Pattern INPUT_TAG_PATTERN = Pattern.compile(
        "<input[^>]*(?:/>|>)",
        Pattern.CASE_INSENSITIVE
    );

    // Checks that an input tag has type="hidden" (or type=hidden without quotes)
    private static final Pattern HIDDEN_TYPE_PATTERN = Pattern.compile(
        "\\btype=[\"']?hidden[\"']?",
        Pattern.CASE_INSENSITIVE
    );

    // Extracts name attribute from an input tag
    private static final Pattern NAME_ATTR_PATTERN = Pattern.compile(
        "\\bname=[\"']([^\"']+)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    // Extracts value attribute from an input tag
    private static final Pattern VALUE_ATTR_PATTERN = Pattern.compile(
        "\\bvalue=[\"']([^\"']*)[\"']",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Detect correlations in a single HTTP response
     * This is a simple version - we'll expand this later
     */
    public List<CorrelationCandidate> detectFromResponse(String responseBody, AbstractSampler sampler) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        
        if (responseBody == null || responseBody.isEmpty()) {
            return candidates;
        }
        
        // Log only if sampler is not null (in tests it might be null)
        if (sampler != null) {
            log.info("Analyzing response from: " + sampler.getName());
        } else {
            log.debug("Analyzing response (no sampler context)");
        }
        
        // Detect session IDs (quoted format: sessionId="value")
        candidates.addAll(detectPattern(responseBody, SESSION_ID_PATTERN,
                                       CorrelationType.SESSION_ID, sampler));

        // Detect session IDs in Set-Cookie / Cookie format: JSESSIONID=VALUE; (unquoted)
        candidates.addAll(detectPattern(responseBody, SET_COOKIE_SESSION_PATTERN,
                                       CorrelationType.SESSION_ID, sampler));

        // Detect URL-embedded session IDs (Java EE URL rewriting: ;jsessionid=HEX in hrefs)
        candidates.addAll(detectPattern(responseBody, URL_SESSION_ID_PATTERN,
                                       CorrelationType.SESSION_ID, sampler));

        // Detect CSRF tokens
        candidates.addAll(detectPattern(responseBody, CSRF_TOKEN_PATTERN, 
                                       CorrelationType.CSRF_TOKEN, sampler));
        
        // Detect ViewState
        candidates.addAll(detectPattern(responseBody, VIEWSTATE_PATTERN, 
                                       CorrelationType.VIEWSTATE, sampler));
        
        // Detect JWT tokens
        candidates.addAll(detectPattern(responseBody, JWT_TOKEN_PATTERN, 
                                       CorrelationType.AUTH_TOKEN, sampler));
        
        // Detect OAuth tokens
        candidates.addAll(detectPattern(responseBody, OAUTH_TOKEN_PATTERN, 
                                       CorrelationType.AUTH_TOKEN, sampler));
        
        // Detect Correlation/Request/Trace IDs
        candidates.addAll(detectPattern(responseBody, CORRELATION_ID_PATTERN, 
                                       CorrelationType.CORRELATION_ID, sampler));
        
        // Detect Nonces
        candidates.addAll(detectPattern(responseBody, NONCE_PATTERN, 
                                       CorrelationType.NONCE, sampler));
        
        // Detect Timestamps
        candidates.addAll(detectPattern(responseBody, TIMESTAMP_PATTERN, 
                                       CorrelationType.TIMESTAMP, sampler));
        
        // Detect API Keys
        candidates.addAll(detectPattern(responseBody, API_KEY_PATTERN, 
                                       CorrelationType.CUSTOM, sampler));
        
        // Detect Transaction IDs
        candidates.addAll(detectPattern(responseBody, TRANSACTION_ID_PATTERN, 
                                       CorrelationType.CUSTOM, sampler));
        
        // Detect SAML tokens
        candidates.addAll(detectPattern(responseBody, SAML_TOKEN_PATTERN, 
                                       CorrelationType.AUTH_TOKEN, sampler));
        
        // Detect Spring Security tokens
        candidates.addAll(detectPattern(responseBody, SPRING_SECURITY_PATTERN, 
                                       CorrelationType.CSRF_TOKEN, sampler));
        
        // Detect WordPress nonces
        candidates.addAll(detectPattern(responseBody, WORDPRESS_NONCE_PATTERN, 
                                       CorrelationType.NONCE, sampler));
        
        // Detect Anti-forgery tokens
        candidates.addAll(detectPattern(responseBody, ANTIFORGERY_PATTERN,
                                       CorrelationType.CSRF_TOKEN, sampler));

        // Detect hidden HTML form fields (e.g., _sourcePage, __fp, framework tokens)
        candidates.addAll(detectHiddenFields(responseBody, sampler));

        log.info("Found " + candidates.size() + " correlation candidates");

        return candidates;
    }

    /**
     * Detect hidden HTML form fields in an HTML response body.
     * Finds all {@code <input type="hidden" name="X" value="Y">} elements where
     * the value looks dynamic (long, encoded), e.g., WebWork _sourcePage / __fp tokens.
     */
    private List<CorrelationCandidate> detectHiddenFields(String responseBody, AbstractSampler sampler) {
        List<CorrelationCandidate> candidates = new ArrayList<>();

        Matcher inputMatcher = INPUT_TAG_PATTERN.matcher(responseBody);
        while (inputMatcher.find()) {
            String inputTag = inputMatcher.group();

            // Only process hidden inputs
            if (!HIDDEN_TYPE_PATTERN.matcher(inputTag).find()) {
                continue;
            }

            Matcher nameMatcher = NAME_ATTR_PATTERN.matcher(inputTag);
            Matcher valueMatcher = VALUE_ATTR_PATTERN.matcher(inputTag);

            if (!nameMatcher.find() || !valueMatcher.find()) {
                continue;
            }

            String paramName = nameMatcher.group(1);
            String value = valueMatcher.group(1);

            // Skip trivially short or static-looking values
            if (!looksLikeDynamicValue(value)) {
                continue;
            }

            log.debug("Detected HIDDEN_FIELD: " + paramName + " = " + value.substring(0, Math.min(20, value.length())) + "...");

            CorrelationCandidate candidate = new CorrelationCandidate();
            candidate.setParameterName(paramName);
            candidate.setSampleValue(value);
            candidate.setSourceResponse(sampler);
            candidate.setCorrelationType(CorrelationType.HIDDEN_FIELD);
            candidate.setExtractorType(ExtractorType.REGEX);

            // High confidence: hidden fields with long encoded values are almost always dynamic
            double confidence = value.length() > 32 ? 0.9 : 0.8;
            candidate.setConfidenceScore(confidence);

            candidate.setVariableName(generateVariableName(paramName, CorrelationType.HIDDEN_FIELD));
            candidates.add(candidate);
        }

        return candidates;
    }

    /**
     * Returns true if a value looks dynamically generated rather than a static constant.
     * Requires length >= 12 and either base64/URL-safe characters or length >= 32.
     */
    private boolean looksLikeDynamicValue(String value) {
        if (value == null || value.length() < 12) return false;
        boolean hasEncodingChars = value.contains("+") || value.contains("/")
                || value.contains("=") || value.contains("-") || value.contains("_");
        return hasEncodingChars || value.length() >= 32;
    }
    
    /**
     * Detect values matching a specific pattern
     */
    private List<CorrelationCandidate> detectPattern(String responseBody,
                                                     Pattern pattern,
                                                     CorrelationType type,
                                                     AbstractSampler sampler) {
        List<CorrelationCandidate> candidates = new ArrayList<>();
        Matcher matcher = pattern.matcher(responseBody);
        
        while (matcher.find()) {
            String parameterName = matcher.group(1);
            String value = matcher.group(2);
            
            log.debug("Detected " + type + ": " + parameterName + " = " + value);
            
            CorrelationCandidate candidate = new CorrelationCandidate();
            candidate.setParameterName(parameterName);
            candidate.setSampleValue(value);
            candidate.setSourceResponse(sampler);
            candidate.setCorrelationType(type);
            
            // Determine extractor type based on response content
            ExtractorType extractorType = determineExtractorType(responseBody, type);
            candidate.setExtractorType(extractorType);
            
            // Calculate confidence score
            double confidence = calculateConfidence(value, type);
            candidate.setConfidenceScore(confidence);
            
            // Generate variable name
            String varName = generateVariableName(parameterName, type);
            candidate.setVariableName(varName);
            
            candidates.add(candidate);
        }
        
        return candidates;
    }
    
    /**
     * Determine which extractor type to use based on response content type.
     *
     * Always returns REGEX. JSONPath extraction is more fragile because the
     * generated path (e.g. $.access_token) only works for top-level fields.
     * Nested responses (e.g. postman-echo's $.json.access_token) would silently
     * return empty. The REGEX extractor is applied to the raw response body and
     * reliably finds the value regardless of nesting depth.
     */
    private ExtractorType determineExtractorType(String responseBody, CorrelationType type) {
        return ExtractorType.REGEX;
    }
    
    /**
     * Calculate confidence score for a detected value
     */
    private double calculateConfidence(String value, CorrelationType type) {
        double score = 0.5; // Base score
        
        // Known types get higher confidence
        if (type == CorrelationType.SESSION_ID || 
            type == CorrelationType.CSRF_TOKEN || 
            type == CorrelationType.VIEWSTATE) {
            score += 0.3;
        }
        
        // Longer values (more random) = higher confidence
        if (value.length() > 32) {
            score += 0.1;
        }
        
        // Contains mix of characters = higher confidence
        if (containsMixedCharacters(value)) {
            score += 0.1;
        }
        
        return Math.min(score, 1.0); // Cap at 1.0
    }
    
    /**
     * Check if value contains mixed characters (more likely to be dynamic)
     */
    private boolean containsMixedCharacters(String value) {
        boolean hasUpper = !value.equals(value.toLowerCase());
        boolean hasLower = !value.equals(value.toUpperCase());
        boolean hasDigit = value.matches(".*\\d.*");
        
        int mixCount = 0;
        if (hasUpper) mixCount++;
        if (hasLower) mixCount++;
        if (hasDigit) mixCount++;
        
        return mixCount >= 2;
    }
    
    /**
     * Generate a variable name from parameter name
     */
    private String generateVariableName(String parameterName, CorrelationType type) {
        // Remove special characters and convert to uppercase
        String varName = parameterName.replaceAll("[^a-zA-Z0-9_]", "_").toUpperCase();
        
        // Add prefix if not already present
        if (!varName.startsWith(type.name())) {
            varName = type.name() + "_" + varName;
        }
        
        return varName;
    }
    
    /**
     * Simple test method - we'll remove this later
     */
    public static void main(String[] args) {
        CorrelationDetector detector = new CorrelationDetector();
        
        // Test with sample response
        String testResponse = "{\"sessionId\": \"abc123xyz789\", \"csrf_token\": \"WpwL7BxY83kJnN5dfKVx\"}";
        
        System.out.println("Testing CorrelationDetector...");
        System.out.println("Test Response: " + testResponse);
        
        List<CorrelationCandidate> results = detector.detectFromResponse(testResponse, null);
        
        System.out.println("\nDetected " + results.size() + " candidates:");
        for (CorrelationCandidate candidate : results) {
            System.out.println("  - " + candidate.getParameterName() + 
                             " (" + candidate.getCorrelationType() + ")" +
                             " Confidence: " + candidate.getConfidenceScore());
        }
    }
}
