const colors: Record<string, string> = {
  running: 'bg-blue-100 text-blue-700',
  completed: 'bg-green-100 text-green-700',
  failed: 'bg-red-100 text-red-700',
  pending: 'bg-gray-100 text-gray-600',
  cancelled: 'bg-yellow-100 text-yellow-700',
  open: 'bg-red-100 text-red-700',
  fixed: 'bg-green-100 text-green-700',
  confirmed: 'bg-blue-100 text-blue-700',
  false_positive: 'bg-gray-100 text-gray-500',
  alive: 'bg-green-100 text-green-700',
  dead: 'bg-red-100 text-red-700',
  critical: 'bg-red-100 text-red-700',
  high: 'bg-orange-100 text-orange-700',
  medium: 'bg-yellow-100 text-yellow-700',
  low: 'bg-blue-100 text-blue-700',
}

export default function StatusBadge({ status }: { status: string }) {
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${colors[status] || 'bg-gray-100'}`}>
      {status}
    </span>
  )
}
