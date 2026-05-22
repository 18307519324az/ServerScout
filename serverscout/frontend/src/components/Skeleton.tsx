export function CardSkeleton() {
  return (
    <div className="animate-pulse bg-white rounded-xl border p-6">
      <div className="h-4 bg-gray-200 rounded w-1/3 mb-4" />
      <div className="h-8 bg-gray-200 rounded w-1/2 mb-2" />
      <div className="h-3 bg-gray-200 rounded w-2/3" />
    </div>
  )
}

export function TableSkeleton({ rows = 5 }: { rows?: number }) {
  return (
    <div className="animate-pulse">
      <div className="h-10 bg-gray-100 rounded-t-lg" />
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="h-14 border-b flex items-center px-4 gap-4">
          <div className="h-4 bg-gray-200 rounded w-24" />
          <div className="h-4 bg-gray-200 rounded w-32" />
          <div className="h-4 bg-gray-200 rounded w-20" />
          <div className="h-4 bg-gray-200 rounded w-16 ml-auto" />
        </div>
      ))}
    </div>
  )
}

export function ChartSkeleton() {
  return (
    <div className="animate-pulse bg-white rounded-xl border p-6 h-64 flex items-center justify-center">
      <div className="h-48 w-48 bg-gray-200 rounded-full" />
    </div>
  )
}
