# MCP Controller API Documentation

## Overview

The **Global MCP Client** provides a REST API to interact with various **Model Context Protocol (MCP) servers**, including the MongoDB MCP Server. This API acts as a gateway to execute tools, manage resources, and monitor server health across different MCP server implementations.

**Base URL**: `http://localhost:8081/api`

---

## 🚀 Quick Start

### Prerequisites
1. **Java 17+** installed
2. **MongoDB** running on `localhost:27017`
3. **MCP Client** application running on port `8081`
4. **MongoDB MCP Server** configured and available

### Starting the Application
```bash
# Navigate to project directory
cd global-mcp-client

# Start the application
mvn spring-boot:run
```

The API will be available at: `http://localhost:8081/api`

---

## 📋 API Endpoints

### 1. Server Management

#### 📊 List All MCP Servers
**GET** `/api/servers`

Lists all configured MCP servers in the system.

```bash
curl -X GET "http://localhost:8081/api/servers"
```

**Response Example:**
```json
["mongo-mcp-server-test"]
```

---

#### ℹ️ Get Server Information
**GET** `/api/servers/{serverId}/info`

Retrieves detailed information about a specific MCP server.

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/info"
```

**Response Example:**
```json
{
  "name": "mongo-mcp-server",
  "version": "1.0.0",
  "description": "MongoDB MCP Server for database operations",
  "vendor": "DeepAI",
  "capabilities": {
    "tools": {},
    "resources": {},
    "prompts": {},
    "logging": {}
  },
  "metadata": {}
}
```

---

#### 🏥 Check Server Health
**GET** `/api/servers/{serverId}/health`

Checks if a specific MCP server is healthy and responsive.

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/health"
```

**Response Example:**
```json
{
  "healthy": true
}
```

---

#### 🏥 Get Overall Health Status
**GET** `/api/health`

Returns health status of all configured servers.

```bash
curl -X GET "http://localhost:8081/api/health"
```

**Response Example:**
```json
{
  "mongo-mcp-server-test": true
}
```

---

### 2. Tool Management & Execution

#### 🔧 List Server Tools
**GET** `/api/servers/{serverId}/tools`

Lists all available tools from a specific MCP server.

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/tools"
```

**Response Example:**
```json
[
  {
    "name": "ping",
    "description": "Test database connectivity by sending a ping command",
    "inputSchema": {
      "type": "object",
      "properties": {},
      "required": []
    }
  },
  {
    "name": "listDatabases",
    "description": "List all databases in the MongoDB instance with statistics",
    "inputSchema": {
      "type": "object",
      "properties": {},
      "required": []
    }
  },
  {
    "name": "listCollections",
    "description": "List all collections in the specified database with metadata",
    "inputSchema": {
      "type": "object",
      "properties": {
        "dbName": {
          "type": "string",
          "description": "Name of the database to list collections from"
        }
      },
      "required": ["dbName"]
    }
  }
]
```

---

#### 🔧 Get All Tools from All Servers
**GET** `/api/tools`

Retrieves tools from all configured servers.

```bash
curl -X GET "http://localhost:8081/api/tools"
```

**Response Example:**
```json
{
  "mongo-mcp-server-test": [
    {
      "name": "ping",
      "description": "Test database connectivity",
      "inputSchema": {...}
    }
  ]
}
```

---

#### ⚡ Execute a Tool
**POST** `/api/servers/{serverId}/tools/{toolName}`

Executes a specific tool with provided arguments.

**Content-Type**: `application/json`

##### Example 1: Ping Database
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/ping" \
  -H "Content-Type: application/json" \
  -d '{}'
```

##### Example 2: List Databases
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/listDatabases" \
  -H "Content-Type: application/json" \
  -d '{}'
```

##### Example 3: List Collections
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/listCollections" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "testdb"
  }'
```

##### Example 4: Create Database
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/createDatabase" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "my_new_db",
    "initialCollectionName": "initial_collection"
  }'
```

##### Example 5: Insert Document
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/insertDocument" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "testdb",
    "collectionName": "users",
    "jsonDocument": "{\"name\": \"John Doe\", \"age\": 30, \"email\": \"john@example.com\"}"
  }'
```

##### Example 6: Find Documents
```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/findDocument" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "testdb",
    "collectionName": "users",
    "jsonQuery": "{\"age\": {\"$gte\": 25}}",
    "projection": "{}",
    "sort": "{}",
    "limit": 10
  }'
```

**Response Format:**
```json
{
  "content": [
    {
      "type": "text",
      "text": "Database connection is healthy - ping successful."
    }
  ],
  "isError": false
}
```

---

### 3. Resource Management

#### 📂 List Server Resources
**GET** `/api/servers/{serverId}/resources`

Lists available resources from a server.

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/resources"
```

---

#### 📖 Read Resource Content
**GET** `/api/servers/{serverId}/resources/read?uri={resourceUri}`

Reads content from a specific resource.

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/resources/read?uri=mongodb://collection/users"
```

---

### 4. Advanced Operations

#### 📨 Send Raw Message
**POST** `/api/servers/{serverId}/messages`

Sends a raw MCP protocol message to the server.

```bash
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/messages" \
  -H "Content-Type: application/json" \
  -d '{
    "method": "tools/call",
    "params": {
      "name": "ping",
      "arguments": {}
    }
  }'
```

---

#### 📡 Subscribe to Notifications (Server-Sent Events)
**GET** `/api/servers/{serverId}/notifications`

**Content-Type**: `text/event-stream`

```bash
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/notifications" \
  -H "Accept: text/event-stream"
```

---

## 🗄️ MongoDB MCP Server Tools (All 39 Tools)

The MongoDB MCP Server provides comprehensive database management tools:

### Core Database Operations (5 tools)
- **`ping`** - Test database connectivity
- **`listDatabases`** - List all databases with statistics
- **`getDatabaseStats`** - Get comprehensive database statistics
- **`createDatabase`** - Create a new database with initial collection
- **`dropDatabase`** - Drop/delete a database permanently
- **`repairDatabase`** - Perform database maintenance and repair operations

### Collection Management (6 tools)
- **`listCollections`** - List all collections with metadata
- **`createCollection`** - Create a new collection with schema validation options
- **`dropCollection`** - Drop/delete a collection permanently
- **`getCollectionStats`** - Get detailed collection statistics
- **`renameCollection`** - Rename a collection safely
- **`validateSchema`** - Validate document schemas against collection rules

### Document Operations (9 tools)
- **`insertDocument`** - Insert a single document with validation
- **`insertMany`** - Bulk insert multiple documents
- **`findDocument`** - Find documents with advanced query, projection, and sorting
- **`findOne`** - Find a single document by criteria
- **`updateDocument`** - Update documents matching criteria
- **`deleteDocument`** - Delete documents matching criteria
- **`countDocuments`** - Count documents matching criteria
- **`distinctValues`** - Get distinct values from a specific field
- **`groupByField`** - Group documents by a specific field and get counts

### Index Management (5 tools)
- **`listIndexes`** - List all indexes for a collection
- **`createIndex`** - Create single or compound indexes with options
- **`dropIndex`** - Drop/delete an index by name
- **`reIndex`** - Rebuild all indexes for optimization
- **`createVectorIndex`** - Create vector search index for AI embeddings

### Query & Search Operations (7 tools)
- **`simpleQuery`** - Execute a simple query on a collection
- **`complexQuery`** - Execute a complex query on a collection
- **`aggregatePipeline`** - Execute complex aggregation pipeline with multiple stages
- **`textSearch`** - Perform full-text search with scoring
- **`geoSearch`** - Perform geospatial queries and operations
- **`vectorSearch`** - Perform semantic similarity search using vector embeddings
- **`semanticSearch`** - Perform natural language search across collections using AI

### AI-Powered Tools (7 tools)
- **`aiAnalyzeDocument`** - Analyze document content using AI for insights and patterns
- **`aiAnalyzeCollection`** - Analyze collection structure and patterns using AI
- **`aiDocumentSummary`** - Generate AI-powered summaries of document content
- **`aiQuerySuggestion`** - Get AI suggestions for optimal queries based on collection analysis
- **`generateEmbeddings`** - Generate vector embeddings for documents using AI models
- **`semanticSearch`** - Perform natural language search across collections using AI
- **`vectorSearch`** - Perform semantic similarity search using vector embeddings

### Performance & Optimization (1 tool)
- **`explainQuery`** - Analyze query execution plan for optimization

---

## 🧪 Testing MongoDB Tools

### Basic Connectivity Test
```bash
# Test if MongoDB server is accessible
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/ping" \
  -H "Content-Type: application/json" \
  -d '{}'
```

### Database Operations Workflow
```bash
# 1. List existing databases
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/listDatabases" \
  -H "Content-Type: application/json" \
  -d '{}'

# 2. Create a new database
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/createDatabase" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "blog_db",
    "initialCollectionName": "posts"
  }'

# 3. List collections in the new database
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/listCollections" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "blog_db"
  }'

# 4. Insert a document
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/insertDocument" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "blog_db",
    "collectionName": "posts",
    "jsonDocument": "{\"title\": \"My First Post\", \"content\": \"Hello World!\", \"author\": \"John Doe\", \"createdAt\": \"2024-01-15\"}"
  }'

# 5. Find documents
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/findDocument" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "blog_db",
    "collectionName": "posts",
    "jsonQuery": "{\"author\": \"John Doe\"}",
    "projection": "{}",
    "sort": "{}",
    "limit": 5
  }'

# 6. Count documents
curl -X POST "http://localhost:8081/api/servers/mongo-mcp-server-test/tools/countDocuments" \
  -H "Content-Type: application/json" \
  -d '{
    "dbName": "blog_db",
    "collectionName": "posts",
    "jsonQuery": "{}"
  }'
```

---

## 🚨 Error Handling

### Common Error Responses

#### Server Not Found
```json
{
  "error": "Server not found",
  "status": 500
}
```

#### Tool Execution Error
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Database connection failed"
    }
  ],
  "isError": true
}
```

#### Invalid Parameters
```json
{
  "error": "Invalid request parameters",
  "details": "Missing required field: databaseName",
  "status": 400
}
```

---

## 📈 Monitoring & Health Checks

### Health Check Endpoint
```bash
# Check overall system health
curl -X GET "http://localhost:8081/api/health"

# Check specific server health
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/health"
```

### Server Information
```bash
# Get detailed server info
curl -X GET "http://localhost:8081/api/servers/mongo-mcp-server-test/info"
```

---

## 🎯 Best Practices

1. **Always check server health** before executing tools
2. **Use appropriate timeout values** for long-running operations
3. **Validate JSON payloads** before sending requests
4. **Handle errors gracefully** in your client applications
5. **Monitor server notifications** for real-time updates
6. **Use proper MongoDB query syntax** in JSON string format

---

## 🔧 Configuration

The MCP servers are configured in `application.yml`:

```yaml
mcp:
  servers:
    mongo-mcp-server-test:
      type: stdio
      command: "java"
      args:
        - "-Dspring.profiles.active=mcp"
        - "-jar"
        - "spring-boot-ai-mongo-mcp-server.jar"
      environment:
        SPRING_DATA_MONGODB_URI: "mongodb://localhost:27017/mcpserver"
```

---

## 📚 Additional Resources

- **OpenAPI/Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **Health Actuator**: `http://localhost:8081/actuator/health`
- **Application Info**: `http://localhost:8081/actuator/info`

---

This API provides a powerful interface to interact with MongoDB through the MCP protocol, enabling seamless database operations via REST endpoints.
