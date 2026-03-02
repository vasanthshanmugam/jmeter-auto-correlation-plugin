package io.github.vasanthshanmugam.jmeter.plugins.correlation.detection;

import io.github.vasanthshanmugam.jmeter.plugins.correlation.model.CorrelationCandidate;
import io.github.vasanthshanmugam.jmeter.plugins.correlation.model.CorrelationType;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for CorrelationDetector
 */
public class CorrelationDetectorTest {

    private CorrelationDetector detector;

    @Before
    public void setUp() {
        detector = new CorrelationDetector();
    }

    @Test
    public void testDetectSessionIdInJSON() {
        String response = "{\"sessionId\": \"abc123xyz789\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull("Results should not be null", results);
        assertTrue("Should detect at least one candidate", results.size() > 0);

        CorrelationCandidate candidate = results.get(0);
        assertEquals("Should detect sessionId", "sessionId", candidate.getParameterName());
        assertEquals("Should be SESSION_ID type", CorrelationType.SESSION_ID, candidate.getCorrelationType());
    }

    @Test
    public void testDetectCSRFToken() {
        String response = "<input name=\"csrf_token\" value=\"WpwL7BxY83kJnN5dfKVx\" />";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect CSRF token", results.size() > 0);

        CorrelationCandidate candidate = results.get(0);
        assertEquals("Should detect csrf_token", "csrf_token", candidate.getParameterName());
        assertEquals("Should be CSRF_TOKEN type", CorrelationType.CSRF_TOKEN, candidate.getCorrelationType());
    }

    @Test
    public void testDetectViewState() {
        String response = "<input name=\"__VIEWSTATE\" value=\"/wEPDwUKMTY3NjQ5ODEx\" />";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect ViewState", results.size() > 0);

        CorrelationCandidate candidate = results.get(0);
        assertEquals("Should detect __VIEWSTATE", "__VIEWSTATE", candidate.getParameterName());
        assertEquals("Should be VIEWSTATE type", CorrelationType.VIEWSTATE, candidate.getCorrelationType());
    }

    @Test
    public void testMultipleDetections() {
        String response = "{\"sessionId\": \"sess123\", \"csrf_token\": \"token456\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertEquals("Should detect 2 candidates", 2, results.size());
    }

    @Test
    public void testEmptyResponse() {
        String response = "";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertEquals("Should return empty list for empty response", 0, results.size());
    }

    @Test
    public void testNullResponse() {
        List<CorrelationCandidate> results = detector.detectFromResponse(null, null);

        assertNotNull(results);
        assertEquals("Should return empty list for null response", 0, results.size());
    }

    @Test
    public void testConfidenceScore() {
        String response = "{\"sessionId\": \"abc123xyz789ABCDEF\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertTrue("Should have results", results.size() > 0);
        CorrelationCandidate candidate = results.get(0);

        assertTrue("Confidence should be between 0 and 1",
                   candidate.getConfidenceScore() >= 0.0 &&
                   candidate.getConfidenceScore() <= 1.0);
        assertTrue("Session ID should have high confidence",
                   candidate.getConfidenceScore() > 0.5);
    }

    @Test
    public void testDetectJWTToken() {
        String response = "{\"access_token\": \"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect JWT token", results.size() > 0);

        CorrelationCandidate candidate = results.get(0);
        assertEquals("Should be AUTH_TOKEN type", CorrelationType.AUTH_TOKEN, candidate.getCorrelationType());
        assertTrue("Should detect JWT format", candidate.getSampleValue().startsWith("eyJ"));
    }

    @Test
    public void testDetectOAuthToken() {
        String response = "{\"access_token\": \"ya29.a0AfH6SMBx..._longtoken_...\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect OAuth token", results.size() > 0);
        assertEquals("Should be AUTH_TOKEN type", CorrelationType.AUTH_TOKEN, results.get(0).getCorrelationType());
    }

    @Test
    public void testDetectCorrelationID() {
        String response = "{\"x-correlation-id\": \"550e8400-e29b-41d4-a716-446655440000\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect correlation ID", results.size() > 0);
        assertEquals("Should be CORRELATION_ID type", CorrelationType.CORRELATION_ID, results.get(0).getCorrelationType());
    }

    @Test
    public void testDetectNonce() {
        String response = "{\"nonce\": \"abc123def456\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect nonce", results.size() > 0);
        assertEquals("Should be NONCE type", CorrelationType.NONCE, results.get(0).getCorrelationType());
    }

    @Test
    public void testDetectTimestamp() {
        String response = "{\"timestamp\": \"1704067200\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect timestamp", results.size() > 0);
        assertEquals("Should be TIMESTAMP type", CorrelationType.TIMESTAMP, results.get(0).getCorrelationType());
    }

    @Test
    public void testDetectMultipleTokenTypes() {
        String response = "{\"sessionId\": \"sess123\", \"access_token\": \"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0In0.SflKxwRJSMeKKF2QT4fwp\", \"nonce\": \"abc123xyz\"}";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect multiple tokens", results.size() >= 3);
    }

    @Test
    public void testDetectSpringSecurityToken() {
        String response = "<meta name=\"_csrf\" content=\"4c9f7e2a-8b3d-4f1e-9a6c-5d8e7f3b2a1c\"/>";

        List<CorrelationCandidate> results = detector.detectFromResponse(response, null);

        assertNotNull(results);
        assertTrue("Should detect Spring Security CSRF token", results.size() > 0);
        assertEquals("Should be CSRF_TOKEN type", CorrelationType.CSRF_TOKEN, results.get(0).getCorrelationType());
    }
}
