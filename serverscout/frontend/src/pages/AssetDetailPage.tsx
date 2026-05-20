import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { fetchAssetDetail, fetchSubdomainsByAsset } from '../services/api'
import StatusBadge from '../components/StatusBadge'
import { ArrowLeft, Globe, Shield, Clock, Wifi, Cpu, Search, Server, AlertCircle } from 'lucide-react'
import dayjs from 'dayjs'

export default function AssetDetailPage() {
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

  if (isLoading) return <div className="text-center py-20 text-gray-400">加载中...</div>
  if (!asset) return <div className="text-center py-20 text-red-500">资产不存在</div>

  return (
    <div>
      <button onClick={() => navigate('/assets')}
        className="flex items-center gap-1 text-sm text-gray-500 hover:text-gray-700 mb-4">
        <ArrowLeft className="w-4 h-4" /> 返回资产列表
      </button>

      {/* Asset Info Card */}
      <div className="bg-white rounded-xl border shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h1 className="text-2xl font-bold font-mono">{asset.ipAddress}</h1>
            <p className="text-gray-500 mt-1">{asset.hostname || '无主机名'}</p>
          </div>
          <div className="text-right text-sm text-gray-500 space-y-1">
            <p className="flex items-center gap-1.5 justify-end">
              <Cpu className="w-3.5 h-3.5" /> OS: {asset.osFingerprint || '未知'}
            </p>
            <p>发现时间: {asset.discoveredAt ? dayjs(asset.discoveredAt).format('YYYY-MM-DD HH:mm') : '-'}</p>
          </div>
        </div>

        {/* Stats row */}
        <div className="flex flex-wrap gap-3 mt-4">
          <div className="px-3 py-1.5 bg-blue-50 rounded-lg text-sm flex items-center gap-1.5">
            <Wifi className="w-3.5 h-3.5" /> 端口: {asset.openPortCount}
          </div>
          <div className={`px-3 py-1.5 rounded-lg text-sm ${
            asset.criticalVulnCount > 0 ? 'bg-red-50 text-red-700' : 'bg-gray-50'}`}>
            高危漏洞: {asset.criticalVulnCount}
          </div>
          {asset.macAddress && (
            <div className="px-3 py-1.5 bg-purple-50 rounded-lg text-sm font-mono">
              MAC: {asset.macAddress}
            </div>
          )}
          <div className="px-3 py-1.5 bg-gray-50 rounded-lg text-sm">
            扫描次数: {asset.scanCount || 1}
          </div>
          {asset.lastScanTime && (
            <div className="px-3 py-1.5 bg-gray-50 rounded-lg text-sm flex items-center gap-1">
              <Clock className="w-3 h-3" />
              最近扫描: {dayjs(asset.lastScanTime).format('YYYY-MM-DD HH:mm')}
            </div>
          )}
        </div>
      </div>

      {/* Subdomain Section */}
      {subdomains.length > 0 && (
        <div className="bg-white rounded-xl border shadow-sm p-6 mb-6">
          <h2 className="font-bold text-lg mb-3 flex items-center gap-2">
            <Search className="w-5 h-5 text-blue-600" />
            关联子域名 ({subdomains.length})
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-2">
            {subdomains.map((sub: any) => (
              <div key={sub.id} className="border rounded-lg p-3 hover:bg-gray-50 transition-colors">
                <p className="font-mono text-sm font-medium text-gray-800 truncate" title={sub.subdomain}>
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
        端口列表 ({asset.ports?.length || 0})
      </h2>
      <div className="space-y-2">
        {asset.ports?.map((port: any) => (
          <div key={port.id} className="bg-white rounded-lg border shadow-sm p-4">
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
                  Banner: {port.banner}
                </div>
              )}
            </div>

            {/* Web Fingerprint */}
            {port.webFingerprint && (
              <div className="mt-3 pl-4 border-l-2 border-blue-200 text-sm space-y-1">
                <div className="flex items-center gap-2 text-gray-700">
                  <span className="font-mono bg-gray-100 px-1.5 py-0.5 rounded text-xs">
                    HTTP {port.webFingerprint.httpStatus}
                  </span>
                  {port.webFingerprint.serverHeader && (
                    <span className="text-gray-500">Server: {port.webFingerprint.serverHeader}</span>
                  )}
                </div>
                {port.webFingerprint.title && (
                  <p className="text-gray-600">Title: {port.webFingerprint.title}</p>
                )}
                {port.webFingerprint.cmsName && (
                  <p className="text-blue-600">
                    CMS: {port.webFingerprint.cmsName} {port.webFingerprint.cmsVersion || ''}
                  </p>
                )}
                {port.webFingerprint.frameworkName && (
                  <p className="text-purple-600">
                    框架: {port.webFingerprint.frameworkName} {port.webFingerprint.frameworkVersion || ''}
                  </p>
                )}
                {port.webFingerprint.wafName && (
                  <p className="text-orange-600">WAF: {port.webFingerprint.wafName}</p>
                )}
                {port.webFingerprint.faviconHash && (
                  <p className="text-gray-400 text-xs font-mono">Favicon: {port.webFingerprint.faviconHash}</p>
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
                  <span className="font-medium text-gray-700">SSL 证书</span>
                  {port.sslCertificate.isExpired && (
                    <span className="px-1.5 py-0.5 bg-red-100 text-red-600 rounded text-xs font-bold">已过期</span>
                  )}
                </div>
                <p className="text-gray-600 truncate">Subject: {port.sslCertificate.subject}</p>
                {port.sslCertificate.issuer && (
                  <p className="text-gray-600 truncate">颁发者: {port.sslCertificate.issuer}</p>
                )}
                <div className="flex gap-3 text-gray-500 text-xs">
                  {port.sslCertificate.notBefore && (
                    <span>{dayjs(port.sslCertificate.notBefore).format('YYYY-MM-DD')} ~ {dayjs(port.sslCertificate.notAfter).format('YYYY-MM-DD')}</span>
                  )}
                  <span>算法: {port.sslCertificate.sigAlg}</span>
                  <span>密钥: {port.sslCertificate.keySize} bit</span>
                </div>
                {port.sslCertificate.san && (
                  <p className="text-gray-500 text-xs truncate">SAN: {port.sslCertificate.san}</p>
                )}
                <p className="text-gray-400 text-xs font-mono">
                  SHA-256: {port.sslCertificate.fingerprintSha256}
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
                    <span className="text-gray-500 text-xs">CVSS {v.cvssScore}</span>
                  </div>
                ))}
              </div>
            )}
          </div>
        ))}
        {(!asset.ports || asset.ports.length === 0) && (
          <div className="text-center py-10 text-gray-400">暂无端口数据</div>
        )}
      </div>
    </div>
  )
}
