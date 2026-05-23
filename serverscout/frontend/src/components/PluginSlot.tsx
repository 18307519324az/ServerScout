import { Suspense, lazy, Component, type ReactNode } from 'react'
import type { PluginSlot as SlotType } from '../types/plugin'

class PluginErrorBoundary extends Component<
  { children: ReactNode; pluginName: string },
  { hasError: boolean }
> {
  state = { hasError: false }
  static getDerivedStateFromError() { return { hasError: true } }
  render() {
    if (this.state.hasError) {
      return (
        <div className="p-3 border border-red-200 bg-red-50 rounded-lg text-xs text-red-600">
          Plugin "{this.props.pluginName}" failed to load
        </div>
      )
    }
    return this.props.children
  }
}

interface PluginSlotProps {
  slot: SlotType
}

const builtinPlugins: { id: string; name: string; slot: SlotType; component: () => Promise<{ default: React.ComponentType<any> }> }[] = [
  {
    id: 'quick-stats-widget',
    name: 'Quick Stats',
    slot: 'dashboard-widget',
    component: () => import('../plugins/widgets/QuickStatsWidget'),
  },
  {
    id: 'vuln-summary-widget',
    name: 'Vulnerability Summary',
    slot: 'dashboard-widget',
    component: () => import('../plugins/widgets/VulnSummaryWidget'),
  },
  {
    id: 'asset-quick-actions',
    name: 'Asset Quick Actions',
    slot: 'asset-detail-top',
    component: () => import('../plugins/widgets/AssetQuickActions'),
  },
]

export default function PluginSlot({ slot }: PluginSlotProps) {
  const plugins = builtinPlugins.filter((p) => p.slot === slot)
  if (plugins.length === 0) return null

  return (
    <div className="plugin-slot space-y-4">
      {plugins.map((plugin) => {
        const LazyComponent = lazy(plugin.component)
        return (
          <PluginErrorBoundary key={plugin.id} pluginName={plugin.name}>
            <Suspense fallback={
              <div className="p-4 border rounded-lg bg-gray-50 animate-pulse">
                <div className="h-4 bg-gray-200 rounded w-24 mb-2" />
                <div className="h-3 bg-gray-200 rounded w-48" />
              </div>
            }>
              <LazyComponent />
            </Suspense>
          </PluginErrorBoundary>
        )
      })}
    </div>
  )
}
