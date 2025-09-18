package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.ConversationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdvancedContextManager {

    private static final Logger logger = LoggerFactory.getLogger(AdvancedContextManager.class);

    @Autowired
    private GeminiModelSelector modelSelector;

    public ConversationContext optimizeContext(ConversationContext context, String currentModel) {
        int maxContextWindow = modelSelector.getContextWindowForModel(currentModel);
        int reservedTokens = 8000; // Reserve tokens for response
        int availableTokens = maxContextWindow - reservedTokens;

        // Estimate tokens (rough approximation: 1 token ≈ 4 characters)
        String fullContext = context.getContextSummary();
        int estimatedTokens = fullContext.length() / 4;

        if (estimatedTokens <= availableTokens) {
            logger.debug("Context fits within limits: {} tokens", estimatedTokens);
            return context; // No optimization needed
        }

        logger.info("🔄 Optimizing context: {} tokens -> target: {} tokens", estimatedTokens, availableTokens);

        // Implement sliding window with importance preservation
        return implementSlidingWindow(context, availableTokens);
    }

    private ConversationContext implementSlidingWindow(ConversationContext context, int targetTokens) {
        List<ConversationContext.ChatMessage> messages = context.getMessages();

        // Always keep the last few messages (most recent context)
        int messagesToKeep = Math.min(10, messages.size());
        List<ConversationContext.ChatMessage> recentMessages = messages.subList(
            Math.max(0, messages.size() - messagesToKeep),
            messages.size()
        );

        // Create summary of older messages if needed
        String summary = "";
        if (messages.size() > messagesToKeep) {
            List<ConversationContext.ChatMessage> olderMessages = messages.subList(0, messages.size() - messagesToKeep);
            summary = "Previous conversation summary: " +
                     olderMessages.stream()
                                 .limit(5) // Summarize first 5 older messages
                                 .map(msg -> msg.role() + ": " + msg.content())
                                 .collect(Collectors.joining("; "));
        }

        // Create optimized context
        ConversationContext optimizedContext = new ConversationContext(context.getContextId());
        if (!summary.isEmpty()) {
            optimizedContext.addAssistantMessage(summary);
        }

        // Add recent messages
        for (ConversationContext.ChatMessage message : recentMessages) {
            if ("user".equals(message.role())) {
                optimizedContext.addUserMessage(message.content());
            } else {
                optimizedContext.addAssistantMessage(message.content());
            }
        }

        logger.info("✅ Context optimized: {} messages retained + summary", recentMessages.size());
        return optimizedContext;
    }

    public String buildOptimizedPrompt(String userMessage, ConversationContext context, String model) {
        ConversationContext optimizedContext = optimizeContext(context, model);

        return String.format(
            "Context: %s\n\nUser: %s\n\nPlease respond helpfully based on the conversation context.",
            optimizedContext.getContextSummary(),
            userMessage
        );
    }
}