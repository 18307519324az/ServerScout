import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Activity, AlertCircle, Layers, Loader2, Server } from 'lucide-react'
import ReactEChartsCore from 'echarts-for-react'
import { fetchAttackSurface, fetchTechStack } from '../services/api'
import echartsInstance from '../echarts'

type TechKey = 'servers' | 'frameworks' | 'cms' | 'wafs'
type AttackNodeType =
  | 'root'
  | 'subnet'
  | 'asset'
  | 'web-port'
  | 'port'
  | 'server'
  | 'service'
  | 'framework'
  | 'cms'
  | 'waf'
  | 'vulnerability'
  | 'asset-summary'
  | 'port-summary'
  | 'tech-summary'
  | 'vuln-summary'

interface TreeNodeMeta {
  count?: number
  sample?: string
  category?: string
}

interface TreeNode {
  name: string
  type?: AttackNodeType
  id?: number
  severity?: string
  meta?: TreeNodeMeta
  children?: TreeNode[]
  collapsed?: boolean
}

interface TechItem {
  name: string
  count: number
}

interface TechStackStats {
  servers: TechItem[]
  frameworks: TechItem[]
  cms: TechItem[]
  wafs: TechItem[]
}

interface RadarCategory {
  key: TechKey
  label: string
  color: string
  totalCount: number
  uniqueCount: number
  totalScore: number
  uniqueScore: number
  topItems: TechItem[]
}

interface RadarModel {
  categories: RadarCategory[]
  hasData: boolean
}

interface RadarTexts {
  categoryServer: string
  categoryFramework: string
  categoryCms: string
  categoryWaf: string
  seriesTotal: string
  seriesUnique: string
  noteNormalized: string
}

interface AttackSurfaceTexts {
  labelRoot: string
  labelSubnet: string
  labelAsset: string
  labelWebPort: string
  labelPort: string
  labelServer: string
  labelService: string
  labelFramework: string
  labelCms: string
  labelWaf: string
  labelVulnerability: string
  labelSummaryAssets: string
  labelSummaryWebPorts: string
  labelSummaryPorts: string
  labelSummaryTech: string
  labelSummaryVulns: string
  tooltipChildren: string
  tooltipSample: string
  tooltipCount: string
  densityHint: string
  visibleNodes: string
  totalNodes: string
}

const EMPTY_TECH_STATS: TechStackStats = {
  servers: [],
  frameworks: [],
  cms: [],
  wafs: [],
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function toFiniteNumber(value: unknown, fallback = 0): number {
  const n = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(n) ? n : fallback
}

function normalizeTechItems(input: unknown): TechItem[] {
  if (!Array.isArray(input)) return []
  return input
    .map((raw) => {
      if (!isRecord(raw)) return null
      const name = typeof raw.name === 'string' ? raw.name.trim() : ''
      if (!name) return null
      return { name, count: Math.max(0, Math.floor(toFiniteNumber(raw.count, 0))) }
    })
    .filter((item): item is TechItem => !!item)
}

function normalizeTechStack(raw: unknown): TechStackStats {
  if (!isRecord(raw)) return EMPTY_TECH_STATS
  return {
    servers: normalizeTechItems(raw.servers),
    frameworks: normalizeTechItems(raw.frameworks),
    cms: normalizeTechItems(raw.cms),
    wafs: normalizeTechItems(raw.wafs),
  }
}

function normalizeTree(raw: unknown): TreeNode | null {
  if (!isRecord(raw)) return null
  const name = typeof raw.name === 'string' ? raw.name : 'Attack Surface'
  const type = typeof raw.type === 'string' ? raw.type as AttackNodeType : undefined
  const id = typeof raw.id === 'number' ? raw.id : undefined
  const severity = typeof raw.severity === 'string' ? raw.severity : undefined
  const meta = isRecord(raw.meta)
    ? {
        count: typeof raw.meta.count === 'number' ? raw.meta.count : undefined,
        sample: typeof raw.meta.sample === 'string' ? raw.meta.sample : undefined,
        category: typeof raw.meta.category === 'string' ? raw.meta.category : undefined,
      }
    : undefined
  const children = Array.isArray(raw.children)
    ? raw.children.map((child) => normalizeTree(child)).filter((item): item is TreeNode => !!item)
    : undefined
  return { name, type, id, severity, meta, children }
}

function countTreeNodes(node: TreeNode | null): number {
  if (!node) return 0
  return 1 + (node.children || []).reduce((sum, child) => sum + countTreeNodes(child), 0)
}

function summarizeLabel(node: TreeNode, text: AttackSurfaceTexts): string {
  const count = node.meta?.count ?? (Number(node.name) || 0)
  if (node.type === 'asset-summary') return text.labelSummaryAssets.replace('{count}', String(count))
  if (node.type === 'tech-summary') return text.labelSummaryTech.replace('{count}', String(count))
  if (node.type === 'vuln-summary') return text.labelSummaryVulns.replace('{count}', String(count))
  if (node.type === 'port-summary' && node.meta?.category === 'web') {
    return text.labelSummaryWebPorts.replace('{count}', String(count))
  }
  return text.labelSummaryPorts.replace('{count}', String(count))
}

function formatNodeLabel(node: TreeNode, text: AttackSurfaceTexts): string {
  switch (node.type) {
    case 'root':
      return text.labelRoot
    case 'subnet':
      return `${text.labelSubnet} ${node.name}`
    case 'asset':
      return `${text.labelAsset} ${node.name}`
    case 'web-port':
      return `${text.labelWebPort} ${node.name}`
    case 'port':
      return `${text.labelPort} ${node.name}`
    case 'server':
      return `${text.labelServer} ${node.name}`
    case 'service':
      return `${text.labelService} ${node.name}`
    case 'framework':
      return `${text.labelFramework} ${node.name}`
    case 'cms':
      return `${text.labelCms} ${node.name}`
    case 'waf':
      return `${text.labelWaf} ${node.name}`
    case 'vulnerability':
      return `${text.labelVulnerability} ${node.name}`
    case 'asset-summary':
    case 'port-summary':
    case 'tech-summary':
    case 'vuln-summary':
      return summarizeLabel(node, text)
    default:
      return node.name
  }
}

function decorateTree(node: TreeNode, text: AttackSurfaceTexts, totalNodes: number, depth = 0): TreeNode {
  const children = (node.children || []).map((child) => decorateTree(child, text, totalNodes, depth + 1))
  const shouldCollapse =
    totalNodes > 220
      ? depth >= 1 && children.length > 0
      : totalNodes > 120
        ? depth >= 2 && children.length > 0
        : depth >= 3 && children.length > 8

  return {
    ...node,
    name: formatNodeLabel(node, text),
    collapsed: shouldCollapse || node.type === 'asset-summary' || node.type === 'port-summary' || node.type === 'tech-summary' || node.type === 'vuln-summary',
    children,
  }
}

function applyNodeColors(node: TreeNode): Record<string, unknown> {
  const colorMap: Record<string, string> = {
    root: '#0f172a',
    subnet: '#2563eb',
    asset: '#16a34a',
    'web-port': '#f59e0b',
    port: '#94a3b8',
    server: '#06b6d4',
    service: '#6366f1',
    framework: '#8b5cf6',
    cms: '#a855f7',
    waf: '#f97316',
    vulnerability: '#ef4444',
    'asset-summary': '#475569',
    'port-summary': '#64748b',
    'tech-summary': '#64748b',
    'vuln-summary': '#7f1d1d',
  }
  const color = colorMap[node.type || ''] || '#64748b'

  return {
    ...node,
    itemStyle: { color },
    children: node.children?.map(applyNodeColors),
  }
}

function buildTreeOption(
  tree: TreeNode,
  isDark: boolean,
  texts: AttackSurfaceTexts,
  totalNodes: number
): Record<string, unknown> {
  const textColor = isDark ? '#d1d5db' : '#374151'
  const mutedTextColor = isDark ? '#9ca3af' : '#6b7280'
  const lineColor = isDark ? '#334155' : '#cbd5e1'
  const borderColor = isDark ? '#0f172a' : '#ffffff'
  const dynamicDepth = totalNodes > 220 ? 2 : totalNodes > 120 ? 3 : 4
  const dynamicSymbol = totalNodes > 220 ? 7 : totalNodes > 120 ? 8 : 10
  const fontSize = totalNodes > 220 ? 10 : 11

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      triggerOn: 'mousemove',
      backgroundColor: isDark ? 'rgba(15,23,42,0.96)' : 'rgba(255,255,255,0.98)',
      borderColor: isDark ? '#334155' : '#e5e7eb',
      textStyle: { color: textColor },
      formatter: (params: any) => {
        const node = params?.data as TreeNode | undefined
        if (!node) return ''
        const lines = [
          `<div style="font-weight:600;margin-bottom:4px;">${node.name}</div>`,
        ]
        if (node.meta?.count) {
          lines.push(`<div>${texts.tooltipCount}: ${node.meta.count}</div>`)
        }
        if (node.meta?.sample) {
          lines.push(`<div style="max-width:420px;white-space:normal;word-break:break-all;">${texts.tooltipSample}: ${node.meta.sample}</div>`)
        }
        if (Array.isArray(node.children) && node.children.length > 0) {
          lines.push(`<div style="color:${mutedTextColor};margin-top:4px;">${texts.tooltipChildren}: ${node.children.length}</div>`)
        }
        return lines.join('')
      },
    },
    series: [{
      type: 'tree',
      data: [applyNodeColors(tree)],
      top: '3%',
      left: '8%',
      bottom: '4%',
      right: totalNodes > 180 ? '30%' : '24%',
      symbol: 'circle',
      symbolSize: dynamicSymbol,
      roam: true,
      initialTreeDepth: dynamicDepth,
      expandAndCollapse: true,
      animationDuration: totalNodes > 220 ? 180 : 500,
      animationDurationUpdate: totalNodes > 220 ? 220 : 700,
      lineStyle: {
        color: lineColor,
        width: totalNodes > 180 ? 0.9 : 1.2,
      },
      itemStyle: {
        borderColor,
        borderWidth: 1.25,
      },
      label: {
        position: 'left',
        align: 'right',
        verticalAlign: 'middle',
        color: textColor,
        fontSize,
        distance: 5,
        width: totalNodes > 180 ? 150 : 180,
        overflow: 'truncate',
      },
      leaves: {
        label: {
          position: 'right',
          align: 'left',
          verticalAlign: 'middle',
          color: textColor,
          fontSize,
          width: totalNodes > 180 ? 150 : 180,
          overflow: 'truncate',
        },
      },
      emphasis: { focus: 'descendant' },
    }],
  }
}

function buildRadarModel(stats: TechStackStats, texts: RadarTexts): RadarModel {
  const definitions: Array<{ key: TechKey; label: string; color: string }> = [
    { key: 'servers', label: texts.categoryServer, color: '#3b82f6' },
    { key: 'frameworks', label: texts.categoryFramework, color: '#22c55e' },
    { key: 'cms', label: texts.categoryCms, color: '#f59e0b' },
    { key: 'wafs', label: texts.categoryWaf, color: '#ef4444' },
  ]

  const base = definitions.map((def) => {
    const items = stats[def.key]
    const totalCount = items.reduce((sum, item) => sum + item.count, 0)
    const uniqueCount = items.length
    return {
      ...def,
      totalCount,
      uniqueCount,
      topItems: items.slice(0, 4),
      totalScore: 0,
      uniqueScore: 0,
    }
  })

  const maxTotal = Math.max(...base.map((c) => c.totalCount), 0)
  const maxUnique = Math.max(...base.map((c) => c.uniqueCount), 0)
  const hasData = maxTotal > 0 || maxUnique > 0

  const categories = base.map((cat) => ({
    ...cat,
    totalScore: maxTotal > 0 ? Math.round((cat.totalCount / maxTotal) * 100) : 0,
    uniqueScore: maxUnique > 0 ? Math.round((cat.uniqueCount / maxUnique) * 100) : 0,
  }))

  return { categories, hasData }
}

function buildRadarOption(model: RadarModel, isDark: boolean, texts: RadarTexts): Record<string, unknown> {
  const textColor = isDark ? '#d1d5db' : '#374151'
  const mutedTextColor = isDark ? '#9ca3af' : '#6b7280'
  const splitLineColor = isDark ? '#374151' : '#e5e7eb'
  const splitAreaColors = isDark
    ? ['rgba(17,24,39,0.65)', 'rgba(31,41,55,0.45)']
    : ['rgba(248,250,252,0.95)', 'rgba(241,245,249,0.85)']

  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      backgroundColor: isDark ? 'rgba(17,24,39,0.95)' : 'rgba(255,255,255,0.98)',
      borderColor: isDark ? '#374151' : '#e5e7eb',
      textStyle: { color: textColor },
      formatter: (params: any) => {
        const seriesName = params?.seriesName as string | undefined
        const isTotalSeries = seriesName === texts.seriesTotal
        const header = isTotalSeries ? texts.seriesTotal : texts.seriesUnique
        const rows = model.categories
          .map((cat) => {
            const rawValue = isTotalSeries ? cat.totalCount : cat.uniqueCount
            const normalized = isTotalSeries ? cat.totalScore : cat.uniqueScore
            return `<div style="display:flex;justify-content:space-between;gap:16px;"><span>${cat.label}</span><span>${rawValue} (${normalized}%)</span></div>`
          })
          .join('')
        return `<div style="min-width:220px;"><div style="font-weight:600;margin-bottom:6px;">${header}</div>${rows}</div>`
      },
    },
    legend: {
      top: 0,
      data: [texts.seriesTotal, texts.seriesUnique],
      textStyle: { color: textColor },
    },
    radar: {
      center: ['50%', '56%'],
      radius: '62%',
      indicator: model.categories.map((cat) => ({ name: cat.label, max: 100 })),
      axisName: { color: textColor, fontSize: 12 },
      axisLine: { lineStyle: { color: splitLineColor } },
      splitLine: { lineStyle: { color: splitLineColor } },
      splitArea: { areaStyle: { color: splitAreaColors } },
      nameGap: 8,
    },
    series: [
      {
        type: 'radar',
        name: texts.seriesTotal,
        data: [{
          name: texts.seriesTotal,
          value: model.categories.map((cat) => cat.totalScore),
          lineStyle: { color: '#3b82f6', width: 2.5 },
          itemStyle: { color: '#3b82f6' },
          areaStyle: { color: 'rgba(59,130,246,0.18)' },
          symbol: 'circle',
          symbolSize: 6,
        }],
      },
      {
        type: 'radar',
        name: texts.seriesUnique,
        data: [{
          name: texts.seriesUnique,
          value: model.categories.map((cat) => cat.uniqueScore),
          lineStyle: { color: '#22c55e', width: 2.5 },
          itemStyle: { color: '#22c55e' },
          areaStyle: { color: 'rgba(34,197,94,0.14)' },
          symbol: 'diamond',
          symbolSize: 6,
        }],
      },
    ],
    graphic: [{
      type: 'text',
      left: 'center',
      top: '92%',
      style: {
        text: texts.noteNormalized,
        fill: mutedTextColor,
        fontSize: 11,
      },
    }],
  }
}

function TechSection({
  title,
  items,
  barClassName,
  typeCountLabel,
}: {
  title: string
  items: TechItem[]
  barClassName: string
  typeCountLabel: string
}) {
  const maxCount = Math.max(...items.map((item) => item.count), 1)

  return (
    <div>
      <div className="mb-2 flex items-center justify-between">
        <p className="text-xs font-semibold uppercase tracking-wide text-gray-500 dark:text-gray-400">{title}</p>
        <p className="text-xs text-gray-400 dark:text-gray-500">{items.length} {typeCountLabel}</p>
      </div>
      <div className="space-y-1.5">
        {items.slice(0, 8).map((item) => (
          <div key={`${title}-${item.name}`} className="flex items-center gap-2 text-xs">
            <span className="w-28 truncate text-gray-700 dark:text-gray-300" title={item.name}>{item.name}</span>
            <div className="h-4 flex-1 overflow-hidden rounded-full bg-gray-100 dark:bg-gray-700">
              <div
                className={`h-full rounded-full ${barClassName}`}
                style={{ width: `${Math.max(5, (item.count / maxCount) * 100)}%` }}
              />
            </div>
            <span className="w-9 text-right font-mono text-gray-500 dark:text-gray-400">{item.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

export default function AttackSurfacePage() {
  const { t, i18n } = useTranslation()
  const [isDark, setIsDark] = useState(false)

  useEffect(() => {
    const checkTheme = () => setIsDark(document.documentElement.classList.contains('dark'))
    checkTheme()
    const observer = new MutationObserver(checkTheme)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => observer.disconnect()
  }, [])

  const {
    data: mapRes,
    isLoading: mapLoading,
    isError: mapIsError,
    error: mapError,
  } = useQuery({
    queryKey: ['attack-surface'],
    queryFn: () => fetchAttackSurface(),
    refetchInterval: 60000,
    retry: 1,
  })

  const {
    data: techRes,
    isLoading: techLoading,
    isError: techIsError,
    error: techError,
  } = useQuery({
    queryKey: ['tech-stack'],
    queryFn: () => fetchTechStack(),
    refetchInterval: 60000,
    retry: 1,
  })

  const tree = useMemo(() => normalizeTree(mapRes?.data?.data), [mapRes?.data?.data])
  const techStats = useMemo(() => normalizeTechStack(techRes?.data?.data), [techRes?.data?.data])

  const radarTexts = useMemo<RadarTexts>(() => ({
    categoryServer: t('attackSurface.radar.categoryServer'),
    categoryFramework: t('attackSurface.radar.categoryFramework'),
    categoryCms: t('attackSurface.radar.categoryCms'),
    categoryWaf: t('attackSurface.radar.categoryWaf'),
    seriesTotal: t('attackSurface.radar.seriesTotal'),
    seriesUnique: t('attackSurface.radar.seriesUnique'),
    noteNormalized: t('attackSurface.radar.noteNormalized'),
  }), [t, i18n.resolvedLanguage])

  const treeTexts = useMemo<AttackSurfaceTexts>(() => ({
    labelRoot: t('attackSurface.node.root'),
    labelSubnet: t('attackSurface.node.subnet'),
    labelAsset: t('attackSurface.node.asset'),
    labelWebPort: t('attackSurface.node.webPort'),
    labelPort: t('attackSurface.node.port'),
    labelServer: t('attackSurface.node.server'),
    labelService: t('attackSurface.node.service'),
    labelFramework: t('attackSurface.node.framework'),
    labelCms: t('attackSurface.node.cms'),
    labelWaf: t('attackSurface.node.waf'),
    labelVulnerability: t('attackSurface.node.vulnerability'),
    labelSummaryAssets: t('attackSurface.node.summaryAssets'),
    labelSummaryWebPorts: t('attackSurface.node.summaryWebPorts'),
    labelSummaryPorts: t('attackSurface.node.summaryPorts'),
    labelSummaryTech: t('attackSurface.node.summaryTech'),
    labelSummaryVulns: t('attackSurface.node.summaryVulns'),
    tooltipChildren: t('attackSurface.node.tooltipChildren'),
    tooltipSample: t('attackSurface.node.tooltipSample'),
    tooltipCount: t('attackSurface.node.tooltipCount'),
    densityHint: t('attackSurface.node.densityHint'),
    visibleNodes: t('attackSurface.node.visibleNodes'),
    totalNodes: t('attackSurface.node.totalNodes'),
  }), [t, i18n.resolvedLanguage])

  const rawTreeNodeCount = useMemo(() => countTreeNodes(tree), [tree])
  const decoratedTree = useMemo(() => (
    tree ? decorateTree(tree, treeTexts, rawTreeNodeCount) : null
  ), [tree, treeTexts, rawTreeNodeCount])
  const visibleTreeNodeCount = useMemo(() => countTreeNodes(decoratedTree), [decoratedTree])
  const treeOption = useMemo(() => (
    decoratedTree ? buildTreeOption(decoratedTree, isDark, treeTexts, rawTreeNodeCount) : null
  ), [decoratedTree, isDark, treeTexts, rawTreeNodeCount])

  const radarModel = useMemo(() => buildRadarModel(techStats, radarTexts), [techStats, radarTexts])
  const radarOption = useMemo(
    () => (radarModel.hasData ? buildRadarOption(radarModel, isDark, radarTexts) : null),
    [radarModel, isDark, radarTexts]
  )
  const techTotal = useMemo(
    () => radarModel.categories.reduce((sum, category) => sum + category.totalCount, 0),
    [radarModel.categories]
  )

  return (
    <div>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold dark:text-white">{t('attackSurface.title')}</h1>
          <p className="mt-0.5 text-sm text-gray-500 dark:text-gray-400">{t('attackSurface.subtitle')}</p>
        </div>
      </div>

      {(mapIsError || techIsError) && (
        <div className="mb-4 flex items-center gap-2 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-800 dark:bg-red-900/20 dark:text-red-400">
          <AlertCircle className="h-4 w-4 flex-shrink-0" />
          <span>
            {mapIsError ? `Attack surface load failed: ${(mapError as Error)?.message || 'unknown error'}` : ''}
            {mapIsError && techIsError ? ' | ' : ''}
            {techIsError ? `Tech stack load failed: ${(techError as Error)?.message || 'unknown error'}` : ''}
          </span>
        </div>
      )}

      <div className="mb-6 rounded-xl border bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
        <div className="mb-3 flex items-center justify-between gap-3">
          <h3 className="flex items-center gap-2 font-semibold dark:text-white">
            <Layers className="h-4 w-4 text-blue-600" />
            {t('attackSurface.subtitle')}
          </h3>
          {decoratedTree && (
            <div className="flex flex-wrap items-center gap-2 text-xs text-gray-500 dark:text-gray-400">
              <span className="rounded-full border border-gray-200 px-2 py-1 dark:border-gray-700">
                {treeTexts.visibleNodes}: {visibleTreeNodeCount}
              </span>
              <span className="rounded-full border border-gray-200 px-2 py-1 dark:border-gray-700">
                {treeTexts.totalNodes}: {rawTreeNodeCount}
              </span>
              {rawTreeNodeCount > 120 && (
                <span className="rounded-full border border-amber-200 bg-amber-50 px-2 py-1 text-amber-700 dark:border-amber-800 dark:bg-amber-900/20 dark:text-amber-300">
                  {treeTexts.densityHint}
                </span>
              )}
            </div>
          )}
        </div>

        {mapLoading ? (
          <div className="flex h-[560px] items-center justify-center text-gray-400 dark:text-gray-500">
            <Loader2 className="h-6 w-6 animate-spin" />
          </div>
        ) : treeOption ? (
          <ReactEChartsCore
            key={`attack-tree-${isDark ? 'dark' : 'light'}-${rawTreeNodeCount}`}
            echarts={echartsInstance}
            option={treeOption}
            style={{ height: 560 }}
            notMerge
            lazyUpdate={false}
          />
        ) : (
          <div className="flex h-[560px] items-center justify-center text-gray-400 dark:text-gray-500">
            {t('attackSurface.noData')}
          </div>
        )}

        <div className="mt-3 flex flex-wrap gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-blue-500" /> {t('attackSurface.legendSubnet')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-green-500" /> {t('attackSurface.legendAsset')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-yellow-500" /> {t('attackSurface.legendWebPort')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-slate-400" /> {t('attackSurface.legendPort')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-cyan-500" /> {t('attackSurface.node.server')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-indigo-500" /> {t('attackSurface.legendService')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-purple-500" /> {t('attackSurface.legendFramework')}</span>
          <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-full bg-red-500" /> {t('attackSurface.legendVuln')}</span>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-2">
        <div className="rounded-xl border bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <h3 className="mb-4 flex items-center gap-2 font-semibold dark:text-white">
            <Server className="h-4 w-4 text-green-600" />
            {t('attackSurface.techStack')}
          </h3>
          {techLoading ? (
            <div className="flex h-[430px] items-center justify-center text-gray-400 dark:text-gray-500">
              <Loader2 className="h-5 w-5 animate-spin" />
            </div>
          ) : radarModel.hasData ? (
            <div className="space-y-4">
              {techStats.servers.length > 0 && (
                <TechSection
                  title={t('attackSurface.radar.categoryServer')}
                  items={techStats.servers}
                  barClassName="bg-blue-500/80 dark:bg-blue-400/70"
                  typeCountLabel={t('attackSurface.radar.typesUnit')}
                />
              )}
              {techStats.frameworks.length > 0 && (
                <TechSection
                  title={t('attackSurface.radar.categoryFramework')}
                  items={techStats.frameworks}
                  barClassName="bg-green-500/80 dark:bg-green-400/70"
                  typeCountLabel={t('attackSurface.radar.typesUnit')}
                />
              )}
              {techStats.cms.length > 0 && (
                <TechSection
                  title={t('attackSurface.radar.categoryCms')}
                  items={techStats.cms}
                  barClassName="bg-amber-500/80 dark:bg-amber-400/70"
                  typeCountLabel={t('attackSurface.radar.typesUnit')}
                />
              )}
              {techStats.wafs.length > 0 && (
                <TechSection
                  title={t('attackSurface.radar.categoryWaf')}
                  items={techStats.wafs}
                  barClassName="bg-red-500/80 dark:bg-red-400/70"
                  typeCountLabel={t('attackSurface.radar.typesUnit')}
                />
              )}
            </div>
          ) : (
            <div className="flex h-[430px] flex-col items-center justify-center text-center text-gray-400 dark:text-gray-500">
              <p className="mb-1">{t('attackSurface.noData')}</p>
              <p className="text-xs">{t('attackSurface.radar.techHint')}</p>
            </div>
          )}
        </div>

        <div className="rounded-xl border bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <h3 className="mb-3 flex items-center gap-2 font-semibold dark:text-white">
            <Activity className="h-4 w-4 text-purple-600" />
            {t('attackSurface.techRadar')}
          </h3>

          <div className="mb-3 grid grid-cols-2 gap-2 text-xs">
            {radarModel.categories.map((cat) => (
              <div
                key={cat.key}
                className="rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-gray-600 dark:border-gray-700 dark:bg-gray-900/50 dark:text-gray-300"
              >
                <div className="mb-0.5 font-semibold" style={{ color: cat.color }}>{cat.label}</div>
                <div>{t('attackSurface.radar.metricTotal')}: {cat.totalCount}</div>
                <div>{t('attackSurface.radar.metricUnique')}: {cat.uniqueCount}</div>
              </div>
            ))}
          </div>

          {radarOption ? (
            <ReactEChartsCore
              key={`attack-radar-${isDark ? 'dark' : 'light'}`}
              echarts={echartsInstance}
              option={radarOption}
              style={{ height: 360 }}
              notMerge
              lazyUpdate={false}
            />
          ) : (
            <div className="flex h-[360px] items-center justify-center text-gray-400 dark:text-gray-500">
              {techLoading ? <Loader2 className="h-5 w-5 animate-spin" /> : t('attackSurface.noData')}
            </div>
          )}

          <div className="mt-3 flex items-center justify-between rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600 dark:border-gray-700 dark:bg-gray-900/50 dark:text-gray-300">
            <span>{t('attackSurface.radar.totalFingerprintInstances')}</span>
            <span className="font-mono font-semibold">{techTotal}</span>
          </div>
        </div>
      </div>
    </div>
  )
}
