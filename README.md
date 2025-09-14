# Global MCP Client

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.5-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.1-blue.svg)](https://spring.io/projects/spring-ai)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## Overview

- 🔌 **Multi-Protocol Support**: HTTP, stdio, and SSE connections
- 🏗️ **Server Management**: Dynamic server discovery and configuration
- 🚀 **Spring Boot**: Production-ready with actuator endpoints
- 🤖 **Copilot Integration**: Enhanced AI responses through MCP servers
- 📊 **Monitoring**: Health checks, metrics, and observability
- 🔧 **Configuration**: Flexible YAML-based server configuration
- 🧪 **Testing**: Comprehensive test suite with TestContainers
- 🐙 **GitHub Integration**: Built-in GitHub MCP server support
- 🍃 **MongoDB Integration**: Production-ready MongoDB MCP server
=======
The **Global MCP Client** is a production-ready Spring Boot application that acts as a centralized gateway for multiple Model Context Protocol (MCP) servers. Built with Spring AI MCP Client integration, it provides a unified REST API interface for client applications to interact with various MCP servers without implementing the MCP protocol directly.

### 🚀 Key Features

### Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 18+ (for stdio servers)
- MongoDB (for MongoDB MCP server)
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
  port: 8081  # Application runs on port 8081

mcp:
  client:
    default-timeout: 15000
    retry:
      max-attempts: 3
      backoff-multiplier: 1.5
  servers:
    github-mcp-server:
      type: http
      url: https://api.githubcopilot.com/mcp/
      timeout: 30000
      enabled: true
      headers:
        Authorization: "Bearer ${GITHUB_TOKEN}"
    mongodb-mcp-server:
      type: stdio
      command: node
      args: ["./mcp-servers/mongodb-server.js"]
      timeout: 15000
      enabled: true
      environment:
        MONGODB_URI: "${MONGODB_URI}"
        MONGODB_DB_NAME: "${MONGODB_DB_NAME}"
```

## Supported MCP Servers

### GitHub MCP Server
- **Type**: HTTP
- **Capabilities**: Repository management, issues, pull requests, file operations
- **Authentication**: GitHub Personal Access Token
- **Documentation**: [docs/server-configurations.md](docs/server-configurations.md#github-mcp-server-configuration)

### MongoDB MCP Server
- **Type**: Stdio (Node.js)
- **Capabilities**: Database operations, collections, documents, queries
- **Requirements**: MongoDB instance, Node.js runtime
- **Documentation**: [docs/server-configurations.md](docs/server-configurations.md#mongodb-mcp-server-configuration)

## Environment Setup

### For Development
1. Copy `.env.example` to `.env`
2. Fill in your credentials:
```bash
GITHUB_TOKEN=your_github_token_here
MONGODB_URI=mongodb://localhost:27017/testDB
MONGODB_DB_NAME=testDB
```

### For Production
Use environment variables or `application-prod.yml`:
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
export MONGODB_URI=mongodb://admin:password@mongodb:27017/production
export SPRING_PROFILES_ACTIVE=prod
=======
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


### Server Management
- `GET /api/mcp/servers` - List all configured servers
- `GET /api/mcp/servers/{serverId}/info` - Get server information
- `GET /api/mcp/servers/{serverId}/health` - Check server health

### Tool Operations
- `GET /api/mcp/servers/{serverId}/tools` - List available tools
- `POST /api/mcp/servers/{serverId}/tools/{toolName}` - Execute tool

### Resource Operations
- `GET /api/mcp/servers/{serverId}/resources` - List server resources
- `GET /api/mcp/servers/{serverId}/resources/read?uri={uri}` - Read resource

### Monitoring
- `GET /actuator/health` - Application health check
- `GET /actuator/metrics` - Application metrics
- `GET /actuator/prometheus` - Prometheus metrics

## Example Usage

### GitHub Operations
```bash
# Create a GitHub issue
curl -X POST http://localhost:8080/api/mcp/servers/github-mcp-server/tools/create_issue \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bug Report",
    "body": "Found a critical bug",
    "repo": "owner/repository"
  }'
```

### MongoDB Operations
```bash
# List databases
curl -X POST http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools/listDatabases

# Insert document
curl -X POST http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools/insertDocument \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "testDB",
    "collectionName": "users",
    "document": {"name": "John", "email": "john@example.com"}
  }'
```

- **GET /api/servers** - List configured servers
- **GET /api/servers/{serverId}** - Get server information
- **POST /api/servers/{serverId}/tools/{toolName}** - Execute tools on specific server
- **GET /api/servers/{serverId}/resources** - Get resources from server
- **GET /api/health** - Overall health status


## Dependencies

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │───▶│  MCP Client      │───▶│  GitHub MCP     │
│   Controller    │    │  Service         │    │  (HTTP)         │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │              ┌─────────────────┐
         ▼                       └─────────────▶│  MongoDB MCP    │
┌─────────────────┐    ┌──────────────────┐    │  (Stdio)        │
│   Validation    │    │  Connection      │    └─────────────────┘
│   & Security    │    │  Management      │    ┌─────────────────┐
└─────────────────┘    └──────────────────┘    │  Custom MCP     │
                                              │  Servers        │
                                              └─────────────────┘
```

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


### Profile-based Testing
```powershell
# Test with only GitHub server
mvn spring-boot:run -Dspring-boot.run.profiles=github-only

# Test with only MongoDB server
mvn spring-boot:run -Dspring-boot.run.profiles=mongodb-only
```

### Docker Support
```powershell
# Build image
mvn spring-boot:build-image

# Run with Docker Compose
docker-compose up -d
```

## Production Deployment

### Docker
```yaml
version: '3.8'
services:
  mcp-client:
    image: global-mcp-client:latest
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - MONGODB_URI=mongodb://mongodb:27017/production
    depends_on:
      - mongodb
```

### Kubernetes
See `k8s/` directory for Kubernetes deployment manifests.

## Documentation

- [Server Configurations](docs/server-configurations.md) - Detailed server setup guide
- [API Documentation](docs/api.md) - Complete API reference
- [Configuration Examples](docs/configuration-examples.md) - Sample configurations

## Contributing

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/amazing-feature`
3. Add tests for new functionality
4. Submit a pull request

## Troubleshooting

### Common Issues
- **GitHub 401 Unauthorized**: Check GITHUB_TOKEN validity
- **MongoDB Connection Failed**: Verify MONGODB_URI and network access
- **Stdio Process Failed**: Ensure Node.js and script paths are correct

### Debug Mode
```bash
export LOG_LEVEL=debug
java -jar target/global-mcp-client-1.0.0-SNAPSHOT.jar
```

## License

MIT License - see LICENSE file for details.
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

4. **Test Connectivity**: Visit `http://localhost:8081/swagger-ui.html`

5. **Use the API**: Make requests to `http://localhost:8081/api/`

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
  - Check for other applications using ports 8081, 8082
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
curl http://localhost:8081/actuator/health

# View server status
curl http://localhost:8081/api/servers

# Test specific server health
curl http://localhost:8081/api/servers/{serverId}/health

# View application logs
tail -f logs/application.log

# Test with actual server ID
curl http://localhost:8081/api/servers/mongo-mcp-server-test/health
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
