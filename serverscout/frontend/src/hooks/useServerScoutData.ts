/**
 * useServerScoutData Hook — provides safe data subset to plugins.
 * Plugins import this to access current platform context.
 */

import { useQuery } from '@tanstack/react-query'
import { fetchAssets, fetchVulnerabilities, fetchScanTasks, fetchDashboardStats, fetchCurrentUser } from '../services/api'
import type { ServerScoutPluginData } from '../types/plugin'

export function useServerScoutData(): ServerScoutPluginData {
  const { data: currentUser } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => fetchCurrentUser(),
  })

  const { data: assetsData } = useQuery({
    queryKey: ['assets'],
    queryFn: () => fetchAssets({ size: 10 }),
  })

  const { data: vulnsData } = useQuery({
    queryKey: ['vulnerabilities'],
    queryFn: () => fetchVulnerabilities({ size: 10 }),
  })

  const { data: tasksData } = useQuery({
    queryKey: ['scan-tasks'],
    queryFn: () => fetchScanTasks({ size: 5 }),
  })

  const { data: statsData } = useQuery({
    queryKey: ['dashboardStats'],
    queryFn: () => fetchDashboardStats(),
  })

  return {
    assets: assetsData?.data?.data?.content || [],
    vulnerabilities: vulnsData?.data?.data?.content || [],
    scanTasks: tasksData?.data?.data?.content || [],
    dashboardStats: statsData?.data?.data || null,
    currentUser: currentUser?.data?.data || null,
  }
}
