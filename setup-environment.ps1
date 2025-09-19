# PowerShell Environment Setup Script for Global MCP Client
# Quick setup for Windows users

param(
    [switch]$Force = $false
)

Write-Host "🚀 Global MCP Client - Environment Setup (PowerShell)" -ForegroundColor Cyan
Write-Host "====================================================" -ForegroundColor Cyan
Write-Host ""

# Check if .env already exists
if ((Test-Path ".env") -and -not $Force) {
    Write-Host "⚠️  .env file already exists!" -ForegroundColor Yellow
    $overwrite = Read-Host "Do you want to overwrite it? (y/N)"
    if ($overwrite -notmatch '^[Yy]$') {
        Write-Host "Setup cancelled. Your existing .env file is preserved." -ForegroundColor Gray
        exit 0
    }
    Write-Host ""
}

Write-Host "Let's set up your API keys and configuration..." -ForegroundColor Green
Write-Host ""

# Function to prompt with default
function Prompt-WithDefault {
    param(
        [string]$Prompt,
        [string]$Default = ""
    )
    
    if ($Default) {
        $input = Read-Host "$Prompt (default: $Default)"
        if ([string]::IsNullOrEmpty($input)) {
            return $Default
        }
        return $input
    } else {
        return Read-Host $Prompt
    }
}

# === AI API KEYS ===
Write-Host "🤖 AI API Keys Configuration" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Gray

$OPENAI_API_KEY = Prompt-WithDefault "Enter your OpenAI API key (sk-...)"
$GEMINI_API_KEY = Prompt-WithDefault "Enter your Google Gemini API key"
$ANTHROPIC_API_KEY = Prompt-WithDefault "Enter your Anthropic Claude API key (optional)" ""

Write-Host ""

# === DATABASE CONFIGURATION ===
Write-Host "🗄️ Database Configuration" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Gray

Write-Host "Oracle Database (for Oracle MCP Server):" -ForegroundColor Yellow
$ORACLE_HOST = Prompt-WithDefault "Oracle host" "localhost"
$ORACLE_PORT = Prompt-WithDefault "Oracle port" "1521"
$ORACLE_SID = Prompt-WithDefault "Oracle SID" "XE"
$ORACLE_USER = Prompt-WithDefault "Oracle username" "C##loan_schema"
$ORACLE_PASSWORD = Prompt-WithDefault "Oracle password" "loan_data"

Write-Host ""
Write-Host "MongoDB (optional):" -ForegroundColor Yellow
$MONGODB_URI = Prompt-WithDefault "MongoDB URI" "mongodb://localhost:27017/mcpserver"

Write-Host ""

# === APPLICATION SETTINGS ===
Write-Host "⚙️ Application Settings" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Gray

$SERVER_PORT = Prompt-WithDefault "Server port" "8081"
$SPRING_PROFILE = Prompt-WithDefault "Spring profile (dev/prod/cli/server)" "dev"
$OLLAMA_URL = Prompt-WithDefault "Ollama base URL (for local AI)" "http://localhost:11434"

Write-Host ""

# === GENERATE .env FILE ===
Write-Host "📝 Generating .env file..." -ForegroundColor Green

$envContent = @"
# Global MCP Client Environment Configuration
# Generated on $(Get-Date)

# === PRIMARY AI PROVIDERS ===
OPENAI_API_KEY=$OPENAI_API_KEY
GEMINI_API_KEY=$GEMINI_API_KEY
ANTHROPIC_API_KEY=$ANTHROPIC_API_KEY

# === DATABASE CONNECTIONS ===
ORACLE_DB_URL=jdbc:oracle:thin:@${ORACLE_HOST}:${ORACLE_PORT}:${ORACLE_SID}
ORACLE_DB_USER=$ORACLE_USER
ORACLE_DB_PASSWORD=$ORACLE_PASSWORD
ORACLE_HOST=$ORACLE_HOST
ORACLE_PORT=$ORACLE_PORT
ORACLE_SID=$ORACLE_SID

MONGODB_URI=$MONGODB_URI
MONGODB_DB_NAME=mcpserver

# === APPLICATION CONFIGURATION ===
SERVER_PORT=$SERVER_PORT
SPRING_PROFILES_ACTIVE=$SPRING_PROFILE
OLLAMA_BASE_URL=$OLLAMA_URL

# === MCP SERVER CONFIGURATION ===
MCP_TOOLS_EXPOSURE=all
ENTERPRISE_ENABLED=true
LOGGING_LEVEL_ROOT=WARN
LOGGING_LEVEL_DEEPAI=DEBUG

# === SECURITY & NETWORKING ===
CORS_ALLOWED_ORIGINS=*
CORS_ALLOWED_METHODS=*
CORS_ALLOWED_HEADERS=*
"@

$envContent | Out-File -FilePath ".env" -Encoding UTF8

Write-Host "✅ .env file created successfully!" -ForegroundColor Green
Write-Host ""

# === TEST API CONNECTIONS ===
Write-Host "🧪 Testing API Connections" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Gray

# Test OpenAI
if ($OPENAI_API_KEY -and $OPENAI_API_KEY -ne "your-openai-api-key-here") {
    Write-Host -NoNewline "Testing OpenAI API... "
    $env:OPENAI_API_KEY = $OPENAI_API_KEY
    
    try {
        $result = & ".\test-openai-api.ps1" -ApiKey $OPENAI_API_KEY 2>$null
        if ($LASTEXITCODE -eq 0) {
            Write-Host "✅ Working" -ForegroundColor Green
        } else {
            Write-Host "❌ Failed" -ForegroundColor Red
            Write-Host "Run '.\test-openai-api.ps1' manually to see details" -ForegroundColor Gray
        }
    } catch {
        Write-Host "❌ Failed" -ForegroundColor Red
        Write-Host "Run '.\test-openai-api.ps1' manually to see details" -ForegroundColor Gray
    }
} else {
    Write-Host "⏭️  Skipping OpenAI test (no API key)" -ForegroundColor Yellow
}

# Test Gemini
if ($GEMINI_API_KEY -and $GEMINI_API_KEY -ne "your-gemini-api-key-here") {
    Write-Host -NoNewline "Testing Gemini API... "
    
    try {
        $headers = @{ "Content-Type" = "application/json" }
        $body = @{ contents = @(@{ parts = @(@{ text = "Hello" }) }) } | ConvertTo-Json -Depth 10
        $response = Invoke-RestMethod -Uri "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent?key=$GEMINI_API_KEY" -Method Post -Headers $headers -Body $body -ErrorAction Stop
        
        Write-Host "✅ Working" -ForegroundColor Green
    } catch {
        Write-Host "❌ Failed" -ForegroundColor Red
    }
} else {
    Write-Host "⏭️  Skipping Gemini test (no API key)" -ForegroundColor Yellow
}

Write-Host ""

# === SET ENVIRONMENT VARIABLES FOR CURRENT SESSION ===
Write-Host "🔧 Setting environment variables for current session..." -ForegroundColor Green

# Load .env file and set environment variables
Get-Content ".env" | ForEach-Object {
    if ($_ -match '^([^#][^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
    }
}

Write-Host ""

# === NEXT STEPS ===
Write-Host "🎉 Setup Complete! Next Steps:" -ForegroundColor Green
Write-Host "=========================================="
Write-Host ""
Write-Host "1. Build the application:" -ForegroundColor Blue
Write-Host "   mvn clean package" -ForegroundColor White
Write-Host ""
Write-Host "2. Start in server mode:" -ForegroundColor Blue
Write-Host "   mvn spring-boot:run -Dspring-boot.run.profiles=server" -ForegroundColor White
Write-Host ""
Write-Host "3. Or start CLI mode:" -ForegroundColor Blue
Write-Host "   java -jar target/global-mcp-client-1.0.0-SNAPSHOT-cli.jar" -ForegroundColor White
Write-Host ""
Write-Host "4. Test API endpoints:" -ForegroundColor Blue
Write-Host "   curl http://localhost:$SERVER_PORT/api/servers" -ForegroundColor White
Write-Host "   curl http://localhost:$SERVER_PORT/actuator/health" -ForegroundColor White
Write-Host ""
Write-Host "5. Access Swagger UI:" -ForegroundColor Blue
Write-Host "   http://localhost:$SERVER_PORT/swagger-ui.html" -ForegroundColor White
Write-Host ""
Write-Host "6. Test AI functionality:" -ForegroundColor Blue
Write-Host "   Invoke-RestMethod -Uri 'http://localhost:$SERVER_PORT/api/ai/ask' ``" -ForegroundColor White
Write-Host "     -Method Post -ContentType 'application/json' ``" -ForegroundColor White
Write-Host "     -Body '{\"question\": \"Hello, how are you?\"}'" -ForegroundColor White
Write-Host ""
Write-Host "💡 Pro Tips:" -ForegroundColor Yellow
Write-Host "   - Environment variables are set for this session" -ForegroundColor Gray
Write-Host "   - Restart PowerShell to reload .env file automatically" -ForegroundColor Gray
Write-Host "   - Use '-Force' parameter to overwrite existing .env" -ForegroundColor Gray
Write-Host ""
Write-Host "Happy coding! 🚀" -ForegroundColor Green