import ReactEChartsCore from 'echarts-for-react'

const colorMap: Record<string, string> = {
  critical: '#ef4444',
  high: '#f97316',
  medium: '#eab308',
  low: '#3b82f6',
}

interface Props {
  data: { name: string; value: number }[]
  height?: number
}

export default function SeverityPieChart({ data, height = 250 }: Props) {
  return (
    <ReactEChartsCore
      option={{
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        legend: { bottom: 0 },
        series: [
          {
            type: 'pie',
            radius: ['45%', '72%'],
            center: ['50%', '45%'],
            data: data.map((s) => ({
              name: s.name,
              value: s.value,
              itemStyle: { color: colorMap[s.name] || '#94a3b8' },
            })),
            label: { formatter: '{b}\n{c}', fontSize: 11 },
          },
        ],
      }}
      style={{ height }}
    />
  )
}