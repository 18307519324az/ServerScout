import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchScanTaskDetail, downloadPdfReport, downloadExcelReport } from '../services/api'
import StatusBadge from '../components/StatusBadge'
import ProgressBar from '../components/ProgressBar'
import { ArrowLeft, Loader2, Server, Shield, Globe, Bug, Layers, CheckCircle2, Circle, Download } from 'lucide-react'

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

export default function ScanTaskDetailPage() {
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

  const handleDownload = async (format: 'pdf' | 'excel', taskId: number) => {
    try {
      const res = format === 'pdf' ? await downloadPdfReport(taskId) : await downloadExcelReport(taskId)
      const ext = format === 'pdf' ? 'pdf' : 'xlsx'
      const blob = new Blob([res.data], {
        type: format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
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

  // SSE 实时进度监听
  useEffect(() => {
    if (!id || task?.status !== 'running') return

    const eventSource = new EventSource(`/api/v1/scan-tasks/${id}/progress`)

    eventSource.addEventListener('progress', (event) => {
      const d = JSON.parse(event.data)
      setProgressMsg(d.currentTarget || '')
      setProgressAssets(d.assetsFound || 0)
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
    })

    eventSource.addEventListener('discovery', (event) => {
      const d: DiscoveryEvent = JSON.parse(event.data)
      setDiscoveries(prev => [...prev.slice(-199), d])
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
    })

    eventSource.addEventListener('completed', () => {
      setProgressMsg('扫描完成！')
      eventSource.close()
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
    })

    eventSource.addEventListener('error', () => {
      setProgressMsg('扫描异常，连接中断')
      eventSource.close()
      queryClient.invalidateQueries({ queryKey: ['scan-task', id] })
    })

    return () => eventSource.close()
  }, [id, task?.status])

  // Auto-scroll discovery feed
  useEffect(() => {
    if (feedRef.current) {
      feedRef.current.scrollTop = feedRef.current.scrollHeight
    }
  }, [discoveries])

  if (isLoading) return <div className="text-center py-20 text-gray-400 dark:text-gray-500">加载中...</div>
  if (!task) return <div className="text-center py-20 text-red-500 dark:text-red-400">任务不存在（可能已被删除，请返回任务列表）</div>

  const isRunning = task.status === 'running' || task.status === 'pending'

  const getDiscoveryIcon = (type: string) => {
    switch (type) {
      case 'asset': return <Server className="w-3.5 h-3.5 text-blue-500" />
      case 'vuln': return <Bug className="w-3.5 h-3.5 text-red-500" />
      case 'fingerprint': return <Globe className="w-3.5 h-3.5 text-green-500" />
      default: return <Shield className="w-3.5 h-3.5 text-gray-500" />
    }
  }

  const getDiscoveryText = (d: DiscoveryEvent) => {
    switch (d.type) {
      case 'asset': {
        const portList = d.ports?.slice(0, 5).map(p => `${p.port}/${p.service || p.protocol}`).join(', ')
        const more = (d.ports?.length || 0) > 5 ? ` +${d.ports!.length - 5}` : ''
        return `发现主机 ${d.ip}${d.hostname ? ` (${d.hostname})` : ''} — ${d.portCount} 个开放端口: ${portList}${more}`
      }
      case 'vuln': {
        const sevLabel = d.severity === 'critical' ? '严重' : d.severity === 'high' ? '高危' : d.severity === 'medium' ? '中危' : '低危'
        return `[${sevLabel}] ${d.cveId || d.name} — ${d.url || d.affected || ''}`
      }
      case 'fingerprint': {
        const parts = [d.server, d.framework, d.cms, d.title].filter(Boolean)
        return `识别 ${d.ip}:${d.port} — ${parts.join(' / ')}`
      }
      default: return JSON.stringify(d)
    }
  }

  const getDiscoveryColor = (type: string) => {
    switch (type) {
      case 'asset': return 'border-blue-200 dark:border-blue-800 bg-blue-50 dark:bg-blue-900/30 text-gray-800 dark:text-gray-200'
      case 'vuln': return 'border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/30 text-gray-800 dark:text-gray-200'
      case 'fingerprint': return 'border-green-200 dark:border-green-800 bg-green-50 dark:bg-green-900/30 text-gray-800 dark:text-gray-200'
      default: return 'border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-800 text-gray-800 dark:text-gray-200'
    }
  }

  return (
    <div>
      <button onClick={() => navigate('/scan-tasks')} className="flex items-center gap-1 text-sm text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 mb-4">
        <ArrowLeft className="w-4 h-4" /> 返回任务列表
      </button>

      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold dark:text-white">{task.name}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-1 font-mono text-sm">{task.targetRange}</p>
          </div>
          <StatusBadge status={task.status} />
        </div>
        <div className="mt-4">
          <div className="flex items-center gap-2">
            <ProgressBar value={task.progress} />
            {isRunning && <Loader2 className="w-4 h-4 animate-spin text-blue-500" />}
          </div>
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-1">{task.progress}%</p>

          {/* 实时进度信息 */}
          {isRunning && progressMsg && (
            <div className="mt-3 p-3 bg-blue-50 dark:bg-blue-900/20 border border-blue-200 dark:border-blue-800 rounded-lg text-sm">
              <p className="text-blue-700 dark:text-blue-400 font-medium flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                {progressMsg}
              </p>
              {progressAssets > 0 && (
                <p className="text-blue-500 dark:text-blue-400 text-xs mt-1">已发现 {progressAssets} 个资产</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Shannon-style Pipeline Visualization */}
      {isRunning && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-5 mb-6">
          <h3 className="font-semibold mb-4 dark:text-white flex items-center gap-2">
            <Layers className="w-4 h-4 text-purple-600" />
            扫描 Pipeline (Shannon 风格)
          </h3>
          <PipelineProgress progress={task.progress} />
        </div>
      )}

      {/* Live discovery feed */}
      {discoveries.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
          <h3 className="font-semibold mb-3 flex items-center gap-2 dark:text-white">
            <span>实时发现日志</span>
            <span className="text-xs text-gray-400 dark:text-gray-500 font-normal">({discoveries.length} 条)</span>
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

      <div className="grid grid-cols-4 gap-4 mb-6">
        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">发现资产</p>
          <p className="text-2xl font-bold dark:text-white">{task.totalAssets}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">发现端口</p>
          <p className="text-2xl font-bold dark:text-white">{task.totalPorts}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">扫描类型</p>
          <p className="text-2xl font-bold text-blue-600 dark:text-blue-400">{task.scanType}</p>
        </div>
        <div className="bg-white dark:bg-gray-800 p-4 rounded-xl border dark:border-gray-700 shadow-sm text-center">
          <p className="text-sm text-gray-500 dark:text-gray-400">状态</p>
          <p className="text-xl font-bold dark:text-white">
            {task.status === 'completed' ? '已完成' :
             task.status === 'running' ? '扫描中' :
             task.status === 'failed' ? '失败' : task.status}
          </p>
        </div>
      </div>

      {task.summary && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6">
          <h3 className="font-semibold mb-4 dark:text-white">扫描摘要</h3>
          <div className="grid grid-cols-3 gap-4 text-sm">
            <div className="dark:text-gray-200">Web 服务: <span className="font-semibold dark:text-white">{task.summary.webServiceCount}</span></div>
            <div className="text-green-600 dark:text-green-400">
              新增资产: {task.summary.newAssetCount || task.totalAssets}
            </div>
            <div className="text-blue-600 dark:text-blue-400">
              更新资产: {task.summary.updatedAssetCount || 0}
            </div>
            <div className="text-red-600 dark:text-red-400">严重漏洞: {task.summary.criticalVulnCount}</div>
            <div className="text-orange-600 dark:text-orange-400">高危漏洞: {task.summary.highVulnCount}</div>
            <div className="text-yellow-600 dark:text-yellow-400">中危漏洞: {task.summary.mediumVulnCount || 0}</div>
          </div>
          {task.summary.topPorts && task.summary.topPorts.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium mb-2 dark:text-white">Top 端口:</p>
              <div className="flex gap-2 flex-wrap">
                {task.summary.topPorts.map((p: any, i: number) => (
                  <span key={i} className="px-2 py-1 bg-gray-100 dark:bg-gray-700 rounded text-xs dark:text-gray-200">
                    Port {p.port}: {p.count}
                  </span>
                ))}
              </div>
            </div>
          )}
          <div className="flex gap-3 mt-4">
            <button onClick={() => handleDownload('pdf', task.id)}
              className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
              <Download className="w-4 h-4" /> 导出 PDF 报告
            </button>
            <button onClick={() => handleDownload('excel', task.id)}
              className="flex items-center gap-1.5 px-4 py-2 border dark:border-gray-600 text-sm rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
              <Download className="w-4 h-4" /> 导出 Excel 报告
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

// Shannon-style scan pipeline visualization
function PipelineProgress({ progress }: { progress: number }) {
  const phases = [
    { label: 'Recon', desc: '端口扫描', threshold: 30, icon: Server, color: 'text-blue-600' },
    { label: 'Fingerprint', desc: 'Web指纹', threshold: 55, icon: Globe, color: 'text-green-600' },
    { label: 'Vuln Scan', desc: '漏洞扫描', threshold: 67, icon: Bug, color: 'text-orange-600' },
    { label: 'CVE Match', desc: 'CVE匹配', threshold: 75, icon: Shield, color: 'text-red-600' },
    { label: 'Report', desc: '报告生成', threshold: 100, icon: CheckCircle2, color: 'text-purple-600' },
  ]

  return (
    <div className="flex items-center gap-1">
      {phases.map((phase, i) => {
        const done = progress >= phase.threshold
        const active = !done && (i === 0 || progress >= phases[i - 1].threshold)
        return (
          <div key={phase.label} className="flex-1 flex items-center">
            <div className={`flex flex-col items-center gap-1 px-2 py-3 rounded-lg flex-1 ${
              done ? 'bg-green-50 dark:bg-green-900/20' : active ? 'bg-blue-50 dark:bg-blue-900/20 ring-2 ring-blue-300 dark:ring-blue-700' : 'bg-gray-50 dark:bg-gray-800'}`}>
              {done ? <CheckCircle2 className="w-5 h-5 text-green-500" />
               : active ? <Loader2 className={`w-5 h-5 animate-spin ${phase.color}`} />
               : <Circle className="w-5 h-5 text-gray-300 dark:text-gray-600" />}
              <span className={`text-xs font-bold ${done ? 'text-green-600 dark:text-green-400' : active ? phase.color : 'text-gray-400 dark:text-gray-500'}`}>
                {phase.label}
              </span>
              <span className="text-[10px] text-gray-400 dark:text-gray-500">{phase.desc}</span>
            </div>
            {i < phases.length - 1 && (
              <div className={`h-0.5 w-4 -mx-0.5 ${progress >= phase.threshold ? 'bg-green-400' : 'bg-gray-200 dark:bg-gray-700'}`} />
            )}
          </div>
        )
      })}
    </div>
  )
}
