import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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

type Point = { x: number; y: number }

const GRAPH_WIDTH = 1200
const GRAPH_HEIGHT = 550
const NODE_MARGIN = 36
const NODE_CLICK_DRAG_THRESHOLD = 6
const MIN_SCALE = 0.08
const MAX_SCALE = 5
const LARGE_GRAPH_NODE_THRESHOLD = 30
const SHOW_LABEL_AT_SCALE = 0.95
const SHOW_HOSTNAME_AT_SCALE = 1.14
const HIDE_NETWORK_AT_SCALE = 0.78

function getRiskColor(criticalCount: number): string {
  if (criticalCount === 0) return '#22c55e'
  if (criticalCount <= 2) return '#f59e0b'
  if (criticalCount <= 5) return '#f97316'
  return '#ef4444'
}

function getNodeSize(portCount: number): number {
  return Math.max(8, Math.min(20, portCount * 2 + 6))
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

function edgeKey(a: number, b: number): string {
  return a < b ? `${a}-${b}` : `${b}-${a}`
}

function linkPriority(type: string): number {
  if (type === 'gateway') return 3
  if (type === 'service') return 2
  return 1
}

function linkIdealDistance(type: string): number {
  if (type === 'gateway') return 180
  if (type === 'service') return 145
  return 110
}

function seededUnit(seed: number): number {
  const x = Math.sin(seed * 12.9898) * 43758.5453
  return x - Math.floor(x)
}

function edgeSampleKey(source: number, target: number): number {
  return (Math.abs(source * 131 + target * 29 + source * target * 7) % 1000) / 1000
}

function groupColor(group: string): { fill: string; stroke: string } {
  const hue = Math.abs(group.split('').reduce((acc, ch) => acc + ch.charCodeAt(0), 0) % 360)
  return {
    fill: `hsla(${hue}, 72%, 62%, 0.08)`,
    stroke: `hsla(${hue}, 68%, 46%, 0.34)`,
  }
}

type Bounds = { minX: number; minY: number; maxX: number; maxY: number }

function createEmptyBounds(): Bounds {
  return { minX: Infinity, minY: Infinity, maxX: -Infinity, maxY: -Infinity }
}

function expandBounds(bounds: Bounds, minX: number, minY: number, maxX: number, maxY: number) {
  bounds.minX = Math.min(bounds.minX, minX)
  bounds.minY = Math.min(bounds.minY, minY)
  bounds.maxX = Math.max(bounds.maxX, maxX)
  bounds.maxY = Math.max(bounds.maxY, maxY)
}

function isFiniteBounds(bounds: Bounds): boolean {
  return Number.isFinite(bounds.minX) && Number.isFinite(bounds.minY) &&
    Number.isFinite(bounds.maxX) && Number.isFinite(bounds.maxY)
}

function computeSceneBounds(points: Map<number, Point>, nodes: TopologyNode[]): Bounds {
  const bounds = createEmptyBounds()
  if (points.size === 0 || nodes.length === 0) return bounds

  // Include node body + label area so fit-view does not clip text.
  nodes.forEach(node => {
    const p = points.get(node.id)
    if (!p) return
    const r = getNodeSize(node.openPortCount || 0)
    const hostExtra = node.hostname ? 20 : 0
    const labelHalfWidth = node.hostname ? 78 : 64
    const horizontal = Math.max(r + 12, labelHalfWidth)
    expandBounds(
      bounds,
      p.x - horizontal,
      p.y - r - 12,
      p.x + horizontal,
      p.y + r + 34 + hostExtra
    )
  })

  // Include group bubble extents as part of visible scene.
  const grouped = new Map<string, Point[]>()
  nodes.forEach(node => {
    const p = points.get(node.id)
    if (!p) return
    const key = node.group || node.subnet || 'ungrouped'
    if (!grouped.has(key)) grouped.set(key, [])
    grouped.get(key)!.push(p)
  })
  grouped.forEach(pts => {
    if (pts.length <= 1) return
    const cx = pts.reduce((sum, p) => sum + p.x, 0) / pts.length
    const cy = pts.reduce((sum, p) => sum + p.y, 0) / pts.length
    const farthest = Math.max(...pts.map(p => Math.hypot(p.x - cx, p.y - cy)))
    const r = farthest + 44
    expandBounds(bounds, cx - r, cy - r, cx + r, cy + r)
  })

  return bounds
}

function buildVisualLinks(nodes: TopologyNode[], links: TopologyLink[]): TopologyLink[] {
  if (nodes.length === 0 || links.length === 0) return []

  const nodeById = new Map(nodes.map(n => [n.id, n]))
  const merged = new Map<string, TopologyLink>()

  for (const link of links) {
    if (!nodeById.has(link.source) || !nodeById.has(link.target) || link.source === link.target) continue
    const s = Math.min(link.source, link.target)
    const t = Math.max(link.source, link.target)
    const key = edgeKey(s, t)
    const prev = merged.get(key)
    if (!prev || linkPriority(link.type) > linkPriority(prev.type)) {
      merged.set(key, { source: s, target: t, type: link.type })
    }
  }

  const all = [...merged.values()]
  const gatewayLinks = all.filter(l => l.type === 'gateway')
  const nonGatewayLinks = all.filter(l => l.type !== 'gateway')
  if (nonGatewayLinks.length === 0) return gatewayLinks

  const parent = new Map<number, number>()
  const degree = new Map<number, number>()
  nodes.forEach(n => {
    parent.set(n.id, n.id)
    degree.set(n.id, 0)
  })

  const find = (x: number): number => {
    const px = parent.get(x)!
    if (px === x) return x
    const root = find(px)
    parent.set(x, root)
    return root
  }
  const union = (a: number, b: number): boolean => {
    const ra = find(a)
    const rb = find(b)
    if (ra === rb) return false
    parent.set(ra, rb)
    return true
  }

  const scoreLink = (l: TopologyLink) => {
    const a = nodeById.get(l.source)
    const b = nodeById.get(l.target)
    const critical = (a?.criticalVulnCount || 0) + (b?.criticalVulnCount || 0)
    const ports = (a?.openPortCount || 0) + (b?.openPortCount || 0)
    return linkPriority(l.type) * 100 + critical * 8 + ports
  }

  const sorted = [...nonGatewayLinks].sort((a, b) => scoreLink(b) - scoreLink(a))
  const kept = new Map<string, TopologyLink>()
  const maxPerNode = Math.max(3, Math.ceil(Math.sqrt(nodes.length)) + (nodes.length > 35 ? 0 : 1))
  const densityFactor = nodes.length > 60 ? 1.25 : nodes.length > 35 ? 1.45 : 1.7
  const targetFlexibleEdges = Math.max(nodes.length - 1, Math.ceil(nodes.length * densityFactor))

  // Keep a connectivity backbone first.
  for (const link of sorted) {
    if (!union(link.source, link.target)) continue
    kept.set(edgeKey(link.source, link.target), link)
    degree.set(link.source, (degree.get(link.source) || 0) + 1)
    degree.set(link.target, (degree.get(link.target) || 0) + 1)
  }

  // Then add high-value edges with per-node caps.
  for (const link of sorted) {
    if (kept.size >= targetFlexibleEdges) break
    const key = edgeKey(link.source, link.target)
    if (kept.has(key)) continue
    const sd = degree.get(link.source) || 0
    const td = degree.get(link.target) || 0
    if (sd >= maxPerNode || td >= maxPerNode) continue

    kept.set(key, link)
    degree.set(link.source, sd + 1)
    degree.set(link.target, td + 1)
  }

  const finalMap = new Map<string, TopologyLink>()
  ;[...gatewayLinks, ...kept.values()].forEach(link => {
    finalMap.set(edgeKey(link.source, link.target), link)
  })
  return [...finalMap.values()]
}

function createLayoutPositions(nodes: TopologyNode[], links: TopologyLink[]): Map<number, Point> {
  const positions = new Map<number, Point>()
  if (nodes.length === 0) return positions

  if (nodes.length === 1) {
    positions.set(nodes[0].id, { x: GRAPH_WIDTH / 2, y: GRAPH_HEIGHT / 2 })
    return positions
  }

  const degreeById = new Map<number, number>()
  const sizeById = new Map<number, number>()
  nodes.forEach(n => {
    degreeById.set(n.id, 0)
    sizeById.set(n.id, getNodeSize(n.openPortCount || 0))
  })
  links.forEach(l => {
    degreeById.set(l.source, (degreeById.get(l.source) || 0) + 1)
    degreeById.set(l.target, (degreeById.get(l.target) || 0) + 1)
  })

  const groups = [...new Set(nodes.map(n => n.group || n.subnet || 'ungrouped'))]
  const groupCenters = new Map<string, Point>()
  const groupOfNode = new Map<number, string>()
  const cx = GRAPH_WIDTH / 2
  const cy = GRAPH_HEIGHT / 2
  const spread = clamp(0.25 + groups.length * 0.038, 0.26, 0.42)
  const rx = GRAPH_WIDTH * spread
  const ry = GRAPH_HEIGHT * Math.min(0.34, spread * 0.82)

  groups.forEach((g, i) => {
    const angle = (i / Math.max(1, groups.length)) * Math.PI * 2
    const radiusJitter = 0.88 + seededUnit(i * 17 + groups.length) * 0.24
    groupCenters.set(g, {
      x: cx + Math.cos(angle) * (groups.length > 1 ? rx * radiusJitter : 0),
      y: cy + Math.sin(angle) * (groups.length > 1 ? ry * radiusJitter : 0),
    })
  })

  const nodesByGroup = new Map<string, TopologyNode[]>()
  for (const node of nodes) {
    const g = node.group || node.subnet || 'ungrouped'
    groupOfNode.set(node.id, g)
    if (!nodesByGroup.has(g)) nodesByGroup.set(g, [])
    nodesByGroup.get(g)!.push(node)
  }

  for (const [group, groupNodes] of nodesByGroup.entries()) {
    const center = groupCenters.get(group) || { x: cx, y: cy }
    const sorted = [...groupNodes].sort((a, b) => (degreeById.get(b.id) || 0) - (degreeById.get(a.id) || 0))
    const localRadius = Math.max(58, Math.min(185, 36 + Math.sqrt(groupNodes.length) * 24))
    sorted.forEach((node, i) => {
      const angle = ((i + seededUnit(node.id)) / Math.max(1, sorted.length)) * Math.PI * 2
      const ringLayer = 0.58 + (i % 3) * 0.18
      const jitter = ringLayer + seededUnit(node.id * 31) * 0.2
      positions.set(node.id, {
        x: clamp(center.x + Math.cos(angle) * localRadius * jitter, NODE_MARGIN, GRAPH_WIDTH - NODE_MARGIN),
        y: clamp(center.y + Math.sin(angle) * localRadius * jitter, NODE_MARGIN, GRAPH_HEIGHT - NODE_MARGIN),
      })
    })
  }

  for (let iter = 0; iter < 220; iter++) {
    const forces = new Map<number, Point>()
    nodes.forEach(n => forces.set(n.id, { x: 0, y: 0 }))

    for (let i = 0; i < nodes.length; i++) {
      for (let j = i + 1; j < nodes.length; j++) {
        const a = nodes[i]
        const b = nodes[j]
        const pa = positions.get(a.id)!
        const pb = positions.get(b.id)!
        let dx = pb.x - pa.x
        let dy = pb.y - pa.y
        let dist = Math.hypot(dx, dy)
        if (dist < 1) dist = 1
        const charge = 6200 / (dist * dist)
        let fx = (dx / dist) * charge
        let fy = (dy / dist) * charge

        const sizeA = sizeById.get(a.id) || 8
        const sizeB = sizeById.get(b.id) || 8
        const minGap = sizeA + sizeB + 20
        if (dist < minGap) {
          const collision = ((minGap - dist) / minGap) * 2.25
          fx += (dx / dist) * collision
          fy += (dy / dist) * collision
        }
        forces.get(b.id)!.x += fx
        forces.get(b.id)!.y += fy
        forces.get(a.id)!.x -= fx
        forces.get(a.id)!.y -= fy
      }
    }

    for (const link of links) {
      const pa = positions.get(link.source)
      const pb = positions.get(link.target)
      if (!pa || !pb) continue
      let dx = pb.x - pa.x
      let dy = pb.y - pa.y
      let dist = Math.hypot(dx, dy)
      if (dist < 1) dist = 1
      const desired = linkIdealDistance(link.type)
      const stiffness = link.type === 'network' ? 0.012 : 0.02
      const spring = (dist - desired) * stiffness
      const fx = (dx / dist) * spring
      const fy = (dy / dist) * spring
      forces.get(link.source)!.x += fx
      forces.get(link.source)!.y += fy
      forces.get(link.target)!.x -= fx
      forces.get(link.target)!.y -= fy
    }

    nodes.forEach(n => {
      const p = positions.get(n.id)!
      const f = forces.get(n.id)!
      const group = groupOfNode.get(n.id)
      const gc = group ? groupCenters.get(group) : null
      if (gc) {
        f.x += (gc.x - p.x) * 0.0062
        f.y += (gc.y - p.y) * 0.0062
      }
      f.x += (cx - p.x) * 0.0015
      f.y += (cy - p.y) * 0.0015

      const stepX = clamp(f.x * 0.74, -13, 13)
      const stepY = clamp(f.y * 0.74, -13, 13)
      p.x = clamp(p.x + stepX, NODE_MARGIN, GRAPH_WIDTH - NODE_MARGIN)
      p.y = clamp(p.y + stepY, NODE_MARGIN, GRAPH_HEIGHT - NODE_MARGIN)
    })
  }

  return positions
}

export default function TopologyPage() {
  const { t } = useTranslation()
  const svgRef = useRef<SVGSVGElement>(null)
  const containerRef = useRef<HTMLDivElement>(null)
  const nodeDragOriginRef = useRef<{ nodeId: number | null; x: number; y: number; moved: boolean }>({
    nodeId: null,
    x: 0,
    y: 0,
    moved: false,
  })
  const suppressNodeClickRef = useRef<number | null>(null)
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()

  const [selectedNode, setSelectedNode] = useState<TopologyNode | null>(null)
  const [hoveredNodeId, setHoveredNodeId] = useState<number | null>(null)
  const [filter, setFilter] = useState<string | null>(null)
  const [showFilter, setShowFilter] = useState(false)
  const [showAssetList, setShowAssetList] = useState(false)
  const [transform, setTransform] = useState({ x: 0, y: 0, scale: 1 })
  const [isPanning, setIsPanning] = useState(false)
  const [dragStart, setDragStart] = useState({ x: 0, y: 0 })
  const [draggingNodeId, setDraggingNodeId] = useState<number | null>(null)
  const [dragNodeOffset, setDragNodeOffset] = useState<Point>({ x: 0, y: 0 })
  const [positions, setPositions] = useState<Map<number, Point>>(new Map())
  const [err, setErr] = useState<string | null>(null)
  const [isDark, setIsDark] = useState(false)

  useEffect(() => {
    const check = () => setIsDark(document.documentElement.classList.contains('dark'))
    check()
    const obs = new MutationObserver(check)
    obs.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
    return () => obs.disconnect()
  }, [])

  useEffect(() => {
    if (!isPanning && draggingNodeId === null) return

    const body = document.body
    const prevUserSelect = body.style.userSelect
    const prevWebkitUserSelect = body.style.getPropertyValue('-webkit-user-select')
    body.style.userSelect = 'none'
    body.style.setProperty('-webkit-user-select', 'none')

    return () => {
      body.style.userSelect = prevUserSelect
      if (prevWebkitUserSelect) {
        body.style.setProperty('-webkit-user-select', prevWebkitUserSelect)
      } else {
        body.style.removeProperty('-webkit-user-select')
      }
    }
  }, [isPanning, draggingNodeId])

  useEffect(() => {
    if (!isPanning && draggingNodeId === null) return
    const preventSelect = (event: Event) => event.preventDefault()
    document.addEventListener('selectstart', preventSelect)
    return () => document.removeEventListener('selectstart', preventSelect)
  }, [isPanning, draggingNodeId])

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

  const allVisibleLinks = useMemo(() =>
    rawLinks.filter(l => nodeIds.has(l.source) && nodeIds.has(l.target)),
    [rawLinks, nodeIds]
  )

  const visualLinks = useMemo(
    () => buildVisualLinks(filteredNodes, allVisibleLinks),
    [filteredNodes, allVisibleLinks]
  )

  const nodeDegree = useMemo(() => {
    const degree = new Map<number, number>()
    filteredNodes.forEach(n => degree.set(n.id, 0))
    visualLinks.forEach(l => {
      degree.set(l.source, (degree.get(l.source) || 0) + 1)
      degree.set(l.target, (degree.get(l.target) || 0) + 1)
    })
    return degree
  }, [filteredNodes, visualLinks])

  const importantNodeIds = useMemo(() => {
    const sorted = [...filteredNodes]
      .map(n => ({
        id: n.id,
        score: (n.criticalVulnCount || 0) * 6 + (nodeDegree.get(n.id) || 0) * 2 + Math.min(8, n.openPortCount || 0),
      }))
      .sort((a, b) => b.score - a.score)
    const keep = Math.max(5, Math.min(14, Math.ceil(filteredNodes.length * 0.16)))
    return new Set(sorted.slice(0, keep).map(item => item.id))
  }, [filteredNodes, nodeDegree])

  const selectedNeighborIds = useMemo(() => {
    if (!selectedNode) return new Set<number>()
    const ids = new Set<number>([selectedNode.id])
    visualLinks.forEach(l => {
      if (l.source === selectedNode.id) ids.add(l.target)
      if (l.target === selectedNode.id) ids.add(l.source)
    })
    return ids
  }, [selectedNode, visualLinks])

  const renderedLinks = useMemo(() => {
    const isLargeGraph = filteredNodes.length >= LARGE_GRAPH_NODE_THRESHOLD
    const networkCollapsed = transform.scale < HIDE_NETWORK_AT_SCALE || filteredNodes.length > 70

    return visualLinks.filter(l => {
      if (selectedNode) {
        if (l.source === selectedNode.id || l.target === selectedNode.id) return true
        return l.type === 'gateway' && transform.scale >= 1.25
      }

      if (networkCollapsed && l.type === 'network') return false
      if (isLargeGraph && l.type === 'service' && transform.scale < 0.92) {
        return edgeSampleKey(l.source, l.target) < 0.65
      }
      return true
    })
  }, [visualLinks, selectedNode, transform.scale, filteredNodes.length])

  const groupBubbles = useMemo(() => {
    const grouped = new Map<string, Point[]>()
    filteredNodes.forEach(node => {
      const point = positions.get(node.id)
      if (!point) return
      const key = node.group || node.subnet || 'ungrouped'
      if (!grouped.has(key)) grouped.set(key, [])
      grouped.get(key)!.push(point)
    })

    return [...grouped.entries()]
      .filter(([, pts]) => pts.length > 1)
      .map(([group, pts]) => {
        const cx = pts.reduce((sum, p) => sum + p.x, 0) / pts.length
        const cy = pts.reduce((sum, p) => sum + p.y, 0) / pts.length
        const farthest = Math.max(...pts.map(p => Math.hypot(p.x - cx, p.y - cy)))
        return { group, cx, cy, r: farthest + 42, count: pts.length }
      })
  }, [filteredNodes, positions])

  const hiddenLinkCount = Math.max(0, allVisibleLinks.length - renderedLinks.length)
  const graphPaneHeight = 'clamp(420px, calc(100vh - 235px), 860px)'

  const fitViewToContainer = useCallback((points: Map<number, Point>, nodes: TopologyNode[]) => {
    if (points.size === 0 || nodes.length === 0) return

    const sceneBounds = computeSceneBounds(points, nodes)
    if (!isFiniteBounds(sceneBounds)) return

    const viewportPadding = 32
    const contentW = Math.max(1, sceneBounds.maxX - sceneBounds.minX)
    const contentH = Math.max(1, sceneBounds.maxY - sceneBounds.minY)
    const availW = Math.max(1, GRAPH_WIDTH - viewportPadding * 2)
    const availH = Math.max(1, GRAPH_HEIGHT - viewportPadding * 2)
    const scale = clamp(Math.min(availW / contentW, availH / contentH), MIN_SCALE, 2.2)

    const centerX = (sceneBounds.minX + sceneBounds.maxX) / 2
    const centerY = (sceneBounds.minY + sceneBounds.maxY) / 2

    setTransform({
      scale,
      x: GRAPH_WIDTH / 2 - centerX * scale,
      y: GRAPH_HEIGHT / 2 - centerY * scale,
    })
  }, [])

  useEffect(() => {
    try {
      setErr(null)
      const nextPositions = createLayoutPositions(filteredNodes, visualLinks)
      setPositions(nextPositions)
      requestAnimationFrame(() => fitViewToContainer(nextPositions, filteredNodes))
    } catch (e: any) {
      setErr(e?.message || String(e))
      setPositions(new Map())
    }
  }, [filteredNodes, visualLinks, fitViewToContainer])

  useEffect(() => {
    const idParam = searchParams.get('assetId')
    if (!idParam) return
    const focusId = Number(idParam)
    if (!Number.isFinite(focusId)) return
    const node = rawNodes.find(n => n.id === focusId)
    if (!node) return
    setSelectedNode(node)
    if (node.group) {
      setFilter(prev => (prev === node.group ? prev : node.group))
    }
  }, [rawNodes, searchParams])

  useEffect(() => {
    const el = containerRef.current
    if (!el || positions.size === 0) return

    const observer = new ResizeObserver(() => {
      fitViewToContainer(positions, filteredNodes)
    })
    observer.observe(el)
    return () => observer.disconnect()
  }, [positions, filteredNodes, fitViewToContainer])

  const toSvgPoint = (clientX: number, clientY: number): Point | null => {
    const svg = svgRef.current
    if (!svg) return null
    const ctm = svg.getScreenCTM()
    if (!ctm) return null
    const pt = svg.createSVGPoint()
    pt.x = clientX
    pt.y = clientY
    const mapped = pt.matrixTransform(ctm.inverse())
    return { x: mapped.x, y: mapped.y }
  }

  const toGraphPoint = (clientX: number, clientY: number): Point | null => {
    const svgPoint = toSvgPoint(clientX, clientY)
    if (!svgPoint) return null
    return {
      x: (svgPoint.x - transform.x) / transform.scale,
      y: (svgPoint.y - transform.y) / transform.scale,
    }
  }

  const handleWheel = (e: React.WheelEvent) => {
    e.preventDefault()
    const svgPoint = toSvgPoint(e.clientX, e.clientY)
    if (!svgPoint) return
    const graphX = (svgPoint.x - transform.x) / transform.scale
    const graphY = (svgPoint.y - transform.y) / transform.scale

    const scaleBy = e.deltaY > 0 ? 0.9 : 1.1
    const nextScale = clamp(transform.scale * scaleBy, MIN_SCALE, MAX_SCALE)

    setTransform({
      x: svgPoint.x - graphX * nextScale,
      y: svgPoint.y - graphY * nextScale,
      scale: nextScale,
    })
  }

  const handleMouseDown = (e: React.MouseEvent) => {
    if (draggingNodeId !== null) return
    const target = e.target as Element
    if (target.closest('[data-topology-node="1"]')) return

    e.preventDefault()
    const svgPoint = toSvgPoint(e.clientX, e.clientY)
    if (!svgPoint) return
    setIsPanning(true)
    setDragStart({ x: svgPoint.x - transform.x, y: svgPoint.y - transform.y })
  }

  const handleNodeMouseDown = (nodeId: number, e: React.MouseEvent) => {
    e.stopPropagation()
    e.preventDefault()
    const graphPoint = toGraphPoint(e.clientX, e.clientY)
    const current = positions.get(nodeId)
    if (!graphPoint || !current) return

    nodeDragOriginRef.current = {
      nodeId,
      x: e.clientX,
      y: e.clientY,
      moved: false,
    }
    setDraggingNodeId(nodeId)
    setDragNodeOffset({ x: graphPoint.x - current.x, y: graphPoint.y - current.y })
  }

  const handleMouseMove = (e: React.MouseEvent) => {
    if (draggingNodeId !== null || isPanning) {
      e.preventDefault()
    }
    if (draggingNodeId !== null) {
      const drag = nodeDragOriginRef.current
      if (drag.nodeId === draggingNodeId && !drag.moved) {
        const dx = Math.abs(e.clientX - drag.x)
        const dy = Math.abs(e.clientY - drag.y)
        if (dx >= NODE_CLICK_DRAG_THRESHOLD || dy >= NODE_CLICK_DRAG_THRESHOLD) {
          drag.moved = true
        }
      }
      const graphPoint = toGraphPoint(e.clientX, e.clientY)
      if (!graphPoint) return
      const nextX = clamp(graphPoint.x - dragNodeOffset.x, NODE_MARGIN, GRAPH_WIDTH - NODE_MARGIN)
      const nextY = clamp(graphPoint.y - dragNodeOffset.y, NODE_MARGIN, GRAPH_HEIGHT - NODE_MARGIN)
      setPositions(prev => {
        const next = new Map(prev)
        next.set(draggingNodeId, { x: nextX, y: nextY })
        return next
      })
      return
    }

    if (!isPanning) return
    const svgPoint = toSvgPoint(e.clientX, e.clientY)
    if (!svgPoint) return
    setTransform(t => ({ ...t, x: svgPoint.x - dragStart.x, y: svgPoint.y - dragStart.y }))
  }

  const handleMouseUp = () => {
    window.getSelection?.()?.removeAllRanges()
    setIsPanning(false)
    if (draggingNodeId !== null) {
      const drag = nodeDragOriginRef.current
      suppressNodeClickRef.current = drag.moved ? draggingNodeId : null
      nodeDragOriginRef.current = { nodeId: null, x: 0, y: 0, moved: false }
    }
    setDraggingNodeId(null)
  }

  const handleZoomIn = () => setTransform(t => ({ ...t, scale: clamp(t.scale * 1.3, MIN_SCALE, MAX_SCALE) }))
  const handleZoomOut = () => setTransform(t => ({ ...t, scale: clamp(t.scale * 0.7, MIN_SCALE, MAX_SCALE) }))

  const handleFitView = () => {
    if (positions.size === 0) {
      setTransform({ x: 0, y: 0, scale: 1 })
      return
    }
    fitViewToContainer(positions, filteredNodes)
  }

  const hasData = rawNodes.length > 0
  const labelColor = isDark ? '#d1d5db' : '#374151'
  const subLabelColor = isDark ? '#9ca3af' : '#9ca3af'
  const networkLinkColor = isDark ? '#4b5563' : '#cbd5e1'

  return (
    <div>
      <h1 className="text-2xl font-bold mb-4 dark:text-white">{t('topology.title')}</h1>

      <div className="flex items-center gap-2 mb-4 flex-wrap">
        <button onClick={handleZoomIn} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <ZoomIn className="w-4 h-4" /> {t('topology.zoomIn')}
        </button>
        <button onClick={handleZoomOut} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <ZoomOut className="w-4 h-4" /> {t('topology.zoomOut')}
        </button>
        <button onClick={handleFitView} className="flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200">
          <Maximize2 className="w-4 h-4" /> {t('topology.fitView')}
        </button>

        {groups.length > 0 && (
          <div className="relative">
            <button
              onClick={() => setShowFilter(!showFilter)}
              className={`flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm ${filter ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}
            >
              <Filter className="w-4 h-4" /> {filter || t('topology.all') + ' ' + t('topology.subnet')}
            </button>
            {showFilter && (
              <div className="absolute top-10 left-0 bg-white dark:bg-gray-800 rounded-lg shadow-lg border dark:border-gray-600 py-1 z-20 min-w-[140px]">
                <button
                  onClick={() => {
                    setFilter(null)
                    setShowFilter(false)
                  }}
                  className="block w-full text-left px-3 py-1.5 text-sm hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200"
                >
                  {t('topology.all')}
                </button>
                {groups.map(g => (
                  <button
                    key={g}
                    onClick={() => {
                      setFilter(g)
                      setShowFilter(false)
                    }}
                    className={`block w-full text-left px-3 py-1.5 text-sm font-mono ${filter === g ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}
                  >
                    {g}
                  </button>
                ))}
              </div>
            )}
          </div>
        )}

        {hasData && (
          <div className="relative">
            <button
              onClick={() => setShowAssetList(!showAssetList)}
              className={`flex items-center gap-1 px-3 py-1.5 border dark:border-gray-600 rounded-lg text-sm ${showAssetList ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400 border-blue-200 dark:border-blue-800' : 'hover:bg-gray-50 dark:hover:bg-gray-700 dark:text-gray-200'}`}
            >
              <List className="w-4 h-4" /> {t('topology.assetList')}
            </button>
            {showAssetList && (
              <div className="absolute top-10 right-0 bg-white dark:bg-gray-800 rounded-lg shadow-lg border dark:border-gray-600 py-1 z-20 w-64 max-h-80 overflow-y-auto">
                {rawNodes.map(n => (
                  <button
                    key={n.id}
                    onClick={() => {
                      setSelectedNode(n)
                      setShowAssetList(false)
                    }}
                    className={`w-full flex items-center justify-between px-3 py-2 text-sm ${selectedNode?.id === n.id ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' : 'hover:bg-gray-50 dark:hover:bg-gray-700 text-gray-700 dark:text-gray-200'}`}
                  >
                    <div className="text-left">
                      <div className="font-mono font-medium">{n.ipAddress}</div>
                      <div className="text-xs text-gray-400 dark:text-gray-500">{n.hostname || '-'} · {n.openPortCount || 0} {t('topology.port')}</div>
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

        <div className="ml-auto flex items-center gap-3 text-xs text-gray-500 dark:text-gray-400 flex-wrap">
          <span className="font-medium">Edges {renderedLinks.length}/{allVisibleLinks.length}</span>
          {hiddenLinkCount > 0 && <span>reduced {hiddenLinkCount}</span>}
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-green-500 inline-block" /> {t('topology.legendSafe')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-yellow-500 inline-block" /> {t('topology.legendLow')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-orange-500 inline-block" /> {t('topology.legendMedium')}</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-red-500 inline-block" /> {t('topology.legendHigh')}</span>
          <span className="flex items-center gap-1 ml-2"><span className="w-8 h-px bg-yellow-500 inline-block" /> {t('topology.legendGateway')}</span>
          <span className="flex items-center gap-1"><span className="w-8 h-px bg-blue-500 inline-block" /> {t('topology.legendService')}</span>
          <span className="flex items-center gap-1"><span className="w-8 h-px bg-gray-300 dark:bg-gray-600 inline-block" /> {t('topology.legendNetwork')}</span>
        </div>
      </div>

      <div className="flex gap-0 border dark:border-gray-700 rounded-xl bg-white dark:bg-gray-800 overflow-hidden" style={{ height: graphPaneHeight, minHeight: 420 }}>
        <div
          ref={containerRef}
          className="flex-1 relative select-none"
          style={{ height: '100%', cursor: draggingNodeId !== null || isPanning ? 'grabbing' : 'grab', overflow: 'hidden' }}
          onWheel={handleWheel}
          onMouseDown={handleMouseDown}
          onMouseMove={handleMouseMove}
          onMouseUp={handleMouseUp}
          onMouseLeave={handleMouseUp}
        >
          {isLoading && (
            <div className="absolute inset-0 flex items-center justify-center bg-white/80 dark:bg-gray-800/80 z-10">
              <div className="flex items-center gap-2 text-gray-400 dark:text-gray-500">
                <Loader2 className="w-5 h-5 animate-spin" /> {t('topology.loadingTopology')}
              </div>
            </div>
          )}

          {!isLoading && !hasData && (
            <div className="absolute inset-0 flex flex-col items-center justify-center z-10">
              <div className="text-gray-400 dark:text-gray-500 text-lg mb-2">{t('topology.noData')}</div>
              <div className="text-gray-400 dark:text-gray-500 text-sm">{t('topology.runScanHint')}</div>
            </div>
          )}

          {hasData && (
            <svg ref={svgRef} className="absolute inset-0" width="100%" height="100%" viewBox={`0 0 ${GRAPH_WIDTH} ${GRAPH_HEIGHT}`} style={{ userSelect: 'none' }}>
              <g transform={`translate(${transform.x},${transform.y}) scale(${transform.scale})`}>
                {groupBubbles.map((bubble) => {
                  const palette = groupColor(bubble.group)
                  return (
                    <g key={`group-${bubble.group}`} pointerEvents="none">
                      <circle
                        cx={bubble.cx}
                        cy={bubble.cy}
                        r={bubble.r}
                        fill={palette.fill}
                        stroke={palette.stroke}
                        strokeWidth={1}
                        strokeDasharray="4,6"
                      />
                    </g>
                  )
                })}

                {renderedLinks.map((l, i) => {
                  const sp = positions.get(l.source)
                  const tp = positions.get(l.target)
                  if (!sp || !tp) return null
                  const stroke = l.type === 'gateway' ? '#f59e0b' : l.type === 'service' ? '#3b82f6' : networkLinkColor
                  const isSelectionLink = !!selectedNode && (l.source === selectedNode.id || l.target === selectedNode.id)
                  const sw = l.type === 'gateway' ? 2.25 : l.type === 'service' ? 1.7 : 1.15
                  const baseOpacity = l.type === 'network' ? 0.35 : 0.66
                  const opacity = selectedNode ? (isSelectionLink ? 0.9 : 0.15) : baseOpacity
                  const dash = l.type === 'service' ? '5,3' : l.type === 'network' ? '3,4' : undefined

                  return (
                    <line
                      key={`${l.source}-${l.target}-${i}`}
                      x1={sp.x}
                      y1={sp.y}
                      x2={tp.x}
                      y2={tp.y}
                      stroke={stroke}
                      strokeWidth={selectedNode && isSelectionLink ? sw + 0.65 : sw}
                      strokeDasharray={dash}
                      strokeOpacity={opacity}
                    />
                  )
                })}

                {filteredNodes.map(n => {
                  const p = positions.get(n.id)
                  if (!p) return null
                  const r = getNodeSize(n.openPortCount || 0)
                  const fill = getRiskColor(n.criticalVulnCount || 0)
                  const isSelected = selectedNode?.id === n.id
                  const isHovered = hoveredNodeId === n.id
                  const inSelection = !selectedNode || selectedNeighborIds.has(n.id)
                  const showLabel =
                    filteredNodes.length <= 12 ||
                    transform.scale >= SHOW_LABEL_AT_SCALE ||
                    isSelected ||
                    isHovered ||
                    importantNodeIds.has(n.id)
                  const showHostname =
                    (transform.scale >= SHOW_HOSTNAME_AT_SCALE && (isSelected || isHovered || importantNodeIds.has(n.id))) ||
                    (isSelected && !!n.hostname)

                  return (
                    <g
                      key={n.id}
                      data-topology-node="1"
                      className="cursor-pointer"
                      opacity={inSelection ? 1 : 0.36}
                      onMouseEnter={() => setHoveredNodeId(n.id)}
                      onMouseLeave={() => setHoveredNodeId(null)}
                      onMouseDown={(e) => handleNodeMouseDown(n.id, e)}
                      onClick={(e) => {
                        e.stopPropagation()
                        if (suppressNodeClickRef.current === n.id) {
                          suppressNodeClickRef.current = null
                          return
                        }
                        setSelectedNode(n)
                      }}
                    >
                      {(isSelected || isHovered) && <circle cx={p.x} cy={p.y} r={r + 9} fill="none" stroke="#3b82f6" strokeWidth={2} opacity={0.35} />}
                      <circle cx={p.x} cy={p.y} r={r + 2.2} fill="rgba(0,0,0,0.1)" />
                      <circle cx={p.x} cy={p.y} r={r} fill={fill} stroke="#fff" strokeWidth={2.5} />
                      <circle cx={p.x - r * 0.25} cy={p.y - r * 0.25} r={r * 0.35} fill="rgba(255,255,255,0.2)" />

                      {(n.criticalVulnCount || 0) > 0 && (
                        <text x={p.x + r - 2} y={p.y - r + 2} textAnchor="end" fontSize={10} fill="#fff" fontWeight="bold">
                          !
                        </text>
                      )}

                      {showLabel && (
                        <text x={p.x} y={p.y + r + 14} textAnchor="middle" fontSize={11} fill={labelColor} fontFamily="monospace" pointerEvents="none">
                          {n.ipAddress}
                        </text>
                      )}
                      {n.hostname && showHostname && (
                        <text x={p.x} y={p.y + r + 28} textAnchor="middle" fontSize={10} fill={subLabelColor} pointerEvents="none">
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

        {selectedNode && (
          <div className="w-72 border-l dark:border-gray-700 bg-gray-50/50 dark:bg-gray-900/50 overflow-y-auto flex-shrink-0">
            <div className="p-4 border-b dark:border-gray-700 bg-white dark:bg-gray-800 flex items-center justify-between">
              <h3 className="font-bold flex items-center gap-2 dark:text-white">
                <span className="w-2.5 h-2.5 rounded-full" style={{ background: getRiskColor(selectedNode.criticalVulnCount || 0) }} />
                {t('topology.nodeDetail')}
              </h3>
              <button onClick={() => setSelectedNode(null)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-200 text-lg leading-none">&times;</button>
            </div>
            <div className="p-4 space-y-0">
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">{t('topology.ipAddress')}</span>
                <span className="font-mono text-sm font-semibold text-gray-800 dark:text-gray-200 break-all text-right ml-2">{selectedNode.ipAddress}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">{t('common.hostname')}</span>
                <span className="text-sm text-gray-700 dark:text-gray-300 text-right ml-2 break-all">{selectedNode.hostname || '-'}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">{t('topology.subnet')}</span>
                <span className="font-mono text-xs text-gray-600 dark:text-gray-400">{selectedNode.subnet || '-'}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">{t('topology.openPorts')}</span>
                <span className="font-mono text-lg font-bold text-blue-600 dark:text-blue-400">{selectedNode.openPortCount}</span>
              </div>
              <div className="flex justify-between items-center py-3 border-b dark:border-gray-700">
                <span className="text-gray-400 dark:text-gray-500 text-xs font-medium">{t('topology.criticalVulns')}</span>
                <span className={`text-lg font-bold ${selectedNode.criticalVulnCount > 0 ? 'text-red-600 dark:text-red-400' : 'text-green-600 dark:text-green-400'}`}>
                  {selectedNode.criticalVulnCount}
                </span>
              </div>

              {selectedNode.serviceLabels?.length > 0 && (
                <div className="py-3 border-b dark:border-gray-700">
                  <span className="text-gray-400 dark:text-gray-500 text-xs font-medium block mb-2">{t('topology.serviceLabels')}</span>
                  <div className="flex flex-wrap gap-1.5">
                    {selectedNode.serviceLabels.map(s => (
                      <span key={s} className="px-2 py-1 bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400 rounded-md text-xs font-mono font-medium border border-blue-100 dark:border-blue-800">
                        {s}
                      </span>
                    ))}
                  </div>
                </div>
              )}

              <div className="pt-4">
                <button onClick={() => navigate(`/assets/${selectedNode.id}`)} className="w-full px-4 py-2.5 bg-blue-600 text-white rounded-lg text-sm font-medium hover:bg-blue-700 transition-colors">
                  {t('topology.viewAssetDetail')}
                </button>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
