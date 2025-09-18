package com.deepai.mcpclient.service.impl;

import com.deepai.mcpclient.config.AiConfiguration;
import com.deepai.mcpclient.model.*;
import com.deepai.mcpclient.security.InputValidationService;
import com.deepai.mcpclient.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import reactor.core.publisher.Mono;
import jakarta.annotation.PostConstruct;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * AI Service that handles natural language processing and integration with MCP tools
 */
@Service
public class AiServiceImpl implements AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiServiceImpl.class);

    private final ChatModel chatModel;
    private final McpClientService mcpClientService;
    private final IntentProcessor intentProcessor;
    private final ResponseFormatter responseFormatter;
    private final InputValidationService inputValidationService;
    private final ResilienceService resilienceService;
    private final AiConfiguration.AiProperties aiProperties;

    // Proper cache managers with size limits and TTL
    @Autowired
    @Qualifier("conversationCacheManager")
    private CacheManager conversationCacheManager;

    @Autowired
    @Qualifier("toolResultsCacheManager")
    private CacheManager toolResultsCacheManager;

    @Autowired
    @Qualifier("toolsCacheManager")
    private CacheManager toolsCacheManager;

    private ScheduledExecutorService toolRefreshScheduler;

    // Helper methods for cache access
    private Cache getConversationCache() {
        return conversationCacheManager.getCache("conversationContexts");
    }

    private Cache getToolResultsCache() {
        return toolResultsCacheManager.getCache("toolResults");
    }

    private Cache getToolsCache() {
        return toolsCacheManager.getCache("availableTools");
    }

    public AiServiceImpl(ChatModel chatModel,
                     McpClientService mcpClientService,
                     InputValidationService inputValidationService,
                     ResilienceService resilienceService,
                     AiConfiguration.AiProperties aiProperties) {
        this.chatModel = chatModel;
        this.mcpClientService = mcpClientService;
        this.inputValidationService = inputValidationService;
        this.resilienceService = resilienceService;
        this.intentProcessor = new IntentProcessor(mcpClientService, chatModel, new com.fasterxml.jackson.databind.ObjectMapper());
        this.responseFormatter = new ResponseFormatter(new com.fasterxml.jackson.databind.ObjectMapper(), this);
        this.aiProperties = aiProperties;
    }

    /**
     * Initialize AI tool awareness on startup
     */
    @PostConstruct
    public void initializeToolAwareness() {
        logger.info("🔧 Initializing AI tool awareness...");

        // Cache available tools for AI context
        refreshToolCache();

        // Schedule periodic refresh
        this.toolRefreshScheduler = Executors.newScheduledThreadPool(1);
        toolRefreshScheduler.scheduleAtFixedRate(this::refreshToolCache, 5, 30, TimeUnit.MINUTES);

        Map<String, List<McpTool>> availableTools = getAvailableTools();
        logger.info("✅ AI tool awareness initialized with {} servers", availableTools.size());
    }


    /**
     * Get cached available tools
     */
    @Cacheable(value = "availableTools", key = "'all-tools'", cacheManager = "toolsCacheManager")
    public Map<String, List<McpTool>> getAvailableTools() {
        // This will be cached - only executed if cache is empty or expired
        Map<String, List<McpTool>> tools = new HashMap<>();

        for (String serverId : mcpClientService.getServerIds()) {
            try {
                List<McpTool> serverTools = mcpClientService.listTools(serverId).block();
                if (serverTools != null && !serverTools.isEmpty()) {
                    tools.put(serverId, serverTools);
                }
            } catch (Exception e) {
                logger.warn("Failed to get tools from server {}: {}", serverId, e.getMessage());
            }
        }

        logger.debug("Refreshed tools cache with {} servers", tools.size());
        return tools;
    }

    /**
     * Force refresh of tools cache
     */
    @CacheEvict(value = "availableTools", key = "'all-tools'", cacheManager = "toolsCacheManager")
    public void refreshToolCache() {
        logger.info("🔄 Forcing refresh of tools cache");
        // Cache will be refreshed on next getAvailableTools() call
    }

    /**
     * Process natural language chat request
     */
    @Override
    public Mono<ChatResponse> processChat(ChatRequest request) {
        logger.info("Processing AI chat request: {}", request.message().length() > 100 ?
            request.message().substring(0, 100) + "..." : request.message());

        // Apply user request rate limiting
        String userId = request.contextId() != null ? request.contextId() : "anonymous";

        // Check if user can make request
        if (!resilienceService.canUserMakeRequest()) {
            logger.warn("🚫 User rate limit exceeded for user: {}", userId);
            return Mono.just(ChatResponse.error("Rate limit exceeded. Please wait before making another request.", request.contextId()));
        }

        return processChatInternal(request);
    }

    /**
     * Internal chat processing with validation and AI logic
     */
    private Mono<ChatResponse> processChatInternal(ChatRequest request) {
        // Validate and sanitize user input
        InputValidationService.ValidationResult messageValidation = inputValidationService.validateUserInput(request.message());
        if (!messageValidation.isValid()) {
            logger.warn("🚨 Invalid input rejected: {}", messageValidation.getErrorMessage());
            return Mono.just(ChatResponse.error("Invalid input: " + messageValidation.getErrorMessage(), request.contextId()));
        }

        // Validate context ID
        InputValidationService.ValidationResult contextValidation = inputValidationService.validateContextId(request.contextId());
        if (!contextValidation.isValid()) {
            logger.warn("🚨 Invalid context ID rejected: {}", contextValidation.getErrorMessage());
            return Mono.just(ChatResponse.error("Invalid context ID: " + contextValidation.getErrorMessage(), null));
        }

        // Create request with sanitized input
        ChatRequest sanitizedRequest = new ChatRequest(
            messageValidation.getSanitizedInput(),
            request.serverId(),
            contextValidation.getSanitizedInput(),
            request.stream()
        );

        // Check for high-risk input patterns
        if (inputValidationService.isHighRiskInput(request.message())) {
            logger.warn("🚨 High-risk input pattern detected, applying additional monitoring");
        }

        ConversationContext context = getOrCreateContext(sanitizedRequest.contextId());
        context.addUserMessage(sanitizedRequest.message());

        if (sanitizedRequest.serverId() != null) {
            context.setPreferredServerId(sanitizedRequest.serverId());
        }

        return Mono.fromCallable(() -> {
            // Check if we should use enhanced tool-aware processing
            Map<String, List<McpTool>> availableTools = getAvailableTools();
            if (!availableTools.isEmpty() && isComplexRequest(sanitizedRequest.message())) {
                logger.info("🧠 Using enhanced tool-aware processing for complex request");
                return processWithToolAwareness(sanitizedRequest.message(), context);
            } else {
                logger.info("🔄 Using traditional intent-based processing");
                return null; // Signal to use traditional approach
            }
        })
        .flatMap(enhancedResponse -> {
            if (enhancedResponse != null) {
                return Mono.just(enhancedResponse);
            } else {
                return processWithTraditionalApproach(sanitizedRequest, context);
            }
        })
        .doOnSuccess(response -> logger.info("AI chat processed successfully for context: {}", response.contextId()))
        .doOnError(error -> logger.error("AI chat processing failed: {}", error.getMessage()))
        .onErrorResume(e -> {
            String errorResponse = "I encountered an error while processing your request. Please try again or rephrase your question.";
            context.addAssistantMessage(errorResponse);
            return Mono.just(ChatResponse.error(errorResponse, context.getContextId()));
        });
    }

    /**
     * Determine if a request is complex enough to warrant tool-aware processing
     */
    private boolean isComplexRequest(String message) {
        String lower = message.toLowerCase();

        // Complex indicators
        return lower.contains("and then") ||
               lower.contains("after that") ||
               lower.contains("first") && (lower.contains("then") || lower.contains("next")) ||
               lower.contains("show me") && lower.contains("then") ||
               lower.contains("find") && lower.contains("in") ||
               lower.contains("analyze") ||
               lower.contains("compare") ||
               lower.contains("all") && lower.contains("with") ||
               message.split("\\s+").length > 15; // Long requests are often complex
    }

    /**
     * Process request with enhanced tool awareness (integrated approach)
     */
    private ChatResponse processWithToolAwareness(String userMessage, ConversationContext context) {
        logger.info("🧠 Processing with integrated tool awareness: '{}'", userMessage);

        try {
            // Use enhanced intent analysis with available tools
            IntentProcessor.Intent intent = intentProcessor.analyzeIntentWithToolAwareness(
                userMessage, getAvailableTools()
            );

            if (intent.requiresToolExecution()) {
                if (intent.getSuggestedSteps() != null && intent.getSuggestedSteps().size() > 1) {
                    // Multi-step execution - implement simplified version
                    return executeMultiStepWorkflow(intent, context);
                } else {
                    // Enhanced single tool execution
                    return executeEnhancedSingleTool(intent, context);
                }
            } else {
                // Generate enhanced conversational response
                String aiResponse = generateEnhancedResponse(userMessage, context);
                context.addAssistantMessage(aiResponse);
                return ChatResponse.success(aiResponse, context.getContextId(), "gemini-1.5-pro");
            }

        } catch (Exception e) {
            logger.error("❌ Tool-aware processing failed: {}", e.getMessage(), e);
            // Fall back to traditional processing
            return generateFallbackResponse(userMessage, context);
        }
    }

    /**
     * Execute multi-step workflow (simplified version)
     */
    private ChatResponse executeMultiStepWorkflow(IntentProcessor.Intent intent, ConversationContext context) {
        logger.info("🔗 Executing simplified multi-step workflow");

        List<ChatResponse.ToolExecution> allExecutions = new ArrayList<>();
        StringBuilder workflowResults = new StringBuilder("Multi-step execution results:\n\n");

        // Execute suggested steps sequentially
        for (int i = 0; i < intent.getSuggestedSteps().size(); i++) {
            String step = intent.getSuggestedSteps().get(i);
            logger.info("🔧 Executing step {}: {}", i + 1, step);

            try {
                // Create simple intent for this step
                IntentProcessor.Intent stepIntent = intentProcessor.analyzeIntent(step);

                if (stepIntent.requiresToolExecution()) {
                    long startTime = System.currentTimeMillis();
                    McpToolResult result = mcpClientService.executeTool(
                        stepIntent.serverId(),
                        stepIntent.toolName(),
                        stepIntent.parameters()
                    ).block();

                    long executionTime = System.currentTimeMillis() - startTime;
                    boolean success = result != null && (result.isError() == null || !result.isError());

                    ChatResponse.ToolExecution execution = new ChatResponse.ToolExecution(
                        stepIntent.toolName(),
                        stepIntent.serverId(),
                        stepIntent.parameters(),
                        success,
                        executionTime
                    );
                    allExecutions.add(execution);

                    if (success) {
                        String formattedResult = responseFormatter.formatToolResult(stepIntent.toolName(), result);
                        workflowResults.append(String.format("Step %d (%s): %s\n",
                            i + 1, stepIntent.toolName(), formattedResult));
                        logger.info("✅ Step {} completed in {}ms", i + 1, executionTime);
                    } else {
                        workflowResults.append(String.format("Step %d (%s): Failed\n",
                            i + 1, stepIntent.toolName()));
                        logger.warn("❌ Step {} failed", i + 1);
                    }
                }

            } catch (Exception e) {
                logger.error("❌ Step {} execution failed: {}", i + 1, e.getMessage());
                workflowResults.append(String.format("Step %d: Error - %s\n", i + 1, e.getMessage()));
            }
        }

        // Generate final AI response based on all results
        String finalResponse = generateWorkflowSummary(intent.getOriginalMessage(), workflowResults.toString(), context);
        context.addAssistantMessage(finalResponse);

        return ChatResponse.withTools(finalResponse, context.getContextId(), allExecutions, "gemini-1.5-pro");
    }

    /**
     * Execute enhanced single tool
     */
    private ChatResponse executeEnhancedSingleTool(IntentProcessor.Intent intent, ConversationContext context) {
        String serverId = intent.serverId() != null ? intent.serverId() :
                context.getPreferredServerId() != null ? context.getPreferredServerId() :
                getDefaultServerId();

        if (serverId == null) {
            logger.warn("No server ID available for enhanced tool execution");
            return generateFallbackResponse(intent.getOriginalMessage(), context);
        }

        long startTime = System.currentTimeMillis();

        try {
            McpToolResult result = mcpClientService.executeTool(serverId, intent.toolName(), intent.parameters()).block();
            long executionTime = System.currentTimeMillis() - startTime;
            boolean success = result != null && (result.isError() == null || !result.isError());

            ChatResponse.ToolExecution execution = new ChatResponse.ToolExecution(
                intent.toolName(),
                serverId,
                intent.parameters(),
                success,
                executionTime
            );

            if (success) {
                String aiResponse = generateResponseWithToolResults(
                    intent.getOriginalMessage(),
                    intent,
                    List.of(execution),
                    context
                );
                context.addAssistantMessage(aiResponse);
                return ChatResponse.withTools(aiResponse, context.getContextId(), List.of(execution), "gemini-1.5-pro");
            } else {
                return generateFallbackResponse(intent.getOriginalMessage(), context);
            }

        } catch (Exception e) {
            logger.error("❌ Enhanced tool execution failed: {}", e.getMessage());
            return generateFallbackResponse(intent.getOriginalMessage(), context);
        }
    }

    /**
     * Generate enhanced conversational response
     */
    private String generateEnhancedResponse(String userMessage, ConversationContext context) {
        try {
            String toolsContext = buildAvailableToolsContext();
            String prompt = String.format("""
                You are an expert MCP assistant with access to various tools.

                User: %s

                Available tools: %s

                Conversation context: %s

                Provide a helpful response. If the user needs tool assistance,
                guide them on what they can do with the available tools.
                """, userMessage, toolsContext, context.getContextSummary());

            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
            return response.getResult().getOutput().getText();

        } catch (Exception e) {
            logger.error("❌ Enhanced response generation failed: {}", e.getMessage());
            return "I'm here to help you with MCP operations. You can ask me to interact with databases, files, and other services through the available tools.";
        }
    }

    /**
     * Build context of available tools
     */
    private String buildAvailableToolsContext() {
        Map<String, List<McpTool>> availableTools = getAvailableTools();
        if (availableTools.isEmpty()) {
            return "No tools currently available";
        }

        StringBuilder context = new StringBuilder();
        for (Map.Entry<String, List<McpTool>> entry : availableTools.entrySet()) {
            String serverId = entry.getKey();
            List<McpTool> tools = entry.getValue();
            context.append(String.format("Server %s: %d tools available\n", serverId, tools.size()));
        }

        return context.toString();
    }

    /**
     * Generate workflow summary
     */
    private String generateWorkflowSummary(String originalRequest, String workflowResults, ConversationContext context) {
        String summaryPrompt = String.format("""
            Summarize the results of a multi-step workflow:

            Original request: "%s"

            Execution results:
            %s

            Provide a helpful, conversational summary that explains what was accomplished.
            """, originalRequest, workflowResults);

        try {
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(new UserMessage(summaryPrompt)));
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            logger.error("❌ Workflow summary generation failed: {}", e.getMessage());
            return "I completed the multi-step workflow. The operations were executed successfully.";
        }
    }

    /**
     * Generate fallback response
     */
    private ChatResponse generateFallbackResponse(String userMessage, ConversationContext context) {
        String fallbackResponse = "I understand your request, but encountered an issue with the enhanced processing. Let me try a simpler approach.";
        context.addAssistantMessage(fallbackResponse);
        return ChatResponse.success(fallbackResponse, context.getContextId(), "pattern-matching");
    }

    /**
     * Traditional intent-based processing for simple requests
     */
    private Mono<ChatResponse> processWithTraditionalApproach(ChatRequest request, ConversationContext context) {
        return Mono.fromCallable(() -> intentProcessor.analyzeIntent(request.message()))
                .flatMap(intent -> {
                    if (intent.requiresToolExecution()) {
                        return executeTools(intent, context)
                                .flatMap(toolExecutions -> {
                                    String aiResponse = generateResponseWithToolResults(request.message(), intent, toolExecutions, context);
                                    context.addAssistantMessage(aiResponse);
                                    String modelUsed = determineModelUsed(aiResponse);
                                    return Mono.just(ChatResponse.withTools(aiResponse, context.getContextId(), toolExecutions, modelUsed));
                                });
                    } else {
                        String aiResponse = generateSimpleResponse(request.message(), context);
                        context.addAssistantMessage(aiResponse);
                        String modelUsed = determineModelUsed(aiResponse);
                        return Mono.just(ChatResponse.success(aiResponse, context.getContextId(), modelUsed));
                    }
                });
    }

    /**
     * Simple ask endpoint for quick questions
     */
    @Override
    public Mono<String> ask(String question) {
        return processChat(ChatRequest.simple(question))
                .map(ChatResponse::response);
    }

    /**
     * Execute MCP tools based on intent
     */
    private Mono<List<ChatResponse.ToolExecution>> executeTools(IntentProcessor.Intent intent, ConversationContext context) {
        String serverId = intent.serverId() != null ? intent.serverId() : 
                context.getPreferredServerId() != null ? context.getPreferredServerId() :
                getDefaultServerId();

        if (serverId == null) {
            logger.warn("No server ID available for tool execution");
            return Mono.just(new ArrayList<>());
        }

        long startTime = System.currentTimeMillis();

        return mcpClientService.executeTool(serverId, intent.toolName(), intent.parameters())
                .map(result -> {
                    // Cache the result to avoid re-execution during formatting
                    String cacheKey = buildResultCacheKey(intent.toolName(), serverId, intent.parameters());
                    if (result != null) {
                        getToolResultsCache().put(cacheKey, result);
                    }

                    long executionTime = System.currentTimeMillis() - startTime;
                    boolean success = result != null && (result.isError() == null || !result.isError());

                    ChatResponse.ToolExecution execution = new ChatResponse.ToolExecution(
                            intent.toolName(),
                            serverId,
                            intent.parameters(),
                            success,
                            executionTime
                    );
                    logger.info("Tool {} executed in {}ms with success: {}", intent.toolName(), executionTime, success);
                    return List.of(execution);
                })
                .onErrorResume(e -> {
                    logger.error("Error executing tool {}: {}", intent.toolName(), e.getMessage(), e);
                    ChatResponse.ToolExecution execution = new ChatResponse.ToolExecution(
                            intent.toolName(),
                            intent.serverId(),
                            intent.parameters(),
                            false,
                            0
                    );
                    return Mono.just(List.of(execution));
                });
    }

    /**
     * Generate AI response with tool execution results
     */
    private String generateResponseWithToolResults(String userMessage, 
                                                 IntentProcessor.Intent intent, 
                                                 List<ChatResponse.ToolExecution> toolExecutions,
                                                 ConversationContext context) {
        try {
            // Get tool results for context
            StringBuilder toolResultsContext = new StringBuilder();
            for (ChatResponse.ToolExecution execution : toolExecutions) {
                if (execution.success()) {
                    // Use cached tool result to avoid re-execution
                    String cacheKey = buildResultCacheKey(execution.toolName(), execution.serverId(), execution.parameters());
                    McpToolResult result = getToolResultsCache().get(cacheKey, McpToolResult.class);
                    
                    if (result != null) {
                        String formattedResult = responseFormatter.formatToolResult(execution.toolName(), result);
                        toolResultsContext.append("Tool ").append(execution.toolName()).append(" result:\n");
                        toolResultsContext.append(formattedResult).append("\n\n");
                    }
                }
            }

            String prompt = "User asked: \"" + userMessage + "\"\n\n" +
                    context.getContextSummary() + "\n\n" +
                    "Tool execution results:\n" +
                    toolResultsContext.toString() + "\n\n" +
                    "Please provide a helpful, human-readable response based on the tool results.\n" +
                    "Be conversational and explain the results clearly.";

            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
            return response.getResult().getOutput().getText();
                    
        } catch (Exception e) {
            logger.error("Error generating AI response with tool results: {}", e.getMessage(), e);
            return "I executed the requested operation, but encountered an issue formatting the response. The operation may have completed successfully.";
        }
    }

    private String buildResultCacheKey(String toolName, String serverId, Map<String, Object> parameters) {
        String paramsKey;
        try {
            paramsKey = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(parameters != null ? parameters : Map.of());
        } catch (Exception e) {
            paramsKey = String.valueOf(parameters);
        }
        return toolName + "|" + serverId + "|" + paramsKey;
    }

    /**
     * Generate simple conversational response
     */
    private String generateSimpleResponse(String userMessage, ConversationContext context) {
        try {
            String prompt = context.getContextSummary() + "\n\n" +
                    "User: " + userMessage + "\n\n" +
                    "Please respond helpfully. If the user is asking about MCP operations, \n" +
                    "guide them on what they can do with the available tools.";

            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(new UserMessage(prompt)));
            return response.getResult().getOutput().getText();
                    
        } catch (Exception e) {
            logger.error("Error generating simple AI response: {}", e.getMessage(), e);
            return "I'm here to help you interact with MCP servers. You can ask me to list databases, find documents, check server health, and more!";
        }
    }
    
    /**
     * Determine which model was used based on the response content
     */
    private String determineModelUsed(String response) {
        // If response contains tool commands or pattern-matching keywords, it's pattern matching
        if (response != null && (
            response.startsWith("tool exec") || 
            response.equals("help") || 
            response.equals("clear") || 
            response.equals("exit") ||
            response.contains("server list") ||
            response.equals("tool all")
        )) {
            return "pattern-matching";
        }
        // Otherwise, assume it came from Gemini (or at least attempted to)
        return "gemini-1.5-flash";
    }

    /**
     * Get or create conversation context
     */
    private ConversationContext getOrCreateContext(String contextId) {
        if (contextId != null) {
            ConversationContext cached = getConversationCache().get(contextId, ConversationContext.class);
            if (cached != null) {
                return cached;
            }
        }

        ConversationContext context = contextId != null ?
            new ConversationContext(contextId) :
            new ConversationContext();

        getConversationCache().put(context.getContextId(), context);
        return context;
    }

    /**
     * Get default server ID (first available server)
     */
    private String getDefaultServerId() {
        List<String> serverIds = mcpClientService.getServerIds();
        return serverIds.isEmpty() ? null : serverIds.get(0);
    }

    /**
     * Cache maintenance and statistics logging
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void logCacheStatistics() {
        try {
            Cache conversationCache = getConversationCache();
            Cache toolResultsCache = getToolResultsCache();
            Cache toolsCache = getToolsCache();

            logger.info("📊 Cache Statistics:");

            if (conversationCache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                var caffeineCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) conversationCache.getNativeCache();
                var stats = caffeineCache.stats();
                logger.info("   💬 Conversations: {} entries, {:.2f}% hit rate",
                    caffeineCache.estimatedSize(), stats.hitRate() * 100);
            }

            if (toolResultsCache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
                var caffeineCache = (com.github.benmanes.caffeine.cache.Cache<?, ?>) toolResultsCache.getNativeCache();
                logger.info("   🔧 Tool results: {} entries", caffeineCache.estimatedSize());
            }

            logger.info("   📋 Available tools: cached with auto-refresh");

        } catch (Exception e) {
            logger.warn("Failed to get cache statistics: {}", e.getMessage());
        }
    }

    /**
     * Cleanup resources on shutdown
     */
    public void destroy() {
        if (toolRefreshScheduler != null && !toolRefreshScheduler.isShutdown()) {
            toolRefreshScheduler.shutdown();
            try {
                if (!toolRefreshScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    toolRefreshScheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                toolRefreshScheduler.shutdownNow();
            }
        }
    }

    /**
     * Get conversation context by ID
     */
    @Override
    public ConversationContext getContext(String contextId) {
        return getConversationCache().get(contextId, ConversationContext.class);
    }

    /**
     * Get all active contexts count
     */
    @Override
    public int getActiveContextsCount() {
        Cache cache = getConversationCache();
        if (cache.getNativeCache() instanceof com.github.benmanes.caffeine.cache.Cache) {
            return (int) ((com.github.benmanes.caffeine.cache.Cache<?, ?>) cache.getNativeCache()).estimatedSize();
        }
        return 0;
    }
}