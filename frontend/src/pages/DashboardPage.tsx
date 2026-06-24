import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchDashboardStats, fetchTopRiskScores } from '../services/api'
import { CardSkeleton, ChartSkeleton } from '../components/Skeleton'
import PluginSlot from '../components/PluginSlot'
import { Server, ScanLine, Bug, Activity, Shield, AlertTriangle, ChevronRight, Globe, Network, FileText, ShieldAlert, ArrowUp } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import type { RiskScoreDetail } from '../types'
import ReactEChartsCore from 'echarts-for-react'
import echartsInstance from '../echarts'

export default function DashboardPage() {
  const { t } = useTranslation()
  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ['dashboard-stats'],
    queryFn: () => fetchDashboardStats(),
    refetchInterval: 30000,
    retry: 1,
  })

  const stats = data?.data?.data

  const { data: topRiskData } = useQuery({
    queryKey: ['top-risk-scores'],
    queryFn: () => fetchTopRiskScores(5),
    refetchInterval: 30000,
  })
  const topRisks: RiskScoreDetail[] = topRiskData?.data?.data || []

  if (isLoading) return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 mt-0.5">Asset Security Overview</p>
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

  const riskLevel = riskScore >= 100 ? { label: t('dashboard.critical'), color: 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400' }
    : riskScore >= 50 ? { label: t('dashboard.high'), color: 'border-orange-200 dark:border-orange-800 bg-orange-50 dark:bg-orange-900/20 text-orange-700 dark:text-orange-400' }
    : riskScore >= 20 ? { label: t('dashboard.medium'), color: 'border-yellow-200 dark:border-yellow-800 bg-yellow-50 dark:bg-yellow-900/20 text-yellow-700 dark:text-yellow-400' }
    : { label: t('dashboard.low'), color: 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-400' }

  const cards = [
    { label: t('dashboard.totalAssets'), value: stats.overview.totalAssets, icon: Server, color: 'text-blue-600 dark:text-blue-400' },
    { label: t('dashboard.totalPorts'), value: stats.overview.totalPorts, icon: Activity, color: 'text-green-600 dark:text-green-400' },
    { label: t('dashboard.totalVulns'), value: stats.overview.totalVulnerabilities, icon: Bug, color: 'text-red-600 dark:text-red-400' },
    { label: t('dashboard.activeTasks'), value: stats.overview.activeTasks, icon: ScanLine, color: 'text-purple-600 dark:text-purple-400' },
  ]

  const honeypotCount = stats.overview.honeypotAssetCount || 0

  const isDark = typeof document !== 'undefined' && document.documentElement.classList.contains('dark')

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold dark:text-white">{t('dashboard.title')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400 mt-0.5">{t('dashboard.securityOverview')}</p>
        </div>
        <Link to="/scan-tasks"
          className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm font-medium">
          <ScanLine className="w-4 h-4" /> {t('dashboard.newScan')}
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
              <p className="text-sm font-medium">{t('dashboard.riskScoreLabel')}</p>
              <p className="text-3xl font-bold">{riskScore}</p>
              <p className="text-xs mt-0.5">{t('dashboard.riskLevelLabel')}：<span className="font-bold">{riskLevel.label}</span></p>
            </div>
          </div>
          <div className="text-right text-sm space-y-1">
            {[{ color: 'bg-red-500', label: t('dashboard.critical'), count: stats.overview.criticalVulns },
              { color: 'bg-orange-500', label: t('dashboard.high'), count: stats.overview.highVulns },
              { color: 'bg-yellow-500', label: t('dashboard.medium'), count: stats.overview.mediumVulns },
              { color: 'bg-blue-500', label: t('dashboard.low'), count: stats.overview.lowVulns },
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
            {t('dashboard.alertPrefix')} <Link to="/vulnerabilities" className="font-bold underline">{stats.overview.criticalVulns} {t('dashboard.alertSuffix')}</Link>、{stats.overview.highVulns} {t('dashboard.alertHighSuffix')}
          </span>
          <Link to="/vulnerabilities" className="ml-auto flex items-center gap-0.5 text-sm font-medium hover:underline">
            {t('dashboard.alertView')} <ChevronRight className="w-4 h-4" />
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

      {/* Honeypot Warning Card */}
      {honeypotCount > 0 && (
        <div className={`rounded-xl border p-5 mb-6 ${
          honeypotCount >= 5 ? 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20' :
          honeypotCount >= 2 ? 'border-orange-300 dark:border-orange-700 bg-orange-50 dark:bg-orange-900/20' :
          'border-yellow-300 dark:border-yellow-700 bg-yellow-50 dark:bg-yellow-900/20'
        }`}>
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <div className={`w-12 h-12 rounded-full flex items-center justify-center ${
                honeypotCount >= 5 ? 'bg-red-100 dark:bg-red-800/30' :
                honeypotCount >= 2 ? 'bg-orange-100 dark:bg-orange-800/30' :
                'bg-yellow-100 dark:bg-yellow-800/30'
              }`}>
                <ShieldAlert className={`w-6 h-6 ${
                  honeypotCount >= 5 ? 'text-red-600 dark:text-red-400' :
                  honeypotCount >= 2 ? 'text-orange-600 dark:text-orange-400' :
                  'text-yellow-600 dark:text-yellow-400'
                }`} />
              </div>
              <div>
                <p className="text-sm font-medium dark:text-white">{t('dashboard.honeypotAssets')}</p>
                <p className={`text-3xl font-bold ${
                  honeypotCount >= 5 ? 'text-red-700 dark:text-red-400' :
                  honeypotCount >= 2 ? 'text-orange-700 dark:text-orange-400' :
                  'text-yellow-700 dark:text-yellow-400'
                }`}>{honeypotCount}</p>
                <p className="text-xs mt-0.5 text-gray-500 dark:text-gray-400">{t('dashboard.honeypotDesc')}</p>
              </div>
            </div>
            <Link to="/asset-management" className={`text-sm font-medium hover:underline ${
              honeypotCount >= 5 ? 'text-red-600 dark:text-red-400' :
              honeypotCount >= 2 ? 'text-orange-600 dark:text-orange-400' :
              'text-yellow-600 dark:text-yellow-400'
            }`}>
              {t('dashboard.alertView')} <ChevronRight className="w-4 h-4 inline" />
            </Link>
          </div>
        </div>
      )}

      {/* Port Distribution + Severity */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">{t('dashboard.portDistTitle')}</h3>
          <ReactEChartsCore echarts={echartsInstance} option={{
            tooltip: {},
            grid: { top: 10, right: 20, bottom: 30, left: 40 },
            xAxis: { type: 'category', data: stats.portDistribution.map((p: any) => p.port), axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' } },
            yAxis: { type: 'value', axisLabel: { color: isDark ? '#9ca3af' : '#6b7280' }, splitLine: { lineStyle: { color: isDark ? '#374151' : '#e5e7eb' } } },
            series: [{ type: 'bar', data: stats.portDistribution.map((p: any) => p.count), itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] } }],
          }} style={{ height: 250 }} />
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">{t('dashboard.severityDistTitle')}</h3>
          <ReactEChartsCore echarts={echartsInstance} option={{
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
          <h3 className="font-semibold mb-3 dark:text-white">{t('dashboard.trendTitle')}</h3>
          {stats.trend?.length > 0 ? (
            <ReactEChartsCore echarts={echartsInstance} option={{
              tooltip: { trigger: 'axis' },
              legend: { data: [t('scanTasks.newAssets'), t('scanTasks.vulnsFound'), t('dashboard.fixedCount')], textStyle: { color: isDark ? '#d1d5db' : '#374151' } },
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
                { name: t('scanTasks.newAssets'), type: 'line', data: stats.trend.map((t: any) => t.assetsDiscovered || 0), smooth: true, lineStyle: { color: '#3b82f6', width: 3, type: 'solid' }, itemStyle: { color: '#3b82f6' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{offset: 0, color: 'rgba(59,130,246,0.25)'}, {offset: 1, color: 'rgba(59,130,246,0.02)'}] } }, symbol: 'circle', symbolSize: 6 },
                { name: t('scanTasks.vulnsFound'), type: 'line', data: stats.trend.map((t: any) => t.vulnsFound || 0), smooth: true, lineStyle: { color: '#ef4444', width: 2.5, type: 'dashed' }, itemStyle: { color: '#ef4444' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{offset: 0, color: 'rgba(239,68,68,0.2)'}, {offset: 1, color: 'rgba(239,68,68,0.02)'}] } }, symbol: 'diamond', symbolSize: 7 },
                { name: t('dashboard.fixedCount'), type: 'line', data: stats.trend.map((t: any) => t.vulnsFixed || 0), smooth: true, lineStyle: { color: '#22c55e', width: 2.5, type: 'dotted' }, itemStyle: { color: '#22c55e' }, areaStyle: { color: { type: 'linear', x: 0, y: 0, x2: 0, y2: 1, colorStops: [{offset: 0, color: 'rgba(34,197,94,0.2)'}, {offset: 1, color: 'rgba(34,197,94,0.02)'}] } }, symbol: 'triangle', symbolSize: 7 },
              ],
            }} style={{ height: 280 }} />
          ) : (
            <div className="flex items-center justify-center h-[280px] text-gray-400 dark:text-gray-500">
              {t('common.noData')}
            </div>
          )}
        </div>
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm">
          <h3 className="font-semibold mb-3 dark:text-white">{t('dashboard.quickActions')}</h3>
          <div className="space-y-2">
            {[
              { to: '/scan-tasks', icon: ScanLine, color: 'text-blue-600', title: t('dashboard.newScan'), desc: t('dashboard.newScanDesc') },
              { to: '/intel', icon: Globe, color: 'text-green-600', title: t('dashboard.intelLookup'), desc: t('dashboard.intelLookupDesc') },
              { to: '/topology', icon: Network, color: 'text-purple-600', title: t('dashboard.topologyView'), desc: t('dashboard.topologyViewDesc') },
              { to: '/reports', icon: FileText, color: 'text-orange-600', title: t('dashboard.reportCenter'), desc: t('dashboard.reportCenterDesc') },
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

      {/* Risk Top 5 */}
      {topRisks.length > 0 && (
        <div className="bg-white dark:bg-gray-800 p-5 rounded-xl border dark:border-gray-700 shadow-sm mb-6">
          <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
            <Shield className="w-4 h-4 text-orange-500" />
            风险资产 Top 5
          </h3>
          <div className="space-y-2">
            {topRisks.map((r, i) => (
              <div key={r.id} className="flex items-center justify-between p-3 rounded-lg border dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors">
                <div className="flex items-center gap-3 min-w-0 flex-1">
                  <span className="text-sm font-bold text-gray-400 dark:text-gray-500 w-5 flex-shrink-0">{i + 1}</span>
                  <div className="min-w-0 flex-1">
                    <p className="text-sm font-medium dark:text-white truncate">
                      {r.assetIp}
                      {r.assetName && <span className="text-xs text-gray-400 dark:text-gray-500 ml-1">({r.assetName})</span>}
                    </p>
                    <p className="text-xs text-gray-400 dark:text-gray-500 truncate">{r.riskReason}</p>
                  </div>
                </div>
                <div className="flex items-center gap-2 flex-shrink-0 ml-3">
                  <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                    r.riskLevel === 'CRITICAL' ? 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' :
                    r.riskLevel === 'HIGH' ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400' :
                    r.riskLevel === 'MEDIUM' ? 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400' :
                    r.riskLevel === 'LOW' ? 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' :
                    'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400'
                  }`}>{{
                    CRITICAL: '严重',
                    HIGH: '高危',
                    MEDIUM: '中危',
                    LOW: '低危',
                    INFO: '信息',
                  }[r.riskLevel] || r.riskLevel}</span>
                  <span className="text-sm font-bold dark:text-white tabular-nums">{r.finalRiskScore}</span>
                  <ArrowUp className={`w-4 h-4 ${
                    r.finalRiskScore >= 60 ? 'text-red-500' : r.finalRiskScore >= 40 ? 'text-orange-500' : 'text-yellow-500'
                  }`} />
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Plugin: Dashboard Widgets */}
      <div className="mt-6">
        <PluginSlot slot="dashboard-widget" />
      </div>
    </div>
  )
}
