import { del, get, post, put, type PageResult } from './request'

// ---------------- 项目 ----------------
export interface SportEvent {
  id?: number
  meetId?: number
  eventName: string
  eventCode?: string
  eventType: string
  subType?: string
  groupId?: number
  groupName?: string
  gender: string
  isRelay?: number
  relayMin?: number
  relayMax?: number
  maxPerUnit?: number
  durationMin?: number
  /** 是否计风速（0/1） */
  windApplicable?: number
  /** 是否计入团体总分（0/1） */
  isTeamScore?: number
  roundCount?: number
  sortNo?: number
  status?: string
  remark?: string
}

export interface EventQuery {
  page?: number
  size?: number
  keyword?: string
  meetId?: number
  groupId?: number
  eventType?: string
  subType?: string
  gender?: string
  status?: string
}

// ---------------- 轮次与规则 ----------------
export interface EventRound {
  id?: number
  eventId?: number
  roundCode: string
  roundName: string
  roundNo?: number
  isFinal?: number
  planHeatCount?: number
  planAdvanceCount?: number
  status?: string
  rule?: EventRoundRule
}

export interface EventRoundRule {
  id?: number
  roundId?: number
  admitRank?: number
  groupTopN?: number
  bestLoserCount?: number
  targetCount?: number
  advanceJson?: string
  tieBreakJson?: string
  sourceRoundId?: number
}

export interface EventRuleConfig {
  id?: number
  eventId?: number
  admitRank?: number
  admitRankSource?: string
  drawMode?: string
  drawModeSource?: string
  laneOrderJson?: string
  laneOrderJsonSource?: string
  laneAssignMode?: string
  laneAssignModeSource?: string
  conflictIntervalMin?: number
  conflictIntervalMinSource?: string
  windJson?: string
  windJsonSource?: string
  attemptCount?: number
  attemptCountSource?: string
  qualifyStandard?: string
  qualifyStandardValue?: number
  qualifyStandardSource?: string
  qualifyStandardValueSource?: string
  heightPlanJson?: string
  heightPlanJsonSource?: string
  tieBreakJson?: string
  tieBreakJsonSource?: string
  durationMin?: number
  durationMinSource?: string
  extraJson?: string
  extraJsonSource?: string
}

export interface EventDetail {
  event: SportEvent
  rounds: EventRound[]
  ruleConfig: EventRuleConfig
  meetRuleConfig?: MeetRuleConfig
}

// ---------------- 名次分 / 加分 ----------------
export interface ScoreRuleItem {
  rankNo: number
  rankFrom?: number
  rankTo?: number
  rankLabel?: string
  pointValue?: number
  bonusPoint?: number
  tiePolicy?: string
  isTeamScore?: number
  sortNo?: number
}

export interface ScoreRule {
  id?: number
  meetId?: number
  ruleName: string
  ruleCode: string
  tiePolicy?: string
  scoreTarget?: string
  rankCount?: number
  isDefault?: number
  applyEventType?: string
  applyGender?: string
  effectiveRoundCode?: string
  isTeamScore?: number
  status?: string
  remark?: string
  items?: ScoreRuleItem[]
}

export interface BonusRule {
  id?: number
  meetId?: number
  ruleName: string
  ruleCode: string
  bonusType: string
  recordType?: string
  bonusPoint?: number
  isStackable?: number
  factor?: number
  eventId?: number
  groupId?: number
  applyEventType?: string
  applyGender?: string
  effectiveRoundCodes?: string
  isInTeamScore?: number
  priority?: number
  status?: string
  remark?: string
}

// ---------------- 届次级规则 ----------------
export interface MeetRuleConfig {
  id?: number
  meetId?: number
  admitRank?: number
  scoreRuleId?: number
  athleteMaxEvents?: number
  relayMaxPerAthlete?: number
  maxPerUnit?: number
  maxAthletePerUnit?: number
  conflictIntervalMin?: number
  refereeIntervalMin?: number
  relayDoubleFactor?: number
  bonusInTeamScore?: number
  medalSort?: string
  timePrecision?: string
  fieldAttemptCount?: number
  defaultLaneCount?: number
  defaultLaneOrderJson?: string
  laneAssignMode?: string
  windJson?: string
  tieBreakJson?: string
  extraJson?: string
}

// ---------------- 项目接口 ----------------
export function pageEvents(params: EventQuery) {
  return get<PageResult<SportEvent>>('/events', params)
}

export function getEvent(id: number) {
  return get<EventDetail>(`/events/${id}`)
}

export function createEvent(data: SportEvent) {
  return post<number>('/events', data)
}

export function updateEvent(id: number, data: SportEvent) {
  return put<void>(`/events/${id}`, data)
}

export function deleteEvent(id: number) {
  return del<void>(`/events/${id}`)
}

export function batchGenerateEvents(data: {
  meetId?: number
  groupIds: number[]
  gender: string
  templates: string[]
}) {
  return post<{ createdCount: number; skippedCount: number; createdNames: string[]; skippedNames: string[] }>(
    '/events/batch',
    data
  )
}

// ---------------- 轮次接口 ----------------
export function listRounds(eventId: number) {
  return get<EventRound[]>(`/events/${eventId}/rounds`)
}

export function createRound(eventId: number, data: EventRound) {
  return post<number>(`/events/${eventId}/rounds`, data)
}

export function getRound(id: number) {
  return get<EventRound>(`/rounds/${id}`)
}

export function updateRound(id: number, data: EventRound) {
  return put<void>(`/rounds/${id}`, data)
}

export function deleteRound(id: number) {
  return del<void>(`/rounds/${id}`)
}

// ---------------- 规则接口 ----------------
export function getRoundRule(roundId: number) {
  return get<EventRoundRule>(`/rounds/${roundId}/rule`)
}

export function saveRoundRule(roundId: number, data: EventRoundRule) {
  return put<void>(`/rounds/${roundId}/rule`, { ...data, roundId })
}

export function getEventRuleConfig(eventId: number) {
  return get<EventRuleConfig>(`/events/${eventId}/rule-config`)
}

export function saveEventRuleConfig(eventId: number, data: EventRuleConfig) {
  return put<void>(`/events/${eventId}/rule-config`, { ...data, eventId })
}

export function resetEventRuleConfig(eventId: number) {
  return post<void>(`/events/${eventId}/rule-config/reset`)
}

export function getMeetRuleConfig(meetId: number) {
  return get<MeetRuleConfig>(`/meets/${meetId}/rule-config`)
}

export function saveMeetRuleConfig(meetId: number, data: MeetRuleConfig) {
  return put<void>(`/meets/${meetId}/rule-config`, { ...data, meetId })
}

// ---------------- 名次分方案 ----------------
export function pageScoreRules(params: { page?: number; size?: number; meetId?: number; keyword?: string }) {
  return get<PageResult<ScoreRule>>('/score-rules', params)
}

export function getScoreRule(id: number) {
  return get<ScoreRule>(`/score-rules/${id}`)
}

export function createScoreRule(data: ScoreRule) {
  return post<number>('/score-rules', data)
}

export function updateScoreRule(id: number, data: ScoreRule) {
  return put<void>(`/score-rules/${id}`, data)
}

export function deleteScoreRule(id: number) {
  return del<void>(`/score-rules/${id}`)
}

export function listScoreRuleItems(id: number) {
  return get<ScoreRuleItem[]>(`/score-rules/${id}/items`)
}

export function saveScoreRuleItems(id: number, items: ScoreRuleItem[]) {
  return put<void>(`/score-rules/${id}/items`, items)
}

// ---------------- 加分规则 ----------------
export function pageBonusRules(params: { page?: number; size?: number; meetId?: number; bonusType?: string }) {
  return get<PageResult<BonusRule>>('/bonus-rules', params)
}

export function createBonusRule(data: BonusRule) {
  return post<number>('/bonus-rules', data)
}

export function updateBonusRule(id: number, data: BonusRule) {
  return put<void>(`/bonus-rules/${id}`, data)
}

export function deleteBonusRule(id: number) {
  return del<void>(`/bonus-rules/${id}`)
}
