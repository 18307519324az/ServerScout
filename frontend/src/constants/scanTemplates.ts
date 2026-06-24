/**
 * Scan template presets — single source of truth for all scan configurations.
 *
 * Usage: import { SCAN_TEMPLATES, applyScanTemplate } from '../constants/scanTemplates'
 *        applyScanTemplate('FULL', formElement)
 */
export const SCAN_TEMPLATES = {
  QUICK: {
    scanType: 'QUICK',
    label: 'Quick',
    description: '快速扫描',
    portRange: '1-1000',
    enableFingerprint: true,
    enableVulnScan: false,
    enableCrawler: false,
  },
  STEALTH: {
    scanType: 'STEALTH',
    label: 'Stealth',
    description: '隐匿扫描',
    portRange: '22,80,443,3389',
    enableFingerprint: true,
    enableVulnScan: false,
    enableCrawler: false,
  },
  WEB: {
    scanType: 'WEB',
    label: 'Web',
    description: 'Web 专用',
    portRange: '80,443,8080,8443,8000,3000,5000',
    enableFingerprint: true,
    enableVulnScan: false,
    enableCrawler: true,
  },
  FULL: {
    scanType: 'FULL',
    label: 'Full',
    description: '全端口 + 漏洞',
    portRange: '1-65535',
    enableFingerprint: true,
    enableVulnScan: true,
    enableCrawler: true,
  },
} as const

export type ScanTemplateId = keyof typeof SCAN_TEMPLATES

/** Preset card metadata (display + colour) */
export const PRESET_CARDS: { id: string; label: string; desc: string; color: string }[] = [
  { id: 'quick',  label: 'Quick',   desc: '快速扫描',      color: 'border-blue-300 dark:border-blue-700 bg-blue-50 dark:bg-blue-900/20 text-blue-700 dark:text-blue-300' },
  { id: 'stealth',label: 'Stealth', desc: '隐匿扫描',      color: 'border-purple-300 dark:border-purple-700 bg-purple-50 dark:bg-purple-900/20 text-purple-700 dark:text-purple-300' },
  { id: 'web',    label: 'Web',     desc: 'Web 专用',       color: 'border-green-300 dark:border-green-700 bg-green-50 dark:bg-green-900/20 text-green-700 dark:text-green-300' },
  { id: 'full',   label: 'Full',    desc: '全端口 + 漏洞',  color: 'border-red-300 dark:border-red-700 bg-red-50 dark:bg-red-900/20 text-red-700 dark:text-red-300' },
]

/** Apply a scan template to the form controls */
export function applyScanTemplate(templateId: string, form: HTMLFormElement): void {
  const key = templateId.toUpperCase() as ScanTemplateId
  const tpl = SCAN_TEMPLATES[key]
  if (!tpl) return

  const scanType = form.querySelector<HTMLSelectElement>('select[name="scanType"]')
  const portRange = form.querySelector<HTMLInputElement>('input[name="portRange"]')
  const fingerCheck = form.querySelector<HTMLInputElement>('input[name="enableFingerprint"]')
  const vulnCheck = form.querySelector<HTMLInputElement>('input[name="enableVulnScan"]')
  const crawlerCheck = form.querySelector<HTMLInputElement>('input[name="enableCrawler"]')

  if (scanType) scanType.value = tpl.scanType
  if (portRange) portRange.value = tpl.portRange
  if (fingerCheck) fingerCheck.checked = tpl.enableFingerprint
  if (vulnCheck) vulnCheck.checked = tpl.enableVulnScan
  if (crawlerCheck) crawlerCheck.checked = tpl.enableCrawler
}
