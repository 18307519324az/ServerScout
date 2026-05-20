import ReactEChartsCore from 'echarts-for-react'

interface Props {
  data: { date: string; assetsDiscovered: number; vulnsFound: number; vulnsFixed: number }[]
  height?: number
}

export default function TrendLineChart({ data, height = 250 }: Props) {
  return (
    <ReactEChartsCore
      option={{
        tooltip: { trigger: 'axis' },
        legend: { bottom: 0, textStyle: { fontSize: 11 } },
        grid: { left: 40, right: 20, top: 10, bottom: 40 },
        xAxis: {
          type: 'category',
          data: data.map((d) => d.date.slice(5)),
          axisLabel: { fontSize: 11 },
        },
        yAxis: { type: 'value', axisLabel: { fontSize: 11 } },
        series: [
          {
            name: '资产发现',
            type: 'line',
            data: data.map((d) => d.assetsDiscovered),
            smooth: true,
            itemStyle: { color: '#3b82f6' },
          },
          {
            name: '漏洞发现',
            type: 'line',
            data: data.map((d) => d.vulnsFound),
            smooth: true,
            itemStyle: { color: '#ef4444' },
          },
          {
            name: '漏洞修复',
            type: 'line',
            data: data.map((d) => d.vulnsFixed),
            smooth: true,
            itemStyle: { color: '#22c55e' },
          },
        ],
      }}
      style={{ height }}
    />
  )
}