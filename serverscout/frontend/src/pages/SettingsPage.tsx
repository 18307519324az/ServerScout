import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchScanTasks, createScanTask, fetchUsers, createUser, updateUserApi, resetUserPassword, deleteUser, fetchCurrentUser, updateCurrentUser, changeCurrentUserPassword, fetchSystemConfigs, detectTools, updateSystemConfigs, fetchPlugins, createPlugin, updatePlugin, togglePlugin, deletePlugin } from '../services/api'
import type { User } from '../types'
import { useToast } from '../hooks/useToast'
import StatusBadge from '../components/StatusBadge'
import ConfirmDialog from '../components/ConfirmDialog'
import { Plus, Loader2, Trash2, Edit3, Key, UserPlus, Shield, Wrench, Settings, User as UserIcon, Clock, Bell, BellRing, Puzzle, Zap } from 'lucide-react'

export default function SettingsPage() {
  const toast = useToast()
  const [showCreate, setShowCreate] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const queryClient = useQueryClient()

  // ========== Current User Profile ==========
  const { data: currentUserData, isLoading: profileLoading } = useQuery({
    queryKey: ['currentUser'],
    queryFn: () => fetchCurrentUser(),
  })
  const currentUser = currentUserData?.data?.data
  const isAdmin = currentUser?.role === 'ADMIN' || localStorage.getItem('role') === 'ADMIN'

  const [profileForm, setProfileForm] = useState({ name: '', gender: '', email: '' })
  const [editingProfile, setEditingProfile] = useState(false)
  const profileInitialized = useRef(false)

  useEffect(() => {
    if (currentUser && !profileInitialized.current) {
      setProfileForm({ name: currentUser.name || '', gender: currentUser.gender || '', email: currentUser.email || '' })
      profileInitialized.current = true
    }
  }, [currentUser?.username])

  const updateProfileMutation = useMutation({
    mutationFn: () => updateCurrentUser(profileForm),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['currentUser'] })
      setEditingProfile(false)
      toast.success('个人信息已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '更新失败'),
  })

  const [passwordForm, setPasswordForm] = useState({ oldPassword: '', newPassword: '', confirmPassword: '' })
  const [showChangePwd, setShowChangePwd] = useState(false)

  const changePwdMutation = useMutation({
    mutationFn: () => changeCurrentUserPassword(passwordForm.oldPassword, passwordForm.newPassword),
    onSuccess: () => {
      setShowChangePwd(false)
      setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' })
      toast.success('密码修改成功，请重新登录')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '密码修改失败'),
  })

  // ========== System Config (Admin only) ==========
  const { data: configsData, isLoading: configsLoading } = useQuery({
    queryKey: ['systemConfigs'],
    queryFn: () => fetchSystemConfigs(),
  })
  const configs = configsData?.data?.data || {}

  // Auto-detect tool paths
  const { data: detectedTools } = useQuery({
    queryKey: ['detectedTools'],
    queryFn: () => detectTools(),
    staleTime: 60000,
  })
  const detected = detectedTools?.data?.data || {}

  const [toolConfig, setToolConfig] = useState({ nmapPath: '', nucleiPath: '' })
  const [editingToolConfig, setEditingToolConfig] = useState(false)
  const toolConfigInitialized = useRef(false)

  useEffect(() => {
    const nmap = configs['nmap-path'] || detected['nmap-path']
    const nuclei = configs['nuclei-path'] || detected['nuclei-path']
    if ((nmap || nuclei) && !toolConfigInitialized.current) {
      setToolConfig({ nmapPath: nmap || 'nmap', nucleiPath: nuclei || 'nuclei' })
      toolConfigInitialized.current = true
    }
  }, [configs['nmap-path'], configs['nuclei-path'], detected['nmap-path'], detected['nuclei-path']])

  const updateConfigMutation = useMutation({
    mutationFn: () => updateSystemConfigs({ 'nmap-path': toolConfig.nmapPath, 'nuclei-path': toolConfig.nucleiPath }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      setEditingToolConfig(false)
      toast.success('工具路径配置已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '保存失败'),
  })

  // ========== Scheduled Scan Config State ==========
  const [scheduledConfig, setScheduledConfig] = useState({
    dailyEnabled: 'true',
    dailyTarget: '192.168.1.0/24',
    dailyCron: '0 0 2 * * ?',
    weeklyEnabled: 'false',
    weeklyTarget: '192.168.1.0/24',
    weeklyCron: '0 0 3 * * SUN',
  })
  const [editingScheduled, setEditingScheduled] = useState(false)
  const scheduledInitialized = useRef(false)

  useEffect(() => {
    if (!configsLoading && !scheduledInitialized.current) {
      setScheduledConfig({
        dailyEnabled: configs['daily-scan-enabled'] || 'true',
        dailyTarget: configs['daily-scan-target'] || '192.168.1.0/24',
        dailyCron: configs['daily-scan-cron'] || '0 0 2 * * ?',
        weeklyEnabled: configs['weekly-scan-enabled'] || 'false',
        weeklyTarget: configs['weekly-scan-target'] || '192.168.1.0/24',
        weeklyCron: configs['weekly-scan-cron'] || '0 0 3 * * SUN',
      })
      scheduledInitialized.current = true
    }
  }, [configsLoading])

  const updateScheduledMutation = useMutation({
    mutationFn: () => updateSystemConfigs({
      'daily-scan-enabled': scheduledConfig.dailyEnabled,
      'daily-scan-target': scheduledConfig.dailyTarget,
      'daily-scan-cron': scheduledConfig.dailyCron,
      'weekly-scan-enabled': scheduledConfig.weeklyEnabled,
      'weekly-scan-target': scheduledConfig.weeklyTarget,
      'weekly-scan-cron': scheduledConfig.weeklyCron,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      setEditingScheduled(false)
      toast.success('定时扫描配置已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '保存失败'),
  })

  // ========== Webhook Config State ==========
  const [webhookConfig, setWebhookConfig] = useState({
    dingtalk: '',
    feishu: '',
    wecom: '',
  })
  const [editingWebhook, setEditingWebhook] = useState(false)
  const webhookInitialized = useRef(false)

  useEffect(() => {
    if (!configsLoading && !webhookInitialized.current) {
      setWebhookConfig({
        dingtalk: configs['webhook-dingtalk'] || '',
        feishu: configs['webhook-feishu'] || '',
        wecom: configs['webhook-wecom'] || '',
      })
      webhookInitialized.current = true
    }
  }, [configsLoading])

  const updateWebhookMutation = useMutation({
    mutationFn: () => updateSystemConfigs({
      'webhook-dingtalk': webhookConfig.dingtalk,
      'webhook-feishu': webhookConfig.feishu,
      'webhook-wecom': webhookConfig.wecom,
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      setEditingWebhook(false)
      toast.success('告警通知配置已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '保存失败'),
  })

  // ========== L2 Scan Strategy Plugin State ==========
  const { data: pluginsData, isLoading: pluginsLoading } = useQuery({
    queryKey: ['plugins'],
    queryFn: () => fetchPlugins(),
  })
  const plugins = pluginsData?.data?.data || []

  const [showPluginForm, setShowPluginForm] = useState(false)
  const [editingPlugin, setEditingPlugin] = useState<any>(null)
  const [pluginForm, setPluginForm] = useState({
    name: '', scanType: '', description: '', commandTemplate: '', resultParser: 'line', findingRegex: '',
  })

  const createPluginMutation = useMutation({
    mutationFn: () => createPlugin(pluginForm),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] })
      setShowPluginForm(false)
      setPluginForm({ name: '', scanType: '', description: '', commandTemplate: '', resultParser: 'line', findingRegex: '' })
      toast.success(`扫描策略 "${pluginForm.name}" 已创建`)
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '创建失败'),
  })

  const updatePluginMutation = useMutation({
    mutationFn: (id: number) => updatePlugin(id, pluginForm),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] })
      setShowPluginForm(false)
      setEditingPlugin(null)
      toast.success('策略已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '更新失败'),
  })

  const togglePluginMutation = useMutation({
    mutationFn: (id: number) => togglePlugin(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] })
      toast.success('策略状态已切换')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '操作失败'),
  })

  const deletePluginMutation = useMutation({
    mutationFn: (id: number) => deletePlugin(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['plugins'] })
      toast.success('策略已删除')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '删除失败'),
  })

  // ========== Scan Task ==========
  const { data: tasksData, isLoading } = useQuery({
    queryKey: ['scan-tasks'],
    queryFn: () => fetchScanTasks({ size: 20 }),
  })
  const tasks = tasksData?.data?.data?.content || []

  const [form, setForm] = useState({
    name: '',
    targetRange: '',
    scanType: 'quick',
    portRange: '1-1000',
    enableFingerprint: true,
    enableVulnScan: false,
  })

  const createMutation = useMutation({
    mutationFn: () => createScanTask(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['scan-tasks'] })
      setShowCreate(false)
      setForm({ name: '', targetRange: '', scanType: 'quick', portRange: '1-1000', enableFingerprint: true, enableVulnScan: false })
    },
  })

  // ========== User Management (Admin) ==========
  const { data: usersData, isLoading: usersLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => fetchUsers(),
    enabled: isAdmin,
  })
  const users: User[] = usersData?.data?.data || []

  const [showUserForm, setShowUserForm] = useState(false)
  const [editingUser, setEditingUser] = useState<User | null>(null)
  const [userForm, setUserForm] = useState({ username: '', password: '', name: '', gender: '', role: 'USER', email: '' })
  const [userDeleteConfirm, setUserDeleteConfirm] = useState<User | null>(null)
  const [resetPwdUser, setResetPwdUser] = useState<User | null>(null)
  const [resetPwdValue, setResetPwdValue] = useState('')

  const createUserMutation = useMutation({
    mutationFn: () => createUser(userForm),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setShowUserForm(false)
      setUserForm({ username: '', password: '', name: '', gender: '', role: 'USER', email: '' })
      toast.success(`用户 "${userForm.username}" 创建成功`)
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '创建用户失败'),
  })

  const updateUserMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: any }) => updateUserApi(id, data),
    onSuccess: (_, vars) => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setEditingUser(null)
      toast.success('用户信息已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '更新用户失败'),
  })

  const resetPasswordMutation = useMutation({
    mutationFn: ({ id, pwd }: { id: number; pwd: string }) => resetUserPassword(id, pwd),
    onSuccess: () => {
      setResetPwdUser(null)
      setResetPwdValue('')
      toast.success('密码重置成功')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '密码重置失败'),
  })

  const deleteUserMutation = useMutation({
    mutationFn: (id: number) => deleteUser(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setUserDeleteConfirm(null)
      toast.success('用户已删除')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '删除用户失败'),
  })

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6 dark:text-white">系统设置</h1>

      {/* ========== User Profile Section (All Users) ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <UserIcon className="w-5 h-5 text-blue-600" />
          <div>
            <h2 className="font-semibold dark:text-white">个人信息</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">管理您的账户信息和密码</p>
          </div>
        </div>

        {profileLoading ? (
          <div className="text-center py-6"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
        ) : editingProfile ? (
          <div className="border rounded-lg p-4 bg-gray-50">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">用户名</label>
                <input type="text" value={currentUser?.username || ''} disabled
                  className="w-full px-3 py-1.5 border rounded text-sm bg-gray-100 text-gray-500" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">角色</label>
                <input type="text" value={currentUser?.role || ''} disabled
                  className="w-full px-3 py-1.5 border rounded text-sm bg-gray-100 text-gray-500" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">姓名</label>
                <input type="text" value={profileForm.name}
                  onChange={(e) => setProfileForm({ ...profileForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">性别</label>
                <select value={profileForm.gender}
                  onChange={(e) => setProfileForm({ ...profileForm, gender: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="">请选择</option>
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">邮箱</label>
                <input type="email" value={profileForm.email}
                  onChange={(e) => setProfileForm({ ...profileForm, email: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="user@example.com" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setEditingProfile(false); setProfileForm({ name: currentUser?.name || '', gender: currentUser?.gender || '', email: currentUser?.email || '' }) }}
                className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100">取消</button>
              <button onClick={() => updateProfileMutation.mutate()}
                disabled={updateProfileMutation.isPending}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {updateProfileMutation.isPending ? '保存中...' : '保存修改'}
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-x-8 gap-y-3">
            <div><span className="text-sm text-gray-500 dark:text-gray-400">用户名：</span><span className="text-sm font-medium dark:text-white">{currentUser?.username}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">角色：</span><span className={`text-sm font-medium ${currentUser?.role === 'ADMIN' ? 'text-purple-600' : 'text-gray-600'}`}>{currentUser?.role}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">姓名：</span><span className="text-sm">{currentUser?.name || '-'}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">性别：</span><span className="text-sm">{currentUser?.gender || '-'}</span></div>
            <div className="col-span-2"><span className="text-sm text-gray-500 dark:text-gray-400">邮箱：</span><span className="text-sm">{currentUser?.email || '-'}</span></div>
            <div className="col-span-2 flex gap-2 mt-2">
              <button onClick={() => setEditingProfile(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-100">
                <Edit3 className="w-3.5 h-3.5" /> 编辑资料
              </button>
              <button onClick={() => setShowChangePwd(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-100 text-orange-600">
                <Key className="w-3.5 h-3.5" /> 修改密码
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ========== Change Password Dialog ========== */}
      {showChangePwd && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <h3 className="font-semibold mb-1">修改密码</h3>
            <p className="text-sm text-gray-500 mb-4">为账户 "{currentUser?.username}" 修改登录密码</p>
            <div className="space-y-3">
              <input type="password" value={passwordForm.oldPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, oldPassword: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="当前密码" />
              <input type="password" value={passwordForm.newPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="新密码（至少6位）" />
              <input type="password" value={passwordForm.confirmPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                className="w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="确认新密码" />
              {changePwdMutation.isError && (
                <p className="text-red-500 text-xs">{(changePwdMutation.error as any)?.response?.data?.message || '操作失败'}</p>
              )}
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowChangePwd(false); setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' }) }}
                className="px-4 py-2 text-sm border rounded-lg hover:bg-gray-100">取消</button>
              <button onClick={() => changePwdMutation.mutate()}
                disabled={!passwordForm.oldPassword || passwordForm.newPassword.length < 6 || passwordForm.newPassword !== passwordForm.confirmPassword || changePwdMutation.isPending}
                className="px-4 py-2 text-sm bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50">
                {changePwdMutation.isPending ? '修改中...' : '确认修改'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ========== Tool Path Config Section ========== */}
      {(
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
          <div className="flex items-center gap-3 mb-4">
            <Wrench className="w-5 h-5 text-orange-600" />
            <div>
              <h2 className="font-semibold dark:text-white">扫描工具配置</h2>
              <p className="text-sm text-gray-500 dark:text-gray-400">配置 Nmap 和 Nuclei 工具的执行路径</p>
            </div>
          </div>

          {configsLoading ? (
            <div className="text-center py-6"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
          ) : editingToolConfig ? (
            <div className="border rounded-lg p-4 bg-gray-50">
              <div className="grid grid-cols-1 gap-4">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Nmap 路径</label>
                  <input type="text" value={toolConfig.nmapPath}
                    onChange={(e) => setToolConfig({ ...toolConfig, nmapPath: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="例: /usr/bin/nmap 或 C:\tools\nmap\nmap.exe" />
                  <p className="text-xs text-gray-400 mt-1">填写 nmap 可执行文件的完整路径（仅在系统 PATH 找不到时需要）</p>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Nuclei 路径</label>
                  <input type="text" value={toolConfig.nucleiPath}
                    onChange={(e) => setToolConfig({ ...toolConfig, nucleiPath: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="例: /usr/bin/nuclei 或 C:\tools\nuclei\nuclei.exe" />
                  <p className="text-xs text-gray-400 mt-1">填写 nuclei 可执行文件的完整路径</p>
                </div>
              </div>
              <div className="flex justify-end gap-2 mt-4">
                <button onClick={() => setEditingToolConfig(false)}
                  className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100">取消</button>
                <button onClick={() => updateConfigMutation.mutate()}
                  disabled={updateConfigMutation.isPending}
                  className="px-4 py-1.5 text-sm bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50">
                  {updateConfigMutation.isPending ? '保存中...' : '保存配置'}
                </button>
              </div>
            </div>
          ) : (
            <div className="space-y-3">
              <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
                <div>
                  <span className="text-sm font-medium dark:text-white">Nmap 路径</span>
                  <code className="ml-3 text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{toolConfig.nmapPath}</code>
                </div>
              </div>
              <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
                <div>
                  <span className="text-sm font-medium dark:text-white">Nuclei 路径</span>
                  <code className="ml-3 text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{toolConfig.nucleiPath}</code>
                </div>
              </div>
              <button onClick={() => setEditingToolConfig(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-100">
                <Settings className="w-3.5 h-3.5" /> 编辑路径
              </button>
            </div>
          )}
        </div>
      )}

      {/* ========== Scan Config Section ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="font-semibold dark:text-white">扫描配置</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">管理扫描参数模板和定期任务</p>
          </div>
          <button
            onClick={() => setShowCreate(true)}
            className="flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
          >
            <Plus className="w-4 h-4" /> 新建扫描
          </button>
        </div>

        {showCreate && (
          <div className="border rounded-lg p-4 mb-4 bg-gray-50 dark:bg-gray-700">
            <h3 className="font-medium mb-3">创建扫描任务</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">任务名称</label>
                <input type="text" value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="例：内网资产巡检" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">扫描目标</label>
                <input type="text" value={form.targetRange}
                  onChange={(e) => setForm({ ...form, targetRange: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="例：192.168.1.0/24" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">扫描类型</label>
                <select value={form.scanType}
                  onChange={(e) => setForm({ ...form, scanType: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="quick">快速扫描 (Quick)</option>
                  <option value="full">全面扫描 (Full)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">端口范围</label>
                <input type="text" value={form.portRange}
                  onChange={(e) => setForm({ ...form, portRange: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500" />
              </div>
              <div className="flex items-center gap-4">
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.enableFingerprint}
                    onChange={(e) => setForm({ ...form, enableFingerprint: e.target.checked })}
                    className="rounded" />
                  启用指纹识别
                </label>
                <label className="flex items-center gap-2 text-sm">
                  <input type="checkbox" checked={form.enableVulnScan}
                    onChange={(e) => setForm({ ...form, enableVulnScan: e.target.checked })}
                    className="rounded" />
                  启用漏洞扫描
                </label>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => setShowCreate(false)} className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100">取消</button>
              <button onClick={() => setConfirmOpen(true)}
                disabled={!form.name || !form.targetRange || createMutation.isPending}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {createMutation.isPending ? '创建中...' : '创建扫描'}
              </button>
            </div>
          </div>
        )}

        <div>
          <h3 className="text-sm font-medium text-gray-500 dark:text-gray-400 mb-2">最近扫描配置</h3>
          {isLoading ? (
            <div className="text-center py-6 text-gray-400 dark:text-gray-500"><Loader2 className="w-5 h-5 animate-spin mx-auto" /></div>
          ) : (
            <div className="space-y-1">
              {tasks.slice(0, 5).map((task: any) => (
                <div key={task.id} className="flex items-center justify-between px-3 py-2 rounded hover:bg-gray-50">
                  <div><span className="text-sm font-medium dark:text-white">{task.name}</span>
                    <span className="text-xs text-gray-400 ml-3">{task.targetRange}</span></div>
                  <StatusBadge status={task.status} />
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* ========== Scheduled Scan Config Section ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Clock className="w-5 h-5 text-green-600" />
          <div>
            <h2 className="font-semibold dark:text-white">定时扫描配置</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">配置每日巡检和每周全面扫描的 Cron 表达式与目标范围</p>
          </div>
        </div>

        {editingScheduled ? (
          <div className="border rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4">
            <div className="border-b pb-4">
              <h3 className="text-sm font-medium mb-3 dark:text-white">每日快速巡检</h3>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">启用</label>
                  <select value={scheduledConfig.dailyEnabled}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyEnabled: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="true">启用</option>
                    <option value="false">禁用</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">扫描目标</label>
                  <input type="text" value={scheduledConfig.dailyTarget}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyTarget: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="192.168.1.0/24" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Cron 表达式</label>
                  <input type="text" value={scheduledConfig.dailyCron}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyCron: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  <p className="text-xs text-gray-400 mt-1">默认: 0 0 2 * * ? (凌晨2点)</p>
                </div>
              </div>
            </div>

            <div>
              <h3 className="text-sm font-medium mb-3 dark:text-white">每周全面巡检</h3>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 mb-1">启用</label>
                  <select value={scheduledConfig.weeklyEnabled}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyEnabled: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                    <option value="true">启用</option>
                    <option value="false">禁用</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">扫描目标</label>
                  <input type="text" value={scheduledConfig.weeklyTarget}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyTarget: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="192.168.1.0/24" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 mb-1">Cron 表达式</label>
                  <input type="text" value={scheduledConfig.weeklyCron}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyCron: e.target.value })}
                    className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500" />
                  <p className="text-xs text-gray-400 mt-1">默认: 0 0 3 * * SUN (周日凌晨3点)</p>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button onClick={() => setEditingScheduled(false)}
                className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600">取消</button>
              <button onClick={() => updateScheduledMutation.mutate()}
                disabled={updateScheduledMutation.isPending}
                className="px-4 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50">
                {updateScheduledMutation.isPending ? '保存中...' : '保存配置'}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div>
                <span className={`inline-block w-2 h-2 rounded-full mr-2 ${scheduledConfig.dailyEnabled === 'true' ? 'bg-green-500' : 'bg-gray-300'}`} />
                <span className="text-sm font-medium dark:text-white">每日巡检</span>
                <span className="text-xs text-gray-400 ml-3">{scheduledConfig.dailyTarget}</span>
              </div>
              <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{scheduledConfig.dailyCron}</code>
            </div>
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div>
                <span className={`inline-block w-2 h-2 rounded-full mr-2 ${scheduledConfig.weeklyEnabled === 'true' ? 'bg-green-500' : 'bg-gray-300'}`} />
                <span className="text-sm font-medium dark:text-white">每周全面巡检</span>
                <span className="text-xs text-gray-400 ml-3">{scheduledConfig.weeklyTarget}</span>
              </div>
              <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{scheduledConfig.weeklyCron}</code>
            </div>
            <button onClick={() => setEditingScheduled(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-100">
              <Settings className="w-3.5 h-3.5" /> 编辑定时任务
            </button>
          </div>
        )}
      </div>

      {/* ========== Webhook Notification Config Section ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <BellRing className="w-5 h-5 text-purple-600" />
          <div>
            <h2 className="font-semibold dark:text-white">告警通知配置</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">扫描完成后通过 Webhook 推送结果摘要到钉钉/飞书/企业微信</p>
          </div>
        </div>

        {editingWebhook ? (
          <div className="border rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4">
            <div>
              <label className="block text-xs text-gray-500 mb-1">钉钉 (DingTalk) Webhook URL</label>
              <input type="text" value={webhookConfig.dingtalk}
                onChange={(e) => setWebhookConfig({ ...webhookConfig, dingtalk: e.target.value })}
                className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="https://oapi.dingtalk.com/robot/send?access_token=xxx" />
              <p className="text-xs text-gray-400 mt-1">钉钉群机器人 Webhook 地址，留空则不发送</p>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">飞书 (Feishu/Lark) Webhook URL</label>
              <input type="text" value={webhookConfig.feishu}
                onChange={(e) => setWebhookConfig({ ...webhookConfig, feishu: e.target.value })}
                className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="https://open.feishu.cn/open-apis/bot/v2/hook/xxx" />
              <p className="text-xs text-gray-400 mt-1">飞书群机器人 Webhook 地址，留空则不发送</p>
            </div>
            <div>
              <label className="block text-xs text-gray-500 mb-1">企业微信 (WeCom) Webhook URL</label>
              <input type="text" value={webhookConfig.wecom}
                onChange={(e) => setWebhookConfig({ ...webhookConfig, wecom: e.target.value })}
                className="w-full px-3 py-2 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
                placeholder="https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxx" />
              <p className="text-xs text-gray-400 mt-1">企业微信群机器人 Webhook 地址，留空则不发送</p>
            </div>
            <div className="flex justify-end gap-2">
              <button onClick={() => setEditingWebhook(false)}
                className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600">取消</button>
              <button onClick={() => updateWebhookMutation.mutate()}
                disabled={updateWebhookMutation.isPending}
                className="px-4 py-1.5 text-sm bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50">
                {updateWebhookMutation.isPending ? '保存中...' : '保存配置'}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {(['dingtalk', 'feishu', 'wecom'] as const).map((key) => {
              const labels = { dingtalk: '钉钉', feishu: '飞书', wecom: '企业微信' }
              const url = webhookConfig[key]
              return (
                <div key={key} className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
                  <div>
                    <span className={`inline-block w-2 h-2 rounded-full mr-2 ${url ? 'bg-green-500' : 'bg-gray-300'}`} />
                    <span className="text-sm font-medium dark:text-white">{labels[key]}</span>
                  </div>
                  <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded max-w-xs truncate">
                    {url || '未配置'}
                  </code>
                </div>
              )
            })}
            <button onClick={() => setEditingWebhook(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm border rounded-lg hover:bg-gray-100">
              <Settings className="w-3.5 h-3.5" /> 编辑通知
            </button>
          </div>
        )}
      </div>

      {/* ========== L2 Scan Strategy Plugin Manager ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex justify-between items-center mb-4">
          <div className="flex items-center gap-3">
            <Puzzle className="w-5 h-5 text-indigo-600" />
            <div>
              <h2 className="font-semibold dark:text-white">扫描策略插件 (L2)</h2>
              <p className="text-sm text-gray-500 dark:text-gray-400">管理自定义扫描策略 — 支持自定义命令执行与结果解析</p>
            </div>
          </div>
          <button
            onClick={() => { setEditingPlugin(null); setPluginForm({ name: '', scanType: '', description: '', commandTemplate: '', resultParser: 'line', findingRegex: '' }); setShowPluginForm(true) }}
            className="flex items-center gap-1 px-3 py-2 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700"
          >
            <Plus className="w-4 h-4" /> 添加策略
          </button>
        </div>

        {/* Plugin Form */}
        {showPluginForm && (
          <div className="border rounded-lg p-4 mb-4 bg-gray-50 dark:bg-gray-700">
            <h3 className="font-medium mb-3 dark:text-white">{editingPlugin ? '编辑策略' : '新建策略'}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">策略名称</label>
                <input type="text" value={pluginForm.name}
                  onChange={(e) => setPluginForm({ ...pluginForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="例: SSH 弱口令检测" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">Scan Type (唯一标识)</label>
                <input type="text" value={pluginForm.scanType}
                  onChange={(e) => setPluginForm({ ...pluginForm, scanType: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="例: ssh-brute" disabled={!!editingPlugin} />
                <p className="text-xs text-gray-400 mt-1">小写字母+连字符，创建后不可修改</p>
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">描述</label>
                <input type="text" value={pluginForm.description}
                  onChange={(e) => setPluginForm({ ...pluginForm, description: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder="简述此策略的用途" />
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 mb-1">命令模板</label>
                <textarea value={pluginForm.commandTemplate} rows={3}
                  onChange={(e) => setPluginForm({ ...pluginForm, commandTemplate: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder={"nmap -sV -p {port_range} --script ssh-brute {target}\n或: python3 custom_scanner.py --target {target}"} />
                <p className="text-xs text-gray-400 mt-1">变量: {'{target}'}, {'{port_range}'}, {'{task_name}'}</p>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">解析模式</label>
                <select value={pluginForm.resultParser}
                  onChange={(e) => setPluginForm({ ...pluginForm, resultParser: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500">
                  <option value="line">按行正则匹配</option>
                  <option value="raw">原始输出</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">正则表达式 (命名捕获组)</label>
                <input type="text" value={pluginForm.findingRegex}
                  onChange={(e) => setPluginForm({ ...pluginForm, findingRegex: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500"
                  placeholder={'(?<severity>critical|high) (?<name>.+)'} />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowPluginForm(false); setEditingPlugin(null) }}
                className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600">取消</button>
              <button
                onClick={() => {
                  if (editingPlugin) {
                    updatePluginMutation.mutate(editingPlugin.id)
                  } else {
                    createPluginMutation.mutate()
                  }
                }}
                disabled={!pluginForm.name || !pluginForm.scanType || !pluginForm.commandTemplate || createPluginMutation.isPending || updatePluginMutation.isPending}
                className="px-4 py-1.5 text-sm bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 disabled:opacity-50">
                {createPluginMutation.isPending || updatePluginMutation.isPending ? '保存中...' : editingPlugin ? '保存修改' : '创建策略'}
              </button>
            </div>
          </div>
        )}

        {/* Plugin List */}
        {pluginsLoading ? (
          <div className="text-center py-6"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
        ) : plugins.length === 0 ? (
          <div className="text-center py-6 text-gray-400 dark:text-gray-500">
            <Puzzle className="w-8 h-8 mx-auto mb-2 opacity-50" />
            <p className="text-sm">暂无自定义策略</p>
            <p className="text-xs mt-1">点击"添加策略"创建自定义扫描策略，如 SSH 弱口令检测、Redis 未授权扫描等</p>
          </div>
        ) : (
          <div className="space-y-2">
            {plugins.map((p: any) => (
              <div key={p.id} className={`border rounded-lg p-3 flex items-center justify-between ${p.enabled ? 'hover:bg-gray-50 dark:hover:bg-gray-700' : 'opacity-50 bg-gray-50 dark:bg-gray-800'}`}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${p.enabled ? 'bg-green-500' : 'bg-gray-300'}`} />
                    <span className="text-sm font-medium dark:text-white">{p.name}</span>
                    <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-1.5 py-0.5 rounded">{p.scanType}</code>
                    {p.description && <span className="text-xs text-gray-400 truncate">{p.description}</span>}
                  </div>
                </div>
                <div className="flex items-center gap-1 ml-2">
                  <button onClick={() => togglePluginMutation.mutate(p.id)}
                    className={`px-2 py-1 text-xs rounded ${p.enabled ? 'text-orange-600 hover:bg-orange-50' : 'text-green-600 hover:bg-green-50'}`}
                    title={p.enabled ? '禁用' : '启用'}>
                    {p.enabled ? '禁用' : '启用'}
                  </button>
                  <button onClick={() => {
                    setEditingPlugin(p)
                    setPluginForm({ name: p.name, scanType: p.scanType, description: p.description || '', commandTemplate: p.commandTemplate, resultParser: p.resultParser || 'line', findingRegex: p.findingRegex || '' })
                    setShowPluginForm(true)
                  }} className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 hover:text-indigo-600" title="编辑">
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={() => { if (confirm(`确认删除策略 "${p.name}"?`)) deletePluginMutation.mutate(p.id) }}
                    className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 hover:text-red-600" title="删除">
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ========== User Management Section (Admin Only) ========== */}
      {isAdmin ? (
      <div className="bg-white rounded-xl border shadow-sm p-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="font-semibold dark:text-white">用户管理</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">管理系统用户和角色权限</p>
          </div>
          <button
            onClick={() => {
              setEditingUser(null)
              setUserForm({ username: '', password: '', name: '', gender: '', role: 'USER', email: '' })
              setShowUserForm(true)
            }}
            className="flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
          >
            <UserPlus className="w-4 h-4" /> 添加用户
          </button>
        </div>

        {showUserForm && (
          <div className="border rounded-lg p-4 mb-4 bg-gray-50 dark:bg-gray-700">
            <h3 className="font-medium mb-3">{editingUser ? '编辑用户' : '新建用户'}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 mb-1">用户名</label>
                <input type="text" value={userForm.username}
                  onChange={(e) => setUserForm({ ...userForm, username: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  disabled={!!editingUser} placeholder="登录用户名" />
              </div>
              {!editingUser && (
                <div>
                  <label className="block text-xs text-gray-500 mb-1">密码</label>
                  <input type="password" value={userForm.password}
                    onChange={(e) => setUserForm({ ...userForm, password: e.target.value })}
                    className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                    placeholder="至少6个字符" />
                </div>
              )}
              <div>
                <label className="block text-xs text-gray-500 mb-1">姓名</label>
                <input type="text" value={userForm.name}
                  onChange={(e) => setUserForm({ ...userForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="用户姓名" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">性别</label>
                <select value={userForm.gender}
                  onChange={(e) => setUserForm({ ...userForm, gender: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="">请选择</option>
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">角色</label>
                <select value={userForm.role}
                  onChange={(e) => setUserForm({ ...userForm, role: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500">
                  <option value="USER">普通用户 (USER)</option>
                  <option value="ADMIN">管理员 (ADMIN)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 mb-1">邮箱（可选）</label>
                <input type="email" value={userForm.email}
                  onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                  className="w-full px-3 py-1.5 border rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
                  placeholder="user@example.com" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowUserForm(false); setEditingUser(null) }} className="px-4 py-1.5 text-sm border rounded-lg hover:bg-gray-100">取消</button>
              <button
                onClick={() => {
                  if (editingUser) {
                    updateUserMutation.mutate({ id: editingUser.id, data: { role: userForm.role, name: userForm.name, gender: userForm.gender, email: userForm.email } })
                  } else {
                    createUserMutation.mutate()
                  }
                }}
                disabled={!userForm.username || (!editingUser && !userForm.password) || createUserMutation.isPending || updateUserMutation.isPending}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {createUserMutation.isPending || updateUserMutation.isPending ? '保存中...' : editingUser ? '保存修改' : '创建用户'}
              </button>
            </div>
            {(createUserMutation.isError || updateUserMutation.isError) && (
              <p className="text-red-500 text-xs mt-2">
                {(createUserMutation.error as any)?.response?.data?.message || (updateUserMutation.error as any)?.response?.data?.message || '操作失败'}
              </p>
            )}
          </div>
        )}

        {usersLoading ? (
          <div className="text-center py-10 text-gray-400 dark:text-gray-500"><Loader2 className="w-5 h-5 animate-spin mx-auto" /></div>
        ) : (
          <table className="w-full text-sm dark:text-gray-300">
            <thead>
              <tr className="border-b dark:border-gray-600 text-gray-500 dark:text-gray-400 text-xs uppercase">
                <th className="text-left py-2 pl-3">用户名</th>
                <th className="text-left py-2">姓名</th>
                <th className="text-left py-2">角色</th>
                <th className="text-left py-2">邮箱</th>
                <th className="text-left py-2">状态</th>
                <th className="text-left py-2">创建时间</th>
                <th className="text-right py-2 pr-3">操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((user) => (
                <tr key={user.id} className="border-b dark:border-gray-600 last:border-0 hover:bg-gray-50 dark:hover:bg-gray-700">
                  <td className="py-2.5 pl-3 font-medium dark:text-gray-200">{user.username}</td>
                  <td className="py-2.5 text-gray-600 dark:text-gray-300">{user.name || '-'}</td>
                  <td className="py-2.5">
                    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${
                      user.role === 'ADMIN' ? 'bg-purple-100 text-purple-700' : 'bg-gray-100 text-gray-600'
                    }`}>
                      <Shield className="w-3 h-3" />{user.role}
                    </span>
                  </td>
                  <td className="py-2.5 text-gray-500 dark:text-gray-400">{user.email || '-'}</td>
                  <td className="py-2.5">
                    <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                      user.enabled ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
                    }`}>{user.enabled ? '启用' : '禁用'}</span>
                  </td>
                  <td className="py-2.5 text-gray-400 dark:text-gray-500 text-xs">{new Date(user.createdAt).toLocaleDateString('zh-CN')}</td>
                  <td className="py-2.5 text-right pr-3">
                    <div className="flex items-center justify-end gap-1">
                      <button onClick={() => {
                        setEditingUser(user)
                        setUserForm({ username: user.username, password: '', name: user.name || '', gender: user.gender || '', role: user.role, email: user.email || '' })
                        setShowUserForm(true)
                      }} className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-blue-600" title="编辑">
                        <Edit3 className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => { setResetPwdUser(user); setResetPwdValue('') }}
                        className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-orange-600" title="重置密码">
                        <Key className="w-3.5 h-3.5" />
                      </button>
                      <button onClick={() => setUserDeleteConfirm(user)}
                        className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-red-600" title="删除">
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
              {users.length === 0 && (
                <tr><td colSpan={7} className="text-center py-10 text-gray-400 dark:text-gray-500">暂无用户</td></tr>
              )}
            </tbody>
          </table>
        )}
      </div>
      ) : (
      <div className="bg-white rounded-xl border shadow-sm p-6">
        <h2 className="font-semibold mb-2">用户管理</h2>
        <div className="flex items-center gap-3 p-4 bg-gray-50 rounded-lg">
          <Shield className="w-10 h-10 text-gray-300" />
          <div>
            <p className="text-sm font-medium text-gray-600 dark:text-gray-300">权限不足</p>
            <p className="text-xs text-gray-400 dark:text-gray-500">用户管理功能仅限管理员使用，请使用 ADMIN 账号登录。</p>
          </div>
        </div>
      </div>
      )}

      {/* ========== Dialogs ========== */}
      {resetPwdUser && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <h3 className="font-semibold mb-1">重置密码</h3>
            <p className="text-sm text-gray-500 mb-4">用户: {resetPwdUser.username}</p>
            <input type="password" value={resetPwdValue}
              onChange={(e) => setResetPwdValue(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 mb-4"
              placeholder="输入新密码（至少6位）" />
            {resetPasswordMutation.isError && (
              <p className="text-red-500 text-xs mb-2">{(resetPasswordMutation.error as any)?.response?.data?.message || '操作失败'}</p>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setResetPwdUser(null)} className="px-4 py-2 text-sm border rounded-lg hover:bg-gray-100">取消</button>
              <button onClick={() => resetPasswordMutation.mutate({ id: resetPwdUser.id, pwd: resetPwdValue })}
                disabled={resetPwdValue.length < 6 || resetPasswordMutation.isPending}
                className="px-4 py-2 text-sm bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50">
                {resetPasswordMutation.isPending ? '重置中...' : '确认重置'}
              </button>
            </div>
          </div>
        </div>
      )}

      <ConfirmDialog
        open={confirmOpen}
        title="创建扫描任务"
        message={`确认创建扫描任务 "${form.name}"？目标范围：${form.targetRange}`}
        confirmLabel="确认创建"
        onConfirm={() => { setConfirmOpen(false); createMutation.mutate() }}
        onCancel={() => setConfirmOpen(false)}
      />
      <ConfirmDialog
        open={!!userDeleteConfirm}
        title="删除用户"
        message={`确认删除用户 "${userDeleteConfirm?.username}"？此操作不可撤销。`}
        confirmLabel="确认删除"
        onConfirm={() => userDeleteConfirm && deleteUserMutation.mutate(userDeleteConfirm.id)}
        onCancel={() => setUserDeleteConfirm(null)}
      />
    </div>
  )
}
