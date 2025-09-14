package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.McpContent;
import com.deepai.mcpclient.model.McpToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * AI-Powered Response Formatter for Global MCP Client
 * 
 * Converts technical MCP tool results into beautiful human-readable format using AI
 * Works with ANY MCP server type and ANY tool response:
 * - Database query results → Beautiful tables and summaries
 * - File system operations → Clear status reports
 * - API service responses → Formatted data displays
 * - Weather data → Natural language descriptions
 * - Git operations → User-friendly status updates
 * - Email services → Readable message summaries
 * - Any other MCP server response → AI-enhanced formatting
 * 
 * Uses Gemini AI for intelligent formatting and natural language descriptions
 */
@Component
public class ResponseFormatter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseFormatter.class);
    
    private final ObjectMapper objectMapper;
    private final AiService aiService;

    public ResponseFormatter(ObjectMapper objectMapper, AiService aiService) {
        this.objectMapper = objectMapper;
        this.aiService = aiService;
    }

    /**
     * Format any MCP tool result into human-readable text
     */
    public String formatToolResult(String toolName, McpToolResult result) {
        logger.debug("Formatting result for tool: {}", toolName);
        
        try {
            // Handle error results
            if (result.isError() != null && result.isError()) {
                return formatErrorResult(toolName, result);
            }

            // Handle success results
            if (result.content() == null || result.content().isEmpty()) {
                return String.format("Tool '%s' executed successfully but returned no content.\n\n❌ No results found for %s.", toolName, formatToolNameForDisplay(toolName));
            }

            StringBuilder formatted = new StringBuilder();
            
            // Add tool-specific header
            String header = getToolResultHeader(toolName);
            if (header != null && !header.trim().isEmpty()) {
                formatted.append(header).append("\n");
            }
            
            // Process each content item
            for (McpContent content : result.content()) {
                String contentText = formatContentItem(content, toolName);
                if (!contentText.trim().isEmpty()) {
                    formatted.append(contentText).append("\n");
                }
            }

            String finalResult = formatted.toString().trim();
            return finalResult.isEmpty() ? 
                String.format("Tool '%s' completed successfully.", toolName) : 
                finalResult;
                
        } catch (Exception e) {
            logger.error("Error formatting tool result for {}: {}", toolName, e.getMessage(), e);
            return String.format("Tool '%s' executed, but there was an issue formatting the response.", toolName);
        }
    }

    /**
     * Format error results from any tool
     */
    private String formatErrorResult(String toolName, McpToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return String.format("❌ Error in %s", toolName);
        }

        StringBuilder errorMsg = new StringBuilder();
        errorMsg.append("❌ Tool '").append(toolName).append("' failed:\n");
        
        for (McpContent content : result.content()) {
            if (content.text() != null) {
                errorMsg.append("Error: ").append(content.text()).append("\n");
            }
        }

        return errorMsg.toString().trim();
    }

    /**
     * Format individual content items - works with any content type
     */
    private String formatContentItem(McpContent content, String toolName) {
        if (content == null) return "";

        try {
            String text = content.text();
            Object data = content.data();
            String mimeType = content.mimeType();

            // Handle JSON data content
            if (data != null && "application/json".equals(mimeType)) {
                try {
                    String jsonText = objectMapper.writeValueAsString(data);
                    return formatByMimeType(jsonText, mimeType, toolName);
                } catch (Exception e) {
                    logger.warn("Failed to serialize data to JSON: {}", e.getMessage());
                    return formatGenericText(data.toString(), toolName);
                }
            }

            // Handle text content
            if (text != null) {
                if (mimeType != null) {
                    return formatByMimeType(text, mimeType, toolName);
                } else {
                    return formatGenericText(text, toolName);
                }
            }

            // Fallback for content with only data field
            if (data != null) {
                return formatGenericText(data.toString(), toolName);
            }

            return "";
            
        } catch (Exception e) {
            logger.warn("Error formatting content item: {}", e.getMessage());
            return content.text() != null ? content.text() : 
                   (content.data() != null ? content.data().toString() : "");
        }
    }

    /**
     * Format content based on MIME type
     */
    private String formatByMimeType(String text, String mimeType, String toolName) {
        if (text == null || text.trim().isEmpty()) return "";

        switch (mimeType.toLowerCase()) {
            case "application/json":
                return formatJsonContent(text, toolName);
            
            case "text/csv":
                return formatCsvContent(text, toolName);
            
            case "application/xml":
            case "text/xml":
                return formatXmlContent(text, toolName);
            
            case "text/html":
                return formatHtmlContent(text, toolName);
            
            case "text/plain":
            default:
                return formatPlainText(text, toolName);
        }
    }

    /**
     * Format JSON content - works with any JSON structure
     */
    private String formatJsonContent(String jsonText, String toolName) {
        try {
            JsonNode json = objectMapper.readTree(jsonText);
            
            // Try to detect common patterns and format accordingly
            if (json.isArray()) {
                return formatJsonArray(json, toolName);
            } else if (json.isObject()) {
                return formatJsonObject(json, toolName);
            } else {
                return "Result: " + json.asText();
            }
            
        } catch (Exception e) {
            logger.debug("Could not parse as JSON, treating as plain text: {}", e.getMessage());
            return formatPlainText(jsonText, toolName);
        }
    }

    /**
     * Format JSON arrays using AI for beautiful human-readable output
     */
    private String formatJsonArray(JsonNode arrayNode, String toolName) {
        int size = arrayNode.size();
        if (size == 0) {
            return "🔍 No results found.";
        }

        try {
            // Convert to JSON string for AI processing
            String jsonData = objectMapper.writeValueAsString(arrayNode);
            
            // Create AI prompt for formatting
            String aiPrompt = String.format(
                "Format this JSON data from a '%s' operation into a beautiful, human-readable format. " +
                "Use appropriate emojis, clear headers, and make it conversational and easy to understand. " +
                "If it's database data, format it like a nice table or list. " +
                "If it's file data, show it clearly. " +
                "Make it friendly and professional:\n\n%s", 
                toolName, jsonData
            );
            
            logger.info("🤖 Using AI to format {} results with {} items", toolName, size);
            
            // Get AI-formatted response
            String aiFormatted = aiService.ask(aiPrompt).block();
            
            if (aiFormatted != null && !aiFormatted.trim().isEmpty()) {
                return "🤖 " + aiFormatted;
            } else {
                logger.warn("AI formatting returned empty result, falling back to standard formatting");
                return formatJsonArrayFallback(arrayNode, toolName);
            }
            
        } catch (Exception e) {
            logger.warn("AI formatting failed for {}: {}, falling back to standard formatting", toolName, e.getMessage());
            return formatJsonArrayFallback(arrayNode, toolName);
        }
    }

    /**
     * Fallback formatting when AI is not available
     */
    private String formatJsonArrayFallback(JsonNode arrayNode, String toolName) {
        StringBuilder formatted = new StringBuilder();
        
        int size = arrayNode.size();
        if (size == 0) {
            return "🔍 No results found.";
        }

        // Detect if it looks like a list of records/objects
        if (size > 0 && arrayNode.get(0).isObject()) {
            formatted.append(String.format("📊 Found %d result%s:\n\n", size, size == 1 ? "" : "s"));
            
            int displayCount = Math.min(size, 10); // Limit display to first 10 items
            for (int i = 0; i < displayCount; i++) {
                JsonNode item = arrayNode.get(i);
                formatted.append(String.format("  %d. %s\n", i + 1, formatJsonObjectInline(item)));
            }
            
            if (size > displayCount) {
                formatted.append(String.format("  ... and %d more result%s\n", 
                    size - displayCount, (size - displayCount) == 1 ? "" : "s"));
            }
        } else {
            // Simple array of values
            formatted.append(String.format("📋 Results (%d items):\n", size));
            int displayCount = Math.min(size, 20);
            for (int i = 0; i < displayCount; i++) {
                formatted.append("  • ").append(arrayNode.get(i).asText()).append("\n");
            }
            
            if (size > displayCount) {
                formatted.append(String.format("  ... and %d more items\n", size - displayCount));
            }
        }

        return formatted.toString();
    }

    /**
     * Format JSON objects using AI for beautiful human-readable output
     */
    private String formatJsonObject(JsonNode objectNode, String toolName) {
        try {
            // Convert to JSON string for AI processing
            String jsonData = objectMapper.writeValueAsString(objectNode);
            
            // Create AI prompt for formatting
            String aiPrompt = String.format(
                "Format this JSON object from a '%s' operation into a beautiful, human-readable format. " +
                "Use appropriate emojis, clear labels, and make it conversational and easy to understand. " +
                "If it's status information, show it clearly. " +
                "If it's configuration data, format it nicely. " +
                "Make it friendly and professional:\n\n%s", 
                toolName, jsonData
            );
            
            logger.info("🤖 Using AI to format {} object result", toolName);
            
            // Get AI-formatted response
            String aiFormatted = aiService.ask(aiPrompt).block();
            
            if (aiFormatted != null && !aiFormatted.trim().isEmpty()) {
                return "🤖 " + aiFormatted;
            } else {
                logger.warn("AI formatting returned empty result, falling back to standard formatting");
                return formatJsonObjectFallback(objectNode, toolName);
            }
            
        } catch (Exception e) {
            logger.warn("AI formatting failed for {}: {}, falling back to standard formatting", toolName, e.getMessage());
            return formatJsonObjectFallback(objectNode, toolName);
        }
    }

    /**
     * Fallback JSON object formatting when AI is not available
     */
    private String formatJsonObjectFallback(JsonNode objectNode, String toolName) {
        // Try to detect common object types and format appropriately
        if (hasStatusFields(objectNode)) {
            return formatStatusObject(objectNode);
        } else if (hasCountField(objectNode)) {
            return formatCountObject(objectNode);
        } else {
            return formatGenericObject(objectNode);
        }
    }

    /**
     * Format objects that look like status responses
     */
    private String formatStatusObject(JsonNode obj) {
        StringBuilder formatted = new StringBuilder();
        formatted.append("Status Information:\n");
        
        obj.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            String value = entry.getValue().asText();
            
            // Format common status fields nicely
            switch (key.toLowerCase()) {
                case "status", "state", "health":
                    formatted.append(String.format("• Status: %s\n", value));
                    break;
                case "message", "description":
                    formatted.append(String.format("• Message: %s\n", value));
                    break;
                case "timestamp", "time", "date":
                    formatted.append(String.format("• Time: %s\n", value));
                    break;
                default:
                    formatted.append(String.format("• %s: %s\n", capitalizeFirst(key), value));
            }
        });
        
        return formatted.toString();
    }

    /**
     * Format objects that contain count information
     */
    private String formatCountObject(JsonNode obj) {
        if (obj.has("count")) {
            int count = obj.get("count").asInt();
            return String.format("Count: %d item%s", count, count == 1 ? "" : "s");
        }
        
        return formatGenericObject(obj);
    }

    /**
     * Format generic JSON objects
     */
    private String formatGenericObject(JsonNode obj) {
        StringBuilder formatted = new StringBuilder();
        
        obj.fields().forEachRemaining(entry -> {
            String key = capitalizeFirst(entry.getKey());
            JsonNode value = entry.getValue();
            
            if (value.isTextual()) {
                formatted.append(String.format("• %s: %s\n", key, value.asText()));
            } else if (value.isNumber()) {
                formatted.append(String.format("• %s: %s\n", key, value.asText()));
            } else if (value.isBoolean()) {
                formatted.append(String.format("• %s: %s\n", key, value.asBoolean() ? "Yes" : "No"));
            } else {
                formatted.append(String.format("• %s: %s\n", key, value.toString()));
            }
        });
        
        return formatted.toString();
    }

    /**
     * Format JSON object in a single line
     */
    private String formatJsonObjectInline(JsonNode obj) {
        StringBuilder inline = new StringBuilder();
        
        // Try to find the most important fields first
        String[] priorityFields = {"name", "title", "id", "_id", "filename", "path", "description"};
        
        for (String field : priorityFields) {
            if (obj.has(field)) {
                inline.append(obj.get(field).asText());
                break;
            }
        }
        
        // Add other key information
        obj.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!isInArray(key, priorityFields)) {
                if (inline.length() > 0) inline.append(" • ");
                inline.append(key).append(": ").append(entry.getValue().asText());
            }
        });
        
        return inline.toString();
    }

    /**
     * Format CSV content
     */
    private String formatCsvContent(String csvText, String toolName) {
        String[] lines = csvText.split("\\n");
        if (lines.length == 0) return "No data found.";
        
        StringBuilder formatted = new StringBuilder();
        formatted.append(String.format("Data table (%d rows):\n\n", lines.length));
        
        // Display first few rows
        int displayRows = Math.min(lines.length, 10);
        for (int i = 0; i < displayRows; i++) {
            formatted.append(lines[i]).append("\n");
        }
        
        if (lines.length > displayRows) {
            formatted.append(String.format("... and %d more rows", lines.length - displayRows));
        }
        
        return formatted.toString();
    }

    /**
     * Format plain text content
     */
    private String formatPlainText(String text, String toolName) {
        if (text == null || text.trim().isEmpty()) {
            return String.format("Tool '%s' completed successfully.", toolName);
        }
        
        // Clean up the text
        String cleaned = text.trim();
        
        // If it's very short, return as-is
        if (cleaned.length() < 200) {
            return cleaned;
        }
        
        // For longer text, add some formatting
        return "Result:\n" + cleaned;
    }

    /**
     * Format generic text when MIME type is unknown
     */
    private String formatGenericText(String text, String toolName) {
        if (text == null || text.trim().isEmpty()) {
            return String.format("Tool '%s' completed successfully.", toolName);
        }

        // Try to detect if it's JSON
        if (text.trim().startsWith("{") || text.trim().startsWith("[")) {
            return formatJsonContent(text, toolName);
        }
        
        return formatPlainText(text, toolName);
    }

    /**
     * Format XML content (basic)
     */
    private String formatXmlContent(String xmlText, String toolName) {
        // Basic XML formatting - could be enhanced
        return "XML Response:\n" + xmlText;
    }

    /**
     * Format HTML content (strip tags for plain text)
     */
    private String formatHtmlContent(String htmlText, String toolName) {
        // Basic HTML stripping - could be enhanced
        String cleaned = htmlText.replaceAll("<[^>]*>", "");
        return formatPlainText(cleaned, toolName);
    }

    /**
     * Check if JSON object has status-like fields
     */
    private boolean hasStatusFields(JsonNode obj) {
        String[] statusFields = {"status", "state", "health", "ok", "success", "error"};
        for (String field : statusFields) {
            if (obj.has(field)) return true;
        }
        return false;
    }

    /**
     * Check if JSON object has count field
     */
    private boolean hasCountField(JsonNode obj) {
        return obj.has("count") || obj.has("total") || obj.has("size");
    }

    /**
     * Capitalize first letter of string
     */
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Check if string is in array
     */
    private boolean isInArray(String str, String[] array) {
        for (String item : array) {
            if (item.equals(str)) return true;
        }
        return false;
    }

    /**
     * Get tool-specific result header based on tool name
     */
    private String getToolResultHeader(String toolName) {
        if (toolName == null) return null;
        
        String displayName = formatToolNameForDisplay(toolName);
        
        // Check for specific tool patterns and provide appropriate headers
        String lowerTool = toolName.toLowerCase();
        
        if (lowerTool.contains("database") || lowerTool.contains("db")) {
            return "📊 " + displayName + " Found";
        } else if (lowerTool.contains("server") || lowerTool.contains("health") || lowerTool.contains("ping")) {
            return "🏥 " + displayName + " Results";
        } else if (lowerTool.contains("file") || lowerTool.contains("list")) {
            return "📁 " + displayName + " Results"; 
        } else if (lowerTool.contains("export") || lowerTool.contains("data")) {
            return "📤 " + displayName + " Results";
        } else if (lowerTool.contains("parse") || lowerTool.contains("json")) {
            return "🔍 " + displayName + " Results";
        } else if (lowerTool.contains("batch") || lowerTool.contains("process")) {
            return "⚡ " + displayName + " Results";
        } else if (lowerTool.contains("unknown") || lowerTool.contains("tool")) {
            return "❓ " + displayName + " Results";
        } else if (lowerTool.contains("long") || lowerTool.contains("text")) {
            return "📝 " + displayName + " Results";
        } else if (lowerTool.contains("delete") || lowerTool.contains("success")) {
            return "✅ " + displayName + " Results";
        } else if (lowerTool.contains("get") || lowerTool.contains("user") || lowerTool.contains("info")) {
            return "👤 " + displayName + " Results";
        } else {
            return "🔧 " + displayName + " Results";
        }
    }
    
    /**
     * Format tool name for display (convert camelCase, snake_case, kebab-case to readable format)
     */
    private String formatToolNameForDisplay(String toolName) {
        if (toolName == null || toolName.isEmpty()) return "Tool";
        
        // Handle camelCase
        String readable = toolName.replaceAll("([a-z])([A-Z])", "$1 $2");
        
        // Handle snake_case and kebab-case
        readable = readable.replace("_", " ").replace("-", " ");
        
        // Capitalize each word
        String[] words = readable.split(" ");
        StringBuilder formatted = new StringBuilder();
        
        for (String word : words) {
            if (word.length() > 0) {
                if (formatted.length() > 0) formatted.append(" ");
                formatted.append(word.substring(0, 1).toUpperCase())
                         .append(word.substring(1).toLowerCase());
            }
        }
        
        return formatted.toString();
    }

    /**
     * Public API: Format any tool result
     */
    public String formatAnyResult(Object result, String toolName) {
        try {
            if (result instanceof McpToolResult) {
                return formatToolResult(toolName, (McpToolResult) result);
            } else if (result instanceof String) {
                return formatPlainText((String) result, toolName);
            } else {
                String jsonString = objectMapper.writeValueAsString(result);
                return formatJsonContent(jsonString, toolName);
            }
        } catch (Exception e) {
            logger.error("Error formatting result: {}", e.getMessage(), e);
            return String.format("Tool '%s' completed, but result formatting failed.", toolName);
        }
    }
}
