# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app
COPY pom.xml .
COPY src ./src

# Copy Maven wrapper
COPY .mvn .mvn
COPY mvnw .

RUN chmod +x mvnw
RUN ./mvnw clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S appgroup && \
    adduser -u 1001 -S appuser -G appgroup

# Copy jar from build stage
COPY --from=builder /app/target/global-mcp-client-*.jar app.jar

# Change ownership
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]