import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  BookOpen, Rocket, Shield, User, LayoutDashboard, Server,
  ScanLine, Bug, Network, Map, Globe, FileText, Settings,
  HelpCircle, Code, Database, ChevronRight, ExternalLink,
} from 'lucide-react'

interface Section {
  id: string
  title: string
  icon: React.ReactNode
  subsections?: { id: string; title: string }[]
}

export default function ManualPage() {
  const { t } = useTranslation()
  const [activeSection, setActiveSection] = useState('quickstart')

  const sections: Section[] = [
    { id: 'quickstart', title: '1. 快速开始', icon: <Rocket className="w-4 h-4" />, subsections: [
      { id: 'env', title: '环境要求' }, { id: 'install', title: '安装启动' }, { id: 'login', title: '首次登录' }
    ]},
    { id: 'admin', title: '2. 管理员指南', icon: <Shield className="w-4 h-4" />, subsections: [
      { id: 'users', title: '用户管理' }, { id: 'tools', title: '扫描工具配置' }, { id: 'schedule', title: '定时扫描' },
      { id: 'api-keys', title: '情报API配置' }, { id: 'notify', title: '通知配置' },
      { id: 'plugins', title: '扫描策略插件' }, { id: 'oplogs', title: '操作日志' },
    ]},
    { id: 'user-guide', title: '3. 用户指南', icon: <User className="w-4 h-4" />, subsections: [
      { id: 'create-scan', title: '创建扫描' }, { id: 'scan-results', title: '查看结果' },
      { id: 'assets-mgmt', title: '资产管理' }, { id: 'vuln-analysis', title: '漏洞分析' },
    ]},
    { id: 'features', title: '4. 核心功能', icon: <LayoutDashboard className="w-4 h-4" />, subsections: [
      { id: 'dashboard', title: '仪表盘' }, { id: 'topology', title: '资产拓扑' },
      { id: 'attack-surface', title: '攻击面地图' }, { id: 'intel', title: '外部威胁情报' },
      { id: 'reports', title: '报告导出' },
    ]},
    { id: 'scenario', title: '5. 典型场景', icon: <ScanLine className="w-4 h-4" /> },
    { id: 'faq', title: '6. 常见问题', icon: <HelpCircle className="w-4 h-4" /> },
    { id: 'appendix', title: '7. 附录', icon: <Code className="w-4 h-4" />, subsections: [
      { id: 'api-list', title: 'API端点速查' }, { id: 'db-schema', title: '数据库表结构' },
      { id: 'architecture', title: '技术架构图' },
    ]},
  ]

  const scrollTo = (id: string) => {
    setActiveSection(id)
    document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  const Img = ({ src, alt, caption }: { src: string; alt: string; caption?: string }) => (
    <div className="my-6">
      <img src={src} alt={alt} className="rounded-lg border shadow-sm w-full" />
      {caption && <p className="text-sm text-gray-500 mt-2 text-center">{caption}</p>}
    </div>
  )

  const H2 = ({ id, children }: { id?: string; children: string }) => (
    <h2 id={id} className="text-xl font-bold text-gray-900 mt-10 mb-4 pb-2 border-b">{children}</h2>
  )

  const H3 = ({ id, children }: { id?: string; children: string }) => (
    <h3 id={id} className="text-lg font-semibold text-gray-800 mt-6 mb-3">{children}</h3>
  )

  const CodeBlock = ({ children }: { children: string }) => (
    <pre className="bg-gray-900 text-green-400 p-4 rounded-lg text-sm overflow-x-auto my-3 font-mono">{children}</pre>
  )

  const Table = ({ headers, rows }: { headers: string[]; rows: string[][] }) => (
    <div className="overflow-x-auto my-4">
      <table className="min-w-full border text-sm">
        <thead className="bg-gray-100">
          <tr>{headers.map((h, i) => <th key={i} className="border px-3 py-2 text-left font-medium">{h}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((row, i) => (
            <tr key={i} className="hover:bg-gray-50">
              {row.map((cell, j) => <td key={j} className="border px-3 py-2">{cell}</td>)}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )

  return (
    <div className="flex h-[calc(100vh-6rem)] gap-6">
      {/* Sidebar TOC */}
      <aside className="hidden xl:block w-56 flex-shrink-0 overflow-y-auto">
        <div className="sticky top-0 bg-white rounded-lg border p-4">
          <h3 className="font-bold text-sm text-gray-500 uppercase tracking-wide mb-3">目录</h3>
          <nav className="space-y-1">
            {sections.map((s) => (
              <div key={s.id}>
                <button
                  onClick={() => scrollTo(s.id)}
                  className={`flex items-center gap-2 w-full text-left px-2 py-1.5 rounded text-sm transition ${
                    activeSection === s.id
                      ? 'bg-blue-50 text-blue-700 font-medium'
                      : 'text-gray-600 hover:bg-gray-50'
                  }`}
                >
                  {s.icon}
                  <span className="truncate">{s.title}</span>
                </button>
                {s.subsections && (
                  <div className="ml-6 mt-0.5 space-y-0.5">
                    {s.subsections.map((sub) => (
                      <button
                        key={sub.id}
                        onClick={() => scrollTo(sub.id)}
                        className="flex items-center gap-1 w-full text-left px-2 py-1 rounded text-xs text-gray-500 hover:text-blue-600 hover:bg-blue-50/50 transition"
                      >
                        <ChevronRight className="w-3 h-3 flex-shrink-0" />
                        <span className="truncate">{sub.title}</span>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ))}
          </nav>
        </div>
      </aside>

      {/* Main Content */}
      <div className="flex-1 overflow-y-auto bg-white rounded-lg border p-6 lg:p-10">
        <div className="max-w-4xl mx-auto">
          {/* Title */}
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 flex items-center gap-3">
              <BookOpen className="w-8 h-8 text-blue-600" />
              ServerScout 用户使用手册
            </h1>
            <p className="text-gray-500 mt-2">版本 v1.0 | 更新日期 2026-05-24</p>
          </div>

          {/* Chapter 1: Quick Start */}
          <H2 id="quickstart">1. 快速开始</H2>
          <p className="text-gray-600 mb-4">环境要求、安装启动、首次登录</p>

          <H3 id="env">1.1 环境要求</H3>
          <Table
            headers={['组件', '版本要求', '说明']}
            rows={[
              ['Java JDK', '17+', '后端运行环境'],
              ['Maven', '3.9+', '后端构建工具'],
              ['Node.js', '18+', '前端运行环境'],
              ['npm', '9+', '前端包管理'],
              ['MySQL', '8.0+', '数据库服务'],
              ['Nmap', '7.x+ (可选)', '端口扫描引擎'],
              ['Nuclei', '3.x+ (可选)', '漏洞扫描引擎'],
            ]}
          />

          <H3 id="install">1.2 安装与启动</H3>
          <p className="text-gray-600 mb-2"><strong>后端启动：</strong></p>
          <CodeBlock>{`# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS serverscout \\
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 修改 application.yml 中的数据库连接配置

# 3. 启动后端
cd serverscout/backend
mvn spring-boot:run
# 后端运行在 http://localhost:8080
# API 文档: http://localhost:8080/docs`}</CodeBlock>

          <p className="text-gray-600 mb-2 mt-4"><strong>前端启动：</strong></p>
          <CodeBlock>{`cd serverscout/frontend
npm install
npm run dev
# 前端运行在 http://localhost:5173`}</CodeBlock>

          <H3 id="login">1.3 首次登录</H3>
          <ol className="list-decimal ml-6 text-gray-600 space-y-1 mb-4">
            <li>浏览器访问 <code className="bg-gray-100 px-1 rounded">http://localhost:5173</code></li>
            <li>系统自动跳转到登录页面</li>
          </ol>
          <Img src="/docs/images/ch1-login-page.png" alt="登录页面" caption="图1: 登录页面" />
          <Table
            headers={['字段', '值']}
            rows={[
              ['用户名', 'admin'],
              ['密码', 'admin123'],
              ['验证码', '计算页面显示的数学题答案（如 17+13=30）'],
            ]}
          />
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-4 my-4 text-sm text-amber-800">
            <strong>安全提示：</strong>首次登录后请立即修改默认密码！详见设置页面的"修改密码"功能。
          </div>

          <Img src="/docs/images/ch4-dashboard-full.png" alt="仪表盘首页" caption="图2: 登录后进入仪表盘首页" />

          {/* Chapter 2: Admin Guide */}
          <H2 id="admin">2. 管理员操作指南</H2>
          <p className="text-gray-600 mb-4">管理员拥有系统最高权限。所有管理功能统一在 <strong>设置</strong> 页面。</p>

          <Img src="/docs/images/ch2-settings.png" alt="系统设置页面" caption="图3: 系统设置页面全景" />

          <H3 id="users">2.1 用户管理</H3>
          <p className="text-gray-600 mb-2">在设置页面的 <strong>用户管理</strong> 区域：</p>
          <ul className="list-disc ml-6 space-y-1 text-gray-600">
            <li><strong>添加用户：</strong>点击"添加用户"，填写用户名/密码/角色/邮箱</li>
            <li><strong>角色说明：</strong>ADMIN（管理员）拥有全部权限，USER（普通用户）可创建扫描和查看结果</li>
            <li><strong>编辑用户：</strong>修改角色、姓名、邮箱、启用/禁用状态</li>
            <li><strong>重置密码：</strong>点击"重置密码"输入新密码</li>
            <li><strong>删除用户：</strong>确认后永久删除</li>
          </ul>

          <H3 id="tools">2.2 扫描工具配置</H3>
          <p className="text-gray-600">在 <strong>扫描工具配置</strong> 区域查看 Nmap/Nuclei 检测状态，点击"编辑路径"修改执行路径，点击"安装指南"了解工具安装方法。</p>

          <H3 id="schedule">2.3 定时扫描配置</H3>
          <p className="text-gray-600">配置每日巡检和每周全面扫描，支持 Cron 表达式灵活设定执行时间，可启用/禁用定时任务。</p>

          <H3 id="api-keys">2.4 外部情报 API 配置</H3>
          <p className="text-gray-600">配置 <strong>Censys API ID/Secret</strong> 和 <strong>VirusTotal API Key</strong> 以启用扩展威胁情报查询功能。</p>

          <H3 id="notify">2.5 告警与邮件通知</H3>
          <p className="text-gray-600 mb-2">在设置页面可配置：</p>
          <ul className="list-disc ml-6 space-y-1 text-gray-600">
            <li><strong>Webhook 通知：</strong>钉钉、飞书、企业微信 — 扫描完成后推送结果摘要</li>
            <li><strong>邮件通知：</strong>配置 SMTP 服务器，扫描完成后发送邮件报告</li>
          </ul>

          <H3 id="plugins">2.6 扫描策略插件 (L2)</H3>
          <p className="text-gray-600">管理自定义扫描策略（如 SSH 弱口令检测），支持自定义命令模板和结果解析正则，可启用/禁用/编辑/删除。</p>

          <H3 id="oplogs">2.7 操作日志</H3>
          <p className="text-gray-600">在设置页面底部查看所有用户的操作记录，支持按用户名/类型筛选，显示 IP 归属地，支持导出 CSV/Excel。</p>

          {/* Chapter 3: User Guide */}
          <H2 id="user-guide">3. 普通用户操作指南</H2>
          <p className="text-gray-600 mb-4">创建扫描、查看结果、分析漏洞的完整流程。</p>

          <H3 id="create-scan">3.1 创建扫描任务</H3>
          <ol className="list-decimal ml-6 space-y-1 text-gray-600 mb-4">
            <li>点击左侧导航栏 <strong>扫描任务</strong></li>
            <li>点击 <strong>新建扫描</strong> 按钮</li>
            <li>填写任务名称、扫描目标（IP/域名/CIDR）、选择扫描类型</li>
            <li>点击确认开始扫描</li>
          </ol>
          <Img src="/docs/images/ch3-create-scan.png" alt="新建扫描对话框" caption="图4: 新建扫描任务对话框" />

          <Table
            headers={['参数', '说明', '示例']}
            rows={[
              ['任务名称', '便于识别的名称', '生产环境巡检'],
              ['扫描目标', 'IP、域名或CIDR网段', '192.168.1.1 / example.com / 10.0.0.0/24'],
              ['扫描类型', 'quick（快速）/ full（全面）/ 自定义插件', 'full'],
            ]}
          />

          <H3 id="scan-results">3.2 查看扫描结果</H3>
          <p className="text-gray-600 mb-2">扫描任务列表展示所有任务及其状态：</p>
          <Img src="/docs/images/ch3-scan-tasks.png" alt="扫描任务列表" caption="图5: 扫描任务列表" />
          <p className="text-gray-600 mb-2">点击任务名称进入详情页，查看扫描进度、发现的资产、端口、漏洞、Web截图等：</p>
          <Img src="/docs/images/ch3-scan-task-detail.png" alt="扫描任务详情" caption="图6: 扫描任务详情页" />

          <H3 id="assets-mgmt">3.3 资产管理</H3>
          <p className="text-gray-600 mb-2">点击 <strong>资产列表</strong> 查看所有已发现的资产，可按状态/关键词筛选：</p>
          <Img src="/docs/images/ch2-assets.png" alt="资产列表" caption="图7: 资产列表页面" />
          <p className="text-gray-600 mb-2">点击资产进入详情，可查看基本信息、开放端口、漏洞、SSL证书、子域名、蜜罐检测、Web爬虫结果等：</p>
          <Img src="/docs/images/ch3-asset-detail.png" alt="资产详情" caption="图8: 资产详情页面" />

          <H3 id="vuln-analysis">3.4 漏洞分析</H3>
          <p className="text-gray-600 mb-2">点击 <strong>漏洞列表</strong> 查看所有检测到的漏洞，可按严重程度/状态筛选：</p>
          <Img src="/docs/images/ch3-vulnerabilities.png" alt="漏洞列表" caption="图9: 漏洞列表页面" />
          <p className="text-gray-600 mb-2">点击漏洞进入详情，查看CVE信息、CVSS评分、复现步骤、修复建议、状态变更历史：</p>
          <Img src="/docs/images/ch3-vuln-detail.png" alt="漏洞详情" caption="图10: 漏洞详情页面" />

          {/* Chapter 4: Core Features */}
          <H2 id="features">4. 核心功能详解</H2>

          <H3 id="dashboard">4.1 仪表盘</H3>
          <p className="text-gray-600 mb-2">首页仪表盘提供系统全局概览：统计卡片区（资产/扫描/漏洞/高危漏洞/蜜罐）、漏洞趋势图、资产分布图、严重程度分布、最近扫描活动、技术栈分布。</p>
          <Img src="/docs/images/ch4-dashboard-full.png" alt="仪表盘" caption="图11: 仪表盘全局概览" />

          <H3 id="topology">4.2 资产拓扑图</H3>
          <p className="text-gray-600 mb-2">使用 G6 图可视化引擎展示资产之间的关系网络图，节点代表资产（不同颜色表示不同类型），连线表示网络关系，支持拖拽、缩放、点击查看详情。</p>
          <Img src="/docs/images/ch4-topology-full.png" alt="资产拓扑" caption="图12: 资产拓扑图" />

          <H3 id="attack-surface">4.3 攻击面地图</H3>
          <p className="text-gray-600 mb-2">使用 D3.js 力导向图可视化攻击面，展示端口、服务、漏洞之间的关联关系，帮助安全团队快速识别风险暴露面。</p>
          <Img src="/docs/images/ch4-attack-surface.png" alt="攻击面地图" caption="图13: 攻击面地图" />

          <H3 id="intel">4.4 外部威胁情报</H3>
          <p className="text-gray-600 mb-2">集成 Censys 和 VirusTotal，支持 IP 情报查询、CVE 详情查询、域名情报查询、综合报告生成。</p>
          <Img src="/docs/images/ch4-intel.png" alt="外部威胁情报" caption="图14: 外部威胁情报页面" />

          <H3 id="reports">4.5 报告导出</H3>
          <p className="text-gray-600 mb-2">按扫描任务导出 PDF 安全评估报告或 Excel 结构化数据，报告包含资产清单、端口信息、漏洞详情、修复建议、扫描统计。</p>
          <Img src="/docs/images/ch4-reports.png" alt="报告中心" caption="图15: 报告中心页面" />

          {/* Chapter 5: Scenario */}
          <H2 id="scenario">5. 典型使用场景</H2>
          <p className="text-gray-600 mb-4">场景：对新上线的 Web 应用进行完整安全评估</p>
          <div className="space-y-3 text-gray-600">
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">1</span>
              <div><strong>登录系统</strong> — 使用管理员账号登录 ServerScout</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">2</span>
              <div><strong>创建扫描</strong> — 扫描任务 → 新建扫描 → 填写目标域名 → 选择"full"全面扫描</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">3</span>
              <div><strong>等待完成</strong> — 观察任务状态从 running 变为 completed</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">4</span>
              <div><strong>查看结果</strong> — 进入任务详情，查看发现的资产、端口和漏洞</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">5</span>
              <div><strong>分析资产</strong> — 资产列表 → 查看详情（端口/服务/SSL/蜜罐/爬虫）</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">6</span>
              <div><strong>处理漏洞</strong> — 按严重程度排序 → 逐个确认 → 修复 → 更新状态</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">7</span>
              <div><strong>查看攻击面</strong> — 攻击面地图直观了解服务暴露情况</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">8</span>
              <div><strong>导出报告</strong> — 报告中心 → 选择任务 → 导出PDF报告</div>
            </div>
            <div className="flex items-start gap-3">
              <span className="flex-shrink-0 w-7 h-7 bg-blue-100 text-blue-700 rounded-full flex items-center justify-center text-sm font-bold">9</span>
              <div><strong>补充情报</strong> — 外部情报页查询 IP 威胁情报、CVE 详情</div>
            </div>
          </div>

          {/* Chapter 6: FAQ */}
          <H2 id="faq">6. 常见问题与排错</H2>
          <div className="space-y-4 text-gray-600">
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q1: 登录失败怎么办？</p>
              <p className="mt-1">检查用户名密码（默认 admin/admin123），确认验证码计算正确，检查后端是否正常运行。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q2: 扫描无结果？</p>
              <p className="mt-1">检查目标可达性（ping），检查防火墙规则，确认 Nmap/Nuclei 工具路径配置正确，尝试 full 扫描模式。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q3: 端口被占用怎么办？</p>
              <p className="mt-1">后端可通过 <code className="bg-gray-200 px-1 rounded">SERVER_PORT=9090 mvn spring-boot:run</code> 修改端口。前端修改 <code className="bg-gray-200 px-1 rounded">vite.config.ts</code> 中的端口配置。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q4: MySQL 连接失败？</p>
              <p className="mt-1">确保 MySQL 已启动，检查 <code className="bg-gray-200 px-1 rounded">serverscout</code> 数据库已创建，核对 application.yml 中的连接配置。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q5: 如何重置管理员密码？</p>
              <p className="mt-1">通过其他管理员账号在设置页重置，或直接在 MySQL 中更新 user 表的 BCrypt 密码字段。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q6: PDF 报告中文乱码？</p>
              <p className="mt-1">在 application.yml 中配置正确的中文字体路径，Windows: <code className="bg-gray-200 px-1 rounded">C:/Windows/Fonts/msyh.ttc</code>，Linux: 安装 fonts-noto-cjk。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q7: Nuclei 模板下载失败？</p>
              <p className="mt-1">手动下载 nuclei-templates 仓库，或配置网络代理后运行 <code className="bg-gray-200 px-1 rounded">nuclei -update-templates</code>。</p>
            </div>
            <div className="bg-gray-50 rounded-lg p-4">
              <p className="font-semibold text-gray-800">Q8: 扫描速度太慢？</p>
              <p className="mt-1">使用 quick 模式（仅 Top 1000 端口），扫描单个 IP 而非大段 CIDR，调整扫描超时和并发数设置。</p>
            </div>
          </div>

          {/* Chapter 7: Appendix */}
          <H2 id="appendix">7. 附录（开发者参考）</H2>

          <H3 id="api-list">7.1 API 端点速查</H3>
          <p className="text-gray-600 mb-2">在线文档: <a href="http://localhost:8080/docs" target="_blank" rel="noopener" className="text-blue-600 hover:underline">http://localhost:8080/docs <ExternalLink className="w-3 h-3 inline" /></a></p>
          <Img src="/docs/images/ch7-swagger-api.png" alt="Swagger API文档" caption="图16: Swagger API 在线文档" />

          <Table
            headers={['模块', '前缀', '主要端点']}
            rows={[
              ['认证', '/api/auth', 'POST /login, /register, GET /captcha, /public-key'],
              ['用户管理', '/api/v1/users', 'CRUD 用户, GET /me, PUT /me/password'],
              ['资产管理', '/api/v1/assets', '分页查询, 详情, 标签, 拓扑, 攻击面, 合并'],
              ['扫描任务', '/api/v1/scan-tasks', '创建, 分页, 详情, 取消, 删除'],
              ['漏洞管理', '/api/v1/vulnerabilities', '分页, 详情, 状态更新, 复现步骤, 日志'],
              ['仪表盘', '/api/v1/dashboard', 'GET /stats, /tech-stack'],
              ['子域名', '/api/v1/subdomains', '枚举, 按域名/资产查询'],
              ['报告', '/api/v1/reports', 'GET /pdf, /excel (按taskId)'],
              ['外部情报', '/api/v1/intel', 'IP/CVE/域名查询, Censys, VirusTotal'],
              ['蜜罐检测', '/api/v1/honeypot', 'GET /stats, /asset/{id}'],
              ['截图/爬虫', '/api/v1/screenshot, /crawler', '网页截图, URL爬取'],
              ['插件管理', '/api/v1/plugins', 'CRUD + 启用/禁用'],
              ['系统配置', '/api/v1/config', '配置管理, 工具检测'],
              ['操作日志', '/api/v1/operation-logs', '分页查询, 导出, 统计'],
            ]}
          />

          <H3 id="db-schema">7.2 数据库表结构</H3>
          <Table
            headers={['表名', '说明', '主要字段']}
            rows={[
              ['asset', '资产表', 'id, ip, domain, os, tags, status, honeypot_probability'],
              ['port', '端口表', 'id, asset_id, port_number, protocol, service, version'],
              ['scan_task', '扫描任务表', 'id, name, target, scan_type, status, progress'],
              ['asset_vulnerability', '漏洞表', 'id, name, severity, cve_id, cvss_score, status'],
              ['vuln_status_log', '漏洞状态日志', 'id, vuln_id, old_status, new_status, operator'],
              ['user', '用户表', 'id, username, password, name, role, email, enabled'],
              ['subdomain', '子域名表', 'id, domain, subdomain, ip, source'],
              ['ssl_certificate', 'SSL证书表', 'id, asset_id, subject, issuer, valid_from/to'],
              ['web_fingerprint', 'Web指纹表', 'id, asset_id, server, tech_stack, title'],
              ['honeypot_rule', '蜜罐规则表', 'id, name, pattern, category, confidence'],
              ['honeypot_detection', '蜜罐检测表', 'id, asset_id, rule_id, match_detail, confidence'],
              ['scan_strategy_plugin', '插件表', 'id, name, scan_type, command_template, enabled'],
              ['crawled_url', '爬虫URL表', 'id, url, title, status_code, task_id, asset_id'],
              ['operation_log', '操作日志表', 'id, username, type, target, ip_address'],
              ['cve_database', 'CVE数据库', 'id, cve_id, description, cvss_score, severity'],
            ]}
          />

          <H3 id="architecture">7.3 技术架构图</H3>
          <div className="bg-gray-900 text-green-400 p-6 rounded-lg text-xs font-mono leading-relaxed overflow-x-auto my-4">
{`┌──────────────────────────────────────────────────────┐
│              前端 (React 18 + TypeScript)              │
│  Vite · Tailwind CSS · Ant Design · ECharts           │
│  React Router · i18next · React Query                 │
│  G6 (拓扑) · D3.js (攻击面) · jsencrypt (RSA)         │
└──────────────────────┬───────────────────────────────┘
                       │ HTTP REST + JWT Auth
┌──────────────────────▼───────────────────────────────┐
│              后端 (Spring Boot 3 + Java 17)            │
│                                                       │
│  Auth · Scan Engine · Asset Mgmt · Vulnerability Mgmt │
│  Report Gen · Intel Service · Screenshot · Notify     │
└──────────────────────┬───────────────────────────────┘
                       │ JDBC
┌──────────────────────▼───────────────────────────────┐
│                  MySQL 8.0 数据库                      │
└──────────────────────────────────────────────────────┘

外部工具:  Nmap (端口扫描) · Nuclei (漏洞扫描)
          Censys (主机情报) · VirusTotal (威胁情报)`}
          </div>
        </div>
      </div>
    </div>
  )
}
