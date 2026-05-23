import { ChevronLeft, ChevronRight } from 'lucide-react'

interface PaginationProps {
  page: number
  totalPages: number
  pageSize: number
  totalElements?: number
  onPageChange: (page: number) => void
  onPageSizeChange: (size: number) => void
  pageSizeOptions?: number[]
}

export default function Pagination({
  page, totalPages, pageSize, totalElements,
  onPageChange, onPageSizeChange, pageSizeOptions = [5, 10, 20, 50],
}: PaginationProps) {
  if (totalPages <= 0) return null

  return (
    <div className="flex items-center justify-between mt-4 flex-wrap gap-3">
      <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
        <span>每页</span>
        <select
          value={pageSize}
          onChange={(e) => onPageSizeChange(Number(e.target.value))}
          className="border dark:border-gray-600 rounded px-2 py-0.5 text-sm bg-white dark:bg-gray-800 dark:text-gray-200 focus:ring-2 focus:ring-blue-500 outline-none"
        >
          {pageSizeOptions.map(s => (
            <option key={s} value={s}>{s} 条</option>
          ))}
        </select>
        {totalElements !== undefined && (
          <span>共 {totalElements} 条</span>
        )}
      </div>

      <div className="flex items-center gap-1">
        <button
          disabled={page === 0}
          onClick={() => onPageChange(Math.max(0, page - 1))}
          className="flex items-center gap-0.5 px-2.5 py-1.5 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
        >
          <ChevronLeft className="w-3.5 h-3.5" />
          上一页
        </button>

        {/* Page numbers */}
        {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
          let pageNum: number
          if (totalPages <= 7) {
            pageNum = i
          } else if (page < 3) {
            pageNum = i
          } else if (page > totalPages - 4) {
            pageNum = totalPages - 7 + i
          } else {
            pageNum = page - 3 + i
          }
          return (
            <button
              key={pageNum}
              onClick={() => onPageChange(pageNum)}
              className={`w-8 h-8 rounded text-sm ${
                pageNum === page
                  ? 'bg-blue-600 text-white'
                  : 'border dark:border-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700'
              }`}
            >
              {pageNum + 1}
            </button>
          )
        })}

        <button
          disabled={page >= totalPages - 1}
          onClick={() => onPageChange(Math.min(totalPages - 1, page + 1))}
          className="flex items-center gap-0.5 px-2.5 py-1.5 border dark:border-gray-600 rounded text-sm disabled:opacity-30 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700"
        >
          下一页
          <ChevronRight className="w-3.5 h-3.5" />
        </button>
      </div>
    </div>
  )
}
