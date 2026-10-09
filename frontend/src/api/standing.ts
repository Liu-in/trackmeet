import { download, get, post, type PageResult } from './request'
import type { SheetBundle } from './print'

// ==================== 类型 ====================
export interface PointsRow {
  id?: number
  meetId?: number
  eventId?: number
  eventName?: string
  roundId?: number
  registrationId?: number
  athleteId?: number
  athleteName?: string
  bibNo?: string
  unitId?: number
  unitName?: string
  groupId?: number
  groupName?: string
  gender?: string
  rankNo?: number
  rankPoint?: number
  bonusPoint?: number
  relayFactor?: number
  totalPoint?: number
  scoreRuleId?: number
  appliedBonusCodes?: string
  calcTime?: string
  isManual?: number
}

export interface TeamScore {
  id?: number
  meetId?: number
  unitId?: number
  unitName?: string
  groupId?: number
  groupName?: string
  /** MALE / FEMALE / TOTAL */
  scoreType?: string
  rankPointSum?: number
  bonusPointSum?: number
  totalPoint?: number
  rankNo?: number
  published?: number
  calcTime?: string
}

export interface MedalRow {
  id?: number
  meetId?: number
  unitId?: number
  unitName?: string
  groupId?: number
  groupName?: string
  gold?: number
  silver?: number
  bronze?: number
  totalMedal?: number
  sortScore?: number
  rankNo?: number
  calcTime?: string
}

export interface Certificate {
  id?: number
  meetId?: number
  certNo?: string
  /** RANK / RESULT / MEDAL / PARTICIPATION */
  certType?: string
  templateId?: number
  athleteId?: number
  athleteName?: string
  unitId?: number
  unitName?: string
  eventId?: number
  eventName?: string
  rankNo?: number
  resultText?: string
  issueDate?: string
  fileId?: number
  qrContent?: string
  status?: string
  printCount?: number
}

export interface StandingOverview {
  meetId?: number
  pointRows?: number
  teamRows?: number
  medalRows?: number
  eventCount?: number
  athleteCount?: number
  calcTime?: string
  messages?: string[]
}

export interface StandingQuery {
  page?: number
  size?: number
  meetId?: number
  groupId?: number
  eventId?: number
  unitId?: number
  scoreType?: string
  keyword?: string
}

// ==================== 榜单接口 ====================
export function getTeamScores(params: { meetId?: number; groupId?: number; scoreType?: string }) {
  return get<TeamScore[]>('/standings/team', params)
}

export function getMedals(params: { meetId?: number; groupId?: number }) {
  return get<MedalRow[]>('/standings/medals', params)
}

export function pagePoints(params: StandingQuery) {
  return get<PageResult<PointsRow>>('/standings/points', params)
}

export function recalculateStandings(data: { meetId?: number; groupId?: number; eventId?: number }) {
  return post<StandingOverview>('/standings/recalculate', data)
}

export function saveStandingSnapshot(data: { meetId?: number; scoreType?: string }) {
  return post<StandingOverview>('/standings/snapshot', data)
}

// ==================== 证书接口 ====================
export function generateCertificates(data: {
  meetId?: number
  certType: string
  eventId?: number
  groupId?: number
  unitId?: number
  templateId?: number
}) {
  return post<number>('/certificates/generate', data)
}

export function pageCertificates(params: { page?: number; size?: number; meetId?: number; certType?: string; unitId?: number; keyword?: string }) {
  return get<PageResult<Certificate>>('/certificates', params)
}

/** 下载单张证书（后端要求 meetId 参数） */
export function downloadCertificate(id: number, meetId?: number, filename = '证书.xlsx') {
  return download(`/certificates/${id}/download`, { meetId }, filename)
}

/** 打印计数（后端 CertificatePrintDTO.meetId 为必填） */
export function printCertificates(ids: number[], meetId?: number) {
  return post<number>('/certificates/print', { ids, meetId })
}

// ==================== 打印/导出（A4 与 Excel 共用 SheetBundle） ====================
export function getResultSheet(params: { meetId?: number; eventId?: number; groupId?: number; unitId?: number; publishOnly?: boolean }) {
  return get<SheetBundle[]>('/print/results', params)
}

export function getTeamScoreSheet(params: { meetId?: number; groupId?: number; scoreType?: string }) {
  return get<SheetBundle[]>('/print/standings/team', params)
}

export function getMedalSheet(params: { meetId?: number; groupId?: number }) {
  return get<SheetBundle[]>('/print/standings/medals', params)
}

export function getPointsSheet(params: { meetId?: number; groupId?: number; unitId?: number }) {
  return get<SheetBundle[]>('/print/points', params)
}

export function downloadResultBook(params: { meetId?: number; eventId?: number; groupId?: number }, filename = '成绩册.xlsx') {
  return download('/print/results', { ...params, format: 'xlsx' }, filename)
}

export function downloadMedalBoard(params: { meetId?: number; groupId?: number }, filename = '奖牌榜.xlsx') {
  return download('/print/standings/medals', { ...params, format: 'xlsx' }, filename)
}
