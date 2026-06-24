import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchCrawledUrlsByAsset } from '../services/api'
import type { CrawledUrl } from '../types'
import { Globe, Image, ExternalLink, Loader2, Camera, ChevronDown, ChevronRight, Clock, FileText } from 'lucide-react'

export default function CrawledUrlsPanel({ assetId, ipAddress }: { assetId: number; ipAddress: string }) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState<Set<number>>(new Set())
  const [screenshotViewer, setScreenshotViewer] = useState<string | null>(null)

  const { data, isLoading } = useQuery({
    queryKey: ['crawledUrls', assetId],
    queryFn: () => fetchCrawledUrlsByAsset(assetId),
    enabled: !!assetId,
  })

  const urls: CrawledUrl[] = data?.data?.data || []

  const screenshots = urls.filter(u => u.screenshotPath)
  const byDepth = new Map<number, CrawledUrl[]>()
  urls.forEach(u => {
    const d = u.crawlDepth || 0
    if (!byDepth.has(d)) byDepth.set(d, [])
    byDepth.get(d)!.push(u)
  })

  const toggle = (id: number) => {
    const next = new Set(expanded)
    if (next.has(id)) { next.delete(id) } else { next.add(id) }
    setExpanded(next)
  }

  const getStatusColor = (status: number) => {
    if (status >= 200 && status < 300) return 'text-green-600 dark:text-green-400'
    if (status >= 300 && status < 400) return 'text-blue-600 dark:text-blue-400'
    if (status >= 400 && status < 500) return 'text-orange-600 dark:text-orange-400'
    return 'text-red-600 dark:text-red-400'
  }

  return (
    <>
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-4 mb-6">
        <div className="flex items-center justify-between mb-3">
          <div className="flex items-center gap-2">
            <Globe className="w-5 h-5 text-teal-600" />
            <h2 className="font-bold text-lg dark:text-white">{t('crawler.title')}</h2>
            <span className="text-xs bg-teal-100 dark:bg-teal-900/30 text-teal-600 dark:text-teal-400 px-2 py-0.5 rounded">
              {urls.length} {t('crawler.pages')}
            </span>
          </div>
        </div>

        {isLoading ? (
          <div className="flex items-center justify-center py-8 text-gray-400">
            <Loader2 className="w-5 h-5 animate-spin mr-2" />
            <span className="text-sm">{t('crawler.loading')}</span>
          </div>
        ) : urls.length === 0 ? (
          /* Empty state — always visible */
          <div className="text-center py-8">
            <Globe className="w-10 h-10 mx-auto mb-2 text-gray-300 dark:text-gray-600" />
            <p className="text-sm text-gray-500 dark:text-gray-400 font-medium">{t('crawler.noData')}</p>
            <p className="text-xs text-gray-400 dark:text-gray-500 mt-1 max-w-md mx-auto">{t('crawler.noDataHint')}</p>
            <div className="mt-3 flex items-center justify-center gap-4 text-[10px] text-gray-400 dark:text-gray-500">
              <span className="flex items-center gap-1"><Camera className="w-3 h-3" /> {t('crawler.autoScreenshot')}</span>
              <span className="flex items-center gap-1"><Globe className="w-3 h-3" /> {t('crawler.pageCrawl')}</span>
              <span className="flex items-center gap-1"><FileText className="w-3 h-3" /> {t('crawler.contentExtract')}</span>
            </div>
          </div>
        ) : (
          <>
            {/* Screenshot Gallery (Goby-like) */}
            {screenshots.length > 0 && (
              <div className="mb-4">
                <h3 className="text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase mb-2 flex items-center gap-1">
                  <Camera className="w-3.5 h-3.5" /> {t('crawler.screenshots')}
                </h3>
                <div className="flex gap-2 overflow-x-auto pb-2">
                  {screenshots.map(u => (
                    <button
                      key={u.id}
                      onClick={() => setScreenshotViewer(u.screenshotPath)}
                      className="flex-shrink-0 border dark:border-gray-600 rounded-lg overflow-hidden hover:ring-2 hover:ring-blue-500 transition-all"
                    >
                      <div className="w-48 h-28 bg-gray-100 dark:bg-gray-700 flex items-center justify-center relative">
                        <Image className="w-8 h-8 text-gray-300 dark:text-gray-600" />
                        <span className="absolute bottom-0 left-0 right-0 bg-black/50 text-white text-[10px] px-2 py-0.5 truncate">
                          {u.httpStatus} · {u.title || u.path}
                        </span>
                      </div>
                      <p className="text-[10px] text-gray-500 dark:text-gray-400 px-2 py-1 truncate max-w-[192px]">
                        {u.url}
                      </p>
                    </button>
                  ))}
                </div>
              </div>
            )}

            {/* URL List */}
            <div className="space-y-1">
              {Array.from(byDepth.entries()).map(([depth, depthUrls]) => (
                <div key={depth}>
                  <h4 className="text-[10px] text-gray-400 dark:text-gray-500 font-semibold uppercase mt-2 mb-1 px-1">
                    {t('crawler.depth')} {depth} · {depthUrls.length} {t('crawler.pagesUnit')}
                  </h4>
                  {depthUrls.map(u => (
                    <div key={u.id} className="border dark:border-gray-700 rounded-lg">
                      <button
                        onClick={() => toggle(u.id)}
                        className="w-full flex items-center gap-2 px-3 py-2 hover:bg-gray-50 dark:hover:bg-gray-700/50 text-left"
                      >
                        {expanded.has(u.id) ? (
                          <ChevronDown className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                        ) : (
                          <ChevronRight className="w-3.5 h-3.5 text-gray-400 flex-shrink-0" />
                        )}
                        <span className={`text-xs font-mono font-semibold flex-shrink-0 ${getStatusColor(u.httpStatus)}`}>
                          {u.httpStatus || 'ERR'}
                        </span>
                        <span className="text-sm dark:text-gray-200 truncate flex-1">
                          {u.title || u.path}
                        </span>
                        <span className="text-[10px] text-gray-400 dark:text-gray-500 flex items-center gap-1 flex-shrink-0">
                          <Clock className="w-3 h-3" />
                          {u.responseTimeMs}ms
                        </span>
                        <a href={u.url} target="_blank" rel="noopener noreferrer"
                          onClick={(e) => e.stopPropagation()}
                          className="p-0.5 hover:text-blue-600 dark:hover:text-blue-400 text-gray-400 flex-shrink-0">
                          <ExternalLink className="w-3.5 h-3.5" />
                        </a>
                      </button>
                      {expanded.has(u.id) && (
                        <div className="px-3 pb-3 border-t dark:border-gray-700 bg-gray-50 dark:bg-gray-800/50">
                          <div className="grid grid-cols-2 gap-x-4 gap-y-1 mt-2 text-xs">
                            <div>
                              <span className="text-gray-400">{t('crawler.path')}: </span>
                              <code className="text-gray-700 dark:text-gray-300">{u.path}</code>
                            </div>
                            <div>
                              <span className="text-gray-400">{t('crawler.type')}: </span>
                              <span className="text-gray-600 dark:text-gray-400">{u.contentType || '-'}</span>
                            </div>
                            <div>
                              <span className="text-gray-400">{t('crawler.linksFound')}: </span>
                              <span className="text-gray-700 dark:text-gray-300">{u.linksFound}</span>
                            </div>
                            <div>
                              <span className="text-gray-400">{t('crawler.responseTime')}: </span>
                              <span className="text-gray-700 dark:text-gray-300">{u.responseTimeMs}ms</span>
                            </div>
                            {u.isDynamic && (
                              <div className="col-span-2">
                                <span className="px-1.5 py-0.5 bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded text-[10px]">
                                  {t('crawler.dynamicPage')}
                                </span>
                              </div>
                            )}
                          </div>
                          {u.bodyText && (
                            <div className="mt-2">
                              <p className="text-[10px] text-gray-400 mb-1 flex items-center gap-1">
                                <FileText className="w-3 h-3" /> {t('crawler.pageTextSummary')}
                              </p>
                              <p className="text-xs text-gray-600 dark:text-gray-400 bg-white dark:bg-gray-900 p-2 rounded border dark:border-gray-600 max-h-32 overflow-y-auto whitespace-pre-wrap">
                                {u.bodyText.length > 500 ? u.bodyText.substring(0, 500) + '...' : u.bodyText}
                              </p>
                            </div>
                          )}
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ))}
            </div>
          </>
        )}
      </div>

      {/* Screenshot Viewer Modal */}
      {screenshotViewer && (
        <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50"
          onClick={() => setScreenshotViewer(null)}>
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl max-w-4xl max-h-[90vh] mx-4 overflow-hidden"
            onClick={(e) => e.stopPropagation()}>
            <div className="flex items-center justify-between p-3 border-b dark:border-gray-700">
              <h3 className="font-semibold text-sm dark:text-white flex items-center gap-2">
                <Camera className="w-4 h-4" /> {t('crawler.viewScreenshot')}
              </h3>
              <button onClick={() => setScreenshotViewer(null)}
                className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700 text-gray-500">
                ✕
              </button>
            </div>
            <div className="p-2 bg-gray-100 dark:bg-gray-900 flex items-center justify-center">
              <div className="bg-white dark:bg-gray-800 border dark:border-gray-600 rounded overflow-hidden">
                <img
                  src={`/api/v1/screenshot/file/${screenshotViewer}`}
                  alt="Screenshot"
                  className="max-w-full max-h-[70vh] object-contain"
                  onError={(e) => {
                    (e.target as HTMLImageElement).src = 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="400" height="300"><rect fill="%23f1f5f9" width="400" height="300"/><text fill="%2394a3b8" x="200" y="150" text-anchor="middle" font-size="14">截图暂不可用</text></svg>'
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
