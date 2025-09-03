package com.deepai.mcpclient.service;

import com.deepai.mcpclient.model.McpToolResult;
import com.deepai.mcpclient.model.McpContent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for ResponseFormatter to verify conversion of various 
 * MCP tool result formats into human-readable responses
 */
public class ResponseFormatterTest {

    private ResponseFormatter responseFormatter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        responseFormatter = new ResponseFormatter(objectMapper);
    }

    @Test
    void testFormatJsonToolResult() throws Exception {
        // Given: Tool result with JSON data
        Map<String, Object> jsonData = Map.of(
            "databases", List.of(
                Map.of("name", "testdb", "collections", 5),
                Map.of("name", "userdb", "collections", 12),
                Map.of("name", "analytics", "collections", 8)
            ),
            "total", 3
        );
        
        String jsonString = objectMapper.writeValueAsString(jsonData);
        McpToolResult toolResult = new McpToolResult(
            List.of(McpContent.text(jsonString)),
            false
        );
        
        // When: Format the result
        String formatted = responseFormatter.formatToolResult("listDatabases", toolResult);
        
        // Then: Verify human-readable formatting
        assertThat(formatted).contains("List Databases Found");
        assertThat(formatted).contains("testdb");
        assertThat(formatted).contains("userdb");
        assertThat(formatted).contains("analytics");
        assertThat(formatted).contains("Databases: [");
        assertThat(formatted).contains("Total: 3");
    }

    @Test
    void testFormatPlainTextToolResult() {
        // Given: Tool result with plain text
        McpToolResult toolResult = new McpToolResult(
            List.of(
                McpContent.text("Server is healthy"), 
                McpContent.text("Response time: 45ms"), 
                McpContent.text("Connection: stable")
            ),
            false
        );
        
        // When: Format the result
        String formatted = responseFormatter.formatToolResult("pingServer", toolResult);
        
        // Then: Verify formatting maintains readability
        assertThat(formatted).contains("Ping Server Results");
        assertThat(formatted).contains("Server is healthy");
        assertThat(formatted).contains("Response time: 45ms");
        assertThat(formatted).contains("Connection: stable");
        assertThat(formatted).doesNotContain("null");
    }

    @Test
    void testFormatCsvToolResult() {
        // Given: Tool result with CSV data
        String csvData = "name,age,city\nJohn,25,New York\nJane,30,Los Angeles\nBob,35,Chicago";
        McpToolResult toolResult = new McpToolResult(
            List.of(new McpContent("text/csv", csvData, null, "text/csv")),
            false
        );
        
        // When: Format the result
        String formatted = responseFormatter.formatToolResult("exportUsers", toolResult);
        
        // Then: Verify CSV is formatted nicely
        assertThat(formatted).contains("Export Users Results");
        assertThat(formatted).contains("Data table");
        assertThat(formatted).contains("name,age,city");
        assertThat(formatted).contains("John,25,New York");
        assertThat(formatted).contains("Jane,30,Los Angeles");
        assertThat(formatted).contains("Bob,35,Chicago");
    }

    @Test
    void testFormatXmlToolResult() {
        // Given: Tool result with XML data
        String xmlData = """
            <files>
                <file name="document.txt" size="1024" />
                <file name="image.jpg" size="2048" />
                <file name="script.sh" size="512" />
            </files>
            """;
        
        McpToolResult toolResult = new McpToolResult(
            List.of(new McpContent("application/xml", xmlData, null, "application/xml")),
            false
        );
        
        // When: Format the result
        String formatted = responseFormatter.formatToolResult("listFiles", toolResult);
        
        // Then: Verify XML is formatted for readability
        assertThat(formatted).contains("List Files Results");
        assertThat(formatted).contains("XML Response:");
        assertThat(formatted).contains("document.txt");
        assertThat(formatted).contains("image.jpg");
        assertThat(formatted).contains("script.sh");
        assertThat(formatted).contains("1024");
        assertThat(formatted).contains("2048");
        assertThat(formatted).contains("512");
    }

    @Test
    void testFormatErrorResult() {
        // Given: Tool result with error
        McpToolResult errorResult = new McpToolResult(
            List.of(
                McpContent.error("Database connection failed"), 
                McpContent.error("Error code: 1002"), 
                McpContent.error("Timeout after 30 seconds")
            ),
            true
        );
        
        // When: Format the error result
        String formatted = responseFormatter.formatToolResult("connectDatabase", errorResult);
        
        // Then: Verify error is clearly indicated
        assertThat(formatted).contains("Tool 'connectDatabase' failed:");
        assertThat(formatted).contains("Database connection failed");
        assertThat(formatted).contains("Error code: 1002");
        assertThat(formatted).contains("Timeout after 30 seconds");
    }

    @Test
    void testFormatEmptyResult() {
        // Given: Tool result with no content
        McpToolResult emptyResult = new McpToolResult(
            List.of(),
            false
        );
        
        // When: Format the empty result
        String formatted = responseFormatter.formatToolResult("emptyQuery", emptyResult);
        
        // Then: Verify appropriate message for empty result
        assertThat(formatted).contains("No results found");
        assertThat(formatted).contains("emptyQuery");
    }

    @Test
    void testFormatLargeJsonArray() throws Exception {
        // Given: Large JSON array result
        List<Map<String, Object>> largeArray = List.of(
            Map.of("id", 1, "name", "Item 1", "status", "active"),
            Map.of("id", 2, "name", "Item 2", "status", "inactive"),
            Map.of("id", 3, "name", "Item 3", "status", "active"),
            Map.of("id", 4, "name", "Item 4", "status", "active"),
            Map.of("id", 5, "name", "Item 5", "status", "inactive")
        );
        
        McpToolResult toolResult = new McpToolResult(
            List.of(McpContent.json(largeArray)),
            false
        );
        
        // When: Format the large array
        String formatted = responseFormatter.formatToolResult("findItems", toolResult);
        
        // Then: Verify array is formatted as a table
        assertThat(formatted).contains("Find Items Results");
        assertThat(formatted).contains("Found 5 results:");
        assertThat(formatted).contains("Item 1");
        assertThat(formatted).contains("Item 5");
        assertThat(formatted).contains("active");
        assertThat(formatted).contains("inactive");
        assertThat(formatted).contains("Item 1");
        assertThat(formatted).contains("Item 5");
        assertThat(formatted).contains("active");
        assertThat(formatted).contains("inactive");
    }

    @Test
    void testFormatComplexNestedJson() throws Exception {
        // Given: Complex nested JSON structure
        Map<String, Object> complexData = Map.of(
            "server", Map.of(
                "name", "prod-db-01",
                "status", "running",
                "uptime", "15 days"
            ),
            "connections", List.of(
                Map.of("client", "app-01", "active", true),
                Map.of("client", "app-02", "active", false)
            ),
            "metrics", Map.of(
                "cpu", "45%",
                "memory", "2.1GB",
                "disk", "78%"
            )
        );
        
        McpToolResult toolResult = new McpToolResult(
            List.of(McpContent.json(complexData)),
            false
        );
        
        // When: Format the complex result
        String formatted = responseFormatter.formatToolResult("serverStatus", toolResult);
        
        // Then: Verify complex data is well-structured
        assertThat(formatted).contains("Server Status Results");
        assertThat(formatted).contains("prod-db-01");
        assertThat(formatted).contains("running");
        assertThat(formatted).contains("app-01");
    }

    @Test
    void testFormatMultipleContentItems() {
        // Given: Tool result with multiple content items
        McpToolResult multiResult = new McpToolResult(
            List.of(
                McpContent.text("First result: Operation successful"),
                McpContent.text("Second result: 15 items processed"),
                McpContent.text("Third result: Completed in 1.2s")
            ),
            false
        );
        
        // When: Format multiple content items
        String formatted = responseFormatter.formatToolResult("batchProcess", multiResult);
        
        // Then: Verify all items are included
        assertThat(formatted).contains("Batch Process Results");
        assertThat(formatted).contains("Operation successful");
        assertThat(formatted).contains("15 items processed");
        assertThat(formatted).contains("Completed in 1.2s");
    }

    @Test
    void testFormatUnknownContentType() {
        // Given: Tool result with unknown content type
        McpToolResult unknownResult = new McpToolResult(
            List.of(new McpContent("application/octet-stream", "Some binary data or unknown format", null, "application/octet-stream")),
            false
        );
        
        // When: Format unknown content type
        String formatted = responseFormatter.formatToolResult("unknownTool", unknownResult);
        
        // Then: Verify fallback formatting
        assertThat(formatted).contains("Unknown Tool Results");
        assertThat(formatted).contains("Some binary data or unknown format");
        assertThat(formatted).doesNotContain("Content type: application/octet-stream");
    }

    @Test
    void testFormatInvalidJson() {
        // Given: Tool result with malformed JSON
        McpToolResult invalidJsonResult = new McpToolResult(
            List.of(new McpContent("application/json", "{invalid json data}", null, "application/json")),
            false
        );
        
        // When: Format invalid JSON
        String formatted = responseFormatter.formatToolResult("parseJson", invalidJsonResult);
        
        // Then: Verify graceful handling of invalid JSON
        assertThat(formatted).contains("Parse Json Results");
        assertThat(formatted).contains("{invalid json data}");
        // Should not crash, should handle gracefully
        assertThat(formatted).isNotEmpty();
    }

    @Test
    void testFormatResultWithSpecialCharacters() {
        // Given: Tool result with special characters and Unicode
        McpToolResult specialResult = new McpToolResult(
            List.of(
                McpContent.text("Result with émojis 🚀✨ and special chars: <>\"'&"), 
                McpContent.text("Unicode test: 中文 العربية русский")
            ),
            false
        );
        
        // When: Format result with special characters
        String formatted = responseFormatter.formatToolResult("unicodeTest", specialResult);
        
        // Then: Verify special characters are preserved
        assertThat(formatted).contains("🚀✨");
        assertThat(formatted).contains("<>\"'&");
        assertThat(formatted).contains("中文");
        assertThat(formatted).contains("العربية");
        assertThat(formatted).contains("русский");
    }

    @Test
    void testFormatResultWithLongText() {
        // Given: Tool result with very long text
        String longText = "A".repeat(5000) + " This is a very long text that should be handled properly";
        McpToolResult longResult = new McpToolResult(
            List.of(McpContent.text(longText)),
            false
        );
        
        // When: Format long text result
        String formatted = responseFormatter.formatToolResult("longTextTool", longResult);
        
        // Then: Verify long text is handled (may be truncated for display)
        assertThat(formatted).contains("Long Text Tool Results");
        assertThat(formatted).isNotEmpty();
        // Should handle gracefully without errors
    }

    @Test
    void testFormatSuccessMessage() {
        // Given: Simple success message
        McpToolResult successResult = new McpToolResult(
            List.of(McpContent.text("Operation completed successfully")),
            false
        );
        
        // When: Format success result
        String formatted = responseFormatter.formatToolResult("deleteFile", successResult);
        
        // Then: Verify success indication
        assertThat(formatted).contains("Delete File Results");
        assertThat(formatted).contains("Operation completed successfully");
    }

    @Test 
    void testFormatDifferentToolNames() {
        // Given: Various tool names
        McpToolResult result = new McpToolResult(
            List.of(McpContent.text("Test result")),
            false
        );
        
        // When/Then: Test different tool name formatting
        String camelCase = responseFormatter.formatToolResult("getUserInfo", result);
        assertThat(camelCase).contains("Get User Info Results");
        
        String snakeCase = responseFormatter.formatToolResult("list_all_files", result);  
        assertThat(snakeCase).contains("List All Files Results");
        
        String kebabCase = responseFormatter.formatToolResult("check-server-status", result);
        assertThat(kebabCase).contains("Check Server Status Results");
    }
}
