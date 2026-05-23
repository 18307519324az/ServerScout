import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchScanTasks, createScanTask, deleteScanTask } from '../services/api'
import { useToast } from '../hooks/useToast'
import StatusBadge from '../components/StatusBadge'
import ProgressBar from '../components/ProgressBar'
import ConfirmDialog from '../components/ConfirmDialog'
import { Plus, X, Trash2, AlertCircle } from 'lucide-react'
import dayjs from 'dayjs'

export default function ScanTaskListPage() {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [showCreate, setShowCreate] = useState(false)
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const [deleteStatus, setDeleteStatus] = useState('')
  const [targetError, setTargetError] = useState('')
  const [createError, setCreateError] = useState('')
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['scan-tasks', page],
    queryFn: () => fetchScanTasks({ page, size: 20 }),
    refetchInterval: 10000,
  })

  const createMutation = useMutation({
    mutationFn: createScanTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setShowCreate(false)
      setCreateError('')
      toast.success('扫描任务创建成功，正在执行...')
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message || err?.message || '未知错误'
      setCreateError('创建失败: ' + msg)
      toast.error('创建失败: ' + msg)
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteScanTask,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setDeleteId(null); setDeleteStatus('')
      toast.success('扫描任务已删除')
    },
    onError: (err: any) => { toast.error(err?.response?.data?.message || '删除失败') },
  })

  const tasks = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.page?.totalPages ?? 0

  const validateTarget = (value: string): boolean => {
    if (!value) return false
    if (/^https?:\/\//i.test(value)) {
      setTargetError('请输入 IP 或域名，不需要 http:// 前缀')
      return false
    }
    // Allow IP with optional :port suffix (e.g., 192.168.1.1:8080)
    if (/^(?:\d{1,3}\.){3}\d{1,3}(?::\d{1,5})?$/.test(value)) {
      setTargetError('')
      return true
    }
    // CIDR notation: only allow /0-/32 for IPv4
    if (/^(?:\d{1,3}\.){3}\d{1,3}\/([0-9]|[12][0-9]|3[0-2])$/.test(value)) {
      setTargetError('')
      return true
    }
    // Warn if using /XX where XX > 32 (likely meant port)
    if (/^(?:\d{1,3}\.){3}\d{1,3}\/\d{2,}$/.test(value)) {
      setTargetError('CIDR 掩码应为 0-32，如需指定端口请在下方"端口范围"字段中输入')
      return false
    }
    if (/^[\w.-]+$/.test(value)) {
      setTargetError('')
      return true
    }
    setTargetError('格式不正确，请使用 IP/CIDR（如 192.168.1.0/24）或域名')
    return false
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h1 className="text-2xl font-bold dark:text-white">扫描任务</h1>
        <button onClick={() => setShowCreate(true)}
          className="flex items-center gap-1 px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 text-sm">
          <Plus className="w-4 h-4" /> 新建扫描
        </button>
      </div>

      {isLoading ? <div className="text-center py-20 text-gray-400 dark:text-gray-500">加载中...</div> : (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 dark:bg-gray-700 text-left text-sm text-gray-500 dark:text-gray-400">
              <tr>
                <th className="px-4 py-3">任务名称</th>
                <th className="px-4 py-3">目标</th>
                <th className="px-4 py-3">类型</th>
                <th className="px-4 py-3">状态</th>
                <th className="px-4 py-3 w-40">进度</th>
                <th className="px-4 py-3 text-center">资产</th>
                <th className="px-4 py-3">时间</th>
                <th className="px-4 py-3 w-16">操作</th>
              </tr>
            </thead>
            <tbody className="text-sm">
              {tasks.map((t: any) => (
                <tr key={t.id} className="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700">
                  <td className="px-4 py-3">
                    <Link to={`/scan-tasks/${t.id}`} className="text-blue-600 dark:text-blue-400 hover:underline font-medium">{t.name}</Link>
                  </td>
                  <td className="px-4 py-3 font-mono text-gray-600 dark:text-gray-300">{t.targetRange}</td>
                  <td className="px-4 py-3 dark:text-gray-300">{t.scanType}</td>
                  <td className="px-4 py-3"><StatusBadge status={t.status} /></td>
                  <td className="px-4 py-3"><ProgressBar value={t.progress} /></td>
                  <td className="px-4 py-3 text-center dark:text-gray-300">{t.totalAssets}</td>
                  <td className="px-4 py-3 text-xs text-gray-500 dark:text-gray-400">{t.createdAt ? dayjs(t.createdAt).format('YYYY-MM-DD HH:mm') : '-'}</td>
                  <td className="px-4 py-3">
                    <button onClick={() => { setDeleteId(t.id); setDeleteStatus(t.status) }} className="p-1 text-gray-400 hover:text-red-600 transition">
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {totalPages > 0 && (
        <div className="flex justify-center gap-2 mt-4">
          <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
            className="px-3 py-1 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300">上一页</button>
          <span className="px-3 py-1 text-sm text-gray-500 dark:text-gray-400">第 {page + 1}/{totalPages} 页</span>
          <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
            className="px-3 py-1 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300">下一页</button>
        </div>
      )}

      {/* Create Dialog */}
      {showCreate && (
        <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center" onClick={() => setShowCreate(false)}>
          <div className="bg-white dark:bg-gray-800 rounded-xl p-6 w-[500px]" onClick={e => e.stopPropagation()}>
            <div className="flex justify-between items-center mb-4">
              <h2 className="font-bold text-lg dark:text-white">新建扫描任务</h2>
              <button onClick={() => setShowCreate(false)}><X className="w-5 h-5" /></button>
            </div>
            <form onSubmit={e => {
              e.preventDefault()
              const fd = new FormData(e.target as HTMLFormElement)
              let target = (fd.get('targetRange') as string).trim()
              if (!validateTarget(target)) return

              // Auto-extract port from ip:port syntax
              const portMatch = target.match(/^(.*):(\d{1,5})$/)
              if (portMatch) {
                target = portMatch[1]
                fd.set('portRange', portMatch[2])
              }

              createMutation.mutate({
                name: fd.get('name') as string,
                targetRange: target,
                scanType: fd.get('scanType') as string,
                portRange: (fd.get('portRange') as string) || '1-1000',
                enableFingerprint: fd.get('enableFingerprint') === 'on',
                enableVulnScan: fd.get('enableVulnScan') === 'on',
              })
            }} className="space-y-3">
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">任务名称</label>
                <input name="name" required className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200" />
              </div>
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描目标 (IP/CIDR/域名)</label>
                <input name="targetRange" required placeholder="例：192.168.1.1 或 192.168.1.0/24 或 example.com"
                  onChange={e => validateTarget(e.target.value)}
                  className={`w-full px-3 py-2 border rounded-lg text-sm font-mono outline-none focus:ring-2 ${targetError ? 'border-red-400 focus:ring-red-500' : 'focus:ring-blue-500'}`} />
                {targetError && <p className="text-red-500 text-xs mt-1">{targetError}</p>}
                <p className="text-gray-400 dark:text-gray-500 text-xs mt-1">多个目标用逗号分隔；端口在下方单独设置（1-1000 为推荐范围）</p>
              </div>
              {/* Scan Preset */}
              <div>
                <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描模板预设</label>
                <div className="grid grid-cols-4 gap-2">
                  {[
                    { id: 'quick', label: 'Quick', desc: '主机发现', color: 'border-blue-300 bg-blue-50 text-blue-700' },
                    { id: 'stealth', label: 'Stealth', desc: '隐匿扫描', color: 'border-purple-300 bg-purple-50 text-purple-700' },
                    { id: 'web', label: 'Web', desc: 'Web 专用', color: 'border-green-300 bg-green-50 text-green-700' },
                    { id: 'full', label: 'Full', desc: '全端口+漏洞', color: 'border-red-300 bg-red-50 text-red-700' },
                  ].map(p => (
                    <label key={p.id} className={`cursor-pointer border rounded-lg p-2 text-center hover:shadow transition ${p.color}`}>
                      <input type="radio" name="preset" value={p.id} defaultChecked={p.id === 'quick'}
                        onChange={() => {
                          const form = document.querySelector('form')!
                          const scanType = form.querySelector<HTMLSelectElement>('select[name="scanType"]')!
                          const portRange = form.querySelector<HTMLInputElement>('input[name="portRange"]')!
                          const fingerCheck = form.querySelector<HTMLInputElement>('input[name="enableFingerprint"]')!
                          const vulnCheck = form.querySelector<HTMLInputElement>('input[name="enableVulnScan"]')!
                          if (p.id === 'quick') {
                            scanType.value = 'quick'; portRange.value = '1-1000';
                            fingerCheck.checked = true; vulnCheck.checked = false;
                          }
                          if (p.id === 'stealth') {
                            scanType.value = 'quick'; portRange.value = '22,80,443,3389';
                            fingerCheck.checked = false; vulnCheck.checked = false;
                          }
                          if (p.id === 'web') {
                            scanType.value = 'quick'; portRange.value = '80,443,8080,8443,3000,5000,7000,8000,8888';
                            fingerCheck.checked = true; vulnCheck.checked = true;
                          }
                          if (p.id === 'full') {
                            scanType.value = 'full'; portRange.value = '1-65535';
                            fingerCheck.checked = true; vulnCheck.checked = true;
                          }
                        }}
                        className="sr-only"
                      />
                      <p className="text-sm font-bold">{p.label}</p>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{p.desc}</p>
                    </label>
                  ))}
                </div>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-sm font-medium mb-1 dark:text-gray-300">扫描类型</label>
                  <select name="scanType" className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm outline-none bg-white dark:bg-gray-700 dark:text-gray-200">
                    <option value="quick">Quick (主机发现)</option>
                    <option value="full">Full (端口+版本+OS)</option>
                  </select>
                </div>
                <div>
                  <label className="block text-sm font-medium mb-1 dark:text-gray-300">端口范围</label>
                  <input name="portRange" defaultValue="1-1000" placeholder="1-1000"
                    className="w-full px-3 py-2 border rounded-lg text-sm font-mono outline-none focus:ring-2 focus:ring-blue-500" />
                </div>
              </div>
              <div className="flex gap-4 text-sm">
                <label className="flex items-center gap-1">
                  <input type="checkbox" name="enableFingerprint" defaultChecked /> 指纹识别
                </label>
                <label className="flex items-center gap-1">
                  <input type="checkbox" name="enableVulnScan" /> 漏洞扫描
                </label>
              </div>
              {createError && (
                <div className="flex items-center gap-2 p-2.5 bg-red-50 border border-red-200 rounded-lg text-red-600 text-sm">
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
    </div>
  )
}
