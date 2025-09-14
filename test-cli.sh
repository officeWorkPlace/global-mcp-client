#!/bin/bash
# CLI Testing Script for MCP Client

echo "🚀 MCP CLI Testing Script"
echo "========================"

JAR_FILE="target/global-mcp-client-1.0.0-SNAPSHOT.jar"

if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found. Please run: mvn package -DskipTests"
    exit 1
fi

echo ""
echo "1. Testing CLI Welcome Message"
echo "------------------------------"
java -jar $JAR_FILE

echo ""
echo "2. Testing Server List"
echo "----------------------"
java -jar $JAR_FILE server list

echo ""
echo "3. Testing Tool List"
echo "--------------------"
java -jar $JAR_FILE tool list mongo-mcp-server-test

echo ""
echo "4. Testing Server Health"
echo "------------------------"
java -jar $JAR_FILE server health mongo-mcp-server-test

echo ""
echo "5. Testing Quick Ping"
echo "---------------------"
java -jar $JAR_FILE tool quick ping --server mongo-mcp-server-test

echo ""
echo "6. Testing Database List"
echo "------------------------"
java -jar $JAR_FILE tool exec mongo-mcp-server-test listDatabases

echo ""
echo "✅ All tests completed!"
