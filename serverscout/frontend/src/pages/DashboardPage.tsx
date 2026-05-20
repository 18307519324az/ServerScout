import { useQuery } from '@tanstack/react-query'
import { fetchDashboardStats } from '../services/api'
import { Server, ScanLine, Bug, Activity } from 'lucide-react'
import ReactEChartsCore from 'echarts-for-react'
import StatusBadge from '../components/StatusBadge'

export default function DashboardPage() {
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => fetchDashboardStats(),
    refetchInterval: 30000,
  })

  const stats = data?.data?.data
  if (isLoading || !stats) return <div className="text-center py-20 text-gray-400">加载中...</div>

  const cards = [
    { label: '总资产', value: stats.overview.totalAssets, icon: Server, color: 'text-blue-600' },
    { label: '总端口', value: stats.overview.totalPorts, icon: Activity, color: 'text-green-600' },
    { label: '总漏洞', value: stats.overview.totalVulnerabilities, icon: Bug, color: 'text-red-600' },
    { label: '活跃任务', value: stats.overview.activeTasks, icon: ScanLine, color: 'text-purple-600' },
  ]

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">攻击面仪表盘</h1>

      {/* 高危漏洞醒目提示 */}
      {stats.overview.criticalVulns > 0 && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg mb-6 flex items-center gap-2">
          <span className="font-bold">⚠ 高危告警：</span>
          发现 {stats.overview.criticalVulns} 个严重漏洞、{stats.overview.highVulns} 个高危漏洞，请尽快处理
        </div>
      )}

      {/* Overview Cards */}
      <div className="grid grid-cols-4 gap-4 mb-6">
        {cards.map((c) => (
          <div key={c.label} className="bg-white p-5 rounded-xl border shadow-sm">
            <div className="flex items-center justify-between">
              <div>
                <p className="text-sm text-gray-500">{c.label}</p>
                <p className="text-3xl font-bold mt-1">{c.value}</p>
              </div>
              <c.icon className={`w-8 h-8 ${c.color}`} />
            </div>
          </div>
        ))}
      </div>

      <div className="grid grid-cols-2 gap-6">
        {/* Port Distribution */}
        <div className="bg-white p-5 rounded-xl border shadow-sm">
          <h3 className="font-semibold mb-3">端口分布 Top 10</h3>
          <ReactEChartsCore option={{
            tooltip: {},
            xAxis: { type: 'category', data: stats.portDistribution.map(p => p.port) },
            yAxis: { type: 'value' },
            series: [{ type: 'bar', data: stats.portDistribution.map(p => p.count), itemStyle: { color: '#3b82f6' } }],
          }} style={{ height: 250 }} />
        </div>

        {/* Severity Distribution */}
        <div className="bg-white p-5 rounded-xl border shadow-sm">
          <h3 className="font-semibold mb-3">漏洞严重等级分布</h3>
          <ReactEChartsCore option={{
            tooltip: { trigger: 'item' },
            series: [{
              type: 'pie', radius: ['40%', '70%'],
              data: stats.severityDistribution.map(s => ({
                name: s.name, value: s.value,
                itemStyle: { color: { critical: '#ef4444', high: '#f97316', medium: '#eab308', low: '#3b82f6' }[s.name] }
              })),
              label: { formatter: '{b}: {c}' },
            }],
          }} style={{ height: 250 }} />
        </div>
      </div>
    </div>
  )
}
