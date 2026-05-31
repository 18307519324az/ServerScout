import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import zh from './locales/zh.json'
import en from './locales/en.json'

function detectBrowserLang() {
  if (typeof navigator === 'undefined') return 'zh'
  const langs = [...(navigator.languages || []), navigator.language]
    .filter(Boolean)
    .map((value) => value.toLowerCase())
  return langs.some((value) => value.startsWith('zh')) ? 'zh' : 'en'
}

const saved = localStorage.getItem('lang')
const initialLang = saved || detectBrowserLang()

i18n.use(initReactI18next).init({
  resources: { zh: { translation: zh }, en: { translation: en } },
  lng: initialLang,
  fallbackLng: 'zh',
  interpolation: { escapeValue: false },
})

export default i18n
