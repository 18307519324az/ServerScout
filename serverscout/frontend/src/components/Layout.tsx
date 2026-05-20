import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { Shield, LayoutDashboard, Server, ScanLine, Bug, Network, FileText, Settings, Globe } from 'lucide-react'

const nav = [
  { path: '/dashboard', label: '仪表盘', icon: LayoutDashboard },
  { path: '/assets', label: '资产管理', icon: Server },
  { path: '/scan-tasks', label: '扫描任务', icon: ScanLine },
  { path: '/vulnerabilities', label: '漏洞管理', icon: Bug },
  { path: '/topology', label: '拓扑图', icon: Network },
  { path: '/intel', label: '外部情报', icon: Globe },
  { path: '/reports', label: '报告中心', icon: FileText },
  { path: '/settings', label: '系统设置', icon: Settings },
]

export default function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()

  const handleLogout = () => {
    localStorage.removeItem('token')
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-56 bg-white border-r border-gray-200 flex flex-col">
        <div className="flex items-center gap-2 px-5 h-16 border-b">
          <Shield className="w-6 h-6 text-blue-600" />
          <span className="font-bold text-lg">ServerScout</span>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {nav.map((item) => {
            const active = location.pathname.startsWith(item.path)
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition
                  ${active ? 'bg-blue-50 text-blue-700' : 'text-gray-600 hover:bg-gray-100'}`}
              >
                <item.icon className="w-4 h-4" />
                {item.label}
              </Link>
            )
          })}
        </nav>
        <div className="p-3 border-t">
          <button
            onClick={handleLogout}
            className="w-full px-3 py-2 text-sm text-gray-600 hover:bg-gray-100 rounded-lg"
          >
            退出登录
          </button>
        </div>
      </aside>

      {/* Main */}
      <main className="flex-1 overflow-auto">
        <div className="p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
