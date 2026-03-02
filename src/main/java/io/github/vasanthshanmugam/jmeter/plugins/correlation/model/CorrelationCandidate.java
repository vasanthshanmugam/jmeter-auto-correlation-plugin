package io.github.vasanthshanmugam.jmeter.plugins.correlation.model;

import org.apache.jmeter.samplers.AbstractSampler;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a detected correlation candidate.
 * This is a dynamic value that appears in a response and is used in subsequent requests.
 */
public class CorrelationCandidate {

    // The parameter name (e.g., "sessionId", "csrf_token")
    private String parameterName;

    // Example value found during detection
    private String sampleValue;

    // The HTTP sampler where this value was found in the response
    private AbstractSampler sourceResponse;

    // List of HTTP samplers that use this value in their requests
    private List<AbstractSampler> targetRequests;

    // What type of extractor should be used (REGEX, JSON, XPATH, etc.)
    private ExtractorType extractorType;

    // The pattern/path to extract the value (e.g., regex pattern, JSONPath, XPath)
    private String extractionPattern;

    // Confidence score (0.0 to 1.0) - how confident are we this needs correlation
    private double confidenceScore;

    // What type of correlation this is (SESSION_ID, CSRF_TOKEN, etc.)
    private CorrelationType correlationType;

    // The variable name to use when extracting (e.g., "SESSION_ID")
    private String variableName;

    // Index of the source sampler in execution order (for reliable tree matching)
    private int sourceSamplerIndex = -1;

    // Display name of the source sampler
    private String sourceSamplerName;

    // Indices of all target samplers in execution order
    private List<Integer> targetSamplerIndices;

    /**
     * Constructor
     */
    public CorrelationCandidate() {
        this.targetRequests = new ArrayList<>();
        this.targetSamplerIndices = new ArrayList<>();
        this.confidenceScore = 0.0;
    }

    /**
     * Full constructor
     */
    public CorrelationCandidate(String parameterName, String sampleValue,
                                AbstractSampler sourceResponse,
                                ExtractorType extractorType) {
        this.parameterName = parameterName;
        this.sampleValue = sampleValue;
        this.sourceResponse = sourceResponse;
        this.extractorType = extractorType;
        this.targetRequests = new ArrayList<>();
        this.targetSamplerIndices = new ArrayList<>();
        this.confidenceScore = 0.0;
    }

    // Getters and Setters

    public String getParameterName() {
        return parameterName;
    }

    public void setParameterName(String parameterName) {
        this.parameterName = parameterName;
    }

    public String getSampleValue() {
        return sampleValue;
    }

    public void setSampleValue(String sampleValue) {
        this.sampleValue = sampleValue;
    }

    public AbstractSampler getSourceResponse() {
        return sourceResponse;
    }

    public void setSourceResponse(AbstractSampler sourceResponse) {
        this.sourceResponse = sourceResponse;
    }

    public List<AbstractSampler> getTargetRequests() {
        return targetRequests;
    }

    public void setTargetRequests(List<AbstractSampler> targetRequests) {
        this.targetRequests = targetRequests;
    }

    public void addTargetRequest(AbstractSampler request) {
        if (!this.targetRequests.contains(request)) {
            this.targetRequests.add(request);
        }
    }

    public ExtractorType getExtractorType() {
        return extractorType;
    }

    public void setExtractorType(ExtractorType extractorType) {
        this.extractorType = extractorType;
    }

    public String getExtractionPattern() {
        return extractionPattern;
    }

    public void setExtractionPattern(String extractionPattern) {
        this.extractionPattern = extractionPattern;
    }

    public double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }

    public CorrelationType getCorrelationType() {
        return correlationType;
    }

    public void setCorrelationType(CorrelationType correlationType) {
        this.correlationType = correlationType;
    }

    public String getVariableName() {
        return variableName;
    }

    public void setVariableName(String variableName) {
        this.variableName = variableName;
    }

    public int getSourceSamplerIndex() {
        return sourceSamplerIndex;
    }

    public void setSourceSamplerIndex(int sourceSamplerIndex) {
        this.sourceSamplerIndex = sourceSamplerIndex;
    }

    public String getSourceSamplerName() {
        return sourceSamplerName;
    }

    public void setSourceSamplerName(String sourceSamplerName) {
        this.sourceSamplerName = sourceSamplerName;
    }

    public List<Integer> getTargetSamplerIndices() {
        return targetSamplerIndices;
    }

    public void addTargetSamplerIndex(int index) {
        if (!this.targetSamplerIndices.contains(index)) {
            this.targetSamplerIndices.add(index);
        }
    }

    /**
     * Get confidence level as a human-readable string
     */
    public String getConfidenceLevel() {
        if (confidenceScore >= 0.8) {
            return "High";
        } else if (confidenceScore >= 0.5) {
            return "Medium";
        } else {
            return "Low";
        }
    }

    /**
     * Get the number of requests that use this value
     */
    public int getUsageCount() {
        return targetRequests.size();
    }

    @Override
    public String toString() {
        return "CorrelationCandidate{" +
                "parameterName='" + parameterName + '\'' +
                ", sampleValue='" + sampleValue + '\'' +
                ", extractorType=" + extractorType +
                ", confidenceScore=" + confidenceScore +
                ", usageCount=" + getUsageCount() +
                '}';
    }
}
