# Demo Mode 演示模式

## 什么是 Demo Mode

Demo Mode 是 ServerScout 内置的演示模式。开启后，系统使用模拟数据替代真实 Nmap / Nuclei 扫描，无需安装任何扫描工具即可完整演示从登录 → 仪表盘 → 创建扫描 → 实时进度 → 扫描完成 → 资产列表 → 漏洞列表 → 风险评分 → 报告导出的全流程。

## 为什么需要 Demo Mode

- **降低演示门槛**：现场演示无需依赖 Nmap、Nuclei 安装和公网目标
- **教学场景**：初学者可零配置体验完整的扫描与分析流程
- **面试展示**：在无法安装工具的环境中也能展示全栈能力
- **开发调试**：后端开发不依赖外部工具即可验证扫描流程

## 开启与关闭

Demo Mode 通过环境变量 `SCANNER_DEMO_MODE` 控制：

```yaml
# application.yml
app:
  scan:
    demo-mode: ${SCANNER_DEMO_MODE:true}
```

```bash
# 开启 Demo Mode（默认）
SCANNER_DEMO_MODE=true

# 关闭 Demo Mode，恢复真实扫描
SCANNER_DEMO_MODE=false
```

## Demo Mode 行为

### 支持的扫描类型

DemoScannerStrategy 支持所有扫描类型：`quick`、`full`、`nuclei`、`custom`。

### 模拟数据规模

| 扫描类型 | 资产数 | 端口数/资产 | 漏洞数 | 适用场景 |
|----------|--------|------------|--------|----------|
| quick | 2-3 | 2-4 | 1-2（LOW/MEDIUM） | 快速演示 |
| full | 3-5 | 3-8 | 5-8（全部等级） | 完整流程展示 |
| nuclei | 0 | — | 5-8（全部等级） | 漏洞检测展示 |

### IP 衍生规则

```
127.0.0.1     → 127.0.0.1, 127.0.0.2, 127.0.0.3
192.168.1.0/24 → 192.168.1.10, 192.168.1.20, 192.168.1.30
无法解析      → 192.168.56.10, 192.168.56.20, 192.168.56.30
```

### 模拟服务端口池

| 端口 | 服务 | 版本 |
|------|------|------|
| 22 | SSH | OpenSSH |
| 80 | HTTP | nginx |
| 443 | HTTPS | nginx |
| 3306 | MySQL | 8.0 |
| 6379 | Redis | 7.0 |
| 8080 | Spring Boot | — |
| 9200 | Elasticsearch | — |

### 漏洞等级分布

Demo 漏洞覆盖 CRITICAL / HIGH / MEDIUM / LOW 全部等级，引用 CveDatabase 中已有的 CVE ID，每个漏洞包含名称、CVE ID、严重等级、来源标记 `[Demo]`。

## Demo Mode 下的阶段状态机

Demo 模式完整经历 9 个扫描阶段，每个阶段产生真实的进度百分比：

| 进度 | 阶段 | Demo 行为 |
|------|------|-----------|
| 10% | 目标校验 | 快速校验，300ms sleep |
| 25% | 端口扫描 | 模拟扫描，500ms sleep |
| 40% | 服务识别 | 识服务别，400ms sleep |
| 57% | 漏洞检测 | 生成模拟漏洞，500ms sleep |
| 75% | 风险分析 | 计算风险评分 |
| 90% | 结果保存 | 保存数据，400ms sleep |
| 100% | 完成 | Webhook 回调 |

## Demo Mode + 风险评分

Demo 扫描完成后，自动调用 `RiskScoreService.calculateForTask()` 为该任务生成风险评分。因此：
- Dashboard Risk Top 5 直接展示 Demo 数据的风险排行
- 扫描详情页自动展示风险评分模块（子分数 + 风险原因 + 修复建议）
- 风险评分接口 `/api/v1/risk-scores/task/{taskId}` 正常返回数据

## Demo Mode + 报告导出

Demo 模式生成的数据同样支持 PDF / Excel 报告导出。报告中会标记为"演示数据"。

## 检测 Demo Mode

前端通过以下接口检测当前是否处于 Demo Mode：

```http
GET /api/v1/config/demo-mode
→ {"code": 200, "data": {"demoMode": true, "message": "..."}}
```

前端侧边栏底部根据该接口显示"演示模式"黄色标签。

## Demo Mode 下的安全防护

- Demo Mode 不执行真实 Nmap / Nuclei
- Demo Mode 不扫描公网目标
- Demo Mode 不加载真实漏洞利用代码
- Demo Mode 不影响非 Demo Mode 的执行流程

---

## Demo Mode vs Real Mode

| 对比项 | Demo Mode | Real Mode |
|--------|-----------|-----------|
| 环境变量 | `SCANNER_DEMO_MODE=true` | `SCANNER_DEMO_MODE=false` |
| 扫描引擎 | DemoScannerStrategy（模拟数据） | Nmap + Nuclei（真实工具） |
| 是否访问真实目标 | 否 | 是 |
| 数据来源 | 预置模拟数据 | 真实扫描结果 |
| Nmap 检测状态 | 不影响行为 | 必须可用 |
| Nuclei 检测状态 | 不影响行为 | 可选 |
| 外部工具依赖 | 无 | 需要 Nmap/Nuclei |
| 创建任务授权确认 | 不要求 | 强制要求 |
| 报告声明 | 标注为演示数据 | 标注为真实扫描 |
| scanMode 字段 | DEMO | REAL |
| 适用场景 | 演示、教学、开发调试 | 真实安全评估 |

## 常见问题

### 为什么 Demo Mode 不执行 Nmap / Nuclei？

Demo Mode 的目的是零依赖演示。如果执行真实 Nmap/Nuclei，则需要目标可达、工具安装、权限配置等多个前提条件，违背了演示模式"开机即用"的设计目标。

### Nmap 已检测到，为什么扫描还是 Demo 数据？

Nmap 已检测到只代表系统中存在 Nmap 可执行文件。当前是否真实执行扫描由 `SCANNER_DEMO_MODE` 环境变量决定。Demo Mode 下即使 Nmap 可用，也不会调用。

### 如何切换到 Real Mode？

修改 `.env` 文件：

```env
SCANNER_DEMO_MODE=false
```

然后重启服务：

```bash
docker compose down
docker compose up -d --build
```

### 如何确认当前模式？

- 后端 API：`GET /api/v1/system/mode` 返回完整的模式信息
- 前端侧边栏：显示"演示模式"（黄色）或"真实模式"（橙色）标签
- 设置页面：显示运行模式信息卡片
- 系统启动日志：启动时会输出当前运行模式

### scanMode 字段含义

每个扫描任务在创建时会记录当时的运行模式，存入 `scanMode` 字段：
- `DEMO`：创建时系统处于 Demo Mode
- `REAL`：创建时系统处于 Real Mode

该字段不会随系统模式切换而改变，用于追溯历史任务的数据来源。

### 报告声明的区别

- Demo Mode 报告声明：*"本报告由 Demo Mode 生成，数据为模拟演示数据，不代表真实资产风险。"*
- Real Mode 报告声明：*"本报告基于真实扫描结果生成，仅适用于授权资产安全评估。"*
