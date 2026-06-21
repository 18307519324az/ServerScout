import {
  ChevronLeft,
  ChevronRight,
  ArrowRight,
  Blocks,
  Bug,
  Eye,
  FileBarChart2,
  Globe,
  Network,
  Radar,
  ScanSearch,
  ShieldCheck,
  Sparkles,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { createPortal } from 'react-dom'
import { useNavigate } from 'react-router-dom'

type Lang = 'zh' | 'en'

function hasValidToken() {
  const token = localStorage.getItem('token')
  if (!token) return false
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return !!payload.exp && Date.now() < payload.exp * 1000
  } catch {
    return false
  }
}

function detectSystemLang(): Lang {
  return 'en'
}

const snapshotDefs = [
  { key: 'dashboard', src: '/showcase/dashboard.png' },
  { key: 'scanTasks', src: '/showcase/scan-tasks.png' },
  { key: 'assets', src: '/showcase/assets.png' },
  { key: 'assetDetail', src: '/showcase/asset-detail.png' },
  { key: 'vulnerabilities', src: '/showcase/vulnerabilities.png' },
  { key: 'topology', src: '/showcase/topology.png' },
  { key: 'attackSurface', src: '/showcase/attack-surface.png' },
  { key: 'reports', src: '/showcase/reports.png' },
  { key: 'scanTaskDetail', src: '/showcase/scan-task-detail.png' },
  { key: 'vulnerabilityDetail', src: '/showcase/vulnerability-detail.png' },
  { key: 'intel', src: '/showcase/intel.png' },
] as const

type SnapshotKey = (typeof snapshotDefs)[number]['key']
type Snapshot = {
  key: SnapshotKey
  src: string
  title: string
  tag: string
}

const moduleIcons = [Radar, ScanSearch, Bug, Network] as const

const copy: Record<
  Lang,
  {
    nav: { platform: string; capabilities: string; evidence: string; architecture: string }
    subtitle: string
    badge: string
    heroTitle1: string
    heroTitle2: string
    heroDesc: string
    login: string
    startNow: string
    openConsole: string
    goToLogin: string
    viewEvidence: string
    liveConsole: string
    online: string
    workspace: string
    previewHint: string
    closePreview: string
    modules: { title: string; desc: string }[]
    metrics: { managedAssets: string; runningScans: string; criticalFindings: string }
    sectionCaps: string
    sectionEvidence: string
    sectionWorkflow: string
    steps: string[]
    snapshotMeta: Record<SnapshotKey, { title: string; tag: string }>
  }
> = {
  en: {
    nav: { platform: 'Platform', capabilities: 'Capabilities', evidence: 'Evidence', architecture: 'Workflow' },
    subtitle: 'Attack Surface Intelligence Platform',
    badge: 'Enterprise Attack Surface Management',
    heroTitle1: 'Visual Security Operations',
    heroTitle2: 'from Asset to Vulnerability',
    heroDesc:
      'ServerScout unifies asset inventory, scanning orchestration, topology analysis and report output into one operational surface for continuous external exposure management.',
    login: 'Login',
    startNow: 'Start Now',
    openConsole: 'Open Console',
    goToLogin: 'Go to Login',
    viewEvidence: 'View Product Evidence',
    liveConsole: 'Live Security Console',
    online: 'online',
    workspace: 'workspace',
    previewHint: 'Click image to preview',
    closePreview: 'Close',
    modules: [
      { title: 'Asset Discovery', desc: 'Host, subnet and service discovery with task traceability.' },
      { title: 'Scan Orchestration', desc: 'Queue-based scanning pipeline with progress tracking.' },
      { title: 'Vulnerability Workflow', desc: 'Nuclei-based findings, severity grading and closure loop.' },
      { title: 'Topology & Attack Surface', desc: 'Interactive graph and attack-surface intelligence views.' },
    ],
    metrics: {
      managedAssets: 'Managed Assets',
      runningScans: 'Running Scans',
      criticalFindings: 'Critical Findings',
    },
    sectionCaps: 'Core Capabilities',
    sectionEvidence: 'Product Evidence Gallery',
    sectionWorkflow: 'Delivery Workflow',
    steps: ['Asset Discovery', 'Service Fingerprinting', 'Vulnerability Detection', 'Reporting & Export'],
    snapshotMeta: {
      dashboard: { title: 'Security Dashboard', tag: 'Overview' },
      scanTasks: { title: 'Scan Task Center', tag: 'Operations' },
      assets: { title: 'Asset Inventory', tag: 'Asset Mgmt' },
      assetDetail: { title: 'Asset Detail & Ports', tag: 'Forensics' },
      vulnerabilities: { title: 'Vulnerability List', tag: 'Risk' },
      topology: { title: 'Asset Topology', tag: 'Graph' },
      attackSurface: { title: 'Attack Surface Map', tag: 'Analytics' },
      reports: { title: 'Report Center', tag: 'Reporting' },
      scanTaskDetail: { title: 'Scan Task Detail', tag: 'Progress' },
      vulnerabilityDetail: { title: 'Vulnerability Detail', tag: 'Detail' },
      intel: { title: 'Intel Center', tag: 'Threat Intel' },
    },
  },
  zh: {
    nav: { platform: '平台', capabilities: '核心能力', evidence: '功能截图', architecture: '交付流程' },
    subtitle: '攻击面情报分析平台',
    badge: '企业级攻击面管理',
    heroTitle1: '可视化安全运营',
    heroTitle2: '从资产到漏洞全流程',
    heroDesc:
      'ServerScout 将资产盘点、扫描编排、拓扑分析与报告输出整合为统一作业界面，帮助团队持续管理外网暴露风险。',
    login: '登录',
    startNow: '立即体验',
    openConsole: '进入控制台',
    goToLogin: '前往登录',
    viewEvidence: '查看功能截图',
    liveConsole: '实时安全总览',
    online: '在线',
    workspace: '工作区',
    previewHint: '点击图片可预览',
    closePreview: '关闭',
    modules: [
      { title: '资产发现', desc: '支持主机、网段与服务识别，并可按任务追溯资产来源。' },
      { title: '扫描编排', desc: '基于队列的任务执行与进度追踪，便于持续化运营。' },
      { title: '漏洞闭环', desc: '结合 Nuclei 与规则匹配，形成分级处置与修复流程。' },
      { title: '拓扑与攻击面', desc: '提供可交互资产拓扑与攻击面态势分析视图。' },
    ],
    metrics: {
      managedAssets: '已管理资产',
      runningScans: '运行中任务',
      criticalFindings: '高危发现',
    },
    sectionCaps: '核心能力',
    sectionEvidence: '功能截图集',
    sectionWorkflow: '标准交付流程',
    steps: ['资产发现', '服务识别', '漏洞检测', '报告导出与通知'],
    snapshotMeta: {
      dashboard: { title: '安全总览仪表盘', tag: '总览' },
      scanTasks: { title: '扫描任务中心', tag: '任务' },
      assets: { title: '资产列表', tag: '资产' },
      assetDetail: { title: '资产详情与端口', tag: '详情' },
      vulnerabilities: { title: '漏洞列表', tag: '风险' },
      topology: { title: '资产拓扑图', tag: '图谱' },
      attackSurface: { title: '攻击面地图', tag: '分析' },
      reports: { title: '报告中心', tag: '报告' },
      scanTaskDetail: { title: '任务详情', tag: '进度' },
      vulnerabilityDetail: { title: '漏洞详情', tag: '处置' },
      intel: { title: '外部情报中心', tag: '情报' },
    },
  },
}

function ShotButton({
  shot,
  className,
  imageClassName,
  hint,
  onOpen,
}: {
  shot: Snapshot
  className: string
  imageClassName: string
  hint: string
  onOpen: (shot: Snapshot) => void
}) {
  return (
    <button
      type="button"
      onClick={() => onOpen(shot)}
      className={`group relative block overflow-hidden ${className}`}
      title={hint}
      aria-label={`${hint}: ${shot.title}`}
    >
      <img src={shot.src} alt={shot.title} className={imageClassName} loading="lazy" />
      <span className="pointer-events-none absolute inset-0 bg-black/0 transition group-hover:bg-black/20" />
      <span className="pointer-events-none absolute right-2 top-2 inline-flex items-center gap-1 rounded-md border border-white/20 bg-black/45 px-2 py-1 text-[11px] text-slate-100 opacity-0 transition group-hover:opacity-100">
        <Eye className="h-3 w-3" />
        {hint}
      </span>
    </button>
  )
}

export default function ShowcasePage() {
  const navigate = useNavigate()
  const loggedIn = useMemo(() => hasValidToken(), [])
  const lang = useMemo(() => detectSystemLang(), [])
  const text = copy[lang]
  const [preview, setPreview] = useState<Snapshot | null>(null)
  const [liveIdx, setLiveIdx] = useState(0)
  const [workspaceIdx, setWorkspaceIdx] = useState(0)
  const [pauseLive, setPauseLive] = useState(false)
  const [pauseWorkspace, setPauseWorkspace] = useState(false)
  const shotClass = 'h-full w-full rounded-lg object-contain [filter:brightness(0.72)_contrast(0.92)_saturate(0.88)]'

  useEffect(() => {
    if (!preview) return
    const onEsc = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setPreview(null)
    }
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    window.addEventListener('keydown', onEsc)
    return () => {
      document.body.style.overflow = previousOverflow
      window.removeEventListener('keydown', onEsc)
    }
  }, [preview])

  const navItems = [
    { id: 'platform', label: text.nav.platform },
    { id: 'capabilities', label: text.nav.capabilities },
    { id: 'evidence', label: text.nav.evidence },
    { id: 'architecture', label: text.nav.architecture },
  ]

  const modules = text.modules.map((module, idx) => ({ ...module, icon: moduleIcons[idx] }))
  const snapshots = snapshotDefs.map((item) => ({ ...item, ...text.snapshotMeta[item.key] }))
  const liveShots = [snapshots[0], snapshots[1], snapshots[4], snapshots[5]].filter(Boolean)
  const workspaceShots = [snapshots[2], snapshots[3], snapshots[6], snapshots[7]].filter(Boolean)
  useEffect(() => {
    if (pauseLive || liveShots.length <= 1) return
    const t = window.setInterval(() => setLiveIdx((v) => (v + 1) % liveShots.length), 4500)
    return () => window.clearInterval(t)
  }, [pauseLive, liveShots.length])

  useEffect(() => {
    if (pauseWorkspace || workspaceShots.length <= 1) return
    const t = window.setInterval(() => setWorkspaceIdx((v) => (v + 1) % workspaceShots.length), 5000)
    return () => window.clearInterval(t)
  }, [pauseWorkspace, workspaceShots.length])

  const openPreview = (shot: Snapshot) => setPreview(shot)
  const previewModal = preview
    ? createPortal(
      <div className="fixed inset-0 z-[9999] flex items-center justify-center bg-black/80 px-4 py-6" onClick={() => setPreview(null)}>
        <div
          className="w-full max-w-6xl rounded-2xl border border-white/15 bg-[#070b12] p-4 shadow-2xl shadow-black/50"
          onClick={(event) => event.stopPropagation()}
        >
          <div className="mb-3 flex items-center justify-between">
            <p className="truncate text-sm font-medium text-slate-200">{preview.title}</p>
            <button
              type="button"
              onClick={() => setPreview(null)}
              className="inline-flex items-center gap-1 rounded-md border border-white/20 px-3 py-1.5 text-xs text-slate-200 hover:bg-white/10"
            >
              <X className="h-3.5 w-3.5" />
              {text.closePreview}
            </button>
          </div>
          <div className="flex max-h-[82vh] items-center justify-center rounded-xl border border-white/10 bg-[#02040a] p-3">
            <img src={preview.src} alt={preview.title} className="max-h-[77vh] w-auto max-w-full object-contain" />
          </div>
        </div>
      </div>,
      document.body
    )
    : null

  return (
    <div className="relative min-h-screen overflow-x-hidden bg-[#02040a] text-slate-100">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_0%_20%,rgba(59,130,246,0.07),transparent_36%),radial-gradient(circle_at_90%_12%,rgba(14,165,233,0.05),transparent_38%),radial-gradient(circle_at_50%_100%,rgba(30,64,175,0.08),transparent_46%)]" />

      <header className="sticky top-0 z-30 border-b border-white/10 bg-[#04060b]/95 backdrop-blur-xl">
        <div className="mx-auto flex h-20 w-full max-w-[1540px] items-center justify-between px-6">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl border border-sky-300/30 bg-sky-300/10">
              <ShieldCheck className="h-5 w-5 text-sky-300" />
            </div>
            <div>
              <p className="text-lg font-semibold tracking-tight">ServerScout</p>
              <p className="text-xs text-slate-400">{text.subtitle}</p>
            </div>
          </div>

          <nav className="flex items-center gap-6 text-sm text-slate-300">
            {navItems.map((item) => (
              <a key={item.id} href={`#${item.id}`} className="transition hover:text-white">
                {item.label}
              </a>
            ))}
          </nav>

          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/login')}
              className="rounded-lg border border-white/20 px-4 py-2 text-sm text-slate-200 transition hover:bg-white/10"
            >
              {text.login}
            </button>
            <button
              onClick={() => navigate(loggedIn ? '/dashboard' : '/login')}
              className="rounded-lg border border-sky-300/35 bg-sky-500/25 px-4 py-2 text-sm font-semibold text-sky-100 transition hover:bg-sky-500/35"
            >
              {loggedIn ? text.openConsole : text.startNow}
            </button>
          </div>
        </div>
      </header>

      <main className="relative z-10 mx-auto w-full max-w-6xl px-6 pb-24 pt-12">
        <section id="platform" className="grid grid-cols-1 items-end gap-8 pb-14 lg:grid-cols-2">
          <div>
            <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-sky-300/35 bg-sky-300/10 px-3 py-1 text-xs text-sky-200">
              <Sparkles className="h-3.5 w-3.5" />
              {text.badge}
            </p>
            <h1 className="text-4xl font-semibold leading-tight text-white sm:text-5xl xl:text-6xl">
              {text.heroTitle1}
              <br />
              {text.heroTitle2}
            </h1>
            <p className="mt-6 max-w-2xl text-base leading-8 text-slate-300">{text.heroDesc}</p>
            <div className="mt-8 flex flex-wrap gap-3">
              <button
                onClick={() => navigate('/login')}
                className="inline-flex items-center gap-2 rounded-lg border border-sky-300/35 bg-sky-500/25 px-5 py-3 text-sm font-semibold text-sky-100 transition hover:bg-sky-500/35"
              >
                {text.goToLogin}
                <ArrowRight className="h-4 w-4" />
              </button>
              <button
                onClick={() => document.getElementById('evidence')?.scrollIntoView({ behavior: 'smooth' })}
                className="rounded-lg border border-white/20 px-5 py-3 text-sm text-slate-100 transition hover:bg-white/10"
              >
                {text.viewEvidence}
              </button>
            </div>
          </div>

          <div className="rounded-2xl border border-white/10 bg-[#060a11]/95 p-4 shadow-2xl shadow-black/40" onMouseEnter={() => setPauseLive(true)} onMouseLeave={() => setPauseLive(false)}>
            <div className="mb-3 flex items-center justify-between border-b border-white/10 pb-3">
              <p className="text-sm font-medium text-slate-200">{text.liveConsole}</p>
              <div className="flex items-center gap-2">
                <button type="button" onClick={() => setLiveIdx((v) => (v - 1 + liveShots.length) % liveShots.length)} className="rounded border border-white/20 p-1 text-slate-300 hover:bg-white/10"><ChevronLeft className="h-3.5 w-3.5" /></button>
                <button type="button" onClick={() => setLiveIdx((v) => (v + 1) % liveShots.length)} className="rounded border border-white/20 p-1 text-slate-300 hover:bg-white/10"><ChevronRight className="h-3.5 w-3.5" /></button>
                <span className="rounded-full border border-emerald-300/40 bg-emerald-400/15 px-2 py-0.5 text-xs text-emerald-200">{text.online}</span>
              </div>
            </div>
            <div className="aspect-[16/10] rounded-xl border border-white/10 bg-[#02040a] p-2">
              <ShotButton
                shot={liveShots[liveIdx]}
                className="h-full w-full rounded-lg"
                imageClassName={`${shotClass} floating-image`}
                hint={text.previewHint}
                onOpen={openPreview}
              />
            </div>
            <div className="mt-4 grid grid-cols-3 gap-3">
              <div className="aspect-[16/11] rounded-lg border border-white/10 bg-[#02040a] p-1.5">
                <ShotButton
                  shot={liveShots[(liveIdx + 1) % liveShots.length]}
                  className="h-full w-full rounded-lg"
                  imageClassName={shotClass}
                  hint={text.previewHint}
                  onOpen={openPreview}
                />
              </div>
              <div className="aspect-[16/11] rounded-lg border border-white/10 bg-[#02040a] p-1.5">
                <ShotButton
                  shot={liveShots[(liveIdx + 2) % liveShots.length]}
                  className="h-full w-full rounded-lg"
                  imageClassName={shotClass}
                  hint={text.previewHint}
                  onOpen={openPreview}
                />
              </div>
              <div className="aspect-[16/11] rounded-lg border border-white/10 bg-[#02040a] p-1.5">
                <ShotButton
                  shot={liveShots[(liveIdx + 3) % liveShots.length]}
                  className="h-full w-full rounded-lg"
                  imageClassName={shotClass}
                  hint={text.previewHint}
                  onOpen={openPreview}
                />
              </div>
            </div>
            <div className="mt-3 flex items-center justify-center gap-2">
              {liveShots.map((_, i) => (
                <button key={i} onClick={() => setLiveIdx(i)} className={`h-2 rounded-full transition-all ${i === liveIdx ? 'w-5 bg-sky-300' : 'w-2 bg-white/30'}`} aria-label={`live-slide-${i + 1}`} />
              ))}
            </div>
          </div>
        </section>

        <section className="overflow-hidden rounded-2xl border border-white/10 bg-[#080c14]/80 shadow-2xl shadow-black/35" onMouseEnter={() => setPauseWorkspace(true)} onMouseLeave={() => setPauseWorkspace(false)}>
          <div className="flex min-h-[560px] flex-col lg:flex-row">
            <aside className="w-full border-b border-white/10 bg-[#0a0d12] p-4 lg:w-64 lg:border-b-0 lg:border-r">
              <p className="mb-4 px-2 text-xs uppercase tracking-[0.14em] text-slate-500">{text.workspace}</p>
              <div className="space-y-2">
                {modules.map((item, idx) => (
                  <button
                    key={item.title}
                    type="button"
                    onClick={() => setWorkspaceIdx(idx)}
                    className={`rounded-lg border px-3 py-2 ${
                      idx === workspaceIdx
                        ? 'border-sky-300/35 bg-sky-300/10 text-sky-100'
                        : 'border-white/10 bg-white/[0.02] text-slate-300'
                    } w-full text-left transition hover:border-sky-300/35 hover:bg-sky-300/5`}
                  >
                    <div className="flex items-center gap-2">
                      <item.icon className="h-4 w-4" />
                      <p className="text-sm font-medium">{item.title}</p>
                    </div>
                    <p className="mt-2 text-xs leading-5 text-slate-400">{item.desc}</p>
                  </button>
                ))}
              </div>
            </aside>

            <div className="flex-1 p-5">
              <div className="mb-5 grid gap-3 md:grid-cols-3">
                <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
                  <p className="text-xs text-slate-400">{text.metrics.managedAssets}</p>
                  <p className="mt-2 text-2xl font-semibold text-white">126</p>
                </div>
                <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
                  <p className="text-xs text-slate-400">{text.metrics.runningScans}</p>
                  <p className="mt-2 text-2xl font-semibold text-cyan-300">04</p>
                </div>
                <div className="rounded-xl border border-white/10 bg-white/[0.03] p-4">
                  <p className="text-xs text-slate-400">{text.metrics.criticalFindings}</p>
                  <p className="mt-2 text-2xl font-semibold text-rose-300">18</p>
                </div>
              </div>

              <div className="mb-3 flex items-center justify-end gap-2">
                <button type="button" onClick={() => setWorkspaceIdx((v) => (v - 1 + workspaceShots.length) % workspaceShots.length)} className="rounded border border-white/20 p-1 text-slate-300 hover:bg-white/10"><ChevronLeft className="h-3.5 w-3.5" /></button>
                <button type="button" onClick={() => setWorkspaceIdx((v) => (v + 1) % workspaceShots.length)} className="rounded border border-white/20 p-1 text-slate-300 hover:bg-white/10"><ChevronRight className="h-3.5 w-3.5" /></button>
              </div>

              <div className="grid gap-4 lg:grid-cols-2">
                <div className="aspect-[16/11] rounded-xl border border-white/10 bg-[#02040a] p-2">
                  <ShotButton
                    shot={workspaceShots[workspaceIdx]}
                    className="h-full w-full rounded-lg"
                    imageClassName={`${shotClass} floating-image`}
                    hint={text.previewHint}
                    onOpen={openPreview}
                  />
                </div>
                <div className="aspect-[16/11] rounded-xl border border-white/10 bg-[#02040a] p-2">
                  <ShotButton
                    shot={workspaceShots[(workspaceIdx + 1) % workspaceShots.length]}
                    className="h-full w-full rounded-lg"
                    imageClassName={shotClass}
                    hint={text.previewHint}
                    onOpen={openPreview}
                  />
                </div>
                <div className="aspect-[16/11] rounded-xl border border-white/10 bg-[#02040a] p-2">
                  <ShotButton
                    shot={workspaceShots[(workspaceIdx + 2) % workspaceShots.length]}
                    className="h-full w-full rounded-lg"
                    imageClassName={shotClass}
                    hint={text.previewHint}
                    onOpen={openPreview}
                  />
                </div>
                <div className="aspect-[16/11] rounded-xl border border-white/10 bg-[#02040a] p-2">
                  <ShotButton
                    shot={workspaceShots[(workspaceIdx + 3) % workspaceShots.length]}
                    className="h-full w-full rounded-lg"
                    imageClassName={shotClass}
                    hint={text.previewHint}
                    onOpen={openPreview}
                  />
                </div>
              </div>
              <div className="mt-3 flex items-center justify-center gap-2">
                {workspaceShots.map((_, i) => (
                  <button key={i} onClick={() => setWorkspaceIdx(i)} className={`h-2 rounded-full transition-all ${i === workspaceIdx ? 'w-5 bg-cyan-300' : 'w-2 bg-white/30'}`} aria-label={`workspace-slide-${i + 1}`} />
                ))}
              </div>
            </div>
          </div>
        </section>

        <section id="capabilities" className="mt-14">
          <div className="mb-5 flex items-center gap-2">
            <Blocks className="h-4 w-4 text-sky-300" />
            <h2 className="text-xl font-semibold text-white">{text.sectionCaps}</h2>
          </div>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {modules.map((module) => (
              <div key={module.title} className="rounded-xl border border-white/10 bg-white/[0.03] p-5">
                <module.icon className="h-5 w-5 text-sky-300" />
                <p className="mt-3 text-base font-semibold text-white">{module.title}</p>
                <p className="mt-2 text-sm leading-6 text-slate-300">{module.desc}</p>
              </div>
            ))}
          </div>
        </section>

        <section id="evidence" className="mt-14">
          <div className="mb-5 flex items-center gap-2">
            <Globe className="h-4 w-4 text-sky-300" />
            <h2 className="text-xl font-semibold text-white">{text.sectionEvidence}</h2>
          </div>
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            {snapshots.slice(0, 8).map((shot) => (
              <article key={shot.title} className="overflow-hidden rounded-xl border border-white/10 bg-[#0c111a]">
                <div className="aspect-[16/11] bg-[#02040a] p-2">
                  <ShotButton
                    shot={shot}
                    className="h-full w-full rounded-lg"
                    imageClassName={shotClass}
                    hint={text.previewHint}
                    onOpen={openPreview}
                  />
                </div>
                <div className="p-3">
                  <div className="mb-2 inline-flex rounded border border-sky-300/30 bg-sky-300/10 px-2 py-0.5 text-[11px] text-sky-200">
                    {shot.tag}
                  </div>
                  <p className="text-sm font-medium text-slate-100">{shot.title}</p>
                </div>
              </article>
            ))}
          </div>
        </section>

        <section id="architecture" className="mt-14 rounded-2xl border border-white/10 bg-[#090d14]/80 p-6">
          <div className="mb-4 flex items-center gap-2">
            <FileBarChart2 className="h-4 w-4 text-sky-300" />
            <h2 className="text-xl font-semibold text-white">{text.sectionWorkflow}</h2>
          </div>
          <div className="grid gap-3 md:grid-cols-4">
            {text.steps.map((step, idx) => (
              <div key={step} className="rounded-lg border border-white/10 bg-white/[0.03] p-4">
                <p className="text-xs text-slate-400">{lang === 'zh' ? `步骤 ${idx + 1}` : `STEP ${idx + 1}`}</p>
                <p className="mt-2 text-sm font-medium text-slate-100">{step}</p>
              </div>
            ))}
          </div>
        </section>
      </main>

      {previewModal}
    </div>
  )
}
