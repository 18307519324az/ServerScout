import { useEffect, useRef } from 'react'
import * as d3 from 'd3'
import { X } from 'lucide-react'
import { useQuery } from '@tanstack/react-query'
import { fetchTopology } from '../services/api'

interface Props {
  open: boolean
  onClose: () => void
}

export default function TopologyDialog({ open, onClose }: Props) {
  const svgRef = useRef<SVGSVGElement>(null)
  const { data } = useQuery({ queryKey: ['topology'], queryFn: () => fetchTopology(), enabled: open })

  useEffect(() => {
    if (!open || !data?.data?.data || !svgRef.current) return

    const { nodes, links } = data.data.data
    const width = 800
    const height = 500

    const svg = d3.select(svgRef.current)
    svg.selectAll('*').remove()
    svg.attr('width', width).attr('height', height)

    const colorScale = d3.scaleThreshold<number, string>()
      .domain([0, 1, 3, 5])
      .range(['#22c55e', '#f59e0b', '#f97316', '#ef4444'])

    const simulation = d3.forceSimulation(nodes as d3.SimulationNodeDatum[])
      .force('link', d3.forceLink(links).id((d: any) => d.id).distance(100))
      .force('charge', d3.forceManyBody().strength(-200))
      .force('center', d3.forceCenter(width / 2, height / 2))

    const link = svg.append('g')
      .selectAll('line')
      .data(links)
      .join('line')
      .attr('stroke', '#cbd5e1')
      .attr('stroke-width', 1)

    const node = svg.append('g')
      .selectAll('circle')
      .data(nodes)
      .join('circle')
      .attr('r', (d: any) => Math.max(8, Math.min(20, d.openPortCount * 2)))
      .attr('fill', (d: any) => colorScale(d.criticalVulnCount))
      .attr('stroke', '#fff')
      .attr('stroke-width', 2)
      .style('cursor', 'pointer')
      .append('title')
      .text((d: any) => `${d.ipAddress}\n开放端口: ${d.openPortCount}\n高危漏洞: ${d.criticalVulnCount}`)

    const label = svg.append('g')
      .selectAll('text')
      .data(nodes)
      .join('text')
      .text((d: any) => d.ipAddress)
      .attr('font-size', '10px')
      .attr('dx', 12)
      .attr('dy', 4)

    simulation.on('tick', () => {
      link.attr('x1', (d: any) => d.source.x).attr('y1', (d: any) => d.source.y)
        .attr('x2', (d: any) => d.target.x).attr('y2', (d: any) => d.target.y)
      node.attr('cx', (d: any) => d.x).attr('cy', (d: any) => d.y)
      label.attr('x', (d: any) => d.x).attr('y', (d: any) => d.y)
    })
  }, [data, open])

  if (!open) return null

  return (
    <div className="fixed inset-0 bg-black/50 z-50 flex items-center justify-center">
      <div className="bg-white rounded-xl w-[900px] max-h-[700px] p-6">
        <div className="flex justify-between items-center mb-4">
          <h2 className="font-bold text-lg">资产拓扑图</h2>
          <button onClick={onClose} className="p-1 hover:bg-gray-100 rounded"><X className="w-5 h-5" /></button>
        </div>
        <svg ref={svgRef} className="flex justify-center" />
        <div className="flex gap-4 mt-3 text-xs text-gray-500 justify-center">
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-green-500 inline-block" /> 安全</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-yellow-500 inline-block" /> 低风险</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-orange-500 inline-block" /> 中风险</span>
          <span className="flex items-center gap-1"><span className="w-3 h-3 rounded-full bg-red-500 inline-block" /> 高风险</span>
        </div>
      </div>
    </div>
  )
}
