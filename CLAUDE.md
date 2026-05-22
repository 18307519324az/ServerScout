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
- /zeus "描述需求" —— 从分析到部署全自动（Skill Olympus）
- /chronos —— 自动修复 Bug 循环

### 代码质量
- /grill-me —— 设计审查，AI 逐问排查方案漏洞
- /tdd —— 测试驱动开发（RED→GREEN→REFACTOR）

### 文档与版本控制
- /to-prd —— 对话内容生成 PRD 文档
- /adr —— 记录架构决策

### 项目管理
- /clio —— 生成流程图、技术文档、用户手册

## 开发工作流
1. 需求分析 → 用 `/grill-me` 审查方案
2. 设计 → 用 `/adr` 记录技术选型
3. 实现 → `npm run dev` / `mvn spring-boot:run`
4. 测试 → 用 `/tdd` 做 TDD，用 Playwright MCP 做 E2E
5. 文档 → 用 `/to-prd` 生成 PRD，用 `/clio` 生成文档
6. 提交 → 用 GitHub MCP 管理 PR
