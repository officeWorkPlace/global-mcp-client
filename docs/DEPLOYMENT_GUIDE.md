# Global MCP Client - Deployment Guide
## Production Deployment and Operations Manual

**Document Version:** 1.0  
**Date:** 2025-09-01  
**Classification:** Operations Manual  
**Target Audience:** DevOps Engineers, System Administrators  

---

## Executive Summary

This deployment guide provides comprehensive instructions for deploying, configuring, and operating the Global MCP Client in production environments. The guide covers multiple deployment scenarios, from single-instance deployments to large-scale enterprise configurations with high availability and disaster recovery.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Installation Methods](#2-installation-methods)
3. [Configuration Management](#3-configuration-management)
4. [Security Setup](#4-security-setup)
5. [High Availability Deployment](#5-high-availability-deployment)
6. [Monitoring and Observability](#6-monitoring-and-observability)
7. [Backup and Recovery](#7-backup-and-recovery)
8. [Performance Tuning](#8-performance-tuning)
9. [Troubleshooting](#9-troubleshooting)
10. [Maintenance Procedures](#10-maintenance-procedures)

---

## 1. Prerequisites

### 1.1 System Requirements

#### Minimum Requirements
- **CPU**: 2 cores (4 recommended)
- **Memory**: 4GB RAM (8GB recommended)
- **Storage**: 20GB available space (SSD recommended)
- **Network**: 1Gbps network interface
- **OS**: Linux (Ubuntu 20.04+, RHEL 8+, CentOS 8+), Windows Server 2019+, macOS 10.15+

#### Recommended Production Requirements
- **CPU**: 8+ cores
- **Memory**: 16GB+ RAM
- **Storage**: 100GB+ SSD storage
- **Network**: 10Gbps network interface
- **Load Balancer**: HAProxy, NGINX, or cloud load balancer

### 1.2 Software Dependencies

#### Required Components
```bash
# Java Runtime Environment
java -version  # OpenJDK 17+ or Oracle JDK 17+

# Database (choose one)
# PostgreSQL 13+
sudo apt-get install postgresql-13

# MongoDB 6.0+
wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | sudo apt-key add -

# Redis 6.2+
sudo apt-get install redis-server

# Process Management
sudo apt-get install systemd  # Linux
# or
brew install supervisor  # macOS
```

#### Optional Components
```bash
# Monitoring Stack
sudo apt-get install prometheus grafana

# Message Queue (for advanced deployments)
sudo apt-get install apache2-kafka

# Container Runtime
sudo apt-get install docker.io docker-compose
```

### 1.3 Network Requirements

#### Port Configuration
```bash
# Application Ports
8080    # HTTP API (default)
8443    # HTTPS API (if enabled)
8081    # Management/Actuator endpoints
9090    # Prometheus metrics

# Database Ports
5432    # PostgreSQL
27017   # MongoDB
6379    # Redis

# Firewall Configuration (Ubuntu/Debian)
sudo ufw allow 8080/tcp
sudo ufw allow 8443/tcp
sudo ufw allow 8081/tcp
sudo ufw allow 9090/tcp
```

---

## 2. Installation Methods

### 2.1 Standalone JAR Deployment

#### Build and Setup
```bash
# Clone and build from source
git clone <repository-url>
cd global-mcp-client
mvn clean package -DskipTests

# Create application directory
sudo mkdir -p /opt/globalmcp
cd /opt/globalmcp

# Copy built JAR
sudo cp target/global-mcp-client-1.0.0-SNAPSHOT.jar /opt/globalmcp/global-mcp-client.jar

# Create application user
sudo useradd -r -s /bin/false globalmcp
sudo chown -R globalmcp:globalmcp /opt/globalmcp

# Create configuration directory
sudo mkdir -p /etc/globalmcp
sudo chown globalmcp:globalmcp /etc/globalmcp

# Copy configuration file
sudo cp application-improved.yml /etc/globalmcp/application.yml
```

#### Systemd Service Configuration
```ini
# /etc/systemd/system/globalmcp.service
[Unit]
Description=Global MCP Client
Documentation=https://docs.globalmcp.com
After=network-online.target
Wants=network-online.target

[Service]
Type=exec
User=globalmcp
Group=globalmcp
ExecStart=/usr/bin/java \
    -Xmx2g \
    -Xms1g \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -Djava.awt.headless=true \
    -Dspring.config.location=/etc/globalmcp/application.yml \
    -jar /opt/globalmcp/global-mcp-client-2.0.0.jar
ExecReload=/bin/kill -HUP $MAINPID
KillMode=mixed
KillSignal=SIGTERM
TimeoutStopSec=30
SuccessExitStatus=0 143
SyslogIdentifier=globalmcp
WorkingDirectory=/opt/globalmcp

# Security settings
NoNewPrivileges=yes
PrivateTmp=yes
ProtectSystem=strict
ReadWritePaths=/var/log/globalmcp /tmp
RestrictRealtime=yes

# Resource limits
LimitNOFILE=65535
LimitNPROC=4096

[Install]
WantedBy=multi-user.target
```

#### Service Management
```bash
# Enable and start service
sudo systemctl daemon-reload
sudo systemctl enable globalmcp
sudo systemctl start globalmcp

# Check status
sudo systemctl status globalmcp

# View logs
sudo journalctl -u globalmcp -f
```

### 2.2 Docker Deployment

#### Docker Compose Configuration
```yaml
# docker-compose.yml
version: '3.8'

services:
  globalmcp:
    image: globalmcp/client:2.0.0
    container_name: globalmcp-client
    restart: unless-stopped
    ports:
      - "8080:8080"
      - "8081:8081"
      - "9090:9090"
    volumes:
      - ./config:/app/config:ro
      - ./logs:/app/logs
      - ./data:/app/data
    environment:
      - SPRING_PROFILES_ACTIVE=production
      - SPRING_CONFIG_LOCATION=/app/config/application.yml
      - JAVA_OPTS=-Xmx2g -Xms1g -XX:+UseG1GC
    networks:
      - globalmcp-network
    depends_on:
      - postgres
      - redis
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  postgres:
    image: postgres:15-alpine
    container_name: globalmcp-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: globalmcp
      POSTGRES_USER: globalmcp
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./init.sql:/docker-entrypoint-initdb.d/init.sql:ro
    networks:
      - globalmcp-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U globalmcp -d globalmcp"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    container_name: globalmcp-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD}
    volumes:
      - redis_data:/data
    networks:
      - globalmcp-network
    healthcheck:
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  nginx:
    image: nginx:alpine
    container_name: globalmcp-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
      - ./ssl:/etc/nginx/ssl:ro
      - ./logs/nginx:/var/log/nginx
    networks:
      - globalmcp-network
    depends_on:
      - globalmcp

volumes:
  postgres_data:
  redis_data:

networks:
  globalmcp-network:
    driver: bridge
```

#### Docker Deployment Commands
```bash
# Create environment file
cat > .env << EOF
POSTGRES_PASSWORD=your_secure_postgres_password
REDIS_PASSWORD=your_secure_redis_password
EOF

# Start services
docker-compose up -d

# Check service status
docker-compose ps

# View logs
docker-compose logs -f globalmcp

# Scale application (if needed)
docker-compose up -d --scale globalmcp=3
```

### 2.3 Kubernetes Deployment

#### Kubernetes Manifests
```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: globalmcp
  labels:
    name: globalmcp

---
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: globalmcp-config
  namespace: globalmcp
data:
  application.yml: |
    spring:
      profiles:
        active: kubernetes,production
      datasource:
        url: jdbc:postgresql://postgres-service:5432/globalmcp
        username: globalmcp
        password: ${POSTGRES_PASSWORD}
      data:
        redis:
          host: redis-service
          port: 6379
          password: ${REDIS_PASSWORD}
    
    mcp:
      client:
        default-timeout: 15000
        max-concurrent-connections: 100
      servers:
        # Server configurations will be loaded from ConfigMaps or Secrets

---
# secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: globalmcp-secrets
  namespace: globalmcp
type: Opaque
data:
  postgres-password: <base64-encoded-password>
  redis-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-jwt-secret>

---
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: globalmcp-deployment
  namespace: globalmcp
  labels:
    app: globalmcp
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1
      maxSurge: 2
  selector:
    matchLabels:
      app: globalmcp
  template:
    metadata:
      labels:
        app: globalmcp
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: globalmcp-service-account
      containers:
      - name: globalmcp
        image: globalmcp/client:2.0.0
        imagePullPolicy: IfNotPresent
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        - containerPort: 8081
          name: management
          protocol: TCP
        - containerPort: 9090
          name: metrics
          protocol: TCP
        env:
        - name: SPRING_CONFIG_LOCATION
          value: /app/config/application.yml
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: globalmcp-secrets
              key: postgres-password
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: globalmcp-secrets
              key: redis-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: globalmcp-secrets
              key: jwt-secret
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: temp-volume
          mountPath: /tmp
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8081
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        startupProbe:
          httpGet:
            path: /actuator/health/startup
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 12
      volumes:
      - name: config-volume
        configMap:
          name: globalmcp-config
      - name: temp-volume
        emptyDir: {}
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000

---
# service.yaml
apiVersion: v1
kind: Service
metadata:
  name: globalmcp-service
  namespace: globalmcp
  labels:
    app: globalmcp
spec:
  type: ClusterIP
  ports:
  - port: 80
    targetPort: 8080
    protocol: TCP
    name: http
  - port: 8081
    targetPort: 8081
    protocol: TCP
    name: management
  - port: 9090
    targetPort: 9090
    protocol: TCP
    name: metrics
  selector:
    app: globalmcp

---
# ingress.yaml
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: globalmcp-ingress
  namespace: globalmcp
  annotations:
    nginx.ingress.kubernetes.io/rewrite-target: /
    nginx.ingress.kubernetes.io/ssl-redirect: "true"
    cert-manager.io/cluster-issuer: "letsencrypt-prod"
spec:
  tls:
  - hosts:
    - api.globalmcp.yourdomain.com
    secretName: globalmcp-tls
  rules:
  - host: api.globalmcp.yourdomain.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: globalmcp-service
            port:
              number: 80
```

#### Kubernetes Deployment Commands
```bash
# Apply configurations
kubectl apply -f namespace.yaml
kubectl apply -f configmap.yaml
kubectl apply -f secret.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
kubectl apply -f ingress.yaml

# Check deployment status
kubectl get pods -n globalmcp
kubectl get services -n globalmcp
kubectl get ingress -n globalmcp

# View logs
kubectl logs -f deployment/globalmcp-deployment -n globalmcp

# Scale deployment
kubectl scale deployment globalmcp-deployment --replicas=5 -n globalmcp
```

---

## 3. Configuration Management

### 3.1 Environment-Specific Configurations

#### Production Configuration
```yaml
# application-production.yml
spring:
  profiles:
    active: production
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      connection-timeout: 30000
      leak-detection-threshold: 60000
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: false
        generate_statistics: false

logging:
  level:
    root: INFO
    com.deepai.mcpclient: INFO
    org.springframework.security: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/globalmcp/application.log
  logback:
    rollingpolicy:
      max-file-size: 100MB
      max-history: 30
      total-size-cap: 3GB

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
      probes:
        enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

mcp:
  client:
    default-timeout: 15000
    max-concurrent-connections: 100
    retry:
      max-attempts: 3
      backoff-multiplier: 2.0
    pool:
      core-size: 20
      max-size: 200
      keep-alive: 60s
      queue-capacity: 10000
  security:
    jwt:
      secret: ${JWT_SECRET}
      expiration: 3600
    rate-limiting:
      enabled: true
      requests-per-minute: 1000
      burst-capacity: 2000
```

#### Development Configuration
```yaml
# application-development.yml
spring:
  profiles:
    active: development
  datasource:
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: create-drop
    show-sql: true

logging:
  level:
    root: DEBUG
    com.deepai.mcpclient: DEBUG
    org.springframework.security: DEBUG
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"

management:
  endpoints:
    web:
      exposure:
        include: "*"

mcp:
  client:
    default-timeout: 5000
    max-concurrent-connections: 10
  security:
    rate-limiting:
      enabled: false
```

### 3.2 External Configuration Management

#### HashiCorp Vault Integration
```yaml
# vault-config.yml
spring:
  cloud:
    vault:
      enabled: true
      host: vault.yourdomain.com
      port: 8200
      scheme: https
      authentication: KUBERNETES
      kubernetes:
        role: globalmcp-role
        kubernetes-path: kubernetes
        service-account-token-file: /var/run/secrets/kubernetes.io/serviceaccount/token
      kv:
        enabled: true
        backend: secret
        profile-separator: '/'
        default-context: globalmcp
        application-name: globalmcp
```

#### Kubernetes ConfigMap Hot Reload
```yaml
# configmap-reload.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: globalmcp-servers
  namespace: globalmcp
  labels:
    app: globalmcp
    component: server-config
data:
  servers.yml: |
    mcp:
      servers:
        mongodb-production:
          type: stdio
          command: java
          args:
            - "-Xmx512m"
            - "-jar"
            - "/opt/servers/mongo-mcp-server.jar"
          timeout: 15000
          enabled: true
          environment:
            MONGODB_URI: "${MONGODB_PROD_URI}"
            LOG_LEVEL: "INFO"
```

---

## 4. Security Setup

### 4.1 TLS/SSL Configuration

#### Certificate Generation
```bash
# Self-signed certificate (development only)
openssl req -x509 -newkey rsa:4096 -keyout globalmcp-key.pem -out globalmcp-cert.pem -days 365 -nodes

# Create PKCS12 keystore
openssl pkcs12 -export -in globalmcp-cert.pem -inkey globalmcp-key.pem -out globalmcp.p12 -name globalmcp

# Production: Use Let's Encrypt with Certbot
sudo certbot certonly --nginx -d api.globalmcp.yourdomain.com
```

#### SSL Configuration
```yaml
# SSL configuration in application.yml
server:
  port: 8443
  ssl:
    enabled: true
    key-store: classpath:globalmcp.p12
    key-store-password: ${KEYSTORE_PASSWORD}
    key-store-type: PKCS12
    key-alias: globalmcp
    protocol: TLS
    enabled-protocols:
      - TLSv1.3
      - TLSv1.2
    ciphers:
      - TLS_AES_256_GCM_SHA384
      - TLS_AES_128_GCM_SHA256
      - TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
      - TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256
```

### 4.2 Authentication Setup

#### OAuth2 Configuration
```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.yourdomain.com/realms/globalmcp
          jwk-set-uri: https://auth.yourdomain.com/realms/globalmcp/protocol/openid-connect/certs
      client:
        registration:
          globalmcp:
            client-id: ${OAUTH2_CLIENT_ID}
            client-secret: ${OAUTH2_CLIENT_SECRET}
            scope:
              - openid
              - profile
              - email
              - mcp:read
              - mcp:write
            authorization-grant-type: authorization_code
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
        provider:
          globalmcp:
            authorization-uri: https://auth.yourdomain.com/realms/globalmcp/protocol/openid-connect/auth
            token-uri: https://auth.yourdomain.com/realms/globalmcp/protocol/openid-connect/token
            user-info-uri: https://auth.yourdomain.com/realms/globalmcp/protocol/openid-connect/userinfo
            jwk-set-uri: https://auth.yourdomain.com/realms/globalmcp/protocol/openid-connect/certs
```

### 4.3 Network Security

#### Firewall Configuration
```bash
# UFW Configuration (Ubuntu)
sudo ufw enable
sudo ufw default deny incoming
sudo ufw default allow outgoing

# Allow SSH
sudo ufw allow ssh

# Allow application ports
sudo ufw allow 8080/tcp
sudo ufw allow 8443/tcp

# Allow from specific networks only
sudo ufw allow from 10.0.0.0/8 to any port 8081
sudo ufw allow from 10.0.0.0/8 to any port 9090

# iptables rules (RHEL/CentOS)
sudo iptables -A INPUT -p tcp --dport 8080 -j ACCEPT
sudo iptables -A INPUT -p tcp --dport 8443 -j ACCEPT
sudo iptables -A INPUT -s 10.0.0.0/8 -p tcp --dport 8081 -j ACCEPT
sudo iptables -A INPUT -s 10.0.0.0/8 -p tcp --dport 9090 -j ACCEPT
sudo iptables-save > /etc/iptables/rules.v4
```

---

## 5. High Availability Deployment

### 5.1 Load Balancer Configuration

#### HAProxy Configuration
```haproxy
# /etc/haproxy/haproxy.cfg
global
    daemon
    maxconn 4096
    log stdout local0
    stats socket /var/run/haproxy.sock mode 600 level admin
    stats timeout 2m

defaults
    mode http
    timeout connect 5s
    timeout client 30s
    timeout server 30s
    option httplog
    option dontlognull
    option redispatch
    retries 3

frontend globalmcp_frontend
    bind *:80
    bind *:443 ssl crt /etc/ssl/certs/globalmcp.pem
    redirect scheme https if !{ ssl_fc }
    
    # Health check endpoint
    acl health_check path_beg /actuator/health
    use_backend globalmcp_health if health_check
    
    # API endpoints
    default_backend globalmcp_backend

backend globalmcp_backend
    balance roundrobin
    option httpchk GET /actuator/health/readiness
    http-check expect status 200
    
    server globalmcp1 10.0.1.10:8080 check inter 5s rise 2 fall 3
    server globalmcp2 10.0.1.11:8080 check inter 5s rise 2 fall 3
    server globalmcp3 10.0.1.12:8080 check inter 5s rise 2 fall 3

backend globalmcp_health
    server globalmcp1 10.0.1.10:8081 check
    server globalmcp2 10.0.1.11:8081 check
    server globalmcp3 10.0.1.12:8081 check

listen stats
    bind *:8404
    stats enable
    stats uri /stats
    stats refresh 30s
```

#### NGINX Load Balancer
```nginx
# /etc/nginx/nginx.conf
upstream globalmcp_backend {
    least_conn;
    server 10.0.1.10:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.11:8080 max_fails=3 fail_timeout=30s;
    server 10.0.1.12:8080 max_fails=3 fail_timeout=30s;
}

upstream globalmcp_management {
    server 10.0.1.10:8081;
    server 10.0.1.11:8081;
    server 10.0.1.12:8081;
}

server {
    listen 80;
    server_name api.globalmcp.yourdomain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name api.globalmcp.yourdomain.com;
    
    ssl_certificate /etc/ssl/certs/globalmcp.crt;
    ssl_certificate_key /etc/ssl/private/globalmcp.key;
    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers off;
    
    # API endpoints
    location / {
        proxy_pass http://globalmcp_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_connect_timeout 30s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;
        
        # Health check for upstream
        proxy_next_upstream error timeout invalid_header http_500 http_502 http_503 http_504;
    }
    
    # Management endpoints (restricted)
    location /actuator {
        allow 10.0.0.0/8;
        allow 172.16.0.0/12;
        allow 192.168.0.0/16;
        deny all;
        
        proxy_pass http://globalmcp_management;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

### 5.2 Database High Availability

#### PostgreSQL Cluster Setup
```yaml
# postgresql-ha.yml (using Patroni)
version: '3.8'
services:
  etcd1:
    image: quay.io/coreos/etcd:v3.5.0
    environment:
      ETCD_NAME: etcd1
      ETCD_INITIAL_ADVERTISE_PEER_URLS: http://etcd1:2380
      ETCD_LISTEN_PEER_URLS: http://0.0.0.0:2380
      ETCD_LISTEN_CLIENT_URLS: http://0.0.0.0:2379
      ETCD_ADVERTISE_CLIENT_URLS: http://etcd1:2379
      ETCD_INITIAL_CLUSTER: etcd1=http://etcd1:2380,etcd2=http://etcd2:2380,etcd3=http://etcd3:2380
      ETCD_INITIAL_CLUSTER_STATE: new
      ETCD_INITIAL_CLUSTER_TOKEN: etcd-cluster-token
    networks:
      - postgres-net

  postgres-primary:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: globalmcp
      POSTGRES_USER: globalmcp
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      POSTGRES_REPLICATION_USER: replicator
      POSTGRES_REPLICATION_PASSWORD: ${REPLICATION_PASSWORD}
    volumes:
      - postgres_primary_data:/var/lib/postgresql/data
      - ./postgres-config/postgresql.conf:/var/lib/postgresql/data/postgresql.conf
      - ./postgres-config/pg_hba.conf:/var/lib/postgresql/data/pg_hba.conf
    networks:
      - postgres-net
    depends_on:
      - etcd1

  postgres-replica:
    image: postgres:15-alpine
    environment:
      PGUSER: postgres
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
      PGPASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_replica_data:/var/lib/postgresql/data
    networks:
      - postgres-net
    depends_on:
      - postgres-primary

volumes:
  postgres_primary_data:
  postgres_replica_data:

networks:
  postgres-net:
    driver: bridge
```

### 5.3 Session Management

#### Redis Cluster for Session Storage
```yaml
# redis-cluster.yml
version: '3.8'
services:
  redis-node-1:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf
    volumes:
      - ./redis-cluster.conf:/usr/local/etc/redis/redis.conf
      - redis_node_1_data:/data
    networks:
      - redis-net
    
  redis-node-2:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf
    volumes:
      - ./redis-cluster.conf:/usr/local/etc/redis/redis.conf
      - redis_node_2_data:/data
    networks:
      - redis-net
      
  redis-node-3:
    image: redis:7-alpine
    command: redis-server /usr/local/etc/redis/redis.conf
    volumes:
      - ./redis-cluster.conf:/usr/local/etc/redis/redis.conf
      - redis_node_3_data:/data
    networks:
      - redis-net

volumes:
  redis_node_1_data:
  redis_node_2_data:
  redis_node_3_data:

networks:
  redis-net:
    driver: bridge
```

---

## 6. Monitoring and Observability

### 6.1 Prometheus Configuration

#### Prometheus Setup
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

rule_files:
  - "globalmcp_rules.yml"

alerting:
  alertmanagers:
    - static_configs:
        - targets:
          - alertmanager:9093

scrape_configs:
  - job_name: 'globalmcp'
    static_configs:
      - targets: ['globalmcp1:9090', 'globalmcp2:9090', 'globalmcp3:9090']
    metrics_path: '/actuator/prometheus'
    scrape_interval: 30s
    scrape_timeout: 10s
    
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']
      
  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']
      
  - job_name: 'nginx'
    static_configs:
      - targets: ['nginx-exporter:9113']
```

#### Alert Rules
```yaml
# globalmcp_rules.yml
groups:
  - name: globalmcp_alerts
    rules:
      - alert: GlobalMcpInstanceDown
        expr: up{job="globalmcp"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Global MCP instance {{ $labels.instance }} is down"
          description: "Global MCP instance {{ $labels.instance }} has been down for more than 1 minute."

      - alert: HighErrorRate
        expr: rate(http_server_requests_seconds_count{status=~"5.."}[5m]) > 0.1
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value }} errors per second"

      - alert: HighResponseTime
        expr: histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])) > 2
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High response time detected"
          description: "95th percentile response time is {{ $value }} seconds"

      - alert: DatabaseConnectionsHigh
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.8
        for: 1m
        labels:
          severity: warning
        annotations:
          summary: "Database connection pool usage high"
          description: "Database connection pool usage is {{ $value }}%"
```

### 6.2 Grafana Dashboards

#### Main Dashboard Configuration
```json
{
  "dashboard": {
    "id": null,
    "title": "Global MCP Client - Overview",
    "tags": ["globalmcp"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Request Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count[5m]))",
            "legendFormat": "Requests/sec"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "reqps"
          }
        }
      },
      {
        "id": 2,
        "title": "Error Rate",
        "type": "stat",
        "targets": [
          {
            "expr": "sum(rate(http_server_requests_seconds_count{status=~\"5..\"}[5m])) / sum(rate(http_server_requests_seconds_count[5m])) * 100",
            "legendFormat": "Error %"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "unit": "percent"
          }
        }
      },
      {
        "id": 3,
        "title": "Response Time",
        "type": "timeseries",
        "targets": [
          {
            "expr": "histogram_quantile(0.50, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "50th percentile"
          },
          {
            "expr": "histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "95th percentile"
          },
          {
            "expr": "histogram_quantile(0.99, rate(http_server_requests_seconds_bucket[5m]))",
            "legendFormat": "99th percentile"
          }
        ]
      }
    ],
    "time": {
      "from": "now-6h",
      "to": "now"
    },
    "refresh": "10s"
  }
}
```

### 6.3 Log Management

#### ELK Stack Configuration
```yaml
# elk-stack.yml
version: '3.8'
services:
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.8.0
    environment:
      - discovery.type=single-node
      - "ES_JAVA_OPTS=-Xms1g -Xmx1g"
      - xpack.security.enabled=false
    volumes:
      - elasticsearch_data:/usr/share/elasticsearch/data
    networks:
      - elk-net

  logstash:
    image: docker.elastic.co/logstash/logstash:8.8.0
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf:ro
      - /var/log/globalmcp:/var/log/globalmcp:ro
    depends_on:
      - elasticsearch
    networks:
      - elk-net

  kibana:
    image: docker.elastic.co/kibana/kibana:8.8.0
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
    networks:
      - elk-net

volumes:
  elasticsearch_data:

networks:
  elk-net:
    driver: bridge
```

#### Logstash Configuration
```ruby
# logstash.conf
input {
  file {
    path => "/var/log/globalmcp/application.log"
    start_position => "beginning"
    codec => multiline {
      pattern => "^\d{4}-\d{2}-\d{2}"
      negate => true
      what => "previous"
    }
  }
}

filter {
  if [message] =~ /^\d{4}-\d{2}-\d{2}/ {
    grok {
      match => { 
        "message" => "%{TIMESTAMP_ISO8601:timestamp} \[%{DATA:thread}\] %{LOGLEVEL:level} %{DATA:logger} - %{GREEDYDATA:content}"
      }
    }
    
    date {
      match => [ "timestamp", "yyyy-MM-dd HH:mm:ss.SSS" ]
    }
    
    if [content] =~ /MCP request/ {
      grok {
        match => {
          "content" => "MCP request to server %{DATA:server_id}: %{DATA:operation}"
        }
        add_tag => ["mcp_request"]
      }
    }
    
    if [level] == "ERROR" {
      mutate {
        add_tag => ["error"]
      }
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "globalmcp-logs-%{+YYYY.MM.dd}"
  }
  
  if "error" in [tags] {
    email {
      to => "alerts@yourdomain.com"
      subject => "Global MCP Error Alert"
      body => "Error detected: %{message}"
    }
  }
}
```

---

## 7. Backup and Recovery

### 7.1 Database Backup Strategy

#### PostgreSQL Backup Script
```bash
#!/bin/bash
# backup-postgres.sh

set -e

# Configuration
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-globalmcp}"
DB_USER="${DB_USER:-globalmcp}"
BACKUP_DIR="/backup/postgres"
RETENTION_DAYS=30
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/${DB_NAME}_${DATE}.sql.gz"

# Create backup directory
mkdir -p "$BACKUP_DIR"

# Perform backup
echo "Starting backup of database $DB_NAME..."
pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
    --verbose --clean --create --if-exists \
    | gzip > "$BACKUP_FILE"

# Verify backup
if [ -f "$BACKUP_FILE" ]; then
    echo "Backup completed successfully: $BACKUP_FILE"
    echo "Backup size: $(du -h "$BACKUP_FILE" | cut -f1)"
else
    echo "Backup failed!" >&2
    exit 1
fi

# Clean up old backups
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -delete
echo "Cleaned up backups older than $RETENTION_DAYS days"

# Upload to cloud storage (optional)
if [ -n "$S3_BUCKET" ]; then
    aws s3 cp "$BACKUP_FILE" "s3://$S3_BUCKET/postgres-backups/"
    echo "Backup uploaded to S3: s3://$S3_BUCKET/postgres-backups/$(basename "$BACKUP_FILE")"
fi

echo "Backup process completed"
```

#### Automated Backup with Cron
```bash
# Add to crontab
0 2 * * * /usr/local/bin/backup-postgres.sh >> /var/log/backup.log 2>&1
```

### 7.2 Application Data Backup

#### Configuration Backup Script
```bash
#!/bin/bash
# backup-config.sh

set -e

CONFIG_DIR="/etc/globalmcp"
BACKUP_DIR="/backup/config"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/globalmcp-config_${DATE}.tar.gz"

mkdir -p "$BACKUP_DIR"

# Create configuration backup
tar -czf "$BACKUP_FILE" -C "$(dirname "$CONFIG_DIR")" "$(basename "$CONFIG_DIR")"

# Verify and log
if [ -f "$BACKUP_FILE" ]; then
    echo "Configuration backup completed: $BACKUP_FILE"
    echo "Backup size: $(du -h "$BACKUP_FILE" | cut -f1)"
else
    echo "Configuration backup failed!" >&2
    exit 1
fi

# Retain only last 7 backups
ls -t "${BACKUP_DIR}"/globalmcp-config_*.tar.gz | tail -n +8 | xargs -r rm

echo "Configuration backup process completed"
```

### 7.3 Disaster Recovery Procedures

#### Database Recovery Script
```bash
#!/bin/bash
# restore-postgres.sh

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <backup_file>"
    exit 1
fi

BACKUP_FILE="$1"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-globalmcp}"
DB_USER="${DB_USER:-globalmcp}"

if [ ! -f "$BACKUP_FILE" ]; then
    echo "Backup file not found: $BACKUP_FILE" >&2
    exit 1
fi

echo "Starting database restoration from $BACKUP_FILE..."

# Stop application first
sudo systemctl stop globalmcp

# Restore database
if [[ "$BACKUP_FILE" == *.gz ]]; then
    gunzip -c "$BACKUP_FILE" | psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres
else
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres < "$BACKUP_FILE"
fi

echo "Database restoration completed"

# Start application
sudo systemctl start globalmcp

echo "Application restarted"
```

#### Point-in-Time Recovery
```bash
#!/bin/bash
# point-in-time-recovery.sh

TARGET_TIME="$1"
BASE_BACKUP="$2"

if [ -z "$TARGET_TIME" ] || [ -z "$BASE_BACKUP" ]; then
    echo "Usage: $0 <target_time> <base_backup>"
    echo "Example: $0 '2025-09-01 14:30:00' /backup/base_backup_20250901.tar.gz"
    exit 1
fi

# Stop PostgreSQL
sudo systemctl stop postgresql

# Clear data directory
sudo rm -rf /var/lib/postgresql/15/main/*

# Restore base backup
sudo tar -xzf "$BASE_BACKUP" -C /var/lib/postgresql/15/main/

# Configure recovery
cat > /tmp/postgresql.auto.conf << EOF
restore_command = 'cp /var/lib/postgresql/15/archive/%f %p'
recovery_target_time = '$TARGET_TIME'
recovery_target_action = 'promote'
EOF

sudo mv /tmp/postgresql.auto.conf /var/lib/postgresql/15/main/
sudo chown postgres:postgres /var/lib/postgresql/15/main/postgresql.auto.conf

# Start PostgreSQL in recovery mode
sudo systemctl start postgresql

echo "Point-in-time recovery initiated to $TARGET_TIME"
```

---

## 8. Performance Tuning

### 8.1 JVM Optimization

#### Production JVM Settings
```bash
# /etc/systemd/system/globalmcp.service.d/override.conf
[Service]
Environment="JAVA_OPTS=-server \
    -Xms2g \
    -Xmx4g \
    -XX:+UseG1GC \
    -XX:MaxGCPauseMillis=200 \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:ReservedCodeCacheSize=256m \
    -XX:InitialCodeCacheSize=64m \
    -XX:CompileThreshold=1000 \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=4 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseZGC \
    -XX:+UnlockDiagnosticVMOptions \
    -XX:+LogVMOutput \
    -XX:LogFile=/var/log/globalmcp/gc.log \
    -XX:+UseGCLogFileRotation \
    -XX:NumberOfGCLogFiles=5 \
    -XX:GCLogFileSize=10M \
    -Djava.awt.headless=true \
    -Djava.security.egd=file:/dev/./urandom \
    -Dfile.encoding=UTF-8 \
    -Duser.timezone=UTC"
```

### 8.2 Database Optimization

#### PostgreSQL Configuration
```postgresql
# postgresql.conf optimization
max_connections = 200
shared_buffers = 1GB
effective_cache_size = 3GB
maintenance_work_mem = 256MB
work_mem = 10MB
wal_buffers = 16MB
checkpoint_completion_target = 0.9
random_page_cost = 1.1
effective_io_concurrency = 200
min_wal_size = 1GB
max_wal_size = 4GB
max_worker_processes = 8
max_parallel_workers_per_gather = 4
max_parallel_workers = 8
max_parallel_maintenance_workers = 4
```

#### Connection Pooling Optimization
```yaml
spring:
  datasource:
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      idle-timeout: 300000
      max-lifetime: 1800000
      connection-timeout: 30000
      leak-detection-threshold: 60000
      pool-name: GlobalMcpHikariCP
      data-source-properties:
        cachePrepStmts: true
        prepStmtCacheSize: 250
        prepStmtCacheSqlLimit: 2048
        useServerPrepStmts: true
        useLocalSessionState: true
        rewriteBatchedStatements: true
        cacheResultSetMetadata: true
        cacheServerConfiguration: true
        elideSetAutoCommits: true
        maintainTimeStats: false
```

### 8.3 Cache Configuration

#### Redis Optimization
```redis
# redis.conf optimization
maxmemory 2gb
maxmemory-policy allkeys-lru
tcp-keepalive 300
timeout 300
tcp-backlog 511
databases 16
save 900 1
save 300 10
save 60 10000
stop-writes-on-bgsave-error yes
rdbcompression yes
rdbchecksum yes
maxclients 10000
```

#### Application Cache Configuration
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 1 hour
      cache-null-values: false
    cache-names:
      - server-capabilities
      - tool-definitions
      - resource-metadata
      - authentication-tokens

management:
  metrics:
    cache:
      meter-binder:
        enabled: true
```

---

## 9. Troubleshooting

### 9.1 Common Issues and Solutions

#### Application Won't Start
```bash
# Check logs
sudo journalctl -u globalmcp -f

# Common issues:
# 1. Port already in use
sudo netstat -tlnp | grep :8080

# 2. Database connection issues
telnet postgres-host 5432

# 3. Insufficient memory
free -h
cat /proc/meminfo

# 4. Permission issues
sudo chown -R globalmcp:globalmcp /opt/globalmcp
sudo chmod +x /opt/globalmcp/*.jar
```

#### Memory Issues
```bash
# Monitor memory usage
sudo ps aux --sort=-%mem | head -10

# Check for memory leaks
sudo jstat -gc -t $JAVA_PID 5s

# Heap dump analysis
sudo jcmd $JAVA_PID GC.run_finalization
sudo jcmd $JAVA_PID VM.gc
sudo jmap -dump:format=b,file=/tmp/heapdump.hprof $JAVA_PID
```

#### Database Connection Issues
```bash
# Test database connectivity
psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "SELECT version();"

# Check connection pool
curl http://localhost:8081/actuator/metrics/hikaricp.connections.active

# Monitor slow queries
tail -f /var/log/postgresql/postgresql.log | grep "slow query"
```

### 9.2 Performance Troubleshooting

#### High CPU Usage
```bash
# Identify CPU-intensive processes
top -p $(pgrep -f globalmcp)

# Java thread dump
sudo jstack $JAVA_PID > /tmp/threaddump.txt

# Profile application
sudo java -jar /opt/profilers/async-profiler.jar -d 60 -f /tmp/profile.html $JAVA_PID
```

#### High Memory Usage
```bash
# Memory analysis
sudo jcmd $JAVA_PID VM.memory_info
sudo jcmd $JAVA_PID GC.class_histogram | head -20

# Check for memory leaks
sudo jstat -gccapacity $JAVA_PID
sudo jstat -gcutil $JAVA_PID 5s 10
```

#### Network Issues
```bash
# Check network connectivity
ping -c 4 $MCP_SERVER_HOST
telnet $MCP_SERVER_HOST $MCP_SERVER_PORT

# Monitor network usage
sudo netstat -i
sudo ss -tuln | grep globalmcp

# Packet capture
sudo tcpdump -i eth0 port 8080 -w /tmp/capture.pcap
```

### 9.3 Debug Configuration

#### Enable Debug Mode
```yaml
logging:
  level:
    com.deepai.mcpclient: DEBUG
    org.springframework.web: DEBUG
    org.springframework.security: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE

management:
  endpoints:
    web:
      exposure:
        include: "*"
  endpoint:
    health:
      show-details: always
    configprops:
      show-values: always
```

#### Health Check Endpoints
```bash
# Application health
curl http://localhost:8081/actuator/health

# Database health
curl http://localhost:8081/actuator/health/db

# Custom health checks
curl http://localhost:8081/actuator/health/mcp-servers

# Metrics
curl http://localhost:8081/actuator/metrics
curl http://localhost:8081/actuator/metrics/jvm.memory.used

# Configuration properties
curl http://localhost:8081/actuator/configprops
```

---

## 10. Maintenance Procedures

### 10.1 Regular Maintenance Tasks

#### Daily Tasks
```bash
#!/bin/bash
# daily-maintenance.sh

echo "Starting daily maintenance..."

# Check disk space
df -h | grep -E '9[0-9]%|100%' && {
    echo "WARNING: High disk usage detected"
    # Clean up old logs
    find /var/log/globalmcp -name "*.log*" -mtime +7 -delete
}

# Check system load
LOAD=$(uptime | awk '{print $10}' | cut -d',' -f1)
if (( $(echo "$LOAD > 2.0" | bc -l) )); then
    echo "WARNING: High system load: $LOAD"
fi

# Check application health
curl -f http://localhost:8081/actuator/health > /dev/null || {
    echo "ERROR: Application health check failed"
    sudo systemctl restart globalmcp
}

# Backup configuration
/usr/local/bin/backup-config.sh

echo "Daily maintenance completed"
```

#### Weekly Tasks
```bash
#!/bin/bash
# weekly-maintenance.sh

echo "Starting weekly maintenance..."

# Full database backup
/usr/local/bin/backup-postgres.sh

# Analyze database
sudo -u postgres psql -d globalmcp -c "ANALYZE;"

# Update system packages
sudo apt update && sudo apt list --upgradable

# Check certificate expiration
openssl x509 -in /etc/ssl/certs/globalmcp.crt -text -noout | grep "Not After"

# Generate maintenance report
{
    echo "=== Weekly Maintenance Report ==="
    echo "Date: $(date)"
    echo "System uptime: $(uptime)"
    echo "Disk usage:"
    df -h
    echo "Memory usage:"
    free -h
    echo "Active connections:"
    curl -s http://localhost:8081/actuator/metrics/hikaricp.connections.active
} > /var/log/globalmcp/weekly-report-$(date +%Y%m%d).txt

echo "Weekly maintenance completed"
```

### 10.2 Update Procedures

#### Application Update Process
```bash
#!/bin/bash
# update-application.sh

NEW_VERSION="$1"

if [ -z "$NEW_VERSION" ]; then
    echo "Usage: $0 <version>"
    exit 1
fi

echo "Starting application update to version $NEW_VERSION..."

# Create backup before update
/usr/local/bin/backup-postgres.sh
/usr/local/bin/backup-config.sh

# Download new version
cd /opt/globalmcp
wget "https://releases.globalmcp.com/v${NEW_VERSION}/global-mcp-client-${NEW_VERSION}.jar"

# Verify checksum
wget "https://releases.globalmcp.com/v${NEW_VERSION}/global-mcp-client-${NEW_VERSION}.jar.sha256"
sha256sum -c "global-mcp-client-${NEW_VERSION}.jar.sha256" || {
    echo "Checksum verification failed!"
    exit 1
}

# Stop application
sudo systemctl stop globalmcp

# Backup current version
mv global-mcp-client-*.jar global-mcp-client-backup.jar

# Install new version
mv "global-mcp-client-${NEW_VERSION}.jar" global-mcp-client.jar
chown globalmcp:globalmcp global-mcp-client.jar

# Update systemd service if needed
if [ -f "update-scripts/systemd-${NEW_VERSION}.service" ]; then
    sudo cp "update-scripts/systemd-${NEW_VERSION}.service" /etc/systemd/system/globalmcp.service
    sudo systemctl daemon-reload
fi

# Start application
sudo systemctl start globalmcp

# Wait for application to be ready
echo "Waiting for application to start..."
for i in {1..30}; do
    curl -f http://localhost:8081/actuator/health > /dev/null 2>&1 && break
    echo "Waiting... ($i/30)"
    sleep 10
done

# Verify update
curl -f http://localhost:8081/actuator/info | grep -q "$NEW_VERSION" && {
    echo "Update successful!"
    rm global-mcp-client-backup.jar
} || {
    echo "Update failed, rolling back..."
    sudo systemctl stop globalmcp
    mv global-mcp-client-backup.jar global-mcp-client.jar
    sudo systemctl start globalmcp
    exit 1
}

echo "Application update completed successfully"
```

#### Rolling Update for Kubernetes
```bash
#!/bin/bash
# k8s-rolling-update.sh

NEW_VERSION="$1"

if [ -z "$NEW_VERSION" ]; then
    echo "Usage: $0 <version>"
    exit 1
fi

echo "Starting rolling update to version $NEW_VERSION..."

# Update deployment image
kubectl set image deployment/globalmcp-deployment \
    globalmcp=globalmcp/client:$NEW_VERSION \
    -n globalmcp

# Monitor rollout
kubectl rollout status deployment/globalmcp-deployment -n globalmcp --timeout=600s

# Verify deployment
kubectl get pods -n globalmcp
kubectl logs -l app=globalmcp -n globalmcp --tail=20

echo "Rolling update completed"
```

### 10.3 Disaster Recovery Testing

#### DR Test Script
```bash
#!/bin/bash
# dr-test.sh

echo "Starting disaster recovery test..."

# Stop primary application
sudo systemctl stop globalmcp

# Simulate database failure
sudo systemctl stop postgresql

# Test backup restoration
LATEST_BACKUP=$(ls -t /backup/postgres/globalmcp_*.sql.gz | head -1)
echo "Testing restoration from: $LATEST_BACKUP"

# Start database
sudo systemctl start postgresql

# Restore from backup
/usr/local/bin/restore-postgres.sh "$LATEST_BACKUP"

# Start application
sudo systemctl start globalmcp

# Verify recovery
sleep 30
curl -f http://localhost:8081/actuator/health && {
    echo "DR test successful"
} || {
    echo "DR test failed"
    exit 1
}

echo "Disaster recovery test completed"
```

---

## Conclusion

This deployment guide provides comprehensive instructions for deploying and operating the Global MCP Client in production environments. Following these procedures ensures reliable, secure, and performant operation of the system.

For additional support or questions, please refer to:
- Technical Documentation: `docs/TECHNICAL_SPECIFICATION.md`
- API Documentation: `docs/API_DOCUMENTATION.md`
- Configuration Examples: `docs/CONFIGURATION_EXAMPLES.md`
- Support Portal: https://support.globalmcp.com

---

**Document Maintenance**

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2025-09-01 | Initial deployment guide | DevOps Team |

**Next Review Date:** 2025-12-01

*This document contains operational procedures and should be kept updated with infrastructure changes.*
