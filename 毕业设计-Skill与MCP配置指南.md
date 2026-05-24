# 毕业设计 — ServerScout 攻击面可视化分析平台

> 技术栈: React 18 + TypeScript + Vite + Tailwind CSS + Spring Boot 3 + Java 17 + MySQL
> 开发工具: Claude Code + Agent Skills + MCP 生态
> GitHub: https://github.com/18307519324az/-

---

## 目录

1. [项目概述](#1-项目概述)
2. [环境要求](#2-环境要求)
3. [快速安装脚本](#3-快速安装脚本)
4. [Skill 体系详解](#4-skill-体系详解)
5. [MCP 服务器配置](#5-mcp-服务器配置)
6. [CLAUDE.md 配置](#6-claudemd-配置)
7. [使用工作流](#7-使用工作流)
8. [技能学习总结](#8-技能学习总结)
9. [第十轮迭代 — AI 驱动的最终完善](#9-第十轮迭代--ai-驱动的最终完善)
10. [项目完整功能清单](#10-项目完整功能清单)
11. [待优化项](#11-待优化项)
12. [遇到的困难与教训](#12-遇到的困难与教训)
13. [项目总结](#13-项目总结)

---

## 1. 项目概述

ServerScout 是一个服务器资产攻击面可视化分析平台，基于 B/S 架构实现：

- **前端**：React 18 + TypeScript + Vite + Tailwind CSS，提供仪表盘、资产管理、漏洞跟踪、扫描任务、系统设置等页面
- **后端**：Spring Boot 3 + Java 17 + MySQL，提供 REST API、JWT 认证、异步扫描执行、通知服务
- **核心能力**：Nmap 主机发现与端口扫描、Nuclei 漏洞检测、Web 指纹识别、SSL 证书采集、CVE 匹配、Web 爬虫、子域名枚举、PDF/Excel 报告导出
- **通知体系**：钉钉/飞书/企业微信 Webhook + 邮件通知（SMTP）
- **外部情报**：Censys IP 查询 + VirusTotal IP/域名查询 + NVD CVE 数据库

---

## 2. 环境要求

| 工具 | 版本要求 | 用途 |
|------|---------|------|
| Node.js | >= 18 | 运行 MCP 服务器 |
| Claude Code CLI | >= 2.1 | AI 编程助手 |
| Git | >= 2.0 | 版本控制 |
| Java | >= 17 | Spring Boot 后端 |
| Maven | >= 3.8 | 后端构建 |
| MySQL | >= 8.0 | 数据持久化 |
| Nmap | >= 7.0 | 主机发现与端口扫描 |
| Nuclei | >= 3.0 | 漏洞扫描引擎 |
| npm / npx | >= 9 | 包管理 |

检查命令：

```bash
node --version
npm --version
claude --version
git --version
java --version
mvn --version
nmap --version
nuclei -version
```

---

## 3. 快速安装脚本

### 3.1 一键安装所有 Skill

```bash
# 安装 mattpocock/skills（4个核心技能）
npx skills@latest add mattpocock/skills

# 或者只安装单个 skill
npx skills@latest add mattpocock/skills/grill-me
npx skills@latest add mattpocock/skills/tdd
npx skills@latest add mattpocock/skills/git-guardrails-claude-code
npx skills@latest add mattpocock/skills/to-prd

# 安装 Skill Olympus
git clone --depth 1 https://github.com/Dannykkh/skill-olympus.git ~/.claude/skills/skill-olympus
```

### 3.2 一键添加所有 MCP 服务器

```bash
# ===== 全栈脚手架 MCP =====
claude mcp add software-engineer -- npx -y @rajawatrajat/mcp-software-engineer

# ===== Swagger MCP（连接 Spring Boot 后端）=====
claude mcp add springboot-mcp -- npx -y @pradeepmajji702/springboot-mcp \
  --env SWAGGER_URL=http://localhost:8080/v3/api-docs

# ===== seed4j MCP（项目脚手架生成）=====
claude mcp add seed4j -- npx -y seed4j-mcp \
  --env SEED4J_BASE_URL=http://localhost:1339

# ===== GitHub MCP =====
claude mcp add -e GITHUB_PERSONAL_ACCESS_TOKEN=ghp_your_token_here \
  github -- npx -y @modelcontextprotocol/server-github

# ===== Playwright MCP（E2E 测试）=====
claude mcp add playwright -- npx @playwright/mcp@latest
npx playwright install
npx playwright install-deps  # Linux 需要

# ===== Supabase/PostgreSQL MCP =====
claude mcp add supabase-db -- npx mcp-supabase-db \
  --env POSTGRES_URL_NON_POOLING=postgresql://user:password@host:5432/database
```

---

## 4. Skill 体系详解

### 4.1 Skill Olympus（全流程 Orchestration）

**仓库**: [Dannykkh/skill-olympus](https://github.com/Dannykkh/skill-olympus)
**规模**: 98 skills + 49 agents + 13 hooks
**核心**: 一条 `/zeus` 命令跑完整 pipeline

#### 工作流 Pipeline

```
/zeus "构建一个电商网站，React + Spring Boot + PostgreSQL"

Phase 1: Analyze    -> /hermes     -> 业务分析、TAM/SAM/SOM
Phase 2: Challenge  -> /athena     -> Go/No-Go 决策
Phase 3: Design     -> /zephermine -> 26步深度访谈 -> SPEC.md
Phase 4: Implement  -> /poseidon   -> 并行实现（波次分组）
Phase 5: Inspect    -> /argos      -> 代码对照设计验证
Phase 6: Test       -> /minos      -> Playwright E2E + 修复循环
Phase 7: Deliver    -> /clio       -> 流程图 + PRD + 文档
```

#### 常用命令

| 命令 | 功能 | 适合场景 |
|------|------|---------|
| `/zeus` | 全流程编排 | 从零搭建整个项目 |
| `/chronos` | 自动修复 Bug FIND->FIX->VERIFY | 调试阶段 |
| `/argos` | 代码对照设计规范检查 | Code Review |
| `/minos` | Playwright 测试 | 前端 E2E 测试 |
| `/clio` | 生成文档、PRD、流程图 | 写毕业设计文档 |
| `/hestia` | 死代码/未使用导出扫描 | 代码清理 |
| `/adr` | 架构决策记录 | 记录技术选型 |

---

### 4.2 mattpocock/skills（代码质量保障）

**仓库**: [mattpocock/skills](https://github.com/mattpocock/skills)
**规模**: 30K+ Stars，4 个核心技能
**哲学**: 真正的工程师技能，拒绝"氛围编程"

#### `/grill-me` — 设计审查

**SKILL.md 全文（仅 3 行）**：

```
Interview me relentlessly about every aspect of this plan until we reach
a shared understanding. Walk down each branch of the design tree resolving
dependencies between decisions one by one.

If a question can be answered by exploring the codebase, explore the codebase
instead.

For each question, provide your recommended answer.
```

**用法**: 输入 `/grill-me`，AI 会逐问审查你的设计方案

**典型问题示例**（以设计缓存功能为例）：
- 缓存粒度是函数级还是请求级？
- 缓存键冲突时的失效策略是什么？
- 缓存服务宕机的降级方案？
- 测试如何 mock 缓存？

#### `/tdd` — 测试驱动开发

**核心流程**:

```
RED:   编写失败测试（定义期望行为）
  |
  v
GREEN: 编写最简实现代码使测试通过
  |
  v
REFACTOR: 在测试保护下重构代码
```

**原则**:
- 测试行为而非实现（通过公共接口测试）
- 垂直切片（一个测试 -> 一个实现）
- 优先集成测试
- 禁止水平切片（先写完所有测试再写实现）
- 禁止在 RED 状态重构

**用法**: 输入 `/tdd` 开始 TDD 流程

#### `/git-guardrails` — Git 安全防护

阻止危险 Git 命令（`push`、`reset --hard`、`clean` 等）在未确认前执行。

**安装**: `npx skills@latest add mattpocock/skills/git-guardrails-claude-code`

#### `/to-prd` — 对话转 PRD

将当前对话上下文自动转化为 PRD，并提交为 GitHub Issue。

**用法**: 讨论完需求后输入 `/to-prd`

---

### 4.3 awesome-claude-skills（精选技能包）

**仓库**: [karanb192/awesome-claude-skills](https://github.com/karanb192/awesome-claude-skills)
**规模**: 50+ 已验证技能

#### artifacts-builder（前端组件生成）

官方名: `web-artifacts-builder`
来源: Anthropic 官方 skills 仓库

**技术栈**: React 18 + TypeScript + Vite + Tailwind CSS + shadcn/ui

**流程**:
1. `bash scripts/init-artifact.sh <项目名>` — 初始化项目
2. 编辑代码开发组件
3. `bash scripts/bundle-artifact.sh` — 打包为单 HTML 文件

**用法**: 在 Claude Code 中描述你要的 UI 组件，AI 自动构建

#### subagent-driven-development（多智能体开发）

来源: obra/superpowers

**核心**: 每个任务分配一个独立的子智能体，完成后经两阶段审查

**工作流**:

```
任务提取 -> 为每个任务：
  1. 分发实现子智能体
  2. 规范符合性审查（代码是否匹配需求？）
  3. 代码质量审查（实现是否良好？）
  4. 修复问题 -> 循环直到通过
```

**子智能体状态**:
- DONE -> 进入审查
- DONE_WITH_CONCERNS -> 先阅读顾虑
- NEEDS_CONTEXT -> 补充上下文
- BLOCKED -> 评估阻塞原因

---

## 5. MCP 服务器配置

### 5.1 全栈脚手架 MCP（mcp-software-engineer）

**用途**: 让 AI 成为全栈工程师，自动创建项目脚手架、数据库操作、API 开发

**配置**（`~/.claude.json` 或项目 `.mcp.json`）：

```json
{
  "mcpServers": {
    "software-engineer": {
      "command": "npx",
      "args": ["-y", "@rajawatrajat/mcp-software-engineer"],
      "env": {}
    }
  }
}
```

**提供工具**:

| 类别 | 工具 |
|------|------|
| 项目管理 | create_project、read_file、write_file、search_files |
| 数据库 | init_database、create_migration、generate_model、query_database |
| 前端 | create_component、setup_styling、setup_routing |
| 后端 | create_api_endpoint、setup_authentication、setup_websockets |
| DevOps | create_dockerfile、deploy_to_cloud、setup_ci_cd |

---

### 5.2 Swagger MCP（Spring Boot <-> React 桥接）

**用途**: 读取 Spring Boot Swagger/OpenAPI 文档，自动生成前端 API 调用代码

**配置**:

```json
{
  "mcpServers": {
    "springboot-mcp": {
      "command": "npx",
      "args": ["-y", "@pradeepmajji702/springboot-mcp"],
      "env": {
        "SWAGGER_URL": "http://localhost:8080/v3/api-docs"
      }
    }
  }
}
```

**注意**: 你的 Spring Boot 项目需要启用 Swagger/OpenAPI

```xml
<!-- pom.xml 依赖 -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.6.0</version>
</dependency>
```

**不同版本的 Swagger URL**:

| Spring Boot 版本 | URL |
|-----------------|-----|
| Spring Boot 3 | http://localhost:8080/v3/api-docs |
| Spring Boot 2 | http://localhost:8080/v2/api-docs |

**对 AI 说的指令示例**:
- "列出所有后端 API 端点"
- "描述 GET /api/users 端点"
- "为 POST /api/users 生成 React API 调用代码"

---

### 5.3 seed4j MCP（项目脚手架生成器）

**用途**: AI 智能体自动生成 Spring Boot + Vue/React 项目结构

**架构**:

```
[Claude Code] <-> STDIO <-> [seed4j-mcp] <-> HTTP <-> [seed4j 服务]
```

**配置**:

```json
{
  "mcpServers": {
    "seed4j": {
      "command": "npx",
      "args": ["-y", "seed4j-mcp"],
      "env": {
        "SEED4J_BASE_URL": "http://localhost:1339"
      }
    }
  }
}
```

**用法示例**:
> "在 /tmp/my-webapp 用 Vue + Spring Boot preset 创建一个新项目"

AI 将自动：列出 presets -> 选择匹配 -> 创建项目 -> 应用 preset

---

### 5.4 GitHub MCP

**用途**: 在 Claude Code 中直接管理 Issue、PR、代码搜索

**安装**:

```bash
claude mcp add -e GITHUB_PERSONAL_ACCESS_TOKEN=ghp_your_token \
  github -- npx -y @modelcontextprotocol/server-github
```

**配置文件方式**:

```json
{
  "mcpServers": {
    "github": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-github"],
      "env": {
        "GITHUB_PERSONAL_ACCESS_TOKEN": "${MY_GITHUB_TOKEN}"
      }
    }
  }
}
```

**功能**: Issue CRUD、PR Review、代码搜索、仓库管理

---

### 5.5 Playwright MCP（前端 E2E 测试）

**用途**: AI 驱动的浏览器自动化测试

**安装**:

```bash
claude mcp add playwright -- npx @playwright/mcp@latest
npx playwright install
npx playwright install-deps  # Linux only
```

**配置**:

```json
{
  "mcpServers": {
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp@latest"]
    }
  }
}
```

**支持 34 个工具**，包括 browser_navigate、browser_click、browser_type、browser_take_screenshot

---

### 5.6 Supabase MCP（数据库操作）

**用途**: 让 AI 直接操作 PostgreSQL/Supabase 数据库

**安装**:

```bash
claude mcp add supabase-db -- npx mcp-supabase-db \
  --env POSTGRES_URL_NON_POOLING=postgresql://user:password@host:5432/database
```

**配置文件**:

```json
{
  "mcpServers": {
    "supabase-db": {
      "command": "npx",
      "args": ["mcp-supabase-db"],
      "env": {
        "POSTGRES_URL_NON_POOLING": "postgresql://postgres:password@localhost:5432/graduation_project"
      }
    }
  }
}
```

---

### 5.7 完整 MCP 配置文件

**项目级配置**（`.mcp.json`，可提交到 Git）：

```json
{
  "mcpServers": {
    "software-engineer": {
      "command": "npx",
      "args": ["-y", "@rajawatrajat/mcp-software-engineer"]
    },
    "springboot-mcp": {
      "command": "npx",
      "args": ["-y", "@pradeepmajji702/springboot-mcp"],
      "env": {
        "SWAGGER_URL": "http://localhost:8080/v3/api-docs"
      }
    },
    "seed4j": {
      "command": "npx",
      "args": ["-y", "seed4j-mcp"],
      "env": {
        "SEED4J_BASE_URL": "http://localhost:1339"
      }
    },
    "playwright": {
      "command": "npx",
      "args": ["@playwright/mcp@latest"]
    }
  }
}
```

> **注意**: GitHub MCP 和 Supabase MCP 涉及 Token 密钥，建议放 `~/.claude.json` 用户级配置

---

## 6. CLAUDE.md 配置

项目根目录的 `CLAUDE.md` 文件告诉 Claude Code 这个项目的上下文。

```markdown
# 毕业设计项目

## 技术栈
- 前端: React 18 + TypeScript + Vite + Tailwind CSS
- 后端: Spring Boot 3 + Java 17
- 数据库: MySQL
- 构建工具: Maven (后端) / npm (前端)

## 目录结构
├── serverscout/frontend/   # React 前端项目
├── serverscout/backend/    # Spring Boot 后端项目
├── docs/                   # 项目文档
├── .claude/skills/         # 已安装的 Claude Code Skills
└── .mcp.json               # MCP 服务器配置

## 可用 Skill 命令

### 全流程开发
- /zeus "描述需求" -- 从分析到部署全自动（Skill Olympus）
- /chronos -- 自动修复 Bug 循环

### 代码质量
- /grill-me -- 设计审查，AI 逐问排查方案漏洞
- /tdd -- 测试驱动开发（RED->GREEN->REFACTOR）

### 文档与版本控制
- /to-prd -- 对话内容生成 PRD 文档
- /adr -- 记录架构决策

### 项目管理
- /clio -- 生成流程图、技术文档、用户手册

## 开发工作流
1. 需求分析 -> 用 /grill-me 审查方案
2. 设计 -> 用 /adr 记录技术选型
3. 实现 -> npm run dev / mvn spring-boot:run
4. 测试 -> 用 /tdd 做 TDD，用 Playwright MCP 做 E2E
5. 文档 -> 用 /to-prd 生成 PRD，用 /clio 生成文档
6. 提交 -> 用 GitHub MCP 管理 PR
```

---

## 7. 使用工作流

### 毕业设计典型开发流程

```
第 1 步：项目初始化
  /zeus "创建一个在线考试系统，前端 React + 后端 Spring Boot + PostgreSQL"

第 2 步：方案审查
  /grill-me  -> AI 逐问审查你的设计，你回答并完善

第 3 步：架构决策
  /adr "为什么选择 JWT 而不是 Session 认证" -> 记录决策

第 4 步：功能开发
  描述需求 -> AI 自动完成（借助 software-engineer MCP）
  /tdd -> 用 TDD 方式开发核心功能

第 5 步：前后端联调
  springboot-mcp 读取 Swagger -> 自动生成前端 API 代码

第 6 步：测试
  用 Playwright MCP 写 E2E 测试
  /minos -> 运行测试并修复

第 7 步：文档
  /to-prd -> 将讨论转化为 PRD
  /clio -> 生成架构图、技术文档

第 8 步：部署与提交
  GitHub MCP 管理 PR 和 Issue
  software-engineer 生成 Dockerfile + CI/CD 配置
```

---

## 8. 技能学习总结

### Skill Olympus -- 全流程 Orchestration

| 维度 | 内容 |
|------|------|
| 核心能力 | 一条命令跑完"分析->设计->实现->审查->测试->交付"全流程 |
| 规模 | 98 skills + 49 agents + 13 hooks |
| 架构 | 12 个希腊神祇命名的智能体，Zeus 作为总指挥 |
| 适用阶段 | 项目初始化、整体功能开发、自动化测试、文档生成 |
| 独特价值 | 零人工干预的完整 pipeline，适合毕设快速搭建原型 |

### mattpocock/skills -- 代码质量保障

| 技能 | 核心指令 | 适用场景 |
|------|---------|---------|
| /grill-me | "逐问审查方案，用代码库验证" | 设计阶段方案评审 |
| /tdd | "RED->GREEN->REFACTOR 垂直切片" | 核心功能开发 |
| /git-guardrails | "阻止危险 git 命令" | 所有阶段 |
| /to-prd | "对话内容转化为 PRD" | 需求明确后 |

**独特价值**: 4 个技能共约 20 行 SKILL.md，极简但极强。被 30K+ Stars 验证。

### awesome-claude-skills（精选）

| 技能 | 来源 | 用途 |
|------|------|------|
| web-artifacts-builder | Anthropic 官方 | 构建 React + Tailwind 前端 UI 组件 |
| subagent-driven-development | obra/superpowers | 多智能体并行开发 + 两阶段审查 |

**独特价值**: 50+ 社区验证技能的精选合集，覆盖全栈开发各环节。

---

## 9. 第十轮迭代 — AI 驱动的最终完善

> 时间：2026年5月24日
> 方式：纯对话式 AI 驱动开发，使用 Playwright MCP 实时验证

### 9.1 扫描工具配置增强

**Nuclei 自动检测**（[SystemConfigController.java](serverscout/backend/src/main/java/com/serverscout/controller/SystemConfigController.java) + [SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 为 Nuclei 路径新增独立的"自动检测"按钮（此前仅 Nmap 有）
- 修复自动检测后表单字段不更新的 Bug：原 `useEffect` 被 `toolConfigInitialized` ref 阻拦，修改后改为直接 `setToolConfig` 更新
- 后端新增 `GET /api/v1/config/detect-tool/{toolName}` 单工具端点，支持分工具检测

**文件浏览器定位**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 为 Nmap 和 Nuclei 路径各添加"浏览"按钮，打开原生文件选择器
- 核心难题：浏览器出于安全限制不暴露完整文件路径（仅返回 `C:\fakepath\filename.exe`）
- 解决方案：前端浏览 -> 后端新端点 `detect-tool/{toolName}` -> 用 `where`/`which` + 常见目录搜索解析完整路径 -> 回填输入框

**状态重置修复**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 修复"修改后不保存，刷新页面仍显示修改后内容"的 Bug
- 进入编辑模式时保存 `originalToolConfig`，取消时恢复

**后端检测路径扩展**（[SystemConfigController.java](serverscout/backend/src/main/java/com/serverscout/controller/SystemConfigController.java)）

新增常见安装路径：Chocolatey (`C:\ProgramData\chocolatey\bin\`)、Scoop (`%USERPROFILE%\scoop\shims\`)、Snap (`/snap/bin/`)、环境变量动态解析（`ProgramFiles`、`LOCALAPPDATA`），并加入空值安全处理

### 9.2 外部情报 API 配置修复

**Bug 1：保存按钮调用错误的 Mutation**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 问题："保存 API 密钥"按钮调用了 `updateWebhookMutation`（保存 Webhook 配置），导致 API 密钥从未被保存，toast 提示"告警通知配置已更新"
- 修复：新增 `updateApiKeysMutation`，构建 `{censys-api-id, censys-api-secret, virustotal-api-key}` 发送到后端

**Bug 2：非受控组件导致状态丢失**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 问题：输入框使用 `defaultValue`（非受控），首次渲染时 `configs` 为空则永远为空；清空保存后刷新仍显示旧值
- 修复：改为 `value` + `onChange` 受控组件，通过 `useEffect` + `apiKeysInitialized` ref 从后端配置初始化

**Bug 3：清空字段后无法保存**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 问题：`if (apiKeysConfig.censysId)` — 空字符串为 falsy，清空后该 key 被跳过，后端收到 `{}` 不更新
- 修复：始终发送全部三个 key，值为空字符串时后端正确清空

**API Key 教程面板**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 新增可折叠教程，分步说明 Censys 和 VirusTotal 的 API Key 获取流程

### 9.3 告警通知配置增强

**开关系统**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx) + [WebhookNotificationService.java](serverscout/backend/src/main/java/com/serverscout/service/WebhookNotificationService.java)）

- 新增全局开关 `webhook-enabled`（关闭后所有平台不发送）
- 新增分平台开关 `webhook-dingtalk-enabled`、`webhook-feishu-enabled`、`webhook-wecom-enabled`
- 查看模式展示丰富状态：绿色=工作中、灰色=已禁用、琥珀色=已启用但未配置URL

**Webhook 教程面板**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 钉钉/飞书/企业微信的分步 Webhook URL 获取教程，含官方文档链接

### 9.4 邮件通知配置增强

**查看/编辑模式**（[SettingsPage.tsx](serverscout/frontend/src/pages/SettingsPage.tsx)）

- 邮件配置区新增查看模式，展示收件邮箱、SMTP 服务器:端口、发件账号、连接方式等汇总信息
- 点击"编辑邮件配置"进入编辑表单，点击"取消"回到查看模式

### 9.5 通知内容详细化

**Webhook 通知**（[WebhookNotificationService.java](serverscout/backend/src/main/java/com/serverscout/service/WebhookNotificationService.java)）

从 4 行简单概要 -> 结构化 Markdown 报告：
- 发现资产（逐资产逐端口，含服务名和版本）
- 漏洞信息（按严重程度分组统计 + 排序，含 CVE ID 和描述）
- 爬虫信息（URL、HTTP 状态码、页面标题）
- 总计摘要行

**邮件通知**（[EmailNotificationService.java](serverscout/backend/src/main/java/com/serverscout/service/EmailNotificationService.java)）

从简单 HTML 表格 -> 完整专业报告：
- 蓝色渐变头部 + 概要表
- 资产与端口分级展示、漏洞详情彩色标签表、爬虫结果表
- 严重程度颜色编码（Critical=红/High=橙/Medium=黄/Low=绿）

**关键 Bug 修复：资产数为 0**（[WebhookNotificationService.java](serverscout/backend/src/main/java/com/serverscout/service/WebhookNotificationService.java) + [EmailNotificationService.java](serverscout/backend/src/main/java/com/serverscout/service/EmailNotificationService.java)）

- 问题：`AssetRepository.findByTaskId()` 仅返回首次发现的资产（`Asset.task` 只在创建时设置），已存在的资产通过 `ScanAssetMapping` 关联
- 修复：改为 `scanAssetMappingRepository.findByScanTaskIdWithAsset()` 获取映射再提取资产列表

---

## 10. 项目完整功能清单

### 10.1 后端（Spring Boot 3 + Java 17）

| 模块 | 功能 | 状态 |
|------|------|------|
| 认证授权 | JWT 登录/注册 + 验证码 + 角色权限（ADMIN/USER） | ✅ 完成 |
| 资产管理 | CRUD + 分页 + 搜索 + 标签 + 合并 + 攻击面统计 | ✅ 完成 |
| 拓扑图 | 资产关联关系可视化数据 | ✅ 完成 |
| 扫描任务 | 创建/取消/删除 + 分页 + SSE 实时进度推送 | ✅ 完成 |
| Nmap 扫描 | quick/full/custom 三种模式，XML 解析 | ✅ 完成 |
| Nuclei 扫描 | vuln/nuclei 模式，正则解析 | ✅ 完成 |
| 自定义扫描 | L2 插件系统，命令模板 + 结果解析器 | ✅ 完成 |
| Web 指纹 | 310+ 规则指纹识别 | ✅ 完成 |
| SSL 证书 | 采集 + 解析 + 过期检测 | ✅ 完成 |
| CVE 匹配 | NVD 数据库 + 版本匹配 + EPSS 评分 | ✅ 完成 |
| Web 爬虫 | URL 发现 + 截图 + 响应分析 | ✅ 完成 |
| 子域名枚举 | DNS 爆破 + 证书透明度 | ✅ 完成 |
| 漏洞管理 | CRUD + 状态流转 + 复现步骤 + 修复建议 | ✅ 完成 |
| PDF/Excel 导出 | JasperReports + Apache POI | ✅ 完成 |
| 外部情报 | Censys + VirusTotal + NVD CVE 查询 | ✅ 完成 |
| 定时扫描 | 每日巡检 + 每周全面扫描，Cron 表达式 | ✅ 完成 |
| Webhook 通知 | 钉钉 Markdown + 飞书卡片 + 企业微信 Markdown | ✅ 完成 |
| 邮件通知 | SMTP + HTML 报告 + SSL/TLS | ✅ 完成 |
| 操作日志 | AOP 拦截 + 分页查询 + 按用户/类型过滤 | ✅ 完成 |
| 工具检测 | where/which + 常见路径搜索 + 单工具检测端点 | ✅ 完成 |
| 用户管理 | CRUD + 密码重置 + 多用户数据隔离 | ✅ 完成 |
| 多用户隔离 | createdBy 字段 + Repository 级别过滤 | ✅ 完成 |

### 10.2 前端（React 18 + TypeScript + Vite + Tailwind CSS）

| 页面 | 功能 | 状态 |
|------|------|------|
| 登录/注册 | 验证码 + JWT + 暗色模式 | ✅ 完成 |
| 仪表盘 | 统计卡片 + 攻击面地图 + 趋势图 + 技术栈雷达 | ✅ 完成 |
| 资产管理 | 表格 + 分页 + 搜索 + 筛选 + 详情面板 | ✅ 完成 |
| 扫描任务 | 创建向导 + 实时进度 SSE + 取消/删除 | ✅ 完成 |
| 漏洞管理 | 表格 + 分页 + 筛选 + 状态更新 + 复现编辑 | ✅ 完成 |
| 系统设置 | 个人信息 + 工具配置 + 定时扫描 + API Keys + Webhook + 邮件 + 插件管理 + 用户管理 + 操作日志 | ✅ 完成 |
| 暗色模式 | 全局 Tailwind dark: 适配 | ✅ 完成 |
| 分页系统 | 所有列表页统一分页组件 | ✅ 完成 |
| Toast 通知 | Context Provider + 4 种类型 + 动画 | ✅ 完成 |

---

## 11. 待优化项

### 11.1 功能层面

| 优先级 | 问题 | 说明 |
|--------|------|------|
| 高 | 单元测试缺失 | 后端 Service 层无 JUnit 测试，前端无 Jest/Vitest 测试 |
| 高 | 集成测试缺失 | 无 Spring Boot Test 集成测试覆盖 Scanner 编排流程 |
| 中 | 文件浏览器跨平台 | 当前仅 Windows 测试通过，macOS/Linux 的 `accept` 属性需调整 |
| 中 | 邮件模板可配置 | HTML 模板硬编码在 Java 代码中，理想方案为 Thymeleaf 模板 |
| 中 | 通知频率限制 | 频繁扫描时可能产生大量通知，需添加去重/聚合机制 |
| 低 | 扫描任务重试 | 失败任务无自动重试机制 |
| 低 | Webhook 签名验证 | 钉钉/飞书支持签名验证，可增强安全性 |
| 低 | 国际化 (i18n) | 当前为中文硬编码，未考虑多语言 |

### 11.2 工程层面

| 优先级 | 问题 | 说明 |
|--------|------|------|
| 高 | 前端状态管理 | SettingsPage 过大（~1400 行），所有状态在单组件内，应拆分 |
| 中 | DTO 层缺失 | Controller 直接暴露 Entity，应引入 DTO 解耦 |
| 中 | API 版本化 | 当前无版本策略，未来 Breaking Change 处理困难 |
| 低 | Docker 化 | 无 Dockerfile，本地启动需手动安装 Java/Node/MySQL/Nmap/Nuclei |
| 低 | CI/CD | 无 GitHub Actions 或其他 CI 配置 |
| 低 | 日志持久化 | 应用日志仅输出到控制台，未写入文件或日志平台 |

### 11.3 安全层面

| 优先级 | 问题 | 说明 |
|--------|------|------|
| 高 | 密码加密强度 | 需确认 BCrypt 的 strength 参数是否足够 |
| 中 | CORS 配置 | 当前可能过于宽松，生产环境需收紧 |
| 低 | 速率限制 | 登录/API 无速率限制，可能被暴力破解 |

---

## 12. 遇到的困难与教训

### 12.1 困难一：浏览器安全限制导致文件路径无法获取

**场景**：为 Nmap/Nuclei 路径添加"浏览"按钮，期望用户选择可执行文件后自动填入完整路径。

**问题**：现代浏览器出于安全考虑，`<input type="file">` 的 `value` 永远返回 `C:\fakepath\filename.exe`，无法获取真实文件路径。这是浏览器的 W3C 安全规范，无法绕过。

**解决方案**：采用"前端浏览 + 后端解析"的间接方案。前端仅获取文件名（`file.name`），然后调用后端 `GET /api/v1/config/detect-tool/{toolName}` 端点，后端通过 `where`/`which` 命令 + 常见目录搜索找到完整路径并返回。

**教训**：
- Web 应用的文件系统访问能力有限，涉及本地路径操作时应考虑后端辅助
- 对于企业内网/桌面应用场景，可考虑 Electron/Tauri 等方案直接访问文件系统
- 不要试图绕过浏览器安全策略——这是设计特性而非 Bug

### 12.2 困难二：React 非受控组件的状态陷阱

**场景**：外部情报 API 配置的三个输入框使用 `defaultValue`（非受控模式）。

**问题**：`defaultValue` 仅在组件**首次挂载**时生效。当 `configs`（从 API 获取的配置）在首次渲染后到达时，输入框不会更新。更严重的是，清空输入框后保存时，由于空字符串是 falsy，`if` 条件跳过了该 key，后端收到空对象 `{}`，数据库旧记录原封不动。

**解决方案**：
- 改为受控组件（`value` + `onChange` + 本地 state）
- 使用 `useEffect` + `initializedRef` 模式从后端数据初始化
- 始终发送所有 key（包括空值），让后端处理更新/清空

**教训**：
- 非受控组件适合表单提交场景（通过 `FormData` 或 ref 读取），不适合需要双向同步的场景
- 与后端数据同步的表单字段应始终使用受控组件
- 注意 JavaScript 的 falsy 值陷阱：空字符串、0、false 在 `if` 判断中都会被跳过

### 12.3 困难三：ScanAssetMapping 间接关联导致的资产数为 0

**场景**：通知邮件和 Webhook 内容显示"0 个资产"。

**问题**：资产（`Asset`）与扫描任务（`ScanTask`）之间存在 `ScanAssetMapping` 中间表。首次发现时 `Asset.task` 会设置，但后续扫描中已存在的资产不会更新 `task` 字段。`AssetRepository.findByTaskId()` 只返回首次发现的资产，忽略了通过中间表关联的后续扫描数据。

**解决方案**：改用 `ScanAssetMappingRepository.findByScanTaskIdWithAsset()` 获取映射记录，再提取关联的资产列表。

**教训**：
- 在查询数据前，务必理解 ORM 实体之间的实际关联方式
- 中间表（关联表）的设计在 ORM 中容易被忽略，但它承载了关键的业务关系
- 通知/报告功能应在理解数据模型的基础上编写查询逻辑，而非直接复用 CRUD 的 Repository 方法

### 12.4 困难四：SettingsPage 单文件膨胀

**场景**：SettingsPage.tsx 从最初的 ~400 行增长到 ~1400 行，包含 10+ 个子配置区域。

**问题**：所有设置（个人资料、扫描工具、定时扫描、API Keys、Webhook、邮件、插件管理、用户管理、操作日志）挤在一个组件文件中，状态管理、mutations、useEffects 交织在一起。

**教训**：
- 项目初期应为每个配置区域设计独立的路由或 Tab 组件
- 状态管理（如 React Context 或 Zustand）应在 3+ 个子区域共享数据时引入
- "后期重构"在毕业设计场景中不可行——一旦功能跑通，重构动力骤降

### 12.5 困难五：AI 辅助开发的沟通成本

**场景**：本轮迭代通过纯对话方式完成，共经历 10+ 轮交互。

**经验**：
- **精确描述**：模糊的需求（如"完善自动检测功能"）需要 AI 自行推断范围，容易产生偏差
- **分步骤验证**：每完成一个子任务立即在浏览器中验证，避免累积问题
- **上下文管理**：长对话中 AI 可能遗忘之前的决策，关键信息需要在每轮中重新确认
- **Playwright MCP 的价值**：实时浏览器验证比"我用 curl 测试过了"可靠得多，应成为标准工作流的一环

---

## 13. 项目总结

### 开发统计

| 指标 | 数值 |
|------|------|
| 总迭代轮次 | 10 轮 |
| 后端 Java 文件 | ~30 个（Controller/Service/Entity/Repository/Config） |
| 前端 TSX 文件 | ~15 个（Pages/Components/Hooks/Services） |
| 后端代码行数 | ~5000 行 |
| 前端代码行数 | ~6000 行 |
| 数据库表 | 12 张 |
| REST API 端点 | ~50 个 |
| Git Commits | 10 个 |

### AI 辅助开发反思

**收益**：
- MVP 速度：从零到可运行的完整项目，仅用约 10 轮 AI 对话
- 经验辐射：涉及 React/Spring Boot/MySQL/网络安全等多个领域，AI 提供了跨领域的知识支持
- Bug 修复效率：复杂 Bug（如 ScanAssetMapping 关联问题）从现象到根因的追溯时间缩短

**局限**：
- **上下文丢失**：长对话中 AI 可能遗忘之前的代码决策，需要反复确认
- **设计债务**：AI 倾向于"最小改动通过"，不主动提出架构优化
- **测试缺失**：AI 可以生成测试代码，但需要用户明确要求
- **安全审计**：AI 不会主动审查所有代码路径的安全问题

**建议的 AI 协作最佳实践**：
1. 项目初期建立 `CLAUDE.md` 和 `.mcp.json`，让 AI 理解项目上下文
2. 每个功能变更后立即用 Playwright MCP 做浏览器验证
3. 每 3-5 轮对话后做一次代码审查
4. 关键 Bug 修复后用文字记录根因（本项目的 12.1-12.4 节即是此类记录）
5. 不要过度依赖 AI——核心架构决策应由开发者主导

---

## 附录：配置作用域说明

| 作用域 | 配置文件位置 | 共享性 | 适用场景 |
|--------|-------------|--------|---------|
| local（当前项目） | .mcp.json（项目根目录） | 可提交 Git 共享 | 项目特定的 MCP |
| user（用户级） | ~/.claude.json | 所有项目都可用 | 含 Token 的 MCP |
| project（项目级） | 项目 .mcp.json（VCS） | 团队共享 | 开发工具 MCP |

---

> 本指南基于 2026 年 5 月 24 日最终迭代整理。
> 项目代码见 GitHub: https://github.com/18307519324az/-
