#!/bin/bash
set -euo pipefail

APP_NAME="server-scout"
APP_VERSION="1.0.0"
DEPLOY_DIR="deploy"
JAR_FILE="backend/target/${APP_NAME}-${APP_VERSION}.jar"

echo "========================================"
echo "ServerScout 部署打包脚本"
echo "========================================"

echo "[1/4] 构建前端"
if [ ! -d "frontend/dist" ] || [ ! -f "frontend/dist/index.html" ]; then
  cd frontend
  npm install
  npm run build
  cd ..
fi

echo "[2/4] 打包后端"
cd backend
mvn clean package -DskipTests
cd ..

echo "[3/4] 准备部署目录"
rm -rf "${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}"
cp "${JAR_FILE}" "${DEPLOY_DIR}/"

cat > "${DEPLOY_DIR}/start.sh" <<'EOF'
#!/bin/bash
set -euo pipefail

export SERVER_PORT="${SERVER_PORT:-8080}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://localhost:3306/serverscout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-serverscout}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-please_change_me}"
export JWT_SECRET="${JWT_SECRET:-please_change_me_at_least_32_chars}"
export LOG_LEVEL="${LOG_LEVEL:-INFO}"
export NMAP_PATH="${NMAP_PATH:-nmap}"
export NUCLEI_PATH="${NUCLEI_PATH:-nuclei}"
export PDF_FONT_PATH="${PDF_FONT_PATH:-/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc}"

JAVA_OPTS="${JAVA_OPTS:--Xms512m -Xmx1024m -XX:+UseG1GC}"
java ${JAVA_OPTS} -jar server-scout-1.0.0.jar
EOF
chmod +x "${DEPLOY_DIR}/start.sh"

cat > "${DEPLOY_DIR}/serverscout.service" <<'EOF'
[Unit]
Description=ServerScout 服务
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/serverscout
EnvironmentFile=-/opt/serverscout/.env
ExecStart=/bin/bash /opt/serverscout/start.sh
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
EOF

cat > "${DEPLOY_DIR}/nginx-serverscout.conf" <<'EOF'
server {
    listen 80;
    server_name your-domain.example.com;

    access_log /var/log/nginx/serverscout_access.log;
    error_log /var/log/nginx/serverscout_error.log;
    client_max_body_size 10m;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }
}
EOF

cat > "${DEPLOY_DIR}/.env.example" <<'EOF'
SERVER_PORT=8080
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/serverscout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
SPRING_DATASOURCE_USERNAME=serverscout
SPRING_DATASOURCE_PASSWORD=please_change_me
JWT_SECRET=please_change_me_at_least_32_chars
NMAP_PATH=nmap
NUCLEI_PATH=nuclei
LOG_LEVEL=INFO
PDF_FONT_PATH=/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc
EOF

echo "[4/4] 部署包已生成"
echo "部署目录：${DEPLOY_DIR}"
echo "建议上传后根据 .env.example 创建 .env，并修改数据库密码与 JWT 密钥。"
