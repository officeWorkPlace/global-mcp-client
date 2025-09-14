package com.deepai.mcpclient.cli.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for formatting output in different formats (table, JSON, YAML)
 * Makes data easy to read for normal users
 */
@Component
public class TableFormatter {
    
    private static final Logger logger = LoggerFactory.getLogger(TableFormatter.class);
    
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final YAMLMapper yamlMapper = new YAMLMapper();
    private final ColorOutput colorOutput = new ColorOutput();
    
    /**
     * Format data based on the specified format
     */
    public void printFormatted(Object data, String format) {
        try {
            switch (format.toLowerCase()) {
                case "json":
                    System.out.println(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data));
                    break;
                case "yaml":
                    System.out.println(yamlMapper.writeValueAsString(data));
                    break;
                case "table":
                default:
                    printTable(data);
                    break;
            }
        } catch (Exception e) {
            logger.error("Error formatting output: {}", e.getMessage(), e);
            // Fallback to simple string representation for user
            System.out.println(data.toString());
        }
    }
    
    /**
     * Print data as a formatted table
     */
    @SuppressWarnings("unchecked")
    private void printTable(Object data) {
        if (data instanceof List) {
            List<?> list = (List<?>) data;
            if (list.isEmpty()) {
                System.out.println(colorOutput.yellow("No data to display"));
                return;
            }
            
            if (list.get(0) instanceof Map) {
                printMapTable((List<Map<String, Object>>) data);
            } else {
                printSimpleList(list);
            }
        } else if (data instanceof Map) {
            printSingleMap((Map<String, Object>) data);
        } else {
            System.out.println(data.toString());
        }
    }
    
    /**
     * Print a table from a list of maps
     */
    private void printMapTable(List<Map<String, Object>> data) {
        if (data.isEmpty()) return;
        
        // Get all unique keys
        List<String> headers = new ArrayList<>();
        for (Map<String, Object> row : data) {
            for (String key : row.keySet()) {
                if (!headers.contains(key)) {
                    headers.add(key);
                }
            }
        }
        
        // Calculate column widths
        int[] widths = new int[headers.size()];
        for (int i = 0; i < headers.size(); i++) {
            widths[i] = headers.get(i).length();
        }
        
        for (Map<String, Object> row : data) {
            for (int i = 0; i < headers.size(); i++) {
                Object value = row.get(headers.get(i));
                String strValue = value != null ? value.toString() : "";
                widths[i] = Math.max(widths[i], strValue.length());
            }
        }
        
        // Print header
        printTableRow(headers.toArray(new String[0]), widths, true);
        printTableSeparator(widths);
        
        // Print data rows
        for (Map<String, Object> row : data) {
            String[] values = new String[headers.size()];
            for (int i = 0; i < headers.size(); i++) {
                Object value = row.get(headers.get(i));
                values[i] = value != null ? value.toString() : "";
            }
            printTableRow(values, widths, false);
        }
    }
    
    /**
     * Print a single map as key-value pairs
     */
    private void printSingleMap(Map<String, Object> data) {
        int maxKeyLength = data.keySet().stream().mapToInt(String::length).max().orElse(0);
        
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = String.format("%-" + maxKeyLength + "s", entry.getKey());
            String value = entry.getValue() != null ? entry.getValue().toString() : "null";
            
            // Color-code the value based on key or content
            if (key.toLowerCase().contains("status") || key.toLowerCase().contains("health")) {
                value = colorOutput.status(value);
            } else if (key.toLowerCase().contains("error")) {
                value = colorOutput.red(value);
            }
            
            System.out.println(colorOutput.cyan(key) + " : " + value);
        }
    }
    
    /**
     * Print a simple list
     */
    private void printSimpleList(List<?> data) {
        for (Object item : data) {
            System.out.println("• " + item.toString());
        }
    }
    
    /**
     * Print a table row
     */
    private void printTableRow(String[] values, int[] widths, boolean isHeader) {
        StringBuilder row = new StringBuilder("│");
        
        for (int i = 0; i < values.length; i++) {
            String value = values[i];
            String formatted = String.format(" %-" + widths[i] + "s ", value);
            
            if (isHeader) {
                formatted = colorOutput.bold(colorOutput.cyan(formatted));
            }
            
            row.append(formatted).append("│");
        }
        
        System.out.println(row.toString());
    }
    
    /**
     * Print table separator
     */
    private void printTableSeparator(int[] widths) {
        StringBuilder separator = new StringBuilder("├");
        
        for (int i = 0; i < widths.length; i++) {
            separator.append("─".repeat(widths[i] + 2));
            if (i < widths.length - 1) {
                separator.append("┼");
            }
        }
        
        separator.append("┤");
        System.out.println(colorOutput.gray(separator.toString()));
    }
}
