import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { fetchAssets, mergeAssets, deleteAsset } from '../services/api'
import { useToast } from '../hooks/useToast'
import StatusBadge from '../components/StatusBadge'
import ConfirmDialog from '../components/ConfirmDialog'
import { Search, GitMerge, CheckSquare, Square, Trash2 } from 'lucide-react'
import dayjs from 'dayjs'

export default function AssetListPage() {
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['assets', page, keyword],
    queryFn: () => fetchAssets({ page, size: 20, keyword: keyword || undefined }),
  })

  const mergeMutation = useMutation({
    mutationFn: ({ sourceIds, targetId }: { sourceIds: number[], targetId: number }) =>
      mergeAssets(sourceIds, targetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] })
      setSelected(new Set())
      toast.success('资产合并成功')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAsset,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['assets'] }); setDeleteId(null); toast.success('资产已删除') },
    onError: (err: any) => { toast.error(err?.response?.data?.message || '删除失败') },
  })

  const assets = data?.data?.data?.content ?? []
  const totalPages = data?.data?.data?.page?.totalPages ?? 0
  const totalElements = data?.data?.data?.page?.totalElements ?? 0

  const toggleSelect = (id: number) => {
    const next = new Set(selected)
    if (next.has(id)) { next.delete(id) } else { next.add(id) }
    setSelected(next)
  }

  const toggleAll = () => {
    if (selected.size === assets.length) {
      setSelected(new Set())
    } else {
      setSelected(new Set(assets.map((a: any) => a.id)))
    }
  }

  const handleMerge = () => {
    if (selected.size < 2) return
    const ids = Array.from(selected)
    const targetId = ids[0]
    const sourceIds = ids.slice(1)
    if (confirm(`合并 ${sourceIds.length} 个资产到 ${ids[0]}？此操作不可撤销。`)) {
      mergeMutation.mutate({ sourceIds, targetId })
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <h1 className="text-2xl font-bold dark:text-white">资产管理</h1>
          <span className="text-sm text-gray-500 dark:text-gray-400">共 {totalElements} 项</span>
        </div>
        <div className="flex items-center gap-3">
          {selected.size >= 2 && (
            <button
              onClick={handleMerge}
              disabled={mergeMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-2 bg-orange-500 text-white rounded-lg text-sm hover:bg-orange-600 disabled:opacity-50"
            >
              <GitMerge className="w-4 h-4" />
              合并选中 ({selected.size})
            </button>
          )}
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-gray-400 dark:text-gray-500" />
            <input placeholder="搜索 IP/主机名..."
              className="pl-9 pr-3 py-2 border dark:border-gray-600 rounded-lg w-64 text-sm focus:ring-2 focus:ring-blue-500 outline-none bg-white dark:bg-gray-800 dark:text-gray-200"
              value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0) }} />
          </div>
        </div>
      </div>

      {isLoading ? <div className="text-center py-20 text-gray-400 dark:text-gray-500">加载中...</div> : (
        <>
          <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 dark:bg-gray-700 text-left text-sm text-gray-500 dark:text-gray-400">
                <tr>
                  <th className="px-2 py-3 w-10">
                    <button onClick={toggleAll} className="p-0.5 hover:bg-gray-200 dark:hover:bg-gray-600 rounded">
                      {selected.size === assets.length && assets.length > 0
                        ? <CheckSquare className="w-4 h-4 text-blue-600" />
                        : <Square className="w-4 h-4 text-gray-400 dark:text-gray-500" />}
                    </button>
                  </th>
                  <th className="px-4 py-3">IP</th>
                  <th className="px-4 py-3">主机名</th>
                  <th className="px-4 py-3">OS</th>
                  <th className="px-4 py-3">状态</th>
                  <th className="px-4 py-3 text-center">开放端口</th>
                  <th className="px-4 py-3 text-center">高危漏洞</th>
                  <th className="px-4 py-3">最近扫描</th>
                  <th className="px-4 py-3">操作</th>
                </tr>
              </thead>
              <tbody className="text-sm">
                {assets.map((a: any) => (
                  <tr key={a.id} className="border-t dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700">
                    <td className="px-2 py-3">
                      <button onClick={() => toggleSelect(a.id)} className="p-0.5 hover:bg-gray-200 dark:hover:bg-gray-600 rounded">
                        {selected.has(a.id)
                          ? <CheckSquare className="w-4 h-4 text-blue-600" />
                          : <Square className="w-4 h-4 text-gray-400 dark:text-gray-500" />}
                      </button>
                    </td>
                    <td className="px-4 py-3">
                      <Link to={`/assets/${a.id}`} className="text-blue-600 dark:text-blue-400 hover:underline font-medium font-mono">
                        {a.ipAddress}
                      </Link>
                    </td>
                    <td className="px-4 py-3 text-gray-600 dark:text-gray-300">{a.hostname || '-'}</td>
                    <td className="px-4 py-3 text-xs text-gray-700 dark:text-gray-200">{a.osFingerprint || '-'}</td>
                    <td className="px-4 py-3"><StatusBadge status={a.status} /></td>
                    <td className="px-4 py-3 text-center font-mono text-gray-800 dark:text-gray-200">{a.openPortCount}</td>
                    <td className="px-4 py-3 text-center">
                      <span className={a.criticalVulnCount > 0 ? 'text-red-600 font-bold' : 'text-gray-500 dark:text-gray-400'}>
                        {a.criticalVulnCount}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-gray-500 dark:text-gray-400 text-xs">
                      {a.lastScanTime ? dayjs(a.lastScanTime).format('MM-DD HH:mm') : '-'}
                    </td>
                    <td className="px-4 py-3">
                      <button onClick={() => setDeleteId(a.id)}
                        className="p-1 text-gray-400 dark:text-gray-500 hover:text-red-600 transition">
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 0 && (
            <div className="flex justify-center gap-2 mt-4">
              <button disabled={page === 0} onClick={() => setPage(p => p - 1)}
                className="px-3 py-1 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300">上一页</button>
              <span className="px-3 py-1 text-sm text-gray-500 dark:text-gray-400">第 {page + 1}/{totalPages} 页</span>
              <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}
                className="px-3 py-1 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300">下一页</button>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={deleteId !== null}
        title="删除资产"
        message="删除后该资产及其端口、漏洞关联数据将被移除，确定继续？"
        confirmLabel="删除"
        variant="danger"
        onConfirm={() => deleteId && deleteMutation.mutate(deleteId)}
        onCancel={() => setDeleteId(null)}
      />
    </div>
  )
}
