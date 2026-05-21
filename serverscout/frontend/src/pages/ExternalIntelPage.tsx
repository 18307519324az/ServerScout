import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import {
  lookupIpIntel, lookupCveDetails, searchCvesExternal, getLatestCves,
  lookupDomainIntel, getEpssScore, getCombinedReport,
} from '../services/api'
import { Search, Globe, ExternalLink, AlertTriangle, Info, ChevronDown, ChevronRight, Loader2, Server, Shield, Bug, ArrowRight, FileSearch, Zap } from 'lucide-react'

type TabKey = 'ip' | 'cve' | 'domain' | 'combined'

const EXT_LINKS = {
  shodan: (ip: string) => `https://www.shodan.io/host/${ip}`,
  censys: (ip: string) => `https://search.censys.io/hosts/${ip}`,
  nvd: (cveId: string) => `https://nvd.nist.gov/vuln/detail/${cveId}`,
  crtsh: (domain: string) => `https://crt.sh/?q=${domain}`,
  securitytrails: (domain: string) => `https://securitytrails.com/domain/${domain}`,
  urlscan: (domain: string) => `https://urlscan.io/search/#${domain}`,
  mitre: (cveId: string) => `https://cve.mitre.org/cgi-bin/cvename.cgi?name=${cveId}`,
}

export default function ExternalIntelPage() {
  const [tab, setTab] = useState<TabKey>('ip')
  const [ipInput, setIpInput] = useState('')
  const [cveInput, setCveInput] = useState('')
  const [cveSearchKeyword, setCveSearchKeyword] = useState('')
  const [domainInput, setDomainInput] = useState('')
  const [lookupKey, setLookupKey] = useState('')
  const [cveLookupKey, setCveLookupKey] = useState('')
  const [cveSearchKey, setCveSearchKey] = useState('')
  const [domainLookupKey, setDomainLookupKey] = useState('')
  const [expandedVulns, setExpandedVulns] = useState<Set<string>>(new Set())

  const { data: ipData, isLoading: ipLoading, isError: ipError } = useQuery({
    queryKey: ['intel-ip', lookupKey],
    queryFn: () => lookupIpIntel(lookupKey),
    enabled: !!lookupKey,
  })

  const { data: cveData, isLoading: cveLoading } = useQuery({
    queryKey: ['intel-cve', cveLookupKey],
    queryFn: () => lookupCveDetails(cveLookupKey),
    enabled: !!cveLookupKey,
  })

  const { data: cveSearchData, isLoading: cveSearchLoading } = useQuery({
    queryKey: ['intel-cve-search', cveSearchKey],
    queryFn: () => searchCvesExternal({ keyword: cveSearchKey, size: 20 }),
    enabled: !!cveSearchKey,
  })

  const { data: latestCves } = useQuery({
    queryKey: ['intel-latest-cves'],
    queryFn: () => getLatestCves(20),
  })

  const { data: domainData, isLoading: domainLoading } = useQuery({
    queryKey: ['intel-domain', domainLookupKey],
    queryFn: () => lookupDomainIntel(domainLookupKey),
    enabled: !!domainLookupKey,
  })

  const handleLookup = {
    ip: () => { if (ipInput.trim()) setLookupKey(ipInput.trim()) },
    cve: () => { if (cveInput.trim()) setCveLookupKey(cveInput.trim().toUpperCase()) },
    domain: () => { if (domainInput.trim()) setDomainLookupKey(domainInput.trim()) },
    cveSearch: () => { if (cveSearchKeyword.trim()) setCveSearchKey(cveSearchKeyword.trim()) },
  }

  const toggleVuln = (cveId: string) => {
    const next = new Set(expandedVulns)
    if (next.has(cveId)) next.delete(cveId); else next.add(cveId)
    setExpandedVulns(next)
  }

  const severityColor = (s: string) => {
    switch (s?.toLowerCase()) {
      case 'critical': return 'text-red-700 bg-red-50 border-red-200'
      case 'high': return 'text-orange-700 bg-orange-50 border-orange-200'
      case 'medium': return 'text-yellow-700 bg-yellow-50 border-yellow-200'
      case 'low': return 'text-green-700 bg-green-50 border-green-200'
      default: return 'text-gray-600 bg-gray-100 border-gray-200'
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold">外部威胁情报</h1>
          <p className="text-sm text-gray-500 mt-1">
            集成 Shodan InternetDB &middot; NVD CVE &middot; AlienVault OTX &middot; URLScan &middot; EPSS
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6 bg-gray-100 dark:bg-gray-800 rounded-lg p-1 w-fit flex-wrap">
        {[
          { key: 'ip' as TabKey, label: 'IP 情报', icon: Server, desc: 'Shodan + 本地资产' },
          { key: 'cve' as TabKey, label: 'CVE 查询', icon: Bug, desc: 'NVD + EPSS' },
          { key: 'domain' as TabKey, label: '域名情报', icon: Globe, desc: 'OTX + URLScan' },
          { key: 'combined' as TabKey, label: '综合报告', icon: FileSearch, desc: '一键聚合查询' },
        ].map(t => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`px-4 py-2 rounded-md text-sm font-medium transition ${
              tab === t.key ? 'bg-white dark:bg-gray-700 shadow text-blue-700 dark:text-blue-400' : 'text-gray-600 dark:text-gray-400 hover:text-gray-900 dark:hover:text-gray-200'
            }`}
          >
            <span className="flex items-center gap-1.5">
              <t.icon className="w-3.5 h-3.5" />
              {t.label}
            </span>
            <span className="block text-xs text-gray-400 dark:text-gray-500 font-normal">{t.desc}</span>
          </button>
        ))}
      </div>

      {/* ==================== IP Intelligence ==================== */}
      {tab === 'ip' && (
        <div>
          <div className="bg-white rounded-xl border p-6 shadow-sm mb-6">
            <div className="flex gap-3 mb-3">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
                <input
                  placeholder="输入 IP 地址，如 1.1.1.1 或 8.8.8.8"
                  className="pl-9 pr-3 py-2.5 border rounded-lg w-full text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                  value={ipInput}
                  onChange={e => setIpInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleLookup.ip()}
                />
              </div>
              <button
                onClick={handleLookup.ip}
                disabled={ipLoading}
                className="px-6 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
              >
                {ipLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Globe className="w-4 h-4" />}
                查询
              </button>
            </div>
            <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-700">
              <Info className="w-3.5 h-3.5 mt-0.5 shrink-0" />
              <div>
                <p className="font-medium mb-0.5">与「资产管理」的关系</p>
                <p>此处查询 Shodan 互联网公开情报（外部视角），与 Nmap 主动扫描（内网/本机视角）互补。
                  Shodan 数据库仅覆盖互联网可达的 IP，内网 IP 或未被 Shodan 抓取的地址将<b>自动回退到本地资产库</b>查询。
                  <Link to="/scan-tasks" className="text-blue-600 hover:underline ml-1">前往扫描任务 →</Link>
                </p>
              </div>
            </div>
          </div>

          {ipError && (
            <div className="p-4 bg-red-50 border border-red-200 rounded-lg text-red-700 text-sm mb-4">
              查询失败，请检查 IP 地址格式
            </div>
          )}

          {ipData?.data?.data && <IpResultCard data={ipData.data.data} expandedVulns={expandedVulns} toggleVuln={toggleVuln} severityColor={severityColor} />}
        </div>
      )}

      {/* ==================== CVE Query ==================== */}
      {tab === 'cve' && (
        <div>
          <div className="bg-white rounded-xl border p-6 shadow-sm mb-6">
            <h3 className="font-semibold text-sm mb-3">CVE 详情查询</h3>
            <div className="flex gap-3">
              <input
                placeholder="输入 CVE 编号，如 CVE-2021-44228"
                className="px-3 py-2.5 border rounded-lg flex-1 text-sm font-mono focus:ring-2 focus:ring-blue-500 outline-none"
                value={cveInput}
                onChange={e => setCveInput(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleLookup.cve()}
              />
              <button
                onClick={handleLookup.cve}
                disabled={cveLoading}
                className="px-6 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
              >
                {cveLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                查询
              </button>
            </div>
          </div>

          {cveData?.data?.data && <CveDetailCard data={cveData.data.data} severityColor={severityColor} />}

          {/* CVE Search */}
          <div className="bg-white rounded-xl border p-6 shadow-sm mb-6">
            <h3 className="font-semibold text-sm mb-3">CVE 关键词搜索</h3>
            <div className="flex gap-3">
              <input
                placeholder="搜索关键词，如 Apache、Spring、WordPress..."
                className="px-3 py-2.5 border rounded-lg flex-1 text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                value={cveSearchKeyword}
                onChange={e => setCveSearchKeyword(e.target.value)}
                onKeyDown={e => e.key === 'Enter' && handleLookup.cveSearch()}
              />
              <button
                onClick={handleLookup.cveSearch}
                disabled={cveSearchLoading}
                className="px-6 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
              >
                {cveSearchLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Search className="w-4 h-4" />}
                搜索
              </button>
            </div>
          </div>

          {cveSearchData?.data?.data && <CveSearchResults data={cveSearchData.data.data} severityColor={severityColor} />}

          {/* Latest CVEs */}
          <div className="bg-white rounded-xl border shadow-sm overflow-hidden">
            <div className="px-6 py-4 border-b bg-gray-50">
              <h3 className="font-semibold text-sm">最近 30 天新发布的 CVE</h3>
            </div>
            <div className="divide-y">
              {latestCves?.data?.data?.slice(0, 10).map((cve: any) => (
                <div key={cve.cveId} className="px-6 py-3 flex items-center gap-4 hover:bg-gray-50">
                  <a
                    href={EXT_LINKS.nvd(cve.cveId)}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-mono text-sm text-blue-600 hover:underline w-40"
                  >
                    {cve.cveId}
                    <ExternalLink className="w-3 h-3 inline ml-1" />
                  </a>
                  <span className={`px-2 py-0.5 rounded text-xs border font-medium ${severityColor(cve.severity)}`}>
                    {cve.severity || 'N/A'}
                  </span>
                  <span className="text-sm text-gray-600 truncate flex-1">{cve.description}</span>
                  {cve.cvssScore && <span className="font-mono text-sm text-gray-500">CVSS {cve.cvssScore}</span>}
                </div>
              ))}
            </div>
          </div>
        </div>
      )}

      {/* ==================== Domain Intelligence ==================== */}
      {tab === 'domain' && (
        <div>
          <div className="bg-white rounded-xl border p-6 shadow-sm mb-6">
            <div className="flex gap-3 mb-3">
              <div className="relative flex-1">
                <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
                <input
                  placeholder="输入域名，如 example.com"
                  className="pl-9 pr-3 py-2.5 border rounded-lg w-full text-sm focus:ring-2 focus:ring-blue-500 outline-none"
                  value={domainInput}
                  onChange={e => setDomainInput(e.target.value)}
                  onKeyDown={e => e.key === 'Enter' && handleLookup.domain()}
                />
              </div>
              <button
                onClick={handleLookup.domain}
                disabled={domainLoading}
                className="px-6 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 disabled:opacity-50 flex items-center gap-2"
              >
                {domainLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Globe className="w-4 h-4" />}
                查询
              </button>
            </div>
            <div className="flex items-start gap-2 p-3 bg-blue-50 border border-blue-200 rounded-lg text-xs text-blue-700">
              <Info className="w-3.5 h-3.5 mt-0.5 shrink-0" />
              <div>
                <p className="font-medium mb-0.5">与「资产管理」的关系</p>
                <p>域名情报通过 OTX 和 URLScan 提供被动 DNS 数据，与主动扫描互补。
                  查询结果中的子域名可作为扫描目标添加到扫描任务中。
                  <Link to="/scan-tasks" className="text-blue-600 hover:underline ml-1">新建扫描 →</Link>
                </p>
              </div>
            </div>
          </div>

          {domainData?.data?.data && <DomainResultCard data={domainData.data.data} />}
        </div>
      )}

      {/* ==================== Combined Report ==================== */}
      {tab === 'combined' && <CombinedReportTab />}
    </div>
  )
}

// ==================== IP Result Card ====================
function IpResultCard({ data, expandedVulns, toggleVuln, severityColor }: any) {
  const source = data.source || 'internetdb'
  const hasExternalData = data.ports?.length > 0 || data.hostnames?.length > 0 || data.cpes?.length > 0
  const hasLocalData = !!data.localAssetId

  return (
    <div className="bg-white rounded-xl border shadow-sm overflow-hidden">
      {/* Source badge */}
      <div className="px-6 py-4 border-b bg-gray-50 flex items-center justify-between">
        <h3 className="font-semibold flex items-center gap-2">
          IP 情报: <span className="font-mono">{data.ip}</span>
          <a href={EXT_LINKS.shodan(data.ip)} target="_blank" rel="noopener noreferrer"
            className="text-blue-600 hover:text-blue-800" title="在 Shodan 中查看">
            <ExternalLink className="w-4 h-4" />
          </a>
          <a href={EXT_LINKS.censys(data.ip)} target="_blank" rel="noopener noreferrer"
            className="text-gray-400 hover:text-gray-600" title="在 Censys 中查看">
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </h3>
        <div className="flex items-center gap-2">
          {source === 'local' && (
            <span className="px-2 py-0.5 bg-amber-100 text-amber-700 rounded text-xs font-medium">本地资产</span>
          )}
          {source === 'internetdb' && (
            <span className="px-2 py-0.5 bg-green-100 text-green-700 rounded text-xs font-medium">Shodan InternetDB</span>
          )}
          {source === 'none' && (
            <span className="px-2 py-0.5 bg-red-100 text-red-700 rounded text-xs font-medium">未收录</span>
          )}
        </div>
      </div>

      {/* Empty state from Shodan */}
      {!hasExternalData && source !== 'local' && (
        <div className="p-6">
          <div className="flex items-start gap-3 p-4 bg-amber-50 border border-amber-200 rounded-lg">
            <AlertTriangle className="w-5 h-5 text-amber-500 shrink-0 mt-0.5" />
            <div>
              <p className="text-sm font-medium text-amber-800 mb-1">Shodan InternetDB 未收录此 IP</p>
              <p className="text-xs text-amber-700 mb-3">
                Shodan InternetDB 仅收录互联网可达的 IP。此 IP 可能不在 Shodan 覆盖范围内，或属于内网地址。
                {hasLocalData && ' 但本地资产库中已有此 IP 的记录。'}
              </p>
              <div className="flex flex-wrap gap-2">
                <Link to={`/assets/${data.localAssetId}`}
                  className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 inline-flex items-center gap-1">
                  <Server className="w-3 h-3" /> 查看本地资产
                </Link>
                <Link to="/scan-tasks"
                  className="px-3 py-1.5 bg-amber-600 text-white rounded text-xs hover:bg-amber-700 inline-flex items-center gap-1">
                  <ArrowRight className="w-3 h-3" /> 新建扫描任务
                </Link>
                <a href={EXT_LINKS.shodan(data.ip)} target="_blank" rel="noopener noreferrer"
                  className="px-3 py-1.5 border border-gray-300 text-gray-700 rounded text-xs hover:bg-gray-50 inline-flex items-center gap-1">
                  <ExternalLink className="w-3 h-3" /> Shodan 查看
                </a>
                <a href={EXT_LINKS.censys(data.ip)} target="_blank" rel="noopener noreferrer"
                  className="px-3 py-1.5 border border-gray-300 text-gray-700 rounded text-xs hover:bg-gray-50 inline-flex items-center gap-1">
                  <ExternalLink className="w-3 h-3" /> Censys 查看
                </a>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Stats */}
      <div className="p-6 grid grid-cols-2 md:grid-cols-4 gap-4">
        <StatBox label="开放端口" value={data.ports?.length || 0} />
        <StatBox label="漏洞数" value={data.vulns?.length || 0} color="text-red-600" />
        <StatBox label="主机名" value={data.hostnames?.length || 0} />
        <StatBox label="标签/CPE" value={(data.tags?.length || 0) + (data.cpes?.length || 0)} />
      </div>

      {/* Cross-reference local asset */}
      {hasLocalData && source === 'internetdb' && (
        <div className="px-6 py-3 border-t bg-blue-50">
          <div className="flex items-center gap-2 text-sm">
            <Server className="w-4 h-4 text-blue-600" />
            <span className="text-blue-700">
              此 IP 已在本地资产库中。
              <Link to={`/assets/${data.localAssetId}`} className="font-medium hover:underline ml-1">
                查看资产详情 →
              </Link>
            </span>
          </div>
        </div>
      )}

      {/* Local asset details */}
      {source === 'local' && data.localPortDetails && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2 flex items-center gap-2">
            <Server className="w-3.5 h-3.5" /> 本地扫描结果
          </h4>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-3 mb-3">
            <div className="text-xs"><span className="text-gray-500">主机名:</span> <span className="font-mono">{data.localHostname || '-'}</span></div>
            <div className="text-xs"><span className="text-gray-500">状态:</span> <span className="font-mono">{data.localStatus || '-'}</span></div>
            <div className="text-xs"><span className="text-gray-500">开放端口:</span> <span className="font-mono">{data.localOpenPorts}</span></div>
            <div className="text-xs"><span className="text-gray-500">严重漏洞:</span> <span className="font-mono text-red-600">{data.localCriticalVulns}</span></div>
          </div>
          <div className="flex flex-wrap gap-2 mb-3">
            {data.ports.map((p: string) => (
              <span key={p} className="px-2 py-0.5 bg-blue-50 text-blue-700 rounded text-xs font-mono">{p}</span>
            ))}
          </div>
          {data.localPortDetails?.length > 0 && (
            <div className="max-h-40 overflow-y-auto border rounded">
              <table className="w-full text-xs">
                <thead className="bg-gray-50 text-gray-500">
                  <tr>
                    <th className="text-left py-1.5 px-2">端口</th>
                    <th className="text-left py-1.5 px-2">协议</th>
                    <th className="text-left py-1.5 px-2">服务</th>
                    <th className="text-left py-1.5 px-2">版本</th>
                  </tr>
                </thead>
                <tbody>
                  {data.localPortDetails.map((pd: any, i: number) => (
                    <tr key={i} className="border-t">
                      <td className="py-1 px-2 font-mono">{pd.port}</td>
                      <td className="py-1 px-2">{pd.protocol}</td>
                      <td className="py-1 px-2">{pd.service || '-'}</td>
                      <td className="py-1 px-2 text-gray-500">{pd.version || '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="flex flex-wrap gap-2 mt-3">
            <Link to={`/assets/${data.localAssetId}`}
              className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 inline-flex items-center gap-1">
              <Server className="w-3 h-3" /> 查看资产详情
            </Link>
            <Link to="/scan-tasks"
              className="px-3 py-1.5 bg-green-600 text-white rounded text-xs hover:bg-green-700 inline-flex items-center gap-1">
              <Globe className="w-3 h-3" /> 重新扫描
            </Link>
          </div>
        </div>
      )}

      {/* External ports */}
      {data.ports?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">开放端口 (Shodan)</h4>
          <div className="flex flex-wrap gap-2">
            {data.ports.map((p: string) => (
              <span key={p} className="px-3 py-1 bg-blue-50 text-blue-700 rounded text-sm font-mono">{p}</span>
            ))}
          </div>
        </div>
      )}

      {/* Hostnames */}
      {data.hostnames?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">关联主机名</h4>
          <div className="space-y-1">
            {data.hostnames.map((h: string) => (
              <div key={h} className="text-sm text-gray-600 font-mono">{h}</div>
            ))}
          </div>
        </div>
      )}

      {/* CVEs */}
      {data.vulnDetails?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">漏洞列表 (Shodan)</h4>
          <div className="space-y-2">
            {data.vulnDetails.map((v: any) => (
              <div key={v.cveId} className="border rounded-lg">
                <button
                  onClick={() => toggleVuln(v.cveId)}
                  className="w-full flex items-center justify-between px-3 py-2 text-sm hover:bg-gray-50"
                >
                  <div className="flex items-center gap-3">
                    <span className="font-mono text-blue-600">{v.cveId}</span>
                    <span className={`px-2 py-0.5 rounded text-xs border font-medium ${severityColor(v.severity)}`}>
                      {v.severity || 'N/A'}
                    </span>
                    {v.cvssScore && <span className="text-gray-500 font-mono">CVSS {v.cvssScore}</span>}
                  </div>
                  {expandedVulns.has(v.cveId) ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
                </button>
                {expandedVulns.has(v.cveId) && (
                  <div className="px-3 py-3 border-t bg-gray-50 text-sm text-gray-600">
                    {v.description && <p className="mb-2">{v.description}</p>}
                    {v.vectorString && <p className="text-xs text-gray-400">Vector: {v.vectorString}</p>}
                    <a
                      href={EXT_LINKS.nvd(v.cveId)}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-blue-600 hover:underline text-xs inline-flex items-center gap-1 mt-1"
                    >
                      <ExternalLink className="w-3 h-3" /> NVD 详情
                    </a>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}

      {/* CPEs */}
      {data.cpes?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">CPE 标识</h4>
          <div className="space-y-1">
            {data.cpes.map((c: string) => (
              <div key={c} className="text-xs text-gray-500 font-mono">{c}</div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== CVE Detail Card ====================
function CveDetailCard({ data, severityColor }: any) {
  return (
    <div className="bg-white rounded-xl border shadow-sm overflow-hidden mb-6">
      <div className="px-6 py-4 border-b bg-gray-50 flex items-center justify-between">
        <h3 className="font-semibold flex items-center gap-2">
          {data.cveId}
          <a href={EXT_LINKS.nvd(data.cveId)} target="_blank" rel="noopener noreferrer"
            className="text-blue-600 hover:text-blue-800" title="在 NVD 中查看">
            <ExternalLink className="w-4 h-4" />
          </a>
        </h3>
        <div className="flex items-center gap-2">
          {data.severity && (
            <span className={`px-2 py-0.5 rounded text-xs border font-medium ${severityColor(data.severity)}`}>
              {data.severity}
            </span>
          )}
          {data.cvssScore && <span className="font-mono text-sm font-bold text-gray-700">CVSS {data.cvssScore}</span>}
        </div>
      </div>
      <div className="p-6">
        <p className="text-sm text-gray-700 mb-4">{data.description}</p>

        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm mb-4">
          {data.published && <InfoRow label="发布日期" value={data.published?.slice(0, 10)} />}
          {data.attackVector && <InfoRow label="攻击向量" value={data.attackVector} />}
          {data.exploitabilityScore > 0 && <InfoRow label="可利用性" value={data.exploitabilityScore.toFixed(1)} />}
          {data.impactScore > 0 && <InfoRow label="影响评分" value={data.impactScore.toFixed(1)} />}
        </div>

        {data.cwes?.length > 0 && (
          <div className="mb-4">
            <h4 className="text-sm font-semibold mb-1">弱点枚举 (CWE)</h4>
            <div className="flex flex-wrap gap-1">
              {[...new Set<string>(data.cwes as string[])].map((cwe: string) => (
                <span key={cwe} className="px-2 py-0.5 bg-gray-100 rounded text-xs text-gray-600">{cwe}</span>
              ))}
            </div>
          </div>
        )}

        {data.affectedProducts?.length > 0 && (
          <div className="mb-4">
            <h4 className="text-sm font-semibold mb-1">受影响产品</h4>
            <div className="flex flex-wrap gap-1">
              {data.affectedProducts.slice(0, 20).map((p: string) => (
                <span key={p} className="px-2 py-0.5 bg-amber-50 rounded text-xs text-amber-700 font-mono">{p}</span>
              ))}
              {data.affectedProducts.length > 20 && (
                <span className="px-2 py-0.5 text-xs text-gray-400">+{data.affectedProducts.length - 20} 更多</span>
              )}
            </div>
          </div>
        )}

        {data.references?.length > 0 && (
          <div className="mb-4">
            <h4 className="text-sm font-semibold mb-1">参考链接</h4>
            <div className="space-y-1 max-h-40 overflow-y-auto">
              {data.references.slice(0, 10).map((ref: any, i: number) => (
                <a key={i} href={ref.url} target="_blank" rel="noopener noreferrer"
                  className="block text-xs text-blue-600 hover:underline truncate">
                  <ExternalLink className="w-3 h-3 inline mr-1" />
                  {ref.url}
                </a>
              ))}
            </div>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          <a href={EXT_LINKS.nvd(data.cveId)} target="_blank" rel="noopener noreferrer"
            className="px-3 py-1.5 bg-blue-600 text-white rounded text-xs hover:bg-blue-700 inline-flex items-center gap-1">
            <ExternalLink className="w-3 h-3" /> 在 NVD 查看完整信息
          </a>
        </div>
      </div>
    </div>
  )
}

// ==================== CVE Search Results ====================
function CveSearchResults({ data, severityColor }: any) {
  return (
    <div className="bg-white rounded-xl border shadow-sm overflow-hidden mb-6">
      <div className="px-6 py-4 border-b bg-gray-50">
        <h3 className="font-semibold text-sm">搜索结果 ({data.totalResults} 条)</h3>
      </div>
      <div className="divide-y">
        {data.content?.map((cve: any) => (
          <div key={cve.cveId} className="px-6 py-3 flex items-center gap-4 hover:bg-gray-50">
            <a href={EXT_LINKS.nvd(cve.cveId)} target="_blank" rel="noopener noreferrer"
              className="font-mono text-sm text-blue-600 hover:underline w-40">
              {cve.cveId}
              <ExternalLink className="w-3 h-3 inline ml-1" />
            </a>
            <span className={`px-2 py-0.5 rounded text-xs border font-medium ${severityColor(cve.severity)}`}>
              {cve.severity || 'N/A'}
            </span>
            <span className="text-sm text-gray-600 truncate flex-1">{cve.description}</span>
            {cve.cvssScore && <span className="font-mono text-sm text-gray-500">CVSS {cve.cvssScore}</span>}
          </div>
        ))}
        {(!data.content || data.content.length === 0) && (
          <div className="px-6 py-12 text-center text-gray-400 text-sm">未找到匹配的 CVE，请尝试其他关键词</div>
        )}
      </div>
    </div>
  )
}

// ==================== Domain Result Card ====================
function DomainResultCard({ data }: any) {
  return (
    <div className="bg-white rounded-xl border shadow-sm overflow-hidden">
      <div className="px-6 py-4 border-b bg-gray-50 flex items-center justify-between">
        <h3 className="font-semibold flex items-center gap-2">
          域名情报: <span className="font-mono">{data.domain}</span>
          <a href={EXT_LINKS.crtsh(data.domain)} target="_blank" rel="noopener noreferrer"
            className="text-blue-600 hover:text-blue-800" title="在 crt.sh 中查看 SSL 证书透明度日志">
            <ExternalLink className="w-4 h-4" />
          </a>
          <a href={EXT_LINKS.urlscan(data.domain)} target="_blank" rel="noopener noreferrer"
            className="text-gray-400 hover:text-gray-600" title="在 URLScan.io 中查看">
            <ExternalLink className="w-3.5 h-3.5" />
          </a>
        </h3>
        <div className="flex gap-2">
          <a href={EXT_LINKS.crtsh(data.domain)} target="_blank" rel="noopener noreferrer"
            className="px-2 py-1 bg-blue-50 text-blue-700 rounded text-xs hover:bg-blue-100 inline-flex items-center gap-1">
            <ExternalLink className="w-3 h-3" /> crt.sh
          </a>
          <a href={EXT_LINKS.securitytrails(data.domain)} target="_blank" rel="noopener noreferrer"
            className="px-2 py-1 bg-gray-100 text-gray-600 rounded text-xs hover:bg-gray-200 inline-flex items-center gap-1">
            <ExternalLink className="w-3 h-3" /> SecurityTrails
          </a>
        </div>
      </div>

      <div className="p-6 grid grid-cols-2 md:grid-cols-3 gap-4">
        <StatBox label="Passive DNS 记录" value={data.passiveDnsCount || 0} />
        <StatBox label="发现的子域名" value={data.subdomains?.length || 0} />
        <StatBox label="URLScan 结果" value={data.urlScanResults?.length || 0} />
      </div>

      {data.subdomains?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">子域名列表 (OTX)</h4>
          <div className="grid grid-cols-2 md:grid-cols-3 gap-1 max-h-60 overflow-y-auto">
            {data.subdomains.map((s: string) => (
              <a key={s}
                href={`https://${s}`}
                target="_blank"
                rel="noopener noreferrer"
                className="text-xs text-gray-600 font-mono px-2 py-1 bg-gray-50 rounded truncate hover:bg-blue-50 hover:text-blue-600"
                title={`打开 ${s}`}
              >
                {s}
              </a>
            ))}
          </div>
        </div>
      )}

      {data.passiveDnsRecords?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">DNS 解析记录 (OTX)</h4>
          <div className="max-h-60 overflow-y-auto">
            <table className="w-full text-xs">
              <thead className="text-gray-500 border-b">
                <tr>
                  <th className="text-left py-1.5">主机名</th>
                  <th className="text-left py-1.5">IP 地址</th>
                  <th className="text-left py-1.5">记录类型</th>
                </tr>
              </thead>
              <tbody>
                {data.passiveDnsRecords.slice(0, 50).map((r: any, i: number) => (
                  <tr key={i} className="border-b last:border-0">
                    <td className="py-1.5 font-mono">
                      <a href={`https://${r.hostname}`} target="_blank" rel="noopener noreferrer"
                        className="text-blue-600 hover:underline">{r.hostname}</a>
                    </td>
                    <td className="py-1.5 font-mono">{r.ip || '-'}</td>
                    <td className="py-1.5">{r.recordType}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {data.urlScanResults?.length > 0 && (
        <div className="px-6 py-3 border-t">
          <h4 className="text-sm font-semibold mb-2">URLScan.io 结果</h4>
          <div className="space-y-2 max-h-80 overflow-y-auto">
            {data.urlScanResults.map((r: any, i: number) => (
              <div key={i} className="border rounded p-3 text-sm">
                <a href={r.url} target="_blank" rel="noopener noreferrer"
                  className="font-mono text-blue-600 hover:underline truncate block">
                  {r.url}
                  <ExternalLink className="w-3 h-3 inline ml-1" />
                </a>
                <div className="text-gray-500 text-xs mt-1">{r.host}</div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Empty state */}
      {!data.subdomains?.length && !data.passiveDnsRecords?.length && !data.urlScanResults?.length && (
        <div className="p-6 text-center text-gray-400 text-sm">
          未找到该域名的情报数据
        </div>
      )}
    </div>
  )
}

// ==================== Helpers ====================
function StatBox({ label, value, color }: { label: string; value: number; color?: string }) {
  return (
    <div className="bg-gray-50 rounded-lg p-3">
      <div className={`text-2xl font-bold ${color || 'text-gray-700'}`}>{value}</div>
      <div className="text-xs text-gray-500 mt-1">{label}</div>
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: any }) {
  return (
    <div>
      <span className="text-gray-500 text-xs">{label}</span>
      <div className="text-gray-700 text-sm font-medium">{typeof value === 'number' ? value : String(value)}</div>
    </div>
  )
}

// ==================== Combined Report Tab ====================
function CombinedReportTab() {
  const [target, setTarget] = useState('')
  const [lookupTarget, setLookupTarget] = useState('')
  const { data, isLoading, isError } = useQuery({
    queryKey: ['intel-combined', lookupTarget],
    queryFn: () => getCombinedReport(lookupTarget),
    enabled: !!lookupTarget,
  })
  const report = data?.data?.data

  return (
    <div>
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 p-6 shadow-sm mb-6">
        <div className="flex items-center gap-2 mb-3">
          <FileSearch className="w-5 h-5 text-purple-600" />
          <h3 className="font-semibold dark:text-white">综合情报查询</h3>
        </div>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          输入 IP 地址或域名，同时查询 Shodan、OTX 和本地资产库，生成综合威胁情报报告
        </p>
        <div className="flex gap-3">
          <div className="relative flex-1">
            <Search className="absolute left-3 top-3 w-4 h-4 text-gray-400" />
            <input
              placeholder="输入 IP 地址（如 8.8.8.8）或域名（如 example.com）"
              className="pl-9 pr-3 py-2.5 border dark:border-gray-600 rounded-lg w-full text-sm focus:ring-2 focus:ring-blue-500 outline-none bg-white dark:bg-gray-700 dark:text-white"
              value={target}
              onChange={e => setTarget(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && target.trim() && setLookupTarget(target.trim())}
            />
          </div>
          <button
            onClick={() => target.trim() && setLookupTarget(target.trim())}
            disabled={isLoading}
            className="px-6 py-2.5 bg-purple-600 text-white rounded-lg text-sm font-medium hover:bg-purple-700 disabled:opacity-50 flex items-center gap-2"
          >
            {isLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <Zap className="w-4 h-4" />}
            综合查询
          </button>
        </div>
      </div>

      {isError && (
        <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-400 text-sm mb-4">
          查询失败，请检查输入格式
        </div>
      )}

      {report && (
        <div className="space-y-6">
          {/* IP Report */}
          {report.type === 'ip' && report.internetDb && (
            <div>
              <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
                <Server className="w-4 h-4" /> IP 情报
                <a href={EXT_LINKS.shodan(report.target)} target="_blank" rel="noopener noreferrer"
                  className="text-blue-600 text-xs hover:underline">Shodan <ExternalLink className="w-3 h-3 inline" /></a>
                <a href={EXT_LINKS.censys(report.target)} target="_blank" rel="noopener noreferrer"
                  className="text-gray-400 text-xs hover:underline">Censys <ExternalLink className="w-3 h-3 inline" /></a>
              </h3>
              <div className="bg-gray-50 dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-4">
                <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-3">
                  <StatBox label="开放端口" value={(report.internetDb.ports || []).length} />
                  <StatBox label="漏洞数" value={(report.internetDb.vulns || []).length} color="text-red-600" />
                  <StatBox label="主机名" value={(report.internetDb.hostnames || []).length} />
                  <StatBox label="CPE/标签" value={(report.internetDb.cpes || []).length + (report.internetDb.tags || []).length} />
                </div>
                {report.internetDb.vulns?.length > 0 && (
                  <div className="flex flex-wrap gap-1">
                    {report.internetDb.vulns.map((v: string) => (
                      <a key={v} href={EXT_LINKS.nvd(v)} target="_blank" rel="noopener noreferrer"
                        className="px-2 py-0.5 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-400 rounded text-xs font-mono hover:underline">
                        {v}
                      </a>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Reputation */}
          {report.reputation && (
            <div>
              <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
                <Shield className="w-4 h-4" /> IP 信誉 (AlienVault OTX)
              </h3>
              <div className="bg-gray-50 dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-4">
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  <StatBox label="威胁情报数量" value={report.reputation.pulseCount || 0} color="text-orange-600" />
                  <StatBox label="国家" value={0} />
                  <StatBox label="城市" value={0} />
                </div>
                {report.reputation.pulses?.length > 0 && (
                  <div className="mt-3 space-y-1 max-h-40 overflow-y-auto">
                    {report.reputation.pulses.slice(0, 5).map((p: any, i: number) => (
                      <div key={i} className="text-xs text-gray-600 dark:text-gray-400 p-2 bg-white dark:bg-gray-700 rounded">
                        <span className="font-medium">{p.name}</span>
                        {p.tags?.length > 0 && (
                          <span className="ml-2 text-gray-400">{p.tags.slice(0, 3).join(', ')}</span>
                        )}
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}

          {/* Domain Report */}
          {report.type === 'domain' && report.otx && (
            <div>
              <h3 className="font-semibold mb-3 dark:text-white flex items-center gap-2">
                <Globe className="w-4 h-4" /> 域名情报
                <a href={EXT_LINKS.crtsh(report.target)} target="_blank" rel="noopener noreferrer"
                  className="text-blue-600 text-xs hover:underline">crt.sh <ExternalLink className="w-3 h-3 inline" /></a>
              </h3>
              <div className="bg-gray-50 dark:bg-gray-800 rounded-lg border dark:border-gray-700 p-4">
                <div className="grid grid-cols-2 md:grid-cols-3 gap-4">
                  <StatBox label="Passive DNS" value={report.otx.passiveDnsCount || 0} />
                  <StatBox label="子域名" value={(report.otx.subdomains || []).length} />
                  <StatBox label="URLScan 结果" value={(report.otx.urlScanResults || []).length} />
                </div>
                {report.otx.subdomains?.length > 0 && (
                  <div className="mt-3 grid grid-cols-2 md:grid-cols-3 gap-1 max-h-40 overflow-y-auto">
                    {report.otx.subdomains.slice(0, 30).map((s: string) => (
                      <span key={s} className="text-xs text-gray-600 dark:text-gray-400 font-mono px-2 py-1 bg-white dark:bg-gray-700 rounded truncate">
                        {s}
                      </span>
                    ))}
                  </div>
                )}
              </div>
            </div>
          )}
        </div>
      )}

      {!report && !isLoading && (
        <div className="text-center py-20 text-gray-400 dark:text-gray-500">
          <FileSearch className="w-12 h-12 mx-auto mb-3 opacity-50" />
          输入目标 IP 或域名进行综合查询，将同时获取 Shodan、OTX 和本地资产库信息
        </div>
      )}
    </div>
  )
}
