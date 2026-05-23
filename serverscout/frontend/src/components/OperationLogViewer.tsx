import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { fetchOperationLogs } from '../services/api'
import type { OperationLog } from '../types'
import { Clock, User, Globe, Activity, Search, ChevronLeft, ChevronRight, FileText, MapPin } from 'lucide-react'

const TYPE_LABELS: Record<string, { label: string; color: string }> = {
  LOGIN_SUCCESS: { label: '登录成功', color: 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' },
  LOGIN_FAILED: { label: '登录失败', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
  API_CALL: { label: 'API调用', color: 'bg-blue-100 text-blue-700 dark:bg-blue-900/30 dark:text-blue-400' },
  CREATE: { label: '创建', color: 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-400' },
  UPDATE: { label: '更新', color: 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-400' },
  DELETE: { label: '删除', color: 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400' },
}

export default function OperationLogViewer() {
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState({ username: '', type: '' })
  const [search, setSearch] = useState({ username: '', type: '' })

  const { data, isLoading } = useQuery({
    queryKey: ['operationLogs', page, search],
    queryFn: () => fetchOperationLogs({
      page, size: 15,
      username: search.username || undefined,
      type: search.type || undefined,
    }),
  })

  const logs: OperationLog[] = data?.data?.data?.content || []
  const totalPages = data?.data?.data?.page?.totalPages || 0
  const totalElements = data?.data?.data?.page?.totalElements || 0

  const getTypeBadge = (type: string) => {
    const info = TYPE_LABELS[type] || { label: type, color: 'bg-gray-100 text-gray-600 dark:bg-gray-700 dark:text-gray-300' }
    return <span className={`px-2 py-0.5 rounded text-xs font-medium ${info.color}`}>{info.label}</span>
  }

  const formatTime = (t: string) => {
    const d = new Date(t)
    return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' })
  }

  return (
    <div className="space-y-4">
      {/* Filter Bar */}
      <div className="flex flex-wrap gap-3 items-center">
        <div className="relative flex-1 min-w-[160px]">
          <User className="w-3.5 h-3.5 absolute left-2.5 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text" value={filter.username} placeholder="按用户名筛选"
            onChange={(e) => setFilter({ ...filter, username: e.target.value })}
            onKeyDown={(e) => { if (e.key === 'Enter') setSearch({ ...filter }) }}
            className="w-full pl-8 pr-3 py-2 border dark:border-gray-600 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200"
          />
        </div>
        <select value={filter.type}
          onChange={(e) => {
            setFilter({ ...filter, type: e.target.value })
            setSearch({ ...filter, type: e.target.value })
          }}
          className="px-3 py-2 border dark:border-gray-600 rounded-lg text-xs focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200">
          <option value="">全部类型</option>
          <option value="LOGIN_SUCCESS">登录成功</option>
          <option value="LOGIN_FAILED">登录失败</option>
          <option value="API_CALL">API调用</option>
        </select>
        <button onClick={() => setSearch({ ...filter })}
          className="flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-xs rounded-lg hover:bg-blue-700">
          <Search className="w-3.5 h-3.5" /> 查询
        </button>
        <span className="text-xs text-gray-400 dark:text-gray-500">
          共 {totalElements} 条记录
        </span>
      </div>

      {/* Log Table */}
      {isLoading ? (
        <div className="text-center py-10 text-gray-400">加载中...</div>
      ) : logs.length === 0 ? (
        <div className="text-center py-10 text-gray-400 dark:text-gray-500">
          <FileText className="w-8 h-8 mx-auto mb-2 opacity-50" />
          <p className="text-sm">暂无操作日志</p>
        </div>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-xs dark:text-gray-300">
            <thead>
              <tr className="border-b dark:border-gray-600 text-gray-500 dark:text-gray-400 uppercase text-[10px]">
                <th className="text-left py-2 pl-3 w-16">类型</th>
                <th className="text-left py-2 w-20">用户</th>
                <th className="text-left py-2 w-36">操作目标</th>
                <th className="text-left py-2 w-40">IP 地址 / 归属地</th>
                <th className="text-left py-2 w-32">时间</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id} className="border-b dark:border-gray-700 last:border-0 hover:bg-gray-50 dark:hover:bg-gray-700/50">
                  <td className="py-2 pl-3">{getTypeBadge(log.operationType)}</td>
                  <td className="py-2 font-medium dark:text-gray-200">{log.username}</td>
                  <td className="py-2 text-gray-500 dark:text-gray-400">
                    <div className="flex items-center gap-1">
                      <Activity className="w-3 h-3" />
                      <span className="truncate max-w-[180px]" title={log.target}>
                        {log.target || log.requestUri || '-'}
                      </span>
                    </div>
                    {log.detail && <p className="text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">{log.detail}</p>}
                  </td>
                  <td className="py-2">
                    <div className="text-gray-500 dark:text-gray-400">
                      <div className="flex items-center gap-1">
                        <Globe className="w-3 h-3" />
                        <code className="text-[11px]">{log.ipAddress || '-'}</code>
                      </div>
                      {log.geoLocation && (
                        <div className="flex items-center gap-1 text-[10px] text-gray-400 dark:text-gray-500 mt-0.5">
                          <MapPin className="w-3 h-3" />
                          <span>{log.geoLocation}</span>
                        </div>
                      )}
                    </div>
                  </td>
                  <td className="py-2 text-gray-400 dark:text-gray-500">
                    <div className="flex items-center gap-1">
                      <Clock className="w-3 h-3" />
                      {formatTime(log.createdAt)}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between text-xs text-gray-500 dark:text-gray-400 pt-2">
          <span>第 {page + 1} / {totalPages} 页</span>
          <div className="flex gap-1">
            <button onClick={() => setPage(Math.max(0, page - 1))} disabled={page === 0}
              className="p-1 rounded border dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30">
              <ChevronLeft className="w-4 h-4" />
            </button>
            <button onClick={() => setPage(Math.min(totalPages - 1, page + 1))} disabled={page >= totalPages - 1}
              className="p-1 rounded border dark:border-gray-600 hover:bg-gray-100 dark:hover:bg-gray-700 disabled:opacity-30">
              <ChevronRight className="w-4 h-4" />
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
