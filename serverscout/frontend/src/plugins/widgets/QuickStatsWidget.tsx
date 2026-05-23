import { useQuery } from '@tanstack/react-query'
import { fetchDashboardStats } from '../../services/api'
import { Shield, Globe, AlertTriangle, Target } from 'lucide-react'

/**
 * Quick Stats Widget for Dashboard — uses the same query key as DashboardPage
 * so data stays in sync with the main dashboard stats.
 */
export default function QuickStatsWidget() {
  const { data } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => fetchDashboardStats(),
    refetchInterval: 30000,
  })

  const stats = data?.data?.data
  const assetCount = stats?.overview?.totalAssets ?? 0
  const criticalCount = stats?.overview?.criticalVulns ?? 0
  const activeTasks = stats?.overview?.activeTasks ?? 0

  const items = [
    { icon: Globe, label: '资产总数', value: assetCount, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50 dark:bg-blue-900/30' },
    { icon: AlertTriangle, label: '严重漏洞', value: criticalCount, color: 'text-red-600 dark:text-red-400', bg: 'bg-red-50 dark:bg-red-900/30' },
    { icon: Target, label: '活跃扫描', value: activeTasks, color: 'text-green-600 dark:text-green-400', bg: 'bg-green-50 dark:bg-green-900/30' },
    { icon: Shield, label: '风险资产', value: stats?.overview?.riskAssetCount ?? 0, color: 'text-orange-600 dark:text-orange-400', bg: 'bg-orange-50 dark:bg-orange-900/30' },
  ]

  return (
    <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">快速统计 (Plugin)</h3>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 font-medium">PLUGIN</span>
      </div>
      <div className="grid grid-cols-2 gap-2">
        {items.map((item, i) => (
          <div key={i} className={`${item.bg} rounded-lg p-3 flex items-center gap-2`}>
            <item.icon className={`w-4 h-4 ${item.color}`} />
            <div>
              <div className={`text-lg font-bold ${item.color}`}>{item.value}</div>
              <div className="text-[10px] text-gray-500 dark:text-gray-400">{item.label}</div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
