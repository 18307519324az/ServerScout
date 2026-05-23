import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { fetchScanTasks, createScanTask, fetchUsers, createUser, updateUserApi, resetUserPassword, deleteUser, fetchCurrentUser, updateCurrentUser, changeCurrentUserPassword, fetchSystemConfigs, detectTools, updateSystemConfigs } from '../services/api'
import type { User } from '../types'
import { useToast } from '../hooks/useToast'
import StatusBadge from '../components/StatusBadge'
import ConfirmDialog from '../components/ConfirmDialog'
import { Plus, Loader2, Trash2, Edit3, Key, UserPlus, Shield, Wrench, Settings, User as UserIcon } from 'lucide-react'

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
