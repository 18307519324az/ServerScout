import { useEffect, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { fetchTopology } from '../services/api'
import { Graph } from '@antv/g6'
import { X, ZoomIn, ZoomOut, Maximize2, Filter, List, ChevronRight } from 'lucide-react'

interface Props {
  open: boolean
  onClose: () => void
}

interface G6Node {
  id: string
  data: {
    id: number
    label: string
    ipAddress: string
    hostname: string
    openPortCount: number
    criticalVulnCount: number
    subnet: string
    group: string
    serviceLabels: string[]
    [key: string]: unknown
  }
  [key: string]: unknown
}

interface G6Edge {
  id: string
  source: string
  target: string
  data: {
    linkType: string
    ports: number[]
    [key: string]: unknown
  }
  [key: string]: unknown
}

function getRiskColor(criticalCount: number): string {
  if (criticalCount === 0) return '#22c55e'
  if (criticalCount <= 2) return '#f59e0b'
  if (criticalCount <= 5) return '#f97316'
  return '#ef4444'
}

function getNodeSize(portCount: number): number {
  return Math.max(50, Math.min(100, portCount * 10 + 30))
}

export default function TopologyGraphV2({ open, onClose }: Props) {
  const containerRef = useRef<HTMLDivElement>(null)
  const graphRef = useRef<any>(null)
  const navigate = useNavigate()
  const [selectedNode, setSelectedNode] = useState<any>(null)
  const [showFilter, setShowFilter] = useState(false)
  const [filter, setFilter] = useState<string | null>(null)
  const [graphReady, setGraphReady] = useState(false)
  const [showAssetList, setShowAssetList] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ['topology'],
    queryFn: () => fetchTopology(),
    enabled: open,
  })

  useEffect(() => {
    if (!open || !data?.data?.data || !containerRef.current) return

    const { nodes: rawNodes, links: rawLinks } = data.data.data
    if (!rawNodes || rawNodes.length === 0) return

    let filteredNodes = rawNodes
    if (filter) {
      filteredNodes = rawNodes.filter((n: any) => n.group === filter)
    }
    if (filteredNodes.length === 0) return

    const nodes: G6Node[] = filteredNodes.map((n: any) => ({
      id: String(n.id),
      data: {
        id: n.id,
        label: n.ipAddress,
        ipAddress: n.ipAddress,
        hostname: n.hostname || '',
        openPortCount: n.openPortCount || 0,
        criticalVulnCount: n.criticalVulnCount || 0,
        subnet: n.subnet || '',
        group: n.group || 'default',
        serviceLabels: n.serviceLabels || [],
      },
    }))

    const nodeIds = new Set(nodes.map(n => n.id))
    const links: G6Edge[] = (rawLinks || [])
      .filter((l: any) => nodeIds.has(String(l.source)) && nodeIds.has(String(l.target)))
      .map((l: any, i: number) => ({
        id: `edge-${i}`,
        source: String(l.source),
        target: String(l.target),
        data: { linkType: l.type || 'network', ports: l.ports || [] },
      }))

    let cancelled = false
    let pointerDownCleanup: (() => void) | null = null
    let resizeObserver: ResizeObserver | null = null

    async function initGraph() {
      // Clean up previous graph, listeners, and observer
      if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
      if (pointerDownCleanup) { pointerDownCleanup(); pointerDownCleanup = null }
      if (graphRef.current) {
        try { graphRef.current.destroy() } catch {}
        graphRef.current = null
      }
      setGraphReady(false)

      const container = containerRef.current!

      // Wait for the container to get proper dimensions from the flex layout
      // The modal uses flexbox which may not have computed dimensions immediately
      let width = 0
      let height = 0
      for (let attempt = 0; attempt < 30; attempt++) {
        width = container.clientWidth
        height = container.clientHeight
        if (width > 0 && height > 0) break
        await new Promise(r => requestAnimationFrame(r))
      }
      if (cancelled) return

      // Fallback dimensions if container still has no size
      if (width < 10) width = 900
      if (height < 10) height = 550

      const graph = new Graph({
        container,
        width,
        height,
        autoFit: 'center',
        animation: true,
        layout: {
          type: 'force',
          preventOverlap: true,
          nodeSize: (d: any) => getNodeSize(d.data?.openPortCount || 0) + 20,
          nodeStrength: 1000,
          edgeStrength: 0.3,
          linkDistance: 350,
          collideStrength: 2,
          maxIteration: 500,
          animated: true,
        },
        node: {
          style: {
            size: (d: any) => getNodeSize(d.data?.openPortCount || 0),
            fill: (d: any) => getRiskColor(d.data?.criticalVulnCount || 0),
            stroke: '#fff',
            strokeWidth: 3,
            labelText: (d: any) => d.data?.ipAddress || d.id,
            labelFontSize: 12,
            labelFill: '#374151',
            labelPlacement: 'bottom',
            labelOffsetY: 8,
            shadowColor: 'rgba(0,0,0,0.15)',
            shadowBlur: 6,
            cursor: 'pointer',
          },
        },
        edge: {
          style: {
            stroke: (d: any) => {
              const lt = d.data?.linkType;
              return lt === 'gateway' ? '#f59e0b' : lt === 'service' ? '#3b82f6' : '#cbd5e1';
            },
            strokeWidth: (d: any) => d.data?.linkType === 'gateway' ? 2 : 1,
            lineDash: (d: any) => {
              const lt = d.data?.linkType;
              return lt === 'service' ? [4, 2] : lt === 'gateway' ? [] : [2, 2];
            },
          },
        },
        behaviors: [
          'drag-canvas',
          'zoom-canvas',
          'drag-element',
          'click-select',
          { type: 'hover-activate', degree: 1, direction: 'both' },
        ],
        data: { nodes, edges: links },
      })

      // Native event handler for hit-testing clicks on graph nodes
      const handleGraphClick = (e: PointerEvent | MouseEvent) => {
        const rect = container.getBoundingClientRect()
        const cssX = e.clientX - rect.left
        const cssY = e.clientY - rect.top
        if (cssX < 0 || cssY < 0 || cssX > rect.width || cssY > rect.height) return
        try {
          const nodeDataArr: any[] = graph.getNodeData()
          for (const nd of nodeDataArr) {
            const pos = graph.getElementPosition?.(nd.id)
            if (pos) {
              const nodeSize = getNodeSize(nd.data?.openPortCount || 0)
              const dx = cssX - pos[0]
              const dy = cssY - pos[1]
              if (Math.sqrt(dx * dx + dy * dy) <= Math.max(nodeSize, 50)) {
                setSelectedNode(nd.data)
                return
              }
            }
          }
        } catch {}
        setSelectedNode(null)
      }
      window.addEventListener('pointerdown', handleGraphClick, true)
      window.addEventListener('click', handleGraphClick, true)
      container.addEventListener('click', handleGraphClick)
      pointerDownCleanup = () => {
        window.removeEventListener('pointerdown', handleGraphClick, true)
        window.removeEventListener('click', handleGraphClick, true)
        container.removeEventListener('click', handleGraphClick)
      }

      // Observe container resize to update graph size
      resizeObserver = new ResizeObserver(() => {
        if (cancelled || !graphRef.current) return
        const w = container.clientWidth
        const h = container.clientHeight
        if (w > 0 && h > 0) {
          try { graph.setSize(w, h) } catch {}
        }
      })
      resizeObserver.observe(container)

      try {
        await graph.render()
        if (cancelled) {
          try { graph.destroy() } catch {}
          if (pointerDownCleanup) { pointerDownCleanup(); pointerDownCleanup = null }
          if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
          return
        }
        graphRef.current = graph
        setGraphReady(true)
        // Auto-fit after render to center the nodes
        setTimeout(() => {
          if (graphRef.current === graph) {
            try { graph.fitView() } catch {}
          }
        }, 500)
      } catch (err) {
        console.error('G6 render failed:', err)
        try { graph.destroy() } catch {}
        if (pointerDownCleanup) { pointerDownCleanup(); pointerDownCleanup = null }
        if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
      }
    }

    // Small delay to let the modal/flex layout settle before initializing
    const initTimer = setTimeout(() => { initGraph() }, 100)

    return () => {
      cancelled = true
      clearTimeout(initTimer)
      if (resizeObserver) { resizeObserver.disconnect(); resizeObserver = null }
      if (pointerDownCleanup) { pointerDownCleanup(); pointerDownCleanup = null }
      if (graphRef.current) {
        try { graphRef.current.destroy() } catch {}
        graphRef.current = null
      }
    }
  }, [data, open, filter])

  if (!open) return null

  const groups = [...new Set<string>((data?.data?.data?.nodes || [])
    .map((n: any) => n.group)
    .filter(Boolean))] as string[]

  const hasData = !!(data?.data?.data?.nodes?.length)

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
      <div className="bg-white rounded-xl w-full max-w-[1200px] h-[85vh] flex flex-col min-h-[500px]">
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-3 border-b">
          <div className="flex items-center gap-3">
            <h2 className="font-bold text-lg">资产网络拓扑图</h2>
            <div className="flex items-center gap-1">
              <button onClick={() => graphRef.current?.zoomTo(1.5)}
                className="p-1.5 hover:bg-gray-100 rounded-lg" title="放大">
                <ZoomIn className="w-4 h-4" />
              </button>
              <button onClick={() => graphRef.current?.zoomTo(0.6)}
                className="p-1.5 hover:bg-gray-100 rounded-lg" title="缩小">
                <ZoomOut className="w-4 h-4" />
              </button>
              <button onClick={() => graphRef.current?.fitView()}
                className="p-1.5 hover:bg-gray-100 rounded-lg" title="适合窗口">
                <Maximize2 className="w-4 h-4" />
              </button>
            </div>
            {groups.length > 0 && (
              <div className="relative">
                <button onClick={() => setShowFilter(!showFilter)}
                  className={`p-1.5 rounded-lg ${filter ? 'bg-blue-100 text-blue-600' : 'hover:bg-gray-100'}`}
                  title="筛选子网">
                  <Filter className="w-4 h-4" />
                </button>
                {showFilter && (
                  <div className="absolute top-10 left-0 bg-white rounded-lg shadow-lg border py-1 z-10 min-w-[140px]">
                    <button onClick={() => { setFilter(null); setShowFilter(false) }}
                      className={`block w-full text-left px-3 py-1.5 text-sm ${!filter ? 'bg-blue-50 text-blue-600' : 'hover:bg-gray-50'}`}>
                      全部子网
                    </button>
                    {groups.map(g => (
                      <button key={g} onClick={() => { setFilter(g); setShowFilter(false) }}
                        className={`block w-full text-left px-3 py-1.5 text-sm ${filter === g ? 'bg-blue-50 text-blue-600' : 'hover:bg-gray-50'}`}>
                        {g}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {/* Asset list button - always available, reliable click target */}
            <div className="relative">
              <button
                onClick={() => setShowAssetList(!showAssetList)}
                className={`flex items-center gap-1 px-2.5 py-1.5 rounded-lg text-sm font-medium transition ${
                  showAssetList ? 'bg-blue-100 text-blue-700' : 'hover:bg-gray-100 text-gray-600'
                }`}
                title="资产列表"
              >
                <List className="w-4 h-4" />
                <span>资产列表</span>
              </button>
              {showAssetList && (
                <div className="absolute top-10 right-0 bg-white rounded-lg shadow-lg border py-1 z-20 w-64 max-h-80 overflow-y-auto">
                  <div className="px-3 py-1.5 text-xs text-gray-400 font-medium border-b">点击查看资产详情</div>
                  {(data?.data?.data?.nodes || []).map((n: any) => {
                    const isSelected = selectedNode?.id === n.id
                    return (
                      <button
                        key={n.id}
                        onClick={() => {
                          setSelectedNode({
                            id: n.id,
                            label: n.ipAddress,
                            ipAddress: n.ipAddress,
                            hostname: n.hostname || '',
                            openPortCount: n.openPortCount || 0,
                            criticalVulnCount: n.criticalVulnCount || 0,
                            subnet: n.subnet || '',
                            group: n.group || 'default',
                            serviceLabels: n.serviceLabels || [],
                          })
                          setShowAssetList(false)
                        }}
                        className={`w-full flex items-center justify-between px-3 py-2 text-sm transition ${
                          isSelected ? 'bg-blue-50 text-blue-700' : 'hover:bg-gray-50 text-gray-700'
                        }`}
                      >
                        <div className="text-left">
                          <div className="font-mono font-medium">{n.ipAddress}</div>
                          <div className="text-xs text-gray-400">{n.hostname || '无主机名'} · {n.openPortCount || 0} 端口</div>
                        </div>
                        <ChevronRight className={`w-4 h-4 flex-shrink-0 ${isSelected ? 'text-blue-500' : 'text-gray-300'}`} />
                      </button>
                    )
                  })}
                </div>
              )}
            </div>
          </div>
          <button onClick={onClose} className="p-1.5 hover:bg-gray-100 rounded-lg">
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Graph area */}
        <div className="flex-1 flex min-h-[400px]">
          <div
            ref={containerRef}
            className="flex-1 relative w-full min-h-[300px]"
            style={{ minHeight: '300px' }}
          >
            {isLoading && (
              <div className="absolute inset-0 flex items-center justify-center bg-white/80 z-10">
                <div className="text-gray-400">加载拓扑数据...</div>
              </div>
            )}
            {!isLoading && !hasData && (
              <div className="absolute inset-0 flex flex-col items-center justify-center bg-white/80 z-10">
                <div className="text-gray-400 text-lg mb-2">暂无拓扑数据</div>
                <div className="text-gray-400 text-sm">请先执行端口扫描以发现网络资产</div>
              </div>
            )}
          </div>

          {/* Sidebar with node details */}
          {selectedNode && (
            <div className="w-80 border-l bg-gray-50/50 overflow-y-auto">
              <div className="p-4 border-b bg-white">
                <h3 className="font-bold text-base flex items-center gap-2">
                  <span className="w-2.5 h-2.5 rounded-full" style={{
                    background: getRiskColor(selectedNode.criticalVulnCount || 0)
                  }} />
                  节点详情
                </h3>
              </div>
              <div className="p-4 space-y-0">
                <div className="flex justify-between items-center py-3 border-b border-gray-100">
                  <span className="text-gray-400 text-xs font-medium">IP 地址</span>
                  <span className="font-mono text-sm font-semibold text-gray-800 break-all text-right ml-2">{selectedNode.ipAddress}</span>
                </div>
                <div className="flex justify-between items-center py-3 border-b border-gray-100">
                  <span className="text-gray-400 text-xs font-medium">主机名</span>
                  <span className="text-sm text-gray-700 text-right ml-2 break-all">{selectedNode.hostname || '-'}</span>
                </div>
                <div className="flex justify-between items-center py-3 border-b border-gray-100">
                  <span className="text-gray-400 text-xs font-medium">子网</span>
                  <span className="font-mono text-xs text-gray-600">{selectedNode.subnet || '-'}</span>
                </div>
                <div className="flex justify-between items-center py-3 border-b border-gray-100">
                  <span className="text-gray-400 text-xs font-medium">开放端口</span>
                  <span className="font-mono text-lg font-bold text-blue-600">{selectedNode.openPortCount}</span>
                </div>
                <div className="flex justify-between items-center py-3 border-b border-gray-100">
                  <span className="text-gray-400 text-xs font-medium">高危漏洞</span>
                  <span className={`text-lg font-bold ${selectedNode.criticalVulnCount > 0 ? 'text-red-600' : 'text-green-600'}`}>
                    {selectedNode.criticalVulnCount}
                  </span>
                </div>
                {selectedNode.serviceLabels?.length > 0 && (
                  <div className="py-3 border-b border-gray-100">
                    <span className="text-gray-400 text-xs font-medium block mb-2">服务</span>
                    <div className="flex flex-wrap gap-1.5">
                      {selectedNode.serviceLabels.map((s: string) => (
                        <span key={s} className="px-2 py-1 bg-blue-50 text-blue-700 rounded-md text-xs font-mono font-medium border border-blue-100">{s}</span>
                      ))}
                    </div>
                  </div>
                )}
                <div className="pt-4">
                  <button
                    onClick={() => { onClose(); navigate(`/assets/${selectedNode.id}`) }}
                    className="w-full px-4 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors"
                  >
                    查看完整资产详情
                  </button>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Legend */}
        <div className="flex gap-4 px-6 py-2 border-t text-xs text-gray-500 justify-center">
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-green-500 inline-block" /> 安全
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-yellow-500 inline-block" /> 低风险
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-orange-500 inline-block" /> 中风险
          </span>
          <span className="flex items-center gap-1">
            <span className="w-3 h-3 rounded-full bg-red-500 inline-block" /> 高风险
          </span>
          <span className="flex items-center gap-1 ml-4">
            <span className="w-4 h-px bg-gray-300 inline-block" style={{ borderStyle: 'dotted' }} /> 网络
          </span>
          <span className="flex items-center gap-1">
            <span className="w-4 h-px bg-yellow-500 inline-block" /> 网关
          </span>
          <span className="flex items-center gap-1">
            <span className="w-4 h-px bg-blue-500 inline-block" style={{ borderStyle: 'dashed' }} /> 共享服务
          </span>
        </div>
      </div>
    </div>
  )
}
