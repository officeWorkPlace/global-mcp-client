package com.deepai.mcpclient.service.impl;

import com.deepai.mcpclient.config.AiConfiguration;
import com.deepai.mcpclient.model.ChatRequest;
import com.deepai.mcpclient.model.ChatResponse;
import com.deepai.mcpclient.model.ConversationContext;
import com.deepai.mcpclient.model.McpToolResult;
import com.deepai.mcpclient.service.AiService;
import com.deepai.mcpclient.service.IntentProcessor;
import com.deepai.mcpclient.service.McpClientService;
import com.deepai.mcpclient.service.ResponseFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private final AiConfiguration.AiProperties aiProperties;
    
    // In-memory context storage (in production, use Redis or database)
    private final Map<String, ConversationContext> conversationContexts = new ConcurrentHashMap<>();
    // Cache recent tool results to avoid duplicate executions during formatting
    private final Map<String, McpToolResult> recentToolResults = new ConcurrentHashMap<>();

    public AiServiceImpl(ChatModel chatModel, 
                     McpClientService mcpClientService, 
                     AiConfiguration.AiProperties aiProperties) {
        this.chatModel = chatModel;
        this.mcpClientService = mcpClientService;
        this.intentProcessor = new IntentProcessor(mcpClientService, chatModel, new com.fasterxml.jackson.databind.ObjectMapper());
        this.responseFormatter = new ResponseFormatter(new com.fasterxml.jackson.databind.ObjectMapper(), this);
        this.aiProperties = aiProperties;
    }

    /**
     * Process natural language chat request
     */
    @Override
    public Mono<ChatResponse> processChat(ChatRequest request) {
        logger.info("Processing AI chat request: {}", request.message());

        ConversationContext context = getOrCreateContext(request.contextId());
        context.addUserMessage(request.message());

        if (request.serverId() != null) {
            context.setPreferredServerId(request.serverId());
        }

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
                        recentToolResults.put(cacheKey, result);
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
                    McpToolResult result = recentToolResults.get(cacheKey);
                    
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
        if (contextId != null && conversationContexts.containsKey(contextId)) {
            return conversationContexts.get(contextId);
        }
        
        ConversationContext context = contextId != null ? 
            new ConversationContext(contextId) : 
            new ConversationContext();
            
        conversationContexts.put(context.getContextId(), context);
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
     * Remove expired conversation contexts
     */
    @Scheduled(fixedRate = 1800000) // 30 minutes
    public void cleanupExpiredContexts() {
        int removed = 0;
        var iterator = conversationContexts.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().isExpired()) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired conversation contexts", removed);
        }
    }

    /**
     * Get conversation context by ID
     */
    @Override
    public ConversationContext getContext(String contextId) {
        return conversationContexts.get(contextId);
    }

    /**
     * Get all active contexts count
     */
    @Override
    public int getActiveContextsCount() {
        return conversationContexts.size();
    }
}