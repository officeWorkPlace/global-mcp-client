# GitHub and MongoDB MCP Server Configuration Guide

## Overview
This guide covers the production-ready configuration for GitHub MCP Server and MongoDB MCP Server integration with the Global MCP Client. The client supports multiple MongoDB server implementations for maximum flexibility.

## GitHub MCP Server Configuration

### Prerequisites
- GitHub Personal Access Token with appropriate permissions
- Access to GitHub Copilot MCP endpoint

### Configuration
```yaml
mcp:
  servers:
    github-mcp-server:
      type: http
      url: "https://api.githubcopilot.com/mcp/"
      timeout: 30000
      enabled: true
      headers:
        User-Agent: "Global-MCP-Client/1.0"
        Accept: "application/json"
        Content-Type: "application/json"
        Authorization: "Bearer ${GITHUB_TOKEN}"
```

### Environment Variables
```bash
# Required
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Optional
GITHUB_MCP_SERVER_URL=https://api.githubcopilot.com/mcp/
GITHUB_MCP_TIMEOUT=30000
GITHUB_MCP_ENABLED=true
```

### Available Tools
The GitHub MCP server provides tools for:
- Repository management (create, update, delete)
- Issue tracking (create, update, comment)
- Pull request operations (create, review, merge)
- File operations (read, write, commit)
- Branch management
- Workflow automation

### Example API Calls
```bash
# List GitHub tools
curl -X GET http://localhost:8080/api/mcp/servers/github-mcp-server/tools

# Create a GitHub issue
curl -X POST http://localhost:8080/api/mcp/servers/github-mcp-server/tools/create_issue \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Bug Report",
    "body": "Description of the issue",
    "repo": "owner/repository"
  }'
```

## MongoDB MCP Server Configuration

The Global MCP Client supports two MongoDB MCP server implementations:

### Option 1: Java Spring Boot MongoDB MCP Server (Recommended)

#### Prerequisites
- Java 17+ runtime
- Pre-built spring-boot-ai-mongo.jar
- MongoDB instance (local or remote)

#### Configuration
```yaml
mcp:
  servers:
    mongo-mcp-server:
      type: stdio
      command: "java"
      args: ["-jar", "C:/spring-boot-ai-mongo.jar"]
      timeout: 20000
      enabled: true
      environment:
        MONGO_HOST: "localhost"
        MONGO_PORT: "27017"
        MONGO_DATABASE: "production"
        SPRING_PROFILES_ACTIVE: "production"
        LOGGING_LEVEL: "INFO"
        SERVER_PORT: "0"
```

#### Environment Variables
```bash
# Required
MONGO_MCP_SERVER_JAR_PATH=C:/spring-boot-ai-mongo.jar
MONGO_HOST=localhost
MONGO_PORT=27017
MONGO_DATABASE=myDB

# Optional
MONGO_MCP_TIMEOUT=20000
MONGO_MCP_ENABLED=true
MONGO_MCP_PROFILE=development
MONGO_MCP_LOG_LEVEL=DEBUG
MONGO_MCP_SERVER_PORT=0
```

#### Claude Desktop Configuration
If using with Claude Desktop, add to `claude_desktop_config.json`:
```json
{
  "mcpServers": {
    "mongo-mcp-server": {
      "command": "java",
      "args": ["-jar", "C:/spring-boot-ai-mongo.jar"],
      "env": {
        "MONGO_HOST": "localhost",
        "MONGO_PORT": "27017"
      }
    }
  }
}
```

### Option 2: Node.js MongoDB MCP Server (Alternative)

#### Prerequisites
- Node.js 18+ runtime
- MongoDB instance (local or remote)
- MongoDB MCP server script

#### Configuration
```yaml
mcp:
  servers:
    mongodb-mcp-server:
      type: stdio
      command: "node"
      args: ["./mcp-servers/mongodb-server.js"]
      timeout: 15000
      enabled: false  # Disabled by default when Java version is enabled
      environment:
        NODE_ENV: "production"
        MONGODB_URI: "${MONGODB_URI}"
        MONGODB_DB_NAME: "${MONGODB_DB_NAME}"
        MCP_SERVER_NAME: "mongodb-mcp-server"
        LOG_LEVEL: "info"
```

#### Environment Variables
```bash
# Required
MONGODB_URI=mongodb://localhost:27017/myDB
MONGODB_DB_NAME=myDB

# Optional
MCP_MONGODB_SERVER_PATH=./mcp-servers/mongodb-server.js
MONGODB_MCP_TIMEOUT=15000
MONGODB_MCP_ENABLED=false
MCP_MONGODB_LOG_LEVEL=info
MONGODB_POOL_SIZE=10
MONGODB_CONNECTION_TIMEOUT=10000
```

#### MongoDB Server Script Setup
Create `mcp-servers/mongodb-server.js`:
```javascript
#!/usr/bin/env node

const { MongoClient } = require('mongodb');
const { Server } = require('@modelcontextprotocol/sdk/server/index.js');
const { StdioServerTransport } = require('@modelcontextprotocol/sdk/server/stdio.js');

const server = new Server(
  {
    name: 'mongodb-mcp-server',
    version: '1.0.0',
  },
  {
    capabilities: {
      tools: {},
    },
  }
);

// Add MongoDB tools here
server.setRequestHandler('tools/list', async () => {
  return {
    tools: [
      {
        name: 'listDatabases',
        description: 'List all databases',
        inputSchema: {
          type: 'object',
          properties: {},
        },
      },
      // Add more tools...
    ],
  };
});

// Start the server
const transport = new StdioServerTransport();
server.connect(transport);
```

### MongoDB Tools Available (Both Implementations)
- Database operations (list, create, drop)
- Collection management (list, create, drop)
- Document operations (insert, find, update, delete)
- Index management (create, list, drop)
- Aggregation pipelines
- Query execution

### Example API Calls
```bash
# List MongoDB tools (Java implementation)
curl -X GET http://localhost:8080/api/mcp/servers/mongo-mcp-server/tools

# List MongoDB tools (Node.js implementation)
curl -X GET http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools

# List databases
curl -X POST http://localhost:8080/api/mcp/servers/mongo-mcp-server/tools/listDatabases

# Insert document
curl -X POST http://localhost:8080/api/mcp/servers/mongo-mcp-server/tools/insertDocument \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "myDB",
    "collectionName": "users",
    "document": {
      "name": "John Doe",
      "email": "john@example.com",
      "age": 30
    }
  }'
```

## Production Deployment

### Docker Compose Example
```yaml
version: '3.8'

services:
  mcp-client:
    build: .
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - GITHUB_TOKEN=${GITHUB_TOKEN}
      - MONGO_HOST=mongodb
      - MONGO_PORT=27017
      - MONGO_DATABASE=production
      - MONGO_MCP_SERVER_JAR_PATH=/opt/jars/spring-boot-ai-mongo.jar
    depends_on:
      - mongodb
    volumes:
      - ./jars:/opt/jars:ro
      - ./mcp-servers:/opt/mcp-servers:ro

  mongodb:
    image: mongo:7.0
    ports:
      - "27017:27017"
    environment:
      - MONGO_INITDB_ROOT_USERNAME=admin
      - MONGO_INITDB_ROOT_PASSWORD=password
    volumes:
      - mongodb_data:/data/db

volumes:
  mongodb_data:
```

### Environment Variables for Production
```bash
# GitHub Configuration
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
GITHUB_MCP_SERVER_URL=https://api.githubcopilot.com/mcp/
GITHUB_MCP_ENABLED=true

# MongoDB Configuration - Java Implementation (Primary)
MONGO_MCP_SERVER_JAR_PATH=/opt/jars/spring-boot-ai-mongo.jar
MONGO_HOST=mongodb
MONGO_PORT=27017
MONGO_DATABASE=production
MONGO_MCP_ENABLED=true
MONGO_MCP_PROFILE=production

# MongoDB Configuration - Node.js Implementation (Backup)
MONGODB_URI=mongodb://admin:password@mongodb:27017/production?authSource=admin
MONGODB_DB_NAME=production
MCP_MONGODB_SERVER_PATH=/opt/mcp-servers/mongodb-server.js
MONGODB_MCP_ENABLED=false

# Application Configuration
SPRING_PROFILES_ACTIVE=prod
PORT=8080
LOG_LEVEL=info
```

## Implementation Selection Guide

### Use Java Spring Boot Implementation When:
- You prefer Java ecosystem and tooling
- You need Spring Boot's enterprise features
- You want to leverage Spring AI capabilities
- You have existing Spring Boot infrastructure
- You need robust configuration management

### Use Node.js Implementation When:
- You prefer JavaScript/Node.js ecosystem
- You need lighter resource footprint
- You have existing Node.js infrastructure
- You want rapid development and deployment
- You need custom MongoDB operations

### Hybrid Configuration
Both implementations can be configured simultaneously with different names:
- `mongo-mcp-server` (Java Spring Boot - Primary)
- `mongodb-mcp-server` (Node.js - Backup/Alternative)

Toggle between implementations using the `enabled` flag in configuration.

## Testing Configuration

### Health Checks
```bash
# Check overall health
curl http://localhost:8080/actuator/health

# Check individual server health
curl http://localhost:8080/api/mcp/servers/github-mcp-server/health
curl http://localhost:8080/api/mcp/servers/mongo-mcp-server/health
curl http://localhost:8080/api/mcp/servers/mongodb-mcp-server/health
```

### Profile-based Testing
```bash
# Test with only GitHub server
java -jar app.jar --spring.profiles.active=github-only

# Test with Java MongoDB server
java -jar app.jar --spring.profiles.active=mongo-java

# Test with Node.js MongoDB server
java -jar app.jar --spring.profiles.active=mongo-nodejs

# Test with example server
java -jar app.jar --spring.profiles.active=testing
```

## Troubleshooting

### Common Issues
1. **GitHub Token Invalid**: Ensure GITHUB_TOKEN has correct permissions
2. **MongoDB Connection Failed**: Check MONGO_HOST/MONGO_PORT or MONGODB_URI
3. **Java JAR Not Found**: Verify MONGO_MCP_SERVER_JAR_PATH exists
4. **Stdio Process Failed**: Check Java/Node.js installation and script paths
5. **Timeout Errors**: Increase timeout values for slow networks

### Debug Logging
```yaml
logging:
  level:
    com.officeworkplace: DEBUG
    org.springframework.web: DEBUG
```

### Monitoring
- Health endpoint: `/actuator/health`
- Metrics endpoint: `/actuator/metrics`
- Prometheus metrics: `/actuator/prometheus`

## Migration Guide

### From Node.js to Java Implementation
1. Set `MONGO_MCP_ENABLED=true` and `MONGODB_MCP_ENABLED=false`
2. Configure Java-specific environment variables
3. Deploy spring-boot-ai-mongo.jar to target location
4. Update health checks to use `mongo-mcp-server` endpoint

### From Java to Node.js Implementation
1. Set `MONGO_MCP_ENABLED=false` and `MONGODB_MCP_ENABLED=true`
2. Configure Node.js-specific environment variables
3. Deploy mongodb-server.js script and dependencies
4. Update health checks to use `mongodb-mcp-server` endpoint