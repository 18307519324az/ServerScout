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
    subtitle: '版本 v1.0 | 更新日期 2026-05-30',
    docsLabel: '在线 API 文档',
    docsUrl: 'http://localhost:8080/docs',
    linksTitle: '快速链接',
    links: [
      { label: '登录页', path: '/login' },
      { label: '扫描任务', path: '/scan-tasks' },
      { label: '资产管理', path: '/assets' },
      { label: '网络拓扑图', path: '/topology' },
      { label: '攻击面地图', path: '/attack-surface' },
      { label: '外部情报', path: '/intel' },
      { label: '报告中心', path: '/reports' },
      { label: '系统设置', path: '/settings' },
    ],
    section1: '1. 快速开始',
    section11: '1.1 环境要求',
    section12: '1.2 启动顺序',
    section13: '1.3 首次登录',
    section2: '2. 功能总览（按页面）',
    section21: '2.1 登录前展示页（/）',
    section22: '2.2 扫描任务（/scan-tasks）',
    section23: '2.3 扫描详情（/scan-tasks/:id）',
    section24: '2.4 资产管理（/assets）',
    section25: '2.5 资产详情（/assets/:id）',
    section26: '2.6 网络拓扑图（/topology）',
    section27: '2.7 攻击面地图（/attack-surface）',
    section28: '2.8 外部情报与报告',
    section3: '3. 管理员配置与建议',
    section31: '3.1 扫描工具与插件',
    section32: '3.2 通知与审计',
    section4: '4. 常见问题与排查',
    section41: '4.1 扫描任务卡在 pending',
    section42: '4.2 删除运行中任务报错',
    section43: '4.3 assets found 跳转为空',
    section44: '4.4 目录高亮不实时变化',
    section5: '5. 近期更新（本次）',
    tableHeaderName: '组件',
    tableHeaderVersion: '建议版本',
    tableHeaderNote: '说明',
    btnGo: '打开',
  },
  en: {
    toc: 'Table of Contents',
    title: 'ServerScout User Manual',
    subtitle: 'Version v1.0 | Updated on 2026-05-30',
    docsLabel: 'Online API Docs',
    docsUrl: 'http://localhost:8080/docs',
    linksTitle: 'Quick Links',
    links: [
      { label: 'Login', path: '/login' },
      { label: 'Scan Tasks', path: '/scan-tasks' },
      { label: 'Assets', path: '/assets' },
      { label: 'Topology', path: '/topology' },
      { label: 'Attack Surface', path: '/attack-surface' },
      { label: 'Threat Intel', path: '/intel' },
      { label: 'Reports', path: '/reports' },
      { label: 'Settings', path: '/settings' },
    ],
    section1: '1. Quick Start',
    section11: '1.1 Requirements',
    section12: '1.2 Startup Order',
    section13: '1.3 First Login',
    section2: '2. Feature Overview (by page)',
    section21: '2.1 Showcase Before Login (/)',
    section22: '2.2 Scan Tasks (/scan-tasks)',
    section23: '2.3 Scan Task Detail (/scan-tasks/:id)',
    section24: '2.4 Asset Management (/assets)',
    section25: '2.5 Asset Detail (/assets/:id)',
    section26: '2.6 Network Topology (/topology)',
    section27: '2.7 Attack Surface Map (/attack-surface)',
    section28: '2.8 Threat Intel and Reports',
    section3: '3. Admin Configuration',
    section31: '3.1 Scan Tools and Plugins',
    section32: '3.2 Notifications and Auditing',
    section4: '4. Troubleshooting',
    section41: '4.1 Task stays in pending',
    section42: '4.2 Deleting running task fails',
    section43: '4.3 assets found navigates to empty list',
    section44: '4.4 TOC highlight does not update in real time',
    section5: '5. Recent Updates (this round)',
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

          <ManualH2 id="quickstart" title={text.section1} />
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
                <tr><td className="border px-3 py-2">Java</td><td className="border px-3 py-2">17+</td><td className="border px-3 py-2">{tx('后端运行环境（Spring Boot）', 'Spring Boot backend runtime')}</td></tr>
                <tr><td className="border px-3 py-2">MySQL</td><td className="border px-3 py-2">8.0+</td><td className="border px-3 py-2">{tx('主数据存储', 'Primary storage')}</td></tr>
                <tr><td className="border px-3 py-2">Node.js</td><td className="border px-3 py-2">18+</td><td className="border px-3 py-2">{tx('前端构建与运行', 'Frontend build/runtime')}</td></tr>
                <tr><td className="border px-3 py-2">Nmap</td><td className="border px-3 py-2">7.x+</td><td className="border px-3 py-2">{tx('端口与服务扫描', 'Port and service scanning')}</td></tr>
                <tr><td className="border px-3 py-2">Nuclei</td><td className="border px-3 py-2">3.x+</td><td className="border px-3 py-2">{tx('漏洞模板扫描', 'Vulnerability templates')}</td></tr>
                <tr><td className="border px-3 py-2">Redis</td><td className="border px-3 py-2">{tx('6/7（可选）', '6/7 (optional)')}</td><td className="border px-3 py-2">{tx('多实例并发控制（同目标限流）', 'Cross-process target concurrency control')}</td></tr>
              </tbody>
            </table>
          </div>

          <ManualH3 id="startup" title={text.section12} />
          <ol className="list-decimal ml-6 space-y-1">
            <li>{tx('先启动 MySQL，确保 `serverscout` 数据库存在。', 'Start MySQL and ensure database `serverscout` exists.')}</li>
            <li>{tx('先启动后端（`backend`，默认端口 `8080`）。', 'Start backend first (`backend`, default port `8080`).')}</li>
            <li>{tx('再启动前端（`frontend`，默认端口 `5173`）。', 'Start frontend dev server (`frontend`, default port `5173`).')}</li>
            <li>{tx('先访问 `/` 展示页，再进入 `/login` 登录。', 'Access `/` (showcase), then go to `/login`.')}</li>
          </ol>

          <ManualH3 id="first-login" title={text.section13} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('完成验证码后登录。', 'Complete captcha and log in.')}</li>
            <li>{tx('首次登录后请在设置中立即修改默认密码。', 'Change default password immediately in Settings.')}</li>
            <li>{tx('管理员建议先检查扫描工具路径和通知配置，再执行生产扫描。', 'Admin users should verify tool paths and notification settings before the first production scan.')}</li>
          </ul>

          <ManualH2 id="overview" title={text.section2} />
          <ManualH3 id="showcase" title={text.section21} />
          <p>{tx('展示页用于登录前能力说明，支持点击截图预览与中英文文案切换。', 'Showcase page presents platform capabilities before authentication. It supports image preview and bilingual content switching.')}</p>

          <ManualH3 id="scan-tasks" title={text.section22} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('支持预设模板（`quick` / `stealth` / `web` / `full`）和插件扫描类型。', 'Create tasks using presets (`quick`, `stealth`, `web`, `full`) or plugin scan types.')}</li>
            <li>{tx('支持多目标输入与端口范围自定义。', 'Supports multi-target input and custom port range.')}</li>
            <li>{tx('任务列表中的资产数支持直接跳转到过滤后的资产列表（`/assets?taskId=...`）。', 'Task list supports direct jump from asset count to filtered asset list (`/assets?taskId=...`).')}</li>
          </ul>

          <ManualH3 id="scan-detail" title={text.section23} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('通过 SSE 展示实时扫描进度与发现日志。', 'Real-time progress and discovery feed via SSE.')}</li>
            <li>{tx('`assets found` 支持一键跳转到该任务对应资产。', 'One-click jump from `assets found` to related asset list.')}</li>
            <li>{tx('任务摘要区域可导出 PDF / Excel 报告。', 'PDF/Excel export is available on task summary block.')}</li>
          </ul>

          <ManualH3 id="assets" title={text.section24} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('支持关键字搜索、合并、删除与分页。', 'Keyword search, merge, delete, pagination.')}</li>
            <li>{tx('从扫描详情跳转时会显示任务筛选标签。', 'Task filter badge appears when entering from scan detail.')}</li>
            <li>{tx('开放端口数量可跳转到资产详情的端口区块。', 'Open-port count can jump to the corresponding asset port section.')}</li>
          </ul>

          <ManualH3 id="asset-detail" title={text.section25} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('统一查看端口、指纹、漏洞、SSL 证书、爬虫结果与蜜罐迹象。', 'Unified view of ports, fingerprints, vulnerabilities, SSL certificates, crawler results and honeypot indicators.')}</li>
            <li>{tx('支持外部情报快捷查询（IP 信誉 / Censys / VirusTotal）。', 'Supports quick external intel lookup (IP reputation / Censys / VirusTotal).')}</li>
          </ul>

          <ManualH3 id="topology" title={text.section26} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('拓扑图支持拖拽、缩放、适合窗口、全屏查看。', 'Topology supports drag, zoom, fit view, and full-screen navigation.')}</li>
            <li>{tx('已优化排版，减少连线过密并让整体居中展示。', 'Layout was optimized to reduce dense edge overlap and keep graph centered in viewport.')}</li>
          </ul>

          <ManualH3 id="attack-surface" title={text.section27} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('包含攻击面图谱与技术栈雷达。', 'Attack-surface graph plus tech-stack radar.')}</li>
            <li>{tx('雷达图分类、图例与指标支持中英文切换。', 'Radar labels and legend support Chinese/English switching.')}</li>
          </ul>

          <ManualH3 id="intel-and-report" title={text.section28} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('外部情报页支持 IP / 域名 / CVE 查询与综合报告视图。', 'Threat intel page supports IP/domain/CVE queries and combined report view.')}</li>
            <li>{tx('报告中心支持按任务导出 PDF 与 Excel。', 'Report center supports task-based PDF and Excel export.')}</li>
          </ul>

          <ManualH2 id="admin-config" title={text.section3} />
          <ManualH3 id="admin-tools" title={text.section31} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('大规模扫描前，请先在设置中确认 Nmap / Nuclei 路径。', 'Configure Nmap/Nuclei path in Settings before enabling large scans.')}</li>
            <li>{tx('扫描策略插件（L2）支持自定义命令模板与解析模式。', 'Scan strategy plugins (L2) support custom command templates and parser modes.')}</li>
            <li>{tx('多后端实例部署时建议启用 Redis 作为并发协调。', 'Recommended production setting: enable Redis when deploying multiple backend instances.')}</li>
          </ul>

          <ManualH3 id="admin-notify" title={text.section32} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('Webhook 通知：钉钉 / 飞书 / 企业微信。', 'Webhook notification: DingTalk / Feishu / WeCom.')}</li>
            <li>{tx('邮件通知：配置 SMTP 地址、账号与授权码。', 'Email notification: configure SMTP host, account, and auth code.')}</li>
            <li>{tx('操作日志支持筛选与导出，用于审计。', 'Operation logs support filtering and export for audit.')}</li>
          </ul>

          <ManualH2 id="troubleshooting" title={text.section4} />
          <ManualH3 id="faq-pending" title={text.section41} />
          <p>
            {tx('先确认该目标是否已有运行中的任务。当前默认限制为', 'Check whether the same target is already occupied by a running task. Current default is')}
            <code className="mx-1 px-1 rounded bg-gray-100 dark:bg-gray-800">max-per-target = 1</code>
            {tx('，用于避免同一目标被重复高强度扫描。', 'to avoid duplicate aggressive scans on the same host.')}
          </p>

          <ManualH3 id="faq-delete" title={text.section42} />
          <p>
            {tx(
              '若删除运行中任务失败，请确认后端已更新到当前版本（删除流程会按顺序处理取消与清理）。',
              'If deleting a running task fails, verify backend is updated to current version where running-task delete path handles cancellation and cleanup in order.'
            )}
          </p>

          <ManualH3 id="faq-assets-empty" title={text.section43} />
          <p>
            {tx(
              '若 assets found 跳转后为空，请确认后端已使用任务过滤兼容分页（任务查询不应套用无效映射排序字段）。',
              'Ensure backend uses task-filter query compatible pagination (task-scoped query should not apply invalid mapping sort field).'
            )}
          </p>

          <ManualH3 id="faq-toc-live" title={text.section44} />
          <p>
            {tx(
              '本页目录已改为从实际标题自动生成，并随滚动位置实时同步高亮。',
              'TOC in this page is now auto-built from rendered headings and synchronized in real time with scroll position.'
            )}
          </p>

          <ManualH2 id="release-note" title={text.section5} />
          <ul className="list-disc ml-6 space-y-1">
            <li>{tx('同一目标默认并发上限恢复为 1。', 'Same-target default concurrency restored to 1.')}</li>
            <li>{tx('补充扫描详情 `assets found` 跳转与关联资产过滤说明。', 'Scan detail `assets found` jump and related asset filtering behavior documented.')}</li>
            <li>{tx('用户手册目录升级为自动生成 + 滚动实时高亮，修复目录不实时变化问题。', 'Manual TOC upgraded to auto-generate + live scroll highlight, fixing non-realtime directory state.')}</li>
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

