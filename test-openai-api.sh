#!/bin/bash

# OpenAI API Key Test Script (Enhanced Version)
# Tests OpenAI API key functionality with better error handling

echo "🔑 OpenAI API Key Test Script (Enhanced)"
echo "========================================"
echo ""

# Check if API key is provided
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ No API key provided!"
    echo ""
    echo "📝 Setup Instructions:"
    echo "   export OPENAI_API_KEY='your-api-key-here'"
    echo ""
    echo "💡 Or create a .env file with:"
    echo "   OPENAI_API_KEY=your-api-key-here"
    echo ""
    exit 1
fi

# Validate API key format
if [[ ! "$OPENAI_API_KEY" =~ ^sk-[a-zA-Z0-9]{20,}$ ]]; then
    echo "⚠️  Warning: API key format looks unusual"
    echo "   Expected format: sk-xxxxxxxxx..."
    echo ""
fi

# Mask API key for display
MASKED_KEY="${OPENAI_API_KEY:0:7}...${OPENAI_API_KEY: -4}"
echo "🔍 Testing API Key: $MASKED_KEY"
echo ""

# Check if jq is available for better JSON parsing
if command -v jq >/dev/null 2>&1; then
    USE_JQ=true
    echo "📊 JSON parsing: Using jq (better)"
else
    USE_JQ=false
    echo "📊 JSON parsing: Using basic parsing (install jq for better results)"
fi
echo ""

# Test API endpoint with more reliable model
echo "🌐 Connecting to OpenAI API..."

RESPONSE=$(curl -s -w "\n%{http_code}" \
    https://api.openai.com/v1/chat/completions \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $OPENAI_API_KEY" \
    -d '{
        "model": "gpt-4o-mini",
        "messages": [
            {
                "role": "user",
                "content": "Hello! This is an API test. Please respond with exactly: API test successful"
            }
        ],
        "max_tokens": 20,
        "temperature": 0
    }' 2>/dev/null)

# Extract HTTP status code and response body
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)

echo "📡 HTTP Status Code: $HTTP_CODE"
echo ""

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ API Connection Successful!"
    echo ""
    echo "📊 Response Details:"
    
    if [ "$USE_JQ" = true ]; then
        # Better JSON parsing with jq
        MODEL=$(echo "$RESPONSE_BODY" | jq -r '.model // "unknown"')
        TOTAL_TOKENS=$(echo "$RESPONSE_BODY" | jq -r '.usage.total_tokens // 0')
        PROMPT_TOKENS=$(echo "$RESPONSE_BODY" | jq -r '.usage.prompt_tokens // 0')
        COMPLETION_TOKENS=$(echo "$RESPONSE_BODY" | jq -r '.usage.completion_tokens // 0')
        CONTENT=$(echo "$RESPONSE_BODY" | jq -r '.choices[0].message.content // "No content"')
        FINISH_REASON=$(echo "$RESPONSE_BODY" | jq -r '.choices[0].finish_reason // "unknown"')
        
        echo "   🤖 Model: $MODEL"
        echo "   📝 Response: $CONTENT"
        echo "   🔢 Token Usage:"
        echo "      - Total: $TOTAL_TOKENS tokens"
        echo "      - Prompt: $PROMPT_TOKENS tokens"
        echo "      - Completion: $COMPLETION_TOKENS tokens"
        echo "   ✅ Finish Reason: $FINISH_REASON"
    else
        # Basic parsing (fallback)
        MODEL=$(echo "$RESPONSE_BODY" | grep -o '"model":"[^"]*"' | cut -d'"' -f4)
        TOTAL_TOKENS=$(echo "$RESPONSE_BODY" | grep -o '"total_tokens":[0-9]*' | cut -d':' -f2)
        CONTENT=$(echo "$RESPONSE_BODY" | sed 's/.*"content":"\([^"]*\)".*/\1/')
        
        echo "   🤖 Model: ${MODEL:-unknown}"
        echo "   📝 Response: ${CONTENT:-No content extracted}"
        echo "   🔢 Total Tokens: ${TOTAL_TOKENS:-unknown}"
    fi
    
    echo ""
    echo "🎉 Your OpenAI API key is working correctly!"
    echo "🚀 Ready to use OpenAI in your Spring AI application!"
    echo ""
    echo "🔗 Next Steps:"
    echo "   1. Add to application.properties:"
    echo "      spring.ai.openai.api-key=\${OPENAI_API_KEY}"
    echo "   2. Test your Spring Boot application"
    
else
    echo "❌ API Test Failed!"
    echo ""
    echo "📋 Error Details:"
    echo "   HTTP Status Code: $HTTP_CODE"
    echo ""
    
    case $HTTP_CODE in
        401)
            echo "🚫 Authentication Error (401)"
            echo "   💡 Issue: Invalid API key"
            echo "   🔧 Solution: Check your OpenAI API key is correct"
            echo "   🔗 Get API key: https://platform.openai.com/api-keys"
            ;;
        403)
            echo "🚫 Access Forbidden (403)"
            echo "   💡 Issue: API key lacks required permissions"
            echo "   🔧 Solution: Check your OpenAI account has API access"
            ;;
        429)
            echo "🚫 Rate Limited (429)"
            echo "   💡 Issue: Too many requests or quota exceeded"
            echo "   🔧 Solution: Wait and try again, or check billing"
            ;;
        500|502|503)
            echo "🚫 Server Error ($HTTP_CODE)"
            echo "   💡 Issue: OpenAI is experiencing issues"
            echo "   🔧 Solution: Try again later"
            echo "   🔗 Status: https://status.openai.com/"
            ;;
        000)
            echo "🚫 Connection Error"
            echo "   💡 Issue: Network connectivity problem"
            echo "   🔧 Solution: Check your internet connection"
            ;;
        *)
            echo "🚫 Unknown Error ($HTTP_CODE)"
            echo "   💡 Issue: Unexpected response"
            echo "   🔗 Check: https://status.openai.com/"
            ;;
    esac
    
    echo ""
    echo "📄 Raw Response:"
    echo "$RESPONSE_BODY" | head -c 500
    if [ ${#RESPONSE_BODY} -gt 500 ]; then
        echo "... (truncated)"
    fi
    echo ""
    
    exit 1
fi