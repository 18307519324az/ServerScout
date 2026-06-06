import { AlertTriangle } from 'lucide-react'

interface Props {
  open: boolean
  title: string
  message: string
  confirmLabel?: string
  variant?: 'danger' | 'default'
  onConfirm: () => void
  onCancel: () => void
}

export default function ConfirmDialog({
  open,
  title,
  message,
  confirmLabel = '确认',
  variant = 'default',
  onConfirm,
  onCancel,
}: Props) {
  if (!open) return null

  return (
    <div className="fixed inset-0 bg-black/50 z-[60] flex items-center justify-center">
      <div className="bg-white dark:bg-gray-800 dark:text-white rounded-xl w-96 max-w-[calc(100vw-2rem)] p-6 shadow-xl">
        <div className="flex items-start gap-3">
          {variant === 'danger' && (
            <div className="w-10 h-10 rounded-full bg-red-100 flex items-center justify-center flex-shrink-0">
              <AlertTriangle className="w-5 h-5 text-red-600" />
            </div>
          )}
          <div>
            <h3 className="font-semibold text-lg">{title}</h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{message}</p>
          </div>
        </div>
        <div className="flex justify-end gap-2 mt-6">
          <button onClick={onCancel} className="px-4 py-2 text-sm rounded-lg border dark:border-gray-600 hover:bg-gray-50 dark:hover:bg-gray-700">
            取消
          </button>
          <button
            onClick={onConfirm}
            className={`px-4 py-2 text-sm rounded-lg text-white ${
              variant === 'danger' ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-600 hover:bg-blue-700'
            }`}
          >
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
