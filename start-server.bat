@echo off
rem Global MCP Client - Server Mode Startup Script

echo Starting Global MCP Client in Server Mode...
echo Server will be available at: http://localhost:8082

cd /d "%~dp0"

java -cp "target/classes;target/lib/*" ^
     -Dspring.profiles.active=server ^
     -Dserver.port=8082 ^
     com.deepai.mcpclient.server.McpServerApplication

pause
