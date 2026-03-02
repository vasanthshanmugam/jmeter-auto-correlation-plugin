package io.github.vasanthshanmugam.jmeter.plugins.correlation.model;

/**
 * Types of correlations that can be detected.
 * Each type represents a common pattern in web applications.
 */
public enum CorrelationType {

    /**
     * Session ID - tracks user session
     * Examples: JSESSIONID, PHPSESSID, ASP.NET_SessionId
     */
    SESSION_ID("Session ID"),

    /**
     * CSRF Token - Cross-Site Request Forgery protection
     * Examples: csrf_token, _token, authenticity_token
     */
    CSRF_TOKEN("CSRF Token"),

    /**
     * ASP.NET ViewState
     */
    VIEWSTATE("ViewState"),

    /**
     * Correlation ID for distributed tracing
     */
    CORRELATION_ID("Correlation ID"),

    /**
     * Timestamp values
     */
    TIMESTAMP("Timestamp"),

    /**
     * Nonce - number used once for security
     */
    NONCE("Nonce"),

    /**
     * OAuth/JWT tokens
     */
    AUTH_TOKEN("Auth Token"),

    /**
     * HTML hidden form field token
     * Examples: _sourcePage, __fp, __RequestVerificationToken (WebWork/Struts/Spring hidden inputs)
     */
    HIDDEN_FIELD("Hidden Field"),

    /**
     * Custom/unknown type
     */
    CUSTOM("Custom");

    private final String displayName;

    CorrelationType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
