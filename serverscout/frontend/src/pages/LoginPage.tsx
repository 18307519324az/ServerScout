import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Shield, Eye, EyeOff, RefreshCw, ChevronDown, AlertTriangle, X } from 'lucide-react'
import { useTranslation } from 'react-i18next'
import { login, register, fetchCaptcha, fetchPublicKey } from '../services/api'
import JSEncrypt from 'jsencrypt'

export default function LoginPage() {
  const { t } = useTranslation()
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

  const [showDisclaimer, setShowDisclaimer] = useState(false)

  const navigate = useNavigate()

  const loadCaptcha = async () => {
    setCaptchaLoading(true)
    try {
      const res = await fetchCaptcha()
      setCaptchaId(res.data.data.captchaId)
      setCaptchaImage(res.data.data.imageBase64)
      setCaptchaAnswer('')
    } catch {
      // ignore
    } finally {
      setCaptchaLoading(false)
    }
  }

  useEffect(() => {
    loadCaptcha()
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
      const msg = err?.response?.data?.message || t('login.errorGeneral')
      setError(msg)
      loadCaptcha()
    } finally {
      setLoading(false)
    }
  }

  const GENDER_OPTIONS = [
    { value: 'MALE', label: t('login.male') },
    { value: 'FEMALE', label: t('login.female') },
    { value: 'OTHER', label: t('login.other') },
  ]

  return (
    <div className="min-h-screen flex flex-col items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100 px-4 py-8">
      {/* Disclaimer Banner */}
      <div className="w-full max-w-[440px] mb-6">
        <div className="bg-amber-50 border border-amber-300 rounded-xl px-4 py-3 text-sm text-amber-900">
          <div className="flex items-start gap-2">
            <AlertTriangle className="w-5 h-5 flex-shrink-0 mt-0.5 text-amber-600" />
            <div className="flex-1 min-w-0">
              <p className="font-semibold">{t('login.disclaimerTitle')}</p>
              <p className="mt-1 text-amber-800">{t('login.disclaimerSummary')}</p>
              <button
                onClick={() => setShowDisclaimer(!showDisclaimer)}
                className="mt-2 text-amber-700 underline hover:text-amber-900 font-medium"
              >
                {showDisclaimer ? t('login.disclaimerHide') : t('login.disclaimerRead')}
              </button>
            </div>
          </div>
        </div>
      </div>

      {/* Disclaimer Modal */}
      {showDisclaimer && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/50">
          <div className="bg-white rounded-2xl shadow-2xl w-full max-w-2xl max-h-[85vh] flex flex-col">
            <div className="flex items-center justify-between px-6 py-4 border-b">
              <h2 className="text-lg font-bold text-gray-900">{t('login.disclaimerTitle')}</h2>
              <button
                onClick={() => setShowDisclaimer(false)}
                className="p-1 rounded-lg hover:bg-gray-100 text-gray-400 hover:text-gray-600"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            <div className="overflow-y-auto px-6 py-4 text-sm text-gray-700 leading-relaxed space-y-3">
              <p><strong>1.</strong> {t('login.disclaimerItem1')}</p>
              <p><strong>2.</strong> {t('login.disclaimerItem2')}</p>
              <p><strong>3.</strong> {t('login.disclaimerItem3')}</p>
              <p><strong>4.</strong> {t('login.disclaimerItem4')}</p>
              <p><strong>5.</strong> {t('login.disclaimerItem5')}</p>
              <p><strong>6.</strong> {t('login.disclaimerItem6')}</p>
              <p><strong>7.</strong> {t('login.disclaimerItem7')}</p>
              <p><strong>8.</strong> {t('login.disclaimerItem8')}</p>
              <p className="text-gray-400 text-xs mt-4 italic">{t('login.disclaimerFinal')}</p>
            </div>
            <div className="px-6 py-4 border-t bg-gray-50 rounded-b-2xl">
              <button
                onClick={() => setShowDisclaimer(false)}
                className="w-full py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 font-medium transition"
              >
                {t('login.disclaimerAgree')}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Login/Register Card */}
      <div className={`bg-white p-6 sm:p-8 rounded-2xl shadow-lg w-full ${isRegister ? 'max-w-[440px]' : 'max-w-96'} transition-all`}>
        <div className="flex items-center justify-center gap-2 mb-6">
          <Shield className="w-8 h-8 text-blue-600" />
          <span className="text-2xl font-bold">{t('login.title')}</span>
        </div>

        <h2 className="text-lg font-semibold text-center mb-6">
          {isRegister ? t('login.createAccount') : t('login.welcomeBack')}
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          {isRegister && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.name')}</label>
              <input
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder={t('login.namePlaceholder')}
                value={name}
                onChange={e => setName(e.target.value)}
              />
            </div>
          )}

          {isRegister && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.gender')}</label>
              <div className="relative">
                <select
                  value={gender}
                  onChange={e => setGender(e.target.value)}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none appearance-none bg-white text-gray-700"
                >
                  <option value="" disabled>{t('login.genderPlaceholder')}</option>
                  {GENDER_OPTIONS.map(opt => (
                    <option key={opt.value} value={opt.value}>{opt.label}</option>
                  ))}
                </select>
                <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400 pointer-events-none" />
              </div>
            </div>
          )}

          {isRegister && (
            <div>
              <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.email')}</label>
              <input
                type="email"
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder="your@email.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
              />
            </div>
          )}

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.username')}</label>
            <input
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
              placeholder={isRegister ? t('login.usernamePlaceholderReg') : t('login.usernamePlaceholder')}
              value={username}
              onChange={e => setUsername(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.password')}</label>
            <div className="relative">
              <input
                type={showPwd ? 'text' : 'password'}
                className="w-full px-3 py-2 pr-10 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder={isRegister ? t('login.passwordPlaceholderReg') : t('login.passwordPlaceholder')}
                value={password}
                onChange={e => setPassword(e.target.value)}
              />
              <button
                type="button"
                onClick={() => setShowPwd(!showPwd)}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-gray-400 hover:text-gray-600"
                tabIndex={-1}
              >
                {showPwd ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
              </button>
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">{t('login.captcha')}</label>
            <div className="flex gap-2 items-center">
              <input
                type="text"
                className="flex-1 px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder={t('login.captchaPlaceholder')}
                value={captchaAnswer}
                onChange={e => setCaptchaAnswer(e.target.value)}
              />
              <button
                type="button"
                onClick={loadCaptcha}
                disabled={captchaLoading}
                className="flex-shrink-0 overflow-hidden rounded-lg border border-gray-200 hover:border-blue-400 transition-colors h-[42px]"
              >
                {captchaLoading ? (
                  <RefreshCw className="w-4 h-4 animate-spin m-3" />
                ) : captchaImage ? (
                  <img src={captchaImage} alt="CAPTCHA" className="h-full cursor-pointer" onClick={loadCaptcha} />
                ) : (
                  <RefreshCw className="w-4 h-4 animate-spin m-3" />
                )}
              </button>
            </div>
          </div>

          {error && (
            <p className="text-red-500 text-sm bg-red-50 px-3 py-2 rounded-lg">{error}</p>
          )}

          <button
            type="submit"
            disabled={loading || !captchaId}
            className="w-full py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium transition"
          >
            {loading ? t('login.processing') : isRegister ? t('login.registerBtn') : t('login.loginBtn')}
          </button>
        </form>

        <p className="text-center mt-4 text-sm">
          {isRegister ? (
            <>
              {t('login.haveAccount')}
              <button
                onClick={() => { setIsRegister(false); resetForm() }}
                className="text-blue-600 hover:underline ml-1"
              >
                {t('login.goToLogin')}
              </button>
            </>
          ) : (
            <>
              {t('login.noAccount')}
              <button
                onClick={() => { setIsRegister(true); resetForm() }}
                className="text-blue-600 hover:underline ml-1"
              >
                {t('login.goToRegister')}
              </button>
            </>
          )}
        </p>
      </div>

      {/* Footer copyright */}
      <p className="mt-6 text-xs text-gray-400">ServerScout v1.0 &copy; {new Date().getFullYear()}</p>
    </div>
  )
}
