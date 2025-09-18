package com.deepai.mcpclient.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class GeminiModelSelector {

    @Value("${ai.models.default:gemini-1.5-pro}")
    private String defaultModel;

    @Value("${ai.models.fast:gemini-1.5-flash}")
    private String fastModel;

    @Value("${ai.models.reasoning:gemini-1.5-pro}")
    private String reasoningModel;

    public String selectOptimalModel(String promptText, boolean requiresFastResponse) {

        if (requiresFastResponse) {
            return fastModel;
        }

        // Use reasoning model for complex queries
        if (isComplexQuery(promptText)) {
            return reasoningModel;
        }

        // Default model for standard queries
        return defaultModel;
    }

    private boolean isComplexQuery(String promptText) {
        String lower = promptText.toLowerCase();

        // Complex query indicators
        return lower.contains("analyze") ||
               lower.contains("compare") ||
               lower.contains("explain why") ||
               lower.contains("reasoning") ||
               lower.contains("complex") ||
               lower.length() > 500 ||
               lower.split("\\s+").length > 100;
    }

    public int getContextWindowForModel(String model) {
        switch (model) {
            case "gemini-1.5-flash":
                return 1_048_576; // 1M tokens
            case "gemini-1.5-pro":
                return 2_097_152; // 2M tokens
            default:
                return 32_000;    // Conservative default
        }
    }
}