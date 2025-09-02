# Global MCP Client - Frequently Asked Questions
## Spring AI MCP Integration Q&A

**Document Version:** 2.0  
**Date:** 2025-09-02  
**Classification:** User Guide  
**Project:** Global MCP Client v1.0.0-SNAPSHOT

---

## General Questions

### Q: What is the Global MCP Client?
**A:** The Global MCP Client is a Spring Boot application that acts as a centralized gateway for multiple MCP (Model Context Protocol) servers. Built with Spring AI MCP Client integration, it provides a unified REST API interface running on port 8082 for client applications to interact with various MCP servers without implementing the MCP protocol directly.

### Q: What's the difference between this client and an MCP server?
**A:** 
- **MCP Server**: Implements specific functionality (e.g., database operations, file management, AI services) and exposes it via MCP protocol
- **Global MCP Client**: Acts as a gateway/router that connects TO multiple MCP servers and provides a unified API for client applications

### Q: Can I use this with any MCP server?
**A:** Yes, as long as the MCP server:
- Implements MCP protocol version `2024-11-05` or compatible
- Supports either `stdio` or `HTTP` transport
- Uses JSON-RPC 2.0 message format
- Implements required methods: `initialize`, `tools/list`, `tools/call`

---

## Configuration Questions

### Q: How do I add a new MCP server?
**A:** Add server configuration to your `application.yml`:

```yaml
mcp:
  servers:
    my-new-server:
      type: stdio  # or http
      command: "python"
      args: ["-m", "my_mcp_server"]
      timeout: 10000
      enabled: true
      environment:
        API_KEY: "${MY_API_KEY}"
```

Then restart the application.

### Q: Can I add servers without restarting the application?
**A:** Currently, server configuration requires an application restart. Dynamic server management is planned for future releases.

### Q: How do I secure environment variables in configuration?
**A:** Use environment variable references:

```yaml
environment:
  API_KEY: "${SECURE_API_KEY}"  # ✅ Good
  # API_KEY: "hardcoded-key"    # ❌ Bad
```

Set the actual values in your system environment or `.env` files.

### Q: What's the difference between `stdio` and `http` server types?
**A:** 
- **stdio**: Launches a local process and communicates via stdin/stdout (most common)
- **http**: Connects to a remote MCP server over HTTP (planned feature)

---

## Server Compatibility Questions

### Q: My MCP server isn't connecting. How do I debug?
**A:** 
1. Check if the server process starts manually:
   ```bash
   python -m your_mcp_server  # Test command directly
   ```
2. Verify MCP protocol compliance:
   ```bash
   # Send initialize message manually
   echo '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"test","version":"1.0.0"}}}' | python -m your_mcp_server
   ```
3. Check application logs for specific errors
4. Increase logging level to DEBUG

### Q: How do I know if my MCP server is compatible?
**A:** Compatible MCP servers must respond to these basic messages:
- `initialize` - Returns server capabilities
- `tools/list` - Returns available tools
- `tools/call` - Executes a tool with parameters

Test manually with the JSON messages in the `mcp-input.txt` file.

### Q: Can I connect to multiple instances of the same MCP server?
**A:** Yes! Give each instance a unique ID:

```yaml
mcp:
  servers:
    mongodb-primary:
      type: stdio
      command: "java"
      args: ["-jar", "mongo-mcp-server.jar"]
      environment:
        MONGODB_URI: "mongodb://primary:27017/db"
    mongodb-replica:
      type: stdio  
      command: "java"
      args: ["-jar", "mongo-mcp-server.jar"]
      environment:
        MONGODB_URI: "mongodb://replica:27017/db"
```

---

## Development Questions

### Q: How do I build and run the project locally?
**A:** 

```bash
# Clone and build
git clone <repository-url>
cd global-mcp-client
mvn clean install

# Run with Maven
mvn spring-boot:run

# Or run the JAR
java -jar target/global-mcp-client-*.jar
```

### Q: How do I run tests?
**A:** 

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=McpClientServiceTest

# Run integration tests
mvn integration-test
```

### Q: How do I create a simple MCP server for testing?
**A:** Here's a minimal Python MCP server:

```python
# simple_mcp_server.py
import json
import sys

def handle_message(message):
    if message["method"] == "initialize":
        return {
            "jsonrpc": "2.0",
            "id": message["id"],
            "result": {
                "protocolVersion": "2024-11-05",
                "capabilities": {"tools": {}},
                "serverInfo": {"name": "simple-server", "version": "1.0.0"}
            }
        }
    elif message["method"] == "tools/list":
        return {
            "jsonrpc": "2.0", 
            "id": message["id"],
            "result": {"tools": []}
        }

while True:
    line = sys.stdin.readline()
    if not line:
        break
    message = json.loads(line)
    response = handle_message(message)
    print(json.dumps(response))
    sys.stdout.flush()
```

Then configure it:
```yaml
mcp:
  servers:
    test-server:
      type: stdio
      command: "python"
      args: ["simple_mcp_server.py"]
      timeout: 5000
      enabled: true
```

---

## API Usage Questions

### Q: How do I list all available tools across all servers?
**A:** 

```bash
curl http://localhost:8082/api/mcp/tools
```

### Q: How do I execute a tool on a specific server?
**A:** 

```bash
curl -X POST http://localhost:8082/api/mcp/servers/{serverId}/tools/{toolName} \
  -H "Content-Type: application/json" \
  -d '{"parameters": {"param1": "value1"}}'
```

### Q: How do I check the health of all servers?
**A:** 

```bash
curl http://localhost:8082/api/mcp/health
```

### Q: Can I use the API from JavaScript/browser?
**A:** Yes, the API supports CORS by default:

```javascript
fetch('http://localhost:8082/api/mcp/servers')
  .then(response => response.json())
  .then(data => console.log(data));
```

---

## Production Questions

### Q: How do I deploy this in production?
**A:** See our [Deployment Guide](DEPLOYMENT_GUIDE.md) for detailed instructions covering:
- Docker deployment
- Kubernetes deployment  
- Load balancing
- Security configuration
- Monitoring setup

### Q: How do I monitor the application?
**A:** The application exposes several monitoring endpoints:
- Health: `http://localhost:8082/actuator/health`
- Metrics: `http://localhost:8082/actuator/metrics`
- Prometheus: `http://localhost:8082/actuator/prometheus`

### Q: How do I scale the application?
**A:** The application is stateless and can be horizontally scaled:
- Run multiple instances behind a load balancer
- Each instance manages its own MCP server connections
- Use external configuration management for consistency

### Q: What are the resource requirements?
**A:** 
- **Minimum**: 512MB RAM, 2 CPU cores
- **Recommended**: 1GB RAM, 4 CPU cores
- **Storage**: 100MB for application + logs
- Additional resources depend on number and complexity of MCP servers

---

## Troubleshooting Questions

### Q: The application won't start. What should I check?
**A:** 
1. Java version: Requires Java 17+
2. Port availability: Default port 8081 must be free
3. Configuration syntax: Validate YAML syntax
4. Environment variables: Check all referenced variables exist
5. Logs: Check startup logs for specific errors

### Q: A server shows as "unhealthy". How do I fix it?
**A:** 
1. Check if the server process is running
2. Verify server command and arguments
3. Check server logs for errors
4. Test server connectivity manually
5. Verify environment variables are set correctly

### Q: How do I enable debug logging?
**A:** Add to your `application.yml`:

```yaml
logging:
  level:
    com.deepai.mcpclient: DEBUG
    org.springframework.web: DEBUG
```

### Q: The application is using too much memory. How do I optimize it?
**A:** 
1. Adjust JVM heap size: `java -Xmx512m -jar app.jar`
2. Reduce connection pool sizes in configuration
3. Lower server timeouts if appropriate
4. Monitor with JVM profiling tools

---

## Advanced Questions

### Q: Can I extend the client with custom functionality?
**A:** The client is designed as a gateway and doesn't include extension points. For custom functionality:
- Create your own MCP server with custom logic
- Connect it via the standard MCP protocol
- The client will automatically discover and expose your tools

### Q: How do I handle authentication with external MCP servers?
**A:** Configure authentication in the server environment:

```yaml
mcp:
  servers:
    secure-server:
      type: stdio
      command: "your-server"
      environment:
        API_KEY: "${SECURE_API_KEY}"
        AUTH_TOKEN: "${AUTH_TOKEN}"
```

### Q: Can I use this with non-MCP services?
**A:** Not directly. The client specifically implements the MCP protocol. To integrate non-MCP services:
1. Create an MCP server wrapper around your service
2. Implement the required MCP methods (`initialize`, `tools/list`, `tools/call`)
3. Connect the wrapper via this client

---

**Need more help?**
- Check the [README](../README.md) for basic information
- Review [Technical Specification](TECHNICAL_SPECIFICATION.md) for detailed architecture
- See [Configuration Examples](CONFIGURATION_EXAMPLES.md) for setup patterns
- Consult [Deployment Guide](DEPLOYMENT_GUIDE.md) for production deployment
