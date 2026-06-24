import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import {
  AlertCircle,
  Bot,
  ClipboardList,
  Database,
  FileText,
  Lightbulb,
  Loader2,
  RefreshCw,
  ShieldCheck,
  Sparkles,
  Target,
  Wand2,
} from 'lucide-react'
import { fetchAssetDetail, fetchAssets, fetchScanTasks, fetchVulnerabilities, generateAiBriefing } from '../services/api'
import type { AiBriefingResult, Asset } from '../types'

const examples = {
  en: `Asset 203.0.113.10 exposes ports 22, 80, and 443.
Nginx 1.18.0 is running on port 443.
CVE-2021-23017 was detected with CVSS 7.5.
The service is internet-facing and supports customer login.`,
  zh: `资产 203.0.113.10 对外开放 22、80 和 443 端口。
443 端口运行 Nginx 1.18.0。
检测到 CVE-2021-23017，CVSS 评分为 7.5。
该服务面向互联网并承载用户登录功能。`,
}

function getRequestError(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.message || fallback
  }
  return fallback
}

export default function AiBriefingPage() {
  const { t, i18n } = useTranslation()
  const language = i18n.language.startsWith('zh') ? 'zh' : 'en'
  const [evidence, setEvidence] = useState(examples[language])
  const [result, setResult] = useState<AiBriefingResult | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [scanLoading, setScanLoading] = useState(false)

  useEffect(() => {
    const otherLanguage = language === 'zh' ? 'en' : 'zh'
    setEvidence((current) => current === examples[otherLanguage] ? examples[language] : current)
    setResult(null)
    setError('')
  }, [language])

  const generate = async () => {
    if (!evidence.trim()) {
      setError(t('aiBriefing.emptyError'))
      setResult(null)
      return
    }
    setLoading(true)
    setError('')
    try {
      const response = await generateAiBriefing(evidence, language)
      setResult(response.data.data)
    } catch (requestError) {
      setResult(null)
      setError(getRequestError(requestError, t('aiBriefing.requestError')))
    } finally {
      setLoading(false)
    }
  }

  const loadScanData = async () => {
    setScanLoading(true)
    setError('')
    try {
      // Step 1: fetch the most recent completed scan task
      const taskResponse = await fetchScanTasks({ status: 'completed', page: 0, size: 1 })
      const recentTask = taskResponse.data.data.content[0]
      if (!recentTask) {
        setError(t('aiBriefing.noRecentScan'))
        setScanLoading(false)
        return
      }

      // Step 2: fetch assets from that specific scan task
      const assetResponse = await fetchAssets({ page: 0, size: 5, taskId: recentTask.id })
      const assetPage = assetResponse.data.data
      const assets = assetPage.content

      // Step 3: fetch full detail for top 2 assets only (ports/services)
      const detailAssetIds = assets.slice(0, 2).map((a) => a.id)
      const detailResults = await Promise.allSettled(
        detailAssetIds.map((id) => fetchAssetDetail(id))
      )
      const detailedAssets: Asset[] = []
      for (const r of detailResults) {
        if (r.status === 'fulfilled') {
          detailedAssets.push(r.value.data.data)
        }
      }

      // Step 4: fetch vulnerabilities for top 4 assets
      const vulnAssetIds = assets.slice(0, 4).map((a) => a.id)
      let allVulnerabilities: Awaited<ReturnType<typeof fetchVulnerabilities>>['data']['data']['content'] = []
      if (vulnAssetIds.length > 0) {
        const vulnResults = await Promise.allSettled(
          vulnAssetIds.map((assetId) => fetchVulnerabilities({ page: 0, size: 3, assetId }))
        )
        const seen = new Set<number>()
        for (const result of vulnResults) {
          if (result.status === 'fulfilled') {
            for (const v of result.value.data.data.content) {
              if (!seen.has(v.id)) {
                seen.add(v.id)
                allVulnerabilities.push(v)
              }
            }
          }
        }
        const order: Record<string, number> = { critical: 0, high: 1, medium: 2, low: 3 }
        allVulnerabilities.sort((a, b) => (order[a.severity?.toLowerCase()] ?? 9) - (order[b.severity?.toLowerCase()] ?? 9))
      }

      // Step 5: build compact evidence text (target < 20000 chars)
      const lines: string[] = []
      const brief = (s: string | null | undefined, max: number) =>
        s ? (s.length > max ? s.slice(0, max) + '…' : s) : ''

      // ── Task header: one compact line ──
      const taskMeta = [recentTask.name, `#${recentTask.id}`, recentTask.scanType,
        recentTask.targetRange, recentTask.portRange, recentTask.completedAt,
        `assets:${assetPage.page.totalElements}`, recentTask.totalPorts ? `ports:${recentTask.totalPorts}` : '']
        .filter(Boolean).join(' | ')
      lines.push(`Scan: ${taskMeta}`)

      // ── Assets with port details ──
      const seenAs = new Set(detailAssetIds)
      for (const a of detailedAssets) {
        const hdr = [a.ipAddress, a.hostname, a.osFingerprint ? `OS:${a.osFingerprint}` : '', `status:${a.status}`]
          .filter(Boolean).join(' ')
        const portParts: string[] = []
        if (a.ports) {
          for (const p of a.ports) {
            const svc = [p.serviceName, p.serviceProduct, p.serviceVersion].filter(Boolean).join('/')
            const tags = [p.isWebService ? 'web' : '', p.state !== 'open' ? p.state : ''].filter(Boolean)
            let pLine = `${p.portNumber}/${p.protocol} ${svc || '?'}`
            if (tags.length) pLine += `[${tags.join(',')}]`
            if (p.sslCertificate) {
              pLine += ` SSL:${brief(p.sslCertificate.subject, 40)}`
              if (p.sslCertificate.isExpired) pLine += '(EXPIRED)'
            }
            if (p.banner) pLine += ` "${brief(p.banner, 50)}"`
            portParts.push(pLine)
          }
        }
        lines.push(`${hdr} (${a.openPortCount ?? 0}ports/${a.criticalVulnCount ?? 0}crit)`)
        if (portParts.length) lines.push(`  ports: ${portParts.join('; ')}`)
      }
      // Remaining assets: one-liner
      const others = assets.filter((a) => !seenAs.has(a.id))
      if (others.length) {
        lines.push(others.map((a) =>
          `${a.ipAddress}${a.hostname ? `(${a.hostname})` : ''}:${a.openPortCount ?? 0}p/${a.criticalVulnCount ?? 0}c`
        ).join(', '))
      }

      // ── Vulnerabilities: one compact line each ──
      if (allVulnerabilities.length > 0) {
        lines.push(`Vulns(${allVulnerabilities.length}):`)
        for (const v of allVulnerabilities) {
          const parts: string[] = []
          parts.push(v.cveId || '?')
          parts.push((v.severity ?? '?').toUpperCase())
          if (v.cvssScore != null) parts.push(`CVSS${v.cvssScore}`)
          parts.push(v.affectedAsset?.ipAddress || '?')
          if (v.affectedPort) parts.push(`:${v.affectedPort}`)
          if (v.affectedSoftware) {
            parts.push(v.affectedVersion ? `${v.affectedSoftware}@${v.affectedVersion}` : v.affectedSoftware)
          }
          if (v.fixSuggestion) parts.push(`→${brief(v.fixSuggestion, 60)}`)
          if (v.status && v.status !== 'open') parts.push(`[${v.status}]`)
          if (v.description) parts.push(`— ${brief(v.description, 80)}`)
          lines.push(parts.join(' '))
        }
      }

      setEvidence(lines.join('\n'))
      setResult(null)
    } catch (requestError) {
      setError(getRequestError(requestError, t('aiBriefing.scanLoadError')))
    } finally {
      setScanLoading(false)
    }
  }

  const reset = () => {
    setEvidence(examples[language])
    setResult(null)
    setError('')
  }

  return (
    <div className="space-y-6">
      <header className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div>
          <div className="flex items-center gap-2 text-sm font-semibold text-blue-600 dark:text-blue-400">
            <Bot className="h-4 w-4" />
            {t('aiBriefing.badge')}
          </div>
          <h1 className="mt-2 text-2xl font-bold text-gray-900 dark:text-white">{t('aiBriefing.title')}</h1>
          <p className="mt-2 max-w-3xl text-sm leading-6 text-gray-600 dark:text-gray-400">{t('aiBriefing.subtitle')}</p>
        </div>
        <div className="inline-flex items-center gap-2 self-start rounded-lg border border-blue-200 bg-blue-50 px-3 py-2 text-xs font-semibold text-blue-700 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-300">
          <ShieldCheck className="h-4 w-4" />
          {t('aiBriefing.inputDriven')}
        </div>
      </header>

      <div className="grid gap-6 xl:grid-cols-[0.86fr_1.14fr]">
        <section className="rounded-lg border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-700">
            <div>
              <h2 className="font-semibold text-gray-900 dark:text-white">{t('aiBriefing.evidenceTitle')}</h2>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{t('aiBriefing.evidenceHint')}</p>
            </div>
            <ClipboardList className="h-5 w-5 text-gray-400" />
          </div>
          <div className="space-y-4 p-5">
            <textarea
              value={evidence}
              onChange={(event) => setEvidence(event.target.value)}
              placeholder={t('aiBriefing.placeholder')}
              className="min-h-[360px] w-full resize-y rounded-lg border border-gray-200 bg-gray-50 px-3 py-3 font-mono text-sm leading-6 text-gray-800 outline-none transition focus:border-blue-500 focus:ring-2 focus:ring-blue-100 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-100 dark:focus:ring-blue-950"
            />
            <p className="text-right text-xs text-gray-400">{evidence.length} / 20000 {t('aiBriefing.chars')}</p>
            {error && (
              <div className="flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950/30 dark:text-red-300">
                <AlertCircle className="mt-0.5 h-4 w-4 flex-shrink-0" />
                <span>{error}</span>
              </div>
            )}
            <div className="flex flex-wrap gap-2">
              <button type="button" onClick={generate} disabled={loading || evidence.length > 20000} className="inline-flex items-center gap-2 rounded-lg bg-blue-600 px-4 py-2 text-sm font-semibold text-white transition hover:bg-blue-700 disabled:cursor-not-allowed disabled:opacity-60">
                {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Wand2 className="h-4 w-4" />}
                {loading ? t('aiBriefing.generating') : t('aiBriefing.generate')}
              </button>
              <button type="button" onClick={loadScanData} disabled={scanLoading} className="inline-flex items-center gap-2 rounded-lg border border-gray-200 bg-white px-4 py-2 text-sm font-semibold text-gray-700 transition hover:bg-gray-50 disabled:opacity-60 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-200 dark:hover:bg-gray-700">
                {scanLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Database className="h-4 w-4" />}
                {scanLoading ? t('aiBriefing.loadingScanData') : t('aiBriefing.loadScanData')}
              </button>
              <button type="button" onClick={reset} className="inline-flex items-center gap-2 rounded-lg bg-gray-100 px-4 py-2 text-sm font-semibold text-gray-700 transition hover:bg-gray-200 dark:bg-gray-700 dark:text-gray-200 dark:hover:bg-gray-600">
                <RefreshCw className="h-4 w-4" />
                {t('aiBriefing.reset')}
              </button>
            </div>
          </div>
        </section>

        <section className="rounded-lg border border-gray-200 bg-white shadow-sm dark:border-gray-700 dark:bg-gray-800">
          <div className="flex items-center justify-between border-b border-gray-200 px-5 py-4 dark:border-gray-700">
            <div>
              <h2 className="font-semibold text-gray-900 dark:text-white">{t('aiBriefing.outputTitle')}</h2>
              <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">{t('aiBriefing.outputHint')}</p>
            </div>
            <Sparkles className="h-5 w-5 text-blue-500" />
          </div>
          <div className="space-y-4 p-5">
            {!result ? (
              <div className="flex min-h-[360px] flex-col items-center justify-center rounded-lg border border-dashed border-gray-300 bg-gray-50 px-6 text-center dark:border-gray-700 dark:bg-gray-900">
                <Lightbulb className="h-10 w-10 text-blue-500" />
                <h3 className="mt-4 font-semibold text-gray-900 dark:text-white">{loading ? t('aiBriefing.generating') : t('aiBriefing.emptyTitle')}</h3>
                <p className="mt-2 max-w-md text-sm leading-6 text-gray-500 dark:text-gray-400">{t('aiBriefing.emptyDesc')}</p>
              </div>
            ) : (
              <>
                <div className="flex flex-wrap items-center gap-2">
                  <span className="text-xs font-semibold text-gray-500 dark:text-gray-400">{t('aiBriefing.modeLabel')}:</span>
                  <span className="rounded-full border border-blue-200 bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-700 dark:border-blue-900 dark:bg-blue-950/30 dark:text-blue-300">
                    {result.mode === 'llm' ? t('aiBriefing.modeLlm') : t('aiBriefing.modeLocal')}
                  </span>
                </div>
                {result.warnings.length > 0 && (
                  <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950/30 dark:text-amber-300">
                    {result.warnings.map((warning) => <p key={warning}>{warning}</p>)}
                  </div>
                )}
                {result.sections.map((section) => (
                  <article key={section.key} className="rounded-lg border border-gray-200 bg-gray-50 p-4 dark:border-gray-700 dark:bg-gray-900">
                    <h3 className="flex items-center gap-2 font-semibold text-gray-900 dark:text-white">
                      <FileText className="h-4 w-4 text-blue-500" />
                      {section.title}
                    </h3>
                    <p className="mt-2 whitespace-pre-line text-sm leading-6 text-gray-600 dark:text-gray-300">{section.body}</p>
                    {section.items.length > 0 && (
                      <ul className="mt-3 space-y-2 text-sm text-gray-600 dark:text-gray-300">
                        {section.items.map((item) => (
                          <li key={item} className="flex gap-2">
                            <Target className="mt-0.5 h-4 w-4 flex-shrink-0 text-green-500" />
                            <span>{item}</span>
                          </li>
                        ))}
                      </ul>
                    )}
                  </article>
                ))}
              </>
            )}
          </div>
        </section>
      </div>

      {result && (
        <section className="grid gap-6 lg:grid-cols-[1.25fr_0.75fr]">
          <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <h2 className="font-semibold text-gray-900 dark:text-white">{t('aiBriefing.inputSummary')}</h2>
            <p className="mt-3 whitespace-pre-line text-sm leading-6 text-gray-600 dark:text-gray-300">{result.inputSummary}</p>
          </div>
          <div className="rounded-lg border border-gray-200 bg-white p-5 shadow-sm dark:border-gray-700 dark:bg-gray-800">
            <h2 className="font-semibold text-gray-900 dark:text-white">{t('aiBriefing.detectedSignals')}</h2>
            <div className="mt-3 space-y-3">
              {Object.entries(result.detectedSignals).filter(([, values]) => values.length > 0).map(([key, values]) => (
                <div key={key}>
                  <p className="text-xs font-semibold uppercase text-gray-500 dark:text-gray-400">{t(`aiBriefing.signals.${key}`, { defaultValue: key })}</p>
                  <div className="mt-1 flex flex-wrap gap-1.5">
                    {values.map((value) => <span key={value} className="rounded-full border border-gray-200 bg-gray-50 px-2.5 py-1 text-xs text-gray-700 dark:border-gray-700 dark:bg-gray-900 dark:text-gray-300">{value}</span>)}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </section>
      )}
    </div>
  )
}
