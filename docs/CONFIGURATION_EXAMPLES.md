# Global MCP Client - Configuration Examples
## Spring AI MCP Client Integration Patterns

**Document Version:** 2.0  
**Date:** 2025-09-02  
**Classification:** Configuration Guide  
**Project:** Global MCP Client v1.0.0-SNAPSHOT

---

## 1. Overview

This document provides working configuration examples for the Global MCP Client, demonstrating how to integrate with multiple MCP servers using Spring AI MCP Client technology. All examples are based on the current implementation and tested configurations.

---

## 2. Current Working Configuration

### 2.1 application-improved.yml (Current Implementation)

```yaml
server:
  port: 8082
  servlet:
    context-path: /

spring:
  application:
    name: global-mcp-client
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
  web:
    cors:
      allowed-origins: "*"
      allowed-methods: "*"
      allowed-headers: "*"

logging:
  level:
    com.deepai: DEBUG
    org.springframework.web: INFO
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
  metrics:
    export:
      prometheus:
        enabled: true

mcp:
  client:
    default-timeout: 15000  # 15 seconds
    retry:
      max-attempts: 3
      backoff-multiplier: 1.5
  servers:
    mongo-mcp-server-test:
      type: stdio
      command: "java"
      args:
        - "-Dspring.profiles.active=mcp"
        - "-Dspring.main.web-application-type=none"
        - "-jar"
        - 'D:\\MCP\\MCP-workspace-bootcampToProd\\spring-boot-ai-mongo-mcp-server\\target\\spring-boot-ai-mongo-mcp-server-0.0.1-SNAPSHOT.jar'
      timeout: 20000  # 20 seconds
      enabled: true
      environment:
        SPRING_DATA_MONGODB_URI: "mongodb://localhost:27017/mcpserver"
        MONGO_DATABASE: "mcpserver"
```

### 2.2 Key Configuration Points

- **Port 8082**: Application runs on port 8082 (not default 8080)
- **Spring AI Integration**: Uses Spring AI MCP server with Spring profiles
- **Smart Detection**: Automatically detects Spring AI vs standard stdio servers
- **Comprehensive Monitoring**: Health, metrics, and Prometheus endpoints enabled
- **CORS Enabled**: Allows cross-origin requests for web clients
- **Debug Logging**: Enhanced logging for troubleshooting

---

## 3. Enterprise Multi-Domain Configuration

### 2.1 Complete Enterprise Setup

```yaml
# application-production.yml
mcp:
  client:
    default-timeout: 10000
    retry:
      max-attempts: 3
      backoff-multiplier: 1.5
  
  servers:
    # Database Layer
    mongodb-primary:
      type: stdio
      command: "java"
      args:
        - "-Xmx512m"
        - "-Dspring.profiles.active=mcp"
        - "-jar"
        - "/opt/servers/mongo-mcp-server.jar"
      timeout: 15000
      enabled: true
      environment:
        MONGODB_URI: "${MONGODB_PRIMARY_URI}"
        MONGODB_DATABASE: "production"
        LOG_LEVEL: "INFO"
    
    postgres-analytics:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "postgres_mcp_server"
        - "--config"
        - "/etc/postgres-mcp/config.json"
      timeout: 12000
      enabled: true
      environment:
        POSTGRES_URL: "${POSTGRES_ANALYTICS_URL}"
        POSTGRES_SCHEMA: "analytics"
        POOL_SIZE: "10"
    
    redis-cache:
      type: stdio
      command: "/usr/local/bin/redis-mcp-server"
      args:
        - "--host"
        - "${REDIS_HOST}"
        - "--port"
        - "${REDIS_PORT}"
      timeout: 8000
      enabled: true
      environment:
        REDIS_PASSWORD: "${REDIS_PASSWORD}"
        REDIS_DB: "0"
    
    # Storage Layer
    s3-documents:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "aws_s3_mcp_server"
      timeout: 20000
      enabled: true
      environment:
        AWS_REGION: "${AWS_REGION}"
        S3_BUCKET: "${DOCUMENTS_BUCKET}"
        AWS_ACCESS_KEY_ID: "${AWS_ACCESS_KEY_ID}"
        AWS_SECRET_ACCESS_KEY: "${AWS_SECRET_ACCESS_KEY}"
    
    filesystem-reports:
      type: stdio
      command: "node"
      args:
        - "/opt/servers/filesystem-mcp-server/index.js"
      timeout: 10000
      enabled: true
      environment:
        ROOT_PATH: "/var/data/reports"
        PERMISSIONS: "read-write"
        MAX_FILE_SIZE: "100MB"
    
    # AI/ML Services
    openai-assistant:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "openai_mcp_server"
        - "--model"
        - "gpt-4o"
      timeout: 30000
      enabled: true
      environment:
        OPENAI_API_KEY: "${OPENAI_API_KEY}"
        OPENAI_ORG_ID: "${OPENAI_ORG_ID}"
        REQUEST_TIMEOUT: "25"
    
    huggingface-models:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "huggingface_mcp_server"
      timeout: 25000
      enabled: true
      environment:
        HF_TOKEN: "${HUGGINGFACE_TOKEN}"
        MODEL_CACHE_DIR: "/var/cache/huggingface"
        DEVICE: "cuda"
    
    # Business Applications
    salesforce-crm:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "salesforce_mcp_server"
      timeout: 15000
      enabled: true
      environment:
        SALESFORCE_USERNAME: "${SF_USERNAME}"
        SALESFORCE_PASSWORD: "${SF_PASSWORD}"
        SALESFORCE_SECURITY_TOKEN: "${SF_SECURITY_TOKEN}"
        SALESFORCE_SANDBOX: "false"
    
    slack-integration:
      type: stdio
      command: "/opt/servers/slack-mcp-server"
      args:
        - "--workspace"
        - "${SLACK_WORKSPACE}"
      timeout: 12000
      enabled: true
      environment:
        SLACK_BOT_TOKEN: "${SLACK_BOT_TOKEN}"
        SLACK_APP_TOKEN: "${SLACK_APP_TOKEN}"
    
    # Development Tools
    github-repos:
      type: stdio
      command: "node"
      args:
        - "/opt/servers/github-mcp-server/dist/index.js"
      timeout: 15000
      enabled: true
      environment:
        GITHUB_TOKEN: "${GITHUB_TOKEN}"
        GITHUB_ORG: "${GITHUB_ORGANIZATION}"
    
    jira-projects:
      type: stdio
      command: "java"
      args:
        - "-jar"
        - "/opt/servers/jira-mcp-server.jar"
      timeout: 12000
      enabled: true
      environment:
        JIRA_URL: "${JIRA_BASE_URL}"
        JIRA_USERNAME: "${JIRA_USERNAME}"
        JIRA_API_TOKEN: "${JIRA_API_TOKEN}"
```

---

## 3. Development Environment Configuration

### 3.1 Local Development Setup

```yaml
# application-development.yml
spring:
  profiles:
    active: development

mcp:
  client:
    default-timeout: 5000
    retry:
      max-attempts: 2
      backoff-multiplier: 1.2
  
  servers:
    # Local MongoDB for development
    mongo-local:
      type: stdio
      command: "java"
      args:
        - "-Dspring.profiles.active=mcp,dev"
        - "-jar"
        - "target/mongo-mcp-server-dev.jar"
      timeout: 8000
      enabled: true
      environment:
        MONGODB_URI: "mongodb://localhost:27017/development"
        LOG_LEVEL: "DEBUG"
    
    # Local file system for testing
    filesystem-local:
      type: stdio
      command: "node"
      args:
        - "servers/filesystem/index.js"
        - "--dev"
      timeout: 5000
      enabled: true
      environment:
        ROOT_PATH: "./test-data"
        PERMISSIONS: "read-write"
    
    # Mock AI service for development
    mock-ai-service:
      type: stdio
      command: "python"
      args:
        - "servers/mock_ai_server.py"
        - "--responses"
        - "test-responses.json"
      timeout: 3000
      enabled: true
      environment:
        MOCK_MODE: "true"
        RESPONSE_DELAY: "100"
```

---

## 4. Cloud-Native Configuration

### 4.1 Kubernetes Environment

```yaml
# application-kubernetes.yml
mcp:
  client:
    default-timeout: 15000
    retry:
      max-attempts: 3
      backoff-multiplier: 2.0
  
  servers:
    # Cloud databases via sidecar containers
    mongodb-atlas:
      type: stdio
      command: "java"
      args:
        - "-Xmx1g"
        - "-XX:+UseG1GC"
        - "-jar"
        - "/app/mongo-mcp-server.jar"
      timeout: 20000
      enabled: true
      environment:
        MONGODB_URI: "${ATLAS_CONNECTION_STRING}"
        MONGODB_SSL: "true"
        MONGODB_AUTH_SOURCE: "admin"
    
    # Cloud storage services
    gcs-storage:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "google_cloud_mcp_server"
      timeout: 25000
      enabled: true
      environment:
        GOOGLE_APPLICATION_CREDENTIALS: "/var/secrets/gcp-key.json"
        GCS_BUCKET: "${GCS_BUCKET_NAME}"
        GCS_PROJECT: "${GCP_PROJECT_ID}"
    
    # Managed AI services
    azure-openai:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "azure_openai_mcp_server"
      timeout: 30000
      enabled: true
      environment:
        AZURE_OPENAI_ENDPOINT: "${AZURE_OPENAI_ENDPOINT}"
        AZURE_OPENAI_KEY: "${AZURE_OPENAI_KEY}"
        AZURE_OPENAI_VERSION: "2024-02-01"
```

---

## 5. High-Availability Configuration

### 5.1 Multi-Region Setup

```yaml
# application-ha.yml
mcp:
  client:
    default-timeout: 8000
    retry:
      max-attempts: 5
      backoff-multiplier: 1.8
  
  servers:
    # Primary region servers
    mongodb-primary-us-east:
      type: stdio
      command: "java"
      args:
        - "-jar"
        - "/opt/servers/mongo-mcp-server.jar"
      timeout: 12000
      enabled: true
      environment:
        MONGODB_URI: "${MONGODB_PRIMARY_URI}"
        REGION: "us-east-1"
        CLUSTER_ROLE: "primary"
    
    # Secondary region servers (read-only)
    mongodb-replica-us-west:
      type: stdio
      command: "java"
      args:
        - "-jar"
        - "/opt/servers/mongo-mcp-server.jar"
      timeout: 12000
      enabled: true
      environment:
        MONGODB_URI: "${MONGODB_REPLICA_URI}"
        REGION: "us-west-2"
        CLUSTER_ROLE: "replica"
        READ_PREFERENCE: "secondary"
    
    # Load-balanced AI services
    openai-loadbalanced:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "openai_lb_mcp_server"
        - "--endpoints"
        - "${OPENAI_ENDPOINTS}"
      timeout: 35000
      enabled: true
      environment:
        OPENAI_KEYS: "${OPENAI_API_KEYS}"
        LOAD_BALANCER_STRATEGY: "round-robin"
        FAILOVER_ENABLED: "true"
```

---

## 6. Security-Hardened Configuration

### 6.1 Enterprise Security Setup

```yaml
# application-secure.yml
mcp:
  client:
    default-timeout: 10000
    retry:
      max-attempts: 3
      backoff-multiplier: 1.5
  
  servers:
    # Encrypted database connections
    mongodb-encrypted:
      type: stdio
      command: "java"
      args:
        - "-Djava.security.policy=/etc/security/mcp.policy"
        - "-Djavax.net.ssl.trustStore=/etc/certs/truststore.jks"
        - "-jar"
        - "/opt/servers/mongo-mcp-server.jar"
      timeout: 15000
      enabled: true
      environment:
        MONGODB_URI: "${ENCRYPTED_MONGODB_URI}"
        SSL_ENABLED: "true"
        SSL_CERT_PATH: "/etc/certs/mongodb-client.pem"
        AUTH_MECHANISM: "SCRAM-SHA-256"
    
    # Vault-integrated secrets
    vault-secrets:
      type: stdio
      command: "python"
      args:
        - "-m"
        - "vault_mcp_server"
        - "--auth-method"
        - "kubernetes"
      timeout: 10000
      enabled: true
      environment:
        VAULT_ADDR: "${VAULT_ADDRESS}"
        VAULT_ROLE: "${VAULT_K8S_ROLE}"
        VAULT_NAMESPACE: "${VAULT_NAMESPACE}"
    
    # Network-isolated services
    internal-api:
      type: stdio
      command: "/usr/local/bin/internal-api-mcp-server"
      args:
        - "--network"
        - "internal-only"
        - "--mTLS"
        - "required"
      timeout: 12000
      enabled: true
      environment:
        CLIENT_CERT: "/etc/tls/client.crt"
        CLIENT_KEY: "/etc/tls/client.key"
        CA_CERT: "/etc/tls/ca.crt"
```

---

## 7. Performance-Optimized Configuration

### 7.1 High-Throughput Setup

```yaml
# application-performance.yml
mcp:
  client:
    default-timeout: 30000
    retry:
      max-attempts: 2
      backoff-multiplier: 1.2
  
  servers:
    # High-performance database
    mongodb-performance:
      type: stdio
      command: "java"
      args:
        - "-Xmx2g"
        - "-XX:+UseZGC"
        - "-XX:+UseTransparentHugePages"
        - "-jar"
        - "/opt/servers/mongo-mcp-server.jar"
      timeout: 45000
      enabled: true
      environment:
        MONGODB_URI: "${HIGH_PERF_MONGODB_URI}"
        CONNECTION_POOL_SIZE: "50"
        MAX_IDLE_TIME: "60000"
        WRITE_CONCERN: "majority"
    
    # Cached data layer
    redis-cluster:
      type: stdio
      command: "/usr/local/bin/redis-cluster-mcp-server"
      args:
        - "--cluster-nodes"
        - "${REDIS_CLUSTER_NODES}"
        - "--pool-size"
        - "20"
      timeout: 15000
      enabled: true
      environment:
        REDIS_CLUSTER_MODE: "true"
        REDIS_TIMEOUT: "5000"
        REDIS_RETRY_ATTEMPTS: "3"
    
    # Async processing
    kafka-streams:
      type: stdio
      command: "java"
      args:
        - "-Xmx1g"
        - "-XX:+UseParallelGC"
        - "-jar"
        - "/opt/servers/kafka-mcp-server.jar"
      timeout: 20000
      enabled: true
      environment:
        KAFKA_BOOTSTRAP_SERVERS: "${KAFKA_BROKERS}"
        KAFKA_ACKS: "all"
        KAFKA_RETRIES: "3"
        KAFKA_BATCH_SIZE: "16384"
```

---

## 8. Environment-Specific Profiles

### 8.1 Profile-Based Configuration

```yaml
# application.yml (base configuration)
mcp:
  client:
    default-timeout: 5000
    retry:
      max-attempts: 2
      backoff-multiplier: 1.2

---
# Development profile
spring.config.activate.on-profile: development
mcp:
  servers:
    mongo-dev:
      type: stdio
      command: "java"
      args: ["-jar", "mongo-mcp-server-dev.jar"]
      enabled: true
      environment:
        MONGODB_URI: "mongodb://localhost:27017/dev"

---
# Testing profile
spring.config.activate.on-profile: testing
mcp:
  servers:
    mongo-test:
      type: stdio
      command: "java"
      args: ["-jar", "mongo-mcp-server-test.jar"]
      enabled: true
      environment:
        MONGODB_URI: "mongodb://testdb:27017/test"

---
# Production profile
spring.config.activate.on-profile: production
mcp:
  client:
    default-timeout: 15000
    retry:
      max-attempts: 3
  servers:
    mongo-prod:
      type: stdio
      command: "java"
      args: ["-Xmx1g", "-jar", "mongo-mcp-server-prod.jar"]
      enabled: true
      environment:
        MONGODB_URI: "${PROD_MONGODB_URI}"
```

---

## 9. Configuration Best Practices

### 9.1 Security Guidelines

```yaml
# Security-focused configuration practices
mcp:
  servers:
    secure-server:
      environment:
        # ✅ Use environment variable references
        API_KEY: "${SECURE_API_KEY}"
        # ❌ Never hardcode secrets
        # API_KEY: "sk-1234567890abcdef"
        
        # ✅ Use secure protocols
        DATABASE_URL: "postgresql://secure-host:5432/db?sslmode=require"
        
        # ✅ Enable security features
        TLS_ENABLED: "true"
        AUTH_REQUIRED: "true"
        ENCRYPTION_AT_REST: "true"
```

### 9.2 Performance Guidelines

```yaml
# Performance optimization practices
mcp:
  client:
    # ✅ Set appropriate timeouts
    default-timeout: 10000
    retry:
      # ✅ Configure retry behavior
      max-attempts: 3
      backoff-multiplier: 1.5
  
  servers:
    high-perf-server:
      # ✅ Set server-specific timeouts
      timeout: 30000
      environment:
        # ✅ Configure connection pooling
        CONNECTION_POOL_SIZE: "25"
        # ✅ Set appropriate batch sizes
        BATCH_SIZE: "1000"
```

### 9.3 Monitoring Guidelines

```yaml
# Monitoring and observability setup
logging:
  level:
    com.deepai.mcpclient: INFO
    com.deepai.mcpclient.service: DEBUG  # Detailed service logs

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## 10. Validation and Testing

### 10.1 Configuration Validation

```bash
# Validate configuration syntax
java -jar global-mcp-client.jar --spring.config.location=application-test.yml --validate-config

# Test server connections
java -jar global-mcp-client.jar --spring.config.location=application-test.yml --test-connections

# Check configuration profiles
java -jar global-mcp-client.jar --spring.profiles.active=production --dry-run
```

### 10.2 Health Check Configuration

```yaml
# Health monitoring setup
mcp:
  client:
    health-check:
      enabled: true
      interval: 30s
      timeout: 5s
      failure-threshold: 3
  
  servers:
    monitored-server:
      health-check:
        endpoint: "/health"
        expected-response: "ok"
        retry-on-failure: true
```

---

**Document Control**

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2025-09-01 | Initial configuration guide | Development Team |

**References**
- Spring Boot Configuration Properties Reference
- MCP Protocol Specification
- Enterprise Security Guidelines
- Performance Tuning Best Practices
