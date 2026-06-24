import { useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { AlertTriangle, ArrowLeft, Bug, CheckCircle2, Circle, Download, Globe, Layers, Loader2, Server, Shield, Square } from 'lucide-react'
import { cancelScanTask, downloadExcelReport, downloadPdfReport, fetchScanTaskDetail, fetchScanTaskStages, fetchRiskScoresByTask } from '../services/api'
import ProgressBar from '../components/ProgressBar'
import StatusBadge from '../components/StatusBadge'
import { getScanTypeLabel } from '../utils/scanType'
import type { ScanTaskStage, RiskScoreDetail } from '../types'

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
  const [sseDisconnected, setSseDisconnected] = useState(false)
  const [pollFailCount, setPollFailCount] = useState(0)
  const [elapsedSeconds, setElapsedSeconds] = useState(0)
  const startTimeRef = useRef(Date.now())
  const feedRef = useRef<HTMLDivElement>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['scan-task', id],
    queryFn: async () => {
      try {
        const result = await fetchScanTaskDetail(Number(id))
        setPollFailCount(0)
        return result
      } catch (err) {
        setPollFailCount(prev => prev + 1)
        throw err
      }
    },
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

  const { data: stagesData } = useQuery({
    queryKey: ['scan-task-stages', id],
    queryFn: () => fetchScanTaskStages(Number(id)),
    enabled: !!id && task?.status !== 'pending',
    refetchInterval: task?.status === 'running' ? 5000 : false,
  })
  const stages = stagesData?.data?.data

  const { data: riskData } = useQuery({
    queryKey: ['risk-scores-task', id],
    queryFn: () => fetchRiskScoresByTask(Number(id)),
    enabled: !!id && task?.status !== 'pending',
    refetchInterval: task?.status === 'running' ? 5000 : false,
  })
  const riskScores = riskData?.data?.data

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
      // Don't show "interrupted" — SSE disconnect is expected and polling continues
      setSseDisconnected(true)
      setProgressMsg(t('scanTasks.sseFallback'))
      eventSource.close()
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
    })

    return () => {
      eventSource.close()
      setSseDisconnected(false)
    }
  }, [id, queryClient, task?.status, t])

  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight
    }
  }, [discoveries])

  // Elapsed time counter for running tasks
  useEffect(() => {
    if (task?.status === 'running' || task?.status === 'pending') {
      startTimeRef.current = Date.now()
      setElapsedSeconds(0)
      const interval = setInterval(() => {
        setElapsedSeconds(Math.floor((Date.now() - startTimeRef.current) / 1000))
      }, 1000)
      return () => clearInterval(interval)
    } else {
      setElapsedSeconds(0)
    }
  }, [task?.status])

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
            <div className="flex items-center gap-2 mt-1">
              {task.scanMode === 'DEMO' ? (
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800 dark:bg-yellow-900/30 dark:text-yellow-400 border border-yellow-300 dark:border-yellow-700" title="数据来源：模拟演示数据">
                  演示数据
                </span>
              ) : task.scanMode === 'REAL' ? (
                <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800 dark:bg-blue-900/30 dark:text-blue-400 border border-blue-300 dark:border-blue-700" title="数据来源：真实扫描工具">
                  真实扫描
                </span>
              ) : null}
              <span className="text-xs text-gray-400 dark:text-gray-500">
                {task.scanMode === 'DEMO' ? '扫描模式：Demo Mode' : task.scanMode === 'REAL' ? '扫描模式：Real Mode' : '扫描模式：未记录'}
              </span>
            </div>
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
            <div className={`mt-3 p-3 border rounded-lg text-sm ${
              pollFailCount >= 5
                ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800'
                : sseDisconnected
                  ? 'bg-yellow-50 dark:bg-yellow-900/20 border-yellow-200 dark:border-yellow-800'
                  : 'bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800'
            }`}>
              <p className={`font-medium flex items-center gap-2 ${
                pollFailCount >= 5
                  ? 'text-red-700 dark:text-red-300'
                  : sseDisconnected
                    ? 'text-yellow-700 dark:text-yellow-300'
                    : 'text-blue-700 dark:text-blue-400'
              }`}>
                {sseDisconnected && pollFailCount >= 5 ? (
                  <AlertTriangle className="w-3.5 h-3.5" />
                ) : sseDisconnected ? (
                  <AlertTriangle className="w-3.5 h-3.5" />
                ) : (
                  <Loader2 className="w-3.5 h-3.5 animate-spin" />
                )}
                {pollFailCount >= 5 ? '无法连接后端，请检查服务状态' : progressMsg}
              </p>
              {progressAssets > 0 && (
                <p className="text-blue-500 dark:text-blue-400 text-xs mt-1">
                  {t('scanTasks.discoveredAssetsProgress', { count: progressAssets })}
                </p>
              )}
              {isRunning && elapsedSeconds > 0 && (
                <p className="text-gray-500 dark:text-gray-400 text-xs mt-1">
                  已运行：{elapsedSeconds} 秒
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

      {task.status === 'completed' && task.totalAssets === 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border border-amber-300 dark:border-amber-700 shadow-sm p-5 mb-6">
          <h3 className="font-semibold mb-2 text-amber-600 dark:text-amber-400 flex items-center gap-2">
            <AlertTriangle className="w-4 h-4" /> 扫描完成但未发现资产
          </h3>
          <div className="text-sm text-amber-700 dark:text-amber-300 bg-amber-50 dark:bg-amber-900/30 p-3 rounded-lg">
            <p className="mb-1">未发现开放端口或存活主机。可能原因：</p>
            <ul className="list-disc list-inside space-y-0.5 text-xs">
              <li>目标安全组未开放对应端口</li>
              <li>目标防火墙拦截了扫描探测</li>
              <li>端口范围未包含实际开放端口</li>
              <li>目标不允许公网访问或已下线</li>
            </ul>
            <p className="mt-2 text-xs">建议：尝试指定已知开放的端口范围，或检查目标安全组/防火墙设置。</p>
          </div>
        </div>
      )}

      {(isRunning || stages) && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5 mb-6">
          <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
            <Layers className="w-4 h-4 text-purple-600" />
            {t('scanTasks.pipelinePhase')}
          </h3>
          {stages && stages.length > 0 ? (
            <div className="space-y-2">
              {stages.map((stage) => (
                <StageRow key={stage.stageCode} stage={stage} />
              ))}
            </div>
          ) : isRunning ? (
            <PipelineProgress progress={task.progress} t={t} />
          ) : null}
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
          onClick={() => task.totalAssets > 0 && navigate(`/asset-management?taskId=${task.id}`)}
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
          <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">{getScanTypeLabel(task.scanType)}</p>
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

      {riskScores && riskScores.length > 0 && (
        <RiskScoreSection scores={riskScores} t={t} />
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

const stageStatusBadge: Record<string, string> = {
  PENDING: 'pending',
  RUNNING: 'running',
  SUCCESS: 'completed',
  FAILED: 'failed',
  SKIPPED: 'cancelled',
}

function StageRow({ stage }: { stage: ScanTaskStage }) {
  const config: Record<string, { icon: any; className: string }> = {
    PENDING: { icon: Circle, className: 'text-gray-300 dark:text-gray-600' },
    RUNNING: { icon: Loader2, className: 'text-blue-500 animate-spin' },
    SUCCESS: { icon: CheckCircle2, className: 'text-green-500' },
    FAILED: { icon: Bug, className: 'text-red-500' },
    SKIPPED: { icon: Circle, className: 'text-yellow-400' },
  }
  const c = config[stage.status] || config.PENDING
  const Icon = c.icon

  return (
    <div className="flex items-center gap-3 px-3 py-2 rounded-lg bg-gray-50 dark:bg-gray-900/50 border dark:border-gray-700">
      <Icon className={`w-4 h-4 flex-shrink-0 ${c.className}`} />
      <div className="flex-1 min-w-0">
        <div className="flex items-center justify-between gap-2">
          <span className="text-sm font-medium dark:text-white truncate">{stage.stageName}</span>
          <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 flex-shrink-0">
            {stage.status === 'RUNNING' && <span>{stage.progress}%</span>}
            {stage.status === 'SUCCESS' && stage.durationMs != null && (
              <span>{(stage.durationMs / 1000).toFixed(1)}s</span>
            )}
            <StatusBadge status={stageStatusBadge[stage.status] || 'pending'} />
          </div>
        </div>
        {stage.status === 'RUNNING' && (
          <div className="mt-1.5">
            <div className="h-1.5 bg-gray-200 dark:bg-gray-700 rounded-full overflow-hidden">
              <div className="h-full bg-blue-500 rounded-full transition-all duration-500" style={{ width: `${stage.progress}%` }} />
            </div>
          </div>
        )}
        {stage.summary && (
          <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5 truncate">{stage.summary}</p>
        )}
        {stage.errorMessage && (
          <p className="text-xs text-red-500 mt-0.5 truncate">{stage.errorMessage}</p>
        )}
      </div>
    </div>
  )
}

const riskLevelColors: Record<string, string> = {
  CRITICAL: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400',
  HIGH: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400',
  MEDIUM: 'bg-yellow-100 text-yellow-700 dark:bg-yellow-900/30 dark:text-yellow-400',
  LOW: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400',
  INFO: 'bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400',
}

const riskLevelText: Record<string, string> = {
  CRITICAL: '严重',
  HIGH: '高危',
  MEDIUM: '中危',
  LOW: '低危',
  INFO: '信息',
}

function RiskScoreSection({ scores, t }: { scores: RiskScoreDetail[]; t: (key: string) => string }) {
  const [expandedAsset, setExpandedAsset] = useState<number | null>(null)
  const sorted = [...scores].sort((a, b) => b.finalRiskScore - a.finalRiskScore)
  const maxScore = sorted[0]?.finalRiskScore ?? 0
  const maxLevel = sorted[0]?.riskLevel ?? 'INFO'
  const avgScore = Math.round(scores.reduce((s, r) => s + r.finalRiskScore, 0) / scores.length)

  return (
    <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mt-6">
      <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
        <AlertTriangle className="w-4 h-4 text-orange-500" />
        风险评分
      </h3>

      {/* Summary banner */}
      <div className="flex flex-wrap items-center gap-4 mb-5 p-4 rounded-lg bg-gray-50 dark:bg-gray-900/50 border dark:border-gray-700">
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">最高风险</p>
          <div className="flex items-center gap-2">
            <span className="text-2xl font-bold dark:text-white">{maxScore}</span>
            <span className={`px-2 py-0.5 rounded text-xs font-medium ${riskLevelColors[maxLevel] || ''}`}>
              {riskLevelText[maxLevel] || maxLevel}
            </span>
          </div>
        </div>
        <div className="w-px h-10 bg-gray-200 dark:bg-gray-700" />
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">平均风险</p>
          <p className="text-2xl font-bold dark:text-white">{avgScore}</p>
        </div>
        <div className="w-px h-10 bg-gray-200 dark:bg-gray-700" />
        <div>
          <p className="text-xs text-gray-500 dark:text-gray-400 mb-0.5">已评估资产</p>
          <p className="text-2xl font-bold dark:text-white">{scores.length}</p>
        </div>
      </div>

      {/* Risk asset list */}
      <div className="space-y-3">
        <p className="text-sm font-medium dark:text-white">资产风险排行</p>
        {sorted.slice(0, 10).map((r) => (
          <div key={r.assetId} className="border dark:border-gray-700 rounded-lg">
            <button
              onClick={() => setExpandedAsset(expandedAsset === r.assetId ? null : r.assetId)}
              className="w-full flex items-center gap-3 px-3 py-2.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-900/30 transition-colors"
            >
              <span className={`w-2 h-2 rounded-full flex-shrink-0 ${
                r.riskLevel === 'CRITICAL' ? 'bg-red-500' :
                r.riskLevel === 'HIGH' ? 'bg-orange-500' :
                r.riskLevel === 'MEDIUM' ? 'bg-yellow-500' : 'bg-blue-500'
              }`} />
              <span className="font-mono text-xs dark:text-gray-300 w-32 truncate text-left">{r.assetIp}</span>
              <span className="flex-1 text-left dark:text-gray-300 truncate">{r.assetName}</span>
              <span className="text-base font-bold dark:text-white">{r.finalRiskScore}</span>
              <span className={`px-1.5 py-0.5 rounded text-xs font-medium ${riskLevelColors[r.riskLevel] || ''}`}>
                {riskLevelText[r.riskLevel] || r.riskLevel}
              </span>
            </button>
            {expandedAsset === r.assetId && (
              <div className="px-3 pb-3 space-y-2 border-t dark:border-gray-700 pt-2">
                <div className="grid grid-cols-2 sm:grid-cols-3 lg:grid-cols-6 gap-2 text-xs">
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">暴露面</p>
                    <p className="font-semibold dark:text-white">{r.assetExposureScore}</p>
                  </div>
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">漏洞严重性</p>
                    <p className="font-semibold dark:text-white">{r.vulnerabilitySeverityScore}</p>
                  </div>
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">服务风险</p>
                    <p className="font-semibold dark:text-white">{r.serviceRiskScore}</p>
                  </div>
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">可利用性</p>
                    <p className="font-semibold dark:text-white">{r.exploitabilityScore}</p>
                  </div>
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">业务重要性</p>
                    <p className="font-semibold dark:text-white">{r.businessImportanceScore}</p>
                  </div>
                  <div className="p-2 rounded bg-gray-50 dark:bg-gray-900/50">
                    <p className="text-gray-500 dark:text-gray-400">修正扣减</p>
                    <p className="font-semibold dark:text-white">-{r.remediationDeduction}</p>
                  </div>
                </div>
                {r.riskReason && (
                  <div>
                    <p className="text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">风险原因</p>
                    <p className="text-xs text-gray-700 dark:text-gray-300 leading-relaxed">{r.riskReason}</p>
                  </div>
                )}
                {r.repairSuggestion && (
                  <div>
                    <p className="text-xs font-medium text-gray-600 dark:text-gray-400 mb-1">修复建议</p>
                    <p className="text-xs text-gray-700 dark:text-gray-300 whitespace-pre-line leading-relaxed">{r.repairSuggestion}</p>
                  </div>
                )}
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
