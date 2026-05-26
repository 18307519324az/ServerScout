import { Component, useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { ToastProvider } from './hooks/useToast'
import MainLayout from './components/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import AssetListPage from './pages/AssetListPage'
import AssetDetailPage from './pages/AssetDetailPage'
import ScanTaskListPage from './pages/ScanTaskListPage'
import ScanTaskDetailPage from './pages/ScanTaskDetailPage'
import VulnerabilityListPage from './pages/VulnerabilityListPage'
import VulnerabilityDetailPage from './pages/VulnerabilityDetailPage'
import TopologyPage from './pages/TopologyPage'
import ReportCenterPage from './pages/ReportCenterPage'
import SettingsPage from './pages/SettingsPage'
import ExternalIntelPage from './pages/ExternalIntelPage'
import AttackSurfacePage from './pages/AttackSurfacePage'
import ManualPage from './pages/ManualPage'

function reportToBackend(message: string, stack?: string) {
  try {
    const payload = {
      message: message || 'unknown',
      stack: stack || '',
      url: window.location.href,
      userAgent: navigator.userAgent,
    }
    fetch('/api/v1/error-report', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    }).catch(() => {})
  } catch {}
}

if (typeof window !== 'undefined') {
  window.onerror = (msg, url, line, col, error) => {
    reportToBackend(String(msg), error instanceof Error ? error.stack : undefined)
  }
  window.addEventListener('unhandledrejection', (event) => {
    reportToBackend('Unhandled Promise: ' + String(event.reason),
      event.reason instanceof Error ? event.reason.stack : undefined)
  })
}

class ErrorBoundary extends Component<{ children: React.ReactNode }, { hasError: boolean }> {
  constructor(props: { children: React.ReactNode }) {
    super(props)
    this.state = { hasError: false }
  }
  static getDerivedStateFromError() { return { hasError: true } }
  componentDidCatch(error: Error, info: React.ErrorInfo) {
    reportToBackend(error.message, error.stack + '\nComponent Stack: ' + info.componentStack)
  }
  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex items-center justify-center bg-gray-50">
          <div className="text-center">
            <h1 className="text-2xl font-bold text-gray-700 mb-2">页面出错了</h1>
            <p className="text-gray-500 mb-4">请刷新页面或返回首页</p>
            <button onClick={() => { this.setState({ hasError: false }); window.location.href = '/' }}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700">
              返回首页
            </button>
          </div>
        </div>
      )
    }
    return this.props.children
  }
}

function isTokenExpired(token: string): boolean {
  try {
    const payload = JSON.parse(atob(token.split('.')[1]))
    // exp is in seconds since epoch
    return payload.exp ? Date.now() >= payload.exp * 1000 : false
  } catch {
    return true // invalid token → treat as expired
  }
}

function getStoredToken(): string | null {
  const token = localStorage.getItem('token')
  if (!token) return null
  if (isTokenExpired(token)) {
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    return null
  }
  return token
}

function AuthGuard({ children }: { children: React.ReactNode }) {
  const [isAuth, setIsAuth] = useState(() => !!getStoredToken())
  useEffect(() => {
    const check = () => setIsAuth(!!getStoredToken())
    window.addEventListener('storage', check)
    return () => window.removeEventListener('storage', check)
  }, [])
  if (!isAuth) return <Navigate to="/login" />
  return <>{children}</>
}

export default function App() {
  return (
    <ErrorBoundary>
      <ToastProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<AuthGuard><MainLayout /></AuthGuard>}>
            <Route index element={<Navigate to="/dashboard" />} />
            <Route path="dashboard" element={<DashboardPage />} />
            <Route path="assets" element={<AssetListPage />} />
            <Route path="assets/:id" element={<AssetDetailPage />} />
            <Route path="scan-tasks" element={<ScanTaskListPage />} />
            <Route path="scan-tasks/:id" element={<ScanTaskDetailPage />} />
            <Route path="vulnerabilities" element={<VulnerabilityListPage />} />
            <Route path="vulnerabilities/:id" element={<VulnerabilityDetailPage />} />
            <Route path="reports" element={<ReportCenterPage />} />
            <Route path="settings" element={<SettingsPage />} />
            <Route path="topology" element={<TopologyPage />} />
            <Route path="attack-surface" element={<AttackSurfacePage />} />
            <Route path="intel" element={<ExternalIntelPage />} />
            <Route path="manual" element={<ManualPage />} />
          </Route>
        </Routes>
      </BrowserRouter>
      </ToastProvider>
    </ErrorBoundary>
  )
}
