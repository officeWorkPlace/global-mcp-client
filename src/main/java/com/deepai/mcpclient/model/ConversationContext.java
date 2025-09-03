package com.deepai.mcpclient.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manages conversation context for AI chat sessions
 * Keeps track of conversation history and context
 */
public class ConversationContext {
    
    private final String contextId;
    private final List<ChatMessage> messages;
    private final LocalDateTime createdAt;
    private LocalDateTime lastUsed;
    private String preferredServerId;
    
    public ConversationContext() {
        this.contextId = UUID.randomUUID().toString();
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
    }
    
    public ConversationContext(String contextId) {
        this.contextId = contextId;
        this.messages = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastUsed = LocalDateTime.now();
    }
    
    /**
     * Add a user message to the conversation
     */
    public void addUserMessage(String message) {
        messages.add(new ChatMessage("user", message, LocalDateTime.now()));
        updateLastUsed();
    }
    
    /**
     * Add an assistant response to the conversation
     */
    public void addAssistantMessage(String message) {
        messages.add(new ChatMessage("assistant", message, LocalDateTime.now()));
        updateLastUsed();
    }
    
    /**
     * Get conversation summary for context
     */
    public String getContextSummary() {
        if (messages.isEmpty()) {
            return "New conversation";
        }
        
        StringBuilder summary = new StringBuilder();
        summary.append("Previous conversation context:\n");
        
        // Include last few messages for context
        int startIndex = Math.max(0, messages.size() - 6);
        for (int i = startIndex; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            summary.append(msg.role()).append(": ").append(msg.content()).append("\n");
        }
        
        return summary.toString();
    }
    
    /**
     * Check if context is expired (older than 1 hour)
     */
    public boolean isExpired() {
        return lastUsed.isBefore(LocalDateTime.now().minusHours(1));
    }
    
    /**
     * Update last used timestamp
     */
    private void updateLastUsed() {
        this.lastUsed = LocalDateTime.now();
    }
    
    // Getters
    public String getContextId() {
        return contextId;
    }
    
    public List<ChatMessage> getMessages() {
        return new ArrayList<>(messages);
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getLastUsed() {
        return lastUsed;
    }
    
    public String getPreferredServerId() {
        return preferredServerId;
    }
    
    public void setPreferredServerId(String preferredServerId) {
        this.preferredServerId = preferredServerId;
    }
    
    /**
     * Individual chat message in conversation
     */
    public record ChatMessage(
        String role,
        String content,
        LocalDateTime timestamp
    ) {}
}
