import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchScanTasks, createScanTask, deleteScanTask, cancelScanTask, fetchScanTypes } from '../services/api'
import { useToast } from '../hooks/useToast'
import { useMode } from '../contexts/ModeContext'
import StatusBadge from '../components/StatusBadge'
import ProgressBar from '../components/ProgressBar'
import Pagination from '../components/Pagination'
import ConfirmDialog from '../components/ConfirmDialog'
import { Plus, X, Trash2, AlertCircle, Eye, RotateCcw, Square, Shield } from 'lucide-react'
import { getScanTypeLabel, validatePortRange } from '../utils/scanType'
import { PRESET_CARDS, applyScanTemplate } from '../constants/scanTemplates'
import dayjs from 'dayjs'

export default function ScanTaskListPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [showCreate, setShowCreate] = useState(false)
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const [deleteStatus, setDeleteStatus] = useState('')
  const [targetError, setTargetError] = useState('')
  const [portRangeError, setPortRangeError] = useState('')
  const [createError, setCreateError] = useState('')
  const [authorized, setAuthorized] = useState(false)
  const [selectedPreset, setSelectedPreset] = useState('quick')
  const [showFullPortConfirm, setShowFullPortConfirm] = useState(false)
  const [pendingPayload, setPendingPayload] = useState<any>(null)
  const queryClient = useQueryClient()
  const { mode, isDemo, isReal } = useMode()

  const { data, isLoading } = useQuery({
    queryKey: ['scan-tasks', page, pageSize],
    queryFn: () => fetchScanTasks({ page, size: pageSize }),
    refetchInterval: (query) => {
      const content = query.state.data?.data?.data?.content ?? []
      return content.some((task: any) => task.status === 'pending' || task.status === 'running') ? 10000 : false
    },
  })

  // Fetch custom scan types from L2 plugins
  const { data: scanTypesData } = useQuery({
    queryKey: ['scanTypes'],
    queryFn: () => fetchScanTypes(),
  })
  const customScanTypes: string[] = scanTypesData?.data?.data || []

  const closeCreateDialog = () => {
    setShowCreate(false)
    setCreateError('')
    setTargetError('')
    setPortRangeError('')
  }

  const createMutation = useMutation({
    mutationFn: createScanTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setShowCreate(false)
      setCreateError('')
      toast.success(t('scanTasks.createScan') + '成功')
    },
    onError: (err: any) => {
      const resp = err?.response?.data
      const field = resp?.data?.field
      const detail = resp?.data?.error || resp?.error
      const msg = detail
        ? (field && field !== 'unknown' ? `${field}: ${detail}` : detail)
        : (resp?.message || err?.message || 'Unknown error')
      setCreateError(`创建失败: ${msg}`)
      toast.error(`创建失败: ${msg}`)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteScanTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setDeleteId(null); setDeleteStatus('')
      toast.success(t('scanTasks.deleteScan') + '成功')
    },
    onError: (err: any) => { toast.error(err?.response?.data?.message || t('common.delete') + '失败') },
  })

  const cancelMutation = useMutation({
    mutationFn: cancelScanTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success('Scan task cancelled')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || 'Failed to cancel scan task'),
  })

  const rescanMutation = useMutation({
    mutationFn: (task: any) => {
      const payload: any = {
        name: `${task.name} - rescan`,
        targetRange: task.targetRange,
        scanType: task.scanType,
        portRange: task.portRange,
        enableFingerprint: Boolean(task.enableFingerprint),
        enableVulnScan: Boolean(task.enableVulnScan),
        enableCrawler: Boolean(task.enableCrawler),
      }
      if (isReal) {
        payload.authorized = true
      }
      return createScanTask(payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      queryClient.invalidateQueries({ queryKey: ['dashboard'] })
      toast.success('Rescan task created')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || 'Failed to create rescan task'),
  })

  const tasks = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.page?.totalPages ?? 0
  const totalElements = data?.data?.data?.page?.totalElements ?? 0

  const validateSingleTarget = (value: string): string | null => {
    if (!value) return '扫描目标不能为空'
    if (/^https?:\/\//i.test(value)) {
      return t('scanTasks.targetRange') + '不需要 http:// 前缀'
    }
    if (/^(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?$/.test(value)) {
      return null
    }
    if (/^(?:\d{1,3}\.){3}\d{1,3}\/([0-9]|[12][0-9]|3[0-2])$/.test(value)) {
      return null
    }
    if (/^(?:\d{1,3}\.){3}\d{1,3}\/\d{2,}$/.test(value)) {
      return 'CIDR 掩码应为 0-32'
    }
    if (/^[\w.-]+(?::\d{1,5})?$/.test(value)) {
      return null
    }
    return '格式不正确，请使用 IP/CIDR（如 192.168.1.0/24）或域名'
  }

  const validateTarget = (value: string): boolean => {
    if (!value) {
      setTargetError('扫描目标不能为空')
      return false
    }

    const targets = value.split(',').map(s => s.trim()).filter(Boolean)
    if (targets.length === 0) {
      setTargetError('扫描目标不能为空')
      return false
    }

    for (const target of targets) {
      const err = validateSingleTarget(target)
      if (err) {
        setTargetError(err)
        return false
      }
    }

    setTargetError('')
    return true
  }

  const countPortsInRange = (range: string): number => {
    if (!range || !range.trim()) return 0
    let count = 0
    for (const part of range.split(',')) {
      const trimmed = part.trim()
      if (trimmed.includes('-')) {
        const [s, e] = trimmed.split('-').map(n => parseInt(n.trim(), 10))
        if (!isNaN(s) && !isNaN(e) && e >= s) count += e - s + 1
      } else {
        const p = parseInt(trimmed, 10)
        if (!isNaN(p)) count++
      }
    }
    return count
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold dark:text-white">{t('scanTasks.title')}</h1>
        <button onClick={() => { setCreateError(''); setTargetError(''); setShowCreate(true) }}
          className="flex items-center gap-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm">
          <Plus className="w-4 h-4" /> {t('scanTasks.newScan')}
        </button>
      </div>

      {isLoading ? <div className="text-center py-20 text-gray-400 dark:text-gray-500">{t('common.loading')}</div> : (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm overflow-x-auto">
          <table className="w-full min-w-[800px]">
            <thead className="bg-gray-50 dark:bg-gray-700 text-left text-sm text-gray-500 dark:text-gray-400">
              <tr>
                <th className="px-4 py-3">{t('scanTasks.scanName')}</th>
                <th className="px-4 py-3">{t('scanTasks.targetRange')}</th>
                <th className="px-4 py-3">{t('scanTasks.scanType')}</th>
                <th className="px-4 py-3">{t('common.status')}</th>
                <th className="px-4 py-3 w-40">{t('scanTasks.progress')}</th>
                <th className="px-4 py-3 text-center">{t('assets.asset')}</th>
                <th className="px-4 py-3">{t('common.time')}</th>
                <th className="px-4 py-3 min-w-40">{t('common.operation')}</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {tasks.map((task: any) => (
                <tr key={task.id} className="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700">
                  <td className="px-4 py-3">
                    <Link to={`/scan-tasks/${task.id}`} className="text-blue-600 dark:text-blue-400 hover:underline font-medium">{task.name}</Link>
                  </td>
                  <td className="px-4 py-3 font-mono text-gray-600 dark:text-gray-300">{task.targetRange}</td>
                  <td className="px-4 py-3 dark:text-gray-300">{getScanTypeLabel(task.scanType)}</td>
                  <td className="px-4 py-3"><StatusBadge status={task.status} /></td>
                  <td className="px-4 py-3"><ProgressBar value={task.progress} /></td>
                  <td className="px-4 py-3 text-center">
                    {task.totalAssets > 0 ? (
                      <Link
                        to={`/asset-management?taskId=${task.id}`}
                        className="font-mono text-blue-600 dark:text-blue-400 hover:underline"
                        title={`${t('nav.assets')} (Task #${task.id})`}
                      >
                        {task.totalAssets}
                      </Link>
                    ) : (
                      <span className="font-mono dark:text-gray-300">{task.totalAssets}</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">{task.createdAt ? dayjs(task.createdAt).format('YYYY-MM-DD HH:mm') : '-'}</td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1">
                      <Link to={`/scan-tasks/${task.id}`} className="p-1 text-gray-400 hover:text-blue-600 transition" title="View details">
                        <Eye className="w-4 h-4" />
                      </Link>
                      <button onClick={() => rescanMutation.mutate(task)} className="p-1 text-gray-400 hover:text-green-600 transition" title="Rescan">
                        <RotateCcw className="w-4 h-4" />
                      </button>
                      {(task.status === 'pending' || task.status === 'running') && (
                        <button onClick={() => cancelMutation.mutate(task.id)} className="p-1 text-gray-400 hover:text-orange-600 transition" title="Cancel scan">
                          <Square className="w-4 h-4" />
                        </button>
                      )}
                      <button onClick={() => { setDeleteId(task.id); setDeleteStatus(task.status) }} className="p-1 text-gray-400 hover:text-red-600 transition" title="Delete">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {tasks.length === 0 && (
                <tr>
                  <td colSpan={8} className="text-center py-16 text-gray-400 dark:text-gray-500">
                    {t('scanTasks.noTasks')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Pagination
        page={page}
        totalPages={totalPages}
        pageSize={pageSize}
        totalElements={totalElements}
        onPageChange={(p) => setPage(p)}
        onPageSizeChange={(s) => { setPageSize(s); setPage(0) }}
      />

      {/* Create Dialog */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center" onClick={closeCreateDialog}>
          <div className="bg-white dark:bg-gray-800 rounded-xl p-6 w-full max-w-[500px] mx-4 max-h-[90vh] overflow-y-auto" onClick={e => e.stopPropagation()}>
            <div className="flex justify-between items-center mb-4">
              <h2 className="font-bold text-lg dark:text-white">新建扫描任务</h2>
              <button onClick={closeCreateDialog}><X className="w-5 h-5 dark:text-gray-300" /></button>
            </div>
            <form onSubmit={e => {
              e.preventDefault()
              const fd = new FormData(e.target as HTMLFormElement)
              const name = String(fd.get('name') || '').trim()
              if (!name) {
                setCreateError('创建失败: 任务名称不能为空')
                return
              }
              let target = (fd.get('targetRange') as string).trim()
              if (!validateTarget(target)) return

              if (isReal && !authorized) {
                setCreateError('真实扫描模式下，请确认你对目标资产拥有合法授权。')
                return
              }

              if (!target.includes(',')) {
                const portMatch = target.match(/^(.*):(\d{1,5})$/)
                if (portMatch) {
                  target = portMatch[1]
                  fd.set('portRange', portMatch[2])
                }
              }

              const portRangeVal = (fd.get('portRange') as string) || ''
              // Validate port range on frontend
              const portErr = validatePortRange(portRangeVal)
              if (portErr) {
                setPortRangeError(portErr)
                return
              }
              setPortRangeError('')

              const payload: any = {
                name,
                targetRange: target,
                scanType: fd.get('scanType') as string,
                portRange: portRangeVal,
                enableFingerprint: fd.get('enableFingerprint') === 'on',
                enableVulnScan: fd.get('enableVulnScan') === 'on',
                enableCrawler: fd.get('enableCrawler') === 'on',
              }
              if (isReal) {
                payload.authorized = authorized
              }
              if (import.meta.env.DEV) { console.log('Create scan task payload:', payload) }

              const scanTypeVal = fd.get('scanType') as string
              if (scanTypeVal === 'FULL' || countPortsInRange(portRangeVal) > 3000) {
                setPendingPayload(payload)
                setShowFullPortConfirm(true)
                return
              }

              createMutation.mutate(payload)
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">任务名称</label>
                <input name="name" className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描目标 (IP/CIDR/域名)</label>
                <input name="targetRange" placeholder="例：192.168.1.1 或 192.168.1.0/24 或 example.com"
                  onChange={e => validateTarget(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-lg text-sm font-mono outline-none focus:ring-2 dark:bg-gray-700 dark:text-gray-200 ${targetError ? 'border-red-400 focus:ring-red-500' : 'dark:border-gray-600 focus:ring-blue-500'}`} />
                {targetError && <p className="text-red-500 text-xs mt-1">{targetError}</p>}
                <p className="text-gray-400 dark:text-gray-500 text-xs mt-1">多个目标用逗号分隔；端口在下方单独设置（Quick 推荐 1-1000，Full 默认 1-65535）</p>
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描模板预设</label>
                <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                  {PRESET_CARDS.map(p => (
                    <label key={p.id} className={`cursor-pointer border rounded-lg p-2 text-center hover:shadow transition ${p.color}`}>
                      <input type="radio" name="preset" value={p.id} defaultChecked={p.id === 'quick'}
                        onChange={(e) => {
                          setSelectedPreset(p.id)
                          setPortRangeError('')
                          const form = e.currentTarget.form
                          if (form) applyScanTemplate(p.id, form)
                        }}
                        className="sr-only"
                      />
                      <p className="text-sm font-bold">{p.label}</p>
                      <p className="text-xs opacity-70">{p.desc}</p>
                    </label>
                  ))}
                </div>
                {selectedPreset === 'stealth' && (
                  <p className="text-xs text-purple-600 dark:text-purple-400 mt-1">
                    隐匿扫描：降低扫描速率和报文特征，适合绕过入侵检测系统。启用服务识别和指纹探测。
                  </p>
                )}
                {selectedPreset === 'full' && (
                  <p className="text-xs text-red-600 dark:text-red-400 mt-1">
                    全扫描会执行更完整的端口、服务、Web、漏洞和风险分析流程，耗时更长，请确保目标已授权。
                  </p>
                )}
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描类型</label>
                  <select name="scanType" className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm outline-none bg-white dark:bg-gray-700 dark:text-gray-200">
                    <option value="QUICK">QUICK（快速扫描）</option>
                    <option value="STEALTH">STEALTH（隐匿扫描）</option>
                    <option value="WEB">WEB（Web 专用）</option>
                    <option value="FULL">FULL（全端口 + 漏洞）</option>
                    {customScanTypes.filter(t => !['QUICK','STEALTH','WEB','FULL','quick','full','vuln','nuclei'].includes(t)).map(t => (
                      <option key={t} value={t}>{t}（自定义策略）</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 dark:text-gray-300">端口范围</label>
                  <input name="portRange" defaultValue="1-1000" placeholder="1-1000"
                    onChange={e => { const err = validatePortRange(e.target.value); setPortRangeError(err || '') }}
                    className={`w-full px-3 py-2 border rounded-lg text-sm font-mono outline-none focus:ring-2 dark:bg-gray-700 dark:text-gray-200 ${portRangeError ? 'border-red-400 focus:ring-red-500' : 'dark:border-gray-600 focus:ring-blue-500'}`} />
                  {portRangeError && <p className="text-red-500 text-xs mt-1">{portRangeError}</p>}
                </div>
              </div>
              <div className="flex gap-4 text-sm dark:text-gray-300 flex-wrap">
                <label className="flex items-center gap-1">
                  <input type="checkbox" name="enableFingerprint" defaultChecked /> 指纹识别
                </label>
                <label className="flex items-center gap-1">
                  <input type="checkbox" name="enableVulnScan" /> 漏洞扫描
                </label>
                <label className="flex items-center gap-1">
                  <input type="checkbox" name="enableCrawler" /> 爬虫发现
                </label>
              </div>

              {/* Mode awareness banner */}
              {isDemo && (
                <div className="flex items-start gap-2 p-3 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg text-sm text-yellow-700 dark:text-yellow-300">
                  <Shield className="w-4 h-4 mt-0.5 flex-shrink-0" />
                  <span>当前为 Demo Mode：本次扫描将生成模拟资产、端口、漏洞、风险评分和报告，不会访问真实目标。</span>
                </div>
              )}
              {isReal && (
                <div className="flex items-start gap-2 p-3 bg-orange-50 dark:bg-orange-900/20 border border-orange-200 dark:border-orange-800 rounded-lg text-sm text-orange-700 dark:text-orange-300">
                  <Shield className="w-4 h-4 mt-0.5 flex-shrink-0" />
                  <span>
                    <p>当前为 Real Mode：系统将调用真实扫描工具。请确认你对目标资产拥有合法授权。</p>
                    <p className="mt-1 text-xs opacity-80">真实扫描耗时取决于端口范围、网络质量和目标响应情况。建议优先使用快速扫描或指定端口范围。</p>
                  </span>
                </div>
              )}
              {isReal && (
                <label className="flex items-start gap-2 p-3 bg-gray-50 dark:bg-gray-700 border border-gray-200 dark:border-gray-600 rounded-lg text-sm cursor-pointer hover:bg-gray-100 dark:hover:bg-gray-600">
                  <input
                    type="checkbox"
                    checked={authorized}
                    onChange={(e) => setAuthorized(e.target.checked)}
                    className="mt-0.5"
                  />
                  <span className="text-gray-700 dark:text-gray-300 font-medium">我确认对该扫描目标拥有合法授权</span>
                </label>
              )}
              {createError && (
                <div className="flex items-center gap-2 p-2.5 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-600 dark:text-red-400 text-sm">
                  <AlertCircle className="w-4 h-4 flex-shrink-0" />
                  {createError}
                </div>
              )}
              <button type="submit" disabled={createMutation.isPending}
                className="w-full py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {createMutation.isPending ? '创建中...' : '开始扫描'}
              </button>
            </form>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={deleteId !== null}
        title="删除扫描任务"
        message={deleteStatus === 'running'
          ? "该任务正在扫描中，删除将先取消扫描再删除数据。删除后相关资产和端口数据也将被移除，确定继续？"
          : "删除后任务相关的资产和端口数据也将被删除，确定继续？"}
        confirmLabel="删除"
        variant="danger"
        onConfirm={() => deleteId && deleteMutation.mutate(deleteId)}
        onCancel={() => { setDeleteId(null); setDeleteStatus('') }}
      />

      <ConfirmDialog
        open={showFullPortConfirm}
        title="全端口扫描确认"
        message={`你选择了全端口扫描（1-65535），耗时可能较长，具体时间取决于目标响应速度、网络质量和安全组配置。扫描过程中可以取消任务。确认继续？`}
        confirmLabel="确认扫描"
        onConfirm={() => {
          if (pendingPayload) createMutation.mutate(pendingPayload)
          setShowFullPortConfirm(false)
          setPendingPayload(null)
        }}
        onCancel={() => { setShowFullPortConfirm(false); setPendingPayload(null) }}
      />
    </div>
  )
}
