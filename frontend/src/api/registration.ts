import { del, get, post, put, type PageResult } from './request'

export interface RelayMember {
  athleteId: number
  legNo?: number
  athleteName?: string
}

export interface Registration {
  id?: number
  meetId?: number
  eventId?: number
  unitId?: number
  athleteId?: number
  groupId?: number
  isRelay?: number
  relayName?: string
  relayTeamSeq?: number
  seedResult?: string
  seedValue?: number
  seedUnit?: string
  seedSource?: string
  remark?: string
  members?: RelayMember[]
}

export interface RegistrationVO extends Registration {
  eventName?: string
  athleteName?: string
  bibNo?: string
  unitName?: string
  groupName?: string
  status?: string
  statusName?: string
  rejectReason?: string
  submitTime?: string
  auditTime?: string
  checkResultJson?: string
  memberNames?: string
}

export interface RegistrationQuery {
  page?: number
  size?: number
  keyword?: string
  meetId?: number
  eventId?: number
  unitId?: number
  groupId?: number
  athleteId?: number
  status?: string
  isRelay?: number
}

export interface RegistrationCheckItem {
  rowIndex?: number
  athleteId?: number
  athleteName?: string
  eventId?: number
  eventName?: string
  level: 'ERROR' | 'WARN'
  code?: string
  message: string
}

export interface RegistrationCheckResult {
  passCount: number
  failCount: number
  items: RegistrationCheckItem[]
}

export interface RegistrationStat {
  dimension: string
  refId?: number
  name: string
  total: number
  pending: number
  approved: number
  rejected: number
}

export interface RegistrationWarning {
  eventId: number
  eventName: string
  total: number
  approved: number
  message: string
}

// ---------------- 报名 CRUD ----------------
export function pageRegistrations(params: RegistrationQuery) {
  return get<PageResult<RegistrationVO>>('/registrations', params)
}

export function getRegistration(id: number) {
  return get<RegistrationVO>(`/registrations/${id}`)
}

export function createRegistration(data: Registration) {
  return post<number>('/registrations', data)
}

export function batchCreateRegistrations(data: {
  meetId?: number
  unitId: number
  eventId: number
  athleteIds: number[]
  isRelay?: number
  relayName?: string
  remark?: string
}) {
  return post<number[]>('/registrations/batch', data)
}

export function updateRegistration(id: number, data: Registration) {
  return put<void>(`/registrations/${id}`, data)
}

export function deleteRegistration(id: number) {
  return del<void>(`/registrations/${id}`)
}

// ---------------- 校验 / 审核 / 统计 ----------------
export function checkRegistrations(data: { meetId?: number; items: Registration[] }) {
  return post<RegistrationCheckResult>('/registrations/check', data)
}

export function getCheckResult(params: { meetId?: number; eventId?: number; unitId?: number }) {
  return get<RegistrationCheckResult>('/registrations/check-result', params)
}

export function approveRegistrations(ids: number[]) {
  return post<void>('/registrations/approve', { ids })
}

export function rejectRegistration(id: number, rejectReason: string) {
  return post<void>(`/registrations/${id}/reject`, { rejectReason })
}

export function getRegistrationStatistics(params: { meetId?: number; dimension?: 'UNIT' | 'EVENT' | 'GROUP' }) {
  return get<RegistrationStat[]>('/registrations/statistics', params)
}

export function getRegistrationWarnings(params: { meetId?: number }) {
  return get<RegistrationWarning[]>('/registrations/warnings', params)
}

export function lockRegistrations(meetId: number) {
  return post<void>('/registrations/lock', { meetId })
}
