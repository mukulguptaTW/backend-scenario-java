# ─────────────────────────────────────────────────────────────────────────────
# Stage 1 — Build (Maven + JDK 21)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven wrapper first — layer is cached unless wrapper version changes
COPY .mvn/           .mvn/
COPY mvnw            mvnw
COPY mvnw.cmd        mvnw.cmd
RUN chmod +x mvnw

# Copy POM separately so the dependency download layer is cached
COPY pom.xml         pom.xml
RUN ./mvnw -B dependency:go-offline --no-transfer-progress

# Copy source and build
COPY src/            src/
RUN ./mvnw -B package -DskipTests --no-transfer-progress

# ─────────────────────────────────────────────────────────────────────────────
# Stage 2 — Runtime (JRE 21, non-root, minimal attack surface)
# ─────────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Security: create a dedicated non-root user/group
RUN addgroup -S petclinic && adduser -S petclinic -G petclinic

WORKDIR /app

# Copy only the fat JAR from the builder stage
COPY --from=builder --chown=petclinic:petclinic \
     /app/target/spring-petclinic-rest-*.jar app.jar

USER petclinic

EXPOSE 9966

# Spring Boot actuator health — used by K8s liveness/readiness probes
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:9966/petclinic/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
