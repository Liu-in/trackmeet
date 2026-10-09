import { del, get, post, put, type PageResult } from './request'

// ==================== 竞赛单元 ====================
export interface ScheduleSession {
  id?: number
  meetId?: number
  sessionName: string
  sessionDate: string
  dayNo?: number
  period: string
  startTime?: string
  endTime?: string
  venueId?: number
  venueIdsJson?: string
  /** 是否锁定（0/1）：锁定后不接受自动排入 */
  isLocked?: number
  lockedBy?: number
  /** 发布状态：DRAFT / PUBLISHED */
  publishStatus?: string
  /** 编排批次号 */
  batchNo?: string
  sortNo?: number
  remark?: string
}

// ==================== 日程 ====================
export interface Schedule {
  id?: number
  meetId?: number
  sessionId?: number
  eventId?: number
  roundId?: number
  venueId?: number
  planStartTime?: string
  planEndTime?: string
  actualStartTime?: string
  actualEndTime?: string
  heatCount?: number
  durationMin?: number
  orderNo?: number
  /** 是否锁定（0/1）：锁定后不参与重排，拖拽被拒绝 */
  isLocked?: number
  lockedBy?: number
  lockTime?: string
  /** 发布状态：DRAFT / PUBLISHED */
  publishStatus?: string
  conflictFlag?: number
  conflictCount?: number
  sourceType?: string
  batchNo?: string
  moveCount?: number
  adjustReason?: string
  remark?: string
}

export interface ScheduleVO extends Schedule {
  eventName?: string
  eventType?: string
  roundCode?: string
  roundName?: string
  groupName?: string
  sessionName?: string
  sessionDate?: string
  period?: string
  venueName?: string
}

export interface ScheduleQuery {
  page?: number
  size?: number
  meetId?: number
  sessionId?: number
  venueId?: number
  eventId?: number
  groupId?: number
  date?: string
  publishStatus?: string
  keyword?: string
}

// ==================== 分组 / 道次 / 田赛顺序 ====================
export interface Heat {
  id?: number
  meetId?: number
  scheduleId?: number
  heatNo: number
  heatName?: string
  laneCount?: number
  callTime?: string
  startTime?: string
  status?: string
  /** 是否锁定（0/1） */
  isLocked?: number
  lockedBy?: number
  batchNo?: string
  memberCount?: number
  remark?: string
}

export interface HeatMember {
  id?: number
  meetId?: number
  heatId?: number
  registrationId?: number
  athleteId?: number
  athleteName?: string
  unitId?: number
  unitName?: string
  bibNo?: string
  orderNo?: number
  laneNo?: number
  isSeed?: number
  isScratch?: number
  seedResult?: string
  status?: string
  /** 组内位置是否锁定（0/1） */
  isLocked?: number
}

export interface Lane {
  id?: number
  meetId?: number
  heatId?: number
  heatMemberId?: number
  laneNo: number
  assignMode?: string
  /** 是否锁定（0/1） */
  isLocked?: number
  lockedBy?: number
}

export interface FieldOrder {
  id?: number
  meetId?: number
  heatId?: number
  heatMemberId?: number
  orderNo: number
  groupNo?: number
  bestSeed?: number
  /** 是否锁定（0/1） */
  isLocked?: number
  lockedBy?: number
}

// ==================== 冲突 ====================
export interface Conflict {
  id?: number
  meetId?: number
  conflictType: string
  level: string
  scheduleId?: number
  relatedScheduleId?: number
  athleteId?: number
  athleteName?: string
  refereeId?: number
  venueId?: number
  detailJson?: string
  message?: string
  resolved?: number
  resolveRemark?: string
  detectTime?: string
}

export interface ConflictQuery {
  page?: number
  size?: number
  meetId?: number
  sessionId?: number
  eventId?: number
  conflictType?: string
  level?: string
  resolved?: number
}

// ==================== 自动编排计划 ====================
export interface AutoPlanItem {
  eventId?: number
  roundId?: number
  eventName?: string
  roundCode?: string
  sessionId?: number
  sessionName?: string
  date?: string
  startTime?: string
  endTime?: string
  venueId?: number
  venueName?: string
  heatCount?: number
  track?: boolean
  participantCount?: number
}

export interface AutoPlan {
  items: AutoPlanItem[]
  conflicts: Conflict[]
  shortageMessages: string[]
  scheduledCount: number
  unscheduledCount: number
  applied?: boolean
}

export interface AutoScheduleRequest {
  meetId?: number
  scope?: 'ALL' | 'SESSION' | 'GROUP' | 'EVENT'
  sessionIds?: number[]
  groupIds?: number[]
  eventIds?: number[]
  keepLocked?: boolean
  athleteIntervalMin?: number
  parallelFieldEvents?: boolean
  apply?: boolean
}

export interface GanttRow {
  sessionId?: number
  sessionName?: string
  sessionDate?: string
  period?: string
  venueId?: number
  venueName?: string
  items: AutoPlanItem[]
}

// ==================== 单元接口 ====================
export function pageSessions(params: { page?: number; size?: number; meetId?: number; date?: string; keyword?: string }) {
  return get<PageResult<ScheduleSession>>('/sessions', params)
}

export function listSessions(meetId: number) {
  return get<PageResult<ScheduleSession>>('/sessions', { page: 1, size: 200, meetId })
}

export function createSession(data: ScheduleSession) {
  return post<number>('/sessions', data)
}

export function updateSession(id: number, data: ScheduleSession) {
  return put<void>(`/sessions/${id}`, data)
}

export function deleteSession(id: number) {
  return del<void>(`/sessions/${id}`)
}

// ==================== 日程接口 ====================
export function pageSchedule(params: ScheduleQuery) {
  return get<PageResult<ScheduleVO>>('/schedule', params)
}

export function getSchedule(id: number) {
  return get<ScheduleVO>(`/schedule/${id}`)
}

export function createSchedule(data: Schedule) {
  return post<number>('/schedule', data)
}

export function updateSchedule(id: number, data: Schedule) {
  return put<void>(`/schedule/${id}`, data)
}

export function deleteSchedule(id: number) {
  return del<void>(`/schedule/${id}`)
}

export function getGantt(params: { meetId?: number; sessionId?: number }) {
  return get<GanttRow[]>('/schedule/gantt', params)
}

export function autoSchedule(data: AutoScheduleRequest) {
  return post<AutoPlan>('/schedule/auto', data)
}

export function regenerateSchedule(data: AutoScheduleRequest) {
  return post<AutoPlan>('/schedule/regenerate', { ...data, apply: true })
}

export function moveSchedule(id: number, data: { sessionId?: number; startTime?: string; venueId?: number; adjustReason?: string }) {
  return post<void>(`/schedule/${id}/move`, data)
}

/** 锁定/解锁日程（后端字段名为 isLocked，对应列 schedule.is_locked） */
export function lockSchedule(id: number, isLocked: boolean) {
  return put<void>(`/schedule/${id}/lock`, { isLocked })
}

export function publishSchedule(id: number, publishStatus: 'PUBLISHED' | 'DRAFT') {
  return put<void>(`/schedule/${id}/publish`, { publishStatus })
}

// ==================== 分组分道接口 ====================
export function generateHeats(data: {
  scheduleId: number
  drawMode?: string
  heatCount?: number
  heatSize?: number
  avoidSameUnit?: boolean
  laneMode?: string
  apply?: boolean
}) {
  return post<{ heats: Heat[]; lanes: Lane[] }>('/heats/generate', { apply: true, ...data })
}

export function listHeats(scheduleId: number) {
  return get<Heat[]>('/heats', { scheduleId })
}

export function listHeatMembers(heatId: number) {
  return get<HeatMember[]>(`/heats/${heatId}/members`)
}

export function addHeatMember(heatId: number, data: { registrationId: number; orderNo?: number }) {
  return post<number>(`/heats/${heatId}/members`, data)
}

export function removeHeatMember(heatId: number, memberId: number) {
  return del<void>(`/heats/${heatId}/members/${memberId}`)
}

export function moveHeatMember(data: { memberId: number; targetHeatId: number; targetOrderNo?: number }) {
  return post<void>('/heats/members/move', data)
}

/** 锁定/解锁分组（后端字段名为 isLocked，对应列 heat.is_locked） */
export function lockHeat(id: number, isLocked: boolean) {
  return put<void>(`/heats/${id}/lock`, { isLocked })
}

export function assignLanes(data: { heatIds: number[]; mode?: string; laneCount?: number }) {
  return post<Lane[]>('/lanes/assign', data)
}

export function updateLane(id: number, laneNo: number) {
  return put<void>(`/lanes/${id}`, { laneNo })
}

export function swapLanes(data: { laneIdA: number; laneIdB: number }) {
  return post<void>('/lanes/swap', data)
}

export function generateFieldOrders(data: { scheduleId: number; mode?: string; apply?: boolean }) {
  return post<FieldOrder[]>('/field-orders/generate', { apply: true, ...data })
}

export function reorderFieldOrders(data: { heatId: number; memberIds: number[] }) {
  return put<void>('/field-orders/reorder', data)
}

// ==================== 冲突接口 ====================
export function detectConflicts(data: { meetId?: number; sessionId?: number; eventId?: number }) {
  return post<Conflict[]>('/schedule/conflicts/detect', data)
}

export function listConflicts(params: ConflictQuery) {
  return get<PageResult<Conflict>>('/schedule/conflicts', { page: 1, size: 200, ...params })
}

export function resolveConflict(id: number, resolveRemark: string) {
  return put<void>(`/schedule/conflicts/${id}/resolve`, { resolveRemark })
}

// ==================== 快照接口 ====================
export function saveSnapshot(data: { meetId?: number; snapshotName: string }) {
  return post<number>('/schedule/snapshot', data)
}

export function restoreSnapshot(id: number) {
  return post<void>(`/schedule/snapshot/${id}/restore`)
}
