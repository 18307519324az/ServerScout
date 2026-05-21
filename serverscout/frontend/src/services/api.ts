import axios from 'axios'
import type {
  ApiResponse, PageData, Asset, ScanTask, Vulnerability,
  DashboardStats, TopologyData, CreateScanTaskRequest,
  Subdomain, SubdomainStats,
} from '../types'

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

// Auth
export const login = (username: string, password: string) =>
  http.post<ApiResponse<{ token: string; username: string }>>('/auth/login', { username, password })

// Assets
export const fetchAssets = (params: { page?: number; size?: number; keyword?: string; status?: string }) =>
  http.get<ApiResponse<PageData<Asset>>>('/v1/assets', { params })

export const fetchAssetDetail = (id: number) =>
  http.get<ApiResponse<Asset>>(`/v1/assets/${id}`)

export const updateAssetTags = (id: number, tags: string[]) =>
  http.put(`/v1/assets/${id}/tags`, tags)

export const deleteAsset = (id: number) =>
  http.delete(`/v1/assets/${id}`)

export const fetchAttackSurface = () =>
  http.get<ApiResponse<any>>('/v1/assets/attack-surface')

export const mergeAssets = (sourceIds: number[], targetId: number) =>
  http.post<ApiResponse<Asset>>('/v1/assets/merge', { sourceIds, targetId })

export const fetchTopology = () =>
  http.get<ApiResponse<TopologyData>>('/v1/assets/topology')

// Scan Tasks
export const createScanTask = (data: CreateScanTaskRequest) =>
  http.post<ApiResponse<ScanTask>>('/v1/scan-tasks', data)

export const fetchScanTasks = (params: { page?: number; size?: number; status?: string }) =>
  http.get<ApiResponse<PageData<ScanTask>>>('/v1/scan-tasks', { params })

export const fetchScanTaskDetail = (id: number) =>
  http.get<ApiResponse<ScanTask>>(`/v1/scan-tasks/${id}`)

export const cancelScanTask = (id: number) =>
  http.post(`/v1/scan-tasks/${id}/cancel`)

export const deleteScanTask = (id: number) =>
  http.delete(`/v1/scan-tasks/${id}`)

// Vulnerabilities
export const fetchVulnerabilities = (params: { page?: number; size?: number; severity?: string; status?: string; assetId?: number }) =>
  http.get<ApiResponse<PageData<Vulnerability>>>('/v1/vulnerabilities', { params })

export const fetchVulnerabilityDetail = (id: number) =>
  http.get<ApiResponse<Vulnerability>>(`/v1/vulnerabilities/${id}`)

export const updateVulnStatus = (id: number, status: string) =>
  http.put(`/v1/vulnerabilities/${id}/status`, { status })

export const deleteVulnerability = (id: number) =>
  http.delete(`/v1/vulnerabilities/${id}`)

// Dashboard
export const fetchDashboardStats = () =>
  http.get<ApiResponse<DashboardStats>>('/v1/dashboard/stats')

export const fetchTechStack = () =>
  http.get<ApiResponse<any>>('/v1/dashboard/tech-stack')

// Subdomains
export const enumerateSubdomains = (domain: string) =>
  http.post<ApiResponse<{ domain: string; total: number; newCount: number; sources: string[] }>>('/v1/subdomains/enumerate', { domain })

export const fetchSubdomainsByDomain = (domain: string) =>
  http.get<ApiResponse<SubdomainStats>>(`/v1/subdomains/domain/${domain}`)

export const fetchSubdomainsByAsset = (assetId: number) =>
  http.get<ApiResponse<Subdomain[]>>(`/v1/subdomains/asset/${assetId}`)

// Reports
export const downloadPdfReport = (taskId: number) =>
  http.get('/v1/reports/pdf', { params: { taskId }, responseType: 'blob' })

export const downloadExcelReport = (taskId: number) =>
  http.get('/v1/reports/excel', { params: { taskId }, responseType: 'blob' })

// External Threat Intelligence
export const lookupIpIntel = (ip: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/ip/${ip}`)

export const lookupCveDetails = (cveId: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/cve/${cveId}`)

export const searchCvesExternal = (params: { keyword: string; page?: number; size?: number }) =>
  http.get<ApiResponse<any>>('/v1/intel/cves/search', { params })

export const getLatestCves = (limit: number = 20) =>
  http.get<ApiResponse<any>>('/v1/intel/cves/latest', { params: { limit } })

export const getEpssScore = (cveId: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/cve/${cveId}/epss`)

export const lookupDomainIntel = (domain: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/domain/${domain}`)

export const lookupIpReputation = (ip: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/ip/${ip}/reputation`)

export const getCombinedReport = (target: string) =>
  http.get<ApiResponse<any>>('/v1/intel/report', { params: { target } })
