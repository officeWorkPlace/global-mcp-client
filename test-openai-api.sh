#!/bin/bash

# OpenAI API Key Test Script (Bash/Linux/WSL)
# This script tests if your OpenAI API key is working correctly

echo "🔑 OpenAI API Key Test Script"
echo "============================="
echo ""

# Check if API key is provided
if [ -z "$OPENAI_API_KEY" ]; then
    echo "❌ No API key provided!"
    echo "Please set the OPENAI_API_KEY environment variable:"
    echo "  export OPENAI_API_KEY='your-api-key-here'"
    echo ""
    exit 1
fi

# Mask API key for display
MASKED_KEY="${OPENAI_API_KEY:0:7}...${OPENAI_API_KEY: -4}"
echo "🔍 Testing API Key: $MASKED_KEY"
echo ""

# Test API endpoint
echo "🌐 Connecting to OpenAI API..."

RESPONSE=$(curl -s -w "\n%{http_code}" \
  https://api.openai.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $OPENAI_API_KEY" \
  -d '{
    "model": "gpt-3.5-turbo",
    "messages": [
      {
        "role": "user",
        "content": "Hello! This is a test message. Please respond with just \"API test successful\""
      }
    ],
    "max_tokens": 50,
    "temperature": 0.1
  }')

# Extract HTTP status code and response body
HTTP_CODE=$(echo "$RESPONSE" | tail -n1)
RESPONSE_BODY=$(echo "$RESPONSE" | head -n -1)

if [ "$HTTP_CODE" -eq 200 ]; then
    echo "✅ API Connection Successful!"
    echo ""
    echo "📊 Response Details:"
    
    # Extract model and usage info using basic text processing
    MODEL=$(echo "$RESPONSE_BODY" | grep -o '"model":"[^"]*"' | cut -d'"' -f4)
    TOTAL_TOKENS=$(echo "$RESPONSE_BODY" | grep -o '"total_tokens":[0-9]*' | cut -d':' -f2)
    CONTENT=$(echo "$RESPONSE_BODY" | grep -o '"content":"[^"]*"' | cut -d'"' -f4)
    
    echo "  Model: $MODEL"
    echo "  Usage: $TOTAL_TOKENS tokens"
    echo "  Response: $CONTENT"
    echo ""
    echo "🎉 Your OpenAI API key is working correctly!"
    echo "🚀 Ready to use OpenAI in your applications!"
else
    echo "❌ API Test Failed!"
    echo ""
    echo "📋 Error Details:"
    echo "  HTTP Status Code: $HTTP_CODE"
    
    case $HTTP_CODE in
        401)
            echo "  💡 Issue: Invalid API key"
            echo "     Please check your OpenAI API key is correct"
            ;;
        403)
            echo "  💡 Issue: Access forbidden"
            echo "     Your API key may not have the required permissions"
            ;;
        429)
            echo "  💡 Issue: Rate limit exceeded"
            echo "     You've hit your API rate limit. Try again later"
            ;;
        500)
            echo "  💡 Issue: OpenAI server error"
            echo "     OpenAI is experiencing issues. Try again later"
            ;;
        *)
            echo "  💡 Issue: Unknown error"
            echo "     Check OpenAI status at https://status.openai.com/"
            ;;
    esac
    
    echo ""
    echo "Response: $RESPONSE_BODY"
    exit 1
fi
