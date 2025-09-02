# Global MCP Client

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.1-blue.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

The **Global MCP Client** is a production-ready Spring Boot application that acts as a centralized gateway for multiple Model Context Protocol (MCP) servers. Built with Spring AI MCP Client integration, it provides a unified REST API interface for client applications to interact with various MCP servers without implementing the MCP protocol directly.

### 🚀 Key Features

- ✅ **Spring AI MCP Integration**: Native Spring AI MCP Client support
- ✅ **Multi-Server Management**: Connect to multiple MCP servers simultaneously
- ✅ **Smart Detection**: Automatic server type detection (Spring AI vs Standard stdio)
- ✅ **Health Monitoring**: Real-time server health checking and status monitoring
- ✅ **REST API Gateway**: Unified HTTP API for all connected MCP servers
- ✅ **Reactive Programming**: Non-blocking operations with Spring WebFlux
- ✅ **Production Ready**: Comprehensive logging, metrics, and error handling

## Architecture

```
┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
│   Client App    │    │   Client App    │    │   Client App    │
└─────────┬───────┘    └─────────┬───────┘    └─────────┬───────┘
          │                      │                      │
          └──────────────────────┼──────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │                         │
                    │   Global MCP Client     │
                    │   (This Application)    │
                    │                         │
                    └─────────────┬───────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          │                       │                       │
┌─────────▼───────┐    ┌─────────▼───────┐    ┌─────────▼───────┐
│   MCP Server 1  │    │   MCP Server 2  │    │   MCP Server 3  │
│   (MongoDB)     │    │   (Files)       │    │   (GitHub)      │
└─────────────────┘    └─────────────────┘    └─────────────────┘
```

## Supported MCP Servers

The Global MCP Client currently supports two main types of MCP servers:

### 🔵 **Spring AI MCP Servers** (Java-based)
- **Auto-detected when**: Command is `java` with Spring-specific arguments
- **Examples**: MongoDB MCP Server, PostgreSQL MCP Server
- **Implementation**: `SpringAiMcpServerConnection`
- **Protocol**: Native MCP over stdio with Spring AI integration

### 🟡 **Standard stdio MCP Servers** (Language-agnostic)
- **Auto-detected when**: No Spring-specific patterns detected
- **Examples**: Python filesystem server, Node.js servers, Rust/Go servers
- **Implementation**: `StdioMcpServerConnection`
- **Protocol**: Standard MCP over stdin/stdout

### 📋 **Tested Compatible Servers**
- ✅ **Spring AI MongoDB MCP Server** - Full CRUD operations, aggregation, AI features
- ✅ **Python Filesystem MCP Server** - File operations, directory management
- ✅ **Custom MCP Servers** - Any MCP-compliant server following the specification

## Core Components

### 1. **MCP Server Management**
- **Connection Types**: Spring AI stdio, Standard stdio (HTTP planned)
- **Smart Detection**: Automatic server type detection based on command
- **Health Monitoring**: Real-time connection and tool availability monitoring
- **Lifecycle Management**: Individual server initialization and shutdown

### 2. **Configuration**
- **Multi-Server Setup**: Configure multiple MCP servers
- **Connection Parameters**: Timeouts, retry policies, headers
- **Server Types**: Different transport protocols per server

### 3. **API Gateway**
- **REST API**: Standard HTTP interface for clients
- **Request Routing**: Intelligent routing based on server capabilities
- **Response Aggregation**: Combine results from multiple servers

### 4. **Models & DTOs**
- **MCP Protocol Objects**: Messages, tools, resources
- **Server Information**: Status, capabilities, metadata
- **Error Handling**: Standardized error responses

## Key Features

### ✅ **What This Client Does:**
- ✅ **Multi-Server Connectivity**: Connect to various MCP servers simultaneously
- ✅ **Protocol Abstraction**: Hide MCP protocol complexity from client applications
- ✅ **Server Health Monitoring**: Track server availability and performance
- ✅ **Request Distribution**: Route requests to appropriate servers
- ✅ **Unified API**: Single API endpoint for multiple MCP servers
- ✅ **Configuration Management**: Dynamic server configuration
- ✅ **Error Handling**: Graceful error handling and fallback mechanisms

### ❌ **What This Client Does NOT Do:**
- ❌ **Data Storage**: No MongoDB or database - servers handle their own data
- ❌ **AI Processing**: No OpenAI integration - AI servers handle AI tasks
- ❌ **Business Logic**: No domain-specific logic - pure MCP gateway
- ❌ **Data Transformation**: Minimal data processing - mainly routing

## Configuration Example

```yaml
server:
  port: 8082  # Application runs on port 8082

mcp:
  client:
    default-timeout: 15000
    retry:
      max-attempts: 3
      backoff-multiplier: 1.5
  servers:
    # Spring AI MongoDB MCP Server (Java-based)
    mongo-mcp-server:
      type: stdio
      command: "java"
      args:
        - "-Dspring.profiles.active=mcp"
        - "-jar"
        - "spring-ai-mongo-mcp-server.jar"
      timeout: 20000
      enabled: true
      environment:
        SPRING_DATA_MONGODB_URI: "mongodb://localhost:27017/mcpserver"
    
    # Python Filesystem MCP Server
    filesystem-server:
      type: stdio
      command: "python"
      args: ["-m", "mcp_server_filesystem"]
      timeout: 10000
      enabled: true
      environment:
        ROOT_PATH: "/safe/directory"
```

## API Endpoints

- **GET /api/mcp/servers** - List configured servers
- **GET /api/mcp/servers/{serverId}** - Get server information
- **POST /api/mcp/servers/{serverId}/tools** - Execute tools on specific server
- **GET /api/mcp/servers/{serverId}/resources** - Get resources from server
- **GET /api/mcp/health** - Overall health status

## Dependencies

### Core Dependencies:
- **Spring Boot 3.3.3**: Web framework and auto-configuration
- **Spring WebFlux**: Reactive web programming
- **Apache HttpClient 5**: HTTP client for server communication
- **Jackson**: JSON processing for MCP protocol
- **OpenAPI/Swagger**: API documentation

### Development Dependencies:
- **JUnit 5**: Unit testing
- **WireMock**: HTTP service mocking for tests
- **Spring Test**: Integration testing support

## Use Cases

1. **Multi-Platform Integration**: Connect apps to GitHub, file systems, databases via single API
2. **Microservices Architecture**: Central MCP gateway for distributed MCP services
3. **Development & Testing**: Mock and test MCP integrations
4. **Load Distribution**: Distribute requests across multiple MCP server instances
5. **Failover & Redundancy**: Automatic failover between redundant MCP servers

## Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- At least one MCP server to connect to

### Quick Start

1. **Clone and Build**:
   ```bash
   git clone <repository-url>
   cd global-mcp-client
   mvn clean package
   ```

2. **Configure Servers**: Edit `application-improved.yml` to define your MCP servers

3. **Start Application**:
   ```bash
   mvn spring-boot:run
   # OR
   java -jar target/global-mcp-client-1.0.0-SNAPSHOT.jar
   ```

4. **Test Connectivity**: Visit `http://localhost:8082/swagger-ui.html`

5. **Use the API**: Make requests to `http://localhost:8082/api/mcp/`

This global client is designed to be the **central hub** for all MCP communications in your infrastructure, while actual data processing and storage happens in the individual MCP servers.

## Troubleshooting

### Common Issues

#### Server Connection Issues
- **Problem**: `Connection refused` or `Server not responding`
- **Solution**: 
  - Check if the MCP server process is running
  - Verify the command path and arguments in configuration
  - Check server logs for startup errors
  - Ensure environment variables are properly set

#### Port Conflicts
- **Problem**: `Port already in use`
- **Solution**: 
  - Change the server port in `application.yml`
  - Check for other applications using ports 8081, 9090
  - Use `netstat -an | findstr :8081` on Windows or `lsof -i :8081` on Unix

#### Configuration Errors
- **Problem**: `Invalid configuration` or startup failures
- **Solution**:
  - Validate YAML syntax using online YAML validators
  - Check indentation (use spaces, not tabs)
  - Verify environment variable references like `${MONGODB_URI}`
  - Review application logs for specific validation errors

#### MCP Server Compatibility
- **Problem**: `Protocol version mismatch` or `Invalid response`
- **Solution**:
  - Ensure MCP server supports protocol version `2024-11-05`
  - Check server implements required MCP methods: `initialize`, `tools/list`, `tools/call`
  - Verify JSON-RPC 2.0 compliance in server responses

### Debug Commands

```bash
# Check application health
curl http://localhost:8082/actuator/health

# View server status
curl http://localhost:8082/api/mcp/servers

# Test specific server health
curl http://localhost:8082/api/mcp/servers/{serverId}/health

# View application logs
tail -f logs/application.log

# Test with actual server ID
curl http://localhost:8082/api/mcp/servers/mongo-mcp-server-test/health
```

### Logging Configuration

For debugging, increase log level in `application.yml`:

```yaml
logging:
  level:
    com.deepai.mcpclient: DEBUG
    org.springframework.web: DEBUG
    root: INFO
```
