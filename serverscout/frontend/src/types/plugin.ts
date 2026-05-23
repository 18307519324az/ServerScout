/**
 * ServerScout Plugin System — L1 Frontend Component Plugin.
 * Allows dynamic loading of custom dashboard widgets, asset detail panels, and report templates.
 */

import type { ComponentType } from 'react'

/** Where a plugin component will be rendered */
export type PluginSlot = 'dashboard-widget' | 'asset-detail-panel' | 'report-template' | 'scan-task-panel'

/** Data made available to plugins via useServerScoutData() hook */
export interface ServerScoutPluginData {
  assets: any[]
  vulnerabilities: any[]
  scanTasks: any[]
  dashboardStats: any
  currentUser: any
}

/** A ServerScout plugin — defined by a plugin.js file */
export interface ServerScoutPlugin {
  /** Unique plugin identifier */
  id: string
  /** Display name */
  name: string
  /** Short description */
  description?: string
  /** Version string */
  version?: string
  /** Where to render this plugin */
  slot: PluginSlot
  /** The React component to render */
  component: ComponentType<{ data: ServerScoutPluginData }>
}

/** Hook exposed to plugins for accessing platform data */
export type UseServerScoutData = () => ServerScoutPluginData

/** Plugin registry — loaded dynamically from /plugins/ directory */
export interface PluginRegistry {
  plugins: ServerScoutPlugin[]
  register: (plugin: ServerScoutPlugin) => void
  getBySlot: (slot: PluginSlot) => ServerScoutPlugin[]
}
