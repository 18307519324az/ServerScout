import { ExternalLink, Search, Shield } from 'lucide-react'

export default function AssetQuickActions() {
  return (
    <div className="rounded-xl border dark:border-gray-700 shadow-sm bg-white dark:bg-gray-800 p-4">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-gray-700 dark:text-gray-300">快捷操作 (Plugin)</h3>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 font-medium">PLUGIN</span>
      </div>
      <div className="flex gap-2">
        <a href="#" onClick={(e) => { e.preventDefault(); alert('Shodan 查询功能 - 可通过插件扩展实现') }}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-300">
          <ExternalLink className="w-3 h-3" /> Shodan
        </a>
        <a href="#" onClick={(e) => { e.preventDefault(); alert('Censys 查询功能 - 可通过插件扩展实现') }}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-300">
          <Search className="w-3 h-3" /> Censys
        </a>
        <a href="#" onClick={(e) => { e.preventDefault(); alert('VirusTotal 查询功能 - 可通过插件扩展实现') }}
          className="flex items-center gap-1 px-3 py-1.5 text-xs border rounded-lg hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-300">
          <Shield className="w-3 h-3" /> VirusTotal
        </a>
      </div>
      <p className="text-[10px] text-gray-400 mt-2">外部情报查询集成 — 插件可替换为真实 API 调用</p>
    </div>
  )
}
