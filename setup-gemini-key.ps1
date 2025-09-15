# Gemini API Key Setup Script
# This script helps you set up the Gemini API key securely

param(
    [Parameter(Mandatory=$true)]
    [string]$ApiKey
)

Write-Host "Setting up Gemini API Key..." -ForegroundColor Green

# Set the environment variable for the current session
$env:GEMINI_API_KEY = $ApiKey

# Verify the key is set
if ($env:GEMINI_API_KEY) {
    Write-Host "✅ Gemini API key set successfully for current session" -ForegroundColor Green
    Write-Host "Key length: $($env:GEMINI_API_KEY.Length) characters" -ForegroundColor Cyan
    
    # Show masked version
    $maskedKey = $env:GEMINI_API_KEY.Substring(0, 8) + "..." + $env:GEMINI_API_KEY.Substring($env:GEMINI_API_KEY.Length - 8)
    Write-Host "Masked key: $maskedKey" -ForegroundColor Cyan
} else {
    Write-Host "❌ Failed to set Gemini API key" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "The API key is now ready for use with the MCP client." -ForegroundColor Yellow
Write-Host "Note: This key is only set for the current PowerShell session." -ForegroundColor Yellow
