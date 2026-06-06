#!/bin/bash
# ============================================
# ServerScout 阿里云部署脚本
# ============================================
set -e

echo "========================================"
echo "  ServerScout 生产部署打包"
echo "========================================"

# 颜色输出
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

APP_NAME="server-scout"
APP_VERSION="1.0.0"
JAR_FILE="backend/target/${APP_NAME}-${APP_VERSION}.jar"
DEPLOY_DIR="deploy"

# 1. 检查前端是否已构建
echo -e "${YELLOW}[1/4] 检查前端构建...${NC}"
if [ ! -d "frontend/dist" ] || [ ! -f "frontend/dist/index.html" ]; then
    echo "前端未构建，正在构建..."
    cd frontend
    npm install --silent
    npm run build
    cd ..
else
    echo -e "${GREEN}前端已构建 ✓${NC}"
fi

# 2. 打包后端 (包含前端静态资源)
echo -e "${YELLOW}[2/4] 打包后端 (含前端静态资源)...${NC}"
cd backend
mvn clean package -DskipTests -q
cd ..
echo -e "${GREEN}后端打包完成 ✓${NC}"

# 3. 准备部署目录
echo -e "${YELLOW}[3/4] 准备部署文件...${NC}"
rm -rf "${DEPLOY_DIR}"
mkdir -p "${DEPLOY_DIR}"

cp "${JAR_FILE}" "${DEPLOY_DIR}/"

# 复制部署辅助文件
cat > "${DEPLOY_DIR}/start.sh" << 'STARTEOF'
#!/bin/bash
# ServerScout 启动脚本
# 用法: ./start.sh [环境]
# 环境: dev (开发) | prod (生产, 默认)

ENV=${1:-prod}

# JVM 参数
JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC"

# 环境变量 (生产环境请修改)
export SERVER_PORT=8080
export DB_URL="jdbc:mysql://localhost:3306/serverscout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
export DB_USERNAME="serverscout"
export DB_PASSWORD="1A5089879282"
export JWT_SECRET="change-me-to-a-random-string-at-least-32-chars"
export LOG_LEVEL="INFO"

# PDF 中文字体路径 (阿里云 Linux)
if [ -f "/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc" ]; then
    export PDF_FONT_PATH="/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"
elif [ -f "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc" ]; then
    export PDF_FONT_PATH="/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"
fi

echo "========================================"
echo "  ServerScout 启动中..."
echo "  环境: ${ENV}"
echo "  端口: ${SERVER_PORT}"
echo "========================================"

java ${JAVA_OPTS} -jar server-scout-1.0.0.jar
STARTEOF
chmod +x "${DEPLOY_DIR}/start.sh"

# systemd 服务文件
cat > "${DEPLOY_DIR}/serverscout.service" << 'SVCEOF'
[Unit]
Description=ServerScout - 服务器资产攻击面可视化分析平台
After=network.target mysql.service

[Service]
Type=simple
User=root
WorkingDirectory=/opt/serverscout
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -XX:+UseG1GC -jar /opt/serverscout/server-scout-1.0.0.jar
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

# 环境变量 (请根据实际修改)
Environment="SERVER_PORT=8080"
Environment="DB_URL=jdbc:mysql://localhost:3306/serverscout?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai"
Environment="DB_USERNAME=serverscout"
Environment="DB_PASSWORD=1A5089879282"
Environment="JWT_SECRET=change-me-to-a-random-string-at-least-32-chars"
Environment="LOG_LEVEL=INFO"
Environment="PDF_FONT_PATH=/usr/share/fonts/truetype/noto/NotoSansCJK-Regular.ttc"

[Install]
WantedBy=multi-user.target
SVCEOF

# Nginx 配置示例
cat > "${DEPLOY_DIR}/nginx-serverscout.conf" << 'NGXEOF'
# ServerScout Nginx 反向代理配置
# 放置到 /etc/nginx/conf.d/serverscout.conf

server {
    listen 80;
    server_name your-domain.com;  # 修改为你的域名或服务器IP

    # 日志
    access_log /var/log/nginx/serverscout_access.log;
    error_log /var/log/nginx/serverscout_error.log;

    # 客户端上传大小限制
    client_max_body_size 10m;

    # 后端 API 代理 (如果需要前后端分离部署)
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 300s;
    }

    # 前端静态文件 (如果前后端合并部署，后端直接提供静态文件)
    # 以下配置仅在使用前后端分离时使用
    # location / {
    #     root /opt/serverscout/static;
    #     index index.html;
    #     try_files $uri $uri/ /index.html;
    # }
}
NGXEOF

echo -e "${GREEN}部署文件准备完成 ✓${NC}"

# 4. 输出部署信息
echo -e "${YELLOW}[4/4] 部署包信息${NC}"
echo "========================================"
echo -e "JAR 文件: ${DEPLOY_DIR}/$(ls ${DEPLOY_DIR}/*.jar | xargs basename)"
echo -e "启动脚本: ${DEPLOY_DIR}/start.sh"
echo -e "Systemd:   ${DEPLOY_DIR}/serverscout.service"
echo -e "Nginx:     ${DEPLOY_DIR}/nginx-serverscout.conf"
echo ""
echo -e "${GREEN}部署步骤:${NC}"
echo "1. 将 deploy/ 目录上传到阿里云服务器:"
echo "   scp -r deploy/ root@your-server:/opt/serverscout/"
echo ""
echo "2. SSH 连接到服务器并配置:"
echo "   ssh root@your-server"
echo "   cd /opt/serverscout"
echo "   # 编辑 start.sh 修改数据库密码等配置"
echo "   vi start.sh"
echo ""
echo "3. 使用 systemd 管理服务 (推荐):"
echo "   cp serverscout.service /etc/systemd/system/"
echo "   systemctl daemon-reload"
echo "   systemctl enable serverscout"
echo "   systemctl start serverscout"
echo "   systemctl status serverscout"
echo ""
echo "4. 配置 Nginx (可选):"
echo "   cp nginx-serverscout.conf /etc/nginx/conf.d/"
echo "   nginx -t && systemctl reload nginx"
echo "========================================"
