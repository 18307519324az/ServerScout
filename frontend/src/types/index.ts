export interface SystemMode {
  mode: 'DEMO' | 'REAL' | 'UNKNOWN'
  demoMode: boolean
  scannerMode: 'DEMO' | 'REAL' | 'UNKNOWN'
  nmapAvailable: boolean
  nucleiAvailable: boolean
  allowPublicTargets: boolean
  configSource: string
  actualBehavior: string
  switchGuide: string
  safetyNotice: string
}

// ========== User ==========
export interface User {
  id: number
  username: string
  name?: string
  gender?: string
  role: string
  email: string
  enabled: boolean
  createdAt: string
}

// ========== Common ==========
export interface ApiResponse<T> {
  code: number
  message: string
  data: T
  timestamp: string
}

export interface AiBriefSection {
  key: string
  title: string
  body: string
  items: string[]
}

export interface AiBriefingResult {
  mode: 'llm' | 'local-analysis'
  language: string
  inputSummary: string
  detectedSignals: Record<string, string[]>
  sections: AiBriefSection[]
  warnings: string[]
}

export interface PageData<T> {
  content: T[]
  page: {
    size: number
    number: number
    totalElements: number
    totalPages: number
  }
}

// ========== Honeypot ==========
export interface HoneypotDetectionInfo {
  id: number
  honeypotType: string
  honeypotCategory: string
  matchEvidence: string
  confidence: string
  detectionMethod: string
  matchedPort: number | null
  matchedAt: string
  ruleName: string
}

export interface HoneypotStats {
  honeypotAssetCount: number
  typeDistribution: { type: string; count: number }[]
}

// ========== Asset ==========
export interface Asset {
  id: number
  ipAddress: string
  hostname: string
  hostnameAliases: string[]
  osFingerprint: string
  status: string
  openPortCount: number
  criticalVulnCount: number
  macAddress?: string
  lastScanTime?: string
  firstSeenTime?: string
  scanCount?: number
  tags: string[]
  isHoneypot?: boolean
  honeypotType?: string
  honeypotConfidence?: string
  honeypotDetections?: HoneypotDetectionInfo[]
  discoveredAt: string
  updatedAt: string
  ports?: PortDetail[]
}

export interface PortDetail {
  id: number
  portNumber: number
  protocol: string
  serviceName: string
  serviceVersion: string
  serviceProduct: string
  state: string
  banner?: string
  isWebService: boolean
  webFingerprint?: WebFingerprintDetail
  sslCertificate?: SslCertBrief
  vulnerabilities?: VulnBrief[]
}

export interface WebFingerprintDetail {
  httpStatus: number
  serverHeader: string
  frameworkName: string
  frameworkVersion: string
  cmsName?: string
  cmsVersion?: string
  wafName?: string
  techStack?: string
  title: string
  faviconHash?: string
  bodyHash?: string
}

export interface SslCertBrief {
  id: number
  subject: string
  issuer: string
  fingerprintSha256: string
  notBefore: string
  notAfter: string
  san: string
  sigAlg: string
  keySize: number
  isExpired: boolean
}

export interface VulnBrief {
  cveId: string
  severity: string
  cvssScore: number
  status: string
}

// ========== Scan Task Stage ==========
export interface ScanTaskStage {
  id: number
  taskId: number
  stageCode: string
  stageName: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'
  progress: number
  startedAt: string | null
  finishedAt: string | null
  durationMs: number | null
  summary: string | null
  errorMessage: string | null
  createdAt: string
  updatedAt: string
}

// ========== Risk Score ==========
export interface RiskScoreDetail {
  id: number
  taskId: number
  assetId: number
  assetIp: string
  assetName: string
  assetExposureScore: number
  vulnerabilitySeverityScore: number
  serviceRiskScore: number
  exploitabilityScore: number
  businessImportanceScore: number
  remediationDeduction: number
  finalRiskScore: number
  riskLevel: string
  riskReason: string
  repairSuggestion: string
}

// ========== Scan Task ==========
export interface ScanTask {
  id: number
  name: string
  targetRange: string
  scanType: string
  portRange?: string
  status: string
  progress: number
  totalAssets: number
  totalPorts: number
  startedAt: string
  completedAt: string
  createdAt: string
  errorMessage?: string
  scanMode?: 'DEMO' | 'REAL' | 'UNKNOWN'
  summary?: ScanSummary
}

export interface ScanSummary {
  assetCount: number
  portCount: number
  webServiceCount: number
  criticalVulnCount: number
  highVulnCount: number
  mediumVulnCount: number
  newAssetCount?: number
  updatedAssetCount?: number
  topPorts: { port: number; count: number }[]
}

export interface CreateScanTaskRequest {
  name: string
  targetRange: string
  scanType: string
  portRange: string
  enableFingerprint: boolean
  enableVulnScan: boolean
  enableCrawler: boolean
  authorized?: boolean
}

// ========== Vulnerability ==========
export interface Vulnerability {
  id: number
  cveId: string
  description: string
  severity: string
  cvssScore: number
  affectedAsset: { id: number; ipAddress: string; hostname: string }
  affectedPort: number
  affectedSoftware: string
  affectedVersion: string
  fixSuggestion: string
  reproductionSteps: string
  status: string
  discoveredAt: string
}

// ========== Dashboard ==========
export interface DashboardStats {
  overview: {
    totalAssets: number
    totalPorts: number
    totalVulnerabilities: number
    criticalVulns: number
    highVulns: number
    mediumVulns: number
    lowVulns: number
    activeTasks: number
    recentScanCount: number
    riskAssetCount: number
    honeypotAssetCount: number
  }
  portDistribution: { port: number; count: number }[]
  severityDistribution: { name: string; value: number }[]
  trend: { date: string; assetsDiscovered: number; vulnsFound: number; vulnsFixed: number }[]
}

// ========== Topology ==========
export interface TopologyData {
  nodes: TopologyNode[]
  links: TopologyLink[]
}

export interface TopologyNode {
  id: number
  ipAddress: string
  hostname: string
  openPortCount: number
  criticalVulnCount: number
  subnet: string
  group: string
  serviceLabels: string[]
}

export interface TopologyLink {
  source: number
  target: number
  type: string
  ports: number[]
}

// ========== Subdomain ==========
export interface Subdomain {
  id: number
  domain: string
  subdomain: string
  ipAddress: string
  source: string
  assetId?: number
  firstSeenTime: string
  lastSeenTime: string
}

export interface SubdomainStats {
  domain: string
  totalSubdomains: number
  resolvedCount: number
  sources: string[]
  subdomains: Subdomain[]
}

// ========== Crawled URL ==========
export interface CrawledUrl {
  id: number
  url: string
  path: string
  httpStatus: number
  contentType: string
  title: string
  bodyText: string
  linksFound: number
  crawlDepth: number
  responseTimeMs: number
  isDynamic: boolean
  screenshotPath: string | null
  crawledAt: string
  asset?: { id: number; ipAddress: string }
  port?: { id: number; portNumber: number; serviceName: string }
}

// ========== Operation Log ==========
export interface OperationLog {
  id: number
  userId: number | null
  username: string
  operationType: string
  target: string
  detail: string
  ipAddress: string
  geoLocation: string
  userAgent: string
  requestMethod: string
  requestUri: string
  statusCode: number
  durationMs: number
  createdAt: string
}
