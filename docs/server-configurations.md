# GitHub and MongoDB MCP Server Configuration Guide

## Overview
This guide covers the production-ready configuration for GitHub MCP Server and MongoDB MCP Server integration with the Global MCP Client.

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

### Prerequisites
- Node.js 18+ runtime
- MongoDB instance (local or remote)
- MongoDB MCP server script

### Configuration
```yaml
mcp:
  servers:
    mongodb-mcp-server:
      type: stdio
      command: "node"
      args: ["./mcp-servers/mongodb-server.js"]
      timeout: 15000
      enabled: true
      environment:
        NODE_ENV: "production"
        MONGODB_URI: "${MONGODB_URI}"
        MONGODB_DB_NAME: "${MONGODB_DB_NAME}"
        MCP_SERVER_NAME: "mongodb-mcp-server"
        LOG_LEVEL: "info"
```

### Environment Variables
```bash
# Required
MONGODB_URI=mongodb://localhost:27017/myDB
MONGODB_DB_NAME=myDB

# Optional
MCP_MONGODB_SERVER_PATH=./mcp-servers/mongodb-server.js
MONGODB_MCP_TIMEOUT=15000
MONGODB_MCP_ENABLED=true
MCP_MONGODB_LOG_LEVEL=info
MONGODB_POOL_SIZE=10
MONGODB_CONNECTION_TIMEOUT=10000
```

### MongoDB Server Script Setup
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

### Available Tools
The MongoDB MCP server provides tools for:
- Database operations (list, create, drop)
- Collection management (list, create, drop)
- Document operations (insert, find, update, delete)
- Index management (create, list, drop)
- Aggregation pipelines
- Query execution

### Example API Calls
```bash
# List MongoDB tools
curl -X GET http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools

# List databases
curl -X POST http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools/listDatabases

# Insert document
curl -X POST http://localhost:8080/api/mcp/servers/mongodb-mcp-server/tools/insertDocument \
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
      - MONGODB_URI=mongodb://mongodb:27017/production
      - MONGODB_DB_NAME=production
    depends_on:
      - mongodb
    volumes:
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

# MongoDB Configuration
MONGODB_URI=mongodb://admin:password@mongodb:27017/production?authSource=admin
MONGODB_DB_NAME=production
MCP_MONGODB_SERVER_PATH=/opt/mcp-servers/mongodb-server.js
MONGODB_MCP_ENABLED=true

# Application Configuration
SPRING_PROFILES_ACTIVE=prod
PORT=8080
LOG_LEVEL=info
```

## Testing Configuration

### Health Checks
```bash
# Check overall health
curl http://localhost:8080/actuator/health

# Check individual server health
curl http://localhost:8080/api/mcp/servers/github-mcp-server/health
curl http://localhost:8080/api/mcp/servers/mongodb-mcp-server/health
```

### Profile-based Testing
```bash
# Test with only GitHub server
java -jar app.jar --spring.profiles.active=github-only

# Test with only MongoDB server
java -jar app.jar --spring.profiles.active=mongodb-only

# Test with example server
java -jar app.jar --spring.profiles.active=testing
```

## Troubleshooting

### Common Issues
1. **GitHub Token Invalid**: Ensure GITHUB_TOKEN has correct permissions
2. **MongoDB Connection Failed**: Check MONGODB_URI and network connectivity
3. **Stdio Process Failed**: Verify Node.js installation and script path
4. **Timeout Errors**: Increase timeout values for slow networks

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