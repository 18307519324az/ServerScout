const colors: Record<string, string> = {
  critical: 'bg-red-100 text-red-700',
  high: 'bg-orange-100 text-orange-700',
  medium: 'bg-yellow-100 text-yellow-700',
  low: 'bg-blue-100 text-blue-700',
}

const labels: Record<string, string> = {
  critical: '严重',
  high: '高危',
  medium: '中危',
  low: '低危',
}

export default function SeverityBadge({ severity }: { severity: string }) {
  return (
    <span className={`inline-block px-2 py-0.5 rounded text-xs font-medium ${colors[severity] || 'bg-gray-100'}`}>
      {labels[severity] || severity}
    </span>
  )
}