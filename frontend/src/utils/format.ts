/**
 * 时间与格式化工具。
 */

export function formatDate(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.length >= 10 ? value.substring(0, 10) : value
}

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '-'
  }
  return value.replace('T', ' ').substring(0, 19)
}

/** 径赛成绩秒 ↔ 显示文本（11.23 → 11"23，75.4 → 1:15.40） */
export function formatTrackTime(seconds?: number | null): string {
  if (seconds === null || seconds === undefined) {
    return '-'
  }
  if (seconds >= 60) {
    const minutes = Math.floor(seconds / 60)
    const rest = (seconds - minutes * 60).toFixed(2).padStart(5, '0')
    return `${minutes}:${rest}`
  }
  const text = seconds.toFixed(2)
  return text.replace('.', '"')
}

/** 名次显示（并列名次） */
export function formatRank(rankNo?: number | null, parallel = false): string {
  if (!rankNo) {
    return '-'
  }
  return parallel ? `${rankNo}（并列）` : String(rankNo)
}

/** 数字安全格式化 */
export function toFixedNumber(value?: number | string | null, digits = 2): string {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  const num = Number(value)
  return Number.isNaN(num) ? '-' : num.toFixed(digits)
}

/** 是否为空值（空串/null/undefined 统一判断） */
export function isBlank(value?: unknown): boolean {
  return value === null || value === undefined || String(value).trim() === ''
}
