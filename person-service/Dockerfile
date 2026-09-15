# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
#
# Same layout requirement as movie-service: the shared proto directory is
# compiled from `srcDir("../proto")`, so /workspace/proto has to sit alongside
# /workspace/person-service. The compose context is therefore the repo root.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY proto/ ./proto/
COPY person-service/ ./person-service/

WORKDIR /workspace/person-service
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/person-service/build/libs/*.jar app.jar

USER 1001

# 8082 = HTTP (Actuator only), 9090 = gRPC consumed by movie-service
EXPOSE 8082 9090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
