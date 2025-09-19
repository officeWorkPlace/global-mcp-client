package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.McpTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Intent Processor for Global MCP Client
 * 
 * Completely generic - works with ANY MCP server:
 * - MongoDB, PostgreSQL, MySQL (database servers)
 * - Filesystem, Git, SVN (file/version control servers) 
 * - Weather, News, API services (web service servers)
 * - Docker, Kubernetes (container orchestration servers)
 * - Email, Calendar, CRM (business application servers)
 * 
 * No assumptions about server type or available tools.
 * Everything is discovered dynamically at runtime.
 */
@Component
public class IntentProcessor {

    private static final Logger logger = LoggerFactory.getLogger(IntentProcessor.class);

    private final McpClientService mcpClientService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;
    
    // Dynamic tool discovery cache
    private final Map<String, List<McpTool>> serverToolsCache = new ConcurrentHashMap<>();
    private volatile long lastToolsCacheUpdate = 0;
    private static final long CACHE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    public IntentProcessor(McpClientService mcpClientService, ChatModel chatModel, ObjectMapper objectMapper) {
        this.mcpClientService = mcpClientService;
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyze user intent - works with ANY MCP server type (Reactive version)
     */
    public Mono<Intent> analyzeIntentAsync(String userInput) {
        logger.debug("Analyzing intent for user input: {}", userInput);

        return discoverAllAvailableTools()
            .flatMap(allServerTools -> {
                if (allServerTools.isEmpty()) {
                    logger.warn("No MCP servers available or no tools discovered");
                    return Mono.just(new Intent(false, null, null, Map.of(), 0.0, "No MCP tools available", null, null));
                }

                // Step 2: Use AI to understand intent and match with any available tool
                return processIntentWithTools(userInput, allServerTools);
            })
            .onErrorResume(error -> {
                logger.error("Intent analysis failed: {}", error.getMessage(), error);
                return Mono.just(new Intent(false, null, null, Map.of(), 0.0,
                    "Intent analysis failed: " + error.getMessage(), null, null));
            });
    }

    /**
     * Analyze user intent - works with ANY MCP server type (Synchronous version for backward compatibility)
     */
    public Intent analyzeIntent(String userInput) {
        logger.debug("Analyzing intent for user input: {}", userInput);

        try {
            // Use reactive version but block for synchronous callers
            return analyzeIntentAsync(userInput).block();
        } catch (Exception e) {
            logger.error("Intent analysis failed: {}", e.getMessage(), e);
            return new Intent(false, null, null, Map.of(), 0.0,
                "Intent analysis failed: " + e.getMessage(), null, null);
        }
    }

    private Mono<Intent> processIntentWithTools(String userInput, Map<String, List<McpTool>> allServerTools) {
        return Mono.fromCallable(() -> {
            try {
                Intent intent = analyzeIntentWithAI(userInput, allServerTools);

                logger.info("Intent analysis: execution={}, tool={}, server={}, confidence={}",
                    intent.requiresToolExecution(), intent.toolName(), intent.serverId(), intent.confidence());

                return intent;

            } catch (Exception e) {
                logger.error("Error in intent analysis: {}", e.getMessage(), e);
                return new Intent(false, null, null, Map.of(), 0.0, "Error analyzing intent: " + e.getMessage(), null, null);
            }
        });
    }

    /**
     * Enhanced intent analysis with tool awareness and multi-step support
     */
    public Intent analyzeIntentWithToolAwareness(String userMessage, Map<String, List<McpTool>> availableTools) {
        logger.info("🧠 Analyzing intent with enhanced tool awareness for: '{}'", userMessage);

        // Build context about available tools
        String toolsContext = buildToolsContextForAI(availableTools);

        String analysisPrompt = String.format("""
            You are an expert AI assistant that analyzes user requests and maps them to available MCP tools.

            USER REQUEST: "%s"

            AVAILABLE TOOLS:
            %s

            Analyze the user's intent and respond with JSON:
            {
              "confidence": 0.95,
              "intent_type": "database_operation|file_operation|analysis|multi_step",
              "primary_action": "tool_name",
              "server_id": "best_server_for_task",
              "parameters": {},
              "reasoning": "why you chose this tool",
              "requires_chain": false,
              "suggested_steps": ["step1", "step2"]
            }

            Choose the BEST tool based on:
            1. Tool description match with user intent
            2. Server capabilities and performance
            3. Parameter requirements vs available info

            If the request suggests multiple operations, set requires_chain to true and suggest steps.
            If no exact match exists, suggest the closest alternative.
            """, userMessage, toolsContext);

        try {
            ChatResponse response = chatModel.call(
                new Prompt(new UserMessage(analysisPrompt))
            );

            String aiAnalysis = response.getResult().getOutput().getText();
            return parseAIIntentAnalysis(aiAnalysis, userMessage);

        } catch (Exception e) {
            logger.warn("⚠️ AI intent analysis failed, falling back to pattern matching: {}", e.getMessage());
            return analyzeIntentFallback(userMessage, availableTools);
        }
    }

    private String buildToolsContextForAI(Map<String, List<McpTool>> availableTools) {
        StringBuilder context = new StringBuilder();

        for (Map.Entry<String, List<McpTool>> entry : availableTools.entrySet()) {
            String serverId = entry.getKey();
            List<McpTool> tools = entry.getValue();

            context.append(String.format("\nSERVER: %s (%d tools)\n", serverId, tools.size()));

            for (McpTool tool : tools) {
                context.append(String.format("- %s: %s\n", tool.name(), tool.description()));

                // Add key parameters for better matching
                if (tool.inputSchema() != null) {
                    Object properties = extractProperties(tool.inputSchema());
                    if (properties != null) {
                        context.append(String.format("  Parameters: %s\n", properties));
                    }
                }
            }
        }

        return context.toString();
    }

    private Object extractProperties(Map<String, Object> inputSchema) {
        if (inputSchema.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) inputSchema.get("properties");
            return properties.keySet();
        }
        return null;
    }

    private Intent parseAIIntentAnalysis(String aiAnalysis, String originalMessage) {
        try {
            // Clean up AI response (remove markdown formatting if present)
            String cleanJson = aiAnalysis.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode analysis = objectMapper.readTree(cleanJson);

            boolean requiresExecution = analysis.path("primary_action").asText() != null &&
                                       !analysis.path("primary_action").asText().isEmpty();

            List<String> suggestedSteps = null;
            boolean requiresChain = analysis.path("requires_chain").asBoolean(false);
            if (requiresChain) {
                JsonNode stepsNode = analysis.path("suggested_steps");
                if (stepsNode.isArray()) {
                    suggestedSteps = new java.util.ArrayList<>();
                    for (JsonNode stepNode : stepsNode) {
                        suggestedSteps.add(stepNode.asText());
                    }
                }
            }

            Map<String, Object> parameters = new HashMap<>();
            JsonNode paramsNode = analysis.path("parameters");
            if (paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(entry -> {
                    parameters.put(entry.getKey(), parseJsonValue(entry.getValue()));
                });
            }

            Intent intent = new Intent(
                requiresExecution,
                analysis.path("primary_action").asText(null),
                analysis.path("server_id").asText(null),
                parameters,
                analysis.path("confidence").asDouble(0.7),
                analysis.path("reasoning").asText("AI analysis"),
                analysis.path("intent_type").asText("general"),
                suggestedSteps
            );

            logger.info("✅ AI intent analysis successful: {} (confidence: {})",
                intent.toolName(), intent.confidence());

            return intent;

        } catch (Exception e) {
            logger.error("❌ Failed to parse AI intent analysis: {}", e.getMessage());
            logger.debug("Raw AI response: {}", aiAnalysis);
            return analyzeIntentFallback(originalMessage, null);
        }
    }

    private Intent analyzeIntentFallback(String userMessage, Map<String, List<McpTool>> availableTools) {
        logger.info("🔄 Using fallback pattern matching for: '{}'", userMessage);

        String lower = userMessage.toLowerCase();

        // Enhanced pattern matching with better tool selection
        if (lower.contains("list") && (lower.contains("database") || lower.contains("db"))) {
            return new Intent(
                true,
                "listDatabases",
                selectBestDatabaseServer(availableTools),
                Map.of(),
                0.8,
                "Pattern match: list databases",
                "database_operation",
                null
            );
        } else if (lower.contains("find") || lower.contains("search")) {
            return new Intent(
                true,
                "findDocuments",
                selectBestDatabaseServer(availableTools),
                extractSearchParameters(userMessage),
                0.7,
                "Pattern match: search operation",
                "database_operation",
                null
            );
        }

        // Add more intelligent patterns...

        return new Intent(
            false,
            "help",
            null,
            Map.of(),
            0.3,
            "No clear pattern match found",
            "conversational",
            null
        );
    }

    private String selectBestDatabaseServer(Map<String, List<McpTool>> availableTools) {
        if (availableTools == null) return null;

        // Intelligent server selection logic
        for (String serverId : availableTools.keySet()) {
            if (serverId.contains("oracle") || serverId.contains("mongo")) {
                return serverId; // Prefer database servers
            }
        }
        return availableTools.keySet().iterator().hasNext() ?
               availableTools.keySet().iterator().next() : null; // Fallback
    }

    private Map<String, Object> extractSearchParameters(String userMessage) {
        Map<String, Object> params = new HashMap<>();

        // Basic parameter extraction - can be enhanced with NLP
        if (userMessage.contains("email")) {
            params.put("field", "email");
        }
        if (userMessage.contains("gmail")) {
            params.put("pattern", ".*gmail.*");
        }

        return params;
    }

    /**
     * Dynamically discover all tools from all connected MCP servers
     * Works with any server type - no assumptions
     */
    private Mono<Map<String, List<McpTool>>> discoverAllAvailableTools() {
        long currentTime = System.currentTimeMillis();

        // Use cache if fresh
        if (currentTime - lastToolsCacheUpdate < CACHE_EXPIRY_MS && !serverToolsCache.isEmpty()) {
            logger.debug("Using cached tool information");
            return Mono.just(new HashMap<>(serverToolsCache));
        }

        logger.info("Discovering tools from all MCP servers...");
        serverToolsCache.clear();

        // Get all configured servers (could be any type)
        List<String> serverIds = mcpClientService.getServerIds();
        logger.info("Found {} configured MCP servers: {}", serverIds.size(), serverIds);

        return Flux.fromIterable(serverIds)
            .flatMap(serverId ->
                mcpClientService.isServerHealthy(serverId)
                    .filter(Boolean.TRUE::equals)
                    .flatMap(healthy -> mcpClientService.listTools(serverId)
                        .doOnNext(tools -> {
                            if (tools != null && !tools.isEmpty()) {
                                serverToolsCache.put(serverId, tools);
                                logger.info("Server '{}': Discovered {} tools", serverId, tools.size());
                                tools.forEach(tool -> logger.debug("  - {}: {}", tool.name(), tool.description()));
                            } else {
                                logger.warn("Server '{}': No tools available", serverId);
                            }
                        })
                        .map(tools -> Map.entry(serverId, tools))
                    )
                    .onErrorResume(error -> {
                        logger.error("Failed to discover tools from server '{}': {}", serverId, error.getMessage());
                        return Mono.empty();
                    })
                    .switchIfEmpty(Mono.fromRunnable(() ->
                        logger.warn("Server '{}': Not healthy, skipping tool discovery", serverId)))
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .doOnNext(discoveredTools -> {
                lastToolsCacheUpdate = currentTime;
                int totalTools = discoveredTools.values().stream().mapToInt(List::size).sum();
                logger.info("Tool discovery complete: {} servers, {} total tools", discoveredTools.size(), totalTools);
            })
            .map(HashMap::new);
    }

    /**
     * Use AI to analyze user intent against ANY available tools
     */
    private Intent analyzeIntentWithAI(String userInput, Map<String, List<McpTool>> allServerTools) {
        try {
            // Build comprehensive context with ALL available tools
            StringBuilder toolsContext = new StringBuilder();
            toolsContext.append("Available MCP Tools Across All Servers:\n\n");
            
            for (Map.Entry<String, List<McpTool>> entry : allServerTools.entrySet()) {
                String serverId = entry.getKey();
                List<McpTool> tools = entry.getValue();
                
                toolsContext.append("=== SERVER: ").append(serverId).append(" ===\n");
                for (McpTool tool : tools) {
                    toolsContext.append("Tool: ").append(tool.name()).append("\n");
                    toolsContext.append("Description: ").append(tool.description()).append("\n");
                    
                    // Include input schema if available
                    if (tool.inputSchema() != null) {
                        toolsContext.append("Parameters: ").append(summarizeSchema(tool.inputSchema())).append("\n");
                    }
                    toolsContext.append("\n");
                }
                toolsContext.append("\n");
            }

            String analysisPrompt = String.format("""
                You are an intelligent assistant for a Universal MCP (Model Context Protocol) Client.
                Your job is to analyze user requests and determine if they can be fulfilled using available MCP tools.
                
                USER REQUEST: "%s"
                
                %s
                
                TASK:
                1. Analyze if the user wants to perform an action that matches any available MCP tool
                2. If yes, identify the best matching tool and server
                3. Extract parameters from the user request based on the tool's schema
                4. Provide reasoning for your choice
                
                RESPOND WITH EXACT JSON FORMAT:
                {
                  "requiresToolExecution": true/false,
                  "toolName": "exact_tool_name_from_list" or null,
                  "serverId": "exact_server_id_from_list" or null,
                  "parameters": {...extracted parameters based on tool schema...},
                  "confidence": 0.0-1.0,
                  "reasoning": "explain your analysis and tool selection"
                }
                
                RULES:
                - Only use exact tool names and server IDs from the available list
                - If no tool matches, set requiresToolExecution to false
                - For parameters, follow the tool's input schema requirements
                - Be specific about why you chose a particular tool
                - Consider user intent, not just keywords
                
                EXAMPLES:
                - "Show me files" → look for filesystem/directory tools
                - "Check weather" → look for weather service tools  
                - "List databases" → look for database tools
                - "What's the latest commit?" → look for git/version control tools
                - "Send email" → look for email service tools
                - "Hello" → no tool execution needed
                """, 
                userInput, 
                toolsContext.toString()
            );

            ChatResponse aiResponse = chatModel.call(new Prompt(new UserMessage(analysisPrompt)));
            String responseContent = aiResponse.getResult().getOutput().getText();

            logger.debug("AI analysis response: {}", responseContent);

            // Parse the AI response
            return parseAIIntentResponse(responseContent, userInput);
            
        } catch (Exception e) {
            logger.error("Error in AI intent analysis: {}", e.getMessage(), e);
            return createFallbackIntent(userInput, allServerTools);
        }
    }

    /**
     * Parse AI response to extract structured intent
     */
    private Intent parseAIIntentResponse(String response, String originalInput) {
        try {
            // Extract JSON from AI response (handle cases where AI adds extra text)
            String jsonPart = extractJsonFromResponse(response);
            JsonNode json = objectMapper.readTree(jsonPart);

            boolean requiresExecution = json.path("requiresToolExecution").asBoolean(false);

            if (!requiresExecution) {
                String reasoning = json.path("reasoning").asText("No tool execution required");
                return new Intent(false, null, null, Map.of(), 0.0, reasoning, "conversational", null);
            }

            String toolName = json.path("toolName").asText(null);
            String serverId = json.path("serverId").asText(null);
            double confidence = json.path("confidence").asDouble(0.7);
            String reasoning = json.path("reasoning").asText("AI analysis");

            // Parse parameters
            Map<String, Object> parameters = new HashMap<>();
            JsonNode paramsNode = json.path("parameters");
            if (paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(entry -> {
                    parameters.put(entry.getKey(), parseJsonValue(entry.getValue()));
                });
            }

            return new Intent(requiresExecution, toolName, serverId, parameters, confidence, reasoning, "general", null);

        } catch (Exception e) {
            logger.error("Error parsing AI response: {}", e.getMessage(), e);
            return new Intent(false, null, null, Map.of(), 0.0, "Failed to parse AI response", "error", null);
        }
    }

    /**
     * Extract JSON portion from AI response
     */
    private String extractJsonFromResponse(String response) {
        // Find JSON block in the response
        int jsonStart = response.indexOf('{');
        int jsonEnd = response.lastIndexOf('}');
        
        if (jsonStart != -1 && jsonEnd != -1 && jsonEnd > jsonStart) {
            return response.substring(jsonStart, jsonEnd + 1);
        }
        
        return "{}"; // Empty JSON if not found
    }

    /**
     * Convert JsonNode to appropriate Java object
     */
    private Object parseJsonValue(JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isNumber()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isArray()) {
            List<Object> list = new java.util.ArrayList<>();
            node.forEach(item -> list.add(parseJsonValue(item)));
            return list;
        }
        if (node.isObject()) {
            Map<String, Object> map = new HashMap<>();
            node.fields().forEachRemaining(entry -> 
                map.put(entry.getKey(), parseJsonValue(entry.getValue())));
            return map;
        }
        return node.asText();
    }

    /**
     * Create fallback intent when AI analysis fails
     */
    private Intent createFallbackIntent(String userInput, Map<String, List<McpTool>> allServerTools) {
        logger.info("Creating fallback intent analysis for: {}", userInput);

        String normalizedInput = userInput.toLowerCase().trim();

        // Simple keyword matching as fallback
        for (Map.Entry<String, List<McpTool>> entry : allServerTools.entrySet()) {
            String serverId = entry.getKey();
            List<McpTool> tools = entry.getValue();

            for (McpTool tool : tools) {
                // Check if input contains tool name or description keywords
                if (normalizedInput.contains(tool.name().toLowerCase()) ||
                    hasKeywordMatch(normalizedInput, tool.description().toLowerCase())) {

                    logger.info("Fallback match: tool='{}', server='{}'", tool.name(), serverId);
                    return new Intent(
                        true,
                        tool.name(),
                        serverId,
                        Map.of(), // Empty parameters for fallback
                        0.4, // Low confidence for fallback
                        "Fallback keyword matching",
                        "general",
                        null
                    );
                }
            }
        }

        return new Intent(false, null, null, Map.of(), 0.0, "No matching tools found", "conversational", null);
    }

    /**
     * Check for keyword matches
     */
    private boolean hasKeywordMatch(String input, String description) {
        String[] commonWords = {"list", "show", "get", "find", "create", "delete", "update", 
                               "check", "ping", "health", "status", "file", "directory"};
        
        for (String word : commonWords) {
            if (input.contains(word) && description.contains(word)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Summarize tool input schema for AI context
     */
    private String summarizeSchema(Object schema) {
        try {
            if (schema instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> schemaMap = (Map<String, Object>) schema;
                return schemaMap.toString();
            }
            return schema.toString();
        } catch (Exception e) {
            return "complex schema";
        }
    }

    /**
     * Public API: Get tools for specific server (Reactive version)
     */
    public Mono<List<McpTool>> getServerToolsAsync(String serverId) {
        return discoverAllAvailableTools()
            .map(allTools -> allTools.getOrDefault(serverId, List.of()));
    }

    /**
     * Public API: Get tools for specific server (Synchronous version for backward compatibility)
     */
    public List<McpTool> getServerTools(String serverId) {
        return getServerToolsAsync(serverId).block();
    }

    /**
     * Public API: Get summary of all servers and their capabilities (Reactive version)
     */
    public Mono<Map<String, Integer>> getServerToolCountsAsync() {
        return discoverAllAvailableTools()
            .map(allTools -> {
                Map<String, Integer> counts = new HashMap<>();
                allTools.forEach((serverId, tools) -> counts.put(serverId, tools.size()));
                return counts;
            });
    }

    /**
     * Public API: Get summary of all servers and their capabilities (Synchronous version for backward compatibility)
     */
    public Map<String, Integer> getServerToolCounts() {
        return getServerToolCountsAsync().block();
    }

    /**
     * Public API: Force refresh of tool cache (Reactive version)
     */
    public Mono<Void> refreshToolCacheAsync() {
        logger.info("Forcing refresh of tool cache");
        lastToolsCacheUpdate = 0;
        return discoverAllAvailableTools()
            .doOnNext(tools -> logger.info("Tool cache refreshed with {} servers", tools.size()))
            .then();
    }

    /**
     * Public API: Force refresh of tool cache (Synchronous version for backward compatibility)
     */
    public void refreshToolCache() {
        refreshToolCacheAsync().block();
    }

    /**
     * Universal Intent result - works with any MCP server type
     */
    public record Intent(
        boolean requiresToolExecution,
        String toolName,                 // Discovered dynamically from any server
        String serverId,                 // Any configured server ID
        Map<String, Object> parameters,  // Generic parameters for any tool
        double confidence,               // Confidence in the analysis
        String reasoning,                // AI reasoning for transparency
        String intentType,               // Type of intent: database_operation, file_operation, etc.
        List<String> suggestedSteps     // Multi-step workflow suggestions
    ) {
        // Convenience methods
        public String getOriginalMessage() {
            return reasoning; // For backward compatibility
        }

        public List<String> getSuggestedSteps() {
            return suggestedSteps;
        }
    }
}
