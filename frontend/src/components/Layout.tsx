import { useState } from 'react'
import { Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import { Shield, LayoutDashboard, Server, ScanLine, Bug, Network, FileText, Settings, Globe, Moon, Sun, Map, Menu, X, Languages, BookOpen, Bot } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import useDarkMode from '../hooks/useDarkMode'

const navKeys = [
  { path: '/dashboard', key: 'dashboard', icon: LayoutDashboard },
  { path: '/assets', key: 'assets', icon: Server },
  { path: '/scan-tasks', key: 'scanTasks', icon: ScanLine },
  { path: '/vulnerabilities', key: 'vulnerabilities', icon: Bug },
  { path: '/topology', key: 'topology', icon: Network },
  { path: '/attack-surface', key: 'attackSurface', icon: Map },
  { path: '/intel', key: 'intel', icon: Globe },
  { path: '/ai-briefing', key: 'aiBriefing', icon: Bot },
  { path: '/reports', key: 'reports', icon: FileText },
  { path: '/settings', key: 'settings', icon: Settings },
]

export default function MainLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const { dark, toggle } = useDarkMode()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const { t, i18n } = useTranslation()

  const toggleLang = () => {
    const next = i18n.language === 'zh' ? 'en' : 'zh'
    i18n.changeLanguage(next)
    localStorage.setItem('lang', next)
  }

  const handleLogout = () => {
    localStorage.removeItem('token')
    localStorage.removeItem('role')
    navigate('/')
  }

  const closeSidebar = () => setSidebarOpen(false)

  const sidebarContent = (
    <>
      <div className="flex items-center justify-between px-5 h-16 border-b dark:border-gray-700">
        <div className="flex items-center gap-2">
          <Shield className="w-6 h-6 text-blue-600" />
          <span className="font-bold text-lg dark:text-white">ServerScout</span>
        </div>
        <button onClick={closeSidebar} className="lg:hidden p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
          <X className="w-5 h-5 dark:text-white" />
        </button>
      </div>
      <nav className="flex-1 p-3 space-y-1">
        {navKeys.map((item) => {
          const active = location.pathname.startsWith(item.path)
          return (
            <Link
              key={item.path}
              to={item.path}
              onClick={closeSidebar}
              className={`flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition
                ${active ? 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400' : 'text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700'}`}
            >
              <item.icon className="w-4 h-4" />
              {t(`nav.${item.key}`)}
            </Link>
          )
        })}
      </nav>
      <div className="p-3 border-t dark:border-gray-700 space-y-2">
        <Link
          to="/manual"
          onClick={closeSidebar}
          className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
        >
          <BookOpen className="w-4 h-4" />
          {i18n.language === 'zh' ? '用户手册' : 'User Manual'}
        </Link>
        <button onClick={toggleLang}
          className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
        >
          <Languages className="w-4 h-4" />
          {i18n.language === 'zh' ? 'English' : '中文'}
        </button>
        <button
          onClick={toggle}
          className="w-full flex items-center gap-2 px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
        >
          {dark ? <Sun className="w-4 h-4" /> : <Moon className="w-4 h-4" />}
          {dark ? t('nav.lightMode') : t('nav.darkMode')}
        </button>
        <button
          onClick={handleLogout}
          className="w-full px-3 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
        >
          {t('nav.logout')}
        </button>
      </div>
    </>
  )

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-900">
      {/* Desktop Sidebar */}
      <aside className="hidden lg:flex w-56 bg-white dark:bg-gray-800 border-r border-gray-200 dark:border-gray-700 flex-col">
        {sidebarContent}
      </aside>

      {/* Mobile Sidebar Overlay */}
      {sidebarOpen && (
        <div className="lg:hidden fixed inset-0 z-50">
          <div className="absolute inset-0 bg-black/50" onClick={closeSidebar} />
          <aside className="absolute left-0 top-0 h-full w-64 bg-white dark:bg-gray-800 flex flex-col z-10 shadow-xl">
            {sidebarContent}
          </aside>
        </div>
      )}

      {/* Main */}
      <main className="flex-1 overflow-auto">
        {/* Mobile top bar */}
        <div className="lg:hidden flex items-center justify-between px-4 h-14 bg-white dark:bg-gray-800 border-b dark:border-gray-700">
          <button onClick={() => setSidebarOpen(true)} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
            <Menu className="w-5 h-5 dark:text-white" />
          </button>
          <div className="flex items-center gap-2">
            <Shield className="w-5 h-5 text-blue-600" />
            <span className="font-bold dark:text-white">ServerScout</span>
          </div>
          <button onClick={toggle} className="p-1 rounded hover:bg-gray-100 dark:hover:bg-gray-700">
            {dark ? <Sun className="w-4 h-4 dark:text-white" /> : <Moon className="w-4 h-4 dark:text-white" />}
          </button>
        </div>
        <div className="p-4 lg:p-6">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
