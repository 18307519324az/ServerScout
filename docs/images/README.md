# 功能截图

本目录包含 ServerScout 的功能截图，在 Demo Mode 下通过 Puppeteer 自动化截取。

## 截图清单

| 文件名 | 内容 | 大小 |
|--------|------|------|
| `login-page.png` | 登录页面 — 科技感登录界面，支持账号/密码/验证码 | 668 KB |
| `dashboard.png` | 仪表盘首页 — 风险评分 Banner、概览卡片、端口分布、趋势图、风险 Top 5 | 100 KB |
| `scan-task-detail.png` | 扫描任务详情 — 阶段状态机进度条、Pipeline 日志、风险评分模块 | 107 KB |
| `scan-stages.png` | 扫描阶段状态展示 — 9 阶段状态机进度展示 | 107 KB |
| `risk-score.png` | 风险评分详情 — 5 因子子分数、风险原因、修复建议 | 100 KB |
| `vulnerabilities.png` | 漏洞列表 — 分级筛选、CVE 展示、状态管理 | 146 KB |
| `reports.png` | 报告中心 — 任务列表、PDF/Excel 下载、Demo 标记 | 106 KB |
| `assets-list.png` | 资产列表 — 资产清单、端口统计、标签管理 | 128 KB |
| `topology.png` | 攻击面拓扑 — 资产关系可视化地图 | 180 KB |
| `docker-running.png` | Docker 运行状态 — 需在 Docker 环境可用时截取 `docker compose ps` 输出 | — |

## 截图来源

截图通过 Puppeteer + Dev-login 端点自动化截取，使用 1920×1080 视口，在 Demo Mode 下运行。

## 重新截图

如需重新截图，执行以下命令：

```bash
# 1. 确保后端和前端已启动
cd backend && mvn spring-boot:run -Dspring-boot.run.profiles=dev
cd frontend && npm run dev

# 2. 安装依赖并运行截图脚本
npm install puppeteer
node take-screenshots.js
```
