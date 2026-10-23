# ==============================================================================
# TrustVault - Multi-Stage Production Dockerfile for Render Deployment
# Runtime: Java 21 LTS (Eclipse Temurin)
# Security: Non-root user execution, minimal attack surface
# ==============================================================================

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy build descriptor and wrapper
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn

# Make wrapper executable and download dependencies
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B || true

# Copy project source code
COPY src src

# Compile and package production executable JAR
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Create unprivileged system user for zero-trust container security
RUN addgroup -S trustvault && adduser -S trustvault -G trustvault

# Copy packaged JAR from build stage
COPY --from=builder /workspace/target/*.jar /app/app.jar

# Ensure appropriate file permissions
RUN chown -R trustvault:trustvault /app

# Switch to non-root user
USER trustvault

# Render dynamically assigns $PORT (default fallback 8080)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080 10000

# Actuator health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:${PORT:-8080}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java -Djava.security.egd=file:/dev/./urandom -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
