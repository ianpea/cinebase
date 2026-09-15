# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Build stage
#
# build.gradle.kts configures `srcDir("../proto")`, so the proto directory has
# to sit next to the service directory. That is why the compose build context is
# the repository root and the copy below preserves that layout in /workspace.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY proto/ ./proto/
COPY movie-service/ ./movie-service/

WORKDIR /workspace/movie-service
RUN chmod +x gradlew && ./gradlew --no-daemon bootJar

# ---------------------------------------------------------------------------
# Runtime stage
# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /workspace/movie-service/build/libs/*.jar app.jar

# Uploaded artwork is written here. Compose bind-mounts ./data/uploads over this
# path, so the directory has to exist and be owned by the user the app runs as.
RUN mkdir -p /app/uploads/artworks && chown -R 1001:1001 /app

USER 1001

ENV UPLOADS_DIR=/app/uploads
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
