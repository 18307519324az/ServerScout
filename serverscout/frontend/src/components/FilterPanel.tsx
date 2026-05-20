interface FilterOption {
  label: string
  value: string
}

interface Props {
  label: string
  options: FilterOption[]
  selected: string
  onChange: (value: string) => void
}

export default function FilterPanel({ label, options, selected, onChange }: Props) {
  return (
    <div className="flex items-center gap-2">
      <span className="text-sm text-gray-500">{label}:</span>
      <div className="flex gap-1">
        {options.map((opt) => (
          <button
            key={opt.value}
            onClick={() => onChange(opt.value)}
            className={`px-3 py-1 rounded text-xs font-medium transition ${
              selected === opt.value
                ? 'bg-blue-600 text-white'
                : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
            }`}
          >
            {opt.label}
          </button>
        ))}
      </div>
    </div>
  )
}