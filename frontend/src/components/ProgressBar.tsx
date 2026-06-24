export default function ProgressBar({ value }: { value: number }) {
  const color = value === 100 ? 'bg-green-500' : 'bg-blue-500'
  return (
    <div className="w-full bg-gray-200 rounded-full h-2">
      <div className={`h-2 rounded-full transition-all duration-500 ${color}`} style={{ width: `${value}%` }} />
    </div>
  )
}
