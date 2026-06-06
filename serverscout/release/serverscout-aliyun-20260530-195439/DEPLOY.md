# ServerScout Alibaba Cloud Deployment

## Package Contents
- server-scout-1.0.0.jar
- start.sh
- serverscout.service
- nginx-serverscout.conf
- .env.example

## 1) Upload to server
```bash
scp -r serverscout-aliyun-*/ root@<your-server-ip>:/opt/serverscout/
```

## 2) Install runtime dependencies (Alibaba Cloud Linux)
```bash
sudo yum install -y java-17-openjdk-headless nginx
# optional (for Redis lock mode)
# sudo yum install -y redis
```

## 3) Prepare app directory
```bash
cd /opt/serverscout/serverscout-aliyun-*
cp .env.example .env
vi .env
chmod +x start.sh
```

## 4) Start once to verify
```bash
./start.sh
```

## 5) Register systemd service
```bash
sudo cp serverscout.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable serverscout
sudo systemctl restart serverscout
sudo systemctl status serverscout --no-pager
```

## 6) Configure Nginx
```bash
sudo cp nginx-serverscout.conf /etc/nginx/conf.d/serverscout.conf
sudo nginx -t
sudo systemctl restart nginx
```

## 7) If using Redis lock mode
```bash
# single host
sudo systemctl enable redis
sudo systemctl restart redis

# then in .env:
# SCAN_TARGET_CONCURRENCY_USE_REDIS=true
# SPRING_DATA_REDIS_HOST=127.0.0.1
# SPRING_DATA_REDIS_PORT=6379
```

## 8) Health check
```bash
curl -I http://127.0.0.1:8080/
```
