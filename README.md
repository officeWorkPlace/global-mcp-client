# Global MCP Client

A production-ready Spring Boot application for connecting to multiple MCP (Model Context Protocol) servers with GitHub Copilot integration.

## Features

- 🔌 **Multi-Protocol Support**: HTTP, stdio, and SSE connections
- 🏗️ **Server Management**: Dynamic server discovery and configuration
- 🚀 **Spring Boot**: Production-ready with actuator endpoints
- 🤖 **Copilot Integration**: Enhanced AI responses through MCP servers
- 📊 **Monitoring**: Health checks, metrics, and observability
- 🔧 **Configuration**: Flexible YAML-based server configuration
- 🧪 **Testing**: Comprehensive test suite with TestContainers
- 🐙 **GitHub Integration**: Built-in GitHub MCP server support
- 🍃 **MongoDB Integration**: Production-ready MongoDB MCP server

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+
- Node.js 18+ (for stdio servers)
- MongoDB (for MongoDB MCP server)

### Build and Run
```powershell
mvn clean package
java -jar target/global-mcp-client-1.0.0-SNAPSHOT.jar
```

### Configuration
Configure your MCP servers in `application.yml`:

```yaml
mcp:
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

## Architecture

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

## Development

### Running Tests
```powershell
mvn test
```

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