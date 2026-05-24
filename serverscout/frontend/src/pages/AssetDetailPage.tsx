import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchAssetDetail, fetchSubdomainsByAsset } from '../services/api'
import StatusBadge from '../components/StatusBadge'
import { ArrowLeft, Globe, Shield, Clock, Wifi, Cpu, Search, Server, AlertCircle, ShieldAlert, Info } from 'lucide-react'
import PluginSlot from '../components/PluginSlot'
import CrawledUrlsPanel from '../components/CrawledUrlsPanel'
import dayjs from 'dayjs'

export default function AssetDetailPage() {
  const { t } = useTranslation()
  const { id } = useParams()
  const navigate = useNavigate()
  const { data, isLoading } = useQuery({
    queryKey: ['asset', id],
    queryFn: () => fetchAssetDetail(Number(id)),
  })

  const asset = data?.data?.data

  // Fetch subdomains for this asset
  const { data: subData } = useQuery({
    queryKey: ['subdomains', 'asset', id],
    queryFn: () => fetchSubdomainsByAsset(Number(id)),
    enabled: !!id,
  })
  const subdomains = subData?.data?.data || []

  if (isLoading) return <div className="text-center py-20 text-gray-400">{t('assetDetail.loadingAsset')}</div>
  if (!asset) return <div className="text-center py-20 text-red-500">{t('assetDetail.assetNotFound')}</div>

  return (
    <div>
      <button onClick={() => navigate('/assets')}
        className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft className="w-4 h-4" /> {t('assetDetail.backToList')}
      </button>

      {/* Asset Info Card */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold font-mono">{asset.ipAddress}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-1">{asset.hostname || t('assetDetail.noHostname')}</p>
          </div>
          <div className="text-right text-sm text-gray-500 space-y-1">
            <p className="flex items-center gap-1.5 justify-end">
              <Cpu className="w-3.5 h-3.5" /> {t('assetDetail.osFingerprint')}: {asset.osFingerprint || t('assetDetail.unknown')}
            </p>
            <p>{t('assetDetail.discoveredAt')}: {asset.discoveredAt ? dayjs(asset.discoveredAt).format('YYYY-MM-DD HH:mm') : '-'}</p>
          </div>
        </div>

        {/* Stats row */}
        <div className="flex flex-wrap gap-3 mt-4">
          <div className="px-3 py-1.5 bg-blue-50 rounded-lg text-sm flex items-center gap-1.5">
            <Wifi className="w-3.5 h-3.5" /> {t('assetDetail.portsCount')}: {asset.openPortCount}
          </div>
          <div className={`px-3 py-1.5 rounded-lg text-sm ${
            asset.criticalVulnCount > 0 ? 'bg-red-50 text-red-700' : 'bg-gray-50'}`}>
            {t('assetDetail.criticalVulnsCount')}: {asset.criticalVulnCount}
          </div>
          {asset.macAddress && (
            <div className="px-3 py-1.5 bg-purple-50 rounded-lg text-sm font-mono">
              {t('assetDetail.macAddress')}: {asset.macAddress}
            </div>
          )}
          <div className="px-3 py-1.5 bg-gray-50 rounded-lg text-sm">
            {t('assetDetail.scanCountLabel')}: {asset.scanCount || 1}
          </div>
          {asset.lastScanTime && (
            <div className="px-3 py-1.5 bg-gray-50 rounded-lg text-sm flex items-center gap-1">
              <Clock className="w-3 h-3" />
              {t('assetDetail.lastScanLabel')}: {dayjs(asset.lastScanTime).format('YYYY-MM-DD HH:mm')}
            </div>
          )}
        </div>
      </div>

      {/* Honeypot Detection Section */}
      {asset.isHoneypot && (
        <div className="bg-red-50 dark:bg-red-900/20 border-2 border-red-300 dark:border-red-700 rounded-xl p-6 mb-6">
          <div className="flex items-center gap-2 mb-4">
            <ShieldAlert className="w-5 h-5 text-red-600 dark:text-red-400" />
            <h2 className="font-bold text-lg text-red-700 dark:text-red-400">{t('assetDetail.honeypotDetection')}</h2>
            <span className="px-2 py-0.5 bg-red-200 dark:bg-red-800 text-red-700 dark:text-red-300 text-xs rounded-full font-medium">
              {asset.honeypotConfidence}
            </span>
          </div>

          {asset.honeypotDetections && asset.honeypotDetections.length > 0 ? (
            <div className="space-y-3">
              {asset.honeypotDetections.map((d: any, i: number) => (
                <div key={i} className="bg-white dark:bg-gray-800 rounded-lg border border-red-200 dark:border-red-700 p-4">
                  <div className="grid grid-cols-2 md:grid-cols-4 gap-3 text-sm">
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.honeypotType')}</p>
                      <p className="font-semibold text-red-700 dark:text-red-400">{d.honeypotType}</p>
                    </div>
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.honeypotCategory')}</p>
                      <p className="font-medium dark:text-gray-200">{d.honeypotCategory || '-'}</p>
                    </div>
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.confidence')}</p>
                      <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${
                        d.confidence === 'HIGH' ? 'bg-red-100 dark:bg-red-900/40 text-red-700 dark:text-red-400' :
                        d.confidence === 'MEDIUM' ? 'bg-orange-100 dark:bg-orange-900/40 text-orange-700 dark:text-orange-400' :
                        'bg-yellow-100 dark:bg-yellow-900/40 text-yellow-700 dark:text-yellow-400'
                      }`}>{d.confidence}</span>
                    </div>
                    <div>
                      <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.detectionMethod')}</p>
                      <p className="font-medium dark:text-gray-200">{d.detectionMethod}</p>
                    </div>
                    {d.matchedPort && (
                      <div>
                        <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.matchedPort')}</p>
                        <p className="font-mono font-medium dark:text-gray-200">{d.matchedPort}</p>
                      </div>
                    )}
                    {d.ruleName && (
                      <div>
                        <p className="text-xs text-gray-500 dark:text-gray-400">{t('assetDetail.ruleName')}</p>
                        <p className="text-xs dark:text-gray-300">{d.ruleName}</p>
                      </div>
                    )}
                  </div>
                  {d.matchEvidence && (
                    <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-700">
                      <p className="text-xs text-gray-500 dark:text-gray-400 mb-1">{t('assetDetail.matchEvidence')}</p>
                      <pre className="text-xs text-gray-700 dark:text-gray-300 bg-gray-50 dark:bg-gray-900 p-2 rounded whitespace-pre-wrap break-all font-mono max-h-24 overflow-y-auto">
                        {d.matchEvidence}
                      </pre>
                    </div>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
              <Info className="w-4 h-4" />
              {t('assetDetail.noHoneypotDetections')}
            </div>
          )}
          <p className="text-xs text-gray-400 dark:text-gray-500 mt-3 flex items-center gap-1">
            <Info className="w-3 h-3" />
            {t('assetDetail.honeypotNote')}
          </p>
        </div>
      )}

      {/* L1 Plugin Slot: asset-detail-top */}
      <PluginSlot slot="asset-detail-top" />

      {/* Crawled URLs & Screenshots (Goby-like) */}
      <CrawledUrlsPanel assetId={asset.id} ipAddress={asset.ipAddress} />

      {/* Subdomain Section */}
      {subdomains.length > 0 && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
          <h2 className="font-bold text-lg mb-3 flex items-center gap-2">
            <Search className="w-5 h-5 text-blue-600" />
            {t('assetDetail.relatedSubdomains')} ({subdomains.length})
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
            {subdomains.map((sub: any) => (
              <div key={sub.id} className="border rounded-lg p-3 hover:bg-gray-50 transition-colors">
                <p className="font-mono text-sm font-medium text-gray-800 dark:text-gray-200 truncate" title={sub.subdomain}>
                  {sub.subdomain}
                </p>
                <div className="flex items-center gap-2 mt-1 text-xs text-gray-500">
                  {sub.ipAddress && (
                    <span className="font-mono bg-gray-100 px-1.5 py-0.5 rounded">{sub.ipAddress}</span>
                  )}
                  <span className="px-1.5 py-0.5 bg-blue-50 text-blue-600 rounded text-xs">
                    {sub.source}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Port List */}
      <h2 className="font-bold text-lg mb-3">
        {t('assetDetail.ports')} ({asset.ports?.length || 0})
      </h2>
      <div className="space-y-2">
        {asset.ports?.map((port: any) => (
          <div key={port.id} className="bg-white dark:bg-gray-800 rounded-lg border dark:border-gray-700 shadow-sm p-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-3">
                <span className="font-mono font-bold text-lg">{port.portNumber}</span>
                <span className="text-gray-400 text-sm">{port.protocol}</span>
                <StatusBadge status={port.state} />
                {port.isWebService && <Globe className="w-4 h-4 text-blue-500" />}
              </div>
              <div className="text-sm text-gray-600 font-medium">
                {port.serviceProduct || port.serviceName} {port.serviceVersion || ''}
              </div>
              {port.banner && (
                <div className="mt-2 text-xs text-gray-500 font-mono bg-gray-50 p-2 rounded truncate"
                     title={port.banner}>
                  {t('assetDetail.bannerLabel')}: {port.banner}
                </div>
              )}
            </div>

            {/* Web Fingerprint */}
            {port.webFingerprint && (
              <div className="mt-3 pl-4 border-l-2 border-blue-200 text-sm space-y-1">
                <div className="flex items-center gap-2 text-gray-700">
                  <span className="font-mono bg-gray-100 px-1.5 py-0.5 rounded text-xs">
                    {t('assetDetail.httpStatus')} {port.webFingerprint.httpStatus}
                  </span>
                  {port.webFingerprint.serverHeader && (
                    <span className="text-gray-500">{t('assetDetail.server')}: {port.webFingerprint.serverHeader}</span>
                  )}
                </div>
                {port.webFingerprint.title && (
                  <p className="text-gray-600">{t('assetDetail.title_cap')}: {port.webFingerprint.title}</p>
                )}
                {port.webFingerprint.cmsName && (
                  <p className="text-blue-600">
                    {t('assetDetail.cms')}: {port.webFingerprint.cmsName} {port.webFingerprint.cmsVersion || ''}
                  </p>
                )}
                {port.webFingerprint.frameworkName && (
                  <p className="text-purple-600">
                    {t('assetDetail.framework')}: {port.webFingerprint.frameworkName} {port.webFingerprint.frameworkVersion || ''}
                  </p>
                )}
                {port.webFingerprint.wafName && (
                  <p className="text-orange-600">{t('assetDetail.waf')}: {port.webFingerprint.wafName}</p>
                )}
                {port.webFingerprint.faviconHash && (
                  <p className="text-gray-400 text-xs font-mono">{t('assetDetail.favicon')}: {port.webFingerprint.faviconHash}</p>
                )}
                {port.webFingerprint.techStack && (
                  <div className="flex flex-wrap gap-1 mt-1">
                    {port.webFingerprint.techStack.split(',').map((t: string) => (
                      <span key={t} className="px-1.5 py-0.5 bg-blue-50 text-blue-700 rounded text-xs font-mono">
                        {t.trim()}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            )}

            {/* SSL Certificate */}
            {port.sslCertificate && (
              <div className="mt-3 pl-4 border-l-2 border-green-200 text-sm space-y-1">
                <div className="flex items-center gap-2">
                  <Shield className="w-3.5 h-3.5 text-green-600" />
                  <span className="font-medium text-gray-700">{t('assetDetail.sslCert')}</span>
                  {port.sslCertificate.isExpired && (
                    <span className="px-1.5 py-0.5 bg-red-100 text-red-600 rounded text-xs font-bold">{t('assetDetail.sslExpired')}</span>
                  )}
                </div>
                <p className="text-gray-600 truncate">{t('assetDetail.sslSubject')}: {port.sslCertificate.subject}</p>
                {port.sslCertificate.issuer && (
                  <p className="text-gray-600 truncate">{t('assetDetail.sslIssuer')}: {port.sslCertificate.issuer}</p>
                )}
                <div className="flex gap-3 text-gray-500 text-xs">
                  {port.sslCertificate.notBefore && (
                    <span>{dayjs(port.sslCertificate.notBefore).format('YYYY-MM-DD')} ~ {dayjs(port.sslCertificate.notAfter).format('YYYY-MM-DD')}</span>
                  )}
                  <span>{t('assetDetail.sslAlgorithm')}: {port.sslCertificate.sigAlg}</span>
                  <span>{t('assetDetail.sslKeySize')}: {port.sslCertificate.keySize} bit</span>
                </div>
                {port.sslCertificate.san && (
                  <p className="text-gray-500 text-xs truncate">{t('assetDetail.sslSan')}: {port.sslCertificate.san}</p>
                )}
                <p className="text-gray-400 text-xs font-mono">
                  {t('assetDetail.sslFingerprint')}: {port.sslCertificate.fingerprintSha256}
                </p>
              </div>
            )}

            {/* Vulnerabilities */}
            {port.vulnerabilities?.length > 0 && (
              <div className="mt-3 pl-4 border-l-2 border-red-200">
                {port.vulnerabilities.map((v: any, i: number) => (
                  <div key={i} className="text-sm flex items-center gap-2 py-0.5">
                    <a href={`https://nvd.nist.gov/vuln/detail/${v.cveId}`}
                      target="_blank" className="text-red-600 hover:underline font-mono text-xs">
                      {v.cveId}
                    </a>
                    <StatusBadge status={v.severity} />
                    <span className="text-gray-500 text-xs">{t('vulns.cvssScore')}: {v.cvssScore}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        {(!asset.ports || asset.ports.length === 0) && (
          <div className="text-center py-10 text-gray-400">{t('assetDetail.noPorts')}</div>
        )}
      </div>
    </div>
  )
}
