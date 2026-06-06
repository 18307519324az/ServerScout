import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { ArrowLeft, Bug, CheckCircle2, Circle, Download, Globe, Layers, Loader2, Server, Shield, Square } from 'lucide-react'
import { cancelScanTask, downloadExcelReport, downloadPdfReport, fetchScanTaskDetail } from '../services/api'
import ProgressBar from '../components/ProgressBar'
import StatusBadge from '../components/StatusBadge'

interface DiscoveryEvent {
  type: string
  ip?: string
  hostname?: string
  osInfo?: string
  portCount?: number
  ports?: { port: number; protocol: string; service: string; version: string; product: string }[]
  severity?: string
  cveId?: string
  name?: string
  url?: string
  affected?: string
  server?: string
  framework?: string
  cms?: string
  title?: string
  port?: number
  timestamp: number
}

function getStatusText(status: string, t: (key: string) => string) {
  switch (status) {
    case 'completed':
      return t('scanTasks.statusCompleted')
    case 'running':
      return t('scanTasks.statusRunning')
    case 'pending':
      return t('scanTasks.statusPending')
    case 'failed':
      return t('scanTasks.statusFailed')
    case 'cancelled':
      return t('scanTasks.statusCancelled')
    default:
      return status
  }
}

function getSeverityLabel(severity?: string) {
  if (!severity) return 'info'
  const normalized = severity.toLowerCase()
  if (normalized === 'critical') return 'critical'
  if (normalized === 'high') return 'high'
  if (normalized === 'medium') return 'medium'
  if (normalized === 'low') return 'low'
  return normalized
}

export default function ScanTaskDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [progressMsg, setProgressMsg] = useState('')
  const [progressAssets, setProgressAssets] = useState(0)
  const [discoveries, setDiscoveries] = useState<DiscoveryEvent[]>([])
  const feedRef = useRef<HTMLDivElement>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['scan-task', id],
    queryFn: () => fetchScanTaskDetail(Number(id)),
    refetchInterval: (query) => {
      const status = query.state.data?.data?.data?.status
      return status === 'running' || status === 'pending' ? 3000 : false
    },
  })

  const task = data?.data?.data
  const cancelMutation = useMutation({
    mutationFn: () => cancelScanTask(Number(id)),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })

  const handleDownload = async (format: 'pdf' | 'excel', taskId: number) => {
    try {
      const res = format === 'pdf' ? await downloadPdfReport(taskId) : await downloadExcelReport(taskId)
      const ext = format === 'pdf' ? 'pdf' : 'xlsx'
      const blob = new Blob([res.data], {
        type: format === 'pdf'
          ? 'application/pdf'
          : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `report_${taskId}.${ext}`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      console.error('Download failed:', err)
    }
  }

  useEffect(() => {
    if (!id || task?.status !== 'running') return

    const eventSource = new EventSource(`/api/v1/scan-tasks/${id}/progress`)

    eventSource.addEventListener('progress', (event) => {
      try {
        const d = JSON.parse((event as MessageEvent<string>).data)
        setProgressMsg(d.currentTarget || '')
        setProgressAssets(d.assetsFound || 0)
        queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
      } catch {
        // Ignore malformed events
      }
    })

    eventSource.addEventListener('discovery', (event) => {
      try {
        const d: DiscoveryEvent = JSON.parse((event as MessageEvent<string>).data)
        setDiscoveries((prev) => [...prev.slice(-199), d])
      } catch {
        // Ignore malformed events
      }
    })

    eventSource.addEventListener('completed', () => {
      setProgressMsg(t('scanTasks.scanCompleted'))
      eventSource.close()
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
    })

    eventSource.addEventListener('error', () => {
      setProgressMsg(t('scanTasks.scanInterrupted'))
      eventSource.close()
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
    })

    return () => eventSource.close()
  }, [id, queryClient, task?.status, t])

  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight
    }
  }, [discoveries])

  if (isLoading) {
    return <div className="text-center py-20 text-gray-400 dark:text-gray-500">{t('common.loading')}</div>
  }

  if (!task) {
    return <div className="text-center py-20 text-red-500 dark:text-red-400">{t('scanTasks.taskNotFound')}</div>
  }

  const isRunning = task.status === 'running' || task.status === 'pending'

  const getDiscoveryIcon = (type: string) => {
    switch (type) {
      case 'asset':
        return <Server className="w-3.5 h-3.5 text-blue-500" />
      case 'vuln':
        return <Bug className="w-3.5 h-3.5 text-red-500" />
      case 'fingerprint':
        return <Globe className="w-3.5 h-3.5 text-green-500" />
      default:
        return <Shield className="w-3.5 h-3.5 text-gray-500" />
    }
  }

  const getDiscoveryText = (d: DiscoveryEvent) => {
    switch (d.type) {
      case 'asset': {
        const portList = d.ports?.slice(0, 5).map((p) => `${p.port}/${p.service || p.protocol}`).join(', ')
        const more = (d.ports?.length || 0) > 5 ? ` +${d.ports!.length - 5}` : ''
        return `Host ${d.ip || '-'}${d.hostname ? ` (${d.hostname})` : ''}, ports: ${d.portCount || 0}${portList ? `, ${portList}${more}` : ''}`
      }
      case 'vuln':
        return `[${getSeverityLabel(d.severity)}] ${d.cveId || d.name || '-'} ${d.url || d.affected || ''}`
      case 'fingerprint': {
        const parts = [d.server, d.framework, d.cms, d.title].filter(Boolean)
        return `Fingerprint ${d.ip || '-'}:${d.port || '-'} ${parts.join(' / ')}`
      }
      default:
        return JSON.stringify(d)
    }
  }

  const getDiscoveryColor = (type: string) => {
    switch (type) {
      case 'asset':
        return 'border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/30 text-gray-800 dark:text-gray-200'
      case 'vuln':
        return 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/30 text-gray-800 dark:text-gray-200'
      case 'fingerprint':
        return 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/30 text-gray-800 dark:text-gray-200'
      default:
        return 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-800 dark:text-gray-200'
    }
  }

  return (
    <div>
      <button
        onClick={() => navigate('/scan-tasks')}
        className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 mb-4"
      >
        <ArrowLeft className="w-4 h-4" /> {t('scanTasks.backToList')}
      </button>

      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold dark:text-white">{task.name}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-1 font-mono text-sm">{task.targetRange}</p>
          </div>
          <div className="flex items-center gap-2">
            {isRunning && (
              <button
                onClick={() => cancelMutation.mutate()}
                disabled={cancelMutation.isPending}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border border-orange-300 text-orange-700 dark:text-orange-300 rounded-lg hover:bg-orange-50 dark:hover:bg-orange-900/20 disabled:opacity-50"
              >
                <Square className="w-3.5 h-3.5" /> Cancel
              </button>
            )}
            <StatusBadge status={task.status} />
          </div>
        </div>

        <div className="mt-4">
          <div className="flex items-center gap-2">
            <ProgressBar value={task.progress} />
            {isRunning && <Loader2 className="w-4 h-4 animate-spin text-blue-500" />}
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">{task.progress}%</p>

          {task.status === 'pending' && !progressMsg && (
            <div className="mt-3 p-3 bg-amber-50 dark:bg-amber-900/20 border border-amber-200 dark:border-amber-800 rounded-lg text-sm text-amber-700 dark:text-amber-300">
              Waiting for an available scan worker or another scan of the same target to finish.
            </div>
          )}
          {isRunning && progressMsg && (
            <div className="mt-3 p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg text-sm">
              <p className="text-blue-700 dark:text-blue-400 font-medium flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                {progressMsg}
              </p>
              {progressAssets > 0 && (
                <p className="text-blue-500 dark:text-blue-400 text-xs mt-1">
                  {t('scanTasks.discoveredAssetsProgress', { count: progressAssets })}
                </p>
              )}
            </div>
          )}
        </div>
      </div>

      {task.status === 'failed' && task.errorMessage && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-red-300 dark:border-red-700 shadow-sm p-5 mb-6">
          <h3 className="font-semibold mb-2 text-red-600 dark:text-red-400 flex items-center gap-2">
            <span className="text-lg">!</span> {t('scanTasks.failedReason')}
          </h3>
          <p className="text-sm text-red-700 dark:text-red-300 bg-red-50 dark:bg-red-900/30 p-3 rounded-lg font-mono whitespace-pre-wrap">
            {task.errorMessage}
          </p>
        </div>
      )}

      {isRunning && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5 mb-6">
          <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
            <Layers className="w-4 h-4 text-purple-600" />
            {t('scanTasks.pipelinePhase')}
          </h3>
          <PipelineProgress progress={task.progress} t={t} />
        </div>
      )}

      {discoveries.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
          <h3 className="font-semibold mb-3 flex items-center gap-2 dark:text-white">
            <span>{t('scanTasks.discoveryLog')}</span>
            <span className="text-xs text-gray-400 dark:text-gray-500 font-normal">
              ({t('scanTasks.recordCount', { count: discoveries.length })})
            </span>
          </h3>
          <div ref={feedRef} className="max-h-80 overflow-y-auto space-y-1.5">
            {discoveries.map((d, i) => (
              <div key={i} className={`flex items-start gap-2 px-2.5 py-1.5 rounded text-xs border ${getDiscoveryColor(d.type)}`}>
                <span className="mt-0.5 flex-shrink-0">{getDiscoveryIcon(d.type)}</span>
                <span className="leading-relaxed">{getDiscoveryText(d)}</span>
              </div>
            ))}
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
        <button
          type="button"
          onClick={() => task.totalAssets > 0 && navigate(`/assets?taskId=${task.id}`)}
          disabled={task.totalAssets <= 0}
          className={`p-4 rounded-xl border shadow-sm text-center transition ${
            task.totalAssets > 0
              ? 'bg-white dark:bg-gray-800 dark:border-gray-700 hover:border-blue-300 dark:hover:border-blue-700'
              : 'bg-white dark:bg-gray-800 dark:border-gray-700 cursor-default'
          }`}
        >
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('scanTasks.assetsFound')}</p>
          <p className="text-2xl font-bold dark:text-white">{task.totalAssets}</p>
          {task.totalAssets > 0 && (
            <p className="text-xs text-blue-600 dark:text-blue-400 mt-1">{t('scanTasks.viewRelatedAssets')}</p>
          )}
        </button>

        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('scanTasks.portsFound')}</p>
          <p className="text-2xl font-bold dark:text-white">{task.totalPorts}</p>
        </div>

        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('scanTasks.scanType')}</p>
          <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">{task.scanType}</p>
        </div>

        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">{t('common.status')}</p>
          <p className="text-xl font-bold dark:text-white">{getStatusText(task.status, t)}</p>
        </div>
      </div>

      {task.summary && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6">
          <h3 className="font-semibold mb-4 dark:text-white">{t('scanTasks.summary')}</h3>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 text-sm">
            <div className="dark:text-gray-200">
              {t('scanTasks.webServices')}: <span className="font-semibold dark:text-white">{task.summary.webServiceCount}</span>
            </div>
            <div className="text-green-600 dark:text-green-400">
              {t('scanTasks.newAssets')}: {task.summary.newAssetCount ?? task.totalAssets}
            </div>
            <div className="text-blue-600 dark:text-blue-400">
              {t('scanTasks.updatedAssets')}: {task.summary.updatedAssetCount ?? 0}
            </div>
            <div className="text-red-600 dark:text-red-400">{t('common.criticalVulns')}: {task.summary.criticalVulnCount}</div>
            <div className="text-orange-600 dark:text-orange-400">{t('dashboard.high')}: {task.summary.highVulnCount}</div>
            <div className="text-yellow-600 dark:text-yellow-400">{t('dashboard.medium')}: {task.summary.mediumVulnCount ?? 0}</div>
          </div>

          {task.summary.topPorts && task.summary.topPorts.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium mb-2 dark:text-white">{t('scanTasks.topPorts')}:</p>
              <div className="flex gap-2 flex-wrap">
                {task.summary.topPorts.map((p: { port: number; count: number }, i: number) => (
                  <span key={i} className="px-2 py-1 bg-gray-100 dark:bg-gray-700 rounded text-xs dark:text-gray-200">
                    Port {p.port}: {p.count}
                  </span>
                ))}
              </div>
            </div>
          )}

          <div className="flex flex-wrap gap-3 mt-4">
            <button
              onClick={() => handleDownload('pdf', task.id)}
              className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
            >
              <Download className="w-4 h-4" /> {t('scanTasks.exportPdf')}
            </button>
            <button
              onClick={() => handleDownload('excel', task.id)}
              className="flex items-center gap-1.5 px-4 py-2 border dark:border-gray-600 text-sm rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200"
            >
              <Download className="w-4 h-4" /> {t('scanTasks.exportExcel')}
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

function PipelineProgress({ progress, t }: { progress: number; t: (key: string) => string }) {
  const phases = [
    { label: t('scanTasks.phaseRecon'), desc: t('scanTasks.phaseReconDesc'), threshold: 30, icon: Server, color: 'text-blue-600' },
    { label: t('scanTasks.phaseFingerprint'), desc: t('scanTasks.phaseFingerprintDesc'), threshold: 55, icon: Globe, color: 'text-green-600' },
    { label: t('scanTasks.phaseVulnScan'), desc: t('scanTasks.phaseVulnScanDesc'), threshold: 67, icon: Bug, color: 'text-orange-600' },
    { label: t('scanTasks.phaseCveMatch'), desc: t('scanTasks.phaseCveMatchDesc'), threshold: 75, icon: Shield, color: 'text-red-600' },
    { label: t('scanTasks.phaseReport'), desc: t('scanTasks.phaseReportDesc'), threshold: 100, icon: CheckCircle2, color: 'text-purple-600' },
  ]

  return (
    <div className="flex flex-col md:flex-row items-stretch md:items-center gap-2">
      {phases.map((phase, i) => {
        const done = progress >= phase.threshold
        const active = !done && (i === 0 || progress >= phases[i - 1].threshold)

        return (
          <div key={phase.label} className="flex-1 flex items-center">
            <div
              className={`flex flex-col items-center gap-1 px-2 py-3 rounded-lg flex-1 ${
                done
                  ? 'bg-green-50 dark:bg-green-900/20'
                  : active
                    ? 'bg-blue-50 dark:bg-blue-900/20 ring-2 ring-blue-300 dark:ring-blue-700'
                    : 'bg-gray-50 dark:bg-gray-800'
              }`}
            >
              {done ? (
                <CheckCircle2 className="w-5 h-5 text-green-500" />
              ) : active ? (
                <Loader2 className={`w-5 h-5 animate-spin ${phase.color}`} />
              ) : (
                <Circle className="w-5 h-5 text-gray-300 dark:text-gray-600" />
              )}

              <span className={`text-xs font-bold ${done ? 'text-green-600 dark:text-green-400' : active ? phase.color : 'text-gray-400 dark:text-gray-500'}`}>
                {phase.label}
              </span>
              <span className="text-[10px] text-gray-400 dark:text-gray-500">{phase.desc}</span>
            </div>

            {i < phases.length - 1 && (
              <div className={`hidden md:block h-0.5 w-4 -mx-0.5 ${progress >= phase.threshold ? 'bg-green-400' : 'bg-gray-200 dark:bg-gray-700'}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}
