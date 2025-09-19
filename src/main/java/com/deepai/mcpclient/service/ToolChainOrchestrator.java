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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Advanced tool chain orchestrator for complex multi-step operations
 */
@Service
public class ToolChainOrchestrator {

    private static final Logger logger = LoggerFactory.getLogger(ToolChainOrchestrator.class);

    private final McpClientService mcpClientService;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    @Autowired
    public ToolChainOrchestrator(McpClientService mcpClientService, ChatModel chatModel) {
        this.mcpClientService = mcpClientService;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Execute complex multi-step operations intelligently
     */
    public ChainExecutionResult executeToolChain(String userGoal, ConversationContext context) {
        logger.info("🎯 Executing tool chain for goal: '{}'", userGoal);

        try {
            // Step 1: Break down the goal into sub-tasks
            List<String> subTasks = decomposeGoal(userGoal);

            // Step 2: For each sub-task, determine required tools
            List<ToolChainStep> steps = new ArrayList<>();
            for (int i = 0; i < subTasks.size(); i++) {
                String subTask = subTasks.get(i);
                ToolChainStep step = planStepExecution(subTask, context, steps);
                if (step != null) {
                    step.setStepNumber(i + 1);
                    steps.add(step);
                }
            }

            // Step 3: Execute steps with dependency management
            return executeStepsWithDependencies(steps, context);

        } catch (Exception e) {
            logger.error("❌ Tool chain execution failed: {}", e.getMessage(), e);
            return new ChainExecutionResult(List.of(), Map.of());
        }
    }

    /**
     * Break down complex goals into actionable sub-tasks
     */
    private List<String> decomposeGoal(String userGoal) {
        String decompositionPrompt = String.format("""
            Break down this complex goal into simple, actionable sub-tasks:

            GOAL: "%s"

            Return a JSON array of sub-tasks in logical order:
            ["sub-task 1", "sub-task 2", ...]

            Each sub-task should be:
            - Specific and executable with a single MCP tool
            - Independent or clearly dependent on previous tasks
            - Focused on one operation (list, find, create, update, etc.)

            Examples:
            - "Show databases then find users" → ["List all databases", "Find user documents"]
            - "Check server health and get file list" → ["Check server health", "List files in directory"]
            """, userGoal);

        try {
            var response = chatModel.call(new Prompt(new UserMessage(decompositionPrompt)));
            String responseText = response.getResult().getOutput().getText();

            // Clean up response and parse JSON
            String cleanJson = responseText.replaceAll("```json", "").replaceAll("```", "").trim();

            @SuppressWarnings("unchecked")
            List<String> tasks = objectMapper.readValue(cleanJson, List.class);

            logger.info("🔗 Decomposed goal into {} sub-tasks: {}", tasks.size(), tasks);
            return tasks;

        } catch (Exception e) {
            logger.warn("⚠️ Failed to decompose goal, using fallback: {}", e.getMessage());
            return List.of(userGoal); // Fallback to single task
        }
    }

    /**
     * Plan execution for a single step within the chain
     */
    private ToolChainStep planStepExecution(String subTask, ConversationContext context, List<ToolChainStep> previousSteps) {
        // Get available tools
        Map<String, List<McpTool>> availableTools = getAllAvailableTools();

        if (availableTools.isEmpty()) {
            logger.warn("⚠️ No tools available for step planning");
            return null;
        }

        // Get results from previous steps for context
        String previousResults = formatPreviousResults(previousSteps);

        String planningPrompt = String.format("""
            Plan how to execute this sub-task using available MCP tools:

            SUB-TASK: "%s"

            PREVIOUS RESULTS: %s

            AVAILABLE TOOLS: %s

            Return a JSON object:
            {
              "tool_name": "name_of_tool_to_use",
              "server_id": "server_to_use",
              "parameters": {},
              "reasoning": "why this tool/server combination",
              "depends_on": [],
              "is_critical": true
            }

            IMPORTANT:
            - Choose tools that actually exist in the available list
            - Keep parameters simple and relevant to the sub-task
            - Set is_critical to true if failure would break the entire chain
            """, subTask, previousResults, formatAvailableTools(availableTools));

        try {
            var response = chatModel.call(new Prompt(new UserMessage(planningPrompt)));
            return parseStepPlan(response.getResult().getOutput().getText(), subTask);

        } catch (Exception e) {
            logger.warn("⚠️ Step planning failed, using fallback: {}", e.getMessage());
            return createFallbackStepPlan(subTask, availableTools);
        }
    }

    /**
     * Get available tools from all servers (Reactive version)
     */
    private Mono<Map<String, List<McpTool>>> getAllAvailableToolsAsync() {
        List<String> serverIds = mcpClientService.getServerIds();

        return Flux.fromIterable(serverIds)
            .flatMap(serverId ->
                mcpClientService.listTools(serverId)
                    .map(tools -> Map.entry(serverId, tools))
                    .onErrorResume(error -> {
                        logger.warn("⚠️ Failed to get tools from server {}: {}", serverId, error.getMessage());
                        return Mono.empty();
                    })
            )
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * Get available tools from all servers (Synchronous version for backward compatibility)
     */
    private Map<String, List<McpTool>> getAllAvailableTools() {
        return getAllAvailableToolsAsync().block();
    }

    /**
     * Format previous step results for context
     */
    private String formatPreviousResults(List<ToolChainStep> previousSteps) {
        if (previousSteps.isEmpty()) {
            return "No previous steps executed yet.";
        }

        StringBuilder results = new StringBuilder("Previous step results:\n");
        for (ToolChainStep step : previousSteps) {
            results.append(String.format("- Step %d: %s using %s\n",
                step.getStepNumber(), step.getAction(), step.getServerId()));
        }

        return results.toString();
    }

    /**
     * Format available tools for AI prompt
     */
    private String formatAvailableTools(Map<String, List<McpTool>> availableTools) {
        StringBuilder formatted = new StringBuilder();

        for (Map.Entry<String, List<McpTool>> entry : availableTools.entrySet()) {
            String serverId = entry.getKey();
            List<McpTool> tools = entry.getValue();

            formatted.append(String.format("Server: %s\n", serverId));
            for (McpTool tool : tools) {
                formatted.append(String.format("- %s: %s\n", tool.name(), tool.description()));
            }
            formatted.append("\n");
        }

        return formatted.toString();
    }

    /**
     * Parse AI response into structured step plan
     */
    private ToolChainStep parseStepPlan(String aiPlan, String originalStep) {
        try {
            String cleanJson = aiPlan.replaceAll("```json", "").replaceAll("```", "").trim();
            JsonNode plan = objectMapper.readTree(cleanJson);

            ToolChainStep step = new ToolChainStep();
            step.setAction(plan.path("tool_name").asText());
            step.setServerId(plan.path("server_id").asText());
            step.setReasoning(plan.path("reasoning").asText());
            step.setCritical(plan.path("is_critical").asBoolean(true));

            // Parse parameters
            JsonNode paramsNode = plan.path("parameters");
            @SuppressWarnings("unchecked")
            Map<String, Object> parameters = objectMapper.convertValue(paramsNode, Map.class);
            step.setParameters(parameters);

            logger.info("✅ Planned step: {} on server {}", step.getAction(), step.getServerId());
            return step;

        } catch (Exception e) {
            logger.error("❌ Failed to parse step plan: {}", e.getMessage());
            return createFallbackStepPlan(originalStep, getAllAvailableTools());
        }
    }

    /**
     * Create fallback step plan when AI planning fails
     */
    private ToolChainStep createFallbackStepPlan(String originalStep, Map<String, List<McpTool>> availableTools) {
        logger.info("🔄 Creating fallback step plan for: {}", originalStep);

        // Simple fallback logic
        String serverId = availableTools.keySet().iterator().next();
        String toolName = "help"; // Safe fallback

        // Try to match common operations
        String lower = originalStep.toLowerCase();
        if (lower.contains("list") || lower.contains("show")) {
            toolName = findToolByKeyword(availableTools, "list");
        } else if (lower.contains("find") || lower.contains("search")) {
            toolName = findToolByKeyword(availableTools, "find");
        }

        ToolChainStep step = new ToolChainStep();
        step.setAction(toolName);
        step.setServerId(serverId);
        step.setParameters(Map.of());
        step.setReasoning("Fallback plan due to parsing failure");
        step.setCritical(false);

        return step;
    }

    /**
     * Find tool by keyword matching
     */
    private String findToolByKeyword(Map<String, List<McpTool>> availableTools, String keyword) {
        for (List<McpTool> tools : availableTools.values()) {
            for (McpTool tool : tools) {
                if (tool.name().toLowerCase().contains(keyword) ||
                    tool.description().toLowerCase().contains(keyword)) {
                    return tool.name();
                }
            }
        }
        return "help"; // Fallback
    }

    /**
     * Execute steps with dependency management
     */
    private ChainExecutionResult executeStepsWithDependencies(List<ToolChainStep> steps, ConversationContext context) {
        Map<String, ToolStepResult> completedSteps = new HashMap<>();
        List<String> executionOrder = resolveDependencies(steps);

        logger.info("🔧 Executing {} steps in resolved order", steps.size());

        for (String stepId : executionOrder) {
            ToolChainStep step = findStepById(steps, stepId);
            if (step == null) {
                logger.warn("⚠️ Step {} not found", stepId);
                continue;
            }

            // Check if dependencies are satisfied
            if (!dependenciesSatisfied(step, completedSteps)) {
                logger.error("❌ Dependencies not satisfied for step: {}", stepId);
                ToolStepResult errorResult = new ToolStepResult(step, null, false);
                errorResult.setError("Dependencies not satisfied");
                completedSteps.put(stepId, errorResult);
                continue;
            }

            // Execute the step
            try {
                logger.info("🔧 Executing step {}: {} using tool: {}", step.getStepNumber(), stepId, step.getAction());

                // Inject results from dependent steps into parameters
                Map<String, Object> enhancedParameters = injectDependencyResults(step, completedSteps);

                long startTime = System.currentTimeMillis();
                McpToolResult result = mcpClientService.executeTool(
                    step.getServerId(),
                    step.getAction(),
                    enhancedParameters
                ).block();

                long executionTime = System.currentTimeMillis() - startTime;
                boolean success = result != null && (result.isError() == null || !result.isError());

                ToolStepResult stepResult = new ToolStepResult(step, result, success);
                completedSteps.put(stepId, stepResult);

                if (success) {
                    logger.info("✅ Step {} completed successfully in {}ms", stepId, executionTime);
                } else {
                    logger.warn("❌ Step {} failed", stepId);
                }

            } catch (Exception e) {
                logger.error("❌ Step {} failed: {}", stepId, e.getMessage());

                ToolStepResult errorResult = new ToolStepResult(step, null, false);
                errorResult.setError(e.getMessage());
                completedSteps.put(stepId, errorResult);

                // Decide if we should continue or abort
                if (step.isCritical()) {
                    logger.error("💥 Critical step failed, aborting chain execution");
                    break;
                }
            }
        }

        ChainExecutionResult result = new ChainExecutionResult(steps, completedSteps);
        logger.info("🎯 Chain execution completed: {} successful, {} failed",
            result.getSuccessCount(), result.getFailureCount());

        return result;
    }

    /**
     * Resolve step dependencies and determine execution order
     */
    private List<String> resolveDependencies(List<ToolChainStep> steps) {
        // Simple approach: execute in order (topological sort could be added)
        return steps.stream()
            .map(ToolChainStep::getId)
            .collect(Collectors.toList());
    }

    /**
     * Find step by ID
     */
    private ToolChainStep findStepById(List<ToolChainStep> steps, String stepId) {
        return steps.stream()
            .filter(step -> stepId.equals(step.getId()))
            .findFirst()
            .orElse(null);
    }

    /**
     * Check if step dependencies are satisfied
     */
    private boolean dependenciesSatisfied(ToolChainStep step, Map<String, ToolStepResult> completedSteps) {
        // Simple implementation - assume dependencies are satisfied if previous steps completed
        return true;
    }

    /**
     * Inject dependency results into step parameters
     */
    private Map<String, Object> injectDependencyResults(ToolChainStep step, Map<String, ToolStepResult> completedSteps) {
        Map<String, Object> parameters = new HashMap<>(step.getParameters());

        // Simple approach: pass through original parameters
        // Advanced implementations could inject results from previous steps

        return parameters;
    }
}