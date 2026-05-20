import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchScanTaskDetail } from '../services/api'
import StatusBadge from '../components/StatusBadge'
import ProgressBar from '../components/ProgressBar'
import { ArrowLeft, Loader2, Server, Shield, Globe, Bug } from 'lucide-react'

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

  if (isLoading) return <div className="text-center py-20 text-gray-400">加载中...</div>
  if (!task) return <div className="text-center py-20 text-red-500">任务不存在（可能已被删除，请返回任务列表）</div>

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
      case 'asset': return 'border-blue-200 bg-blue-50'
      case 'vuln': return 'border-red-200 bg-red-50'
      case 'fingerprint': return 'border-green-200 bg-green-50'
      default: return 'border-gray-200 bg-gray-50'
    }
  }

  return (
    <div>
      <button onClick={() => navigate('/scan-tasks')} className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft className="w-4 h-4" /> 返回任务列表
      </button>

      <div className="bg-white rounded-xl border shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold">{task.name}</h1>
            <p className="text-gray-500 mt-1 font-mono text-sm">{task.targetRange}</p>
          </div>
          <StatusBadge status={task.status} />
        </div>
        <div className="mt-4">
          <div className="flex items-center gap-2">
            <ProgressBar value={task.progress} />
            {isRunning && <Loader2 className="w-4 h-4 animate-spin text-blue-500" />}
          </div>
          <p className="text-xs text-gray-400 mt-1">{task.progress}%</p>

          {/* 实时进度信息 */}
          {isRunning && progressMsg && (
            <div className="mt-3 p-3 bg-blue-50 border border-blue-200 rounded-lg text-sm">
              <p className="text-blue-700 font-medium flex items-center gap-2">
                <Loader2 className="w-3.5 h-3.5 animate-spin" />
                {progressMsg}
              </p>
              {progressAssets > 0 && (
                <p className="text-blue-500 text-xs mt-1">已发现 {progressAssets} 个资产</p>
              )}
            </div>
          )}
        </div>
      </div>

      {/* Live discovery feed */}
      {discoveries.length > 0 && (
        <div className="bg-white rounded-xl border shadow-sm p-6 mb-6">
          <h3 className="font-semibold mb-3 flex items-center gap-2">
            <span>实时发现日志</span>
            <span className="text-xs text-gray-400 font-normal">({discoveries.length} 条)</span>
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
        <div className="bg-white p-4 rounded-xl border shadow-sm text-center">
          <p className="text-sm text-gray-500">发现资产</p>
          <p className="text-2xl font-bold">{task.totalAssets}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border shadow-sm text-center">
          <p className="text-sm text-gray-500">发现端口</p>
          <p className="text-2xl font-bold">{task.totalPorts}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border shadow-sm text-center">
          <p className="text-sm text-gray-500">扫描类型</p>
          <p className="text-2xl font-bold text-blue-600">{task.scanType}</p>
        </div>
        <div className="bg-white p-4 rounded-xl border shadow-sm text-center">
          <p className="text-sm text-gray-500">状态</p>
          <p className="text-xl font-bold">
            {task.status === 'completed' ? '已完成' :
             task.status === 'running' ? '扫描中' :
             task.status === 'failed' ? '失败' : task.status}
          </p>
        </div>
      </div>

      {task.summary && (
        <div className="bg-white rounded-xl border shadow-sm p-6">
          <h3 className="font-semibold mb-4">扫描摘要</h3>
          <div className="grid grid-cols-3 gap-4 text-sm">
            <div>Web 服务: {task.summary.webServiceCount}</div>
            <div className="text-green-600">
              新增资产: {task.summary.newAssetCount || task.totalAssets}
            </div>
            <div className="text-blue-600">
              更新资产: {task.summary.updatedAssetCount || 0}
            </div>
            <div className="text-red-600">严重漏洞: {task.summary.criticalVulnCount}</div>
            <div className="text-orange-600">高危漏洞: {task.summary.highVulnCount}</div>
            <div className="text-yellow-600">中危漏洞: {task.summary.mediumVulnCount || 0}</div>
          </div>
          {task.summary.topPorts && task.summary.topPorts.length > 0 && (
            <div className="mt-4">
              <p className="text-sm font-medium mb-2">Top 端口:</p>
              <div className="flex gap-2">
                {task.summary.topPorts.map((p: any, i: number) => (
                  <span key={i} className="px-2 py-1 bg-gray-100 rounded text-xs">
                    Port {p.port}: {p.count}
                  </span>
                ))}
              </div>
            </div>
          )}
          <div className="flex gap-3 mt-4">
            <a href={`/api/v1/reports/pdf?taskId=${task.id}`}
              className="px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700" target="_blank">
              导出 PDF 报告
            </a>
            <a href={`/api/v1/reports/excel?taskId=${task.id}`}
              className="px-4 py-2 border text-sm rounded-lg hover:bg-gray-50" target="_blank">
              导出 Excel 报告
            </a>
          </div>
        </div>
      )}
    </div>
  )
}
