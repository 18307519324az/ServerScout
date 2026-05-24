import { useQuery } from '@tanstack/react-query'
import { fetchDashboardStats } from '../../services/api'
import { useTranslation } from 'react-i18next'
import { Shield, Globe, AlertTriangle, Target } from 'lucide-react'

export default function QuickStatsWidget() {
  const { t } = useTranslation()
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
    { icon: Globe, label: t('dashboard.totalAssetCount'), value: assetCount, color: 'text-blue-600 dark:text-blue-400', bg: 'bg-blue-50 dark:bg-blue-900/30' },
    { icon: AlertTriangle, label: t('dashboard.criticalVulnCount'), value: criticalCount, color: 'text-red-600 dark:text-red-400', bg: 'bg-red-50 dark:bg-red-900/30' },
    { icon: Target, label: t('dashboard.activeScanCount'), value: activeTasks, color: 'text-green-600 dark:text-green-400', bg: 'bg-green-50 dark:bg-green-900/30' },
    { icon: Shield, label: t('dashboard.riskAssetCount'), value: stats?.overview?.riskAssetCount ?? 0, color: 'text-orange-600 dark:text-orange-400', bg: 'bg-orange-50 dark:bg-orange-900/30' },
  ]

  return (
    <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">{t('dashboard.quickStats')}</h3>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 font-medium">{t('common.plugin')}</span>
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
