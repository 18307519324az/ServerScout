import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchDashboardStats } from '../services/api'
import { CardSkeleton, ChartSkeleton } from '../components/Skeleton'
import PluginSlot from '../components/PluginSlot'
import { Server, ScanLine, Bug, Activity, Shield, AlertTriangle, ChevronRight, Globe, Network, FileText } from 'lucide-react'
import ReactEChartsCore from 'echarts-for-react'

export default function DashboardPage() {
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => fetchDashboardStats(),
    refetchInterval: 30000,
    retry: 1,
  })

  const stats = data?.data?.data

  if (isLoading) return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">攻击面仪表盘</h1>
          <p className="text-sm text-gray-500 mt-0.5">资产安全态势一览</p>
        </div>
      </div>
      <div className="grid grid-cols-4 gap-4 mb-6">
        {Array.from({ length: 4 }).map((_, i) => <CardSkeleton key={i} />)}
      </div>
      <div className="grid grid-cols-2 gap-6">
        <ChartSkeleton />
        <ChartSkeleton />
      </div>
    </div>
  )

  if (isError || !stats) return (
    <div className="text-center py-20">
      <div className="text-red-500 text-lg mb-2">数据加载失败</div>
      <p className="text-gray-400 dark:text-gray-500 text-sm mb-4">
        {error instanceof Error ? error.message : '请确认后端服务是否已启动'}
      </p>
      <button
        onClick={() => refetch()}
        className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm"
      >
        重新加载
      </button>
    </div>
  )

  const riskScore = (stats.overview.criticalVulns || 0) * 10
    + (stats.overview.highVulns || 0) * 5
    + (stats.overview.mediumVulns || 0) * 2
    + (stats.overview.lowVulns || 0) * 1

  const riskLevel = riskScore >= 100 ? { label: '严重', color: 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400' }
    : riskScore >= 50 ? { label: '高', color: 'border-orange-200 dark:border-orange-800 bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400' }
    : riskScore >= 20 ? { label: '中', color: 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-400' }
    : { label: '低', color: 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400' }

  const cards = [
    { label: '总资产', value: stats.overview.totalAssets, icon: Server, color: 'text-blue-600 dark:text-blue-400' },
    { label: '总端口', value: stats.overview.totalPorts, icon: Activity, color: 'text-green-600 dark:text-green-400' },
    { label: '总漏洞', value: stats.overview.totalVulnerabilities, icon: Bug, color: 'text-red-600 dark:text-red-400' },
    { label: '活跃任务', value: stats.overview.activeTasks, icon: ScanLine, color: 'text-purple-600 dark:text-purple-400' },
  ]

  const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold dark:text-white">攻击面仪表盘</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">资产安全态势一览</p>
        </div>
        <Link to="/scan-tasks"
          className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium">
          <ScanLine className="w-4 h-4" /> 新建扫描
        </Link>
      </div>

      {/* Risk Score Banner */}
      <div className={`rounded-xl border p-5 mb-6 ${riskLevel.color}`}>
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full flex items-center justify-center bg-white/80 dark:bg-gray-800/80">
              <Shield className={`w-7 h-7 ${
                riskScore >= 50 ? 'text-red-500' : riskScore >= 20 ? 'text-yellow-500' : 'text-green-500'
              }`} />
            </div>
            <div>
              <p className="text-sm font-medium">安全风险评分</p>
              <p className="text-3xl font-bold">{riskScore}</p>
              <p className="text-xs mt-0.5">风险等级：<span className="font-bold">{riskLevel.label}</span></p>
            </div>
          </div>
          <div className="text-right text-sm space-y-1">
            {[{ color: 'bg-red-500', label: '严重', count: stats.overview.criticalVulns },
              { color: 'bg-orange-500', label: '高危', count: stats.overview.highVulns },
              { color: 'bg-yellow-500', label: '中危', count: stats.overview.mediumVulns },
              { color: 'bg-blue-500', label: '低危', count: stats.overview.lowVulns },
            ].map(s => (
              <div key={s.label} className="flex items-center gap-2 justify-end">
                <span className={`w-2.5 h-2.5 rounded-full ${s.color}`} />
                <span>{s.label}: {s.count}</span>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* Critical Alert Banner */}
      {stats.overview.criticalVulns > 0 && (
        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 text-red-700 dark:text-red-400 px-4 py-3 rounded-lg mb-6 flex items-center gap-2">
          <AlertTriangle className="w-5 h-5 flex-shrink-0" />
          <span>
            发现 <Link to="/vulnerabilities" className="font-bold underline">{stats.overview.criticalVulns} 个严重漏洞</Link>、{stats.overview.highVulns} 个高危漏洞，请尽快处理
          </span>
          <Link to="/vulnerabilities" className="ml-auto flex items-center gap-0.5 text-sm font-medium hover:underline">
            查看 <ChevronRight className="w-4 h-4" />
          </Link>
        </div>
      )}

      {/* Overview Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        {cards.map((c) => (
          <div key={c.label} className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500 dark:text-gray-400">{c.label}</p>
                <p className="text-3xl font-bold mt-1 dark:text-white">{c.value}</p>
              </div>
              <c.icon className={`w-8 h-8 ${c.color}`} />
            </div>
          </div>
        ))}
      </div>

      {/* Port Distribution + Severity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">端口分布 Top 10</h3>
          <ReactEChartsCore option={{
            tooltip: {},
            grid: { top: 10, right: 20, bottom: 30, left: 40 },
            xAxis: { type: 'category', data: stats.portDistribution.map((p: any) => p.port), axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' } },
            yAxis: { type: 'value', axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' }, splitLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb' } } },
            series: [{ type: 'bar', data: stats.portDistribution.map((p: any) => p.count), itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] } }],
          }} style={{ height: 250 }} />
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">漏洞严重等级分布</h3>
          <ReactEChartsCore option={{
            tooltip: { trigger: 'item' },
            series: [{
              type: 'pie', radius: ['45%', '75%'],
              data: stats.severityDistribution.map((s: any) => ({
                name: s.name, value: s.value,
                itemStyle: { color: ({ critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#3b82f6' } as Record<string, string>)[s.name] }
              })),
              label: { formatter: '{b}: {c}', color: isDark ? '#d1d5db' : '#374151' },
            }],
          }} style={{ height: 250 }} />
        </div>
      </div>

      {/* Trend Chart + Quick Actions */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">30天趋势</h3>
          {stats.trend?.length > 0 ? (
            <ReactEChartsCore option={{
              tooltip: { trigger: 'axis' },
              legend: { data: ['新增资产', '发现漏洞', '修复漏洞'], textStyle: { color: isDark ? '#d1d5db' : '#374151' } },
              grid: { top: 40, right: 20, bottom: 30, left: 40 },
              xAxis: {
                type: 'category', data: stats.trend.map((t: any) => t.date?.slice(5) || ''),
                axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' },
              },
              yAxis: {
                type: 'value',
                axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' },
                splitLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb' } },
              },
              series: [
                { name: '新增资产', type: 'line', data: stats.trend.map((t: any) => t.assetsDiscovered || 0), smooth: true, itemStyle: { color: '#3b82f6' } },
                { name: '发现漏洞', type: 'line', data: stats.trend.map((t: any) => t.vulnsFound || 0), smooth: true, itemStyle: { color: '#ef4444' } },
                { name: '修复漏洞', type: 'line', data: stats.trend.map((t: any) => t.vulnsFixed || 0), smooth: true, itemStyle: { color: '#22c55e' } },
              ],
            }} style={{ height: 280 }} />
          ) : (
            <div className="flex items-center justify-center h-[280px] text-gray-400 dark:text-gray-500">
              暂无趋势数据，完成扫描后将自动生成
            </div>
          )}
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">快速操作</h3>
          <div className="space-y-2">
            {[
              { to: '/scan-tasks', icon: ScanLine, color: 'text-blue-600', title: '新建扫描', desc: 'Nmap + Nuclei 自动化扫描' },
              { to: '/intel', icon: Globe, color: 'text-green-600', title: '外部情报查询', desc: 'Shodan + NVD + OTX' },
              { to: '/topology', icon: Network, color: 'text-purple-600', title: '网络拓扑', desc: '资产关系可视化' },
              { to: '/reports', icon: FileText, color: 'text-orange-600', title: '报告中心', desc: 'PDF / Excel 导出' },
            ].map(a => (
              <Link key={a.to} to={a.to}
                className="flex items-center gap-3 p-3 rounded-lg border dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700 text-sm transition-colors">
                <a.icon className={`w-4 h-4 ${a.color}`} />
                <div>
                  <p className="font-medium dark:text-white">{a.title}</p>
                  <p className="text-xs text-gray-500 dark:text-gray-400">{a.desc}</p>
                </div>
                <ChevronRight className="w-4 h-4 ml-auto text-gray-400" />
              </Link>
            ))}
          </div>
        </div>
      </div>

      {/* Plugin: Dashboard Widgets */}
      <div className="mt-6">
        <PluginSlot slot="dashboard-widget" />
      </div>
    </div>
  )
}
