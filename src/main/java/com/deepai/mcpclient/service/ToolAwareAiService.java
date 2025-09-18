package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI service that is aware of available MCP tools and can plan multi-step operations
 */
@Service
public class ToolAwareAiService {

    private static final Logger logger = LoggerFactory.getLogger(ToolAwareAiService.class);

    private final McpClientService mcpClientService;
    private final ChatModel chatModel;
    private final ResponseFormatter responseFormatter;
    private final ObjectMapper objectMapper;

    @Autowired
    public ToolAwareAiService(McpClientService mcpClientService,
                             ChatModel chatModel,
                             ResponseFormatter responseFormatter) {
        this.mcpClientService = mcpClientService;
        this.chatModel = chatModel;
        this.responseFormatter = responseFormatter;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * AI agent that knows about available tools and can plan multi-step operations (Reactive version)
     */
    public Mono<com.deepai.mcpclient.model.ChatResponse> processWithToolAwarenessAsync(String userMessage, String contextId) {
        logger.info("🧠 Processing tool-aware request: '{}'", userMessage);

        return getAllAvailableTools()
            .flatMap(availableTools -> {
                if (availableTools.isEmpty()) {
                    logger.warn("⚠️ No tools available, falling back to conversational response");
                    return Mono.just(generateFallbackResponse(userMessage, contextId));
                }

                return processToolWorkflowAsync(userMessage, contextId, availableTools);
            })
            .onErrorResume(e -> {
                logger.error("❌ Tool-aware processing failed: {}", e.getMessage(), e);
                Exception exception = (e instanceof Exception) ? (Exception) e : new RuntimeException(e);
                return Mono.just(generateErrorResponse(userMessage, contextId, exception));
            });
    }

    /**
     * AI agent that knows about available tools and can plan multi-step operations (Synchronous version for backward compatibility)
     */
    public com.deepai.mcpclient.model.ChatResponse processWithToolAwareness(String userMessage, String contextId) {
        logger.warn("⚠️ Using blocking synchronous method - consider migrating to reactive processWithToolAwarenessAsync");
        try {
            return processWithToolAwarenessAsync(userMessage, contextId)
                .timeout(Duration.ofSeconds(30))
                .block();
        } catch (Exception e) {
            logger.error("❌ Synchronous tool processing failed: {}", e.getMessage());
            return generateErrorResponse(userMessage, contextId, e);
        }
    }

    private Mono<com.deepai.mcpclient.model.ChatResponse> processToolWorkflowAsync(String userMessage, String contextId, Map<String, List<McpTool>> availableTools) {
        // Step 2: Let AI analyze what tools are needed
        String toolAnalysisPrompt = buildToolAnalysisPrompt(userMessage, availableTools);

        return Mono.fromCallable(() -> chatModel.call(new Prompt(new UserMessage(toolAnalysisPrompt))))
            .map(analysisResponse -> parseExecutionPlan(analysisResponse.getResult().getOutput().getText()))
            .flatMap(plan -> executeToolPlanAsync(plan)
                .map(results -> Map.entry(plan, results)))
            .flatMap(planAndResults -> {
                ToolExecutionPlan plan = planAndResults.getKey();
                List<ToolResult> results = planAndResults.getValue();

                // Step 5: Let AI synthesize final response
                String synthesisPrompt = buildSynthesisPrompt(userMessage, plan, results);
                return Mono.fromCallable(() -> chatModel.call(new Prompt(new UserMessage(synthesisPrompt))))
                    .map(finalResponse -> buildChatResponseWithToolChain(
                        finalResponse.getResult().getOutput().getText(),
                        contextId,
                        plan.getSteps(),
                        results,
                        "gemini-1.5-pro"
                    ));
            });
    }

    private Mono<Map<String, List<McpTool>>> getAllAvailableTools() {
        List<String> serverIds = mcpClientService.getServerIds();

        return Flux.fromIterable(serverIds)
            .flatMap(serverId ->
                mcpClientService.listTools(serverId)
                    .doOnNext(tools -> {
                        if (tools != null && !tools.isEmpty()) {
                            logger.info("📋 Found {} tools on server {}", tools.size(), serverId);
                        }
                    })
                    .map(tools -> Map.entry(serverId, tools))
                    .onErrorResume(error -> {
                        logger.warn("⚠️ Failed to get tools from server {}: {}", serverId, error.getMessage());
                        return Mono.empty();
                    })
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    private String buildToolAnalysisPrompt(String userMessage, Map<String, List<McpTool>> availableTools) {
        StringBuilder toolsDescription = new StringBuilder();
        toolsDescription.append("AVAILABLE TOOLS:\n\n");

        for (Map.Entry<String, List<McpTool>> entry : availableTools.entrySet()) {
            String serverId = entry.getKey();
            List<McpTool> tools = entry.getValue();

            toolsDescription.append(String.format("Server: %s\n", serverId));
            for (McpTool tool : tools) {
                toolsDescription.append(String.format("- %s: %s\n",
                    tool.name(), tool.description()));

                // Add parameter info
                if (tool.inputSchema() != null) {
                    toolsDescription.append(String.format("  Parameters: %s\n",
                        formatSchema(tool.inputSchema())));
                }
            }
            toolsDescription.append("\n");
        }

        return String.format("""
            You are an expert AI agent with access to MCP tools.
            Analyze the user's request and create an execution plan.

            USER REQUEST: "%s"

            %s

            Please create a step-by-step execution plan in JSON format:
            {
              "analysis": "your analysis of what needs to be done",
              "steps": [
                {
                  "step": 1,
                  "action": "tool_name",
                  "server": "server_id",
                  "parameters": {},
                  "reasoning": "why this step is needed"
                }
              ],
              "expected_outcome": "what the user should expect"
            }

            IMPORTANT: Only use tools that are actually available in the list above.
            If multiple steps are needed, plan them in logical order.
            Keep parameters simple and based on the user's request.
            """, userMessage, toolsDescription.toString());
    }

    private String formatSchema(Map<String, Object> schema) {
        if (schema == null || schema.isEmpty()) {
            return "{}";
        }

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null) {
            return schema.toString();
        }

        return properties.keySet().toString();
    }

    private ToolExecutionPlan parseExecutionPlan(String aiResponse) {
        try {
            // Clean up AI response (remove markdown formatting if present)
            String cleanJson = aiResponse.replaceAll("```json", "").replaceAll("```", "").trim();

            JsonNode planJson = objectMapper.readTree(cleanJson);

            ToolExecutionPlan plan = new ToolExecutionPlan();
            plan.setAnalysis(planJson.get("analysis").asText());
            plan.setExpectedOutcome(planJson.get("expected_outcome").asText());

            JsonNode stepsArray = planJson.get("steps");
            List<ToolExecutionStep> steps = new ArrayList<>();

            for (JsonNode stepNode : stepsArray) {
                ToolExecutionStep step = new ToolExecutionStep();
                step.setStepNumber(stepNode.get("step").asInt());
                step.setAction(stepNode.get("action").asText());
                step.setServerId(stepNode.get("server").asText());
                step.setReasoning(stepNode.get("reasoning").asText());

                // Parse parameters
                JsonNode params = stepNode.get("parameters");
                Map<String, Object> parameters = objectMapper.convertValue(params, Map.class);
                step.setParameters(parameters);

                steps.add(step);
            }

            plan.setSteps(steps);
            logger.info("✅ Parsed execution plan with {} steps", steps.size());
            return plan;

        } catch (Exception e) {
            logger.error("❌ Failed to parse AI execution plan: {}", e.getMessage());
            logger.debug("Raw AI response: {}", aiResponse);
            // Fallback to simple plan
            return createFallbackPlan(aiResponse);
        }
    }

    private ToolExecutionPlan createFallbackPlan(String originalMessage) {
        logger.info("🔄 Creating fallback execution plan");

        ToolExecutionStep fallbackStep = new ToolExecutionStep();
        fallbackStep.setStepNumber(1);
        fallbackStep.setAction("help");
        fallbackStep.setServerId(mcpClientService.getServerIds().iterator().next());
        fallbackStep.setParameters(Map.of());
        fallbackStep.setReasoning("Fallback plan due to parsing failure");

        ToolExecutionPlan plan = new ToolExecutionPlan();
        plan.setAnalysis("Unable to parse complex plan, using simple fallback");
        plan.setExpectedOutcome("Basic help response");
        plan.setSteps(List.of(fallbackStep));

        return plan;
    }

    private Mono<List<ToolResult>> executeToolPlanAsync(ToolExecutionPlan plan) {
        logger.info("🔧 Executing plan with {} steps", plan.getSteps().size());

        return Flux.fromIterable(plan.getSteps())
            .concatMap(step -> executeStepAsync(step)
                .delayElement(Duration.ofMillis(100)) // Small delay between tools
                .onErrorResume(e -> {
                    logger.error("❌ Step {} failed: {}", step.getStepNumber(), e.getMessage());
                    ToolResult errorResult = new ToolResult(step, (Exception) e);

                    // Decide whether to continue or abort
                    if (step.isCritical()) {
                        logger.error("💥 Critical step failed, aborting execution plan");
                        return Mono.error(e);
                    }
                    return Mono.just(errorResult);
                })
            )
            .collectList();
    }

    private Mono<ToolResult> executeStepAsync(ToolExecutionStep step) {
        logger.info("🔧 Executing step {}: {} on server {}",
            step.getStepNumber(), step.getAction(), step.getServerId());

        long startTime = System.currentTimeMillis();

        return mcpClientService.executeTool(
                step.getServerId(),
                step.getAction(),
                step.getParameters()
            )
            .map(mcpResult -> {
                long executionTime = System.currentTimeMillis() - startTime;
                ToolResult result = new ToolResult(step, mcpResult);

                if (result.isSuccess()) {
                    logger.info("✅ Step {} completed successfully in {}ms", step.getStepNumber(), executionTime);
                } else {
                    logger.warn("❌ Step {} failed: {}", step.getStepNumber(), result.getErrorMessage());
                }

                return result;
            });
    }

    private String buildSynthesisPrompt(String userMessage, ToolExecutionPlan plan, List<ToolResult> results) {
        StringBuilder resultsDescription = new StringBuilder();
        resultsDescription.append("EXECUTION RESULTS:\n\n");

        for (ToolResult result : results) {
            resultsDescription.append(String.format("Step %d (%s):\n",
                result.getStep().getStepNumber(),
                result.getStep().getAction()));

            if (result.isSuccess()) {
                resultsDescription.append(String.format("✅ Success: %s\n",
                    result.getFormattedOutput()));
            } else {
                resultsDescription.append(String.format("❌ Failed: %s\n",
                    result.getErrorMessage()));
            }
            resultsDescription.append("\n");
        }

        return String.format("""
            You executed a multi-step plan to help the user. Now provide a final response.

            ORIGINAL REQUEST: "%s"

            EXECUTION PLAN: %s

            %s

            Please provide a helpful, human-readable response that:
            1. Summarizes what was accomplished
            2. Presents the key results clearly
            3. Addresses the user's original question
            4. Suggests next steps if appropriate

            Be conversational and explain technical results in plain language.
            """, userMessage, plan.getAnalysis(), resultsDescription.toString());
    }

    private com.deepai.mcpclient.model.ChatResponse buildChatResponseWithToolChain(
            String response, String contextId, List<ToolExecutionStep> steps,
            List<ToolResult> results, String model) {

        // Convert to ChatResponse.ToolExecution format
        List<com.deepai.mcpclient.model.ChatResponse.ToolExecution> toolExecutions = new ArrayList<>();

        for (int i = 0; i < Math.min(steps.size(), results.size()); i++) {
            ToolExecutionStep step = steps.get(i);
            ToolResult result = results.get(i);

            com.deepai.mcpclient.model.ChatResponse.ToolExecution execution =
                new com.deepai.mcpclient.model.ChatResponse.ToolExecution(
                    step.getAction(),
                    step.getServerId(),
                    step.getParameters(),
                    result.isSuccess(),
                    0 // execution time not tracked here
                );

            toolExecutions.add(execution);
        }

        return com.deepai.mcpclient.model.ChatResponse.withTools(response, contextId, toolExecutions, model);
    }

    private com.deepai.mcpclient.model.ChatResponse generateFallbackResponse(String userMessage, String contextId) {
        String fallbackResponse = "I understand you want help, but I currently don't have access to any MCP tools. " +
                                "Please check that MCP servers are properly connected and try again.";
        return com.deepai.mcpclient.model.ChatResponse.success(fallbackResponse, contextId);
    }

    private com.deepai.mcpclient.model.ChatResponse generateErrorResponse(String userMessage, String contextId, Exception e) {
        String errorResponse = "I encountered an error while processing your request: " + e.getMessage() +
                             ". Please try rephrasing your question or check the system logs.";
        return com.deepai.mcpclient.model.ChatResponse.error(errorResponse, contextId);
    }
}