# Global MCP Client - Quick Start Guide
## Get Started with Spring AI MCP Integration

**Document Version:** 2.0  
**Date:** 2025-09-02  
**Target Audience:** Developers, New Users  
**Project:** Global MCP Client v1.0.0-SNAPSHOT

---

## 🚀 Get Running in 5 Minutes

This guide will help you get the Global MCP Client running locally with Spring AI MCP integration and a working MCP server in under 5 minutes.

---

## Prerequisites

Before starting, ensure you have:
- **Java 17+** installed (`java -version` to check)
- **Maven 3.6+** installed (`mvn -version` to check)
- **Git** for cloning the repository

---

## Step 1: Clone and Build

```bash
# Clone the repository
git clone <your-repository-url>
cd global-mcp-client

# Build the project
mvn clean install -DskipTests

# Verify build success
ls target/global-mcp-client-*.jar
```

---

## Step 2: Create a Simple Test MCP Server

Create a minimal MCP server for testing:

```python
# Create file: test-mcp-server.py
import json
import sys

def handle_message(message):
    method = message.get("method")
    msg_id = message.get("id")
    
    if method == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": msg_id,
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {
                    "tools": {}
                },
                "serverInfo": {
                    "name": "test-server",
                    "version": "1.0.0"
                }
            }
        }
    
    elif method == "tools/list":
        return {
            "jsonrpc": "2.0",
            "id": msg_id,
            "result": {
                "tools": [
                    {
                        "name": "hello_world",
                        "description": "Says hello to the world",
                        "inputSchema": {
                            "type": "object",
                            "properties": {
                                "name": {
                                    "type": "string",
                                    "description": "Name to greet"
                                }
                            }
                        }
                    }
                ]
            }
        }
    
    elif method == "tools/call":
        tool_name = message["params"]["name"]
        if tool_name == "hello_world":
            name = message["params"]["arguments"].get("name", "World")
            return {
                "jsonrpc": "2.0",
                "id": msg_id,
                "result": {
                    "content": [
                        {
                            "type": "text",
                            "text": f"Hello, {name}!"
                        }
                    ]
                }
            }
    
    # Default error response
    return {
        "jsonrpc": "2.0",
        "id": msg_id,
        "error": {
            "code": -32601,
            "message": "Method not found"
        }
    }

# Main loop
try:
    while True:
        line = sys.stdin.readline()
        if not line:
            break
        
        try:
            message = json.loads(line.strip())
            response = handle_message(message)
            print(json.dumps(response), flush=True)
        except json.JSONDecodeError:
            continue
        except Exception as e:
            error_response = {
                "jsonrpc": "2.0",
                "id": None,
                "error": {
                    "code": -32603,
                    "message": f"Internal error: {str(e)}"
                }
            }
            print(json.dumps(error_response), flush=True)
            
except KeyboardInterrupt:
    pass
```

Make it executable:
```bash
# On Unix/macOS
chmod +x test-mcp-server.py

# Test it works
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' | python test-mcp-server.py
```

---

## Step 3: Configure the Client

Create or update `src/main/resources/application.yml`:

```yaml
server:
  port: 8081  # Application runs on port 8081

spring:
  application:
    name: global-mcp-client

logging:
  level:
    com.deepai.mcpclient: INFO
    root: INFO

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  prometheus:
    metrics:
      export:
        enabled: true

mcp:
  client:
    default-timeout: 5000
    retry:
      max-attempts: 2
      backoff-multiplier: 1.2
  servers:
    test-server:
      type: stdio
      command: "python"
      args: ["test-mcp-server.py"]
      timeout: 8000
      enabled: true
      environment:
        LOG_LEVEL: "INFO"
```

---

## Step 4: Run the Application

```bash
# Run the application
mvn spring-boot:run

# Or run the built JAR
java -jar target/global-mcp-client-*.jar
```

You should see output like:
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::               (v3.3.3)

2025-09-01 19:45:00.123  INFO --- [main] Application: Starting Application...
2025-09-01 19:45:02.456  INFO --- [main] Application: Started Application in 2.333 seconds
```

---

## Step 5: Test the API

### Check Application Health
```bash
curl http://localhost:8081/actuator/health
```

Expected response:
```json
{
  "status": "UP",
  "components": {
    "diskSpace": {"status": "UP"},
    "ping": {"status": "UP"}
  }
}
```

### List Connected Servers
```bash
curl http://localhost:8081/api/servers
```

Expected response:
```json
{
  "data": [
    {
      "id": "test-server",
      "status": "connected",
      "type": "stdio",
      "health": "healthy"
    }
  ]
}
```

### List Available Tools
```bash
curl http://localhost:8081/api/tools
```

Expected response:
```json
{
  "data": [
    {
      "name": "hello_world",
      "description": "Says hello to the world",
      "server_id": "test-server"
    }
  ]
}
```

### Execute a Tool
```bash
curl -X POST http://localhost:8081/api/servers/test-server/tools/hello_world \
  -H "Content-Type: application/json" \
  -d '{"parameters": {"arguments": {"name": "Alice"}}}'
```

Expected response:
```json
{
  "data": {
    "content": [
      {
        "type": "text", 
        "text": "Hello, Alice!"
      }
    ]
  }
}
```

---

## 🎉 Success!

You now have:
- ✅ A running Global MCP Client
- ✅ A connected test MCP server
- ✅ Working API endpoints
- ✅ A tool you can execute

---

## Next Steps

### Explore the API
- Visit `http://localhost:8081/swagger-ui.html` for interactive API documentation
- Check `http://localhost:8081/actuator/health` for health monitoring
- View metrics at `http://localhost:8081/actuator/metrics`

### Add Real MCP Servers
Replace the test server with real MCP servers:

```yaml
mcp:
  servers:
    mongodb-server:
      type: stdio
      command: "java"
      args: ["-jar", "path/to/mongo-mcp-server.jar"]
      timeout: 10000
      enabled: true
      environment:
        MONGODB_URI: "mongodb://localhost:27017/mydb"
    
    filesystem-server:
      type: stdio  
      command: "node"
      args: ["path/to/filesystem-server.js"]
      timeout: 8000
      enabled: true
      environment:
        ROOT_PATH: "/home/user/documents"
```

### Production Deployment
- Review [Deployment Guide](DEPLOYMENT_GUIDE.md) for production setup
- See [Configuration Examples](CONFIGURATION_EXAMPLES.md) for advanced configurations
- Check [Security Guide](DEPLOYMENT_GUIDE.md#security-setup) for production security

---

## Troubleshooting Quick Fixes

### Application Won't Start
```bash
# Check Java version
java -version  # Should be 17+

# Check port availability
netstat -an | findstr :8081  # Windows
lsof -i :8081                # Unix/macOS

# Check logs
tail -f logs/application.log
```

### Test Server Not Connecting
```bash
# Test server manually
echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' | python test-mcp-server.py

# Check Python is available
python --version
which python
```

### API Calls Failing
```bash
# Check application is running
curl http://localhost:8081/actuator/health

# Check server status
curl http://localhost:8081/api/servers

# Enable debug logging (add to application.yml):
# logging:
#   level:
#     com.deepai.mcpclient: DEBUG
```

---

## 📚 Additional Resources

- **[README](../README.md)** - Project overview and architecture
- **[FAQ](FAQ.md)** - Frequently asked questions  
- **[API Documentation](API_DOCUMENTATION.md)** - Complete API reference
- **[Configuration Guide](CONFIGURATION_EXAMPLES.md)** - Advanced configuration patterns
- **[Deployment Guide](DEPLOYMENT_GUIDE.md)** - Production deployment instructions

---

**Happy coding! 🎯**
