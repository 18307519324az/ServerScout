/**
 * ServerScout Plugin System — L1 Frontend Component Plugin + L2 Backend Scan Strategy Plugin.
 * L1: Dynamic loading of custom dashboard widgets, asset detail panels, report templates, scan task panels.
 * L2: Custom scan strategy plugins (SSH brute, Redis unauth, etc.) managed via backend API.
 */

import type { ComponentType } from 'react'

/** Where a L1 plugin component will be rendered */
export type PluginSlot =
  | 'dashboard-widget'
  | 'asset-detail-panel'
  | 'asset-detail-top'
  | 'report-template'
  | 'scan-task-panel'
  | 'settings-section'

/** Data made available to plugins via useServerScoutData() hook */
export interface ServerScoutPluginData {
  assets: any[]
  vulnerabilities: any[]
  scanTasks: any[]
  dashboardStats: any
  currentUser: any
}

/** A L1 ServerScout plugin — defined by a plugin.js file or built-in registry */
export interface ServerScoutPlugin {
  id: string
  name: string
  description?: string
  version?: string
  slot: PluginSlot
  component: ComponentType<{ data: ServerScoutPluginData }>
}

/** L2 Backend scan strategy plugin metadata (from API) */
export interface ScanStrategyPlugin {
  id: number
  name: string
  scanType: string
  description: string
  commandTemplate: string
  enabled: boolean
  createdAt: string
}

/** Hook exposed to plugins for accessing platform data */
export type UseServerScoutData = () => ServerScoutPluginData

/** Plugin registry — loaded dynamically from /plugins/ directory */
export interface PluginRegistry {
  plugins: ServerScoutPlugin[]
  register: (plugin: ServerScoutPlugin) => void
  getBySlot: (slot: PluginSlot) => ServerScoutPlugin[]
}
