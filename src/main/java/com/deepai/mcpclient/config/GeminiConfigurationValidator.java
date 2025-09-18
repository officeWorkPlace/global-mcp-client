package com.deepai.mcpclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component("geminiConfigValidator")
public class GeminiConfigurationValidator {

    private static final Logger logger = LoggerFactory.getLogger(GeminiConfigurationValidator.class);

    @Value("${GEMINI_API_KEY:${ai.api-key:not-set}}")
    private String geminiApiKey;

    @Value("${ai.provider:gemini}")
    private String aiProvider;

    @Value("${ai.model:gemini-1.5-flash}")
    private String aiModel;

    public boolean isConfigurationValid() {
        try {
            // Validate API key
            if (geminiApiKey == null || "not-set".equals(geminiApiKey) || "your-gemini-api-key-here".equals(geminiApiKey)) {
                logger.error("Gemini API key not configured. Set GEMINI_API_KEY environment variable");
                return false;
            }

            if (!geminiApiKey.startsWith("AIza")) {
                logger.error("Invalid Gemini API key format. API key should start with 'AIza'");
                return false;
            }

            // Log configuration details
            logger.info("Gemini configuration valid - Provider: {}, Model: {}, API key length: {}",
                       aiProvider, aiModel, geminiApiKey.length());

            return true;

        } catch (Exception e) {
            logger.error("Gemini configuration validation failed", e);
            return false;
        }
    }
}