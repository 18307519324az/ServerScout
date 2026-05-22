import { useQuery } from '@tanstack/react-query'
import { fetchAttackSurface, fetchTechStack } from '../services/api'
import { Shield, Server, Globe, Bug, Activity, Layers, Loader2 } from 'lucide-react'
import ReactEChartsCore from 'echarts-for-react'

export default function AttackSurfacePage() {
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
          <h1 className="text-2xl font-bold dark:text-white">攻击面地图</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">
            Shannon 风格侦察层级可视化 — Subnet → Asset → Port → Tech → Vuln
          </p>
        </div>
      </div>

      {/* Attack Surface Tree */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5 mb-6">
        <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
          <Layers className="w-4 h-4 text-blue-600" />
          侦察层级图谱
        </h3>
        {mapLoading ? (
          <div className="flex items-center justify-center h-[500px] text-gray-400">
            <Loader2 className="w-6 h-6 animate-spin" />
          </div>
        ) : tree ? (
          <ReactEChartsCore option={buildTreeOption(tree, isDark)} style={{ height: 550 }} />
        ) : (
          <div className="flex items-center justify-center h-[500px] text-gray-400 dark:text-gray-500">
            暂无数据，完成资产扫描后将自动生成攻击面地图
          </div>
        )}
        <div className="mt-3 flex flex-wrap gap-3 text-xs text-gray-500 dark:text-gray-400">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-blue-500" /> 子网</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-green-500" /> 资产</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-yellow-500" /> Web端口</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-purple-500" /> 框架/CMS</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-red-500" /> 漏洞</span>
        </div>
      </div>

      {/* Technology Stack Distribution */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Tech Stack Summary Cards */}
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5">
          <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
            <Server className="w-4 h-4 text-green-600" />
            技术栈分布
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
            技术栈雷达
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
  const indicators: any[] = []
  const values: any[] = []

  const allItems = [
    ...(stats.frameworks || []),
    ...(stats.servers || []),
    ...(stats.cms || []),
    ...(stats.wafs || []),
  ]
  const top10 = allItems.sort((a: any, b: any) => b.count - a.count).slice(0, 8)

  for (const item of top10) {
    indicators.push({ name: item.name, max: item.count * 1.2 || 10 })
    values.push(item.count)
  }

  if (indicators.length === 0) return {}

  return {
    tooltip: {},
    legend: { data: ['检测数量'], textStyle: { color: isDark ? '#d1d5db' : '#374151' } },
    radar: {
      indicator: indicators,
      center: ['50%', '55%'],
      radius: '65%',
      axisName: { color: isDark ? '#d1d5db' : '#374151', fontSize: 10 },
      splitArea: { areaStyle: { color: [isDark ? '#1f2937' : '#fff', isDark ? '#374151' : '#f9fafb'] } },
    },
    series: [{
      type: 'radar',
      data: [{ value: values, name: '检测数量', areaStyle: { color: 'rgba(59, 130, 246, 0.3)' }, lineStyle: { color: '#3b82f6' } }],
    }],
  }
}
