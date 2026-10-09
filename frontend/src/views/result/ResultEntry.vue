<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { InputInstance } from 'element-plus'
import DictSelect from '@/components/DictSelect.vue'
import { DICT_TYPE, getDictItems, type DictItem } from '@/api/dict'
import { pageEvents, type SportEvent } from '@/api/event'
import { listSessions, pageSchedule, type ScheduleSession, type ScheduleVO } from '@/api/schedule'
import {
  batchResultStatus,
  batchSaveResults,
  confirmRecord,
  createResult,
  generateRank,
  getEntrySheet,
  previewRank,
  saveResultAttempt,
  saveWind,
  submitResults,
  updateResult,
  updateResultAttempt,
  type EntrySheetRow,
  type RankRow,
  type ResultAttempt,
  type ResultSaveDTO
} from '@/api/result'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 成绩录入：竞赛单元 → 日程条目 → 成绩录入表（径赛/接力按成绩原文，田赛展开试跳明细，全能另录单项分）。
 * 权限编码：result:list / result:add / result:edit / result:status / result:wind / result:submit
 *          result:attempt:add / result:approve（名次生成）/ result:record（破纪录认定）
 */
defineOptions({ name: 'ResultEntry' })

/** 录入表行：在接口返回的 EntrySheetRow 上补充本地录入态（不新增后端字段） */
type EntryRow = EntrySheetRow & {
  inputRaw: string
  savedRaw: string
  inputScore?: number
  foulType?: string
  foulReason?: string
  attempts: ResultAttempt[]
  parseError?: string
  editing?: boolean
  saving?: boolean
  isRecordCandidate?: number
  isRecordConfirmed?: number
  recordType?: string
  isLocked?: number
  foulDraft?: string
  newAttemptInput?: string
  newAttemptHeight?: number
  newAttemptFoul?: number
  newAttemptPass?: number
}

interface AttemptInfo {
  best?: string
  rows: ResultAttempt[]
}

const meetStore = useMeetStore()
const userStore = useUserStore()

const PERIOD_LABELS: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上'
}

const RECORD_TYPE_OPTIONS = [
  { label: '世界纪录', value: 'WORLD' },
  { label: '亚洲纪录', value: 'ASIA' },
  { label: '全国纪录', value: 'NATION' },
  { label: '省级纪录', value: 'PROVINCE' },
  { label: '市级纪录', value: 'CITY' },
  { label: '赛会纪录', value: 'MEET' },
  { label: '组别纪录', value: 'GROUP' }
]

/** 纪录层级选项：优先取 record_type 字典，字典不可用时回退到内置层级 */
const recordTypeItems = ref<DictItem[]>([])
const recordTypeOptions = computed<{ label: string; value: string }[]>(() => {
  if (recordTypeItems.value.length > 0) {
    return recordTypeItems.value.map((item) => ({ label: item.itemLabel, value: item.itemValue }))
  }
  return RECORD_TYPE_OPTIONS
})

async function loadRecordTypes() {
  try {
    recordTypeItems.value = (await getDictItems(DICT_TYPE.recordType)) || []
  } catch {
    recordTypeItems.value = []
  }
}

/* ------------------------------- 权限 ------------------------------- */

const canList = computed(() => userStore.hasPerm('result:list'))
const canAdd = computed(() => userStore.hasPerm('result:add'))
const canEdit = computed(() => userStore.hasPerm('result:edit'))
const canStatus = computed(() => userStore.hasPerm('result:status'))
const canWind = computed(() => userStore.hasPerm('result:wind'))
const canSubmit = computed(() => userStore.hasPerm('result:submit'))
const canAttempt = computed(() => userStore.hasPerm('result:attempt:add'))
const canApprove = computed(() => userStore.hasPerm('result:approve'))
const canRecord = computed(() => userStore.hasPerm('result:record'))

const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 顶部选择链路 ------------------------------- */

const sessions = ref<ScheduleSession[]>([])
const sessionsLoading = ref(false)
const sessionId = ref<number | undefined>(undefined)

const schedules = ref<ScheduleVO[]>([])
const schedulesLoading = ref(false)
const scheduleId = ref<number | undefined>(undefined)

const events = ref<SportEvent[]>([])

const sheetLoading = ref(false)
const rows = ref<EntryRow[]>([])

const currentSchedule = computed(() => schedules.value.find((item) => item.id === scheduleId.value))
const currentSession = computed(() => sessions.value.find((item) => item.id === sessionId.value))
const currentEvent = computed<SportEvent | undefined>(() => {
  const eventId = currentSchedule.value?.eventId
  if (!eventId) {
    return undefined
  }
  return events.value.find((item) => item.id === eventId)
})

const scheduleEventType = computed(() => currentSchedule.value?.eventType || currentEvent.value?.eventType || '')
const isTrackEvent = computed(() => scheduleEventType.value === 'TRACK' || scheduleEventType.value === 'RELAY')
const isFieldEvent = computed(() => scheduleEventType.value === 'FIELD')
const isAllroundEvent = computed(() => scheduleEventType.value === 'ALLROUND')
const eventTypeText = computed(() => {
  const type = scheduleEventType.value
  if (type === 'TRACK') {
    return '径赛'
  }
  if (type === 'RELAY') {
    return '接力'
  }
  if (type === 'FIELD') {
    return '田赛'
  }
  if (type === 'ALLROUND') {
    return '全能'
  }
  return '未知'
})

const currentEventId = computed(() => currentSchedule.value?.eventId ?? currentEvent.value?.id)
const currentRoundId = computed(() => currentSchedule.value?.roundId)
const canPreviewRank = computed(() => typeof currentEventId.value === 'number' && typeof currentRoundId.value === 'number')

function periodLabel(period?: string): string {
  if (!period) {
    return ''
  }
  return PERIOD_LABELS[period] || period
}

function sessionLabel(item: ScheduleSession): string {
  const parts: string[] = []
  if (item.sessionDate) {
    parts.push(item.sessionDate)
  }
  if (item.period) {
    parts.push(periodLabel(item.period))
  }
  parts.push(item.sessionName || `单元 ${item.id ?? ''}`)
  return parts.join(' ')
}

/** label = 项目名 + 轮次 + 时间 */
function scheduleLabel(item: ScheduleVO): string {
  const name = item.eventName || `项目 ${item.eventId ?? ''}`
  const round = item.roundName || item.roundCode || ''
  const time = item.planStartTime || item.sessionDate || ''
  return `${name} ${round} ${time}`.replace(/\s+/g, ' ').trim()
}

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    events.value = []
    return
  }
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId })
    events.value = page?.records || []
  } catch {
    events.value = []
  }
}

async function loadSessions() {
  if (!meetStore.currentMeetId || !canList.value) {
    sessions.value = []
    sessionId.value = undefined
    return
  }
  sessionsLoading.value = true
  try {
    const page = await listSessions(meetStore.currentMeetId)
    sessions.value = page?.records || []
    sessionId.value = sessions.value.length > 0 ? sessions.value[0].id : undefined
  } catch {
    sessions.value = []
    sessionId.value = undefined
  } finally {
    sessionsLoading.value = false
  }
}

async function loadSchedules() {
  scheduleId.value = undefined
  rows.value = []
  if (!sessionId.value || !meetStore.currentMeetId) {
    schedules.value = []
    return
  }
  schedulesLoading.value = true
  try {
    const page = await pageSchedule({
      meetId: meetStore.currentMeetId,
      sessionId: sessionId.value,
      page: 1,
      size: 200
    })
    schedules.value = page?.records || []
    scheduleId.value = schedules.value.length > 0 ? schedules.value[0].id : undefined
  } catch {
    schedules.value = []
    scheduleId.value = undefined
  } finally {
    schedulesLoading.value = false
  }
}

/* ------------------------------- 成绩解析与格式化 ------------------------------- */

/** 径赛成绩写法：11"23 / 11.23 / 1:02.45，统一解析为秒 */
function parseTrackInput(raw: string): { ok: boolean; seconds?: number; message?: string } {
  const text = (raw || '').trim().replace(/[″”"'’‘′]/g, '"').replace(/[：]/g, ':')
  if (!text) {
    return { ok: true, seconds: undefined }
  }
  let match = /^(\d{1,2}):([0-5]?\d)(?:\.(\d{1,2}))?$/.exec(text)
  if (match) {
    const minutes = Number(match[1])
    const seconds = Number(match[2])
    const fractionText = match[3] || ''
    const fraction = fractionText ? Number(fractionText) / Math.pow(10, fractionText.length) : 0
    return { ok: true, seconds: Number((minutes * 60 + seconds + fraction).toFixed(2)) }
  }
  match = /^(\d{1,3})"(\d{1,2})?$/.exec(text)
  if (match) {
    const seconds = Number(match[1])
    const fractionText = match[2] || ''
    const fraction = fractionText ? Number(fractionText) / Math.pow(10, fractionText.length) : 0
    return { ok: true, seconds: Number((seconds + fraction).toFixed(2)) }
  }
  match = /^(\d{1,4})(?:\.(\d{1,3}))?$/.exec(text)
  if (match) {
    const whole = Number(match[1])
    const fractionText = match[2] || ''
    const fraction = fractionText ? Number(fractionText) / Math.pow(10, fractionText.length) : 0
    return { ok: true, seconds: Number((whole + fraction).toFixed(2)) }
  }
  return { ok: false, message: '径赛成绩支持 11"23 / 11.23 / 1:02.45，请检查输入' }
}

/** 田赛成绩：5.80 或 5.80m 记米；580 或 580cm 记厘米（折米） */
function parseFieldInput(raw: string): { ok: boolean; meters?: number; message?: string } {
  const text = (raw || '').trim().toLowerCase()
  if (!text) {
    return { ok: true, meters: undefined }
  }
  let match = /^(\d{1,3}(?:\.\d{1,2})?)\s*(cm|厘米)$/.exec(text)
  if (match) {
    return { ok: true, meters: Number((Number(match[1]) / 100).toFixed(2)) }
  }
  match = /^(\d{1,3}(?:\.\d{1,2})?)\s*(m|米)?$/.exec(text)
  if (match) {
    return { ok: true, meters: Number(Number(match[1]).toFixed(2)) }
  }
  return { ok: false, message: '田赛成绩请输入 5.80（米）或 580cm，请检查输入' }
}

function formatSeconds(seconds?: number): string {
  if (typeof seconds !== 'number' || Number.isNaN(seconds)) {
    return ''
  }
  if (seconds < 60) {
    const text = seconds.toFixed(2)
    const dot = text.indexOf('.')
    return dot < 0 ? text : `${text.slice(0, dot)}"${text.slice(dot + 1)}`
  }
  const total = Math.round(seconds * 100) / 100
  const minutes = Math.floor(total / 60)
  const rest = total - minutes * 60
  const restText = rest < 10 ? `0${rest.toFixed(2)}` : rest.toFixed(2)
  return `${minutes}:${restText}`
}

function normalizeRaw(raw: string): { ok: boolean; raw?: string; value?: number; message?: string } {
  if (isTrackEvent.value) {
    const track = parseTrackInput(raw)
    if (!track.ok) {
      return { ok: false, message: track.message }
    }
    if (typeof track.seconds !== 'number') {
      return { ok: true, raw: undefined, value: undefined }
    }
    return { ok: true, raw: formatSeconds(track.seconds), value: track.seconds }
  }
  const field = parseFieldInput(raw)
  if (!field.ok) {
    return { ok: false, message: field.message }
  }
  if (typeof field.meters !== 'number') {
    return { ok: true, raw: undefined, value: undefined }
  }
  return { ok: true, raw: field.meters.toFixed(2), value: field.meters }
}

function rowStatus(row: EntryRow): string {
  return row.status || 'VALID'
}

function statusNeedsFoul(status: string): boolean {
  return status === 'DSQ' || status === 'DNF'
}

function statusKeepsResult(status: string): boolean {
  return status === 'VALID' || status === 'PASS'
}

function bestAttemptText(row: EntryRow): string {
  const best = row.attempts
    .filter((item) => item.isSuccess === 1 && typeof item.attemptValue === 'number' && !item.isPass)
    .sort((a, b) => (b.attemptValue ?? 0) - (a.attemptValue ?? 0))[0]
  if (best && typeof best.attemptValue === 'number') {
    return best.attemptValue.toFixed(2)
  }
  if (!isFieldEvent.value && row.inputRaw) {
    return `${row.inputRaw}（当前最好）`
  }
  return '-'
}

const canEditOrAdd = computed(() => canEdit.value || canAdd.value)

function isRowLocked(row: EntryRow): boolean {
  return row.isLocked === 1 || row.auditStatus === 'APPROVED'
}

function isResultEditable(row: EntryRow): boolean {
  return canEditOrAdd.value && !isRowLocked(row)
}

/* ------------------------------- 录入表载入 ------------------------------- */

function resetLocalState(row: EntryRow): void {
  row.inputRaw = row.resultRaw || ''
  row.savedRaw = row.resultRaw || ''
  row.inputScore = row.resultScore
  row.attempts = (row.attempts || []).map((item) => ({
    ...item,
    resultId: item.resultId ?? row.resultId
  }))
  row.parseError = ''
  row.editing = false
  row.saving = false
  row.foulDraft = ''
  row.newAttemptInput = ''
  row.newAttemptHeight = undefined
  row.newAttemptFoul = 0
  row.newAttemptPass = 0
  if (isFieldEvent.value && row.attempts.length === 0) {
    row.attempts = [1, 2, 3].map((no) => ({
      attemptNo: no,
      resultId: row.resultId,
      isSuccess: 0,
      isPass: 0,
      isFoul: 0
    }))
  }
}

function fetchSheet(): Promise<EntrySheetRow[]> {
  if (!meetStore.currentMeetId || typeof scheduleId.value !== 'number') {
    return Promise.resolve<EntrySheetRow[]>([])
  }
  return getEntrySheet({ meetId: meetStore.currentMeetId, scheduleId: scheduleId.value })
}

async function loadSheet(options?: { silent?: boolean }) {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  if (typeof scheduleId.value !== 'number') {
    rows.value = []
    return
  }
  if (!canList.value) {
    rows.value = []
    ElMessage.warning('缺少「成绩查看」权限（result:list）')
    return
  }
  const edits = new Map<number, { raw: string; score?: number }>()
  rows.value.forEach((row) => {
    if (typeof row.resultId === 'number' && row.inputRaw !== row.savedRaw) {
      edits.set(row.resultId, { raw: row.inputRaw, score: row.inputScore })
    }
  })

  sheetLoading.value = true
  try {
    const list = (await fetchSheet()) || []
    const next: EntryRow[] = list.map((item) => {
      const row: EntryRow = {
        ...item,
        inputRaw: item.resultRaw || '',
        savedRaw: item.resultRaw || '',
        inputScore: item.resultScore,
        attempts: (item.attempts || []).slice()
      }
      resetLocalState(row)
      return row
    })
    if (edits.size > 0) {
      let kept = 0
      next.forEach((row) => {
        if (typeof row.resultId !== 'number') {
          return
        }
        const pending = edits.get(row.resultId)
        if (!pending) {
          return
        }
        if (row.savedRaw === row.inputRaw) {
          row.inputRaw = pending.raw
          row.inputScore = pending.score
          row.editing = true
          kept += 1
        }
      })
      if (kept > 0 && !options?.silent) {
        ElMessage.info(`已保留 ${kept} 行未保存的输入`)
      }
    }
    rows.value = next
  } catch {
    rows.value = []
  } finally {
    sheetLoading.value = false
  }
}

/* ------------------------------- 分组与统计 ------------------------------- */

interface HeatGroup {
  key: string
  heatId?: number
  heatNo?: number
  heatName: string
  rows: EntryRow[]
}

const heatGroups = computed<HeatGroup[]>(() => {
  const map = new Map<string, HeatGroup>()
  rows.value.forEach((row) => {
    const key = typeof row.heatId === 'number' ? `heat-${row.heatId}` : `blank-${row.heatNo ?? 'x'}`
    let group = map.get(key)
    if (!group) {
      group = {
        key,
        heatId: row.heatId,
        heatNo: row.heatNo,
        heatName: row.heatName || (typeof row.heatNo === 'number' ? `第 ${row.heatNo} 组` : '未分组'),
        rows: []
      }
      map.set(key, group)
    }
    group.rows.push(row)
  })
  const groups = Array.from(map.values())
  groups.forEach((group) => {
    group.rows.sort((a, b) => {
      const left = a.laneNo ?? a.orderNo ?? 0
      const right = b.laneNo ?? b.orderNo ?? 0
      return left - right
    })
  })
  return groups
})

const recordedCount = computed(() => rows.value.filter((row) => !!row.inputRaw || !statusKeepsResult(rowStatus(row))).length)
const totalCount = computed(() => rows.value.length)
const errorCount = computed(() => rows.value.filter((row) => !!row.parseError || !!validationMessage(row)).length)

function validationMessage(row: EntryRow): string {
  if (row.parseError) {
    return row.parseError
  }
  const status = rowStatus(row)
  if (statusKeepsResult(status)) {
    if (!row.inputRaw && typeof row.attempts.find((item) => item.isSuccess === 1)?.attemptValue !== 'number') {
      return '未录入成绩'
    }
    return ''
  }
  if (statusNeedsFoul(status) && !(row.foulType || row.foulDraft)) {
    return '需选择犯规类型'
  }
  if (status === 'DSQ' && !row.foulReason) {
    return '需填写犯规说明'
  }
  return ''
}

/* ------------------------------- 行内录入 ------------------------------- */

const inputRefs = ref<(InputInstance | null)[]>([])

function collectInputRef(el: unknown, index: number): void {
  inputRefs.value[index] = (el as InputInstance | null) || null
}

function focusNext(index: number): void {
  const next = inputRefs.value[index + 1]
  if (next && typeof next.focus === 'function') {
    next.focus()
    return
  }
  ElMessage.info('已到最后一行，可直接点击「保存本组」提交录入结果')
}

function onResultInput(row: EntryRow): void {
  row.editing = true
  row.parseError = ''
  if (!row.inputRaw) {
    return
  }
  const parser = isTrackEvent.value ? parseTrackInput : parseFieldInput
  const parsed = parser(row.inputRaw)
  if (parsed.ok) {
    return
  }
  row.parseError = parsed.message
}

function onResultBlur(row: EntryRow): void {
  row.editing = true
  if (!row.inputRaw) {
    row.parseError = ''
    return
  }
  const normalized = normalizeRaw(row.inputRaw)
  if (!normalized.ok) {
    row.parseError = normalized.message || '成绩格式不正确'
    return
  }
  row.parseError = ''
  if (normalized.raw) {
    row.inputRaw = normalized.raw
  }
}

function onStatusChange(row: EntryRow): void {
  if (!row.status) {
    row.status = 'VALID'
  }
  if (!statusKeepsResult(rowStatus(row))) {
    row.inputRaw = ''
    row.parseError = ''
    row.attempts.forEach((attempt) => {
      attempt.isSuccess = 0
      attempt.attemptValue = undefined
      attempt.isPass = rowStatus(row) === 'PASS' ? 1 : 0
    })
  } else {
    row.foulDraft = ''
    row.foulReason = ''
  }
  row.editing = true
}

function rowValue(row: EntryRow): number | undefined {
  const parsed = normalizeRaw(row.inputRaw)
  return parsed.ok ? parsed.value : undefined
}

function savePayload(row: EntryRow, value?: number): ResultSaveDTO {
  const payload: ResultSaveDTO = {
    meetId: meetStore.currentMeetId,
    scheduleId: scheduleId.value,
    heatId: row.heatId,
    resultId: row.resultId,
    registrationId: row.registrationId,
    athleteId: row.athleteId,
    eventId: currentEventId.value,
    roundId: currentRoundId.value,
    resultUnit: isTrackEvent.value ? 's' : isFieldEvent.value ? 'm' : row.resultUnit,
    status: rowStatus(row),
    foulType: row.foulType || row.foulDraft,
    foulReason: row.foulReason
  }
  if (statusKeepsResult(rowStatus(row)) && row.inputRaw) {
    payload.resultRaw = row.inputRaw
    payload.resultValue = value ?? rowValue(row)
  }
  if (isAllroundEvent.value && typeof row.inputScore === 'number') {
    payload.resultScore = row.inputScore
    payload.resultUnit = 'pt'
  }
  return payload
}

async function saveRow(row: EntryRow) {
  if (!canEditOrAdd.value) {
    ElMessage.warning('缺少「成绩录入/修改」权限（result:add 或 result:edit）')
    return
  }
  if (isRowLocked(row)) {
    ElMessage.warning('该成绩已审核锁定，请先撤销审核后再修改')
    return
  }
  if (row.parseError) {
    ElMessage.warning(row.parseError)
    return
  }
  if (rowStatus(row) === 'DSQ' && (!(row.foulType || row.foulDraft) || !row.foulReason)) {
    ElMessage.warning('取消资格（DSQ）必须选择犯规类型并填写犯规说明')
    return
  }
  if (statusKeepsResult(rowStatus(row)) && row.inputRaw && typeof rowValue(row) !== 'number') {
    ElMessage.warning('成绩格式不正确，请检查后再保存')
    return
  }

  row.saving = true
  try {
    const payload = savePayload(row)
    if (typeof row.resultId === 'number') {
      await updateResult(row.resultId, payload)
      ElMessage.success('成绩已更新')
    } else {
      const createdId = await createResult(payload)
      if (typeof createdId === 'number') {
        row.resultId = createdId
      }
      ElMessage.success('成绩已录入')
    }
    row.savedRaw = row.inputRaw
    row.editing = false
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    row.saving = false
  }
}

/* ------------------------------- 批量保存 / 提交 ------------------------------- */

const batchSaving = ref(false)

async function handleBatchSave() {
  if (!canEditOrAdd.value) {
    ElMessage.warning('缺少「成绩录入/修改」权限（result:add 或 result:edit）')
    return
  }
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  if (typeof scheduleId.value !== 'number') {
    ElMessage.warning('请先选择日程条目')
    return
  }
  if (rows.value.length === 0) {
    ElMessage.warning('当前录入表没有可保存的行')
    return
  }
  const dirty = rows.value.filter((row) => row.editing || row.inputRaw !== row.savedRaw)
  if (dirty.length === 0) {
    ElMessage.info('没有需要保存的修改')
    return
  }
  const bad = dirty.find((row) => !!row.parseError)
  if (bad) {
    ElMessage.warning(`存在格式不正确的成绩：${bad.athleteName || bad.bibNo || '未知运动员'}`)
    return
  }
  const locked = dirty.find((row) => isRowLocked(row))
  if (locked) {
    ElMessage.warning(`「${locked.athleteName || locked.bibNo || '该运动员'}」的成绩已锁定，请先撤销审核`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认保存本组共 ${dirty.length} 行成绩吗？未填写的行将按「无成绩 + 状态」提交。`,
      '保存本组',
      { type: 'warning', confirmButtonText: '确认保存', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  batchSaving.value = true
  try {
    const results: ResultSaveDTO[] = dirty.map((row) => savePayload(row))
    await batchSaveResults({ meetId: meetStore.currentMeetId, scheduleId: scheduleId.value, results })
    ElMessage.success(`已保存 ${dirty.length} 行成绩`)
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    batchSaving.value = false
  }
}

const submitting = ref(false)

async function handleSubmit() {
  if (!canSubmit.value) {
    ElMessage.warning('缺少「提交审核」权限（result:submit）')
    return
  }
  if (rows.value.length === 0) {
    ElMessage.warning('当前录入表没有可提交的行')
    return
  }
  if (rows.value.some((row) => row.inputRaw !== row.savedRaw || row.editing)) {
    ElMessage.warning('存在未保存的录入内容，请先「保存本组」')
    return
  }
  const bad = rows.value.find((row) => !!validationMessage(row) && validationMessage(row) !== '')
  if (bad) {
    ElMessage.warning(
      `「${bad.athleteName || bad.bibNo || '未知运动员'}」${validationMessage(bad)}，请补全后再提交审核`
    )
    return
  }
  const ids = rows.value.map((row) => row.resultId).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning('当前组尚无已保存的成绩记录，无法提交审核')
    return
  }
  if (ids.length < rows.value.length) {
    ElMessage.warning('部分行尚未保存到后端，请先「保存本组」后再提交')
    return
  }
  try {
    await ElMessageBox.confirm(`确认提交 ${ids.length} 条成绩进入审核吗？提交后需审核通过才会锁定。`, '提交审核', {
      type: 'warning',
      confirmButtonText: '确认提交',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  submitting.value = true
  try {
    await submitResults(ids)
    ElMessage.success(`已提交 ${ids.length} 条成绩待审核`)
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ------------------------------- 批量状态 ------------------------------- */

function selectableRow(row: EntryRow): boolean {
  return typeof row.resultId === 'number' && !isRowLocked(row)
}

const selection = ref<EntryRow[]>([])
const statusDialogVisible = ref(false)
const statusSubmitting = ref(false)
const statusForm = reactive({
  status: 'DNS',
  foulType: '',
  foulReason: ''
})

function onSelectionChange(list: EntryRow[]): void {
  selection.value = list.filter((row) => !isRowLocked(row))
  if (selection.value.length !== list.length) {
    ElMessage.warning('已审核锁定的成绩不可勾选')
  }
}

function openStatusDialog() {
  if (!canStatus.value) {
    ElMessage.warning('缺少「成绩状态」权限（result:status）')
    return
  }
  if (selection.value.length === 0) {
    ElMessage.warning('请先勾选需要标记的行')
    return
  }
  statusForm.status = 'DNS'
  statusForm.foulType = ''
  statusForm.foulReason = ''
  statusDialogVisible.value = true
}

async function submitStatusDialog() {
  const ids = selection.value.map((row) => row.resultId).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning('请先勾选需要标记的行')
    return
  }
  if (statusForm.status === 'DSQ' && !statusForm.foulType) {
    ElMessage.warning('取消资格（DSQ）必须选择犯规类型')
    return
  }
  if (statusForm.status === 'DSQ' && !statusForm.foulReason.trim()) {
    ElMessage.warning('取消资格（DSQ）必须填写犯规说明')
    return
  }
  statusSubmitting.value = true
  try {
    await batchResultStatus({
      ids,
      status: statusForm.status,
      foulType: statusForm.foulType || undefined,
      foulReason: statusForm.foulReason.trim() || undefined
    })
    ElMessage.success(`已标记 ${ids.length} 行为「${statusForm.status}」`)
    statusDialogVisible.value = false
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    statusSubmitting.value = false
  }
}

/* ------------------------------- 风速 ------------------------------- */

const windDialogVisible = ref(false)
const windSubmitting = ref(false)
const windForm = reactive({ windSpeed: 0 })
const windTarget = ref<{ heatId?: number; heatNo?: number; resultIds?: number[]; label: string } | null>(null)

function openWindDialog(target?: { heatId?: number; heatNo?: number; resultIds?: number[]; label: string }) {
  if (!canWind.value) {
    ElMessage.warning('缺少「风速录入」权限（result:wind）')
    return
  }
  if (target) {
    windTarget.value = target
  } else if (selection.value.length > 0) {
    const ids = selection.value.map((row) => row.resultId).filter((id): id is number => typeof id === 'number')
    windTarget.value = { resultIds: ids, label: `已勾选 ${ids.length} 行按成绩逐条写入` }
  } else {
    const groupsWithRows = heatGroups.value.filter((group) => typeof group.heatId === 'number')
    if (groupsWithRows.length === 0) {
      ElMessage.warning('请先选择日程条目，或勾选行后按成绩写入风速')
      return
    }
    if (groupsWithRows.length > 1) {
      ElMessage.warning('当前日程包含多个分组，请使用分组标题栏的「录入风速」按组录入')
      return
    }
    windTarget.value = {
      heatId: groupsWithRows[0].heatId,
      heatNo: groupsWithRows[0].heatNo,
      label: `${groupsWithRows[0].heatName} 整组按组写入`
    }
  }
  windForm.windSpeed = 0
  windDialogVisible.value = true
}

async function submitWindDialog() {
  const target = windTarget.value
  if (!target) {
    return
  }
  const speed = Number(windForm.windSpeed)
  if (Number.isNaN(speed)) {
    ElMessage.warning('请输入风速（m/s），顺风为正、逆风为负')
    return
  }
  if (Math.abs(speed) > 10) {
    ElMessage.warning('风速超出常见范围（±10 m/s），请确认后重新输入')
    return
  }
  windSubmitting.value = true
  try {
    await saveWind({
      heatId: target.heatId,
      resultIds: target.resultIds,
      windSpeed: speed
    })
    windDialogVisible.value = false
    ElMessage.success('风速已录入')
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    windSubmitting.value = false
  }
}

/* ------------------------------- 名次预览 / 生成名次 ------------------------------- */

const rankDrawerVisible = ref(false)
const rankLoading = ref(false)
const rankGenerating = ref(false)
const rankRows = ref<RankRow[]>([])
const rankMessages = ref<string[]>([])

async function overviewRank() {
  if (!ensureTarget()) {
    return
  }
  const eventId = currentEventId.value
  const roundId = currentRoundId.value
  if (typeof eventId !== 'number' || typeof roundId !== 'number') {
    ElMessage.warning('当前日程条目缺少项目或轮次信息，无法预览名次')
    return
  }
  rankDrawerVisible.value = true
  rankLoading.value = true
  rankRows.value = []
  rankMessages.value = []
  try {
    const list = await previewRank({ meetId: meetStore.currentMeetId, eventId, roundId })
    rankRows.value = list || []
  } catch {
    rankRows.value = []
  } finally {
    rankLoading.value = false
  }
}

async function handleGenerateRank() {
  const eventId = currentEventId.value
  const roundId = currentRoundId.value
  if (typeof eventId !== 'number' || typeof roundId !== 'number') {
    ElMessage.warning('当前日程条目缺少项目或轮次信息，无法生成名次')
    return
  }
  try {
    await ElMessageBox.confirm('确认按当前成绩生成名次吗？生成会覆盖本项目轮次的既有名次记录。', '生成名次', {
      type: 'warning',
      confirmButtonText: '确认生成',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  rankGenerating.value = true
  try {
    const result = await generateRank({ meetId: meetStore.currentMeetId, eventId, roundId })
    rankRows.value = result?.ranks || rankRows.value
    rankMessages.value = result?.parallelMessages || []
    ElMessage.success(`名次已生成，共 ${result?.rankedCount ?? rankRows.value.length} 条`)
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    rankGenerating.value = false
  }
}

/* ------------------------------- 破纪录认定 ------------------------------- */

const recordDialogVisible = ref(false)
const recordSubmitting = ref(false)
const recordForm = reactive({ recordType: 'MEET' })
const recordTarget = ref<EntryRow | null>(null)

function openRecordDialog(row: EntryRow) {
  if (!canRecord.value) {
    ElMessage.warning('缺少「破纪录认定」权限（result:record）')
    return
  }
  if (typeof row.resultId !== 'number') {
    ElMessage.warning('请先保存该行成绩后再认定纪录')
    return
  }
  recordTarget.value = row
  recordForm.recordType = row.recordType || 'MEET'
  recordDialogVisible.value = true
}

async function submitRecordDialog() {
  const row = recordTarget.value
  if (!row || typeof row.resultId !== 'number') {
    return
  }
  recordSubmitting.value = true
  try {
    await confirmRecord({ resultId: row.resultId, recordType: recordForm.recordType, confirmed: true })
    row.isRecordConfirmed = 1
    row.recordType = recordForm.recordType
    recordDialogVisible.value = false
    ElMessage.success('破纪录已认定')
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    recordSubmitting.value = false
  }
}

/* ------------------------------- 田赛试跳明细 ------------------------------- */

function attemptsOf(row: EntryRow): AttemptInfo {
  const list = row.attempts.slice().sort((a, b) => a.attemptNo - b.attemptNo)
  const bestList = list.filter((item) => item.isSuccess === 1 && typeof item.attemptValue === 'number')
  const best = bestList.sort((a, b) => (b.attemptValue ?? 0) - (a.attemptValue ?? 0))[0]
  return { best: best && typeof best.attemptValue === 'number' ? best.attemptValue.toFixed(2) : undefined, rows: list }
}

function nextAttemptNo(row: EntryRow): number {
  return row.attempts.reduce((max, item) => Math.max(max, item.attemptNo || 0), 0) + 1
}

function addAttempt(row: EntryRow): void {
  if (!canAttempt.value) {
    ElMessage.warning('缺少「试跳录入」权限（result:attempt:add）')
    return
  }
  if (typeof row.resultId !== 'number') {
    ElMessage.warning('请先保存该行成绩，再录入试跳/试掷明细')
    return
  }
  row.attempts = [
    ...row.attempts,
    {
      attemptNo: nextAttemptNo(row),
      resultId: row.resultId,
      isSuccess: 0,
      isPass: 0,
      isFoul: 0,
      attemptUnit: 'm'
    }
  ]
}

async function saveAttempt(row: EntryRow, attempt: ResultAttempt) {
  if (!canAttempt.value) {
    ElMessage.warning('缺少「试跳录入」权限（result:attempt:add）')
    return
  }
  if (typeof row.resultId !== 'number') {
    ElMessage.warning('请先保存该行成绩，再录入试跳/试掷明细')
    return
  }
  if (attempt.isPass === 1) {
    attempt.attemptValue = undefined
    attempt.isSuccess = 0
    attempt.isFoul = 0
  }
  if (attempt.isFoul === 1) {
    attempt.isSuccess = 0
  } else if (typeof attempt.attemptValue === 'number' && attempt.isPass !== 1) {
    attempt.isSuccess = 1
  }
  if (typeof attempt.attemptValue === 'number' && Number.isNaN(attempt.attemptValue)) {
    ElMessage.warning('试跳/试掷成绩必须是数值（米），如 5.80')
    return
  }
  if (attempt.isSuccess === 1 && typeof attempt.attemptValue !== 'number') {
    ElMessage.warning('成功的试跳/试掷必须填写成绩数值（米）')
    return
  }
  row.saving = true
  try {
    const payload: ResultAttempt = { ...attempt, resultId: row.resultId, attemptUnit: attempt.attemptUnit || 'm' }
    if (typeof attempt.id === 'number') {
      await updateResultAttempt(attempt.id, payload)
    } else {
      const id = await saveResultAttempt(payload)
      if (typeof id === 'number') {
        attempt.id = id
      }
    }
    ElMessage.success('试跳明细已保存')
    await loadSheet({ silent: true })
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    row.saving = false
  }
}

/* ------------------------------- 初始化与联动 ------------------------------- */

function ensureTarget(): boolean {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return false
  }
  if (typeof scheduleId.value !== 'number') {
    ElMessage.warning('请先选择日程条目（项目轮次）')
    return false
  }
  return true
}

watch(sessionId, () => {
  loadSchedules()
})

watch(scheduleId, () => {
  loadSheet()
})

watch(
  () => meetStore.currentMeetId,
  async () => {
    sessions.value = []
    sessionId.value = undefined
    schedules.value = []
    scheduleId.value = undefined
    rows.value = []
    await Promise.all([loadEvents(), loadSessions()])
  }
)

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && (!meetStore.units.length || !meetStore.groups.length)) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([loadEvents(), loadSessions(), loadRecordTypes()])
  await nextTick()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">成绩录入</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜按竞赛单元与日程条目录入成绩，支持径赛、接力、田赛与全能
      </div>
    </div>

    <el-alert
      v-if="!canList"
      title="当前账号缺少「成绩查看」权限（result:list），录入表不可加载。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-alert
      title="径赛成绩支持 11&quot;23 / 11.23 / 1:02.45；田赛录最好成绩并可用展开行记录每次试跳"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="tm-card">
      <div class="tm-search-bar">
        <span>竞赛单元：</span>
        <el-select
          v-model="sessionId"
          :loading="sessionsLoading"
          :disabled="!meetReady"
          placeholder="请选择竞赛单元"
          filterable
          clearable
          style="width: 260px"
        >
          <el-option v-for="item in sessions" :key="item.id" :label="sessionLabel(item)" :value="item.id" />
        </el-select>
        <span>日程条目：</span>
        <el-select
          v-model="scheduleId"
          :loading="schedulesLoading"
          :disabled="!sessionId"
          placeholder="请选择项目轮次"
          filterable
          clearable
          style="width: 360px"
        >
          <el-option v-for="item in schedules" :key="item.id" :label="scheduleLabel(item)" :value="item.id" />
        </el-select>
        <el-button :loading="sheetLoading" :disabled="!scheduleId" @click="loadSheet()">刷新录入表</el-button>
      </div>

      <div v-if="!meetReady" class="tm-form-tip" style="margin-top: 8px">请先在顶部选择届次。</div>
      <div v-else-if="canList && !sessionsLoading && sessions.length === 0" class="tm-form-tip" style="margin-top: 8px">
        当前届次暂无竞赛单元，请先在日程编排中创建竞赛单元。
      </div>
      <div v-else-if="sessionId && !schedulesLoading && schedules.length === 0" class="tm-form-tip" style="margin-top: 8px">
        该竞赛单元暂无日程条目，请先在日程编排中生成日程。
      </div>

      <el-descriptions v-if="currentSchedule" :column="4" border size="small" style="margin-top: 12px">
        <el-descriptions-item label="项目">{{ currentSchedule.eventName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="轮次">{{ currentSchedule.roundName || currentSchedule.roundCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="项目类型">{{ eventTypeText }}</el-descriptions-item>
        <el-descriptions-item label="单元">{{ currentSession?.sessionName || currentSchedule.sessionName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="场地">{{ currentSchedule.venueName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="计划时间">{{ currentSchedule.planStartTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="分组数">{{ heatGroups.length }}</el-descriptions-item>
        <el-descriptions-item label="录入行">{{ recordedCount }} / {{ totalCount }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <template v-if="canList">
      <div class="tm-card">
        <div class="tm-toolbar">
          <div class="tm-toolbar-actions">
            <el-button type="primary" :loading="batchSaving" :disabled="!scheduleId" @click="handleBatchSave">
              保存本组
            </el-button>
            <el-button :loading="submitting" :disabled="!scheduleId" @click="handleSubmit">提交审核</el-button>
            <el-button :disabled="!selection.length" @click="openStatusDialog">
              批量标记{{ selection.length ? `（${selection.length}）` : '' }}
            </el-button>
            <el-button :disabled="!scheduleId" @click="openWindDialog()">录入风速</el-button>
          </div>
          <div class="tm-toolbar-actions">
            <el-button :disabled="!canPreviewRank || !scheduleId" @click="overviewRank">预览名次</el-button>
            <el-button :loading="sheetLoading" :disabled="!scheduleId" @click="loadSheet()">刷新</el-button>
          </div>
        </div>
        <div class="tm-form-tip" style="margin-top: 8px">
          已录入 {{ recordedCount }} / {{ totalCount }} 行{{ errorCount > 0 ? `，需处理 ${errorCount} 行` : '' }}。风速按「组」批量写入，
          勾选行后则按成绩逐条写入。键盘流：输入框内按 Enter 跳到下一行（等同于 Tab 连续录入）。
        </div>
      </div>

      <div class="tm-stat-grid">
        <div class="tm-stat-card">
          <div class="tm-stat-label">录入表总行数</div>
          <div class="tm-stat-value">{{ totalCount }}</div>
        </div>
        <div class="tm-stat-card">
          <div class="tm-stat-label">已录入</div>
          <div class="tm-stat-value">{{ recordedCount }}</div>
        </div>
        <div class="tm-stat-card">
          <div class="tm-stat-label">待处理 / 错误行</div>
          <div class="tm-stat-value">{{ errorCount }}</div>
        </div>
        <div class="tm-stat-card">
          <div class="tm-stat-label">已勾选</div>
          <div class="tm-stat-value">{{ selection.length }}</div>
        </div>
      </div>

      <div v-loading="sheetLoading">
        <el-empty v-if="rows.length === 0" description="请选择日程条目以载入成绩录入表" />
        <div v-for="group in heatGroups" :key="group.key" class="tm-card" style="margin-bottom: 12px">
          <div class="tm-toolbar" style="margin-bottom: 8px">
            <div class="tm-toolbar-actions">
              <span class="tm-page-title">{{ group.heatName }}</span>
              <el-tag size="small" effect="plain">{{ group.rows.length }} 人</el-tag>
            </div>
            <div class="tm-toolbar-actions">
              <el-button
                size="small"
                :disabled="typeof group.heatId !== 'number'"
                @click="openWindDialog({ heatId: group.heatId, heatNo: group.heatNo, label: group.heatName + ' 整组按组写入' })"
              >
                录入风速
              </el-button>
            </div>
          </div>

          <el-table
            :data="group.rows"
            border
            stripe
            row-key="resultId"
            size="small"
            @selection-change="onSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="selectableRow" />
            <el-table-column v-if="isFieldEvent || isAllroundEvent" type="expand" width="46">
              <template #default="{ row }">
                <div class="entry-attempt-panel">
                  <div class="tm-form-tip" style="margin-bottom: 8px">
                    最好成绩：{{ bestAttemptText(row as EntryRow) }}｜逐次录入后将自动标记最好一次；免跳（PASS）不消耗试跳次数。
                  </div>
                  <el-table :data="attemptsOf(row as EntryRow).rows" border size="small">
                    <el-table-column prop="attemptNo" label="次序" width="60" align="center" />
                    <el-table-column label="成绩（米/厘米）" width="170">
                      <template #default="scope">
                        <el-input
                          v-model="(scope.row as ResultAttempt).attemptValue"
                          placeholder="如 5.80 或 580cm"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="高度（米）" width="140">
                      <template #default="scope">
                        <el-input-number
                          v-model="(scope.row as ResultAttempt).heightValue"
                          :precision="2"
                          :step="0.05"
                          :controls="false"
                          style="width: 100%"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="成功" width="70" align="center">
                      <template #default="scope">
                        <el-checkbox
                          v-model="(scope.row as ResultAttempt).isSuccess"
                          :true-value="1"
                          :false-value="0"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="免跳" width="70" align="center">
                      <template #default="scope">
                        <el-checkbox
                          v-model="(scope.row as ResultAttempt).isPass"
                          :true-value="1"
                          :false-value="0"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="犯规" width="70" align="center">
                      <template #default="scope">
                        <el-checkbox
                          v-model="(scope.row as ResultAttempt).isFoul"
                          :true-value="1"
                          :false-value="0"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                        />
                      </template>
                    </el-table-column>
                    <el-table-column label="最好" width="70" align="center">
                      <template #default="scope">
                        <el-tag v-if="(scope.row as ResultAttempt).isBest === 1" size="small" type="success">最好</el-tag>
                        <span v-else>-</span>
                      </template>
                    </el-table-column>
                    <el-table-column label="操作" width="90" align="center">
                      <template #default="scope">
                        <el-button
                          link
                          type="primary"
                          :disabled="!canAttempt || isRowLocked(row as EntryRow)"
                          @click="saveAttempt(row as EntryRow, scope.row as ResultAttempt)"
                        >
                          保存
                        </el-button>
                      </template>
                    </el-table-column>
                    <template #empty>
                      <div class="tm-form-tip">
                        暂无试跳明细；{{ typeof (row as EntryRow).resultId === 'number' ? '可点下方「新增一次」开始录入' : '请先保存成绩行' }}
                      </div>
                    </template>
                  </el-table>
                  <div style="margin-top: 8px">
                    <el-button size="small" :disabled="!canAttempt || isRowLocked(row as EntryRow)" @click="addAttempt(row as EntryRow)">
                      新增一次
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="heatNo" label="组次" width="60" align="center">
              <template #default="{ row }">{{ (row as EntryRow).heatNo ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="laneNo" label="道次" width="60" align="center">
              <template #default="{ row }">{{ (row as EntryRow).laneNo ?? (row as EntryRow).orderNo ?? '-' }}</template>
            </el-table-column>
            <el-table-column prop="bibNo" label="号码" width="80" align="center" />
            <el-table-column prop="athleteName" label="姓名" min-width="90" show-overflow-tooltip />
            <el-table-column prop="unitName" label="单位" min-width="120" show-overflow-tooltip />
            <el-table-column prop="seedResult" label="种子成绩" width="100" align="center" />
            <el-table-column :label="isTrackEvent ? '成绩（11&quot;23 / 11.23 / 1:02.45）' : '成绩（最好，米）'" width="230">
              <template #default="{ row, $index }">
                <el-input
                  :ref="(el: unknown) => collectInputRef(el, $index)"
                  v-model="(row as EntryRow).inputRaw"
                  :placeholder="isTrackEvent ? '11.23 或 1:02.45' : '5.80 或 580cm'"
                  :disabled="!isResultEditable(row as EntryRow)"
                  @input="onResultInput(row as EntryRow)"
                  @blur="onResultBlur(row as EntryRow)"
                  @keyup.enter="focusNext($index)"
                />
                <div v-if="(row as EntryRow).parseError" class="entry-error">{{ (row as EntryRow).parseError }}</div>
              </template>
            </el-table-column>
            <el-table-column v-if="isAllroundEvent" label="单项得分" width="120" align="center">
              <template #default="{ row }">
                <el-input-number
                  v-model="(row as EntryRow).inputScore"
                  :precision="2"
                  :controls="false"
                  style="width: 100%"
                  :disabled="!isResultEditable(row as EntryRow)"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="isFieldEvent" label="当前最好" width="100" align="center">
              <template #default="{ row }">{{ bestAttemptText(row as EntryRow) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="150">
              <template #default="{ row }">
                <DictSelect
                  v-model="(row as EntryRow).status"
                  :dict-type="DICT_TYPE.resultStatus"
                  :disabled="!canStatus || isRowLocked(row as EntryRow)"
                  @change="onStatusChange(row as EntryRow)"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="canStatus" label="犯规" width="150">
              <template #default="{ row }">
                <DictSelect
                  v-model="(row as EntryRow).foulDraft"
                  :dict-type="DICT_TYPE.foulType"
                  :clearable="true"
                  placeholder="犯规类型"
                  :disabled="isRowLocked(row as EntryRow) || !statusNeedsFoul(rowStatus(row as EntryRow))"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="canStatus" label="犯规/判罚说明" width="180">
              <template #default="{ row }">
                <el-input
                  v-model="(row as EntryRow).foulReason"
                  placeholder="DSQ 必填"
                  :disabled="isRowLocked(row as EntryRow) || !statusNeedsFoul(rowStatus(row as EntryRow))"
                />
              </template>
            </el-table-column>
            <el-table-column label="风速" width="110" align="center">
              <template #default="{ row }">
                <template v-if="typeof (row as EntryRow).windSpeed === 'number'">
                  <el-tag :type="(row as EntryRow).windLegal === 0 ? 'danger' : 'success'" size="small" effect="plain">
                    {{ (row as EntryRow).windSpeed }} m/s
                  </el-tag>
                  <div v-if="(row as EntryRow).windLegal === 0" class="entry-error">超风速</div>
                </template>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="名次" width="80" align="center">
              <template #default="{ row }">
                {{ (row as EntryRow).rankNo ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="标记" width="150">
              <template #default="{ row }">
                <el-tag v-if="(row as EntryRow).isRecordCandidate === 1" size="small" type="danger" effect="dark">
                  破纪录候选
                </el-tag>
                <el-tag
                  v-else-if="(row as EntryRow).isRecordConfirmed === 1"
                  size="small"
                  type="success"
                  effect="plain"
                >
                  纪录已认定
                </el-tag>
                <el-tag v-if="isRowLocked(row as EntryRow)" size="small" type="info" effect="plain">已锁定</el-tag>
                <el-tag
                  v-else-if="(row as EntryRow).editing || (row as EntryRow).inputRaw !== (row as EntryRow).savedRaw"
                  size="small"
                  type="warning"
                  effect="plain"
                >
                  未保存
                </el-tag>
                <span
                  v-if="
                    (row as EntryRow).isRecordCandidate !== 1 &&
                    (row as EntryRow).isRecordConfirmed !== 1 &&
                    !isRowLocked(row as EntryRow) &&
                    !(row as EntryRow).editing &&
                    (row as EntryRow).inputRaw === (row as EntryRow).savedRaw
                  "
                >
                  -
                </span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="190" fixed="right" align="center">
              <template #default="{ row }">
                <el-button
                  link
                  type="primary"
                  :loading="(row as EntryRow).saving"
                  :disabled="!isResultEditable(row as EntryRow)"
                  @click="saveRow(row as EntryRow)"
                >
                  保存
                </el-button>
                <el-button
                  v-if="canRecord && ((row as EntryRow).isRecordCandidate === 1 || (row as EntryRow).isRecordConfirmed === 1)"
                  link
                  type="danger"
                  :disabled="(row as EntryRow).isRecordConfirmed === 1"
                  @click="openRecordDialog(row as EntryRow)"
                >
                  认定
                </el-button>
                <el-button
                  link
                  :disabled="!canWind || typeof (row as EntryRow).resultId !== 'number'"
                  @click="
                    openWindDialog({
                      resultIds: [(row as EntryRow).resultId as number],
                      label: `${(row as EntryRow).athleteName || '该运动员'} 单条写入`
                    })
                  "
                >
                  风速
                </el-button>
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="该组暂无参赛行" />
            </template>
          </el-table>
        </div>
      </div>
    </template>

    <!-- 批量状态 -->
    <el-dialog v-model="statusDialogVisible" title="批量标记成绩状态" width="520px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="目标行数">
          <span>{{ selection.length }} 行</span>
        </el-form-item>
        <el-form-item label="成绩状态">
          <DictSelect v-model="statusForm.status" :dict-type="DICT_TYPE.resultStatus" />
        </el-form-item>
        <el-form-item v-if="statusForm.status === 'DSQ'" label="犯规类型">
          <DictSelect v-model="statusForm.foulType" :dict-type="DICT_TYPE.foulType" placeholder="请选择犯规类型" />
        </el-form-item>
        <el-form-item v-if="statusForm.status === 'DSQ'" label="犯规说明">
          <el-input v-model="statusForm.foulReason" type="textarea" :rows="3" placeholder="判罚依据说明（必填，200 字以内）" />
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        选择非 VALID 状态后，成绩会被清空（后端按状态处理）；取消资格（DSQ）必须选择犯规类型并填写说明。
      </div>
      <template #footer>
        <el-button @click="statusDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="statusSubmitting" @click="submitStatusDialog">确认标记</el-button>
      </template>
    </el-dialog>

    <!-- 风速 -->
    <el-dialog v-model="windDialogVisible" title="录入风速" width="480px" destroy-on-close>
      <el-form label-width="100px">
        <el-form-item label="作用范围">
          <span>{{ windTarget?.label || '-' }}</span>
        </el-form-item>
        <el-form-item label="风速(m/s)">
          <el-input-number v-model="windForm.windSpeed" :step="0.1" :precision="1" :min="-10" :max="10" controls-position="right" />
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        顺风为正、逆风为负；勾选行时按 resultIds 逐条写入，未勾选时按 heatId 整组写入。超风速（windLegal=0）会在行内标红提示。
      </div>
      <template #footer>
        <el-button @click="windDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="windSubmitting" @click="submitWindDialog">确认录入</el-button>
      </template>
    </el-dialog>

    <!-- 破纪录认定 -->
    <el-dialog v-model="recordDialogVisible" title="破纪录认定" width="480px" destroy-on-close>
      <el-form label-width="110px">
        <el-form-item label="运动员">
          <span>{{ recordTarget?.athleteName || '-' }}（{{ recordTarget?.unitName || '-' }}）</span>
        </el-form-item>
        <el-form-item label="成绩">
          <span>{{ recordTarget?.inputRaw || recordTarget?.resultRaw || '-' }}</span>
        </el-form-item>
        <el-form-item label="纪录层级">
          <el-select v-model="recordForm.recordType" placeholder="请选择纪录层级" style="width: 100%">
            <el-option v-for="item in recordTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="recordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="recordSubmitting" @click="submitRecordDialog">确认认定</el-button>
      </template>
    </el-dialog>

    <!-- 名次预览 -->
    <el-drawer v-model="rankDrawerVisible" title="名次预览" size="720px" destroy-on-close>
      <div v-loading="rankLoading">
        <div class="tm-toolbar" style="margin-bottom: 12px">
          <div class="tm-toolbar-actions">
            <span>{{ currentSchedule?.eventName || '-' }} / {{ currentSchedule?.roundName || currentSchedule?.roundCode || '-' }}</span>
          </div>
          <div class="tm-toolbar-actions">
            <el-button
              v-if="canApprove"
              type="primary"
              :loading="rankGenerating"
              :disabled="!canPreviewRank"
              @click="handleGenerateRank"
            >
              生成名次
            </el-button>
            <el-button :loading="rankLoading" @click="overviewRank">刷新</el-button>
          </div>
        </div>
        <el-alert
          v-for="(message, index) in rankMessages"
          :key="index"
          :title="message"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
        />
        <el-table :data="rankRows" border size="small" max-height="560">
          <el-table-column label="名次" width="80" align="center">
            <template #default="{ row }">
              {{ (row as RankRow).rankDisplay || (row as RankRow).rankNo || '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="bibNo" label="号码" width="80" align="center" />
          <el-table-column prop="athleteName" label="姓名" min-width="90" show-overflow-tooltip />
          <el-table-column prop="unitName" label="单位" min-width="120" show-overflow-tooltip />
          <el-table-column prop="resultRaw" label="成绩" width="110" align="center" />
          <el-table-column label="风速" width="100" align="center">
            <template #default="{ row }">
              {{ typeof (row as RankRow).windSpeed === 'number' ? `${(row as RankRow).windSpeed} m/s` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="备注" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ (row as RankRow).remark || (row as RankRow).status || '-' }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无排名数据，可先「生成名次」" />
          </template>
        </el-table>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.entry-error {
  color: #f56c6c;
  font-size: 12px;
  line-height: 1.4;
  margin-top: 2px;
}

.entry-attempt-panel {
  padding: 8px 12px 12px;
  background-color: #fafcff;
}
</style>
