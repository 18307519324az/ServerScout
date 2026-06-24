FROM node:20-bullseye AS frontend-build
WORKDIR /build/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.9-eclipse-temurin-17 AS backend-build
WORKDIR /build/backend
COPY backend/pom.xml ./
RUN mvn -B dependency:go-offline
COPY backend/src ./src
COPY backend/scripts ./scripts
COPY --from=frontend-build /build/frontend/dist ../frontend/dist
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update && \
    apt-get install -y nmap curl && \
    rm -rf /var/lib/apt/lists/*
COPY --from=backend-build /build/backend/target/server-scout-*.jar app.jar
COPY --from=backend-build /build/backend/scripts ./scripts
RUN mkdir -p /app/data /app/logs
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --retries=3 CMD curl -f http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
