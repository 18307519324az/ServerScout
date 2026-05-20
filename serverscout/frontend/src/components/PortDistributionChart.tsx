import ReactEChartsCore from 'echarts-for-react'

interface Props {
  data: { port: number; count: number }[]
  height?: number
}

export default function PortDistributionChart({ data, height = 250 }: Props) {
  return (
    <ReactEChartsCore
      option={{
        tooltip: { trigger: 'axis' },
        grid: { left: 40, right: 20, top: 10, bottom: 30 },
        xAxis: {
          type: 'category',
          data: data.map((p) => p.port),
          axisLabel: { fontSize: 11 },
        },
        yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
        series: [
          {
            type: 'bar',
            data: data.map((p) => p.count),
            itemStyle: { color: '#3b82f6', borderRadius: [4, 4, 0, 0] },
          },
        ],
      }}
      style={{ height }}
    />
  )
}