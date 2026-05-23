import { useState } from 'react'
import { ExternalLink, Search, Shield, Loader2, Globe, AlertTriangle, Link2, X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { fetchAssetDetail, lookupCensysHost, lookupVirusTotalIp, lookupIpReputation } from '../../services/api'

interface ResultModal {
  title: string
  loading: boolean
  data: any
  error: string
}

export default function AssetQuickActions() {
  const { id } = useParams()
  const [modal, setModal] = useState<ResultModal | null>(null)

  const { data: assetData } = useQuery({
    queryKey: ['asset', id],
    queryFn: () => fetchAssetDetail(Number(id)),
    enabled: !!id,
  })

  const asset = assetData?.data?.data

  const runLookup = async (type: 'censys' | 'virustotal' | 'reputation') => {
    const ip = asset?.ipAddress
    if (!ip) return

    setModal({ title: getTitle(type), loading: true, data: null, error: '' })
    try {
      let res: any
      switch (type) {
        case 'censys':
          res = await lookupCensysHost(ip)
          break
        case 'virustotal':
          res = await lookupVirusTotalIp(ip)
          break
        case 'reputation':
          res = await lookupIpReputation(ip)
          break
      }
      setModal({ title: getTitle(type), loading: false, data: res?.data?.data || res?.data, error: '' })
    } catch (err: any) {
      setModal({ title: getTitle(type), loading: false, data: null, error: err?.response?.data?.message || '查询失败' })
    }
  }

  const getTitle = (type: string) => {
    switch (type) {
      case 'censys': return 'Censys 主机情报'
      case 'virustotal': return 'VirusTotal 安全检测'
      case 'reputation': return 'IP 信誉查询'
      default: return '查询结果'
    }
  }

  const getBadgeColor = (type: string) => {
    switch (type) {
      case 'censys': return 'text-blue-600 bg-blue-50 dark:bg-blue-900/30 dark:text-blue-400 border-blue-200 dark:border-blue-800'
      case 'virustotal': return 'text-green-600 bg-green-50 dark:bg-green-900/30 dark:text-green-400 border-green-200 dark:border-green-800'
      case 'reputation': return 'text-orange-600 bg-orange-50 dark:bg-orange-900/30 dark:text-orange-400 border-orange-200 dark:border-orange-800'
    }
  }

  return (
    <>
      <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
        <div className="flex items-center justify-between mb-3">
          <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">快捷操作 (Plugin)</h3>
          <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 font-medium">PLUGIN</span>
        </div>

        {asset ? (
          <div className="space-y-2">
            <p className="text-xs text-gray-500 dark:text-gray-400">
              目标: <code className="bg-gray-100 dark:bg-gray-700 px-1.5 py-0.5 rounded font-mono">{asset.ipAddress}</code>
              {asset.hostname && <span className="ml-1">({asset.hostname})</span>}
            </p>
            <div className="flex flex-wrap gap-2">
              <button onClick={() => runLookup('reputation')}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs border rounded-lg hover:shadow-sm transition-shadow ${getBadgeColor('reputation')}`}>
                <Shield className="w-3.5 h-3.5" /> IP 信誉
              </button>
              <button onClick={() => runLookup('censys')}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs border rounded-lg hover:shadow-sm transition-shadow ${getBadgeColor('censys')}`}>
                <Globe className="w-3.5 h-3.5" /> Censys
              </button>
              <button onClick={() => runLookup('virustotal')}
                className={`flex items-center gap-1.5 px-3 py-2 text-xs border rounded-lg hover:shadow-sm transition-shadow ${getBadgeColor('virustotal')}`}>
                <AlertTriangle className="w-3.5 h-3.5" /> VirusTotal
              </button>
            </div>
            <p className="text-[10px] text-gray-400 dark:text-gray-500 mt-1">
              点击按钮查询该资产的外部威胁情报
            </p>
          </div>
        ) : (
          <p className="text-xs text-gray-400 dark:text-gray-500">加载资产信息中...</p>
        )}
      </div>

      {/* Result Modal */}
      {modal && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50" onClick={() => setModal(null)}>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl w-full max-w-lg mx-4 max-h-[80vh] flex flex-col"
            onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-4 border-b dark:border-gray-700">
              <h3 className="font-semibold dark:text-white flex items-center gap-2">
                <Link2 className="w-4 h-4 text-purple-500" />
                {modal.title}
              </h3>
              <button onClick={() => setModal(null)}
                className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
                <X className="w-4 h-4 dark:text-gray-400" />
              </button>
            </div>
            <div className="p-4 overflow-y-auto flex-1">
              {modal.loading ? (
                <div className="flex items-center justify-center py-8">
                  <Loader2 className="w-6 h-6 animate-spin text-gray-400" />
                  <span className="ml-2 text-sm text-gray-500">查询中...</span>
                </div>
              ) : modal.error ? (
                <div className="text-center py-6 text-red-500 text-sm">
                  <AlertTriangle className="w-8 h-8 mx-auto mb-2 opacity-50" />
                  {modal.error}
                  <p className="text-xs text-gray-400 mt-1">请检查 API Key 是否已在系统设置中配置</p>
                </div>
              ) : (
                <pre className="text-xs font-mono bg-gray-50 dark:bg-gray-900 p-4 rounded-lg overflow-x-auto whitespace-pre-wrap dark:text-gray-300">
                  {JSON.stringify(modal.data, null, 2)}
                </pre>
              )}
            </div>
            <div className="p-3 border-t dark:border-gray-700 text-right">
              <button onClick={() => setModal(null)}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                关闭
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
