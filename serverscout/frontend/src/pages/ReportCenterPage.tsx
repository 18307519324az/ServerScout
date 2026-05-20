import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { fetchScanTasks, downloadPdfReport, downloadExcelReport } from '../services/api'
import { FileText, Download, Loader2 } from 'lucide-react'

export default function ReportCenterPage() {
  const [selectedTaskId, setSelectedTaskId] = useState<number | null>(null)

  const { data: tasksData, isLoading } = useQuery({
    queryKey: ['scan-tasks', 'completed'],
    queryFn: () => fetchScanTasks({ size: 100, status: 'completed' }),
  })

  const tasks = tasksData?.data?.data?.content || []

  const pdfMutation = useMutation({
    mutationFn: (taskId: number) => downloadPdfReport(taskId),
  })

  const excelMutation = useMutation({
    mutationFn: (taskId: number) => downloadExcelReport(taskId),
  })

  const handleDownload = (format: 'pdf' | 'excel', taskId: number) => {
    const fn = format === 'pdf' ? pdfMutation : excelMutation
    fn.mutate(taskId, {
      onSuccess: (res: any) => {
        const ext = format === 'pdf' ? 'pdf' : 'xlsx'
        const blob = new Blob([res.data], {
          type: format === 'pdf' ? 'application/pdf' : 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        })
        const url = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = url
        a.download = `report_${taskId}.${ext}`
        a.click()
        URL.revokeObjectURL(url)
      },
    })
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6">报告中心</h1>

      <div className="bg-white rounded-xl border shadow-sm p-6">
        <h2 className="font-semibold mb-4">导出扫描报告</h2>
        <p className="text-sm text-gray-500 mb-4">
          选择一个已完成的扫描任务，导出漏洞和安全分析报告。
        </p>

        {isLoading ? (
          <div className="text-center py-10 text-gray-400">
            <Loader2 className="w-6 h-6 animate-spin mx-auto" />
          </div>
        ) : tasks.length === 0 ? (
          <div className="text-center py-10 text-gray-400">
            暂无已完成的扫描任务
          </div>
        ) : (
          <div className="space-y-2">
            {tasks.map((task: any) => (
              <div
                key={task.id}
                className={`flex items-center justify-between p-4 rounded-lg border transition cursor-pointer ${
                  selectedTaskId === task.id ? 'border-blue-500 bg-blue-50' : 'hover:bg-gray-50'
                }`}
                onClick={() => setSelectedTaskId(task.id)}
              >
                <div className="flex items-center gap-3">
                  <FileText className="w-5 h-5 text-gray-400" />
                  <div>
                    <p className="font-medium">{task.name}</p>
                    <p className="text-xs text-gray-400">
                      {task.targetRange} · {task.totalAssets} 资产 · {task.totalPorts} 端口
                    </p>
                  </div>
                </div>
                {selectedTaskId === task.id && (
                  <div className="flex gap-2">
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDownload('pdf', task.id) }}
                      disabled={pdfMutation.isPending}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
                    >
                      <Download className="w-4 h-4" /> PDF
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDownload('excel', task.id) }}
                      disabled={excelMutation.isPending}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
                    >
                      <Download className="w-4 h-4" /> Excel
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}