package com.deepai.mcpclient.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Input validation and prompt injection prevention service
 * Protects against malicious user inputs that could compromise AI responses
 */
@Service
public class InputValidationService {

    private static final Logger logger = LoggerFactory.getLogger(InputValidationService.class);

    // Maximum allowed input length (prevent DoS)
    private static final int MAX_INPUT_LENGTH = 10000;
    private static final int MAX_CONTEXT_ID_LENGTH = 100;

    // Prompt injection detection patterns
    private static final List<Pattern> PROMPT_INJECTION_PATTERNS = List.of(
        // System role manipulation
        Pattern.compile("(?i)(system|assistant|user)\\s*:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)ignore\\s+previous\\s+instructions", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)forget\\s+everything", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)new\\s+instructions", Pattern.CASE_INSENSITIVE),

        // Role confusion attempts
        Pattern.compile("(?i)you\\s+are\\s+now", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)act\\s+as", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)pretend\\s+to\\s+be", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)role\\s*:\\s*", Pattern.CASE_INSENSITIVE),

        // Instruction override attempts
        Pattern.compile("(?i)override\\s+(system|instructions|rules)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)disregard\\s+(previous|above|system)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\[\\s*SYSTEM\\s*\\]", Pattern.CASE_INSENSITIVE),

        // Code injection attempts
        Pattern.compile("(?i)<\\s*script", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)javascript:", Pattern.CASE_INSENSITIVE),

        // Markdown injection
        Pattern.compile("```\\s*(system|bash|python|javascript)", Pattern.CASE_INSENSITIVE),

        // Direct prompt manipulation
        Pattern.compile("(?i)END\\s+OF\\s+PROMPT", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)\\{\\{.*system.*\\}\\}", Pattern.CASE_INSENSITIVE)
    );

    // Dangerous character patterns
    private static final Pattern SUSPICIOUS_UNICODE = Pattern.compile("[\\u202E\\u200B-\\u200F\\u2066-\\u2069]");
    private static final Pattern EXCESSIVE_WHITESPACE = Pattern.compile("\\s{50,}");

    /**
     * Validate and sanitize user input
     */
    public ValidationResult validateUserInput(String input) {
        if (input == null) {
            return ValidationResult.invalid("Input cannot be null");
        }

        // Check length limits
        if (input.length() > MAX_INPUT_LENGTH) {
            logger.warn("🚨 Input rejected: exceeds maximum length ({} > {})", input.length(), MAX_INPUT_LENGTH);
            return ValidationResult.invalid("Input too long. Maximum length is " + MAX_INPUT_LENGTH + " characters");
        }

        // Check for empty/whitespace only
        if (input.trim().isEmpty()) {
            return ValidationResult.invalid("Input cannot be empty");
        }

        // Detect prompt injection attempts
        for (Pattern pattern : PROMPT_INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                logger.warn("🚨 Potential prompt injection detected: pattern matched");
                return ValidationResult.invalid("Input contains potentially harmful content");
            }
        }

        // Check for suspicious Unicode characters
        if (SUSPICIOUS_UNICODE.matcher(input).find()) {
            logger.warn("🚨 Suspicious Unicode characters detected");
            return ValidationResult.invalid("Input contains invalid characters");
        }

        // Check for excessive whitespace (potential DoS)
        if (EXCESSIVE_WHITESPACE.matcher(input).find()) {
            logger.warn("🚨 Excessive whitespace detected");
            return ValidationResult.invalid("Input contains excessive whitespace");
        }

        // Sanitize the input
        String sanitizedInput = sanitizeInput(input);

        return ValidationResult.valid(sanitizedInput);
    }

    /**
     * Validate context ID
     */
    public ValidationResult validateContextId(String contextId) {
        if (contextId == null || contextId.trim().isEmpty()) {
            return ValidationResult.valid(null); // Context ID is optional
        }

        if (contextId.length() > MAX_CONTEXT_ID_LENGTH) {
            return ValidationResult.invalid("Context ID too long");
        }

        // Only allow alphanumeric, hyphens, and underscores
        if (!contextId.matches("^[a-zA-Z0-9_-]+$")) {
            return ValidationResult.invalid("Context ID contains invalid characters");
        }

        return ValidationResult.valid(contextId);
    }

    /**
     * Sanitize input by removing/encoding dangerous content
     */
    private String sanitizeInput(String input) {
        // Remove excessive whitespace
        String sanitized = input.replaceAll("\\s+", " ");

        // Remove suspicious Unicode characters
        sanitized = SUSPICIOUS_UNICODE.matcher(sanitized).replaceAll("");

        // Normalize line breaks
        sanitized = sanitized.replaceAll("\\r\\n", "\n").replaceAll("\\r", "\n");

        // Limit consecutive newlines
        sanitized = sanitized.replaceAll("\n{4,}", "\n\n\n");

        return sanitized.trim();
    }

    /**
     * Additional security check for critical operations
     */
    public boolean isHighRiskInput(String input) {
        if (input == null) return false;

        // Check for multiple injection patterns (higher risk)
        long matchCount = PROMPT_INJECTION_PATTERNS.stream()
            .mapToLong(pattern -> pattern.matcher(input).find() ? 1 : 0)
            .sum();

        return matchCount >= 2;
    }

    /**
     * Result of input validation
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String sanitizedInput;
        private final String errorMessage;

        private ValidationResult(boolean valid, String sanitizedInput, String errorMessage) {
            this.valid = valid;
            this.sanitizedInput = sanitizedInput;
            this.errorMessage = errorMessage;
        }

        public static ValidationResult valid(String sanitizedInput) {
            return new ValidationResult(true, sanitizedInput, null);
        }

        public static ValidationResult invalid(String errorMessage) {
            return new ValidationResult(false, null, errorMessage);
        }

        public boolean isValid() {
            return valid;
        }

        public String getSanitizedInput() {
            return sanitizedInput;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }
}