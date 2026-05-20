import { Component, useState, useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
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

class ErrorBoundary extends Component<{ children: React.ReactNode }, { hasError: boolean }> {
  constructor(props: { children: React.ReactNode }) {
    super(props)
    this.state = { hasError: false }
  }
  static getDerivedStateFromError() { return { hasError: true } }
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

function AuthGuard({ children }: { children: React.ReactNode }) {
  const [isAuth, setIsAuth] = useState(!!localStorage.getItem('token'))
  useEffect(() => {
    const check = () => setIsAuth(!!localStorage.getItem('token'))
    window.addEventListener('storage', check)
    return () => window.removeEventListener('storage', check)
  }, [])
  if (!isAuth) return <Navigate to="/login" />
  return <>{children}</>
}

export default function App() {
  return (
    <ErrorBoundary>
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
          </Route>
        </Routes>
      </BrowserRouter>
    </ErrorBoundary>
  )
}
