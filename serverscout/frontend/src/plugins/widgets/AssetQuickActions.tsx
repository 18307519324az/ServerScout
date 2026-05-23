import { ExternalLink, Search, Shield } from 'lucide-react'
import { useNavigate } from 'react-router-dom'

export default function AssetQuickActions() {
  const navigate = useNavigate()

  return (
    <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">快捷操作 (Plugin)</h3>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 font-medium">PLUGIN</span>
      </div>
      <div className="flex gap-2">
        <button onClick={() => navigate('/intel')}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <ExternalLink className="w-3 h-3" /> Shodan
        </button>
        <button onClick={() => navigate('/intel')}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <Search className="w-3 h-3" /> Censys
        </button>
        <button onClick={() => navigate('/intel')}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border dark:border-gray-600 rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <Shield className="w-3 h-3" /> VirusTotal
        </button>
      </div>
      <p className="text-[10px] text-gray-400 dark:text-gray-500 mt-2">前往外部情报页面查询 Shodan / Censys / VirusTotal</p>
    </div>
  )
}
