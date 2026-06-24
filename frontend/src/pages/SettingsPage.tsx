import { useState, useEffect, useRef } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { fetchUsers, createUser, updateUserApi, resetUserPassword, deleteUser, fetchCurrentUser, updateCurrentUser, changeCurrentUserPassword, fetchSystemConfigs, detectTools, detectSingleTool, updateSystemConfigs, fetchPlugins, createPlugin, updatePlugin, togglePlugin, deletePlugin } from '../services/api'
import type { User } from '../types'
import { useToast } from '../hooks/useToast'
import StatusBadge from '../components/StatusBadge'
import ConfirmDialog from '../components/ConfirmDialog'
import OperationLogViewer from '../components/OperationLogViewer'
import { useMode } from '../contexts/ModeContext'
import { Plus, Loader2, Trash2, Edit3, Key, UserPlus, Shield, Wrench, Settings, User as UserIcon, Clock, BellRing, Puzzle, ExternalLink, CheckCircle2, XCircle, HelpCircle, ScanLine, Globe, ScrollText, FolderOpen, Search } from 'lucide-react'

const HOURS = Array.from({ length: 24 }, (_, i) => i)
const MINUTES = [0, 15, 30, 45]

const DAY_VALUES = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'] as const

export default function SettingsPage() {
  const { t } = useTranslation()
  const toast = useToast()
  const queryClient = useQueryClient()
  const { mode, loading: modeLoading } = useMode()

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
  const { data: detectedTools, refetch: refetchDetect } = useQuery({
    queryKey: ['detectedTools'],
    queryFn: () => detectTools(),
    staleTime: 60000,
  })
  const detected = detectedTools?.data?.data || {}
  const nmapDetected = detected['nmap-path'] || false
  const nucleiDetected = detected['nuclei-path'] || false

  const [toolConfig, setToolConfig] = useState({ nmapPath: '', nucleiPath: '' })
  const [editingToolConfig, setEditingToolConfig] = useState(false)
  const [showToolHelp, setShowToolHelp] = useState(false)
  const toolConfigInitialized = useRef(false)
  const [originalToolConfig, setOriginalToolConfig] = useState({ nmapPath: '', nucleiPath: '' })
  const nmapFileInputRef = useRef<HTMLInputElement>(null)
  const nucleiFileInputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    const nmap = configs['nmap-path'] || detected['nmap-path']
    const nuclei = configs['nuclei-path'] || detected['nuclei-path']
    if ((nmap || nuclei) && !toolConfigInitialized.current) {
      setToolConfig({
        nmapPath: nmap || toolConfig.nmapPath || 'nmap',
        nucleiPath: nuclei || toolConfig.nucleiPath || 'nuclei',
      })
      toolConfigInitialized.current = true
    }
  }, [configs['nmap-path'], configs['nuclei-path'], detected['nmap-path'], detected['nuclei-path']])

  // Auto-detect tool path (per-tool or all)
  const handleAutoDetect = async (tool?: 'nmap' | 'nuclei') => {
    const label = tool === 'nmap' ? 'Nmap' : tool === 'nuclei' ? 'Nuclei' : '工具'
    toast.info(`正在检测${label}路径...`)
    try {
      const result = await refetchDetect()
      if (result.data?.data?.data) {
        const d = result.data.data.data
        setToolConfig(prev => ({
          nmapPath: (!tool || tool === 'nmap') ? (d['nmap-path'] || prev.nmapPath) : prev.nmapPath,
          nucleiPath: (!tool || tool === 'nuclei') ? (d['nuclei-path'] || prev.nucleiPath) : prev.nucleiPath,
        }))
        const found = tool === 'nmap' ? d['nmap-path'] : tool === 'nuclei' ? d['nuclei-path'] : (d['nmap-path'] || d['nuclei-path'])
        toast.success(found ? `{t('settings.detected')}${label}: ${found}` : `未检测到${label}路径，请手动输入`)
      }
    } catch {
      toast.error('检测失败，请检查后台服务')
    }
  }

  // Open file browser to locate tool executable
  const handleFileBrowse = (tool: 'nmap' | 'nuclei') => {
    if (tool === 'nmap') nmapFileInputRef.current?.click()
    else nucleiFileInputRef.current?.click()
  }

  // Handle file selection from hidden file input — resolve full path via backend
  const handleFileSelected = async (tool: 'nmap' | 'nuclei', e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (!file) return
    e.target.value = ''
    const label = tool === 'nmap' ? 'Nmap' : 'Nuclei'
    toast.info(`正在解析${label}路径...`)
    try {
      const result = await detectSingleTool(tool)
      const detected = result.data?.data?.[`${tool}-path`]
      if (detected) {
        setToolConfig(prev => ({
          ...prev,
          [tool === 'nmap' ? 'nmapPath' : 'nucleiPath']: detected,
        }))
        toast.success(`已定位${label}: ${detected}`)
      } else {
        setToolConfig(prev => ({
          ...prev,
          [tool === 'nmap' ? 'nmapPath' : 'nucleiPath']: file.name,
        }))
        toast.warning(`未能解析完整路径，已填入文件名: ${file.name}`)
      }
    } catch {
      setToolConfig(prev => ({
        ...prev,
        [tool === 'nmap' ? 'nmapPath' : 'nucleiPath']: file.name,
      }))
      toast.error('路径解析失败')
    }
  }

  // Enter edit mode — save current values for cancel recovery
  const handleEnterEditMode = () => {
    setOriginalToolConfig({ ...toolConfig })
    setEditingToolConfig(true)
  }

  // Cancel edit mode — restore original values
  const handleCancelEditMode = () => {
    setToolConfig({ ...originalToolConfig })
    setEditingToolConfig(false)
  }

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
    dailyEnabled: true,
    dailyTarget: '192.168.1.0/24',
    dailyHour: 2,
    dailyMinute: 0,
    weeklyEnabled: false,
    weeklyTarget: '192.168.1.0/24',
    weeklyDay: 'SUN',
    weeklyHour: 3,
    weeklyMinute: 0,
  })
  const [editingScheduled, setEditingScheduled] = useState(false)
  const scheduledInitialized = useRef(false)

  // Parse cron to time components
  const parseCronToTime = (cron: string) => {
    // Format: "0 mm HH * * ?" for daily, "0 mm HH * * DAY" for weekly
    const parts = cron.trim().split(/\s+/)
    if (parts.length >= 3) {
      return { minute: parseInt(parts[1]) || 0, hour: parseInt(parts[2]) || 0 }
    }
    return { minute: 0, hour: 2 }
  }

  useEffect(() => {
    if (!configsLoading && !scheduledInitialized.current) {
      const dailyCron = configs['daily-scan-cron'] || '0 0 2 * * ?'
      const weeklyCron = configs['weekly-scan-cron'] || '0 0 3 * * SUN'
      const dailyTime = parseCronToTime(dailyCron)
      const weeklyTime = parseCronToTime(weeklyCron)
      const weeklyParts = weeklyCron.trim().split(/\s+/)
      const weeklyDay = weeklyParts.length >= 6 ? weeklyParts[5] : 'SUN'

      setScheduledConfig({
        dailyEnabled: configs['daily-scan-enabled'] !== 'false',
        dailyTarget: configs['daily-scan-target'] || '192.168.1.0/24',
        dailyHour: dailyTime.hour,
        dailyMinute: dailyTime.minute,
        weeklyEnabled: configs['weekly-scan-enabled'] === 'true',
        weeklyTarget: configs['weekly-scan-target'] || '192.168.1.0/24',
        weeklyDay,
        weeklyHour: weeklyTime.hour,
        weeklyMinute: weeklyTime.minute,
      })
      scheduledInitialized.current = true
    }
  }, [configsLoading])

  const buildCron = (hour: number, minute: number, dayOfWeek?: string) => {
    if (dayOfWeek) {
      return `0 ${minute} ${hour} * * ${dayOfWeek}`
    }
    return `0 ${minute} ${hour} * * ?`
  }

  const updateScheduledMutation = useMutation({
    mutationFn: () => updateSystemConfigs({
      'daily-scan-enabled': String(scheduledConfig.dailyEnabled),
      'daily-scan-target': scheduledConfig.dailyTarget,
      'daily-scan-cron': buildCron(scheduledConfig.dailyHour, scheduledConfig.dailyMinute),
      'weekly-scan-enabled': String(scheduledConfig.weeklyEnabled),
      'weekly-scan-target': scheduledConfig.weeklyTarget,
      'weekly-scan-cron': buildCron(scheduledConfig.weeklyHour, scheduledConfig.weeklyMinute, scheduledConfig.weeklyDay),
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
    enabled: true,
    dingtalk: '',
    feishu: '',
    wecom: '',
    dingtalkEnabled: true,
    feishuEnabled: true,
    wecomEnabled: true,
  })
  const [editingWebhook, setEditingWebhook] = useState(false)
  const [showWebhookHelp, setShowWebhookHelp] = useState(false)
  const webhookInitialized = useRef(false)

  useEffect(() => {
    if (!configsLoading && !webhookInitialized.current) {
      setWebhookConfig({
        enabled: configs['webhook-enabled'] !== 'false',
        dingtalk: configs['webhook-dingtalk'] || '',
        feishu: configs['webhook-feishu'] || '',
        wecom: configs['webhook-wecom'] || '',
        dingtalkEnabled: configs['webhook-dingtalk-enabled'] !== 'false',
        feishuEnabled: configs['webhook-feishu-enabled'] !== 'false',
        wecomEnabled: configs['webhook-wecom-enabled'] !== 'false',
      })
      webhookInitialized.current = true
    }
  }, [configsLoading])

  const updateWebhookMutation = useMutation({
    mutationFn: () => {
      const configs: Record<string, string> = {
        'webhook-enabled': String(webhookConfig.enabled),
        'webhook-dingtalk': webhookConfig.dingtalk,
        'webhook-feishu': webhookConfig.feishu,
        'webhook-wecom': webhookConfig.wecom,
        'webhook-dingtalk-enabled': String(webhookConfig.dingtalkEnabled),
        'webhook-feishu-enabled': String(webhookConfig.feishuEnabled),
        'webhook-wecom-enabled': String(webhookConfig.wecomEnabled),
      }
      return updateSystemConfigs(configs)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      setEditingWebhook(false)
      toast.success('告警通知配置已更新')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '保存失败'),
  })

  // ========== Email Config State ==========
  const [emailConfig, setEmailConfig] = useState({
    enabled: false,
    recipient: '',
    smtpHost: '',
    smtpPort: '587',
    smtpUsername: '',
    smtpPassword: '',
    smtpSsl: false,
  })
  const emailInitialized = useRef(false)
  const [editingEmail, setEditingEmail] = useState(false)

  useEffect(() => {
    if (!configsLoading && !emailInitialized.current) {
      setEmailConfig({
        enabled: configs['email-enabled'] === 'true',
        recipient: configs['email-recipient'] || '',
        smtpHost: configs['email-smtp-host'] || '',
        smtpPort: configs['email-smtp-port'] || '587',
        smtpUsername: configs['email-smtp-username'] || '',
        smtpPassword: configs['email-smtp-password'] || '',
        smtpSsl: configs['email-smtp-ssl'] === 'true',
      })
      emailInitialized.current = true
    }
  }, [configsLoading])

  const updateEmailMutation = useMutation({
    mutationFn: () => updateSystemConfigs({
      'email-enabled': String(emailConfig.enabled),
      'email-recipient': emailConfig.recipient,
      'email-smtp-host': emailConfig.smtpHost,
      'email-smtp-port': emailConfig.smtpPort,
      'email-smtp-username': emailConfig.smtpUsername,
      'email-smtp-password': emailConfig.smtpPassword,
      'email-smtp-ssl': String(emailConfig.smtpSsl),
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      setEditingEmail(false)
      toast.success(t('settings.emailNotification') + ' ' + t('common.save') + '成功')
    },
    onError: (err: any) => toast.error(err?.response?.data?.message || '保存失败'),
  })

  // ========== External API Keys State ==========
  const [apiKeysConfig, setApiKeysConfig] = useState({ censysId: '', censysSecret: '', virustotalKey: '' })
  const [showApiKeysHelp, setShowApiKeysHelp] = useState(false)
  const apiKeysInitialized = useRef(false)

  useEffect(() => {
    if (!configsLoading && !apiKeysInitialized.current) {
      setApiKeysConfig({
        censysId: configs['censys-api-id'] || '',
        censysSecret: configs['censys-api-secret'] || '',
        virustotalKey: configs['virustotal-api-key'] || '',
      })
      apiKeysInitialized.current = true
    }
  }, [configsLoading])

  // ========== External API Keys Mutation ==========
  const updateApiKeysMutation = useMutation({
    mutationFn: (configs: Record<string, string>) => updateSystemConfigs(configs),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['systemConfigs'] })
      toast.success('外部情报 API 密钥已保存')
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

  const formatTime = (h: number, m: number) => `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`

  const dayLabel = (value: string) => {
    const map: Record<string, string> = {
      MON: t('settings.mon'), TUE: t('settings.tue'), WED: t('settings.wed'),
      THU: t('settings.thu'), FRI: t('settings.fri'), SAT: t('settings.sat'), SUN: t('settings.sun'),
    }
    return map[value] || value
  }

  return (
    <div>
      <h1 className="text-2xl font-bold mb-6 dark:text-white">{t('settings.title')}</h1>

      {/* ========== Run Mode Card ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Shield className="w-5 h-5 text-blue-600" />
          <div>
            <h2 className="font-semibold dark:text-white">运行模式</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">系统当前运行模式与扫描引擎状态</p>
          </div>
        </div>
        {modeLoading ? (
          <div className="text-center py-4"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
        ) : mode ? (
          <div className={`rounded-lg border p-4 ${mode.mode === 'DEMO' ? 'bg-yellow-50 dark:bg-yellow-900/10 border-yellow-200 dark:border-yellow-700' : mode.mode === 'REAL' ? 'bg-orange-50 dark:bg-orange-900/10 border-orange-200 dark:border-orange-700' : 'bg-gray-50 dark:bg-gray-700 border-gray-200 dark:border-gray-600'}`}>
            <div className="grid grid-cols-2 gap-x-8 gap-y-2 text-sm">
              <div><span className="text-gray-500 dark:text-gray-400">当前模式：</span>
                <span className={`font-medium ${mode.mode === 'DEMO' ? 'text-yellow-700 dark:text-yellow-400' : mode.mode === 'REAL' ? 'text-orange-700 dark:text-orange-400' : 'text-gray-700 dark:text-gray-300'}`}>
                  {mode.mode === 'DEMO' ? 'Demo Mode（演示模式）' : mode.mode === 'REAL' ? 'Real Mode（真实模式）' : '未知'}
                </span>
              </div>
              <div><span className="text-gray-500 dark:text-gray-400">配置来源：</span><span className="dark:text-gray-200">{mode.configSource}</span></div>
              <div><span className="text-gray-500 dark:text-gray-400">Nmap：</span>
                <span className={mode.nmapAvailable ? 'text-green-600 dark:text-green-400' : 'text-red-500'}>
                  {mode.nmapAvailable ? '✓ 已检测到' : '✗ 未检测到'}
                </span>
              </div>
              <div><span className="text-gray-500 dark:text-gray-400">Nuclei：</span>
                <span className={mode.nucleiAvailable ? 'text-green-600 dark:text-green-400' : 'text-red-500'}>
                  {mode.nucleiAvailable ? '✓ 已检测到' : '✗ 未检测到'}
                </span>
              </div>
              <div><span className="text-gray-500 dark:text-gray-400">公网扫描：</span><span className="dark:text-gray-200">{mode.allowPublicTargets ? '允许' : '默认禁止'}</span></div>
              <div className="col-span-2"><span className="text-gray-500 dark:text-gray-400">实际行为：</span><span className="dark:text-gray-200">{mode.actualBehavior}</span></div>
              <div className="col-span-2"><span className="text-gray-500 dark:text-gray-400">切换方式：</span><span className="dark:text-gray-200">{mode.switchGuide}</span></div>
              <div className="col-span-2"><span className="text-gray-500 dark:text-gray-400">安全提示：</span><span className="dark:text-gray-200">{mode.safetyNotice}</span></div>
            </div>
            <p className="text-xs text-gray-400 dark:text-gray-500 mt-3 border-t dark:border-gray-600 pt-2">
              Nmap / Nuclei 已检测到只代表工具可用性；实际是否执行真实扫描，由当前运行模式决定。
            </p>
          </div>
        ) : (
          <div className="text-sm text-gray-500 dark:text-gray-400 py-2">无法获取运行模式信息</div>
        )}
      </div>

      {/* ========== User Profile Section (All Users) ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <UserIcon className="w-5 h-5 text-blue-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.profile')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.profileDesc')}</p>
          </div>
        </div>

        {profileLoading ? (
          <div className="text-center py-6"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
        ) : editingProfile ? (
          <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">用户名</label>
                <input type="text" value={currentUser?.username || ''} disabled
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm bg-gray-100 dark:bg-gray-600 text-gray-500 dark:text-gray-400" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">角色</label>
                <input type="text" value={currentUser?.role || ''} disabled
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm bg-gray-100 dark:bg-gray-600 text-gray-500 dark:text-gray-400" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">姓名</label>
                <input type="text" value={profileForm.name}
                  onChange={(e) => setProfileForm({ ...profileForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">性别</label>
                <select value={profileForm.gender}
                  onChange={(e) => setProfileForm({ ...profileForm, gender: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200">
                  <option value="">请选择</option>
                  <option value="MALE">男</option>
                  <option value="FEMALE">女</option>
                  <option value="OTHER">其他</option>
                </select>
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">邮箱</label>
                <input type="email" value={profileForm.email}
                  onChange={(e) => setProfileForm({ ...profileForm, email: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200"
                  placeholder="user@example.com" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setEditingProfile(false); setProfileForm({ name: currentUser?.name || '', gender: currentUser?.gender || '', email: currentUser?.email || '' }) }}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
              <button onClick={() => updateProfileMutation.mutate()}
                disabled={updateProfileMutation.isPending}
                className="px-4 py-1.5 text-sm bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {updateProfileMutation.isPending ? '保存中...' : '保存修改'}
              </button>
            </div>
          </div>
        ) : (
          <div className="grid grid-cols-2 gap-x-8 gap-y-3">
            <div><span className="text-sm text-gray-500 dark:text-gray-400">{t('settings.username')}：</span><span className="text-sm font-medium dark:text-white">{currentUser?.username}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">{t('settings.role')}：</span><span className={`text-sm font-medium ${currentUser?.role === 'ADMIN' ? 'text-purple-600 dark:text-purple-400' : 'text-gray-600 dark:text-gray-300'}`}>{currentUser?.role}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">{t('settings.name')}：</span><span className="text-sm dark:text-gray-200">{currentUser?.name || '-'}</span></div>
            <div><span className="text-sm text-gray-500 dark:text-gray-400">{t('settings.gender')}：</span><span className="text-sm dark:text-gray-200">{currentUser?.gender || '-'}</span></div>
            <div className="col-span-2"><span className="text-sm text-gray-500 dark:text-gray-400">{t('settings.email')}：</span><span className="text-sm dark:text-gray-200">{currentUser?.email || '-'}</span></div>
            <div className="col-span-2 flex gap-2 mt-2">
              <button onClick={() => setEditingProfile(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                <Edit3 className="w-3.5 h-3.5" /> {t('settings.editProfile')}
              </button>
              <button onClick={() => setShowChangePwd(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 text-orange-600 dark:text-orange-400">
                <Key className="w-3.5 h-3.5" /> {t('settings.changePassword')}
              </button>
            </div>
          </div>
        )}
      </div>

      {/* ========== Change Password Dialog ========== */}
      {showChangePwd && (
        <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50">
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <h3 className="font-semibold mb-1 dark:text-white">{t('settings.changePassword')}</h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">为账户 "{currentUser?.username}" 修改登录密码</p>
            <div className="space-y-3">
              <input type="password" value={passwordForm.oldPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, oldPassword: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200"
                placeholder="当前密码" />
              <input type="password" value={passwordForm.newPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, newPassword: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200"
                placeholder="新密码（至少6位）" />
              <input type="password" value={passwordForm.confirmPassword}
                onChange={(e) => setPasswordForm({ ...passwordForm, confirmPassword: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200"
                placeholder="确认新密码" />
              {changePwdMutation.isError && (
                <p className="text-red-500 text-xs">{(changePwdMutation.error as any)?.response?.data?.message || '操作失败'}</p>
              )}
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowChangePwd(false); setPasswordForm({ oldPassword: '', newPassword: '', confirmPassword: '' }) }}
                className="px-4 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
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
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Wrench className="w-5 h-5 text-orange-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.scanTools')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.scanToolsDesc')}</p>
          </div>
        </div>

        {configsLoading ? (
          <div className="text-center py-6"><Loader2 className="w-5 h-5 animate-spin mx-auto text-gray-400" /></div>
        ) : editingToolConfig ? (
          <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700">
            <div className="grid grid-cols-1 gap-4">
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">Nmap 路径</label>
                <div className="flex gap-2">
                  <input type="text" value={toolConfig.nmapPath}
                    onChange={(e) => setToolConfig({ ...toolConfig, nmapPath: e.target.value })}
                    className="flex-1 px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="例: /usr/bin/nmap 或 C:\tools\nmap\nmap.exe" />
                  <button onClick={() => handleAutoDetect('nmap')}
                    className="px-3 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200 whitespace-nowrap flex items-center gap-1">
                    <Search className="w-3.5 h-3.5" /> 自动检测
                  </button>
                  <button onClick={() => handleFileBrowse('nmap')}
                    className="px-3 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200 whitespace-nowrap flex items-center gap-1">
                    <FolderOpen className="w-3.5 h-3.5" /> 浏览
                  </button>
                </div>
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">Nuclei 路径</label>
                <div className="flex gap-2">
                  <input type="text" value={toolConfig.nucleiPath}
                    onChange={(e) => setToolConfig({ ...toolConfig, nucleiPath: e.target.value })}
                    className="flex-1 px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="例: /usr/bin/nuclei 或 C:\tools\nuclei\nuclei.exe" />
                  <button onClick={() => handleAutoDetect('nuclei')}
                    className="px-3 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200 whitespace-nowrap flex items-center gap-1">
                    <Search className="w-3.5 h-3.5" /> 自动检测
                  </button>
                  <button onClick={() => handleFileBrowse('nuclei')}
                    className="px-3 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200 whitespace-nowrap flex items-center gap-1">
                    <FolderOpen className="w-3.5 h-3.5" /> 浏览
                  </button>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={handleCancelEditMode}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
              <button onClick={() => updateConfigMutation.mutate()}
                disabled={updateConfigMutation.isPending}
                className="px-4 py-1.5 text-sm bg-orange-600 text-white rounded-lg hover:bg-orange-700 disabled:opacity-50">
                {updateConfigMutation.isPending ? '保存中...' : '保存配置'}
              </button>
            </div>
            <input type="file" ref={nmapFileInputRef} onChange={(e) => handleFileSelected('nmap', e)}
              className="hidden" accept=".exe" />
            <input type="file" ref={nucleiFileInputRef} onChange={(e) => handleFileSelected('nuclei', e)}
              className="hidden" accept=".exe" />
          </div>
        ) : (
          <div className="space-y-3">
            {/* Nmap status */}
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div className="flex items-center gap-2">
                {nmapDetected ? (
                  <CheckCircle2 className="w-4 h-4 text-green-500" />
                ) : (
                  <XCircle className="w-4 h-4 text-red-400" />
                )}
                <span className="text-sm font-medium dark:text-white">Nmap</span>
                <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{toolConfig.nmapPath || 'nmap'}</code>
                {nmapDetected && <span className="text-xs text-green-600 dark:text-green-400">{t('settings.detected')}</span>}
                {!nmapDetected && <span className="text-xs text-red-500">未检测到</span>}
              </div>
            </div>
            {/* Nuclei status */}
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div className="flex items-center gap-2">
                {nucleiDetected ? (
                  <CheckCircle2 className="w-4 h-4 text-green-500" />
                ) : (
                  <XCircle className="w-4 h-4 text-red-400" />
                )}
                <span className="text-sm font-medium dark:text-white">Nuclei</span>
                <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">{toolConfig.nucleiPath || 'nuclei'}</code>
                {nucleiDetected && <span className="text-xs text-green-600 dark:text-green-400">{t('settings.detected')}</span>}
                {!nucleiDetected && <span className="text-xs text-red-500">未检测到</span>}
              </div>
            </div>
            <div className="flex gap-2">
              <button onClick={handleEnterEditMode}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                <Settings className="w-3.5 h-3.5" /> {t('settings.editPath')}
              </button>
              <button onClick={() => setShowToolHelp(!showToolHelp)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                <HelpCircle className="w-3.5 h-3.5" /> {t('settings.installGuide')}
              </button>
            </div>

            {/* Tool installation help */}
            {showToolHelp && (
              <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4 text-sm">
                <div>
                  <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                    <span className="w-5 h-5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs flex items-center justify-center font-bold">1</span>
                    Nmap 下载与安装
                  </h4>
                  <p className="text-gray-500 dark:text-gray-400 mb-2">Nmap 是开源的网络扫描工具，用于主机发现和端口扫描。</p>
                  <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                    <p><strong>Windows:</strong> 下载安装包 → <a href="https://nmap.org/download.html" target="_blank" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5">nmap.org/download.html <ExternalLink className="w-3 h-3" /></a></p>
                    <p className="ml-4 text-gray-400 dark:text-gray-500">安装后默认路径: <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">C:\Program Files (x86)\Nmap\nmap.exe</code></p>
                    <p><strong>macOS:</strong> <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">brew install nmap</code></p>
                    <p><strong>Linux (Debian/Ubuntu):</strong> <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">sudo apt install nmap</code></p>
                    <p><strong>Linux (RHEL/CentOS):</strong> <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">sudo yum install nmap</code></p>
                    <p className="mt-1 text-gray-400 dark:text-gray-500">安装后终端输入 <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">nmap --version</code> 验证安装成功</p>
                  </div>
                </div>
                <div>
                  <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                    <span className="w-5 h-5 rounded bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400 text-xs flex items-center justify-center font-bold">2</span>
                    Nuclei 下载与安装
                  </h4>
                  <p className="text-gray-500 dark:text-gray-400 mb-2">Nuclei 是 ProjectDiscovery 的漏洞扫描引擎，基于模板匹配检测漏洞。</p>
                  <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                    <p><strong>所有平台 (推荐):</strong> <a href="https://github.com/projectdiscovery/nuclei/releases" target="_blank" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5">GitHub Releases <ExternalLink className="w-3 h-3" /></a></p>
                    <p className="ml-4 text-gray-400 dark:text-gray-500">下载对应系统的可执行文件，放到 PATH 目录下</p>
                    <p><strong>macOS:</strong> <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">brew install nuclei</code></p>
                    <p><strong>Linux / Windows (Go):</strong> <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">go install -v github.com/projectdiscovery/nuclei/v3/cmd/nuclei@latest</code></p>
                    <p className="mt-1 text-gray-400 dark:text-gray-500">安装后运行 <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">nuclei -version</code> 验证，首次需下载模板: <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">nuclei -update-templates</code></p>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ========== Scheduled Scan Config Section ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Clock className="w-5 h-5 text-green-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.scheduledScan')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.scheduledScanDesc')}</p>
          </div>
        </div>

        {editingScheduled ? (
          <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4">
            {/* Daily Scan */}
            <div className="border-b dark:border-gray-600 pb-4">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-medium dark:text-white">{t('settings.dailyQuickScan')}</h3>
                <label className="flex items-center gap-2 cursor-pointer">
                  <span className="text-xs text-gray-500 dark:text-gray-400">{scheduledConfig.dailyEnabled ? t('settings.enabled') : t('settings.disabled')}</span>
                  <input type="checkbox" checked={scheduledConfig.dailyEnabled}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyEnabled: e.target.checked })}
                    className="rounded" />
                </label>
              </div>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.dailyTarget')}</label>
                  <input type="text" value={scheduledConfig.dailyTarget}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyTarget: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="192.168.1.0/24" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.dailyTime')}</label>
                  <div className="flex items-center gap-1">
                    <select value={scheduledConfig.dailyHour}
                      onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyHour: Number(e.target.value) })}
                      className="px-2 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                      {HOURS.map(h => (
                        <option key={h} value={h}>{String(h).padStart(2, '0')}</option>
                      ))}
                    </select>
                    <span className="text-sm dark:text-gray-300">:</span>
                    <select value={scheduledConfig.dailyMinute}
                      onChange={(e) => setScheduledConfig({ ...scheduledConfig, dailyMinute: Number(e.target.value) })}
                      className="px-2 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                      {MINUTES.map(m => (
                        <option key={m} value={m}>{String(m).padStart(2, '0')}</option>
                      ))}
                    </select>
                    <span className="text-xs text-gray-400 dark:text-gray-500 ml-1">{t('settings.everyDay')}</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Weekly Scan */}
            <div>
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-sm font-medium dark:text-white">{t('settings.weeklyFullScan')}</h3>
                <label className="flex items-center gap-2 cursor-pointer">
                  <span className="text-xs text-gray-500 dark:text-gray-400">{scheduledConfig.weeklyEnabled ? t('settings.enabled') : t('settings.disabled')}</span>
                  <input type="checkbox" checked={scheduledConfig.weeklyEnabled}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyEnabled: e.target.checked })}
                    className="rounded" />
                </label>
              </div>
              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.weeklyTarget')}</label>
                  <input type="text" value={scheduledConfig.weeklyTarget}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyTarget: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="192.168.1.0/24" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.weeklyDay')}</label>
                  <select value={scheduledConfig.weeklyDay}
                    onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyDay: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                    {DAY_VALUES.map(d => (
                      <option key={d} value={d}>{dayLabel(d)}</option>
                    ))}
                  </select>
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.weeklyTime')}</label>
                  <div className="flex items-center gap-1">
                    <select value={scheduledConfig.weeklyHour}
                      onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyHour: Number(e.target.value) })}
                      className="px-2 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                      {HOURS.map(h => (
                        <option key={h} value={h}>{String(h).padStart(2, '0')}</option>
                      ))}
                    </select>
                    <span className="text-sm dark:text-gray-300">:</span>
                    <select value={scheduledConfig.weeklyMinute}
                      onChange={(e) => setScheduledConfig({ ...scheduledConfig, weeklyMinute: Number(e.target.value) })}
                      className="px-2 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                      {MINUTES.map(m => (
                        <option key={m} value={m}>{String(m).padStart(2, '0')}</option>
                      ))}
                    </select>
                  </div>
                </div>
              </div>
            </div>

            <div className="flex justify-end gap-2">
              <button onClick={() => setEditingScheduled(false)}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">{t('common.cancel')}</button>
              <button onClick={() => updateScheduledMutation.mutate()}
                disabled={updateScheduledMutation.isPending}
                className="px-4 py-1.5 text-sm bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50">
                {updateScheduledMutation.isPending ? t('common.save') + '...' : t('settings.saveScheduledConfig')}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div>
                <span className={`inline-block w-2 h-2 rounded-full mr-2 ${scheduledConfig.dailyEnabled ? 'bg-green-500' : 'bg-gray-300'}`} />
                <span className="text-sm font-medium dark:text-white">{t('settings.dailyScan')}</span>
                <span className="text-xs text-gray-400 dark:text-gray-500 ml-3">{scheduledConfig.dailyTarget}</span>
              </div>
              <span className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">
                {scheduledConfig.dailyEnabled ? `${t('settings.everyDay')} ${formatTime(scheduledConfig.dailyHour, scheduledConfig.dailyMinute)}` : t('settings.disabled')}
              </span>
            </div>
            <div className="flex justify-between items-center px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
              <div>
                <span className={`inline-block w-2 h-2 rounded-full mr-2 ${scheduledConfig.weeklyEnabled ? 'bg-green-500' : 'bg-gray-300'}`} />
                <span className="text-sm font-medium dark:text-white">{t('settings.weeklyScan')}</span>
                <span className="text-xs text-gray-400 dark:text-gray-500 ml-3">{scheduledConfig.weeklyTarget}</span>
              </div>
              <span className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded">
                {scheduledConfig.weeklyEnabled
                  ? `${t('settings.every')}${dayLabel(scheduledConfig.weeklyDay)} ${formatTime(scheduledConfig.weeklyHour, scheduledConfig.weeklyMinute)}`
                  : t('settings.disabled')}
              </span>
            </div>
            <button onClick={() => setEditingScheduled(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
              <Settings className="w-3.5 h-3.5" /> {t('settings.editSchedule')}
            </button>
          </div>
        )}
      </div>

      {/* ========== External API Keys ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <Globe className="w-5 h-5 text-purple-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.apiKeys')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.apiKeysDesc')}</p>
          </div>
        </div>
        <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">Censys API ID</label>
              <input type="text" value={apiKeysConfig.censysId}
                onChange={(e) => setApiKeysConfig({ ...apiKeysConfig, censysId: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                placeholder="Censys API ID" />
            </div>
            <div>
              <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">Censys API Secret</label>
              <input type="password" value={apiKeysConfig.censysSecret}
                onChange={(e) => setApiKeysConfig({ ...apiKeysConfig, censysSecret: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                placeholder="Censys API Secret" />
            </div>
            <div className="col-span-2">
              <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">VirusTotal API Key</label>
              <input type="password" value={apiKeysConfig.virustotalKey}
                onChange={(e) => setApiKeysConfig({ ...apiKeysConfig, virustotalKey: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                placeholder="从 https://www.virustotal.com/gui/my-apikey 获取" />
            </div>
          </div>
          <div className="flex items-center gap-2">
            <button onClick={() => {
              const apiKeys: Record<string, string> = {
                'censys-api-id': apiKeysConfig.censysId,
                'censys-api-secret': apiKeysConfig.censysSecret,
                'virustotal-api-key': apiKeysConfig.virustotalKey,
              }
              updateApiKeysMutation.mutate(apiKeys)
            }}
              disabled={updateApiKeysMutation.isPending}
              className="px-3 py-1.5 text-sm bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50">
              {updateApiKeysMutation.isPending ? '保存中...' : t('settings.saveApiConfig')}
            </button>
            <button onClick={() => setShowApiKeysHelp(!showApiKeysHelp)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
              <HelpCircle className="w-3.5 h-3.5" /> {showApiKeysHelp ? '收起教程' : t('settings.getApiTutorial')}
            </button>
          </div>

          {showApiKeysHelp && (
            <div className="space-y-4 text-sm">
              <div>
                <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                  <span className="w-5 h-5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs flex items-center justify-center font-bold">1</span>
                  Censys API 密钥获取
                </h4>
                <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                  <p><strong>步骤 1：</strong>访问 <a href="https://search.censys.io/account/api" target="_blank" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5">Censys API 页面 <ExternalLink className="w-3 h-3" /></a>，注册或登录 Censys 账户。</p>
                  <p><strong>步骤 2：</strong>在 API 管理页面，点击 <strong>"Create API Key"</strong> 或查看已有的 API 凭据。</p>
                  <p><strong>步骤 3：</strong>复制 <strong>API ID</strong> 和 <strong>API Secret</strong>，分别填入上方的输入框。</p>
                  <p className="mt-1 text-gray-400 dark:text-gray-500">💡 免费账户每月有 250 次查询额度，可用于 IP 信息查询和证书搜索。</p>
                  <p className="text-gray-400 dark:text-gray-500">⚠️ Secret 仅在创建时显示一次，请妥善保存。</p>
                </div>
              </div>
              <div>
                <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                  <span className="w-5 h-5 rounded bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400 text-xs flex items-center justify-center font-bold">2</span>
                  VirusTotal API 密钥获取
                </h4>
                <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                  <p><strong>步骤 1：</strong>访问 <a href="https://www.virustotal.com/gui/my-apikey" target="_blank" className="text-blue-600 dark:text-blue-400 hover:underline inline-flex items-center gap-0.5">VirusTotal API Key 页面 <ExternalLink className="w-3 h-3" /></a>，注册或登录 VirusTotal 账户。</p>
                  <p><strong>步骤 2：</strong>在个人设置 → API Key 页面，可看到自动生成的 API Key。</p>
                  <p><strong>步骤 3：</strong>复制 <strong>API Key</strong>，填入上方 "VirusTotal API Key" 输入框。</p>
                  <p className="mt-1 text-gray-400 dark:text-gray-500">💡 免费 API Key 有每日 500 次查询限制，每分钟最多 4 次。</p>
                  <p className="text-gray-400 dark:text-gray-500">⚠️ API Key 请勿公开泄露，否则他人可能消耗你的配额。</p>
                </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* ========== Scan Quick Link ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center justify-between">
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.scanManagement')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.scanManagementDesc')}</p>
          </div>
          <Link to="/scan-tasks"
            className="flex items-center gap-1.5 px-4 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700">
            <ScanLine className="w-4 h-4" /> {t('settings.goToScan')}
          </Link>
        </div>
      </div>

      {/* ========== Webhook Notification Config Section ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <BellRing className="w-5 h-5 text-purple-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.webhook')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.webhookDesc')}</p>
          </div>
        </div>

        {editingWebhook ? (
          <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4">
            <label className="flex items-center justify-between cursor-pointer border-b dark:border-gray-600 pb-3">
              <div>
                <span className="text-sm font-medium dark:text-white">启用告警通知</span>
                <p className="text-xs text-gray-400 dark:text-gray-500">关闭后所有平台均不发送通知</p>
              </div>
              <input type="checkbox" checked={webhookConfig.enabled}
                onChange={(e) => setWebhookConfig({ ...webhookConfig, enabled: e.target.checked })}
                className="rounded w-4 h-4" />
            </label>
            {([
              { key: 'dingtalk' as const, label: t('settings.webhookDingtalk') + ' (DingTalk)', enabledKey: 'dingtalkEnabled' as const },
              { key: 'feishu' as const, label: t('settings.webhookFeishu') + ' (Feishu/Lark)', enabledKey: 'feishuEnabled' as const },
              { key: 'wecom' as const, label: t('settings.webhookWecom') + ' (WeCom)', enabledKey: 'wecomEnabled' as const },
            ]).map(({ key, label, enabledKey }) => (
              <div key={key}>
                <div className="flex items-center justify-between mb-1">
                  <label className="text-xs text-gray-500 dark:text-gray-400">{label} Webhook URL</label>
                  <label className="flex items-center gap-1.5 cursor-pointer">
                    <span className="text-xs text-gray-400 dark:text-gray-500">{webhookConfig[enabledKey] ? t('settings.enabled') : t('settings.disabled')}</span>
                    <input type="checkbox" checked={webhookConfig[enabledKey]}
                      onChange={(e) => setWebhookConfig({ ...webhookConfig, [enabledKey]: e.target.checked })}
                      className="rounded" />
                  </label>
                </div>
                <input type="text" value={webhookConfig[key]}
                  onChange={(e) => setWebhookConfig({ ...webhookConfig, [key]: e.target.value })}
                  className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder={`https://...`} />
              </div>
            ))}
            <div className="flex justify-end gap-2">
              <button onClick={() => setEditingWebhook(false)}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
              <button onClick={() => updateWebhookMutation.mutate()}
                disabled={updateWebhookMutation.isPending}
                className="px-4 py-1.5 text-sm bg-purple-600 text-white rounded-lg hover:bg-purple-700 disabled:opacity-50">
                {updateWebhookMutation.isPending ? '保存中...' : '保存配置'}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {/* Global webhook status bar */}
            <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-gray-50 dark:bg-gray-700">
              <div className="flex items-center gap-2">
                <span className={`inline-block w-2.5 h-2.5 rounded-full ${webhookConfig.enabled ? 'bg-green-500' : 'bg-gray-400'}`} />
                <span className="text-sm font-medium dark:text-white">{t('settings.globalWebhook')}</span>
              </div>
              <span className={`text-xs font-medium px-2 py-0.5 rounded ${webhookConfig.enabled ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-400'}`}>
                {webhookConfig.enabled ? t('settings.webhookEnabled') : t('settings.webhookDisabled')}
              </span>
            </div>
            {([
              { key: 'dingtalk' as const, label: t('settings.webhookDingtalk'), enabledKey: 'dingtalkEnabled' as const },
              { key: 'feishu' as const, label: t('settings.webhookFeishu'), enabledKey: 'feishuEnabled' as const },
              { key: 'wecom' as const, label: t('settings.webhookWecom'), enabledKey: 'wecomEnabled' as const },
            ]).map(({ key, label, enabledKey }) => {
              const url = webhookConfig[key]
              const enabled = webhookConfig[enabledKey]
              const globalOn = webhookConfig.enabled
              const active = globalOn && enabled && !!url
              return (
                <div key={key} className="flex items-center justify-between px-3 py-2 rounded hover:bg-gray-50 dark:hover:bg-gray-700">
                  <div className="flex items-center gap-2 min-w-0">
                    <span className={`inline-block w-2 h-2 rounded-full ${active ? 'bg-green-500' : !enabled ? 'bg-gray-300' : !url ? 'bg-amber-400' : 'bg-gray-300'}`} />
                    <span className="text-sm font-medium dark:text-white">{label}</span>
                    {!enabled && <span className="text-xs text-gray-400 dark:text-gray-500">{t('settings.disabled')}</span>}
                    {enabled && !url && <span className="text-xs text-amber-500">{t('settings.webhookNoUrl')}</span>}
                  </div>
                  <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-2 py-0.5 rounded max-w-[240px] truncate ml-2">
                    {url || '—'}
                  </code>
                </div>
              )
            })}
            <div className="flex gap-2">
              <button onClick={() => setEditingWebhook(true)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                <Settings className="w-3.5 h-3.5" /> {t('settings.editNotification')}
              </button>
              <button onClick={() => setShowWebhookHelp(!showWebhookHelp)}
                className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
                <HelpCircle className="w-3.5 h-3.5" /> {showWebhookHelp ? '收起教程' : t('settings.installGuide')}
              </button>
            </div>

            {/* Webhook tutorial */}
            {showWebhookHelp && (
              <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-4 text-sm">
                <div>
                  <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                    <span className="w-5 h-5 rounded bg-blue-100 dark:bg-blue-900/40 text-blue-600 dark:text-blue-400 text-xs flex items-center justify-center font-bold">1</span>
                    {t('settings.webhookDingtalk')} (DingTalk) — 获取 Webhook URL
                  </h4>
                  <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                    <p><strong>步骤 1：</strong>打开{t('settings.webhookDingtalk')} PC 端或移动端，进入目标群聊。</p>
                    <p><strong>步骤 2：</strong>点击群设置 → <strong>智能群助手</strong> → <strong>添加机器人</strong>。</p>
                    <p><strong>步骤 3：</strong>选择 <strong>"自定义（通过 Webhook 接入自定义服务）"</strong>。</p>
                    <p><strong>步骤 4：</strong>填写机器人名称（如 "ServerScout 安全巡检"），可选添加头像。</p>
                    <p><strong>步骤 5：</strong>勾选协议，点击 <strong>"完成"</strong>，复制生成的 Webhook URL。</p>
                    <p className="mt-1 text-gray-400 dark:text-gray-500">URL 格式: <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">https://oapi.dingtalk.com/robot/send?access_token=xxxxxx</code></p>
                    <p className="text-gray-400 dark:text-gray-500">⚠️ 请勿泄露 Webhook URL，否则他人可向您的群聊发送消息。</p>
                    <p className="text-blue-600 dark:text-blue-400">📖 官方文档: <a href="https://open.dingtalk.com/document/orgapp/custom-robot-access" target="_blank" className="hover:underline inline-flex items-center gap-0.5">自定义机器人接入 <ExternalLink className="w-3 h-3" /></a></p>
                  </div>
                </div>
                <div>
                  <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                    <span className="w-5 h-5 rounded bg-green-100 dark:bg-green-900/40 text-green-600 dark:text-green-400 text-xs flex items-center justify-center font-bold">2</span>
                    {t('settings.webhookFeishu')} (Feishu/Lark) — 获取 Webhook URL
                  </h4>
                  <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                    <p><strong>步骤 1：</strong>打开{t('settings.webhookFeishu')} PC 端或网页版，进入目标群聊。</p>
                    <p><strong>步骤 2：</strong>点击群设置 → <strong>群机器人</strong> → <strong>添加机器人</strong> → <strong>自定义机器人</strong>。</p>
                    <p><strong>步骤 3：</strong>填写机器人名称和描述，点击 <strong>"添加"</strong>。</p>
                    <p><strong>步骤 4：</strong>复制生成的 Webhook URL。</p>
                    <p className="mt-1 text-gray-400 dark:text-gray-500">URL 格式: <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxx</code></p>
                    <p className="text-blue-600 dark:text-blue-400">📖 官方文档: <a href="https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot" target="_blank" className="hover:underline inline-flex items-center gap-0.5">自定义机器人使用指南 <ExternalLink className="w-3 h-3" /></a></p>
                  </div>
                </div>
                <div>
                  <h4 className="font-medium mb-2 dark:text-white flex items-center gap-2">
                    <span className="w-5 h-5 rounded bg-amber-100 dark:bg-amber-900/40 text-amber-600 dark:text-amber-400 text-xs flex items-center justify-center font-bold">3</span>
                    {t('settings.webhookWecom')} (WeCom) — 获取 Webhook URL
                  </h4>
                  <div className="space-y-1 text-xs text-gray-600 dark:text-gray-300">
                    <p><strong>步骤 1：</strong>打开{t('settings.webhookWecom')} PC 端或移动端，进入目标群聊。</p>
                    <p><strong>步骤 2：</strong>点击群设置 → <strong>群机器人</strong> → <strong>添加群机器人</strong>。</p>
                    <p><strong>步骤 3：</strong>填写机器人名称，点击 <strong>"添加"</strong>。</p>
                    <p><strong>步骤 4：</strong>复制生成的 Webhook URL。</p>
                    <p className="mt-1 text-gray-400 dark:text-gray-500">URL 格式: <code className="bg-gray-200 dark:bg-gray-600 px-1 rounded">https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=xxxxxx</code></p>
                    <p className="text-blue-600 dark:text-blue-400">📖 官方文档: <a href="https://developer.work.weixin.qq.com/document/path/91770" target="_blank" className="hover:underline inline-flex items-center gap-0.5">群机器人配置说明 <ExternalLink className="w-3 h-3" /></a></p>
                  </div>
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* ========== Email Notification Config ========== */}
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <BellRing className="w-5 h-5 text-rose-600" />
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.emailNotification')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.emailNotificationDesc')}</p>
          </div>
        </div>

        {editingEmail ? (
          <div className="border dark:border-gray-600 rounded-lg p-4 bg-gray-50 dark:bg-gray-700 space-y-3">
            <label className="flex items-center justify-between cursor-pointer border-b dark:border-gray-600 pb-3">
              <div>
                <span className="text-sm font-medium dark:text-white">启用{t('settings.emailStatus')}</span>
                <p className="text-xs text-gray-400 dark:text-gray-500">关闭后扫描完成不发送邮件报告</p>
              </div>
              <input type="checkbox"
                checked={emailConfig.enabled}
                onChange={(e) => setEmailConfig({ ...emailConfig, enabled: e.target.checked })}
                className="rounded w-4 h-4" />
            </label>
            <div>
              <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.recipientEmail')}</label>
              <input type="email" value={emailConfig.recipient}
                onChange={(e) => setEmailConfig({ ...emailConfig, recipient: e.target.value })}
                className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                placeholder="admin@example.com" />
            </div>
            <div className="border-t dark:border-gray-600 pt-3 mt-2">
              <p className="text-xs text-gray-500 dark:text-gray-400 mb-2">{t('settings.smtpServer')}配置（使用 QQ邮箱/163/Gmail 等邮箱的 SMTP 授权码）</p>
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.smtpServer')}地址</label>
                  <input type="text" value={emailConfig.smtpHost}
                    onChange={(e) => setEmailConfig({ ...emailConfig, smtpHost: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="smtp.qq.com" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">SMTP 端口</label>
                  <input type="text" value={emailConfig.smtpPort}
                    onChange={(e) => setEmailConfig({ ...emailConfig, smtpPort: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="587" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">发件邮箱账号</label>
                  <input type="text" value={emailConfig.smtpUsername}
                    onChange={(e) => setEmailConfig({ ...emailConfig, smtpUsername: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="your-email@qq.com" />
                </div>
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">SMTP 授权码/密码</label>
                  <input type="password" value={emailConfig.smtpPassword}
                    onChange={(e) => setEmailConfig({ ...emailConfig, smtpPassword: e.target.value })}
                    className="w-full px-3 py-2 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder="邮箱 SMTP 授权码（非登录密码）" />
                </div>
                <div className="col-span-2">
                  <label className="flex items-center gap-2 cursor-pointer">
                    <input type="checkbox"
                      checked={emailConfig.smtpSsl}
                      onChange={(e) => setEmailConfig({ ...emailConfig, smtpSsl: e.target.checked })}
                      className="rounded" />
                    <span className="text-xs text-gray-500 dark:text-gray-400">启用 {t('settings.sslEncrypted')} (端口 465 通常需要勾选)</span>
                  </label>
                </div>
              </div>
            </div>
            <div className="flex justify-end gap-2 pt-2">
              <button onClick={() => setEditingEmail(false)}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
              <button onClick={() => updateEmailMutation.mutate()}
                disabled={updateEmailMutation.isPending}
                className="px-4 py-1.5 text-sm bg-rose-600 text-white rounded-lg hover:bg-rose-700 disabled:opacity-50">
                {updateEmailMutation.isPending ? '保存中...' : '保存邮件配置'}
              </button>
            </div>
          </div>
        ) : (
          <div className="space-y-3">
            {/* Status summary bar */}
            <div className="flex items-center justify-between px-3 py-2 rounded-lg bg-gray-50 dark:bg-gray-700">
              <div className="flex items-center gap-2">
                <span className={`inline-block w-2.5 h-2.5 rounded-full ${emailConfig.enabled && emailConfig.recipient && emailConfig.smtpHost ? 'bg-green-500' : 'bg-gray-400'}`} />
                <span className="text-sm font-medium dark:text-white">{t('settings.emailStatus')}</span>
              </div>
              <span className={`text-xs font-medium px-2 py-0.5 rounded ${emailConfig.enabled ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-gray-200 dark:bg-gray-600 text-gray-500 dark:text-gray-400'}`}>
                {emailConfig.enabled ? t('settings.webhookEnabled') : t('settings.webhookDisabled')}
              </span>
            </div>
            {/* Detail rows */}
            <div className="px-3 space-y-1.5 text-sm">
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">{t('settings.recipientEmail')}</span>
                <span className="dark:text-gray-200 font-mono text-xs">{emailConfig.recipient || '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">{t('settings.smtpServer')}</span>
                <span className="dark:text-gray-200 font-mono text-xs">{emailConfig.smtpHost ? `${emailConfig.smtpHost}:${emailConfig.smtpPort}` : '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">{t('settings.senderAccount')}</span>
                <span className="dark:text-gray-200 font-mono text-xs">{emailConfig.smtpUsername || '—'}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-gray-500 dark:text-gray-400">{t('settings.connectionType')}</span>
                <span className="dark:text-gray-200 text-xs">{emailConfig.smtpSsl ? t('settings.sslEncrypted') : t('settings.plainStarttls')}</span>
              </div>
            </div>
            <button onClick={() => setEditingEmail(true)}
              className="flex items-center gap-1 px-3 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 dark:text-gray-200">
              <Settings className="w-3.5 h-3.5" /> {t('settings.editEmail')}
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
              <h2 className="font-semibold dark:text-white">{t('settings.scanPlugins')}</h2>
              <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.scanPluginsDesc')}</p>
            </div>
          </div>
          <button
            onClick={() => { setEditingPlugin(null); setPluginForm({ name: '', scanType: '', description: '', commandTemplate: '', resultParser: 'line', findingRegex: '' }); setShowPluginForm(true) }}
            className="flex items-center gap-1 px-3 py-2 bg-indigo-600 text-white text-sm rounded-lg hover:bg-indigo-700"
          >
            <Plus className="w-4 h-4" /> {t('settings.addPlugin')}
          </button>
        </div>

        {/* Plugin Form */}
        {showPluginForm && (
          <div className="border dark:border-gray-600 rounded-lg p-4 mb-4 bg-gray-50 dark:bg-gray-700">
            <h3 className="font-medium mb-3 dark:text-white">{editingPlugin ? t('settings.editPlugin') : t('settings.createPlugin')}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginName')}</label>
                <input type="text" value={pluginForm.name}
                  onChange={(e) => setPluginForm({ ...pluginForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder="例: SSH 弱口令检测" />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginScanType')}</label>
                <input type="text" value={pluginForm.scanType}
                  onChange={(e) => setPluginForm({ ...pluginForm, scanType: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder="例: ssh-brute" disabled={!!editingPlugin} />
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginDescription')}</label>
                <input type="text" value={pluginForm.description}
                  onChange={(e) => setPluginForm({ ...pluginForm, description: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder="简述此策略的用途" />
              </div>
              <div className="col-span-2">
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginCommand')}</label>
                <textarea value={pluginForm.commandTemplate} rows={3}
                  onChange={(e) => setPluginForm({ ...pluginForm, commandTemplate: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder={"nmap -sV -p {port_range} --script ssh-brute {target}\n或: python3 custom_scanner.py --target {target}"} />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginParser')}</label>
                <select value={pluginForm.resultParser}
                  onChange={(e) => setPluginForm({ ...pluginForm, resultParser: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                  <option value="line">{t('settings.parserLine')}</option>
                  <option value="raw">{t('settings.parserRaw')}</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.pluginRegex')}</label>
                <input type="text" value={pluginForm.findingRegex}
                  onChange={(e) => setPluginForm({ ...pluginForm, findingRegex: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm font-mono focus:outline-none focus:ring-2 focus:ring-indigo-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder={'(?<severity>critical|high) (?<name>.+)'} />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowPluginForm(false); setEditingPlugin(null) }}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">{t('common.cancel')}</button>
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
                {createPluginMutation.isPending || updatePluginMutation.isPending ? t('settings.saving') : editingPlugin ? t('settings.saveChangesBtn') : t('settings.createPluginBtn')}
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
            <p className="text-xs mt-1">点击"{t('settings.addPlugin')}"创建自定义扫描策略，如 SSH 弱口令检测、Redis 未授权扫描等</p>
          </div>
        ) : (
          <div className="space-y-2">
            {plugins.map((p: any) => (
              <div key={p.id} className={`border dark:border-gray-600 rounded-lg p-3 flex items-center justify-between ${p.enabled ? 'hover:bg-gray-50 dark:hover:bg-gray-700' : 'opacity-50 bg-gray-50 dark:bg-gray-800'}`}>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className={`w-2 h-2 rounded-full ${p.enabled ? 'bg-green-500' : 'bg-gray-300'}`} />
                    <span className="text-sm font-medium dark:text-white">{p.name}</span>
                    <code className="text-xs bg-gray-100 dark:bg-gray-700 dark:text-gray-200 px-1.5 py-0.5 rounded">{p.scanType}</code>
                    {p.description && <span className="text-xs text-gray-400 dark:text-gray-500 truncate">{p.description}</span>}
                  </div>
                </div>
                <div className="flex items-center gap-1 ml-2">
                  <button onClick={() => togglePluginMutation.mutate(p.id)}
                    className={`px-2 py-1 text-xs rounded ${p.enabled ? 'text-orange-600 hover:bg-orange-50 dark:hover:bg-orange-900/20' : 'text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20'}`}
                    title={p.enabled ? t('common.disabled') : t('common.enabled')}>
                    {p.enabled ? t('common.disabled') : t('common.enabled')}
                  </button>
                  <button onClick={() => {
                    setEditingPlugin(p)
                    setPluginForm({ name: p.name, scanType: p.scanType, description: p.description || '', commandTemplate: p.commandTemplate, resultParser: p.resultParser || 'line', findingRegex: p.findingRegex || '' })
                    setShowPluginForm(true)
                  }} className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-indigo-600" title="编辑">
                    <Edit3 className="w-3.5 h-3.5" />
                  </button>
                  <button onClick={() => { if (confirm(`确认删除策略 "${p.name}"?`)) deletePluginMutation.mutate(p.id) }}
                    className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-red-600" title="删除">
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
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6">
        <div className="flex justify-between items-center mb-4">
          <div>
            <h2 className="font-semibold dark:text-white">{t('settings.userManagement')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.userManagementDesc')}</p>
          </div>
          <button
            onClick={() => {
              setEditingUser(null)
              setUserForm({ username: '', password: '', name: '', gender: '', role: 'USER', email: '' })
              setShowUserForm(true)
            }}
            className="flex items-center gap-1 px-3 py-2 bg-blue-600 text-white text-sm rounded-lg hover:bg-blue-700"
          >
            <UserPlus className="w-4 h-4" /> {t('settings.addUser')}
          </button>
        </div>

        {showUserForm && (
          <div className="border dark:border-gray-600 rounded-lg p-4 mb-4 bg-gray-50 dark:bg-gray-700">
            <h3 className="font-medium mb-3 dark:text-white">{editingUser ? t('settings.editUser') : t('settings.createUser')}</h3>
            <div className="grid grid-cols-2 gap-4">
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.username')}</label>
                <input type="text" value={userForm.username}
                  onChange={(e) => setUserForm({ ...userForm, username: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  disabled={!!editingUser} placeholder={t('settings.userNamePlaceholder')} />
              </div>
              {!editingUser && (
                <div>
                  <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('login.password')}</label>
                  <input type="password" value={userForm.password}
                    onChange={(e) => setUserForm({ ...userForm, password: e.target.value })}
                    className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                    placeholder={t('settings.passwordPlaceholder')} />
                </div>
              )}
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.name')}</label>
                <input type="text" value={userForm.name}
                  onChange={(e) => setUserForm({ ...userForm, name: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder={t('settings.userRealNamePlaceholder')} />
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.gender')}</label>
                <select value={userForm.gender}
                  onChange={(e) => setUserForm({ ...userForm, gender: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                  <option value="">{t('settings.selectOption')}</option>
                  <option value="MALE">{t('settings.male')}</option>
                  <option value="FEMALE">{t('settings.female')}</option>
                  <option value="OTHER">{t('settings.other')}</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.role')}</label>
                <select value={userForm.role}
                  onChange={(e) => setUserForm({ ...userForm, role: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200">
                  <option value="USER">{t('settings.roleUser')} (USER)</option>
                  <option value="ADMIN">{t('settings.roleAdmin')} (ADMIN)</option>
                </select>
              </div>
              <div>
                <label className="block text-xs text-gray-500 dark:text-gray-400 mb-1">{t('settings.emailOptional')}</label>
                <input type="email" value={userForm.email}
                  onChange={(e) => setUserForm({ ...userForm, email: e.target.value })}
                  className="w-full px-3 py-1.5 border dark:border-gray-600 rounded text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-600 dark:text-gray-200"
                  placeholder="user@example.com" />
              </div>
            </div>
            <div className="flex justify-end gap-2 mt-4">
              <button onClick={() => { setShowUserForm(false); setEditingUser(null) }}
                className="px-4 py-1.5 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">{t('common.cancel')}</button>
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
                {createUserMutation.isPending || updateUserMutation.isPending ? t('settings.saving') : editingUser ? t('settings.saveChangesBtn') : t('settings.createUserBtn')}
              </button>
            </div>
            {(createUserMutation.isError || updateUserMutation.isError) && (
              <p className="text-red-500 text-xs mt-2">
                {(createUserMutation.error as any)?.response?.data?.message || (updateUserMutation.error as any)?.response?.data?.message || t('login.errorGeneral')}
              </p>
            )}
          </div>
        )}

        {usersLoading ? (
          <div className="text-center py-10 text-gray-400 dark:text-gray-500"><Loader2 className="w-5 h-5 animate-spin mx-auto" /></div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-sm dark:text-gray-300">
              <thead>
                <tr className="border-b dark:border-gray-600 text-gray-500 dark:text-gray-400 text-xs uppercase">
                  <th className="text-left py-2 pl-3">{t('settings.userTableUser')}</th>
                  <th className="text-left py-2">{t('settings.userTableName')}</th>
                  <th className="text-left py-2">{t('settings.userTableRole')}</th>
                  <th className="text-left py-2">{t('settings.userTableEmail')}</th>
                  <th className="text-left py-2">{t('settings.userTableStatus')}</th>
                  <th className="text-left py-2">{t('settings.userTableCreatedAt')}</th>
                  <th className="text-right py-2 pr-3">{t('settings.userTableActions')}</th>
                </tr>
              </thead>
              <tbody>
                {users.map((user) => (
                  <tr key={user.id} className="border-b dark:border-gray-700 last:border-0 hover:bg-gray-50 dark:hover:bg-gray-700">
                    <td className="py-2.5 pl-3 font-medium dark:text-gray-200">{user.username}</td>
                    <td className="py-2.5 text-gray-600 dark:text-gray-300">{user.name || '-'}</td>
                    <td className="py-2.5">
                      <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-medium ${
                        user.role === 'ADMIN' ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-700 dark:text-purple-400' : 'bg-gray-100 dark:bg-gray-700 text-gray-600 dark:text-gray-300'
                      }`}>
                        <Shield className="w-3 h-3" />{user.role}
                      </span>
                    </td>
                    <td className="py-2.5 text-gray-500 dark:text-gray-400">{user.email || '-'}</td>
                    <td className="py-2.5">
                      <span className={`px-2 py-0.5 rounded text-xs font-medium ${
                        user.enabled ? 'bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-400' : 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
                      }`}>{user.enabled ? t('common.enabled') : t('common.disabled')}</span>
                    </td>
                    <td className="py-2.5 text-gray-400 dark:text-gray-500 text-xs">{new Date(user.createdAt).toLocaleDateString('zh-CN')}</td>
                    <td className="py-2.5 text-right pr-3">
                      <div className="flex items-center justify-end gap-1">
                        <button onClick={() => {
                          setEditingUser(user)
                          setUserForm({ username: user.username, password: '', name: user.name || '', gender: user.gender || '', role: user.role, email: user.email || '' })
                          setShowUserForm(true)
                        }} className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-blue-600 dark:hover:text-blue-400" title="编辑">
                          <Edit3 className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={() => { setResetPwdUser(user); setResetPwdValue('') }}
                          className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-orange-600 dark:hover:text-orange-400" title="重置密码">
                          <Key className="w-3.5 h-3.5" />
                        </button>
                        <button onClick={() => setUserDeleteConfirm(user)}
                          className="p-1 rounded hover:bg-gray-200 dark:hover:bg-gray-600 text-gray-500 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400" title="删除">
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
          </div>
        )}
      </div>
      ) : (
      <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6">
        <h2 className="font-semibold mb-2 dark:text-white">用户管理</h2>
        <div className="flex items-center gap-3 p-4 bg-gray-50 dark:bg-gray-700 rounded-lg">
          <Shield className="w-10 h-10 text-gray-300 dark:text-gray-500" />
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
          <div className="bg-white dark:bg-gray-800 rounded-xl shadow-xl p-6 w-full max-w-sm mx-4">
            <h3 className="font-semibold mb-1 dark:text-white">重置密码</h3>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">用户: {resetPwdUser.username}</p>
            <input type="password" value={resetPwdValue}
              onChange={(e) => setResetPwdValue(e.target.value)}
              className="w-full px-3 py-2 border dark:border-gray-600 rounded-lg text-sm focus:outline-none focus:ring-2 focus:ring-blue-500 bg-white dark:bg-gray-700 dark:text-gray-200 mb-4"
              placeholder="输入新密码（至少6位）" />
            {resetPasswordMutation.isError && (
              <p className="text-red-500 text-xs mb-2">{(resetPasswordMutation.error as any)?.response?.data?.message || '操作失败'}</p>
            )}
            <div className="flex justify-end gap-2">
              <button onClick={() => setResetPwdUser(null)}
                className="px-4 py-2 text-sm border dark:border-gray-600 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-600 dark:text-gray-200">取消</button>
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
        open={!!userDeleteConfirm}
        title="删除用户"
        message={`确认删除用户 "${userDeleteConfirm?.username}"？此操作不可撤销。`}
        confirmLabel="确认删除"
        onConfirm={() => userDeleteConfirm && deleteUserMutation.mutate(userDeleteConfirm.id)}
        onCancel={() => setUserDeleteConfirm(null)}
      />

      {/* ========== Operation Logs Section (Admin Only) ========== */}
      {isAdmin && (
        <div className="bg-white dark:bg-gray-800 rounded-xl border dark:border-gray-700 shadow-sm p-6 mt-6">
          <div className="flex items-center gap-3 mb-4">
            <ScrollText className="w-5 h-5 text-teal-600" />
            <div>
              <h2 className="font-semibold dark:text-white">{t('settings.operationLogs')}</h2>
              <p className="text-sm text-gray-500 dark:text-gray-400">{t('settings.operationLogsDesc')}</p>
            </div>
          </div>
          <OperationLogViewer />
        </div>
      )}
    </div>
  )
}
