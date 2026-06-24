// Tree-shaken echarts instance — only registers components actually used
import * as echarts from 'echarts/core'
import { BarChart, PieChart, LineChart, TreeChart, RadarChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, GridComponent,
  LegendComponent, ToolboxComponent, PolarComponent,
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

echarts.use([
  BarChart, PieChart, LineChart, TreeChart, RadarChart,
  TitleComponent, TooltipComponent, GridComponent,
  LegendComponent, ToolboxComponent, PolarComponent,
  CanvasRenderer,
])

export default echarts
