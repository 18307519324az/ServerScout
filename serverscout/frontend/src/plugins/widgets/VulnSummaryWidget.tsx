import { useQuery } from '@tanstack/react-query'
import { fetchVulnerabilities } from '../../services/api'
import { AlertTriangle, Shield, Bug, CheckCircle } from 'lucide-react'

export default function VulnSummaryWidget() {
  const { data } = useQuery({
    queryKey: ['vulnerabilities', 'summary'],
    queryFn: () => fetchVulnerabilities({ size: 100 }),
    refetchInterval: 60000,
  })

  const vulns = data?.data?.data?.content || []
  const critical = vulns.filter((v: any) => v.severity === 'critical').length
  const high = vulns.filter((v: any) => v.severity === 'high').length
  const medium = vulns.filter((v: any) => v.severity === 'medium').length
  const fixed = vulns.filter((v: any) => v.status === 'fixed').length

  const items = [
    { icon: AlertTriangle, label: '严重', value: critical, color: 'text-red-600', bg: 'bg-red-50 dark:bg-red-900/30' },
    { icon: Bug, label: '高危', value: high, color: 'text-orange-600', bg: 'bg-orange-50 dark:bg-orange-900/30' },
    { icon: Shield, label: '中危', value: medium, color: 'text-yellow-600', bg: 'bg-yellow-50 dark:bg-yellow-900/30' },
    { icon: CheckCircle, label: '已修复', value: fixed, color: 'text-green-600', bg: 'bg-green-50 dark:bg-green-900/30' },
  ]

  return (
    <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">漏洞概览 (Plugin)</h3>
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
