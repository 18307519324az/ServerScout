import axios from 'axios'
import type {
  ApiResponse, PageData, Asset, ScanTask, Vulnerability,
  DashboardStats, TopologyData, CreateScanTaskRequest,
  Subdomain, SubdomainStats, User,
  HoneypotStats, HoneypotDetectionInfo,
  AiBriefingResult,
} from '../types'

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    return payload.exp ? Date.now() >= payload.exp * 1000 : false
  } catch {
    return true
  }
}

const http = axios.create({ baseURL: '/api', timeout: 30000 })

http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    if (isTokenExpired(token)) {
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      window.location.href = '/login'
      return Promise.reject(new Error('Token expired'))
    }
    config.headers.Authorization = `Bearer ${token}`
  }
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
export const login = (username: string, password: string, captchaId: string, captchaAnswer: string) =>
  http.post<ApiResponse<{ token: string; username: string; role: string }>>('/auth/login', { username, password, captchaId, captchaAnswer })

export const register = (data: {
  username: string; password: string; name: string; gender: string; email: string;
  captchaId: string; captchaAnswer: string;
}) =>
  http.post<ApiResponse<{ token: string; username: string; name: string; role: string }>>('/auth/register', data)

export const fetchPublicKey = () =>
  http.get<ApiResponse<{ publicKey: string }>>('/auth/public-key')

export const fetchCaptcha = () =>
  http.get<ApiResponse<{ captchaId: string; imageBase64: string }>>('/auth/captcha')

// Users
export const fetchCurrentUser = () =>
  http.get<ApiResponse<User>>('/v1/users/me')

export const updateCurrentUser = (data: { name?: string; gender?: string; email?: string }) =>
  http.put<ApiResponse<User>>('/v1/users/me', data)

export const changeCurrentUserPassword = (oldPassword: string, newPassword: string) =>
  http.put<ApiResponse<void>>('/v1/users/me/password', { oldPassword, newPassword })

export const fetchUsers = () =>
  http.get<ApiResponse<User[]>>('/v1/users')

export const createUser = (data: { username: string; password: string; name?: string; gender?: string; role?: string; email?: string }) =>
  http.post<ApiResponse<User>>('/v1/users', data)

export const updateUserApi = (id: number, data: { role?: string; name?: string; gender?: string; email?: string; enabled?: boolean }) =>
  http.put<ApiResponse<User>>(`/v1/users/${id}`, data)

export const resetUserPassword = (id: number, newPassword: string) =>
  http.put<ApiResponse<void>>(`/v1/users/${id}/password`, { newPassword })

export const deleteUser = (id: number) =>
  http.delete<ApiResponse<void>>(`/v1/users/${id}`)

// System Config
export const fetchSystemConfigs = () =>
  http.get<ApiResponse<Record<string, string>>>('/v1/config')

export const detectTools = () =>
  http.get<ApiResponse<Record<string, string>>>('/v1/config/detect-tools')

export const detectSingleTool = (toolName: string) =>
  http.get<ApiResponse<Record<string, string>>>(`/v1/config/detect-tool/${toolName}`)

export const updateSystemConfigs = (configs: Record<string, string>) =>
  http.put<ApiResponse<void>>('/v1/config', configs)

// Assets
export const fetchAssets = (params: { page?: number; size?: number; keyword?: string; status?: string; taskId?: number }) =>
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

export const updateVulnReproduction = (id: number, steps: string) =>
  http.put(`/v1/vulnerabilities/${id}/reproduction`, { steps })

export const deleteVulnerability = (id: number) =>
  http.delete(`/v1/vulnerabilities/${id}`)

// Honeypot
export const fetchHoneypotStats = () =>
  http.get<ApiResponse<HoneypotStats>>('/v1/honeypot/stats')

export const fetchHoneypotDetectionsByAsset = (assetId: number) =>
  http.get<ApiResponse<HoneypotDetectionInfo[]>>(`/v1/honeypot/asset/${assetId}`)

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

export const syncIntel = () =>
  http.post<ApiResponse<any>>('/v1/intel/sync')

// Scan Strategy Plugins (L2)
export const fetchPlugins = () =>
  http.get<ApiResponse<any[]>>('/v1/plugins')

export const fetchPlugin = (id: number) =>
  http.get<ApiResponse<any>>(`/v1/plugins/${id}`)

export const createPlugin = (data: {
  name: string; scanType: string; description?: string;
  commandTemplate: string; resultParser?: string; findingRegex?: string;
}) =>
  http.post<ApiResponse<any>>('/v1/plugins', data)

export const updatePlugin = (id: number, data: {
  name: string; scanType: string; description?: string;
  commandTemplate: string; resultParser?: string; findingRegex?: string;
}) =>
  http.put<ApiResponse<any>>(`/v1/plugins/${id}`, data)

export const togglePlugin = (id: number) =>
  http.patch<ApiResponse<any>>(`/v1/plugins/${id}/toggle`)

export const deletePlugin = (id: number) =>
  http.delete<ApiResponse<void>>(`/v1/plugins/${id}`)

export const fetchScanTypes = () =>
  http.get<ApiResponse<string[]>>('/v1/plugins/scan-types')

// Censys
export const lookupCensysHost = (ip: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/censys/${ip}`)

// VirusTotal
export const lookupVirusTotalIp = (ip: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/virustotal/ip/${ip}`)

export const lookupVirusTotalDomain = (domain: string) =>
  http.get<ApiResponse<any>>(`/v1/intel/virustotal/domain/${domain}`)

// Screenshot
export const captureScreenshot = (url: string, width?: number, height?: number) =>
  http.post<ApiResponse<{ url: string; data: string }>>('/v1/screenshot', { url, width, height })

// Vulnerability Status Audit Log
export const fetchVulnStatusLogs = (vulnId: number) =>
  http.get<ApiResponse<any[]>>(`/v1/vulnerabilities/${vulnId}/logs`)

// Crawler
export const fetchCrawledUrlsByAsset = (assetId: number) =>
  http.get<ApiResponse<any[]>>(`/v1/crawler/asset/${assetId}`)

export const fetchCrawledUrlsByTask = (taskId: number) =>
  http.get<ApiResponse<any[]>>(`/v1/crawler/task/${taskId}`)

export const fetchCrawlerScreenshots = (taskId: number) =>
  http.get<ApiResponse<any[]>>(`/v1/crawler/task/${taskId}/screenshots`)

// Operation Logs (Admin only)
export const fetchOperationLogs = (params: {
  page?: number; size?: number; username?: string; type?: string;
  startTime?: string; endTime?: string;
}) =>
  http.get<ApiResponse<{ content: any[]; page: { size: number; number: number; totalElements: number; totalPages: number } }>>('/v1/operation-logs', { params })

export const fetchOperationLogsByUser = (username: string, params: { page?: number; size?: number }) =>
  http.get<ApiResponse<{ content: any[]; page: any }>>(`/v1/operation-logs/user/${username}`, { params })

export const exportOperationLogs = (params: {
  format?: string; username?: string; type?: string;
  startTime?: string; endTime?: string;
}) =>
  http.get('/v1/operation-logs/export', { params, responseType: 'blob' })

export const fetchOperationLogStats = () =>
  http.get<ApiResponse<any>>('/v1/operation-logs/stats')

export const recalculateVulnerabilityRisk = () =>
  http.post<ApiResponse<{ recalculated: number }>>('/v1/vulnerabilities/recalculate-risk')

export const generateAiBriefing = (evidence: string, locale: string) =>
  http.post<ApiResponse<AiBriefingResult>>('/v1/ai-briefing/generate', { evidence, locale })
