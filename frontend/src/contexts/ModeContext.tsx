import { createContext, useContext, useState, useEffect, type ReactNode } from 'react'
import { fetchSystemMode } from '../services/api'
import type { SystemMode } from '../types'

interface ModeContextValue {
  mode: SystemMode | null
  loading: boolean
  error: boolean
  refresh: () => void
  isDemo: boolean
  isReal: boolean
}

const defaultMode: ModeContextValue = {
  mode: null,
  loading: true,
  error: false,
  refresh: () => {},
  isDemo: false,
  isReal: false,
}

const ModeContext = createContext<ModeContextValue>(defaultMode)

export const useMode = () => useContext(ModeContext)

export const ModeProvider = ({ children }: { children: ReactNode }) => {
  const [mode, setMode] = useState<SystemMode | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(false)

  const loadMode = () => {
    setLoading(true)
    setError(false)
    fetchSystemMode()
      .then((res) => {
        setMode(res.data.data)
      })
      .catch(() => {
        setError(true)
        setMode(null)
      })
      .finally(() => setLoading(false))
  }

  useEffect(() => {
    loadMode()
  }, [])

  const isDemo = mode?.mode === 'DEMO'
  const isReal = mode?.mode === 'REAL'

  return (
    <ModeContext.Provider value={{ mode, loading, error, refresh: loadMode, isDemo, isReal }}>
      {children}
    </ModeContext.Provider>
  )
}
