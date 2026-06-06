#!/usr/bin/env bash
set -euo pipefail

APP_JAR="server-scout-1.0.0.jar"
JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"

export SERVER_PORT="${SERVER_PORT:-8080}"
export SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"

# Production DB defaults (original packaging defaults)
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://127.0.0.1:3306/serverscout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-serverscout}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-1A5089879282}"

export JWT_SECRET="${JWT_SECRET:-change-me-to-at-least-32-chars}"
export LOG_LEVEL="${LOG_LEVEL:-INFO}"

# Redis switch (optional)
export SCAN_TARGET_CONCURRENCY_USE_REDIS="${SCAN_TARGET_CONCURRENCY_USE_REDIS:-false}"
export SPRING_DATA_REDIS_HOST="${SPRING_DATA_REDIS_HOST:-127.0.0.1}"
export SPRING_DATA_REDIS_PORT="${SPRING_DATA_REDIS_PORT:-6379}"

# Optional: Playwright/Node path for backend screenshot feature
# export PLAYWRIGHT_NODE_PATH=/usr/bin/node

exec java ${JAVA_OPTS} -jar "${APP_JAR}" --server.port="${SERVER_PORT}"
