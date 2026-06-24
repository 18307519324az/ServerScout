export function normalizeScanType(scanType?: string): string {
  return (scanType || '').trim().toUpperCase()
}

export function getScanTypeLabel(scanType?: string): string {
  switch (normalizeScanType(scanType)) {
    case 'QUICK':
      return '快速扫描'
    case 'STEALTH':
      return '隐匿扫描'
    case 'WEB':
      return 'Web 专用'
    case 'FULL':
      return '全端口 + 漏洞'
    case 'CUSTOM':
      return '自定义扫描'
    case 'NUCLEI':
      return 'Nuclei 漏洞检测'
    default:
      return scanType || '-'
  }
}

export function getScanTypeSelectLabel(scanType?: string): string {
  switch (normalizeScanType(scanType)) {
    case 'QUICK':
      return 'QUICK（快速扫描）'
    case 'STEALTH':
      return 'STEALTH（隐匿扫描）'
    case 'WEB':
      return 'WEB（Web 专用）'
    case 'FULL':
      return 'FULL（全端口 + 漏洞）'
    default:
      return scanType || '-'
  }
}

/** Validate a port range string on the frontend */
export function validatePortRange(range: string): string | null {
  if (!range || !range.trim()) {
    return '端口范围不能为空'
  }
  const parts = range.split(',')
  for (const raw of parts) {
    const token = raw.trim()
    if (!token) {
      return '端口范围格式不正确'
    }
    // Must match single port or range: digits or digits-digits
    if (!/^\d{1,5}(?:-\d{1,5})?$/.test(token)) {
      return `端口范围不合法: ${token}`
    }
    const bounds = token.split('-').map(Number)
    const start = bounds[0]
    const end = bounds.length === 2 ? bounds[1] : start
    if (start < 1 || end > 65535 || start > end) {
      return `端口范围不合法，端口必须在 1-65535 之间: ${token}`
    }
  }
  return null
}
