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

## Quick Start

### Prerequisites
- Java 17+
- Maven 3.9+

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
    github:
      type: http
      url: https://api.githubcopilot.com/mcp/
      timeout: 30000
    local-db:
      type: stdio
      command: node
      args: ["${user.dir}/mcp-servers/db-server.js"]
```

## API Endpoints

- `GET /api/mcp/servers` - List all configured servers
- `POST /api/mcp/servers/{serverId}/tools/{toolName}` - Execute tool
- `GET /api/mcp/servers/{serverId}/resources` - List server resources
- `GET /health` - Application health check
- `GET /metrics` - Application metrics

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │───▶│  MCP Client      │───▶│  MCP Servers    │
│   Controller    │    │  Service         │    │  (HTTP/stdio)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         ▼                       ▼                       ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Validation    │    │  Connection      │    │  Protocol       │
│   & Security    │    │  Management      │    │  Handlers       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Development

### Running Tests
```powershell
mvn test
```

### Docker Support
```powershell
mvn spring-boot:build-image
docker run -p 8080:8080 global-mcp-client:1.0.0-SNAPSHOT
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Submit a pull request

## License

MIT License - see LICENSE file for details.
