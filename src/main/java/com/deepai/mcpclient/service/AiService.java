package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.ChatRequest;
import com.deepai.mcpclient.model.ChatResponse;
import com.deepai.mcpclient.model.ConversationContext;
import reactor.core.publisher.Mono;

public interface AiService {
    Mono<ChatResponse> processChat(ChatRequest request);
    Mono<String> ask(String question);
    ConversationContext getContext(String contextId);
    int getActiveContextsCount();
}