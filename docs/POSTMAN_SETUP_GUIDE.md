# MCP API Postman Collection Setup Guide

## 🚀 Quick Setup

### 1. Import the Collection
1. Open **Postman**
2. Click **Import** button
3. Select **File** and choose `docs/MCP_API_Postman_Collection.json`
4. The collection **"MCP Controller API - MongoDB Testing"** will be imported

### 2. Configure Environment Variables
The collection uses variables for easy configuration:

- **`base_url`**: `http://localhost:8081/api`
- **`server_id`**: `mongo-mcp-server-test`

These are already set in the collection, but you can modify them if needed.

### 3. Prerequisites Check
Before testing, ensure:
- ✅ **Global MCP Client** is running on port `8081`
- ✅ **MongoDB** is running on `localhost:27017`
- ✅ **MongoDB MCP Server** is configured and accessible

---

## 📁 Collection Structure

### 1. **Server Management** - Basic server operations
- List All MCP Servers
- Get Server Information
- Check Server Health
- Get Overall Health Status

### 2. **Tool Discovery** - Explore available tools
- List Server Tools
- Get All Tools from All Servers

### 3. **MongoDB Core Operations** - Basic database tests
- Ping Database
- List Databases
- Get Database Stats

### 4. **Database Management** - Database lifecycle
- Create Database
- Drop Database

### 5. **Collection Management** - Collection operations
- List Collections
- Create Collection
- Get Collection Stats
- Drop Collection

### 6. **Document Operations** - CRUD operations
- Insert Single Document
- Insert Multiple Documents
- Find Documents (simple & complex queries)
- Update Document
- Delete Document
- Count Documents

### 7. **Index Management** - Performance optimization
- List Indexes
- Create Index
- Create Compound Index
- Drop Index

### 8. **Advanced Operations** - Complex queries & analytics
- Simple Query
- Aggregation Pipeline (Count by Author)
- Aggregation Pipeline (Published Posts Stats)
- Bulk Operations

### 9. **Resource Management** - MCP resources
- List Server Resources
- Read Resource Content

### 10. **Advanced Communication** - Low-level MCP protocol
- Send Raw MCP Message
- Subscribe to Notifications (SSE)

---

## 🧪 Testing Workflow

### Basic Health Check
1. Run **"Check Server Health"** first
2. If healthy, run **"Ping Database"**
3. Run **"List Server Tools"** to see available operations

### Complete Database Workflow
Follow this order for a complete test:

1. **Create Database** → Creates `blog_db` with `posts` collection
2. **List Collections** → Verify collection creation
3. **Insert Single Document** → Add a blog post
4. **Insert Multiple Documents** → Add more posts
5. **Find Documents** → Query the data
6. **Count Documents** → Get document statistics
7. **Create Index** → Add performance indexes
8. **Update Document** → Modify existing data
9. **Aggregation Pipeline** → Run analytics queries
10. **Drop Collection/Database** → Clean up (optional)

---

## 🔧 Customization

### Modify Variables
You can change the base URL or server ID:
1. Right-click the collection
2. Select **Edit**
3. Go to **Variables** tab
4. Modify `base_url` or `server_id` as needed

### Add Custom Requests
You can add your own API requests:
1. Right-click any folder
2. Select **Add Request**
3. Configure the endpoint and parameters

---

## 📊 Expected Responses

### Successful Response Format
```json
{
  "content": [
    {
      "type": "text",
      "text": "Operation completed successfully"
    }
  ],
  "isError": false
}
```

### Error Response Format
```json
{
  "content": [
    {
      "type": "text",
      "text": "Error: Operation failed - details here"
    }
  ],
  "isError": true
}
```

---

## 🚨 Troubleshooting

### Common Issues

#### 1. Connection Refused
**Error**: `Could not connect to localhost:8081`
**Solution**: 
- Start the Global MCP Client application
- Verify it's running on port 8081

#### 2. Server Not Healthy
**Error**: `{"healthy": false}`
**Solution**:
- Check if MongoDB is running
- Verify MongoDB MCP Server configuration
- Check application logs for errors

#### 3. Tool Execution Failures
**Error**: Tool returns error in response
**Solution**:
- Verify MongoDB connection string
- Check database and collection names
- Validate JSON syntax in request body

#### 4. Empty Tool List
**Error**: Empty array returned from tools endpoint
**Solution**:
- Check if MCP server is properly initialized
- Verify server configuration in `application.yml`
- Check server logs for initialization errors

---

## 💡 Tips for Testing

1. **Start Simple**: Begin with health checks and ping operations
2. **Check Logs**: Monitor application logs for detailed error information
3. **Use Valid JSON**: Ensure all JSON payloads are properly formatted
4. **Sequential Testing**: Follow the logical workflow order
5. **Clean Up**: Drop test databases/collections after testing

---

## 📚 Additional Resources

- **API Documentation**: `docs/MCP_API_GUIDE.md`
- **Swagger UI**: `http://localhost:8081/swagger-ui.html`
- **Application Health**: `http://localhost:8081/actuator/health`

---

This Postman collection provides comprehensive testing capabilities for the MongoDB MCP Server through the Global MCP Client API. Happy testing! 🎉
