import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BookOpen, ChevronRight, ExternalLink } from 'lucide-react'

interface TocItem {
  id: string
  title: string
  level: 2 | 3
}

type ManualLang = 'zh' | 'en'

const MANUAL_TEXT = {
  zh: {
    toc: '目录',
    title: 'ServerScout 用户手册',
    subtitle: '版本 v1.0 | 更新日期 2026-06-24',
    docsLabel: '在线 API 文档',
    docsUrl: 'http://localhost:8080/docs',
    linksTitle: '快速链接',
    links: [
      { label: '登录页', path: '/login' },
      { label: '扫描任务', path: '/scan-tasks' },
      { label: '资产管理', path: '/asset-management' },
      { label: '网络拓扑图', path: '/topology' },
      { label: '攻击面地图', path: '/attack-surface' },
      { label: '外部情报', path: '/intel' },
      { label: '报告中心', path: '/reports' },
      { label: '系统设置', path: '/settings' },
    ],
    section1: '1. 快速开始',
    section11: '1.1 环境要求',
    section12: '1.2 Docker 启动',
    section13: '1.3 首次登录',
    section14: '1.4 演示账号',
    section2: '2. 运行模式',
    section3: '3. 扫描任务',
    section31: '3.1 创建扫描任务',
    section32: '3.2 扫描模板',
    section33: '3.3 功能开关',
    section34: '3.4 Pipeline 阶段',
    section35: '3.5 长时间扫描',
    section4: '4. 资产管理',
    section41: '4.1 资产列表',
    section42: '4.2 资产详情',
    section5: '5. 报告中心',
    section6: '6. AI 简报',
    section7: '7. 管理员配置',
    section71: '7.1 扫描工具与插件',
    section72: '7.2 通知与审计',
    section8: '8. 常见问题',
    section81: 'Q：为什么 Real Mode 扫描不到我的云服务器？',
    section82: 'Q：为什么 Full 扫描很慢？',
    section83: 'Q：为什么某些 Pipeline 阶段显示 skipped？',
    section84: 'Q：Demo Mode 下的漏洞和风险是真的吗？',
    section85: 'Q：Nmap 已检测到，为什么还是 Demo 数据？',
    section9: '9. 版本能力概览',
    tableHeaderName: '组件',
    tableHeaderVersion: '建议版本',
    tableHeaderNote: '说明',
    btnGo: '打开',
  },
  en: {
    toc: 'Table of Contents',
    title: 'ServerScout User Manual',
    subtitle: 'Version v1.0 | Updated on 2026-06-24',
    docsLabel: 'Online API Docs',
    docsUrl: 'http://localhost:8080/docs',
    linksTitle: 'Quick Links',
    links: [
      { label: 'Login', path: '/login' },
      { label: 'Scan Tasks', path: '/scan-tasks' },
      { label: 'Assets', path: '/asset-management' },
      { label: 'Topology', path: '/topology' },
      { label: 'Attack Surface', path: '/attack-surface' },
      { label: 'Threat Intel', path: '/intel' },
      { label: 'Reports', path: '/reports' },
      { label: 'Settings', path: '/settings' },
    ],
    section1: '1. Quick Start',
    section11: '1.1 Requirements',
    section12: '1.2 Docker Startup',
    section13: '1.3 First Login',
    section14: '1.4 Demo Accounts',
    section2: '2. Operation Modes',
    section3: '3. Scan Tasks',
    section31: '3.1 Creating a Scan Task',
    section32: '3.2 Scan Templates',
    section33: '3.3 Feature Switches',
    section34: '3.4 Pipeline Stages',
    section35: '3.5 Long-running Scans',
    section4: '4. Asset Management',
    section41: '4.1 Asset List',
    section42: '4.2 Asset Detail',
    section5: '5. Report Center',
    section6: '6. AI Briefing',
    section7: '7. Admin Configuration',
    section71: '7.1 Scan Tools and Plugins',
    section72: '7.2 Notifications and Auditing',
    section8: '8. FAQ',
    section81: 'Q: Why can\'t Real Mode scan my cloud server?',
    section82: 'Q: Why is Full scan so slow?',
    section83: 'Q: Why do some Pipeline stages show skipped?',
    section84: 'Q: Are Demo Mode vulnerabilities and risks real?',
    section85: 'Q: Nmap is detected, but why is it still Demo data?',
    section9: '9. Version Capabilities',
    tableHeaderName: 'Component',
    tableHeaderVersion: 'Recommended',
    tableHeaderNote: 'Note',
    btnGo: 'Open',
  },
} as const

function ManualH2({ id, title }: { id: string; title: string }) {
  return (
    <h2
      id={id}
      data-manual-anchor="true"
      data-toc-id={id}
      data-toc-title={title}
      data-toc-level="2"
      className="text-xl font-bold text-gray-900 dark:text-gray-100 mt-10 mb-4 pb-2 border-b border-gray-200 dark:border-gray-700"
    >
      {title}
    </h2>
  )
}

function ManualH3({ id, title }: { id: string; title: string }) {
  return (
    <h3
      id={id}
      data-manual-anchor="true"
      data-toc-id={id}
      data-toc-title={title}
      data-toc-level="3"
      className="text-lg font-semibold text-gray-800 dark:text-gray-200 mt-6 mb-3"
    >
      {title}
    </h3>
  )
}

export default function ManualPage() {
  const { i18n } = useTranslation()
  const lang: ManualLang = i18n.language.startsWith('zh') ? 'zh' : 'en'
  const text = useMemo(() => MANUAL_TEXT[lang], [lang])
  const tx = useCallback((zh: string, en: string) => (lang === 'zh' ? zh : en), [lang])

  const contentRef = useRef<HTMLDivElement>(null)
  const [tocItems, setTocItems] = useState<TocItem[]>([])
  const [activeSection, setActiveSection] = useState('')

  const rebuildToc = useCallback(() => {
    const root = contentRef.current
    if (!root) return

    const headings = Array.from(root.querySelectorAll<HTMLElement>('[data-manual-anchor="true"]'))
    const items: TocItem[] = headings
      .map((heading) => {
        const id = heading.dataset.tocId || heading.id
        const title = heading.dataset.tocTitle || heading.textContent || ''
        const rawLevel = Number(heading.dataset.tocLevel || 2)
        const level: 2 | 3 = rawLevel === 3 ? 3 : 2
        return { id, title, level }
      })
      .filter((item) => item.id && item.title)

    setTocItems(items)
    setActiveSection((prev) => {
      if (items.length === 0) return prev
      return items.some((item) => item.id === prev) ? prev : items[0].id
    })
  }, [])

  useEffect(() => {
    const root = contentRef.current
    if (!root) return

    rebuildToc()

    const headings = Array.from(root.querySelectorAll<HTMLElement>('[data-manual-anchor="true"]'))
    if (headings.length === 0) return

    const observer = new IntersectionObserver(
      (entries) => {
        const visible = entries
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top)

        if (visible[0]) {
          const id = (visible[0].target as HTMLElement).dataset.tocId
          if (id) setActiveSection(id)
        }
      },
      {
        root,
        rootMargin: '-20% 0px -65% 0px',
        threshold: [0, 1],
      }
    )

    headings.forEach((heading) => observer.observe(heading))

    const mutationObserver = new MutationObserver(() => {
      rebuildToc()
    })
    mutationObserver.observe(root, { subtree: true, childList: true, characterData: true })

    return () => {
      observer.disconnect()
      mutationObserver.disconnect()
    }
  }, [lang, rebuildToc])

  const scrollTo = (id: string) => {
    const root = contentRef.current
    if (!root) return

    const target = root.querySelector<HTMLElement>(`[data-toc-id="${id}"]`)
    if (!target) return

    setActiveSection(id)
    root.scrollTo({ top: Math.max(0, target.offsetTop - 8), behavior: 'smooth' })
  }

  return (
    <div className="flex h-[calc(100vh-6rem)] gap-6">
      <aside className="hidden xl:block w-64 flex-shrink-0 overflow-y-auto">
        <div className="sticky top-0 bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-4">
          <h3 className="font-bold text-sm text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-3">{text.toc}</h3>
          <nav className="space-y-1">
            {tocItems.map((item) => (
              <button
                key={item.id}
                onClick={() => scrollTo(item.id)}
                className={`flex items-center gap-2 w-full text-left rounded transition ${
                  item.level === 2 ? 'px-2 py-1.5 text-sm' : 'ml-4 px-2 py-1 text-xs'
                } ${
                  activeSection === item.id
                    ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300 font-medium'
                    : 'text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'
                }`}
              >
                {item.level === 3 && <ChevronRight className="w-3 h-3 flex-shrink-0" />}
                <span className="truncate">{item.title}</span>
              </button>
            ))}
          </nav>
        </div>
      </aside>

      <div
        ref={contentRef}
        className="flex-1 overflow-y-auto bg-white dark:bg-gray-900 rounded-lg border border-gray-200 dark:border-gray-700 p-6 lg:p-10"
      >
        <div className="max-w-4xl mx-auto text-gray-700 dark:text-gray-300 leading-7">
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 flex items-center gap-3">
              <BookOpen className="w-8 h-8 text-blue-600" />
              {text.title}
            </h1>
            <p className="text-gray-500 dark:text-gray-400 mt-2">{text.subtitle}</p>
            <p className="text-sm mt-2">
              <a
                href={text.docsUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center gap-1 text-blue-600 dark:text-blue-400 hover:underline"
              >
                {text.docsLabel} <ExternalLink className="w-3.5 h-3.5" />
              </a>
            </p>
          </div>

          {/* ========== 1. Quick Start ========== */}
          <ManualH2 id="quickstart" title={text.section1} />
          <p className="mb-4">
            {tx(
              'ServerScout 是服务器资产攻击面管理与风险分析平台，集成了 Nmap 端口探测与 Nuclei 漏洞检测，支持资产发现、服务识别、漏洞扫描、风险评分、报告导出与 AI 风险简报。',
              'ServerScout is a server asset attack surface management and risk analysis platform. It integrates Nmap port scanning, Nuclei vulnerability detection, asset discovery, service identification, risk scoring, report export, and AI briefing.'
            )}
          </p>

          <ManualH3 id="requirements" title={text.section11} />
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{text.tableHeaderName}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{text.tableHeaderVersion}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{text.tableHeaderNote}</th>
                </tr>
              </thead>
              <tbody>
                <tr><td className="border px-3 py-2">Docker</td><td className="border px-3 py-2">24+</td><td className="border px-3 py-2">{tx('推荐方式，包含完整运行环境', 'Recommended — includes full runtime environment')}</td></tr>
                <tr><td className="border px-3 py-2">Java</td><td className="border px-3 py-2">17+</td><td className="border px-3 py-2">{tx('后端运行环境（Spring Boot）', 'Spring Boot backend runtime')}</td></tr>
                <tr><td className="border px-3 py-2">MySQL</td><td className="border px-3 py-2">8.0+</td><td className="border px-3 py-2">{tx('主数据存储', 'Primary storage')}</td></tr>
                <tr><td className="border px-3 py-2">Node.js</td><td className="border px-3 py-2">18+</td><td className="border px-3 py-2">{tx('前端构建与运行', 'Frontend build/runtime')}</td></tr>
                <tr><td className="border px-3 py-2">Redis</td><td className="border px-3 py-2">{tx('6/7（可选）', '6/7 (optional)')}</td><td className="border px-3 py-2">{tx('多实例并发控制', 'Multi-instance concurrency control')}</td></tr>
              </tbody>
            </table>
          </div>

          <ManualH3 id="startup" title={text.section12} />
          <p className="mb-3">
            {tx(
              '推荐使用 Docker Compose 一键启动完整环境，无需手动配置数据库和扫描工具。',
              'We recommend Docker Compose for a one-command startup with the full environment, no manual database or scanner tool setup required.'
            )}
          </p>
          <ol className="list-decimal ml-6 space-y-1">
            <li><code className="mx-1 px-1 rounded bg-gray-100 dark:bg-gray-800">docker compose up -d --build</code>{tx('：构建并启动所有服务', ' — build and start all services')}</li>
            <li>{tx('等待约 60-90 秒，MySQL 初始化完成后端自动启动。', 'Wait ~60-90 seconds for MySQL initialization.')}</li>
            <li>{tx('访问 http://localhost:3000 进入前端页面。', 'Visit http://localhost:3000 for the frontend page.')}</li>
          </ol>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
            {tx(
              '首次体验建议使用 Demo Mode（默认启用），系统会生成模拟资产、端口、漏洞和风险评分数据，无需安装 Nmap 或 Nuclei。',
              'First-time users are encouraged to try Demo Mode (enabled by default). The system generates simulated assets, ports, vulnerabilities, and risk scores — no Nmap or Nuclei installation needed.'
            )}
          </p>

          <ManualH3 id="first-login" title={text.section13} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('打开浏览器访问前端地址，进入登录页面。', 'Open the browser and navigate to the frontend URL to reach the login page.')}</li>
            <li>{tx('输入演示账号和密码，完成登录。', 'Enter the demo account credentials to log in.')}</li>
            <li>{tx('首次登录后建议在系统设置中修改默认密码。', 'After first login, change the default password in Settings.')}</li>
          </ul>

          <ManualH3 id="demo-accounts" title={text.section14} />
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('账号', 'Account')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('密码', 'Password')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('角色', 'Role')}</th>
                </tr>
              </thead>
              <tbody>
                <tr><td className="border px-3 py-2">admin</td><td className="border px-3 py-2">admin123</td><td className="border px-3 py-2">{tx('管理员（完整权限）', 'Admin (full access)')}</td></tr>
                <tr><td className="border px-3 py-2">demo_user</td><td className="border px-3 py-2">demo123</td><td className="border px-3 py-2">{tx('普通用户（受限权限）', 'Regular user (limited access)')}</td></tr>
              </tbody>
            </table>
          </div>

          {/* ========== 2. Operation Modes ========== */}
          <ManualH2 id="modes" title={text.section2} />
          <p className="mb-3">
            {tx(
              'ServerScout 支持两种运行模式，通过环境变量 SCANNER_DEMO_MODE 控制。切换后需重启服务。',
              'ServerScout supports two operation modes controlled by the SCANNER_DEMO_MODE environment variable. Restart is required after switching.'
            )}
          </p>
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('项目', 'Item')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('Demo Mode 演示模式', 'Demo Mode')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('Real Mode 真实模式', 'Real Mode')}</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('数据来源', 'Data Source')}</td>
                  <td className="border px-3 py-2">{tx('模拟数据生成器', 'Simulated data generator')}</td>
                  <td className="border px-3 py-2">{tx('真实 Nmap / Nuclei / HTTP 探测 / 爬虫', 'Real Nmap / Nuclei / HTTP probes / crawler')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('是否访问目标', 'Target Access')}</td>
                  <td className="border px-3 py-2">{tx('不访问真实目标', 'Does not access real targets')}</td>
                  <td className="border px-3 py-2">{tx('会访问扫描目标', 'Accesses scan targets')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('工具要求', 'Tool Requirements')}</td>
                  <td className="border px-3 py-2">{tx('无需安装 Nmap / Nuclei', 'No Nmap or Nuclei required')}</td>
                  <td className="border px-3 py-2">{tx('需要安装并配置扫描工具', 'Requires scanning tools installed')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('授权确认', 'Authorization')}</td>
                  <td className="border px-3 py-2">{tx('不需要', 'Not required')}</td>
                  <td className="border px-3 py-2">{tx('创建任务前必须确认目标授权', 'Must confirm authorization before creating tasks')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('适用场景', 'Use Case')}</td>
                  <td className="border px-3 py-2">{tx('演示、教学、开发、本地体验', 'Demos, training, development, local trials')}</td>
                  <td className="border px-3 py-2">{tx('授权资产安全评估', 'Authorized asset security assessment')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('切换方式', 'Switch Method')}</td>
                  <td className="border px-3 py-2"><code className="px-1 rounded bg-gray-100 dark:bg-gray-800">SCANNER_DEMO_MODE=true</code></td>
                  <td className="border px-3 py-2"><code className="px-1 rounded bg-gray-100 dark:bg-gray-800">SCANNER_DEMO_MODE=false</code></td>
                </tr>
              </tbody>
            </table>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {tx(
              'Nmap / Nuclei 是否已检测到只代表工具可用性；实际是否执行真实扫描，由当前运行模式决定。当前模式会显示在左侧侧边栏底部，也可以在系统设置页的运行模式信息卡片中查看。',
              'Nmap/Nuclei detection only reflects tool availability — whether real scanning is performed depends on the current operation mode. The current mode is shown at the bottom of the sidebar and in the Settings page mode info card.'
            )}
          </p>

          {/* ========== 3. Scan Tasks ========== */}
          <ManualH2 id="scan-tasks" title={text.section3} />

          <ManualH3 id="create-task" title={text.section31} />
          <ol className="list-decimal ml-6 space-y-1">
            <li>{tx('进入扫描任务页面，点击"新建扫描任务"。', 'Go to Scan Tasks and click "New Scan Task".')}</li>
            <li>{tx('填写任务名称和扫描目标。', 'Enter a task name and scan target.')}</li>
            <li>{tx('选择扫描模板（Quick / Stealth / Web / Full），或选择自定义扫描手动配置。', 'Choose a scan template (Quick / Stealth / Web / Full) or select Custom for manual configuration.')}</li>
            <li>{tx('按需调整端口范围和功能开关（指纹识别、漏洞扫描、爬虫发现）。', 'Adjust the port range and feature toggles (fingerprint, vulnerability scan, crawler) as needed.')}</li>
            <li>{tx('Real Mode 下必须勾选目标授权确认。', 'In Real Mode, confirm target authorization before proceeding.')}</li>
            <li>{tx('点击"开始扫描"，任务进入 Pipeline 执行。', 'Click "Start Scan" to begin the Pipeline execution.')}</li>
          </ol>

          <ManualH3 id="scan-templates" title={text.section32} />
          <p className="mb-3">
            {tx(
              '系统内置四种扫描模板，覆盖不同使用场景。选择模板后端口范围和功能开关会自动填充，支持按需调整。',
              'Four built-in scan templates cover different use cases. Port range and feature toggles auto-fill when a template is selected, with manual adjustment supported.'
            )}
          </p>
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('模板', 'Template')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('扫描类型', 'Scan Type')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('默认端口范围', 'Default Port Range')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('指纹识别', 'Fingerprint')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('漏洞扫描', 'Vuln Scan')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('爬虫发现', 'Crawler')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('适用场景', 'Use Case')}</th>
                </tr>
              </thead>
              <tbody>
                <tr><td className="border px-3 py-2">Quick</td><td className="border px-3 py-2">QUICK</td><td className="border px-3 py-2">1-1000</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('关闭', 'Off')}</td><td className="border px-3 py-2">{tx('关闭', 'Off')}</td><td className="border px-3 py-2">{tx('快速发现主机和常见端口', 'Fast host and common port discovery')}</td></tr>
                <tr><td className="border px-3 py-2">Stealth</td><td className="border px-3 py-2">STEALTH</td><td className="border px-3 py-2">22,80,443,3389</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('关闭', 'Off')}</td><td className="border px-3 py-2">{tx('关闭', 'Off')}</td><td className="border px-3 py-2">{tx('低速、轻量、较隐蔽的探测', 'Low-speed, lightweight, stealthy probing')}</td></tr>
                <tr><td className="border px-3 py-2">Web</td><td className="border px-3 py-2">WEB</td><td className="border px-3 py-2">80,443,8080,8443,8000,3000,5000</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('关闭', 'Off')}</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('Web 服务专项探测', 'Web service discovery')}</td></tr>
                <tr><td className="border px-3 py-2">Full</td><td className="border px-3 py-2">FULL</td><td className="border px-3 py-2">1-65535</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('开启', 'On')}</td><td className="border px-3 py-2">{tx('全端口、漏洞与风险分析', 'Full port, vulnerability and risk analysis')}</td></tr>
              </tbody>
            </table>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-2">
            {tx(
              'Full 会扫描 1-65535 端口，耗时明显更长。建议先使用 Quick 或 Web 模板确认目标开放端口，再对授权目标执行 Full 扫描。',
              'Full scan covers all 1-65535 ports and takes significantly longer. We recommend identifying open ports with Quick or Web templates first, then running Full on authorized targets.'
            )}
          </p>

          <ManualH3 id="feature-switches" title={text.section33} />
          <p className="mb-3">
            {tx(
              '创建扫描任务时，三个功能开关控制不同扫描行为。未启用的功能不会在当前任务中执行，对应 Pipeline 阶段会显示 skipped。',
              'Three feature toggles control scan behavior when creating a task. Disabled features are not executed in the current task, and the corresponding Pipeline stages show as skipped.'
            )}
          </p>
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('开关', 'Toggle')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('作用', 'Function')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('关闭后的行为', 'When Disabled')}</th>
                </tr>
              </thead>
              <tbody>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('指纹识别', 'Fingerprint')}</td>
                  <td className="border px-3 py-2">{tx('识别服务类型、版本和基础指纹', 'Identify service type, version, and basic fingerprint')}</td>
                  <td className="border px-3 py-2">{tx('服务识别阶段会跳过', 'Service identification stage is skipped')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('漏洞扫描', 'Vuln Scan')}</td>
                  <td className="border px-3 py-2">{tx('调用漏洞检测能力并进行 CVE 匹配', 'Run vulnerability detection and CVE matching')}</td>
                  <td className="border px-3 py-2">{tx('漏洞检测和 CVE 匹配阶段会跳过', 'Vulnerability detection and CVE matching stages are skipped')}</td>
                </tr>
                <tr>
                  <td className="border px-3 py-2 font-medium">{tx('爬虫发现', 'Crawler')}</td>
                  <td className="border px-3 py-2">{tx('对 Web 服务进行页面发现', 'Discover pages on web services')}</td>
                  <td className="border px-3 py-2">{tx('Web 探测 / 爬虫相关结果不会生成', 'Web probing and crawler results are not generated')}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <ManualH3 id="pipeline-stages" title={text.section34} />
          <p className="mb-3">
            {tx(
              '扫描任务按 Pipeline 顺序执行各阶段，每个阶段有独立的状态标识。',
              'Scan tasks execute stages sequentially through a Pipeline, with each stage having its own status indicator.'
            )}
          </p>
          <div className="overflow-x-auto my-3">
            <table className="min-w-full border border-gray-200 dark:border-gray-700 text-sm">
              <thead className="bg-gray-100 dark:bg-gray-800">
                <tr>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('状态', 'Status')}</th>
                  <th className="border border-gray-200 dark:border-gray-700 px-3 py-2 text-left">{tx('含义', 'Meaning')}</th>
                </tr>
              </thead>
              <tbody>
                <tr><td className="border px-3 py-2 font-medium">pending</td><td className="border px-3 py-2">{tx('等待执行，仅应出现在任务运行中', 'Waiting — only appears while the task is running')}</td></tr>
                <tr><td className="border px-3 py-2 font-medium">running</td><td className="border px-3 py-2">{tx('正在执行', 'In progress')}</td></tr>
                <tr><td className="border px-3 py-2 font-medium">completed</td><td className="border px-3 py-2">{tx('已完成', 'Completed')}</td></tr>
                <tr><td className="border px-3 py-2 font-medium">skipped</td><td className="border px-3 py-2">{tx('当前扫描配置不适用或未启用，已跳过', 'Not applicable or not enabled — skipped')}</td></tr>
                <tr><td className="border px-3 py-2 font-medium">failed</td><td className="border px-3 py-2">{tx('阶段执行失败', 'Stage execution failed')}</td></tr>
              </tbody>
            </table>
          </div>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
            {tx(
              '任务进入 completed / failed / canceled 终态后，Pipeline 不应再出现 pending。未启用或不适用的阶段会显示 skipped，并展示跳过原因。',
              'When a task reaches a terminal state (completed / failed / canceled), no pending stages should remain. Inapplicable stages display as skipped with the reason shown.'
            )}
          </p>

          <ManualH3 id="long-scans" title={text.section35} />
          <p>
            {tx(
              '真实扫描耗时取决于端口范围、网络质量、安全组配置和目标响应速度。Full 全端口扫描可能运行较久。如果实时连接断开，页面会自动切换为轮询刷新，扫描任务仍在后端继续运行。用户可以继续等待，也可以点击取消任务。',
              'Scan duration depends on port range, network quality, firewall configuration, and target responsiveness. Full port-range scans can take a long time. If the real-time connection drops, the page automatically switches to polling mode — the scan continues server-side. You may wait or cancel the task.'
            )}
          </p>

          {/* ========== 4. Asset Management ========== */}
          <ManualH2 id="assets" title={text.section4} />
          <p className="mb-3">
            {tx(
              '资产管理页面用于查看扫描发现的主机资产、开放端口数量、高危漏洞数量和最近扫描时间。用户可以进入资产详情查看关联端口、漏洞、指纹和风险信息。',
              'The asset management page displays discovered hosts, open ports, high-risk vulnerabilities, and last scan time. Click an asset to view its ports, vulnerabilities, fingerprints, and risk details.'
            )}
          </p>

          <ManualH3 id="asset-list" title={text.section41} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('支持关键字搜索、合并、删除与分页。', 'Keyword search, merge, delete, and pagination.')}</li>
            <li>{tx('从扫描详情跳转时会显示任务筛选标签，方便定位特定任务的资产。', 'A task filter badge appears when navigating from scan details.')}</li>
            <li>{tx('开放端口数量可跳转到资产详情的端口区块。', 'Click the open-port count to jump to the port section in asset details.')}</li>
          </ul>

          <ManualH3 id="asset-detail" title={text.section42} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('统一查看端口、指纹、漏洞、SSL 证书、爬虫结果与蜜罐迹象。', 'Unified view of ports, fingerprints, vulnerabilities, SSL certificates, crawler results, and honeypot indicators.')}</li>
            <li>{tx('支持外部情报快捷查询（IP 信誉 / Censys / VirusTotal）。', 'Quick external intel lookup (IP reputation / Censys / VirusTotal).')}</li>
          </ul>

          {/* ========== 5. Report Center ========== */}
          <ManualH2 id="reports" title={text.section5} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('报告中心支持基于扫描结果生成 PDF / Excel 报告。', 'The report center generates PDF and Excel reports based on scan results.')}</li>
            <li>{tx('报告中会标明数据来源：Demo Mode 模拟数据或 Real Mode 真实扫描数据。', 'Reports indicate the data source: Demo Mode simulated data or Real Mode scan data.')}</li>
            <li>{tx('只有状态为 completed 的任务才能导出报告。', 'Only tasks with a completed status can export reports.')}</li>
          </ul>

          {/* ========== 6. AI Briefing ========== */}
          <ManualH2 id="ai-briefing" title={text.section6} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('AI 简报用于根据资产、漏洞、风险评分和报告数据生成摘要，帮助快速理解当前攻击面风险。', 'AI Briefing summarizes assets, vulnerabilities, risk scores, and report data to help you quickly understand your current attack surface risk.')}</li>
            <li>{tx('支持自由格式输入和结构化风险摘要输出。', 'Supports free-form input and structured risk summary output.')}</li>
          </ul>

          {/* ========== 7. Admin Configuration ========== */}
          <ManualH2 id="admin-config" title={text.section7} />

          <ManualH3 id="admin-tools" title={text.section71} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('大规模扫描前，请在设置中确认 Nmap / Nuclei 路径是否正确。', 'Before running large scans, verify Nmap and Nuclei paths in Settings.')}</li>
            <li>{tx('扫描策略插件支持自定义命令模板与解析模式。', 'Scan strategy plugins support custom command templates and parser modes.')}</li>
            <li>{tx('多后端实例部署时建议启用 Redis 作为并发协调。', 'Recommended: enable Redis for concurrency coordination across multiple backend instances.')}</li>
          </ul>

          <ManualH3 id="admin-notify" title={text.section72} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('Webhook 通知：钉钉 / 飞书 / 企业微信。', 'Webhook notification: DingTalk / Feishu / WeCom.')}</li>
            <li>{tx('邮件通知：配置 SMTP 地址、账号与授权码。', 'Email notification: configure SMTP host, account, and auth code.')}</li>
            <li>{tx('操作日志支持筛选与导出，用于审计。', 'Operation logs support filtering and export for audit.')}</li>
          </ul>

          {/* ========== 8. FAQ ========== */}
          <ManualH2 id="faq" title={text.section8} />

          <ManualH3 id="faq-real-mode" title={text.section81} />
          <p>
            {tx(
              '常见原因是安全组未开放端口、目标防火墙拦截、端口范围未覆盖真实开放端口，或网络响应较慢。建议先确认安全组开放端口，并使用 Quick 或 Web 模板指定端口扫描。',
              'Common causes include: security groups not allowing the port, firewall blocking on the target, port range not covering the actual open port, or slow network response. Check your security group rules and try Quick or Web templates to confirm open ports.'
            )}
          </p>

          <ManualH3 id="faq-full-scan" title={text.section82} />
          <p>
            {tx(
              'Full 会扫描 1-65535 端口，耗时取决于网络和目标响应情况。建议先使用 Quick 或 Web 模板确认端口，再执行 Full。',
              'Full scan covers all 1-65535 ports. Duration depends on network conditions and target responsiveness. Use Quick or Web templates first to identify open ports before running Full.'
            )}
          </p>

          <ManualH3 id="faq-skipped" title={text.section83} />
          <p>
            {tx(
              'skipped 表示该阶段不适用于当前扫描配置，或对应功能未启用。例如未启用漏洞扫描时，漏洞检测和 CVE 匹配阶段会跳过。',
              'Skipped means the stage is not applicable to the current scan configuration or the corresponding feature is disabled. For example, disabling vulnerability scan causes the vulnerability detection and CVE matching stages to be skipped.'
            )}
          </p>

          <ManualH3 id="faq-demo-data" title={text.section84} />
          <p>
            {tx(
              '不是的。Demo Mode 生成的是模拟数据，仅用于演示和功能体验，不代表真实资产风险。',
              'No. Demo Mode generates simulated data for demonstration and evaluation purposes only. It does not reflect real asset risk.'
            )}
          </p>

          <ManualH3 id="faq-nmap-detected" title={text.section85} />
          <p>
            {tx(
              '工具是否可用和是否执行真实扫描是两个概念。Demo Mode 下即使检测到 Nmap，也不会执行真实扫描。只有切换到 Real Mode 后才会调用真实工具。',
              'Tool availability and whether real scanning is executed are two separate concepts. In Demo Mode, real scanning is never performed even if Nmap is detected. Real tool execution only happens in Real Mode.'
            )}
          </p>

          {/* ========== 9. Version Capabilities ========== */}
          <ManualH2 id="capabilities" title={text.section9} />
          <p className="mb-3">
            {tx(
              '以下为 ServerScout 当前版本支持的核心能力：',
              'The following core capabilities are available in the current version of ServerScout:'
            )}
          </p>
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('支持 Demo Mode / Real Mode 两种运行模式。', 'Demo Mode / Real Mode operation.')}</li>
            <li>{tx('支持 Quick、Stealth、Web、Full 四类扫描模板。', 'Quick, Stealth, Web, and Full scan templates.')}</li>
            <li>{tx('支持扫描阶段 Pipeline 展示与状态追踪。', 'Pipeline stage display with status tracking.')}</li>
            <li>{tx('支持资产、端口、漏洞、爬虫发现和风险评分管理。', 'Asset, port, vulnerability, crawler, and risk score management.')}</li>
            <li>{tx('支持 PDF / Excel 报告导出。', 'PDF and Excel report export.')}</li>
            <li>{tx('支持长时间扫描的轮询刷新。', 'Polling refresh for long-running scans.')}</li>
            <li>{tx('支持资产关联数据安全删除。', 'Safe deletion of asset-related data.')}</li>
          </ul>

          <h3 className="text-lg font-semibold text-gray-800 dark:text-gray-200 mt-8 mb-2">{text.linksTitle}</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
            {text.links.map((link) => (
              <a
                key={link.path}
                href={link.path}
                className="flex items-center justify-between px-3 py-2 rounded border border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800"
              >
                <span className="text-sm">{link.label}</span>
                <span className="text-xs text-blue-600 dark:text-blue-400">{text.btnGo}</span>
              </a>
            ))}
          </div>
        </div>
      </div>
    </div>
  )
}
