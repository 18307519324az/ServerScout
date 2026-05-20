import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchScanTasks, createScanTask } from '../services/api'
import StatusBadge from '../components/StatusBadge'
import ConfirmDialog from '../components/ConfirmDialog'
import { Plus, Loader2 } from 'lucide-react'

export default function SettingsPage() {
  const [showCreate, setShowCreate] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const queryClient = useQueryClient()

  const { data: tasksData, isLoading } = useQuery({
    queryKey: ['scan-tasks'],
    queryFn: () => fetchScanTasks({ size: 20 }),
  })

  const tasks = tasksData?.data?.data?.content || []

  const [form, setForm] = useState({
    name: '',
    targetRange: '',
    scanType: 'quick',
    portRange: '1-1000',
    enableFingerprint: true,
    enableVulnScan: false,
  })

  const createMutation = useMutation({
    mutationFn: () => createScanTask(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setShowCreate(false)
      setForm({ name: '', targetRange: '', scanType: 'quick', portRange: '1-1000', enableFingerprint: true, enableVulnScan: false })
    },
  })

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">系统设置</h1>

      {/* Scan Config Section */}
      <div className="bg-white rounded-xl border shadow-sm p-6 mb-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="font-semibold">扫描配置</h2>
            <p className="text-sm text-gray-500">管理扫描参数模板和定期任务</p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
          >
            <Plus className="w-4 h-4" /> 新建扫描
          </button>
        </div>

        {showCreate && (
          <div className="border rounded-lg p-4 mb-4 bg-gray-50">
            <h3 className="font-medium mb-3">创建扫描任务</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">任务名称</label>
                <input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="例：内网资产巡检"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">扫描目标</label>
                <input
                  type="text"
                  value={form.targetRange}
                  onChange={(e) => setForm({ ...form, targetRange: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="例：192.168.1.0/24"
                />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">扫描类型</label>
                <select
                  value={form.scanType}
                  onChange={(e) => setForm({ ...form, scanType: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                >
                  <option value="quick">快速扫描 (Quick)</option>
                  <option value="full">全面扫描 (Full)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">端口范围</label>
                <input
                  type="text"
                  value={form.portRange}
                  onChange={(e) => setForm({ ...form, portRange: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                />
              </div>
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.enableFingerprint}
                    onChange={(e) => setForm({ ...form, enableFingerprint: e.target.checked })}
                    className="rounded"
                  />
                  启用指纹识别
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input
                    type="checkbox"
                    checked={form.enableVulnScan}
                    onChange={(e) => setForm({ ...form, enableVulnScan: e.target.checked })}
                    className="rounded"
                  />
                  启用漏洞扫描
                </label>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setShowCreate(false)} className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100">
                取消
              </button>
              <button
                onClick={() => setConfirmOpen(true)}
                disabled={!form.name || !form.targetRange || createMutation.isPending}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
              >
                {createMutation.isPending ? '创建中...' : '创建扫描'}
              </button>
            </div>
          </div>
        )}

        <div>
          <h3 className="text-sm font-medium text-gray-500 mb-2">最近扫描配置</h3>
          {isLoading ? (
            <div className="text-center py-6 text-gray-400"><Loader2 className="w-5 h-5 animate-spin mx-auto" /></div>
          ) : (
            <div className="space-y-1">
              {tasks.slice(0, 5).map((task: any) => (
                <div key={task.id} className="flex items-center justify-between px-3 py-2 rounded hover:bg-gray-50">
                  <div>
                    <span className="text-sm font-medium">{task.name}</span>
                    <span className="text-xs text-gray-400 ml-3">{task.targetRange}</span>
                  </div>
                  <StatusBadge status={task.status} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Placeholder for future settings */}
      <div className="bg-white rounded-xl border shadow-sm p-6">
        <h2 className="font-semibold mb-2">用户管理</h2>
        <p className="text-sm text-gray-400">用户管理和权限配置功能将在后续版本中实现。</p>
      </div>

      <ConfirmDialog
        open={confirmOpen}
        title="创建扫描任务"
        message={`确认创建扫描任务 "${form.name}"？目标范围：${form.targetRange}`}
        confirmLabel="确认创建"
        onConfirm={() => { setConfirmOpen(false); createMutation.mutate() }}
        onCancel={() => setConfirmOpen(false)}
      />
    </div>
  )
}