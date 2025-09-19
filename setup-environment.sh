#!/bin/bash

# Quick Environment Setup Script for Global MCP Client
# This script helps you set up your development environment quickly

echo "🚀 Global MCP Client - Environment Setup"
echo "========================================"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to prompt for input with default
prompt_with_default() {
    local prompt="$1"
    local default="$2"
    local result
    
    if [ -n "$default" ]; then
        echo -n -e "${BLUE}$prompt${NC} (default: ${YELLOW}$default${NC}): "
    else
        echo -n -e "${BLUE}$prompt${NC}: "
    fi
    
    read result
    if [ -z "$result" ] && [ -n "$default" ]; then
        result="$default"
    fi
    echo "$result"
}

# Check if .env already exists
if [ -f .env ]; then
    echo -e "${YELLOW}⚠️  .env file already exists!${NC}"
    echo -n -e "${BLUE}Do you want to overwrite it? (y/N): ${NC}"
    read overwrite
    if [[ ! "$overwrite" =~ ^[Yy]$ ]]; then
        echo "Setup cancelled. Your existing .env file is preserved."
        exit 0
    fi
    echo ""
fi

echo "Let's set up your API keys and configuration..."
echo ""

# === AI API KEYS ===
echo -e "${GREEN}🤖 AI API Keys Configuration${NC}"
echo "----------------------------------------"

OPENAI_API_KEY=$(prompt_with_default "Enter your OpenAI API key (sk-...)")
GEMINI_API_KEY=$(prompt_with_default "Enter your Google Gemini API key")
ANTHROPIC_API_KEY=$(prompt_with_default "Enter your Anthropic Claude API key (optional)" "")

echo ""

# === DATABASE CONFIGURATION ===
echo -e "${GREEN}🗄️ Database Configuration${NC}"
echo "----------------------------------------"

echo "Oracle Database (for Oracle MCP Server):"
ORACLE_HOST=$(prompt_with_default "Oracle host" "localhost")
ORACLE_PORT=$(prompt_with_default "Oracle port" "1521")
ORACLE_SID=$(prompt_with_default "Oracle SID" "XE")
ORACLE_USER=$(prompt_with_default "Oracle username" "C##loan_schema")
ORACLE_PASSWORD=$(prompt_with_default "Oracle password" "loan_data")

echo ""
echo "MongoDB (optional):"
MONGODB_URI=$(prompt_with_default "MongoDB URI" "mongodb://localhost:27017/mcpserver")

echo ""

# === APPLICATION SETTINGS ===
echo -e "${GREEN}⚙️ Application Settings${NC}"
echo "----------------------------------------"

SERVER_PORT=$(prompt_with_default "Server port" "8081")
SPRING_PROFILE=$(prompt_with_default "Spring profile (dev/prod/cli/server)" "dev")
OLLAMA_URL=$(prompt_with_default "Ollama base URL (for local AI)" "http://localhost:11434")

echo ""

# === GENERATE .env FILE ===
echo -e "${GREEN}📝 Generating .env file...${NC}"

cat > .env << EOF
# Global MCP Client Environment Configuration
# Generated on $(date)

# === PRIMARY AI PROVIDERS ===
OPENAI_API_KEY=${OPENAI_API_KEY}
GEMINI_API_KEY=${GEMINI_API_KEY}
ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}

# === DATABASE CONNECTIONS ===
ORACLE_DB_URL=jdbc:oracle:thin:@${ORACLE_HOST}:${ORACLE_PORT}:${ORACLE_SID}
ORACLE_DB_USER=${ORACLE_USER}
ORACLE_DB_PASSWORD=${ORACLE_PASSWORD}
ORACLE_HOST=${ORACLE_HOST}
ORACLE_PORT=${ORACLE_PORT}
ORACLE_SID=${ORACLE_SID}

MONGODB_URI=${MONGODB_URI}
MONGODB_DB_NAME=mcpserver

# === APPLICATION CONFIGURATION ===
SERVER_PORT=${SERVER_PORT}
SPRING_PROFILES_ACTIVE=${SPRING_PROFILE}
OLLAMA_BASE_URL=${OLLAMA_URL}

# === MCP SERVER CONFIGURATION ===
MCP_TOOLS_EXPOSURE=all
ENTERPRISE_ENABLED=true
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_DEEPAI=DEBUG

# === SECURITY & NETWORKING ===
CORS_ALLOWED_ORIGINS=*
CORS_ALLOWED_METHODS=*
CORS_ALLOWED_HEADERS=*
EOF

echo -e "${GREEN}✅ .env file created successfully!${NC}"
echo ""

# === TEST API CONNECTIONS ===
echo -e "${GREEN}🧪 Testing API Connections${NC}"
echo "----------------------------------------"

# Test OpenAI
if [ -n "$OPENAI_API_KEY" ] && [ "$OPENAI_API_KEY" != "your-openai-api-key-here" ]; then
    echo -n "Testing OpenAI API... "
    export OPENAI_API_KEY="$OPENAI_API_KEY"
    if ./test-openai-api.sh > /dev/null 2>&1; then
        echo -e "${GREEN}✅ Working${NC}"
    else
        echo -e "${RED}❌ Failed${NC}"
        echo "Run './test-openai-api.sh' manually to see details"
    fi
else
    echo -e "${YELLOW}⏭️  Skipping OpenAI test (no API key)${NC}"
fi

# Test Gemini
if [ -n "$GEMINI_API_KEY" ] && [ "$GEMINI_API_KEY" != "your-gemini-api-key-here" ]; then
    echo -n "Testing Gemini API... "
    GEMINI_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null \
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=${GEMINI_API_KEY}" \
        -H "Content-Type: application/json" \
        -d '{"contents":[{"parts":[{"text":"Hello"}]}]}' 2>/dev/null)
    
    if [ "$GEMINI_RESPONSE" = "200" ]; then
        echo -e "${GREEN}✅ Working${NC}"
    else
        echo -e "${RED}❌ Failed (Status: $GEMINI_RESPONSE)${NC}"
    fi
else
    echo -e "${YELLOW}⏭️  Skipping Gemini test (no API key)${NC}"
fi

echo ""

# === NEXT STEPS ===
echo -e "${GREEN}🎉 Setup Complete! Next Steps:${NC}"
echo "=========================================="
echo ""
echo -e "${BLUE}1. Load environment variables:${NC}"
echo "   source .env"
echo ""
echo -e "${BLUE}2. Build the application:${NC}"
echo "   mvn clean package"
echo ""
echo -e "${BLUE}3. Start in server mode:${NC}"
echo "   mvn spring-boot:run -Dspring-boot.run.profiles=server"
echo ""
echo -e "${BLUE}4. Or start CLI mode:${NC}"
echo "   java -jar target/global-mcp-client-1.0.0-SNAPSHOT-cli.jar"
echo ""
echo -e "${BLUE}5. Test API endpoints:${NC}"
echo "   curl http://localhost:${SERVER_PORT}/api/servers"
echo "   curl http://localhost:${SERVER_PORT}/actuator/health"
echo ""
echo -e "${BLUE}6. Access Swagger UI:${NC}"
echo "   http://localhost:${SERVER_PORT}/swagger-ui.html"
echo ""
echo -e "${BLUE}7. Test AI functionality:${NC}"
echo "   curl -X POST http://localhost:${SERVER_PORT}/api/ai/ask \\"
echo "        -H \"Content-Type: application/json\" \\"
echo "        -d '{\"question\": \"Hello, how are you?\"}'\"
echo ""
echo -e "${YELLOW}💡 Pro Tips:${NC}"
echo "   - Use 'mvn spring-boot:run -Dspring-boot.run.profiles=cli' for CLI mode"
echo "   - Check logs with 'tail -f logs/application.log'"
echo "   - Monitor with 'curl http://localhost:${SERVER_PORT}/actuator/metrics'"
echo ""
echo -e "${GREEN}Happy coding! 🚀${NC}"