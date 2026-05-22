import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { Shield, Eye, EyeOff, RefreshCw } from 'lucide-react'
import { login, register, fetchCaptcha } from '../services/api'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [isRegister, setIsRegister] = useState(false)

  // Captcha
  const [captchaId, setCaptchaId] = useState('')
  const [captchaQuestion, setCaptchaQuestion] = useState('')
  const [captchaAnswer, setCaptchaAnswer] = useState('')
  const [captchaLoading, setCaptchaLoading] = useState(false)

  const navigate = useNavigate()

  const loadCaptcha = async () => {
    setCaptchaLoading(true)
    try {
      const res = await fetchCaptcha()
      setCaptchaId(res.data.data.captchaId)
      setCaptchaQuestion(res.data.data.question)
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

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!captchaAnswer.trim()) {
      setError('请输入验证码')
      return
    }
    setLoading(true)
    try {
      const fn = isRegister ? register : login
      const res = await fn(username, password, captchaId, captchaAnswer)
      localStorage.setItem('token', res.data.data.token)
      localStorage.setItem('role', res.data.data.role || 'USER')
      navigate('/dashboard')
    } catch (err: any) {
      const msg = err?.response?.data?.message || '操作失败'
      setError(msg)
      loadCaptcha()
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-blue-50 to-indigo-100">
      <div className="bg-white p-8 rounded-2xl shadow-lg w-96">
        <div className="flex items-center justify-center gap-2 mb-8">
          <Shield className="w-8 h-8 text-blue-600" />
          <span className="text-2xl font-bold">ServerScout</span>
        </div>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">用户名</label>
            <input
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
              placeholder={isRegister ? '设置登录用户名（至少3位）' : '请输入用户名'}
              value={username}
              onChange={e => setUsername(e.target.value)}
            />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">密码</label>
            <div className="relative">
              <input
                type={showPwd ? 'text' : 'password'}
                className="w-full px-3 py-2 pr-10 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder={isRegister ? '设置密码（至少6位）' : '请输入密码'}
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

          {/* Captcha */}
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">验证码</label>
            <div className="flex gap-2 items-center">
              <input
                type="text"
                className="flex-1 px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 outline-none"
                placeholder="请输入计算结果"
                value={captchaAnswer}
                onChange={e => setCaptchaAnswer(e.target.value)}
              />
              <button
                type="button"
                onClick={loadCaptcha}
                disabled={captchaLoading}
                className="flex items-center gap-1 px-3 py-2 bg-gray-100 rounded-lg text-sm font-mono font-bold text-gray-700 hover:bg-gray-200 whitespace-nowrap min-w-[100px]"
              >
                {captchaLoading ? (
                  <RefreshCw className="w-4 h-4 animate-spin" />
                ) : (
                  <>
                    <span>{captchaQuestion || '加载中...'}</span>
                    <RefreshCw className="w-3.5 h-3.5 text-gray-400" />
                  </>
                )}
              </button>
            </div>
          </div>

          {error && <p className="text-red-500 text-sm">{error}</p>}
          <button type="submit" disabled={loading || !captchaId}
            className="w-full py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 font-medium">
            {loading ? '处理中...' : isRegister ? '注册' : '登录'}
          </button>
        </form>

        <p className="text-center mt-4 text-sm">
          {isRegister ? (
            <>
              已有账号？
              <button
                onClick={() => { setIsRegister(false); setError('') }}
                className="text-blue-600 hover:underline ml-1"
              >
                去登录
              </button>
            </>
          ) : (
            <>
              还没有账号？
              <button
                onClick={() => { setIsRegister(true); setError('') }}
                className="text-blue-600 hover:underline ml-1"
              >
                立即注册
              </button>
            </>
          )}
        </p>
      </div>
    </div>
  )
}
