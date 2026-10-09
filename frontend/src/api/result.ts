import { del, get, post, put, type PageResult } from './request'

// ==================== 成绩 ====================
export interface Result {
  id?: number
  meetId?: number
  scheduleId?: number
  heatId?: number
  heatMemberId?: number
  eventId?: number
  roundId?: number
  registrationId?: number
  athleteId?: number
  unitId?: number
  groupId?: number
  laneNo?: number
  /** 成绩原文，如 11"23 / 1:02.45 / 5.80 */
  resultRaw?: string
  /** 可比较数值（径赛秒、田赛米） */
  resultValue?: number
  resultUnit?: string
  /** 全能单项得分 */
  resultScore?: number
  finalHeight?: number
  rankNo?: number
  rankDisplay?: string
  /** VALID / DNS / DNF / DSQ / NM / PASS / SCRATCH */
  status?: string
  foulType?: string
  foulReason?: string
  windSpeed?: number
  windLegal?: number
  isRecordCandidate?: number
  isRecordConfirmed?: number
  recordId?: number
  recordType?: string
  isPb?: number
  isSb?: number
  /** DRAFT / SUBMITTED / APPROVED / REJECTED */
  auditStatus?: string
  auditBy?: number
  auditTime?: string
  rejectReason?: string
  isLocked?: number
  allroundGroupId?: number
  remark?: string
}

export interface ResultVO extends Result {
  eventName?: string
  roundName?: string
  roundCode?: string
  groupName?: string
  athleteName?: string
  bibNo?: string
  unitName?: string
  heatNo?: number
  orderNo?: number
  seedResult?: string
  auditStatusName?: string
  statusName?: string
}

export interface ResultQuery {
  page?: number
  size?: number
  meetId?: number
  eventId?: number
  roundId?: number
  heatId?: number
  unitId?: number
  auditStatus?: string
  status?: string
  keyword?: string
}

/** 成绩录入表的一行（按组次/道次排列） */
export interface EntrySheetRow {
  resultId?: number
  heatId?: number
  heatNo?: number
  heatName?: string
  laneNo?: number
  orderNo?: number
  registrationId?: number
  athleteId?: number
  athleteName?: string
  bibNo?: string
  unitId?: number
  unitName?: string
  /** 项目类型 TRACK/FIELD/ALLROUND/RELAY（后端返回时用于区分录入方式） */
  eventType?: string
  eventName?: string
  seedResult?: string
  resultRaw?: string
  resultValue?: number
  resultUnit?: string
  resultScore?: number
  status?: string
  windSpeed?: number
  windLegal?: number
  rankNo?: number
  auditStatus?: string
  /** 已审核锁定（1 锁定） */
  isLocked?: number
  isRecordCandidate?: number
  isRecordConfirmed?: number
  recordType?: string
  foulType?: string
  foulReason?: string
  attempts?: ResultAttempt[]
}

export interface ResultAttempt {
  id?: number
  resultId?: number
  attemptNo: number
  attemptRound?: number
  attemptValue?: number
  attemptUnit?: string
  isSuccess?: number
  isPass?: number
  isFoul?: number
  heightValue?: number
  isBest?: number
  remark?: string
}

export interface ResultSaveDTO {
  meetId?: number
  scheduleId?: number
  heatId?: number
  heatMemberId?: number
  groupId?: number
  resultId?: number
  registrationId?: number
  athleteId?: number
  eventId?: number
  roundId?: number
  resultRaw?: string
  resultValue?: number
  resultUnit?: string
  resultScore?: number
  status?: string
  foulType?: string
  foulReason?: string
  windSpeed?: number
  remark?: string
}

export interface ResultAudit {
  id?: number
  meetId?: number
  resultId?: number
  registrationId?: number
  action?: string
  beforeJson?: string
  afterJson?: string
  reason?: string
  operatorId?: number
  operatorName?: string
  operateTime?: string
  ip?: string
  remark?: string
}

export interface RankRow {
  resultId?: number
  registrationId?: number
  athleteId?: number
  athleteName?: string
  unitId?: number
  unitName?: string
  bibNo?: string
  heatNo?: number
  laneNo?: number
  resultRaw?: string
  resultValue?: number
  rankNo?: number
  rankDisplay?: string
  status?: string
  windSpeed?: number
  remark?: string
}

export interface RankGenerateResult {
  eventId?: number
  roundId?: number
  rankedCount?: number
  parallelMessages?: string[]
  ranks?: RankRow[]
}

export interface AdvanceRow {
  id?: number
  eventId?: number
  fromRoundId?: number
  toRoundId?: number
  registrationId?: number
  athleteId?: number
  athleteName?: string
  unitId?: number
  unitName?: string
  bibNo?: string
  sourceHeatNo?: number
  sourceRank?: number
  sourceResultValue?: number
  sourceResultRaw?: string
  /** GROUP_TOP / BEST_LOSER / MANUAL */
  advanceType?: string
  advanceOrder?: number
  isConfirmed?: number
}

export interface SportRecord {
  id?: number
  meetId?: number
  eventId?: number
  groupId?: number
  recordType?: string
  gender?: string
  athleteName?: string
  unitName?: string
  recordValue?: number
  recordRaw?: string
  recordUnit?: string
  recordDate?: string
  recordPlace?: string
  windSpeed?: number
  isCurrent?: number
  remark?: string
}

// ==================== 成绩录入接口 ====================
export function pageResults(params: ResultQuery) {
  return get<PageResult<ResultVO>>('/results', params)
}

export function getEntrySheet(params: { meetId?: number; scheduleId?: number; eventId?: number; roundId?: number }) {
  return get<EntrySheetRow[]>('/results/entry-sheet', params)
}

export function createResult(data: ResultSaveDTO) {
  return post<number>('/results', data)
}

export function batchSaveResults(data: { meetId?: number; scheduleId: number; results: ResultSaveDTO[] }) {
  return post<number>('/results/batch', data)
}

export function updateResult(id: number, data: ResultSaveDTO) {
  return put<void>(`/results/${id}`, data)
}

export function saveResultAttempt(data: ResultAttempt) {
  return post<number>('/results/attempt', data)
}

export function updateResultAttempt(id: number, data: ResultAttempt) {
  return put<void>(`/results/attempt/${id}`, data)
}

export function batchResultStatus(data: { ids: number[]; status: string; foulType?: string; foulReason?: string }) {
  return post<void>('/results/status', data)
}

export function saveWind(data: { resultIds?: number[]; heatId?: number; windSpeed: number }) {
  return post<void>('/results/wind', data)
}

export function submitResults(ids: number[]) {
  return post<void>('/results/submit', { ids })
}

export function approveResults(ids: number[]) {
  return post<void>('/results/approve', { ids })
}

export function rejectResults(ids: number[], rejectReason: string) {
  return post<void>('/results/reject', { ids, rejectReason })
}

export function revokeApproval(id: number, reason: string) {
  return post<void>(`/results/${id}/revoke-approval`, { reason })
}

export function getResultAudits(id: number) {
  return get<ResultAudit[]>(`/results/${id}/audits`)
}

// ==================== 名次与晋级 ====================
export function previewRank(params: { meetId?: number; eventId: number; roundId: number }) {
  return get<RankRow[]>('/results/rank', params)
}

export function generateRank(data: { meetId?: number; eventId: number; roundId: number; trackAscending?: boolean }) {
  return post<RankGenerateResult>('/results/rank/generate', data)
}

export function generateAdvance(data: {
  meetId?: number
  eventId: number
  fromRoundId: number
  toRoundId: number
  groupTopN?: number
  bestLoserCount?: number
  targetCount?: number
  excludeIllegalWind?: boolean
}) {
  return post<AdvanceRow[]>('/results/advance/generate', data)
}

export function listAdvance(params: { meetId?: number; eventId?: number; toRoundId?: number }) {
  return get<AdvanceRow[]>('/results/advance', params)
}

export function updateAdvance(id: number, data: { advanceOrder?: number; advanceType?: string }) {
  return put<void>(`/results/advance/${id}`, data)
}

export function confirmAdvance(data: { meetId?: number; eventId: number; toRoundId: number }) {
  return put<void>('/results/advance/confirm', data)
}

// ==================== 破纪录 ====================
export function confirmRecord(data: { resultId: number; recordType: string; confirmed: boolean }) {
  return post<void>('/results/record/confirm', data)
}

export function pageRecords(params: { meetId?: number; eventId?: number; groupId?: number; recordType?: string; keyword?: string }) {
  return get<PageResult<SportRecord>>('/records', { page: 1, size: 100, ...params })
}

export function createRecord(data: SportRecord) {
  return post<number>('/records', data)
}

export function updateRecord(id: number, data: SportRecord) {
  return put<void>(`/records/${id}`, data)
}

export function deleteRecord(id: number) {
  return del<void>(`/records/${id}`)
}
