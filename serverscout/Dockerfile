# Multi-stage build for ServerScout
# Stage 1: Build frontend + backend
FROM eclipse-temurin:17-jdk AS build
WORKDIR /build

# Install Node.js for frontend build
RUN apt-get update && apt-get install -y curl && \
    curl -fsSL https://deb.nodesource.com/setup_20.x | bash - && \
    apt-get install -y nodejs && rm -rf /var/lib/apt/lists/*

# Copy Maven wrapper and pom
COPY backend/pom.xml backend/
COPY backend/.mvn backend/.mvn
COPY backend/mvnw backend/

# Cache Maven dependencies
WORKDIR /build/backend
RUN ./mvnw dependency:go-offline -B -q || true

# Copy backend source
COPY backend/src backend/src

# Build frontend
WORKDIR /build
COPY frontend/package.json frontend/package-lock.json frontend/
WORKDIR /build/frontend
RUN npm ci --legacy-peer-deps && \
    npm run build

# Build backend with frontend dist
WORKDIR /build/backend
COPY frontend/dist /build/frontend/dist
RUN mkdir -p src/main/resources/static && \
    cp -r /build/frontend/dist/* src/main/resources/static/ && \
    ./mvnw package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:17-jre
WORKDIR /app

# Install nmap and nuclei for scanning
RUN apt-get update && \
    apt-get install -y nmap curl && \
    rm -rf /var/lib/apt/lists/*

COPY --from=build /build/backend/target/server-scout-*.jar app.jar
COPY --from=build /build/backend/scripts scripts

RUN mkdir -p /app/data /app/logs

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
