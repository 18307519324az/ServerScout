import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchAttackSurface, fetchTechStack } from '../services/api'
import { Shield, Server, Globe, Bug, Activity, Layers, Loader2 } from 'lucide-react'
import ReactEChartsCore from 'echarts-for-react'

export default function AttackSurfacePage() {
  const { t } = useTranslation()
  const { data: mapData, isLoading: mapLoading } = useQuery({
    queryKey: ['attack-surface'],
    queryFn: () => fetchAttackSurface(),
    refetchInterval: 60000,
  })

  const { data: techData, isLoading: techLoading } = useQuery({
    queryKey: ['tech-stack'],
    queryFn: () => fetchTechStack(),
    refetchInterval: 60000,
  })

  const tree = mapData?.data?.data
  const techStats = techData?.data?.data
  const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold dark:text-white">{t('attackSurface.title')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
            Shannon 风格侦察层级可视化 — Subnet → Asset → Port → Tech → Vuln
          </p>
        </div>
      </div>

      {/* Attack Surface Tree */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5 mb-6">
        <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
          <Layers className="w-4 h-4 text-blue-600" />
          {t('attackSurface.subtitle')}
        </h3>
        {mapLoading ? (
          <div className="flex items-center justify-center h-[500px] text-gray-400">
            <Loader2 className="w-6 h-6 animate-spin" />
          </div>
        ) : tree ? (
          <ReactEChartsCore option={buildTreeOption(tree, isDark)} style={{ height: 550 }} />
        ) : (
          <div className="flex items-center justify-center h-[500px] text-gray-400 dark:text-gray-500">
            {t('attackSurface.noData')}
          </div>
        )}
        <div className="mt-3 flex flex-wrap gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-blue-500" /> {t('attackSurface.legendSubnet')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-green-500" /> {t('attackSurface.legendAsset')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-yellow-500" /> {t('attackSurface.legendWebPort')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-purple-500" /> {t('attackSurface.legendFramework')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-red-500" /> {t('attackSurface.legendVuln')}</span>
        </div>
      </div>

      {/* Technology Stack Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Tech Stack Summary Cards */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5">
          <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
            <Server className="w-4 h-4 text-green-600" />
            {t('attackSurface.techStack')}
          </h3>
          {techLoading ? (
            <div className="flex items-center justify-center h-60 text-gray-400">
              <Loader2 className="w-5 h-5 animate-spin" />
            </div>
          ) : techStats ? (
            <div className="space-y-4">
              {techStats.frameworks?.length > 0 && (
                <TechSection title="框架 (Frameworks)" items={techStats.frameworks} color="bg-purple-100 dark:bg-purple-900/30" />
              )}
              {techStats.servers?.length > 0 && (
                <TechSection title="Web 服务器" items={techStats.servers} color="bg-blue-100 dark:bg-blue-900/30" />
              )}
              {techStats.cms?.length > 0 && (
                <TechSection title="CMS" items={techStats.cms} color="bg-green-100 dark:bg-green-900/30" />
              )}
              {techStats.wafs?.length > 0 && (
                <TechSection title="WAF" items={techStats.wafs} color="bg-orange-100 dark:bg-orange-900/30" />
              )}
              {!techStats.frameworks?.length && !techStats.servers?.length && !techStats.cms?.length && !techStats.wafs?.length && (
                <div className="text-center py-8 text-gray-400 dark:text-gray-500">
                  暂无指纹数据，执行带指纹识别的扫描后自动填充
                </div>
              )}
            </div>
          ) : null}
        </div>

        {/* Tech Radar Chart */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5">
          <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
            <Activity className="w-4 h-4 text-purple-600" />
            {t('attackSurface.techRadar')}
          </h3>
          {techStats ? (
            <ReactEChartsCore option={buildRadarOption(techStats, isDark)} style={{ height: 380 }} />
          ) : (
            <div className="flex items-center justify-center h-[380px] text-gray-400 dark:text-gray-500">
              {techLoading ? <Loader2 className="w-5 h-5 animate-spin" /> : '暂无数据'}
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function TechSection({ title, items, color }: { title: string; items: any[]; color: string }) {
  const maxCount = Math.max(...items.map((i: any) => i.count), 1)
  return (
    <div>
      <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">{title}</p>
      <div className="space-y-1.5">
        {items.slice(0, 8).map((item: any) => (
          <div key={item.name} className="flex items-center gap-2 text-xs">
            <span className="w-24 truncate text-gray-600 dark:text-gray-300">{item.name}</span>
            <div className="flex-1 h-4 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden">
              <div
                className={`h-full rounded-full ${color} transition-all`}
                style={{ width: `${(item.count / maxCount) * 100}%` }}
              />
            </div>
            <span className="w-8 text-right font-mono text-gray-400">{item.count}</span>
          </div>
        ))}
      </div>
    </div>
  )
}

function buildTreeOption(data: any, isDark: boolean) {
  // Recursively assign colors based on node type
  const coloredData = applyNodeColors(data)
  return {
    tooltip: { trigger: 'item', triggerOn: 'mousemove' },
    series: [{
      type: 'tree',
      data: [coloredData],
      top: '5%',
      left: '8%',
      bottom: '5%',
      right: '20%',
      symbolSize: 10,
      label: {
        position: 'left',
        verticalAlign: 'middle',
        align: 'right',
        fontSize: 11,
        color: isDark ? '#d1d5db' : '#374151',
      },
      leaves: { label: { position: 'right', verticalAlign: 'middle', align: 'left' } },
      initialTreeDepth: 3,
      expandAndCollapse: true,
      animationDuration: 550,
      animationDurationUpdate: 750,
      lineStyle: {
        color: isDark ? '#4b5563' : '#d1d5db',
      },
    }],
  }
}

/** Recursively assign itemStyle colors to tree nodes based on type */
function applyNodeColors(node: any): any {
  if (!node) return node
  const colors: Record<string, string> = {
    subnet: '#3b82f6',
    asset: '#22c55e',
    'web-port': '#eab308',
    port: '#9ca3af',
    service: '#a78bfa',
    framework: '#a855f7',
    cms: '#a855f7',
    waf: '#f97316',
    vulnerability: '#ef4444',
  }
  const color = colors[node.type] || '#6b7280'
  return {
    ...node,
    itemStyle: { color },
    children: node.children ? node.children.map(applyNodeColors) : undefined,
  }
}

function buildRadarOption(stats: any, isDark: boolean) {
  const categories = [
    { key: 'servers', label: '服务器', color: '#3b82f6' },
    { key: 'frameworks', label: '框架', color: '#22c55e' },
    { key: 'cms', label: 'CMS', color: '#f97316' },
    { key: 'wafs', label: 'WAF', color: '#ef4444' },
  ]

  // Compute total count and unique items per category
  const catData = categories.map(cat => {
    const items = stats[cat.key] || []
    const totalCount = items.reduce((sum: number, i: any) => sum + i.count, 0)
    return {
      ...cat,
      totalCount,
      uniqueItems: items.length,
      topItems: items.slice(0, 5),
    }
  })

  const hasData = catData.some(c => c.totalCount > 0)
  if (!hasData) return {}

  const globalMax = Math.max(...catData.map(c => c.totalCount), 1)

  const textColor = isDark ? '#d1d5db' : '#374151'
  const bgColor = isDark ? '#1f2937' : '#fff'

  return {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const cat = catData.find(c => c.label === params.name)
        if (!cat) return ''
        const topNames = cat.topItems.map((i: any) => `${i.name}(${i.count})`).join(', ')
        return `<b>${cat.label}</b><br/>检测总量: <b>${cat.totalCount}</b><br/>种类数: <b>${cat.uniqueItems}</b><br/>Top: ${topNames || '无'}`
      },
    },
    legend: {
      data: ['检测总量', '种类数'],
      textStyle: { color: textColor },
      top: 0,
    },
    radar: {
      indicator: catData.map(c => ({ name: c.label, max: globalMax * 1.2 })),
      center: ['50%', '58%'],
      radius: '60%',
      axisName: {
        color: textColor,
        fontSize: 12,
      },
      splitArea: {
        areaStyle: { color: [bgColor, isDark ? '#374151' : '#f3f4f6'] },
      },
    },
    series: [
      {
        type: 'radar',
        name: '检测总量',
        data: [{
          value: catData.map(c => c.totalCount),
          name: '检测总量',
          areaStyle: { color: '#3b82f620' },
          lineStyle: { color: '#3b82f6', width: 2 },
          itemStyle: { color: '#3b82f6' },
          symbol: 'circle',
          symbolSize: 6,
        }],
      },
      {
        type: 'radar',
        name: '种类数',
        data: [{
          value: catData.map(c => c.uniqueItems),
          name: '种类数',
          areaStyle: { color: '#22c55e20' },
          lineStyle: { color: '#22c55e', width: 2 },
          itemStyle: { color: '#22c55e' },
          symbol: 'diamond',
          symbolSize: 6,
        }],
      },
    ],
  }
}
