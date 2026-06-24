import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  ChevronDown,
  Eye,
  EyeOff,
  RefreshCw,
  ScanSearch,
  Shield,
  Sparkles,
} from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { fetchCaptcha, fetchPublicKey, login, register, fetchSystemMode } from '../services/api'
import JSEncrypt from 'jsencrypt'

export default function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [isRegister, setIsRegister] = useState(false)

  const [name, setName] = useState('')
  const [gender, setGender] = useState('')
  const [email, setEmail] = useState('')

  const [captchaId, setCaptchaId] = useState('')
  const [captchaImage, setCaptchaImage] = useState('')
  const [captchaAnswer, setCaptchaAnswer] = useState('')
  const [captchaLoading, setCaptchaLoading] = useState(false)
  const [demoMode, setDemoMode] = useState(false)

  const genderOptions = [
    { value: 'MALE', label: t('login.male') },
    { value: 'FEMALE', label: t('login.female') },
    { value: 'OTHER', label: t('login.other') },
  ]

  const loadCaptcha = async () => {
    setCaptchaLoading(true)
    try {
      const res = await fetchCaptcha()
      setCaptchaId(res.data.data.captchaId)
      setCaptchaImage(res.data.data.imageBase64)
      setCaptchaAnswer('')
    } finally {
      setCaptchaLoading(false)
    }
  }

  useEffect(() => {
    loadCaptcha()
    fetchSystemMode().then(res => setDemoMode(res.data.data?.demoMode || false)).catch(() => {})
  }, [isRegister])

  const resetForm = () => {
    setName('')
    setGender('')
    setEmail('')
    setUsername('')
    setPassword('')
    setCaptchaAnswer('')
    setError('')
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!username.trim() || username.trim().length < 3) {
      setError(t('login.errorUsername'))
      return
    }
    if (!password || password.length < 6) {
      setError(t('login.errorPassword'))
      return
    }
    if (!captchaAnswer.trim()) {
      setError(t('login.errorCaptcha'))
      return
    }

    if (isRegister) {
      if (!name.trim()) {
        setError(t('login.errorName'))
        return
      }
      if (!gender) {
        setError(t('login.errorGender'))
        return
      }
      if (!email.trim()) {
        setError(t('login.errorEmail'))
        return
      }
      if (!/^[\w.%+-]+@[\w.-]+\.[A-Za-z]{2,}$/.test(email.trim())) {
        setError(t('login.errorEmailInvalid'))
        return
      }
    }

    setLoading(true)
    try {
      const keyRes = await fetchPublicKey()
      const publicKey = keyRes.data.data?.publicKey
      let encryptedPassword = password
      if (publicKey) {
        const encryptor = new JSEncrypt()
        encryptor.setPublicKey(publicKey)
        const result = encryptor.encrypt(password)
        if (!result) {
          setError(t('login.errorEncrypt'))
          setLoading(false)
          return
        }
        encryptedPassword = result
      }

      if (isRegister) {
        const res = await register({
          username: username.trim(),
          password: encryptedPassword,
          name: name.trim(),
          gender,
          email: email.trim(),
          captchaId,
          captchaAnswer: captchaAnswer.trim(),
        })
        localStorage.setItem('token', res.data.data.token)
        localStorage.setItem('role', res.data.data.role || 'USER')
      } else {
        const res = await login(username.trim(), encryptedPassword, captchaId, captchaAnswer.trim())
        localStorage.setItem('token', res.data.data.token)
        localStorage.setItem('role', res.data.data.role || 'USER')
      }

      navigate('/dashboard')
    } catch (err: any) {
      setError(err?.response?.data?.message || t('login.errorGeneral'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="relative min-h-screen overflow-hidden bg-[#02040a] text-slate-100">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_15%_20%,rgba(56,189,248,0.18),transparent_28%),radial-gradient(circle_at_85%_18%,rgba(99,102,241,0.16),transparent_30%),radial-gradient(circle_at_50%_100%,rgba(14,165,233,0.12),transparent_42%)]" />
      <div className="absolute inset-0 opacity-[0.08]" style={{ backgroundImage: 'linear-gradient(rgba(255,255,255,.25) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.25) 1px, transparent 1px)', backgroundSize: '44px 44px' }} />

      <div className="relative z-10 mx-auto flex min-h-screen w-full max-w-7xl items-center px-4 py-10 sm:px-6 lg:px-8">
        <div className="grid w-full gap-8 lg:grid-cols-[1.12fr_0.88fr]">
          <section className="hidden rounded-[28px] border border-white/10 bg-white/[0.035] p-8 shadow-2xl shadow-black/35 lg:flex lg:flex-col">
            <div className="mb-10 flex items-center gap-3">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-sky-300/30 bg-sky-300/10">
                <Shield className="h-6 w-6 text-sky-300" />
              </div>
              <div>
                <p className="text-xl font-semibold tracking-tight">ServerScout</p>
                <p className="text-sm text-slate-400">{t('login.subtitle')}</p>
              </div>
            </div>

            <div className="max-w-xl">
              <p className="mb-4 inline-flex items-center gap-2 rounded-full border border-sky-300/30 bg-sky-300/10 px-3 py-1 text-xs text-sky-200">
                <Sparkles className="h-3.5 w-3.5" />
                {t('login.workspaceBadge')}
              </p>
              <h1 className="text-5xl font-semibold leading-tight text-white">
                {isRegister ? t('login.createAccount') : t('login.welcomeBack')}
              </h1>
              <p className="mt-5 text-base leading-8 text-slate-300">
                {t('login.heroDesc')}
              </p>
            </div>

            <div className="mt-10 grid gap-4 md:grid-cols-3">
              <div className="rounded-2xl border border-white/10 bg-[#07101b] p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">{t('login.metricCoverage')}</p>
                <p className="mt-3 text-2xl font-semibold text-white">126</p>
                <p className="mt-1 text-sm text-slate-400">{t('login.metricManagedAssets')}</p>
              </div>
              <div className="rounded-2xl border border-white/10 bg-[#07101b] p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">{t('login.metricPipeline')}</p>
                <p className="mt-3 text-2xl font-semibold text-cyan-300">04</p>
                <p className="mt-1 text-sm text-slate-400">{t('login.metricActiveScans')}</p>
              </div>
              <div className="rounded-2xl border border-white/10 bg-[#07101b] p-4">
                <p className="text-xs uppercase tracking-[0.18em] text-slate-500">{t('login.metricRisk')}</p>
                <p className="mt-3 text-2xl font-semibold text-rose-300">18</p>
                <p className="mt-1 text-sm text-slate-400">{t('login.metricCriticalFindings')}</p>
              </div>
            </div>

            <div className="mt-8 grid gap-4 xl:grid-cols-2">
              <div className="rounded-2xl border border-sky-300/15 bg-gradient-to-br from-sky-500/10 to-transparent p-5">
                <div className="mb-3 flex items-center gap-2 text-sky-200">
                  <ScanSearch className="h-4 w-4" />
                  <span className="text-sm font-medium">{t('login.reconPipelineTitle')}</span>
                </div>
                <p className="text-sm leading-7 text-slate-300">
                  {t('login.reconPipelineDesc')}
                </p>
              </div>
              <div className="rounded-2xl border border-emerald-300/15 bg-gradient-to-br from-emerald-500/10 to-transparent p-5">
                <div className="mb-3 flex items-center gap-2 text-emerald-200">
                  <Sparkles className="h-4 w-4" />
                  <span className="text-sm font-medium">{t('login.evidenceReadyTitle')}</span>
                </div>
                <p className="text-sm leading-7 text-slate-300">
                  {t('login.evidenceReadyDesc')}
                </p>
              </div>
            </div>

          </section>

          <section className="flex items-center justify-center">
            <div className="w-full max-w-[520px] rounded-[30px] border border-white/10 bg-[#060913]/92 p-6 shadow-2xl shadow-black/45 backdrop-blur-xl sm:p-8">
              <div className="mb-6 flex items-center justify-between">
                <div className="flex items-center gap-3">
                  <div className="flex h-12 w-12 items-center justify-center rounded-2xl border border-sky-300/30 bg-sky-300/10">
                    <Shield className="h-6 w-6 text-sky-300" />
                  </div>
                  <div>
                    <p className="text-lg font-semibold text-white">ServerScout</p>
                    <p className="text-sm text-slate-400">{t('login.subtitle')}</p>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={() => navigate('/')}
                  className="hidden rounded-full border border-white/10 px-3 py-1.5 text-xs text-slate-300 transition hover:bg-white/10 sm:inline-flex"
                >
                  {t('login.backHome')}
                </button>
              </div>

              <div className="mb-6">
                <p className="text-sm uppercase tracking-[0.18em] text-sky-300/80">
                  {isRegister ? t('login.registrationPortal') : t('login.secureAccess')}
                </p>
                <h2 className="mt-2 text-3xl font-semibold text-white">
                  {isRegister ? t('login.createAccount') : t('login.welcomeBack')}
                </h2>
                <p className="mt-2 text-sm text-slate-400">
                  {isRegister
                    ? t('login.registerDesc')
                    : t('login.loginDesc')}
                </p>
              </div>

              <form onSubmit={handleSubmit} className="space-y-4">

                {demoMode && (
                  <div className="rounded-2xl border border-emerald-400/25 bg-emerald-400/10 px-4 py-3 text-sm">
                    <p className="mb-1 font-medium text-emerald-200">DEMO 模式</p>
                    <p className="text-emerald-300/80">
                      用户名: <span className="font-mono text-emerald-200">admin</span>
                      &nbsp;&nbsp;密码: <span className="font-mono text-emerald-200">Admin@123456</span>
                      &nbsp;&nbsp;验证码: 任意输入
                    </p>
                  </div>
                )}
                {isRegister && (
                  <div className="grid gap-4 sm:grid-cols-2">
                    <label className="block">
                      <span className="mb-1.5 block text-sm text-slate-300">{t('login.name')}</span>
                      <input
                        value={name}
                        onChange={(e) => setName(e.target.value)}
                        placeholder={t('login.namePlaceholder')}
                        className="w-full rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-sky-300/40 focus:bg-white/[0.06]"
                      />
                    </label>

                    <label className="block">
                      <span className="mb-1.5 block text-sm text-slate-300">{t('login.gender')}</span>
                      <div className="relative">
                        <select
                          value={gender}
                          onChange={(e) => setGender(e.target.value)}
                          className="w-full appearance-none rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-white outline-none transition focus:border-sky-300/40 focus:bg-white/[0.06]"
                        >
                          <option value="" disabled className="text-slate-700">{t('login.genderPlaceholder')}</option>
                          {genderOptions.map((option) => (
                            <option key={option.value} value={option.value} className="text-slate-900">
                              {option.label}
                            </option>
                          ))}
                        </select>
                        <ChevronDown className="pointer-events-none absolute right-4 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                      </div>
                    </label>
                  </div>
                )}

                {isRegister && (
                  <label className="block">
                    <span className="mb-1.5 block text-sm text-slate-300">{t('login.email')}</span>
                    <input
                      type="email"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      placeholder={t('login.emailPlaceholder')}
                      className="w-full rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-sky-300/40 focus:bg-white/[0.06]"
                    />
                  </label>
                )}

                <label className="block">
                  <span className="mb-1.5 block text-sm text-slate-300">{t('login.username')}</span>
                  <input
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    placeholder={isRegister ? t('login.usernamePlaceholderReg') : t('login.usernamePlaceholder')}
                    className="w-full rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-sky-300/40 focus:bg-white/[0.06]"
                  />
                </label>

                <label className="block">
                  <span className="mb-1.5 block text-sm text-slate-300">{t('login.password')}</span>
                  <div className="relative">
                    <input
                      type={showPwd ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      placeholder={isRegister ? t('login.passwordPlaceholderReg') : t('login.passwordPlaceholder')}
                      className="w-full rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 pr-12 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-sky-300/40 focus:bg-white/[0.06]"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPwd((value) => !value)}
                      className="absolute right-4 top-1/2 -translate-y-1/2 text-slate-400 transition hover:text-slate-200"
                      tabIndex={-1}
                    >
                      {showPwd ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                    </button>
                  </div>
                </label>

                <div>
                  <span className="mb-1.5 block text-sm text-slate-300">{t('login.captcha')}</span>
                  <div className="flex gap-3">
                    <input
                      type="text"
                      value={captchaAnswer}
                      onChange={(e) => setCaptchaAnswer(e.target.value)}
                      placeholder={t('login.captchaPlaceholder')}
                      className="min-w-0 flex-1 rounded-2xl border border-white/10 bg-white/[0.04] px-4 py-3 text-sm text-white outline-none transition placeholder:text-slate-500 focus:border-sky-300/40 focus:bg-white/[0.06]"
                    />
                    <button
                      type="button"
                      onClick={loadCaptcha}
                      disabled={captchaLoading}
                      className="flex h-[50px] w-[150px] flex-shrink-0 items-center justify-center overflow-hidden rounded-2xl border border-white/10 bg-white/[0.04] transition hover:border-sky-300/35 hover:bg-white/[0.06] disabled:opacity-70"
                    >
                      {captchaLoading ? (
                        <RefreshCw className="h-4 w-4 animate-spin text-slate-300" />
                      ) : captchaImage ? (
                        <img src={captchaImage} alt="CAPTCHA" className="h-full w-full object-cover" />
                      ) : (
                        <RefreshCw className="h-4 w-4 text-slate-300" />
                      )}
                    </button>
                  </div>
                </div>

                {error && (
                  <div className="rounded-2xl border border-rose-400/25 bg-rose-400/10 px-4 py-3 text-sm text-rose-200">
                    {error}
                  </div>
                )}

                <button
                  type="submit"
                  disabled={loading || !captchaId}
                  className="inline-flex w-full items-center justify-center gap-2 rounded-2xl border border-sky-300/30 bg-sky-500/25 px-4 py-3.5 text-sm font-semibold text-sky-100 transition hover:bg-sky-500/35 disabled:cursor-not-allowed disabled:opacity-60"
                >
                  {loading ? t('login.processing') : (isRegister ? t('login.registerBtn') : t('login.loginBtn'))}
                  {!loading && <ArrowRight className="h-4 w-4" />}
                </button>
              </form>

              <div className="mt-6 flex items-center justify-between gap-4 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 text-sm text-slate-300">
                <span>{isRegister ? t('login.haveAccount') : t('login.noAccount')}</span>
                <button
                  type="button"
                  onClick={() => {
                    setIsRegister((value) => !value)
                    resetForm()
                  }}
                  className="font-medium text-sky-300 transition hover:text-sky-200"
                >
                  {isRegister ? t('login.goToLogin') : t('login.goToRegister')}
                </button>
              </div>

              <p className="mt-6 text-center text-xs text-slate-500">{t('login.footerVersion', { year: new Date().getFullYear() })}</p>
            </div>
          </section>
        </div>
      </div>
    </div>
  )
}
