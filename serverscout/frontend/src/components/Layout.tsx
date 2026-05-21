import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { Shield, LayoutDashboard, Server, ScanLine, Bug, Network, FileText, Settings, Globe, Moon, Sun, Map } from 'lucide-react'
import useDarkMode from '../hooks/useDarkMode'

const nav = [
  { path: '/dashboard', label: '仪表盘', icon: LayoutDashboard },
  { path: '/assets', label: '资产管理', icon: Server },
  { path: '/scan-tasks', label: '扫描任务', icon: ScanLine },
  { path: '/vulnerabilities', label: '漏洞管理', icon: Bug },
  { path: '/topology', label: '拓扑图', icon: Network },
  { path: '/attack-surface', label: '攻击面地图', icon: Map },
  { path: '/intel', label: '外部情报', icon: Globe },
  { path: '/reports', label: '报告中心', icon: FileText },
  { path: '/settings', label: '系统设置', icon: Settings },
]

export default function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { dark, toggle } = useDarkMode()

  const handleLogout = () => {
    localStorage.removeItem('token')
    navigate('/login')
  }

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-900">
      {/* Sidebar */}
      <aside className="w-56 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex flex-col">
        <div className="flex items-center gap-2 px-5 h-16 border-b dark:border-gray-700">
          <Shield className="w-6 h-6 text-blue-600" />
          <span className="font-bold text-lg dark:text-white">ServerScout</span>
        </div>
        <nav className="flex-1 p-3 space-y-1">
          {nav.map((item) => {
            const active = location.pathname.startsWith(item.path)
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition
                  ${active ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'}`}
              >
                <item.icon className="w-4 h-4" />
                {item.label}
              </Link>
            )
          })}
        </nav>
        <div className="p-3 border-t dark:border-gray-700 space-y-2">
          <button
            onClick={toggle}
            className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
            title={dark ? '切换到亮色模式' : '切换到暗色模式'}
          >
            {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
            {dark ? '亮色模式' : '暗色模式'}
          </button>
          <button
            onClick={handleLogout}
            className="w-full px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
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
