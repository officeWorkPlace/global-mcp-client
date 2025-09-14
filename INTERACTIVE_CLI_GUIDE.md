# 🚀 MCP Interactive CLI Testing Guide

## How to Use Interactive Mode

1. **Start the Interactive Shell:**
   ```powershell
   java -jar target\global-mcp-client-1.0.0-SNAPSHOT.jar shell
   ```

2. **You'll see a prompt like this:**
   ```
   🚀 mcpcli> 
   ```

3. **Type commands without the 'java -jar' prefix:**
   ```
   🚀 mcpcli> server list
   🚀 mcpcli> tool list mongo-mcp-server-test
   🚀 mcpcli> tool exec mongo-mcp-server-test ping
   🚀 mcpcli> tool quick databases
   ```

## Available Commands in Interactive Mode

### Server Management:
- `server list` - List all servers
- `server info mongo-mcp-server-test` - Server details
- `server health mongo-mcp-server-test` - Health check
- `server status mongo-mcp-server-test` - Status with tools

### Tool Operations:
- `tool list mongo-mcp-server-test` - List all tools
- `tool all` - All tools from all servers
- `tool exec mongo-mcp-server-test ping` - Execute ping tool
- `tool exec mongo-mcp-server-test listDatabases` - List databases
- `tool quick ping` - Quick ping test
- `tool quick databases` - Quick database list

### Special Commands:
- `help` - Show available commands
- `clear` - Clear screen
- `exit` - Exit shell

## Example Interactive Session:

```
🚀 mcpcli> server list
✅ Found 1 server(s):
  📦 mongo-mcp-server-test - MongoDB Database Server (✅ Healthy)

🚀 mcpcli> tool list mongo-mcp-server-test
✅ Tools for mongo-mcp-server-test (39 total):
  🛠️  ping - Test server connectivity
  🛠️  listDatabases - List all databases
  🛠️  createDatabase - Create a new database
  ... (and 36 more tools)

🚀 mcpcli> tool exec mongo-mcp-server-test ping
ℹ️ Executing ping on mongo-mcp-server-test...
✅ Tool executed successfully!
Pong! MongoDB server is responding.

🚀 mcpcli> tool quick databases
ℹ️ Using server: mongo-mcp-server-test
✅ Database list retrieved:
[List of your databases...]

🚀 mcpcli> exit
✅ Goodbye! 👋
```

## Quick Start Commands for Testing:

Once you start the shell, try these commands one by one:

1. `server list`
2. `tool list mongo-mcp-server-test`
3. `tool exec mongo-mcp-server-test ping`
4. `tool quick databases`
5. `help`
6. `exit`
