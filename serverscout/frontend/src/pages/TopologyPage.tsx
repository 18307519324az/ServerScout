import { useEffect, useRef, useState, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchTopology } from '../services/api'
import { ZoomIn, ZoomOut, Maximize2, Filter, List, ChevronRight, Loader2, AlertCircle } from 'lucide-react'

interface TopologyNode {
  id: number
  ipAddress: string
  hostname: string
  openPortCount: number
  criticalVulnCount: number
  subnet: string
  group: string
  serviceLabels: string[]
}

interface TopologyLink {
  source: number
  target: number
  type: string
}

function getRiskColor(criticalCount: number): string {
  if (criticalCount === 0) return '#22c55e'
  if (criticalCount <= 2) return '#f59e0b'
  if (criticalCount <= 5) return '#f97316'
  return '#ef4444'
}

function getNodeSize(portCount: number): number {
  return Math.max(8, Math.min(20, portCount * 2 + 6))
}

export default function TopologyPage() {
  const svgRef = useRef<SVGSVGElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const [selectedNode, setSelectedNode] = useState<TopologyNode | null>(null)
  const [filter, setFilter] = useState<string | null>(null)
  const [showFilter, setShowFilter] = useState(false)
  const [showAssetList, setShowAssetList] = useState(false)
  const [transform, setTransform] = useState({ x: 0, y: 0, scale: 1 })
  const [isDragging, setIsDragging] = useState(false)
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [err, setErr] = useState<string | null>(null)
  const [isDark, setIsDark] = useState(false)

  // Detect dark mode
  useEffect(() => {
    const check = () => setIsDark(document.documentElement.classList.contains('dark'))
    check()
    const obs = new MutationObserver(check)
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => obs.disconnect()
  }, [])

  const { data, isLoading } = useQuery({
    queryKey: ['topology'],
    queryFn: () => fetchTopology(),
  })

  const rawNodes: TopologyNode[] = data?.data?.data?.nodes || []
  const rawLinks: TopologyLink[] = data?.data?.data?.links || []
  const groups = [...new Set(rawNodes.map(n => n.group).filter(Boolean))]

  const filteredNodes = useMemo(() => {
    if (!filter) return rawNodes
    return rawNodes.filter(n => n.group === filter)
  }, [rawNodes, filter])

  const nodeIds = useMemo(() => new Set(filteredNodes.map(n => n.id)), [filteredNodes])

  const filteredLinks = useMemo(() =>
    rawLinks.filter(l => nodeIds.has(l.source) && nodeIds.has(l.target)),
    [rawLinks, nodeIds]
  )

  // Force simulation to compute node positions
  const positions = useMemo(() => {
    if (filteredNodes.length === 0) return new Map<number, { x: number; y: number }>()

    try {
      const width = 1200
      const height = 550
      const pos = new Map<number, { x: number; y: number }>()

      // Initialize positions randomly but within bounds
      filteredNodes.forEach((n, i) => {
        const angle = (i / filteredNodes.length) * Math.PI * 2
        const radius = Math.min(width, height) * 0.3
        pos.set(n.id, {
          x: width / 2 + Math.cos(angle) * radius * (0.5 + Math.random() * 0.5),
          y: height / 2 + Math.sin(angle) * radius * (0.5 + Math.random() * 0.5),
        })
      })

      // Simple force simulation - run a few iterations
      for (let iter = 0; iter < 200; iter++) {
        const forces = new Map<number, { dx: number; dy: number }>()
        filteredNodes.forEach(n => forces.set(n.id, { dx: 0, dy: 0 }))

        // Repulsive forces between all pairs
        for (let i = 0; i < filteredNodes.length; i++) {
          for (let j = i + 1; j < filteredNodes.length; j++) {
            const a = filteredNodes[i]
            const b = filteredNodes[j]
            const pa = pos.get(a.id)!
            const pb = pos.get(b.id)!
            let dx = pb.x - pa.x
            let dy = pb.y - pa.y
            let dist = Math.sqrt(dx * dx + dy * dy)
            if (dist < 1) dist = 1
            const force = 8000 / (dist * dist)
            const fx = (dx / dist) * force
            const fy = (dy / dist) * force
            forces.get(b.id)!.dx += fx
            forces.get(b.id)!.dy += fy
            forces.get(a.id)!.dx -= fx
            forces.get(a.id)!.dy -= fy
          }
        }

        // Attractive forces for edges
        filteredLinks.forEach(l => {
          const pa = pos.get(l.source)
          const pb = pos.get(l.target)
          if (!pa || !pb) return
          let dx = pb.x - pa.x
          let dy = pb.y - pa.y
          const dist = Math.sqrt(dx * dx + dy * dy)
          if (dist < 1) return
          const force = dist * 0.001
          const fx = (dx / dist) * force
          const fy = (dy / dist) * force
          forces.get(l.source)!.dx += fx
          forces.get(l.target)!.dx -= fx
          forces.get(l.target)!.dy -= fy
          forces.get(l.source)!.dy += fy
        })

        // Center gravity
        filteredNodes.forEach(n => {
          const p = pos.get(n.id)!
          forces.get(n.id)!.dx += (width / 2 - p.x) * 0.01
          forces.get(n.id)!.dy += (height / 2 - p.y) * 0.01
        })

        // Apply forces with damping
        const damping = 0.5
        filteredNodes.forEach(n => {
          const p = pos.get(n.id)!
          const f = forces.get(n.id)!
          p.x += f.dx * damping
          p.y += f.dy * damping
          p.x = Math.max(40, Math.min(width - 40, p.x))
          p.y = Math.max(40, Math.min(height - 40, p.y))
        })
      }

      return pos
    } catch (e: any) {
      setErr(e.message || String(e))
      return new Map()
    }
  }, [filteredNodes, filteredLinks])

  // Pan/zoom handlers
  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault()
    const scaleBy = e.deltaY > 0 ? 0.9 : 1.1
    setTransform(t => ({
      ...t,
      scale: Math.max(0.2, Math.min(5, t.scale * scaleBy)),
    }))
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (e.target === svgRef.current || (e.target as Element).tagName === 'circle' || (e.target as Element).tagName === 'line') {
      setIsDragging(true)
      setDragStart({ x: e.clientX - transform.x, y: e.clientY - transform.y })
    }
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isDragging) return
    setTransform(t => ({ ...t, x: e.clientX - dragStart.x, y: e.clientY - dragStart.y }))
  }

  const handleMouseUp = () => setIsDragging(false)

  const handleZoomIn = () => setTransform(t => ({ ...t, scale: Math.min(5, t.scale * 1.3) }))
  const handleZoomOut = () => setTransform(t => ({ ...t, scale: Math.max(0.2, t.scale * 0.7) }))
  const handleFitView = () => setTransform({ x: 0, y: 0, scale: 1 })

  const hasData = rawNodes.length > 0

  const labelColor = isDark ? '#d1d5db' : '#374151'
  const subLabelColor = isDark ? '#9ca3af' : '#9ca3af'
  const networkLinkColor = isDark ? '#4b5563' : '#cbd5e1'

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4 dark:text-white">资产网络拓扑图</h1>

      {/* Toolbar */}
      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <button onClick={handleZoomIn} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <ZoomIn className="w-4 h-4" /> 放大
        </button>
        <button onClick={handleZoomOut} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <ZoomOut className="w-4 h-4" /> 缩小
        </button>
        <button onClick={handleFitView} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <Maximize2 className="w-4 h-4" /> 适合窗口
        </button>

        {groups.length > 0 && (
          <div className="relative">
            <button onClick={() => setShowFilter(!showFilter)}
              className={`flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm ${filter ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}>
              <Filter className="w-4 h-4" /> {filter || '全部子网'}
            </button>
            {showFilter && (
              <div className="absolute top-10 left-0 bg-white dark:bg-gray-800 rounded-lg shadow-lg border dark:border-gray-600 py-1 z-20 min-w-[140px]">
                <button onClick={() => { setFilter(null); setShowFilter(false) }}
                  className="block w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">全部</button>
                {groups.map(g => (
                  <button key={g} onClick={() => { setFilter(g); setShowFilter(false) }}
                    className={`block w-full text-left px-3 py-1.5 text-sm font-mono ${filter === g ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}>{g}</button>
                ))}
              </div>
            )}
          </div>
        )}

        {hasData && (
          <div className="relative">
            <button onClick={() => setShowAssetList(!showAssetList)}
              className={`flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm ${showAssetList ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}>
              <List className="w-4 h-4" /> 资产列表
            </button>
            {showAssetList && (
              <div className="absolute top-10 right-0 bg-white dark:bg-gray-800 rounded-lg shadow-lg border dark:border-gray-600 py-1 z-20 w-64 max-h-80 overflow-y-auto">
                {rawNodes.map(n => (
                  <button key={n.id}
                    onClick={() => {
                      setSelectedNode(n)
                      setShowAssetList(false)
                    }}
                    className={`w-full flex items-center justify-between px-3 py-2 text-sm ${selectedNode?.id === n.id ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-200'}`}>
                    <div className="text-left">
                      <div className="font-mono font-medium">{n.ipAddress}</div>
                      <div className="text-xs text-gray-400 dark:text-gray-500">{n.hostname || '-'} · {n.openPortCount || 0} 端口</div>
                    </div>
                    <ChevronRight className={`w-4 h-4 flex-shrink-0 ${selectedNode?.id === n.id ? 'text-blue-500' : 'text-gray-300 dark:text-gray-600'}`} />
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {err && (
          <div className="flex items-center gap-1 px-3 py-1.5 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-sm text-red-600 dark:text-red-400">
            <AlertCircle className="w-4 h-4" /> {err}
          </div>
        )}

        {/* Legend */}
        <div className="flex gap-4 ml-auto text-xs text-gray-500 dark:text-gray-400 items-center flex-wrap">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-green-500 inline-block" /> 安全</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-yellow-500 inline-block" /> 低风险</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-orange-500 inline-block" /> 中风险</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-red-500 inline-block" /> 高风险</span>
          <span className="flex items-center gap-1 ml-3"><span className="w-8 h-px bg-yellow-500 inline-block" /> 网关</span>
          <span className="flex items-center gap-1"><span className="w-8 h-px bg-blue-500 inline-block" /> 服务</span>
          <span className="flex items-center gap-1"><span className="w-8 h-px bg-gray-300 dark:bg-gray-600 inline-block" /> 网络</span>
        </div>
      </div>

      {/* Main content: graph + sidebar */}
      <div className="flex gap-0 border dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 overflow-hidden" style={{ minHeight: 550 }}>
        <div
          ref={containerRef}
          className="flex-1 relative"
          style={{ minHeight: 550, cursor: isDragging ? 'grabbing' : 'grab', overflow: 'hidden' }}
          onWheel={handleWheel}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
        >
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center bg-white/80 dark:bg-gray-800/80 z-10">
              <div className="flex items-center gap-2 text-gray-400 dark:text-gray-500">
                <Loader2 className="w-5 h-5 animate-spin" /> 加载拓扑数据...
              </div>
            </div>
          )}
          {!isLoading && !hasData && (
            <div className="absolute inset-0 flex flex-col items-center justify-center z-10">
              <div className="text-gray-400 dark:text-gray-500 text-lg mb-2">暂无拓扑数据</div>
              <div className="text-gray-400 dark:text-gray-500 text-sm">请先执行端口扫描发现网络资产</div>
            </div>
          )}
          {hasData && (
            <svg
              ref={svgRef}
              className="absolute inset-0"
              width="100%"
              height="100%"
              style={{ minWidth: 600, minHeight: 400 }}
            >
              <g transform={`translate(${transform.x},${transform.y}) scale(${transform.scale})`}>
                {/* Edges */}
                {filteredLinks.map((l, i) => {
                  const sp = positions.get(l.source)
                  const tp = positions.get(l.target)
                  if (!sp || !tp) return null
                  const stroke = l.type === 'gateway' ? '#f59e0b' : l.type === 'service' ? '#3b82f6' : networkLinkColor
                  const sw = l.type === 'gateway' ? 2.5 : l.type === 'service' ? 2 : 1.5
                  const dash = l.type === 'service' ? '6,3' : l.type === 'network' ? '4,4' : undefined
                  return (
                    <line key={i} x1={sp.x} y1={sp.y} x2={tp.x} y2={tp.y}
                      stroke={stroke} strokeWidth={sw} strokeDasharray={dash} />
                  )
                })}

                {/* Nodes */}
                {filteredNodes.map(n => {
                  const p = positions.get(n.id)
                  if (!p) return null
                  const r = getNodeSize(n.openPortCount || 0)
                  const fill = getRiskColor(n.criticalVulnCount || 0)
                  const isSelected = selectedNode?.id === n.id
                  return (
                    <g key={n.id} className="cursor-pointer" onClick={(e) => { e.stopPropagation(); setSelectedNode(n) }}>
                      {/* Glow for selected */}
                      {isSelected && (
                        <circle cx={p.x} cy={p.y} r={r + 8} fill="none" stroke="#3b82f6" strokeWidth={2} opacity={0.3} />
                      )}
                      {/* Halo shadow */}
                      <circle cx={p.x} cy={p.y} r={r + 2} fill="rgba(0,0,0,0.1)" />
                      {/* Main circle */}
                      <circle cx={p.x} cy={p.y} r={r} fill={fill} stroke="#fff" strokeWidth={2.5} />
                      {/* Inner highlight */}
                      <circle cx={p.x - r * 0.25} cy={p.y - r * 0.25} r={r * 0.35} fill="rgba(255,255,255,0.2)" />
                      {/* Critical vuln indicator */}
                      {(n.criticalVulnCount || 0) > 0 && (
                        <text x={p.x + r - 2} y={p.y - r + 2} textAnchor="end" fontSize={10} fill="#fff" fontWeight="bold">
                          !
                        </text>
                      )}
                      {/* Label */}
                      <text x={p.x} y={p.y + r + 14} textAnchor="middle" fontSize={11} fill={labelColor} fontFamily="monospace">
                        {n.ipAddress}
                      </text>
                      {/* Sub-label: hostname */}
                      {n.hostname && (
                        <text x={p.x} y={p.y + r + 28} textAnchor="middle" fontSize={10} fill={subLabelColor}>
                          {n.hostname}
                        </text>
                      )}
                    </g>
                  )
                })}
              </g>
            </svg>
          )}
        </div>

        {/* Node detail sidebar */}
        {selectedNode && (
          <div className="w-72 border-l dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900/50 overflow-y-auto flex-shrink-0">
            <div className="p-4 border-b dark:border-gray-700 bg-white dark:bg-gray-800 flex items-center justify-between">
              <h3 className="font-bold flex items-center gap-2 dark:text-white">
                <span className="w-2.5 h-2.5 rounded-full" style={{ background: getRiskColor(selectedNode.criticalVulnCount || 0) }} />
                节点详情
              </h3>
              <button onClick={() => setSelectedNode(null)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-lg leading-none">&times;</button>
            </div>
            <div className="p-4 space-y-0">
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">IP 地址</span>
                <span className="font-mono text-sm font-semibold text-gray-800 dark:text-gray-200 break-all text-right ml-2">{selectedNode.ipAddress}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">主机名</span>
                <span className="text-sm text-gray-700 dark:text-gray-300 text-right ml-2 break-all">{selectedNode.hostname || '-'}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">子网</span>
                <span className="font-mono text-xs text-gray-600 dark:text-gray-400">{selectedNode.subnet || '-'}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">开放端口</span>
                <span className="font-mono text-lg font-bold text-blue-600 dark:text-blue-400">{selectedNode.openPortCount}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">高危漏洞</span>
                <span className={`text-lg font-bold ${selectedNode.criticalVulnCount > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>
                  {selectedNode.criticalVulnCount}
                </span>
              </div>
              {selectedNode.serviceLabels?.length > 0 && (
                <div className="py-3 border-b dark:border-gray-700">
                  <span className="text-gray-400 dark:text-gray-500 text-xs font-medium block mb-2">服务</span>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedNode.serviceLabels.map(s => (
                      <span key={s} className="px-2 py-1 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 rounded-md text-xs font-mono font-medium border border-blue-100 dark:border-blue-800">{s}</span>
                    ))}
                  </div>
                </div>
              )}
              <div className="pt-4">
                <button
                  onClick={() => navigate(`/assets/${selectedNode.id}`)}
                  className="w-full px-4 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                >查看完整资产详情</button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
