import { useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { fetchScanTasks, downloadPdfReport, downloadExcelReport } from '../services/api'
import { FileText, Download, Loader2 } from 'lucide-react'

export default function ReportCenterPage() {
  const { t } = useTranslation()
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
      <h1 className="text-2xl font-bold mb-6 dark:text-white">{t('reports.title')}</h1>

      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6">
        <h2 className="font-semibold mb-4 dark:text-white">{t('reports.selectTask')}</h2>
        <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
          {t('reports.tipsContent')}
        </p>

        {isLoading ? (
          <div className="text-center py-10 text-gray-400 dark:text-gray-500">
            <Loader2 className="w-6 h-6 animate-spin mx-auto" />
          </div>
        ) : tasks.length === 0 ? (
          <div className="text-center py-10 text-gray-400 dark:text-gray-500">
            {t('reports.noTasks')}
          </div>
        ) : (
          <div className="space-y-2">
            {tasks.map((task: any) => (
              <div
                key={task.id}
                className={`flex items-center justify-between p-4 rounded-lg border transition cursor-pointer ${
                  selectedTaskId === task.id
                    ? 'border-blue-500 bg-blue-50 dark:bg-blue-900/20'
                    : 'border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-700'
                }`}
                onClick={() => setSelectedTaskId(task.id)}
              >
                <div className="flex items-center gap-3">
                  <FileText className="w-5 h-5 text-gray-400 dark:text-gray-500" />
                  <div>
                    <p className="font-medium dark:text-white">{task.name}</p>
                    <p className="text-xs text-gray-400 dark:text-gray-500">
                      {task.targetRange} · {task.totalAssets} {t('assets.asset')} · {task.totalPorts} {t('common.openPorts')}
                    </p>
                    <p className="text-xs mt-1">
                      {task.scanMode === 'DEMO' ? (
                        <span className="text-yellow-600 dark:text-yellow-400">数据来源：演示模式</span>
                      ) : task.scanMode === 'REAL' ? (
                        <span className="text-blue-600 dark:text-blue-400">数据来源：真实扫描</span>
                      ) : (
                        <span className="text-gray-400 dark:text-gray-500">数据来源：未记录</span>
                      )}
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
                      <Download className="w-4 h-4" /> {t('reports.exportPdf')}
                    </button>
                    <button
                      onClick={(e) => { e.stopPropagation(); handleDownload('excel', task.id) }}
                      disabled={excelMutation.isPending}
                      className="flex items-center gap-1 px-3 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
                    >
                      <Download className="w-4 h-4" /> {t('reports.exportExcel')}
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
