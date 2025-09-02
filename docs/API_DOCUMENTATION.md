# Global MCP Client - API Documentation
## Spring AI MCP Client Gateway API Reference

**Document Version:** 2.0  
**Date:** 2025-09-02  
**Classification:** Technical Reference  
**API Version:** v1.0  
**Project:** Global MCP Client v1.0.0-SNAPSHOT

---

## 1. Executive Summary

The Global MCP Client API provides programmatic access to multiple Model Context Protocol (MCP) servers through a unified Spring Boot REST interface. Built on Spring AI MCP Client technology, this API enables seamless integration with both Spring AI MCP servers and standard stdio MCP servers.

### 1.1 Key Features

- **Spring AI Integration**: Native support for Spring AI MCP servers
- **Smart Server Detection**: Automatic detection of Spring AI vs standard stdio servers
- **Multi-Server Management**: Concurrent connections to multiple MCP servers
- **Reactive API**: Non-blocking operations with Spring WebFlux
- **Health Monitoring**: Real-time server health and status tracking
- **Production Ready**: Comprehensive logging, metrics, and error handling

---

## 2. API Overview

### 2.1 Base URL Structure

**Current Implementation URLs:**

```
Development: http://localhost:8082/api/mcp/
Production:  https://your-domain.com/api/mcp/
Staging:     https://staging.your-domain.com/api/mcp/
```

**Important Notes:**
- The application runs on port **8082** by default
- Base path is `/api/mcp/` (not `/api/v1/`)
- Health endpoints are available at `/actuator/health`
- Swagger UI is available at `/swagger-ui.html`

### 2.2 Authentication

All API requests require authentication via one of the following methods:

#### Bearer Token Authentication
```http
Authorization: Bearer <your-api-token>
```

#### API Key Authentication
```http
X-API-Key: <your-api-key>
```

#### OAuth 2.0
```http
Authorization: Bearer <oauth-access-token>
```

### 2.3 Content Types

```http
Content-Type: application/json
Accept: application/json
```

---

## 3. Server Management Endpoints

### 3.1 List Configured Servers

**GET** `/servers`

Returns a list of all configured MCP servers.

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `status` | string | No | Filter by server status (`active`, `inactive`, `error`) |
| `type` | string | No | Filter by connection type (`stdio`, `http`) |
| `page` | integer | No | Page number for pagination (default: 1) |
| `limit` | integer | No | Number of results per page (default: 50, max: 200) |

#### Response

```json
{
  "servers": [
    {
      "id": "mongodb-primary",
      "name": "MongoDB Primary Server",
      "type": "stdio",
      "status": "active",
      "endpoint": "java -jar mongo-mcp-server.jar",
      "capabilities": {
        "tools": 15,
        "resources": 5,
        "prompts": 3
      },
      "health": {
        "status": "healthy",
        "last_check": "2025-09-01T19:05:32Z",
        "response_time_ms": 120
      },
      "created_at": "2025-08-15T10:30:00Z",
      "updated_at": "2025-09-01T18:45:15Z"
    }
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total": 12,
    "has_next": false
  }
}
```

### 3.2 Get Server Details

**GET** `/servers/{serverId}`

Retrieves detailed information about a specific server.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `serverId` | string | Yes | Unique server identifier |

#### Response

```json
{
  "id": "mongodb-primary",
  "name": "MongoDB Primary Server",
  "description": "Primary MongoDB database server with full CRUD operations",
  "type": "stdio",
  "status": "active",
  "configuration": {
    "command": "java",
    "args": ["-jar", "mongo-mcp-server.jar"],
    "timeout": 15000,
    "environment": {
      "MONGODB_URI": "mongodb://***",
      "MONGODB_DATABASE": "production"
    }
  },
  "capabilities": {
    "protocol_version": "2024-11-05",
    "tools": {
      "count": 15,
      "categories": ["database", "query", "aggregation"]
    },
    "resources": {
      "count": 5,
      "types": ["collection", "index", "schema"]
    },
    "prompts": {
      "count": 3,
      "categories": ["query-help", "performance", "modeling"]
    }
  },
  "metrics": {
    "requests_total": 1247,
    "errors_total": 12,
    "avg_response_time_ms": 340,
    "uptime_seconds": 432000
  },
  "health": {
    "status": "healthy",
    "checks": [
      {
        "name": "connectivity",
        "status": "passing",
        "last_check": "2025-09-01T19:05:32Z"
      },
      {
        "name": "authentication",
        "status": "passing", 
        "last_check": "2025-09-01T19:05:32Z"
      }
    ]
  }
}
```

### 3.3 Add New Server

**POST** `/servers`

Registers a new MCP server with the client.

#### Request Body

```json
{
  "id": "new-server",
  "name": "New MCP Server",
  "description": "Description of the new server",
  "type": "stdio",
  "configuration": {
    "command": "python",
    "args": ["-m", "new_mcp_server"],
    "timeout": 10000,
    "environment": {
      "API_KEY": "${API_KEY}",
      "LOG_LEVEL": "INFO"
    }
  },
  "enabled": true
}
```

#### Response

```json
{
  "id": "new-server",
  "status": "registered",
  "message": "Server successfully registered and initialized",
  "initialization_time_ms": 2340
}
```

### 3.4 Update Server Configuration

**PUT** `/servers/{serverId}`

Updates the configuration of an existing server.

#### Request Body

```json
{
  "name": "Updated Server Name",
  "configuration": {
    "timeout": 20000,
    "environment": {
      "LOG_LEVEL": "DEBUG"
    }
  },
  "enabled": true
}
```

#### Response

```json
{
  "id": "mongodb-primary",
  "status": "updated",
  "message": "Server configuration updated successfully",
  "restart_required": false
}
```

### 3.5 Remove Server

**DELETE** `/servers/{serverId}`

Removes a server from the client configuration.

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `force` | boolean | No | Force removal even if server is active |

#### Response

```json
{
  "id": "old-server",
  "status": "removed",
  "message": "Server successfully removed from configuration"
}
```

---

## 4. Tool Discovery and Execution

### 4.1 List Available Tools

**GET** `/tools`

Discovers and lists all available tools across all active servers.

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `server_id` | string | No | Filter tools by specific server |
| `category` | string | No | Filter by tool category |
| `search` | string | No | Search in tool names and descriptions |

#### Response

```json
{
  "tools": [
    {
      "name": "create_document",
      "description": "Creates a new document in MongoDB collection",
      "server_id": "mongodb-primary",
      "server_name": "MongoDB Primary Server",
      "category": "database",
      "input_schema": {
        "type": "object",
        "properties": {
          "collection": {
            "type": "string",
            "description": "Collection name"
          },
          "document": {
            "type": "object",
            "description": "Document data"
          }
        },
        "required": ["collection", "document"]
      },
      "examples": [
        {
          "description": "Create a user document",
          "input": {
            "collection": "users",
            "document": {
              "name": "John Doe",
              "email": "john@example.com"
            }
          }
        }
      ]
    }
  ],
  "total_count": 47,
  "servers_count": 8,
  "categories": ["database", "storage", "ai", "api", "file"]
}
```

### 4.2 Get Tool Details

**GET** `/tools/{toolName}`

Retrieves detailed information about a specific tool.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `toolName` | string | Yes | Name of the tool |

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `server_id` | string | No | Specify server if tool exists on multiple servers |

#### Response

```json
{
  "name": "create_document",
  "description": "Creates a new document in MongoDB collection with validation",
  "server_id": "mongodb-primary",
  "server_name": "MongoDB Primary Server",
  "category": "database",
  "version": "1.2.0",
  "input_schema": {
    "type": "object",
    "properties": {
      "collection": {
        "type": "string",
        "description": "Target collection name",
        "pattern": "^[a-zA-Z][a-zA-Z0-9_]*$"
      },
      "document": {
        "type": "object",
        "description": "Document data to insert"
      },
      "options": {
        "type": "object",
        "properties": {
          "validate": {
            "type": "boolean",
            "default": true
          },
          "upsert": {
            "type": "boolean",
            "default": false
          }
        }
      }
    },
    "required": ["collection", "document"]
  },
  "output_schema": {
    "type": "object",
    "properties": {
      "success": {"type": "boolean"},
      "inserted_id": {"type": "string"},
      "acknowledged": {"type": "boolean"}
    }
  },
  "rate_limits": {
    "requests_per_minute": 100,
    "requests_per_hour": 1000
  },
  "examples": [
    {
      "name": "Create user",
      "description": "Creates a new user document",
      "input": {
        "collection": "users",
        "document": {
          "name": "John Doe",
          "email": "john@example.com",
          "created_at": "2025-09-01T19:10:00Z"
        }
      },
      "expected_output": {
        "success": true,
        "inserted_id": "64f5e2a8d1b2c3a4e5f6g7h8",
        "acknowledged": true
      }
    }
  ]
}
```

### 4.3 Execute Tool

**POST** `/tools/{toolName}/execute`

Executes a specific tool with the provided parameters.

#### Path Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `toolName` | string | Yes | Name of the tool to execute |

#### Request Body

```json
{
  "server_id": "mongodb-primary",
  "parameters": {
    "collection": "users",
    "document": {
      "name": "Jane Smith",
      "email": "jane@example.com",
      "department": "Engineering"
    },
    "options": {
      "validate": true
    }
  },
  "timeout": 10000,
  "idempotency_key": "unique-request-id-12345"
}
```

#### Response

```json
{
  "execution_id": "exec_64f5e2a8d1b2c3a4e5f6g7h8",
  "status": "completed",
  "result": {
    "success": true,
    "inserted_id": "64f5e2a8d1b2c3a4e5f6g7h9",
    "acknowledged": true,
    "metadata": {
      "collection_size": 1247,
      "execution_time_ms": 340
    }
  },
  "server_id": "mongodb-primary",
  "started_at": "2025-09-01T19:10:15Z",
  "completed_at": "2025-09-01T19:10:15.340Z",
  "duration_ms": 340
}
```

### 4.4 Batch Tool Execution

**POST** `/tools/batch`

Executes multiple tools in a single request, with optional dependency ordering.

#### Request Body

```json
{
  "executions": [
    {
      "tool_name": "create_document",
      "server_id": "mongodb-primary",
      "parameters": {
        "collection": "orders",
        "document": {"customer_id": 123, "amount": 99.99}
      },
      "execution_id": "create-order"
    },
    {
      "tool_name": "update_inventory",
      "server_id": "inventory-server",
      "parameters": {
        "product_id": 456,
        "quantity_delta": -1
      },
      "execution_id": "update-stock",
      "depends_on": ["create-order"]
    }
  ],
  "options": {
    "fail_fast": true,
    "max_parallel": 3
  }
}
```

#### Response

```json
{
  "batch_id": "batch_64f5e2a8d1b2c3a4e5f6g7h8",
  "status": "completed",
  "executions": [
    {
      "execution_id": "create-order",
      "status": "completed",
      "result": {
        "success": true,
        "inserted_id": "order_123"
      },
      "duration_ms": 245
    },
    {
      "execution_id": "update-stock",
      "status": "completed",
      "result": {
        "success": true,
        "new_quantity": 49
      },
      "duration_ms": 180
    }
  ],
  "started_at": "2025-09-01T19:12:00Z",
  "completed_at": "2025-09-01T19:12:00.425Z",
  "total_duration_ms": 425
}
```

---

## 5. Resource Management

### 5.1 List Resources

**GET** `/resources`

Lists available resources across all servers.

#### Request Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `server_id` | string | No | Filter resources by server |
| `type` | string | No | Filter by resource type |
| `uri_pattern` | string | No | Filter by URI pattern |

#### Response

```json
{
  "resources": [
    {
      "uri": "mongodb://collections/users",
      "name": "Users Collection",
      "description": "User account data collection",
      "mime_type": "application/json",
      "server_id": "mongodb-primary",
      "type": "collection",
      "size_bytes": 1024000,
      "last_modified": "2025-09-01T18:30:00Z",
      "permissions": ["read", "write", "delete"]
    }
  ],
  "total_count": 23
}
```

### 5.2 Get Resource Content

**GET** `/resources/content`

Retrieves the content of a specific resource.

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `uri` | string | Yes | Resource URI |
| `server_id` | string | No | Server ID if URI is ambiguous |
| `format` | string | No | Response format (`json`, `text`, `binary`) |

#### Response

```json
{
  "uri": "mongodb://collections/users",
  "content": {
    "schema": {
      "name": "string",
      "email": "string",
      "created_at": "datetime"
    },
    "sample_documents": [
      {
        "_id": "64f5e2a8d1b2c3a4e5f6g7h8",
        "name": "John Doe",
        "email": "john@example.com"
      }
    ]
  },
  "metadata": {
    "content_type": "application/json",
    "size_bytes": 2048,
    "last_modified": "2025-09-01T18:30:00Z"
  }
}
```

---

## 6. Health and Monitoring

### 6.1 System Health

**GET** `/health`

Returns the overall system health status.

#### Response

```json
{
  "status": "healthy",
  "version": "1.0.0",
  "uptime_seconds": 432000,
  "checks": {
    "database": {
      "status": "healthy",
      "response_time_ms": 12
    },
    "servers": {
      "status": "healthy",
      "active_count": 8,
      "total_count": 10
    },
    "memory": {
      "status": "healthy",
      "used_percent": 65.4
    }
  },
  "timestamp": "2025-09-01T19:10:00Z"
}
```

### 6.2 Server Health Check

**GET** `/servers/{serverId}/health`

Performs a health check on a specific server.

#### Response

```json
{
  "server_id": "mongodb-primary",
  "status": "healthy",
  "checks": [
    {
      "name": "connectivity",
      "status": "passing",
      "message": "Connection established successfully",
      "response_time_ms": 45,
      "last_check": "2025-09-01T19:09:30Z"
    },
    {
      "name": "authentication", 
      "status": "passing",
      "message": "Authentication successful",
      "last_check": "2025-09-01T19:09:30Z"
    },
    {
      "name": "capability_discovery",
      "status": "passing", 
      "message": "15 tools, 5 resources discovered",
      "last_check": "2025-09-01T19:09:30Z"
    }
  ]
}
```

### 6.3 Metrics

**GET** `/metrics`

Returns system and server metrics in Prometheus format.

#### Query Parameters

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `format` | string | No | Response format (`prometheus`, `json`) |
| `server_id` | string | No | Filter metrics by server |

#### Response (Prometheus Format)

```
# HELP mcp_client_requests_total Total number of requests
# TYPE mcp_client_requests_total counter
mcp_client_requests_total{server="mongodb-primary",method="tool_call"} 1247

# HELP mcp_client_request_duration_seconds Request duration in seconds
# TYPE mcp_client_request_duration_seconds histogram
mcp_client_request_duration_seconds_bucket{server="mongodb-primary",le="0.1"} 120
mcp_client_request_duration_seconds_bucket{server="mongodb-primary",le="0.5"} 890
mcp_client_request_duration_seconds_bucket{server="mongodb-primary",le="1.0"} 1200
mcp_client_request_duration_seconds_bucket{server="mongodb-primary",le="+Inf"} 1247

# HELP mcp_server_status Server status (1=active, 0=inactive)
# TYPE mcp_server_status gauge
mcp_server_status{server="mongodb-primary"} 1
mcp_server_status{server="redis-cache"} 1
```

---

## 7. Error Handling

### 7.1 Standard Error Response

All API errors follow a consistent format:

```json
{
  "error": {
    "code": "INVALID_PARAMETERS",
    "message": "The provided parameters are invalid",
    "details": "Parameter 'collection' is required but was not provided",
    "request_id": "req_64f5e2a8d1b2c3a4e5f6g7h8",
    "timestamp": "2025-09-01T19:10:00Z"
  }
}
```

### 7.2 HTTP Status Codes

| Code | Status | Description |
|------|--------|-------------|
| 200 | OK | Request successful |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid request parameters |
| 401 | Unauthorized | Authentication required |
| 403 | Forbidden | Access denied |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Resource conflict |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Server error |
| 502 | Bad Gateway | Upstream server error |
| 503 | Service Unavailable | Service temporarily unavailable |

### 7.3 Error Codes

| Code | Description |
|------|-------------|
| `AUTHENTICATION_FAILED` | Authentication credentials are invalid |
| `AUTHORIZATION_DENIED` | Access denied for the requested resource |
| `INVALID_PARAMETERS` | Request parameters are invalid |
| `RESOURCE_NOT_FOUND` | Requested resource does not exist |
| `SERVER_UNAVAILABLE` | Target server is not available |
| `TOOL_EXECUTION_FAILED` | Tool execution failed |
| `TIMEOUT_EXCEEDED` | Request timeout exceeded |
| `RATE_LIMIT_EXCEEDED` | Rate limit exceeded |
| `SERVER_ERROR` | Internal server error |

---

## 8. Rate Limiting

### 8.1 Rate Limit Headers

All responses include rate limiting information:

```http
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1693597200
X-RateLimit-Window: 3600
```

### 8.2 Rate Limit Tiers

| Tier | Requests/Hour | Requests/Minute | Burst Limit |
|------|---------------|-----------------|-------------|
| Free | 1,000 | 100 | 20 |
| Pro | 10,000 | 500 | 100 |
| Enterprise | 100,000 | 2,000 | 500 |

---

## 9. Webhooks

### 9.1 Webhook Events

The API supports webhooks for real-time notifications:

| Event | Description |
|-------|-------------|
| `server.connected` | Server successfully connected |
| `server.disconnected` | Server connection lost |
| `server.error` | Server encountered an error |
| `tool.executed` | Tool execution completed |
| `tool.failed` | Tool execution failed |

### 9.2 Webhook Configuration

**POST** `/webhooks`

```json
{
  "url": "https://your-app.com/webhooks/mcp",
  "events": ["server.connected", "tool.executed"],
  "secret": "your-webhook-secret",
  "active": true
}
```

---

## 10. SDKs and Libraries

### 10.1 Official SDKs

- **JavaScript/TypeScript**: `@globalmcp/client-sdk`
- **Python**: `globalmcp-client`
- **Java**: `com.globalmcp:client-sdk`
- **Go**: `github.com/globalmcp/client-go`
- **C#**: `GlobalMcp.Client`

### 10.2 Code Examples

#### JavaScript SDK

```javascript
import { GlobalMcpClient } from '@globalmcp/client-sdk';

const client = new GlobalMcpClient({
  apiKey: 'your-api-key',
  baseURL: 'https://api.globalmcp.com/v1'
});

// List available tools
const tools = await client.tools.list();

// Execute a tool
const result = await client.tools.execute('create_document', {
  server_id: 'mongodb-primary',
  parameters: {
    collection: 'users',
    document: { name: 'John Doe', email: 'john@example.com' }
  }
});
```

#### Python SDK

```python
from globalmcp_client import GlobalMcpClient

client = GlobalMcpClient(
    api_key='your-api-key',
    base_url='https://api.globalmcp.com/v1'
)

# List available tools
tools = client.tools.list()

# Execute a tool
result = client.tools.execute(
    'create_document',
    server_id='mongodb-primary',
    parameters={
        'collection': 'users',
        'document': {'name': 'John Doe', 'email': 'john@example.com'}
    }
)
```

---

**Document Control**

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2025-08-15 | Initial API documentation | API Team |
| 1.1 | 2025-08-30 | Added batch execution and webhooks | API Team |
| 1.2 | 2025-09-01 | Enhanced error handling and metrics | API Team |

**References**
- OpenAPI 3.0 Specification
- REST API Design Guidelines  
- MCP Protocol Documentation
- Enterprise Integration Patterns
