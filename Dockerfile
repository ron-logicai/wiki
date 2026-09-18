# --- Stage 1: build the BlockNote editor bundle -----------------------------
FROM node:26-alpine AS frontend
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# --- Stage 2: build the Spring Boot jar --------------------------------------
FROM eclipse-temurin:25-jdk AS backend
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src/ src/
# The frontend build writes into src/main/resources/static/editor; copy it in.
COPY --from=frontend /build/src/main/resources/static/editor src/main/resources/static/editor
RUN ./mvnw -q -DskipTests package

# --- Stage 3: runtime --------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
  && useradd --system --uid 1001 wiki && mkdir -p /app/data && chown wiki /app/data
COPY --from=backend /build/target/*.jar app.jar
USER wiki
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
