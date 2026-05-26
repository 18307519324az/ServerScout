import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchAssets, mergeAssets, deleteAsset } from '../services/api'
import { useToast } from '../hooks/useToast'
import { useDebounce } from '../hooks/useDebounce'
import StatusBadge from '../components/StatusBadge'
import Pagination from '../components/Pagination'
import ConfirmDialog from '../components/ConfirmDialog'
import { Search, GitMerge, CheckSquare, Square, Trash2, AlertTriangle } from 'lucide-react'
import dayjs from 'dayjs'

export default function AssetListPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(20)
  const [keyword, setKeyword] = useState('')
  const debouncedKeyword = useDebounce(keyword, 350)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [deleteId, setDeleteId] = useState<number | null>(null)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['assets', page, pageSize, debouncedKeyword],
    queryFn: () => fetchAssets({ page, size: pageSize, keyword: debouncedKeyword || undefined }),
  })

  const mergeMutation = useMutation({
    mutationFn: ({ sourceIds, targetId }: { sourceIds: number[], targetId: number }) =>
      mergeAssets(sourceIds, targetId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['assets'] })
      setSelected(new Set())
      toast.success(t('assets.mergeSuccess'))
    },
  })

  const deleteMutation = useMutation({
    mutationFn: deleteAsset,
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['assets'] }); setDeleteId(null); toast.success(t('assets.deleteSuccess')) },
    onError: (err: any) => { toast.error(err?.response?.data?.message || t('common.delete') + '失败') },
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
    if (confirm(t('assets.mergeConfirm').replace('{count}', String(sourceIds.length)).replace('{target}', ids[0].toString()))) {
      mergeMutation.mutate({ sourceIds, targetId })
    }
  }

  return (
    <div>
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-3">
        <div className="flex items-center gap-4">
          <h1 className="text-2xl font-bold dark:text-white">{t('assets.title')}</h1>
          <span className="text-sm text-gray-500 dark:text-gray-400">{t('assets.total').replace('{count}', String(totalElements))}</span>
        </div>
        <div className="flex items-center gap-3">
          {selected.size >= 2 && (
            <button
              onClick={handleMerge}
              disabled={mergeMutation.isPending}
              className="flex items-center gap-1.5 px-3 py-2 bg-orange-500 text-white rounded-lg text-sm hover:bg-orange-600 disabled:opacity-50"
            >
              <GitMerge className="w-4 h-4" />
              {t('assets.merge')} ({selected.size})
            </button>
          )}
          <div className="relative">
            <Search className="absolute left-3 top-2.5 w-4 h-4 text-gray-400 dark:text-gray-500" />
            <input placeholder={t('assets.searchPlaceholder')}
              className="pl-9 pr-3 py-2 border dark:border-gray-600 rounded-lg w-full sm:w-64 text-sm focus:ring-2 focus:ring-blue-500 outline-none bg-white dark:bg-gray-800 dark:text-gray-200"
              value={keyword} onChange={e => { setKeyword(e.target.value); setPage(0) }} />
          </div>
        </div>
      </div>

      {isLoading ? <div className="text-center py-20 text-gray-400 dark:text-gray-500">{t('common.loading')}</div> : (
        <>
          {/* Mobile card view */}
          <div className="md:hidden space-y-3">
            {assets.map((a: any) => (
              <div key={a.id} className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-4">
                <div className="flex items-center justify-between mb-2">
                  <Link to={`/assets/${a.id}`} className="text-blue-600 dark:text-blue-400 font-medium font-mono">
                    {a.ipAddress}
                  </Link>
                  <div className="flex items-center gap-1.5">
                    {a.isHoneypot && (
                      <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 text-xs rounded font-medium">
                        <AlertTriangle className="w-3 h-3" />
                        {t('assetDetail.suspectedHoneypot')}
                      </span>
                    )}
                    <StatusBadge status={a.status} />
                  </div>
                </div>
                <div className="text-xs text-gray-500 dark:text-gray-400 space-y-1">
                  <div className="flex justify-between"><span>{t('common.hostname')}</span><span className="dark:text-gray-300">{a.hostname || '-'}</span></div>
                  <div className="flex justify-between"><span>{t('common.openPorts')}</span><span className="font-mono">{a.openPortCount}</span></div>
                  <div className="flex justify-between"><span>{t('common.criticalVulns')}</span><span className={a.criticalVulnCount > 0 ? 'text-red-600 font-bold' : ''}>{a.criticalVulnCount}</span></div>
                  <div className="flex justify-between"><span>{t('common.lastScan')}</span><span>{a.lastScanTime ? dayjs(a.lastScanTime).format('MM-DD HH:mm') : '-'}</span></div>
                </div>
                <div className="flex justify-end mt-2">
                  <button onClick={() => setDeleteId(a.id)}
                    className="p-1 text-gray-400 hover:text-red-600">
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))}
          </div>

          <div className="hidden md:block bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm overflow-x-auto">
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
                  <th className="px-4 py-3">{t('common.ip')}</th>
                  <th className="px-4 py-3">{t('common.hostname')}</th>
                  <th className="px-4 py-3">{t('assets.os')}</th>
                  <th className="px-4 py-3">{t('common.status')}</th>
                  <th className="px-4 py-3 text-center">{t('common.openPorts')}</th>
                  <th className="px-4 py-3 text-center">{t('common.criticalVulns')}</th>
                  <th className="px-4 py-3">{t('common.lastScan')}</th>
                  <th className="px-4 py-3">{t('common.operation')}</th>
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
                      <div className="flex items-center gap-2">
                        <Link to={`/assets/${a.id}`} className="text-blue-600 dark:text-blue-400 hover:underline font-medium font-mono">
                          {a.ipAddress}
                        </Link>
                        {a.isHoneypot && (
                          <span className="inline-flex items-center gap-0.5 px-1.5 py-0.5 bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400 text-xs rounded font-medium" title={a.honeypotType}>
                            <AlertTriangle className="w-3 h-3" />
                            {t('assetDetail.suspectedHoneypot')}
                          </span>
                        )}
                      </div>
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

          <Pagination
            page={page}
            totalPages={totalPages}
            pageSize={pageSize}
            totalElements={totalElements}
            onPageChange={(p) => setPage(p)}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0) }}
          />
        </>
      )}

      <ConfirmDialog
        open={deleteId !== null}
        title={t('assets.deleteTitle')}
        message={t('assets.deleteMessage')}
        confirmLabel={t('common.delete')}
        variant="danger"
        onConfirm={() => deleteId && deleteMutation.mutate(deleteId)}
        onCancel={() => setDeleteId(null)}
      />
    </div>
  )
}
