package org.apache.jmeter.plugins.correlation.config;

import java.io.*;
import java.util.Properties;

/**
 * Plugin configuration management.
 * Stores user preferences and plugin settings.
 */
public class PluginConfiguration {
    
    private static final String CONFIG_FILE = "jmeter-autocorrelation.properties";
    private static PluginConfiguration instance;
    
    private Properties properties;
    
    // Configuration keys
    public static final String DETECTION_SENSITIVITY = "detection.sensitivity";
    public static final String MIN_CONFIDENCE_THRESHOLD = "detection.min_confidence";
    public static final String AUTO_SELECT_HIGH_CONFIDENCE = "detection.auto_select_high";
    public static final String VARIABLE_PREFIX = "extraction.variable_prefix";
    public static final String BACKUP_BEFORE_APPLY = "application.backup_before_apply";
    public static final String ENABLE_SESSION_ID_DETECTION = "detection.enable_session_id";
    public static final String ENABLE_CSRF_DETECTION = "detection.enable_csrf";
    public static final String ENABLE_VIEWSTATE_DETECTION = "detection.enable_viewstate";
    public static final String ENABLE_JWT_DETECTION = "detection.enable_jwt";
    public static final String ENABLE_OAUTH_DETECTION = "detection.enable_oauth";
    public static final String ENABLE_CORRELATION_ID_DETECTION = "detection.enable_correlation_id";
    public static final String ENABLE_NONCE_DETECTION = "detection.enable_nonce";
    public static final String ENABLE_TIMESTAMP_DETECTION = "detection.enable_timestamp";
    
    // Default values
    private static final String DEFAULT_SENSITIVITY = "MEDIUM";
    private static final double DEFAULT_MIN_CONFIDENCE = 0.5;
    private static final boolean DEFAULT_AUTO_SELECT = true;
    private static final String DEFAULT_VARIABLE_PREFIX = "";
    private static final boolean DEFAULT_BACKUP = true;
    
    private PluginConfiguration() {
        properties = new Properties();
        loadDefaults();
        loadFromFile();
    }
    
    public static synchronized PluginConfiguration getInstance() {
        if (instance == null) {
            instance = new PluginConfiguration();
        }
        return instance;
    }
    
    private void loadDefaults() {
        properties.setProperty(DETECTION_SENSITIVITY, DEFAULT_SENSITIVITY);
        properties.setProperty(MIN_CONFIDENCE_THRESHOLD, String.valueOf(DEFAULT_MIN_CONFIDENCE));
        properties.setProperty(AUTO_SELECT_HIGH_CONFIDENCE, String.valueOf(DEFAULT_AUTO_SELECT));
        properties.setProperty(VARIABLE_PREFIX, DEFAULT_VARIABLE_PREFIX);
        properties.setProperty(BACKUP_BEFORE_APPLY, String.valueOf(DEFAULT_BACKUP));
        
        // Enable all detection types by default
        properties.setProperty(ENABLE_SESSION_ID_DETECTION, "true");
        properties.setProperty(ENABLE_CSRF_DETECTION, "true");
        properties.setProperty(ENABLE_VIEWSTATE_DETECTION, "true");
        properties.setProperty(ENABLE_JWT_DETECTION, "true");
        properties.setProperty(ENABLE_OAUTH_DETECTION, "true");
        properties.setProperty(ENABLE_CORRELATION_ID_DETECTION, "true");
        properties.setProperty(ENABLE_NONCE_DETECTION, "true");
        properties.setProperty(ENABLE_TIMESTAMP_DETECTION, "true");
    }
    
    private void loadFromFile() {
        File configFile = getConfigFile();
        if (configFile.exists()) {
            try (FileInputStream fis = new FileInputStream(configFile)) {
                properties.load(fis);
            } catch (IOException e) {
                System.err.println("Failed to load configuration: " + e.getMessage());
            }
        }
    }
    
    public void saveToFile() {
        File configFile = getConfigFile();
        try (FileOutputStream fos = new FileOutputStream(configFile)) {
            properties.store(fos, "JMeter Auto-Correlation Plugin Configuration");
        } catch (IOException e) {
            System.err.println("Failed to save configuration: " + e.getMessage());
        }
    }
    
    private File getConfigFile() {
        String userHome = System.getProperty("user.home");
        File jmeterDir = new File(userHome, ".jmeter");
        if (!jmeterDir.exists()) {
            jmeterDir.mkdirs();
        }
        return new File(jmeterDir, CONFIG_FILE);
    }
    
    // Getters
    public String getDetectionSensitivity() {
        return properties.getProperty(DETECTION_SENSITIVITY, DEFAULT_SENSITIVITY);
    }
    
    public double getMinConfidenceThreshold() {
        String value = properties.getProperty(MIN_CONFIDENCE_THRESHOLD, String.valueOf(DEFAULT_MIN_CONFIDENCE));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return DEFAULT_MIN_CONFIDENCE;
        }
    }
    
    public boolean isAutoSelectHighConfidence() {
        return Boolean.parseBoolean(properties.getProperty(AUTO_SELECT_HIGH_CONFIDENCE, String.valueOf(DEFAULT_AUTO_SELECT)));
    }
    
    public String getVariablePrefix() {
        return properties.getProperty(VARIABLE_PREFIX, DEFAULT_VARIABLE_PREFIX);
    }
    
    public boolean isBackupBeforeApply() {
        return Boolean.parseBoolean(properties.getProperty(BACKUP_BEFORE_APPLY, String.valueOf(DEFAULT_BACKUP)));
    }
    
    public boolean isDetectionEnabled(String detectionType) {
        return Boolean.parseBoolean(properties.getProperty(detectionType, "true"));
    }
    
    // Setters
    public void setDetectionSensitivity(String sensitivity) {
        properties.setProperty(DETECTION_SENSITIVITY, sensitivity);
    }
    
    public void setMinConfidenceThreshold(double threshold) {
        properties.setProperty(MIN_CONFIDENCE_THRESHOLD, String.valueOf(threshold));
    }
    
    public void setAutoSelectHighConfidence(boolean autoSelect) {
        properties.setProperty(AUTO_SELECT_HIGH_CONFIDENCE, String.valueOf(autoSelect));
    }
    
    public void setVariablePrefix(String prefix) {
        properties.setProperty(VARIABLE_PREFIX, prefix);
    }
    
    public void setBackupBeforeApply(boolean backup) {
        properties.setProperty(BACKUP_BEFORE_APPLY, String.valueOf(backup));
    }
    
    public void setDetectionEnabled(String detectionType, boolean enabled) {
        properties.setProperty(detectionType, String.valueOf(enabled));
    }
    
    /**
     * Reset all settings to defaults
     */
    public void resetToDefaults() {
        properties.clear();
        loadDefaults();
        saveToFile();
    }
}
