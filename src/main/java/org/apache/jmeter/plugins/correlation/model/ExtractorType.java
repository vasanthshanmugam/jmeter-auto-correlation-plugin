package org.apache.jmeter.plugins.correlation.model;

/**
 * Types of extractors that can be used for correlation.
 * Each type corresponds to a JMeter post-processor component.
 */
public enum ExtractorType {
    
    /**
     * Regular Expression Extractor
     * Best for: HTML, text responses with patterns
     */
    REGEX("Regular Expression"),
    
    /**
     * JSON Extractor (JSONPath)
     * Best for: JSON responses
     */
    JSON("JSON Path"),
    
    /**
     * XPath Extractor
     * Best for: XML responses
     */
    XPATH("XPath"),
    
    /**
     * Boundary Extractor
     * Best for: Simple text between two boundaries
     */
    BOUNDARY("Boundary"),
    
    /**
     * CSS/jQuery Extractor
     * Best for: HTML with CSS selectors
     */
    CSS_JQUERY("CSS/jQuery");
    
    private final String displayName;
    
    ExtractorType(String displayName) {
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
