<script setup lang="ts">
import { computed, getCurrentInstance, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import { Lock, Rank } from '@element-plus/icons-vue'
import {
  autoSchedule,
  deleteSchedule,
  detectConflicts,
  getGantt,
  listConflicts,
  listSessions,
  lockSchedule,
  moveSchedule,
  pageSchedule,
  publishSchedule,
  regenerateSchedule,
  resolveConflict,
  restoreSnapshot,
  saveSnapshot,
  updateSchedule,
  type AutoPlan,
  type AutoPlanItem,
  type AutoScheduleRequest,
  type Conflict,
  type GanttRow,
  type ScheduleSession,
  type ScheduleVO
} from '@/api/schedule'
import { pageEvents, type SportEvent } from '@/api/event'
import { listVenues, type Venue } from '@/api/meet'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 日程编排：筛选、拖拽调整、锁定/发布、自动编排向导、冲突面板、时间轴（甘特）视图、快照。
 * 权限编码：schedule:list / schedule:add / schedule:edit / schedule:delete / schedule:lock
 *          schedule:publish / schedule:auto / schedule:snapshot
 *          schedule:conflict:list / schedule:conflict:detect / schedule:conflict:resolve
 */
defineOptions({ name: 'SchedulePlan' })

type ScopeType = 'ALL' | 'SESSION' | 'GROUP' | 'EVENT'

/** 列表行：附带行级唯一 key，避免同一项目多轮次时 row-key 冲突 */
interface ScheduleRow extends ScheduleVO {
  rowKey: string
}

interface SessionOption {
  value: number
  label: string
}

const meetStore = useMeetStore()
const userStore = useUserStore()
const instance = getCurrentInstance()

const loading = ref(false)
const ganttLoading = ref(false)

/* ------------------------------- 筛选与列表 ------------------------------- */

const query = reactive({
  page: 1,
  size: 20,
  sessionId: undefined as number | undefined,
  venueId: undefined as number | undefined,
  eventId: undefined as number | undefined,
  date: '',
  publishStatus: '',
  keyword: ''
})

const rows = ref<ScheduleRow[]>([])
const total = ref(0)
const selectedRows = ref<ScheduleVO[]>([])
/** 仅看冲突：后端 ScheduleQuery 无对应字段，故在前端对当前页做过滤展示 */
const conflictOnly = ref(false)

const sessionList = ref<ScheduleSession[]>([])
const venues = ref<Venue[]>([])
const events = ref<SportEvent[]>([])

const meetReady = computed(() => !!meetStore.currentMeetId)

const PERIOD_LABELS: Record<string, string> = {
  MORNING: '上午',
  AFTERNOON: '下午',
  EVENING: '晚上'
}

const PUBLISH_OPTIONS = [
  { value: '', label: '全部' },
  { value: 'DRAFT', label: '草稿' },
  { value: 'PUBLISHED', label: '已发布' }
]

const SCOPE_OPTIONS = [
  { value: 'ALL', label: '全部（整届次重排）' },
  { value: 'SESSION', label: '按竞赛单元' },
  { value: 'GROUP', label: '按组别' },
  { value: 'EVENT', label: '按项目' }
] as const

const CONFLICT_TYPE_META: Record<string, { label: string; type: 'danger' | 'warning' | 'primary' | 'info' }> = {
  ATHLETE: { label: '兼项', type: 'danger' },
  VENUE: { label: '场地', type: 'warning' },
  REFEREE: { label: '裁判', type: 'primary' },
  INTERVAL: { label: '间隔不足', type: 'info' }
}

const sessionOptions = computed<SessionOption[]>(() =>
  sessionList.value.map((item) => ({
    value: item.id as number,
    label: item.sessionDate ? `${item.sessionDate} ${item.sessionName}` : item.sessionName
  }))
)

const filteredRows = computed(() => (conflictOnly.value ? rows.value.filter((row) => hasConflict(row)) : rows.value))

const sessionLabel = computed(() => {
  const hit = sessionOptions.value.find((item) => item.value === query.sessionId)
  return hit?.label || '-'
})

function periodLabel(period?: string): string {
  if (!period) {
    return '-'
  }
  return PERIOD_LABELS[period] || period
}

function publishType(status?: string): 'success' | 'info' | 'warning' {
  if (status === 'PUBLISHED') {
    return 'success'
  }
  if (status === 'DRAFT') {
    return 'info'
  }
  return 'warning'
}

function publishLabel(status?: string): string {
  if (status === 'PUBLISHED') {
    return '已发布'
  }
  if (status === 'DRAFT') {
    return '草稿'
  }
  return status || '-'
}

function venueNameById(venueId?: number): string {
  if (!venueId) {
    return '-'
  }
  const hit = venues.value.find((item) => item.id === venueId)
  return hit?.venueName || String(venueId)
}

function sessionNameById(sessionId?: number): string {
  if (!sessionId) {
    return '-'
  }
  const hit = sessionOptions.value.find((item) => item.value === sessionId)
  return hit?.label || String(sessionId)
}

/** 单元时段：自动编排计划项只带 sessionId，需从单元列表反查时段做展示 */
function sessionPeriod(sessionId?: number): string | undefined {
  if (!sessionId) {
    return undefined
  }
  return sessionList.value.find((item) => item.id === sessionId)?.period
}

function roundText(row: ScheduleVO): string {
  return row.roundName || row.roundCode || '-'
}

function hasConflict(row: ScheduleVO): boolean {
  return row.conflictFlag === 1 || (row.conflictCount ?? 0) > 0
}

function conflictText(row: ScheduleVO): string {
  const count = row.conflictCount ?? 0
  if (count > 0) {
    return String(count)
  }
  return row.conflictFlag === 1 ? '!' : ''
}

async function loadOptions() {
  const meetId = meetStore.currentMeetId
  if (!meetId) {
    sessionList.value = []
    venues.value = []
    events.value = []
    return
  }
  const [sessions, venueList, eventPage] = await Promise.allSettled([
    listSessions(meetId),
    listVenues({ meetId }),
    pageEvents({ page: 1, size: 200, meetId })
  ])
  sessionList.value = sessions.status === 'fulfilled' ? sessions.value?.records || [] : []
  venues.value = venueList.status === 'fulfilled' ? venueList.value || [] : []
  events.value = eventPage.status === 'fulfilled' ? eventPage.value?.records || [] : []
}

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageSchedule({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      sessionId: query.sessionId,
      venueId: query.venueId,
      eventId: query.eventId,
      date: query.date || undefined,
      publishStatus: query.publishStatus || undefined,
      keyword: query.keyword.trim() || undefined
    })
    rows.value = (page?.records || []).map((record) => ({
      ...record,
      rowKey: `${record.id ?? 'tmp'}-${record.roundId ?? 0}-${record.eventId ?? 0}`
    }))
    total.value = page?.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  query.page = 1
  load()
}

function handleReset() {
  query.sessionId = undefined
  query.venueId = undefined
  query.eventId = undefined
  query.date = ''
  query.publishStatus = ''
  query.keyword = ''
  query.page = 1
  conflictOnly.value = false
  load()
}

function handleSizeChange(size: number) {
  query.size = size
  query.page = 1
  load()
}

function handlePageChange(page: number) {
  query.page = page
  load()
}

function handleSelectionChange(selection: ScheduleVO[]) {
  selectedRows.value = selection || []
}

function clearSelection() {
  const table = (instance?.proxy?.$refs as Record<string, unknown> | undefined)?.scheduleTable as
    | { clearSelection?: () => void }
    | undefined
  table?.clearSelection?.()
  selectedRows.value = []
}

/* ------------------------------- 拖拽调整 ------------------------------- */

const dragRowId = ref<number | undefined>(undefined)
const dragOverRowId = ref<number | undefined>(undefined)
const highlightRowId = ref<number | undefined>(undefined)

function isLocked(row: ScheduleVO): boolean {
  return row.isLocked === 1
}

function isRowDraggable(row: ScheduleVO): boolean {
  return !!row.id && !isLocked(row) && userStore.hasPerm('schedule:edit')
}

function rowClassName({ row }: { row: ScheduleVO }): string {
  const target = row as ScheduleRow
  if (target.id && target.id === highlightRowId.value) {
    return 'tm-row-highlight'
  }
  if (target.id && target.id === dragOverRowId.value) {
    return 'tm-row-drag-over'
  }
  return ''
}

function handleDragStart(event: DragEvent, row: ScheduleVO) {
  if (!isRowDraggable(row)) {
    event.preventDefault()
    return
  }
  dragRowId.value = row.id
  dragOverRowId.value = undefined
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', String(row.id))
  }
}

function handleDragOver(event: DragEvent, row: ScheduleVO) {
  if (!dragRowId.value || !isRowDraggable(row) || dragRowId.value === row.id) {
    return
  }
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
  dragOverRowId.value = row.id
}

function handleDragLeave(row: ScheduleVO) {
  if (dragOverRowId.value === row.id) {
    dragOverRowId.value = undefined
  }
}

function handleDragEnd() {
  dragRowId.value = undefined
  dragOverRowId.value = undefined
}

/**
 * 拖拽落点：命中目标行后，把被拖日程移动到目标行所属单元、目标行的计划开始时间与场地。
 * 落点数据全部取自目标行（target.sessionId / target.planStartTime / target.venueId）。
 */
async function handleDrop(event: DragEvent, target: ScheduleVO) {
  event.preventDefault()
  const draggedId = dragRowId.value
  dragOverRowId.value = undefined
  dragRowId.value = undefined
  if (!draggedId || !target.id || draggedId === target.id) {
    return
  }
  if (isLocked(target)) {
    ElMessage.warning('目标日程已锁定，请先解锁')
    return
  }
  if (target.sessionId === undefined) {
    ElMessage.warning('目标日程缺少竞赛单元信息，无法作为落点')
    return
  }
  loading.value = true
  try {
    await moveSchedule(draggedId, {
      sessionId: target.sessionId,
      startTime: target.planStartTime,
      venueId: target.venueId,
      adjustReason: '列表拖拽调整'
    })
    ElMessage.success('已调整日程')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 移动 / 编辑 / 删除 ------------------------------- */

const moveVisible = ref(false)
const moveSubmitting = ref(false)
const moveTarget = ref<ScheduleVO | null>(null)
const moveForm = reactive({
  sessionId: undefined as number | undefined,
  startTime: '',
  venueId: undefined as number | undefined,
  adjustReason: '手工调整'
})

function handleMove(row: ScheduleVO) {
  if (!row.id) {
    return
  }
  moveTarget.value = row
  moveForm.sessionId = row.sessionId
  moveForm.startTime = row.planStartTime || ''
  moveForm.venueId = row.venueId
  moveForm.adjustReason = '手工调整'
  moveVisible.value = true
}

async function handleMoveSubmit() {
  const target = moveTarget.value
  if (!target?.id) {
    return
  }
  if (!moveForm.sessionId) {
    ElMessage.warning('请选择目标竞赛单元')
    return
  }
  if (!moveForm.startTime) {
    ElMessage.warning('请选择计划开始时间')
    return
  }
  moveSubmitting.value = true
  try {
    await moveSchedule(target.id, {
      sessionId: moveForm.sessionId,
      startTime: moveForm.startTime,
      venueId: moveForm.venueId,
      adjustReason: moveForm.adjustReason.trim() || '手工调整'
    })
    ElMessage.success('已调整日程')
    moveVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    moveSubmitting.value = false
  }
}

const editVisible = ref(false)
const editSubmitting = ref(false)
const editForm = reactive({
  id: undefined as number | undefined,
  sessionId: undefined as number | undefined,
  venueId: undefined as number | undefined,
  planStartTime: '',
  planEndTime: '',
  heatCount: undefined as number | undefined,
  durationMin: undefined as number | undefined,
  orderNo: undefined as number | undefined,
  adjustReason: '',
  remark: ''
})

function handleEdit(row: ScheduleVO) {
  if (!row.id) {
    return
  }
  editForm.id = row.id
  editForm.sessionId = row.sessionId
  editForm.venueId = row.venueId
  editForm.planStartTime = row.planStartTime || ''
  editForm.planEndTime = row.planEndTime || ''
  editForm.heatCount = row.heatCount
  editForm.durationMin = row.durationMin
  editForm.orderNo = row.orderNo
  editForm.adjustReason = row.adjustReason || ''
  editForm.remark = row.remark || ''
  editVisible.value = true
}

async function handleEditSubmit() {
  if (!editForm.id) {
    return
  }
  if (!editForm.sessionId) {
    ElMessage.warning('请选择竞赛单元')
    return
  }
  editSubmitting.value = true
  try {
    await updateSchedule(editForm.id, {
      sessionId: editForm.sessionId,
      venueId: editForm.venueId,
      planStartTime: editForm.planStartTime || undefined,
      planEndTime: editForm.planEndTime || undefined,
      heatCount: editForm.heatCount,
      durationMin: editForm.durationMin,
      orderNo: editForm.orderNo,
      adjustReason: editForm.adjustReason.trim() || undefined,
      remark: editForm.remark.trim() || undefined
    })
    ElMessage.success('日程已更新')
    editVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    editSubmitting.value = false
  }
}

async function handleDelete(row: ScheduleVO) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.eventName || '该'} ${roundText(row)}」的日程安排吗？`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteSchedule(row.id)
    ElMessage.success('日程已删除')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 锁定 / 发布 ------------------------------- */

async function handleLockChange(row: ScheduleVO, value: string | number | boolean) {
  if (!row.id) {
    return
  }
  const locked = value === true || value === 1 || value === '1'
  try {
    await lockSchedule(row.id, locked)
    row.isLocked = locked ? 1 : 0
    ElMessage.success(locked ? '日程已锁定' : '日程已解锁')
  } catch {
    // 错误提示由 request 拦截器统一处理
  }
}

async function handlePublish(row: ScheduleVO) {
  if (!row.id) {
    return
  }
  const next = row.publishStatus === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED'
  const verb = next === 'PUBLISHED' ? '发布' : '撤回'
  try {
    await ElMessageBox.confirm(`确认${verb}「${row.eventName || '该'} ${roundText(row)}」的日程吗？`, `${verb}确认`, {
      type: 'warning',
      confirmButtonText: `确认${verb}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await publishSchedule(row.id, next)
    ElMessage.success(next === 'PUBLISHED' ? '日程已发布' : '日程已撤回为草稿')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 批量锁定：对已勾选行循环调用 lockSchedule（锁定状态与目标值一致的行跳过） */
async function handleBatchLock(locked: boolean) {
  const targets = selectedRows.value.filter((row) => !!row.id && (row.isLocked === 1) !== locked)
  if (!targets.length) {
    ElMessage.warning(locked ? '所选日程均已锁定' : '所选日程均未锁定')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认批量${locked ? '锁定' : '解锁'} ${targets.length} 条日程吗？`,
      '批量操作确认',
      { type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await Promise.all(targets.map((row) => lockSchedule(row.id as number, locked)))
    ElMessage.success(`已${locked ? '锁定' : '解锁'} ${targets.length} 条日程`)
    clearSelection()
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/** 批量发布：对已勾选行循环调用 publishSchedule，状态与目标一致的行跳过 */
async function handleBatchPublish(status: 'PUBLISHED' | 'DRAFT') {
  const targets = selectedRows.value.filter((row) => !!row.id && row.publishStatus !== status)
  if (!targets.length) {
    ElMessage.warning(status === 'PUBLISHED' ? '所选日程均已发布' : '所选日程均为草稿')
    return
  }
  const verb = status === 'PUBLISHED' ? '发布' : '撤回'
  try {
    await ElMessageBox.confirm(`确认批量${verb} ${targets.length} 条日程吗？`, '批量操作确认', {
      type: 'warning',
      confirmButtonText: `确认${verb}`,
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await Promise.all(targets.map((row) => publishSchedule(row.id as number, status)))
    ElMessage.success(`已${verb} ${targets.length} 条日程`)
    clearSelection()
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 自动编排向导 ------------------------------- */

interface AutoForm {
  scope: ScopeType
  sessionIds: number[]
  groupIds: number[]
  eventIds: number[]
  keepLocked: boolean
  athleteIntervalMin: number
  parallelFieldEvents: boolean
}

const autoVisible = ref(false)
const autoStep = ref(0)
const autoLoading = ref(false)
const autoPlanLoading = ref(false)
const autoApplied = ref(false)
const autoPlan = ref<AutoPlan | null>(null)
const autoMergedConflicts = ref<Conflict[]>([])
const autoForm = reactive<AutoForm>({
  scope: 'ALL',
  sessionIds: [],
  groupIds: [],
  eventIds: [],
  keepLocked: true,
  athleteIntervalMin: 30,
  parallelFieldEvents: true
})

const autoScopeReady = computed(() => {
  if (autoForm.scope === 'SESSION') {
    return autoForm.sessionIds.length > 0
  }
  if (autoForm.scope === 'GROUP') {
    return autoForm.groupIds.length > 0
  }
  if (autoForm.scope === 'EVENT') {
    return autoForm.eventIds.length > 0
  }
  return true
})

function openAutoDialog() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  autoStep.value = 0
  autoPlan.value = null
  autoMergedConflicts.value = []
  autoApplied.value = false
  autoForm.scope = 'ALL'
  autoForm.sessionIds = []
  autoForm.groupIds = []
  autoForm.eventIds = []
  autoForm.keepLocked = true
  autoForm.athleteIntervalMin = 30
  autoForm.parallelFieldEvents = true
  autoVisible.value = true
}

/** 合并「本次计划返回的冲突」与「库中未处理冲突」，按 id/类型+日程 去重后展示 */
function applyAutoPlan(plan: AutoPlan | null) {
  autoPlan.value = plan
  const merged = new Map<string, Conflict>()
  const push = (item: Conflict) => {
    const key = item.id ? `id-${item.id}` : `${item.conflictType}-${item.scheduleId ?? 0}-${item.message ?? ''}`
    if (!merged.has(key)) {
      merged.set(key, item)
    }
  }
  ;(plan?.conflicts || []).forEach(push)
  conflicts.value.forEach(push)
  autoMergedConflicts.value = Array.from(merged.values())
}

function buildAutoRequest(apply: boolean): AutoScheduleRequest {
  const data: AutoScheduleRequest = {
    meetId: meetStore.currentMeetId,
    scope: autoForm.scope,
    keepLocked: autoForm.keepLocked,
    athleteIntervalMin: autoForm.athleteIntervalMin,
    parallelFieldEvents: autoForm.parallelFieldEvents,
    apply
  }
  if (autoForm.scope === 'SESSION') {
    data.sessionIds = [...autoForm.sessionIds]
  } else if (autoForm.scope === 'GROUP') {
    data.groupIds = [...autoForm.groupIds]
  } else if (autoForm.scope === 'EVENT') {
    data.eventIds = [...autoForm.eventIds]
  }
  return data
}

/** 预览：apply=false 只计算不落库，并汇总计划冲突与后端返回的已存冲突 */
async function handleAutoPreview() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!autoScopeReady.value) {
    ElMessage.warning('请先选择自动编排的范围对象')
    return
  }
  autoPlanLoading.value = true
  try {
    const plan = await autoSchedule(buildAutoRequest(false))
    await loadConflicts()
    applyAutoPlan(plan)
    autoStep.value = 1
    autoApplied.value = false
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    autoPlanLoading.value = false
  }
}

async function handleAutoApply() {
  if (!autoScopeReady.value) {
    ElMessage.warning('请先选择自动编排的范围对象')
    return
  }
  try {
    await ElMessageBox.confirm('应用编排将按预览结果写入日程，已锁定条目按参数保留，确认继续吗？', '应用编排确认', {
      type: 'warning',
      confirmButtonText: '确认应用',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  autoLoading.value = true
  try {
    const plan = await autoSchedule(buildAutoRequest(true))
    await load()
    await loadConflicts()
    applyAutoPlan(plan)
    autoApplied.value = true
    ElMessage.success(`已应用编排：新排 ${plan?.scheduledCount ?? 0} 条`)
    autoVisible.value = false
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    autoLoading.value = false
  }
}

/** 重新生成：regenerateSchedule 内部强制 apply=true，锁定条目保持不变 */
async function handleRegenerate() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!autoScopeReady.value) {
    ElMessage.warning('请先选择自动编排的范围对象')
    return
  }
  try {
    await ElMessageBox.confirm(
      '重新生成将覆盖当前范围下的非锁定日程，锁定条目将保持不变。确认继续吗？',
      '重新生成确认',
      { type: 'warning', confirmButtonText: '确认重新生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  autoLoading.value = true
  try {
    const plan = await regenerateSchedule(buildAutoRequest(true))
    await load()
    await loadConflicts()
    applyAutoPlan(plan)
    ElMessage.success(`已重新生成：新排 ${plan?.scheduledCount ?? 0} 条`)
    autoVisible.value = false
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    autoLoading.value = false
  }
}

/* ------------------------------- 冲突面板 ------------------------------- */

const conflictLoading = ref(false)
const detecting = ref(false)
const conflicts = ref<Conflict[]>([])
let conflictToastTimer: ReturnType<typeof setTimeout> | undefined

function conflictTypeMeta(type?: string) {
  if (!type) {
    return { label: '-', type: 'info' as const }
  }
  return CONFLICT_TYPE_META[type] || { label: type, type: 'info' as const }
}

function conflictLevelType(level?: string): 'danger' | 'warning' | 'info' {
  if (level === 'ERROR') {
    return 'danger'
  }
  if (level === 'WARN') {
    return 'warning'
  }
  return 'info'
}

async function loadConflicts() {
  if (!meetStore.currentMeetId) {
    conflicts.value = []
    return
  }
  conflictLoading.value = true
  try {
    const page = await listConflicts({ meetId: meetStore.currentMeetId, resolved: 0 })
    conflicts.value = page?.records || []
  } catch {
    conflicts.value = []
  } finally {
    conflictLoading.value = false
  }
}

async function refreshConflicts() {
  await loadConflicts()
  notifyConflictResult()
}

/** 检测完成后若有 ERROR 级冲突，额外用通知提醒（主流程错误仍由拦截器提示） */
function notifyConflictResult() {
  const errorCount = conflicts.value.filter((item) => item.level === 'ERROR').length
  if (conflictToastTimer) {
    clearTimeout(conflictToastTimer)
  }
  conflictToastTimer = setTimeout(() => {
    if (errorCount > 0) {
      ElNotification({
        title: '冲突检测完成',
        message: `发现 ${errorCount} 条阻断级（ERROR）冲突，请在冲突面板处理`,
        type: 'warning',
        duration: 5000
      })
    }
  }, 0)
}

async function handleDetect() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  detecting.value = true
  try {
    const result = await detectConflicts({ meetId: meetStore.currentMeetId })
    await refreshConflicts()
    // 检测接口若直接返回明细而列表接口暂不可用，则以返回值兜底展示
    if (Array.isArray(result) && result.length && !conflicts.value.length) {
      conflicts.value = result
    }
    await load()
    ElMessage.success('冲突检测已完成')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    detecting.value = false
  }
}

/** 定位：切到日程列表页签，滚动到对应行并高亮；不在当前页时给出提示（错误由拦截器提示） */
async function handleLocateConflict(item: Conflict) {
  if (!item.scheduleId) {
    ElMessage.warning('该冲突未关联具体日程条目，无法定位')
    return
  }
  activeTab.value = 'list'
  conflictOnly.value = false
  let hit = rows.value.find((row) => row.id === item.scheduleId)
  if (!hit) {
    query.page = 1
    await load()
    hit = rows.value.find((row) => row.id === item.scheduleId)
  }
  if (!hit) {
    ElMessage.warning('对应日程不在当前筛选条件的第一页，请调整筛选条件后重试')
    return
  }
  highlightRowId.value = hit.id
  await nextTick()
  const table = (instance?.proxy?.$refs as Record<string, unknown> | undefined)?.scheduleTable as
    | { toggleRowSelection?: (row: ScheduleVO, selected?: boolean) => void; setScrollTop?: (top: number) => void }
    | undefined
  table?.toggleRowSelection?.(hit, true)
  const element = document.querySelector('.tm-row-highlight') as HTMLElement | null
  element?.scrollIntoView({ block: 'center', behavior: 'smooth' })
  setTimeout(() => {
    if (highlightRowId.value === hit?.id) {
      highlightRowId.value = undefined
    }
  }, 4000)
}

async function handleResolveConflict(item: Conflict) {
  if (!item.id) {
    return
  }
  let remark = ''
  try {
    const result = await ElMessageBox.prompt('请输入冲突处理说明（如「已调整至下一单元」）', '处理冲突', {
      confirmButtonText: '确认处理',
      cancelButtonText: '取消',
      inputPlaceholder: '处理说明',
      inputValue: item.message || '',
      inputValidator: (value: string) => (value && value.trim() ? true : '请填写处理说明')
    })
    remark = result.value?.trim() || ''
  } catch {
    return
  }
  conflictLoading.value = true
  try {
    await resolveConflict(item.id, remark)
    ElMessage.success('冲突已处理')
    await loadConflicts()
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    conflictLoading.value = false
  }
}

/* ------------------------------- 时间轴（甘特）视图 ------------------------------- */

const activeTab = ref('list')
const ganttRows = ref<GanttRow[]>([])
/** 甘特视图数据在首次切换页签时按需加载 */
const ganttLoaded = ref(false)

interface GanttBar {
  key: string
  label: string
  style: Record<string, string>
  overflow: boolean
  title: string
}

interface GanttSession {
  key: string
  name: string
  date: string
  period: string
  startTime: string
  endTime: string
  bars: GanttBar[]
}

interface GanttGroup {
  key: string
  label: string
  sessions: GanttSession[]
}

function toMinutes(value?: string): number | null {
  if (!value) {
    return null
  }
  const match = /(\d{1,2}):(\d{2})(?::(\d{2}))?/.exec(value)
  if (!match) {
    return null
  }
  const hour = Number(match[1])
  const minute = Number(match[2])
  const second = match[3] ? Number(match[3]) : 0
  if (Number.isNaN(hour) || Number.isNaN(minute)) {
    return null
  }
  return hour * 60 + minute + second / 60
}

function buildBars(items: AutoPlanItem[], startMin: number | null, endMin: number | null): GanttBar[] {
  const windowMin = startMin !== null && endMin !== null && endMin > startMin ? endMin - startMin : 0
  return (items || []).map((item, index) => {
    const itemStart = toMinutes(item.startTime)
    const itemEnd = toMinutes(item.endTime)
    const overflow =
      windowMin > 0 &&
      itemStart !== null &&
      itemEnd !== null &&
      ((startMin !== null && itemStart < startMin) || (endMin !== null && itemEnd > endMin))
    let left = 0
    let width = 100
    if (windowMin > 0 && itemStart !== null && itemEnd !== null && startMin !== null) {
      left = Math.max(0, Math.min(100, ((itemStart - startMin) / windowMin) * 100))
      const right = Math.max(0, Math.min(100, ((itemEnd - startMin) / windowMin) * 100))
      width = Math.max(right - left, 2)
    }
    const name = item.eventName || `日程 ${item.eventId ?? index + 1}`
    return {
      key: `${item.eventId ?? index}-${item.roundCode ?? index}-${index}`,
      label: name,
      style: {
        left: `${left.toFixed(2)}%`,
        width: `${width.toFixed(2)}%`,
        overflow: overflow ? 'visible' : 'hidden'
      },
      overflow,
      title: `${name} ${item.roundCode || ''} ${item.startTime || '-'}~${item.endTime || '-'}${
        overflow ? '（超出单元时间）' : ''
      }`
    }
  })
}

const ganttGroups = computed<GanttGroup[]>(() => {
  const groupMap = new Map<string, GanttGroup>()
  ganttRows.value.forEach((row, rowIndex) => {
    const rowKey = `${row.sessionId ?? 's'}-${rowIndex}`
    const venue = row.venueName || venueNameById(row.venueId)
    const groupKey = row.venueId ? `venue-${row.venueId}` : `venue-none-${venue}`
    if (!groupMap.has(groupKey)) {
      groupMap.set(groupKey, { key: groupKey, label: venue, sessions: [] })
    }
    const startMin = toMinutes(row.items?.[0]?.startTime)
    const endMin = toMinutes(row.items?.[0]?.endTime)
    groupMap.get(groupKey)?.sessions.push({
      key: rowKey,
      name: row.sessionName || '未命名单元',
      date: row.sessionDate || '-',
      period: periodLabel(row.period),
      startTime: row.items?.[0]?.startTime || '-',
      endTime: row.items?.[0]?.endTime || '-',
      bars: buildBars(row.items || [], startMin, endMin)
    })
  })
  return Array.from(groupMap.values())
})

async function loadGantt() {
  if (!meetStore.currentMeetId) {
    ganttRows.value = []
    return
  }
  ganttLoading.value = true
  try {
    ganttRows.value = (await getGantt({ meetId: meetStore.currentMeetId })) || []
    ganttLoaded.value = true
  } catch {
    ganttRows.value = []
  } finally {
    ganttLoading.value = false
  }
}

function handleTabChange(name: string | number) {
  if (name === 'gantt' && !ganttLoaded.value && !ganttLoading.value) {
    loadGantt()
  }
}

/* ------------------------------- 快照 ------------------------------- */

interface SnapshotItem {
  id: number
  name: string
  time: string
}

/**
 * 快照列表：src/api/schedule.ts 只提供 saveSnapshot / restoreSnapshot，没有快照列表接口，
 * 因此这里在本地维护最近 5 条「本次会话内保存过的快照」，用于快速恢复；刷新页面后需重新保存。
 */
const recentSnapshots = ref<SnapshotItem[]>([])
const snapshotVisible = ref(false)
const snapshotLoading = ref(false)

async function handleSaveSnapshot() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  let snapshotName = ''
  try {
    const result = await ElMessageBox.prompt('请输入快照名称（如「自动编排 v1」）', '保存快照', {
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPlaceholder: '快照名称',
      inputValue: `编排快照 ${new Date().toLocaleString()}`,
      inputValidator: (value: string) => (value && value.trim() ? true : '请填写快照名称')
    })
    snapshotName = result.value?.trim() || ''
  } catch {
    return
  }
  snapshotLoading.value = true
  try {
    const id = await saveSnapshot({ meetId: meetStore.currentMeetId, snapshotName })
    if (typeof id === 'number') {
      recentSnapshots.value.unshift({ id, name: snapshotName, time: new Date().toLocaleString() })
      recentSnapshots.value = recentSnapshots.value.slice(0, 5)
    }
    ElMessage.success('快照已保存')
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    snapshotLoading.value = false
  }
}

function openSnapshotDialog() {
  snapshotVisible.value = true
}

async function handleRestoreSnapshot(item: SnapshotItem) {
  try {
    await ElMessageBox.confirm(
      `确认恢复到快照「${item.name}」吗？当前日程将被该快照覆盖，已锁定的日程请自行确认。`,
      '恢复快照确认',
      { type: 'warning', confirmButtonText: '确认恢复', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  snapshotLoading.value = true
  try {
    await restoreSnapshot(item.id)
    ElMessage.success('快照已恢复')
    snapshotVisible.value = false
    await load()
    await loadConflicts()
    ganttLoaded.value = false
    if (activeTab.value === 'gantt') {
      await loadGantt()
    }
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    snapshotLoading.value = false
  }
}

/* ------------------------------- 初始化 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  await loadOptions()
  await Promise.all([load(), loadConflicts()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">日程编排</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ total }} 条日程｜未处理冲突
        {{ conflicts.length }} 条
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" @tab-change="handleTabChange">
      <el-tab-pane label="日程列表" name="list">
        <!-- 筛选栏 -->
        <div class="tm-search-bar" style="margin-bottom: 12px">
          <el-select v-model="query.sessionId" placeholder="竞赛单元" clearable filterable style="width: 220px">
            <el-option v-for="item in sessionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-select v-model="query.venueId" placeholder="场地" clearable style="width: 160px">
            <el-option v-for="item in venues" :key="item.id" :label="item.venueName" :value="item.id" />
          </el-select>
          <el-select v-model="query.eventId" placeholder="项目" clearable filterable style="width: 180px">
            <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id" />
          </el-select>
          <el-date-picker
            v-model="query.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="日期"
            clearable
            style="width: 160px"
          />
          <el-select v-model="query.publishStatus" placeholder="发布状态" style="width: 140px">
            <el-option v-for="item in PUBLISH_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input
            v-model="query.keyword"
            placeholder="项目 / 单元 / 备注"
            clearable
            style="width: 180px"
            @keyup.enter="handleSearch"
          />
          <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>

        <div class="tm-toolbar" style="margin-bottom: 12px">
          <div class="tm-toolbar-actions">
            <el-button
              v-if="userStore.hasPerm('schedule:auto')"
              type="primary"
              :disabled="!meetReady"
              @click="openAutoDialog"
            >
              自动编排
            </el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:lock')"
              :disabled="!selectedRows.length"
              @click="handleBatchLock(true)"
            >
              批量锁定
            </el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:lock')"
              :disabled="!selectedRows.length"
              @click="handleBatchLock(false)"
            >
              批量解锁
            </el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:publish')"
              type="success"
              plain
              :disabled="!selectedRows.length"
              @click="handleBatchPublish('PUBLISHED')"
            >
              批量发布
            </el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:publish')"
              plain
              :disabled="!selectedRows.length"
              @click="handleBatchPublish('DRAFT')"
            >
              批量撤回
            </el-button>
          </div>
          <div class="tm-toolbar-actions">
            <el-checkbox v-model="conflictOnly" label="仅看冲突" />
            <el-button v-if="userStore.hasPerm('schedule:snapshot')" :loading="snapshotLoading" @click="handleSaveSnapshot">
              保存快照
            </el-button>
            <el-button v-if="userStore.hasPerm('schedule:snapshot')" @click="openSnapshotDialog">恢复快照</el-button>
            <el-button :loading="loading" @click="load">刷新</el-button>
          </div>
        </div>

        <div v-if="!meetReady" class="tm-form-tip" style="margin-bottom: 8px">请先在顶栏选择届次</div>
        <div class="tm-form-tip" style="margin-bottom: 8px">
          拖拽行可调整日程：拖到目标行即移动到该行的竞赛单元、开始时间与场地；已锁定行不可拖拽，请先解锁。
        </div>

        <el-table
          ref="scheduleTable"
          v-loading="loading"
          :data="filteredRows"
          row-key="rowKey"
          border
          stripe
          max-height="560"
          :row-class-name="rowClassName"
          @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="46" align="center" />
          <!-- 行级拖拽：el-table 不支持把 draggable 直接透传到 tr，因此在行内放置拖拽手柄，
               由手柄承载原生 dragstart/dragover/drop，落点行为仍以「目标行」为语义 -->
          <el-table-column label="拖拽" width="60" align="center">
            <template #default="{ row }">
              <span
                :draggable="isRowDraggable(row as ScheduleVO)"
                :title="isLocked(row as ScheduleVO) ? '已锁定，请先解锁' : '按住拖到目标行可调整日程'"
                style="cursor: grab"
                @dragstart="handleDragStart($event, row as ScheduleVO)"
                @dragover="handleDragOver($event, row as ScheduleVO)"
                @dragleave="handleDragLeave(row as ScheduleVO)"
                @drop="handleDrop($event, row as ScheduleVO)"
                @dragend="handleDragEnd"
              >
                <el-icon><Rank /></el-icon>
              </span>
            </template>
          </el-table-column>
          <el-table-column prop="sessionDate" label="日期" width="112">
            <template #default="{ row }">{{ (row as ScheduleVO).sessionDate || '-' }}</template>
          </el-table-column>
          <el-table-column label="时段" width="80" align="center">
            <template #default="{ row }">{{ periodLabel((row as ScheduleVO).period) }}</template>
          </el-table-column>
          <el-table-column label="开始-结束" width="190" align="center">
            <template #default="{ row }">
              {{ (row as ScheduleVO).planStartTime || '-' }} ~ {{ (row as ScheduleVO).planEndTime || '-' }}
            </template>
          </el-table-column>
          <el-table-column label="场地" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ (row as ScheduleVO).venueName || venueNameById((row as ScheduleVO).venueId) }}</template>
          </el-table-column>
          <el-table-column label="项目" min-width="150" show-overflow-tooltip>
            <template #default="{ row }">{{ (row as ScheduleVO).eventName || '-' }}</template>
          </el-table-column>
          <el-table-column label="轮次" width="110" show-overflow-tooltip>
            <template #default="{ row }">{{ roundText(row as ScheduleVO) }}</template>
          </el-table-column>
          <el-table-column label="组数" width="70" align="center">
            <template #default="{ row }">{{ (row as ScheduleVO).heatCount ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="预计时长" width="90" align="center">
            <template #default="{ row }">
              {{ (row as ScheduleVO).durationMin !== undefined && (row as ScheduleVO).durationMin !== null ? `${(row as ScheduleVO).durationMin} 分钟` : '-' }}
            </template>
          </el-table-column>
          <el-table-column label="锁定" width="110" align="center">
            <template #default="{ row }">
              <el-tooltip v-if="isLocked(row as ScheduleVO)" content="已锁定，请先解锁" placement="top">
                <el-icon style="color: #e6a23c; margin-right: 4px; vertical-align: middle"><Lock /></el-icon>
              </el-tooltip>
              <el-switch
                :model-value="(row as ScheduleVO).isLocked ?? 0"
                :active-value="1"
                :inactive-value="0"
                :disabled="!userStore.hasPerm('schedule:lock')"
                @change="(value: string | number | boolean) => handleLockChange(row as ScheduleVO, value)"
              />
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="150" align="center">
            <template #default="{ row }">
              <el-tag :type="publishType((row as ScheduleVO).publishStatus)" size="small">
                {{ publishLabel((row as ScheduleVO).publishStatus) }}
              </el-tag>
              <el-button
                v-if="userStore.hasPerm('schedule:publish')"
                link
                type="primary"
                @click="handlePublish(row as ScheduleVO)"
              >
                {{ (row as ScheduleVO).publishStatus === 'PUBLISHED' ? '撤回' : '发布' }}
              </el-button>
            </template>
          </el-table-column>
          <el-table-column label="冲突" width="90" align="center">
            <template #default="{ row }">
              <el-badge
                v-if="hasConflict(row as ScheduleVO)"
                :value="conflictText(row as ScheduleVO)"
                type="danger"
                class="tm-conflict-badge"
              >
                <el-button link type="danger" @click="conflictOnly = true">冲突</el-button>
              </el-badge>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="190" fixed="right" align="center">
            <template #default="{ row }">
              <el-button
                v-if="userStore.hasPerm('schedule:edit')"
                link
                type="primary"
                @click="handleEdit(row as ScheduleVO)"
              >
                编辑
              </el-button>
              <el-button
                v-if="userStore.hasPerm('schedule:edit')"
                link
                type="primary"
                @click="handleMove(row as ScheduleVO)"
              >
                移动
              </el-button>
              <el-button
                v-if="userStore.hasPerm('schedule:delete')"
                link
                type="danger"
                @click="handleDelete(row as ScheduleVO)"
              >
                删除
              </el-button>
            </template>
          </el-table-column>
          <template #empty>
            <el-empty description="暂无日程数据，可先执行自动编排" />
          </template>
        </el-table>

        <div class="tm-pagination">
          <el-pagination
            :current-page="query.page"
            :page-size="query.size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="时间轴视图" name="gantt" lazy>
        <div class="tm-toolbar" style="margin-bottom: 12px">
          <div class="tm-toolbar-actions">
            <el-button :loading="ganttLoading" @click="loadGantt">刷新时间轴</el-button>
          </div>
          <div class="tm-form-tip">时间条按单元起止时刻换算百分比宽度；红色边框表示超出单元时间窗。</div>
        </div>

        <div v-loading="ganttLoading">
          <template v-if="ganttGroups.length">
            <div v-for="group in ganttGroups" :key="group.key" class="tm-gantt-group">
              <div class="tm-gantt-group-title">场地：{{ group.label }}</div>
              <div v-for="session in group.sessions" :key="session.key" class="tm-gantt-row">
                <div class="tm-gantt-label">
                  <div class="tm-gantt-label-name">{{ session.name }}</div>
                  <div class="tm-form-tip">
                    {{ session.date }} {{ session.period }}｜{{ session.startTime }}~{{ session.endTime }}
                  </div>
                </div>
                <div class="tm-gantt-track">
                  <div
                    v-for="bar in session.bars"
                    :key="bar.key"
                    class="tm-gantt-bar"
                    :class="{ 'is-overflow': bar.overflow }"
                    :style="bar.style"
                    :title="bar.title"
                  >
                    <span class="tm-gantt-bar-text">{{ bar.label }}</span>
                  </div>
                  <div v-if="!session.bars.length" class="tm-gantt-empty">该单元暂无日程</div>
                </div>
              </div>
            </div>
          </template>
          <el-empty v-else description="暂无时间轴数据，请先执行自动编排或手工添加日程" />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 冲突面板 -->
    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <span class="tm-page-title">冲突面板</span>
          <el-tag v-if="conflicts.length" type="danger" size="small">{{ conflicts.length }} 条未处理</el-tag>
          <el-tag v-else type="success" size="small">无未处理冲突</el-tag>
        </div>
        <div class="tm-toolbar-actions">
          <el-button
            v-if="userStore.hasPerm('schedule:conflict:detect')"
            type="primary"
            plain
            :loading="detecting"
            :disabled="!meetReady"
            @click="handleDetect"
          >
            执行冲突检测
          </el-button>
          <el-button :loading="conflictLoading" @click="loadConflicts">刷新冲突</el-button>
        </div>
      </div>

      <el-table v-loading="conflictLoading" :data="conflicts" border size="small" max-height="320">
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="conflictTypeMeta((row as Conflict).conflictType).type" size="small" effect="plain">
              {{ conflictTypeMeta((row as Conflict).conflictType).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="级别" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="conflictLevelType((row as Conflict).level)" size="small">
              {{ (row as Conflict).level || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="说明" min-width="260" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as Conflict).message || '-' }}
            <span v-if="(row as Conflict).athleteName" class="tm-form-tip">
              （{{ (row as Conflict).athleteName }}）
            </span>
          </template>
        </el-table-column>
        <el-table-column label="关联日程" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="tm-form-tip">
              日程 #{{ (row as Conflict).scheduleId ?? '-' }}
              <template v-if="(row as Conflict).relatedScheduleId">｜关联 #{{ (row as Conflict).relatedScheduleId }}</template>
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="(row as Conflict).resolved === 1 ? 'success' : 'warning'" size="small">
              {{ (row as Conflict).resolved === 1 ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleLocateConflict(row as Conflict)">定位</el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:conflict:resolve')"
              link
              type="warning"
              @click="handleResolveConflict(row as Conflict)"
            >
              处理
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无未处理冲突" />
        </template>
      </el-table>
    </div>

    <!-- 自动编排向导 -->
    <el-dialog v-model="autoVisible" title="自动编排向导" width="900px" destroy-on-close top="6vh">
      <el-steps :active="autoStep" simple style="margin-bottom: 16px">
        <el-step title="设置编排参数" />
        <el-step title="预览与应用" />
      </el-steps>

      <template v-if="autoStep === 0">
        <el-form :model="autoForm" label-width="130px">
          <el-row :gutter="12">
            <el-col :span="12">
              <el-form-item label="编排范围">
                <el-select v-model="autoForm.scope" placeholder="请选择范围" style="width: 100%">
                  <el-option v-for="item in SCOPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="兼项最小间隔">
                <el-input-number
                  v-model="autoForm.athleteIntervalMin"
                  :min="0"
                  :max="240"
                  :step="5"
                  style="width: 100%"
                />
              </el-form-item>
            </el-col>
            <el-col v-if="autoForm.scope === 'SESSION'" :span="24">
              <el-form-item label="竞赛单元" required>
                <el-select
                  v-model="autoForm.sessionIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择参与编排的竞赛单元"
                  style="width: 100%"
                >
                  <el-option v-for="item in sessionOptions" :key="item.value" :label="item.label" :value="item.value" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="autoForm.scope === 'GROUP'" :span="24">
              <el-form-item label="组别" required>
                <el-select
                  v-model="autoForm.groupIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择参与编排的组别"
                  style="width: 100%"
                >
                  <el-option
                    v-for="item in meetStore.groupOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                  />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col v-if="autoForm.scope === 'EVENT'" :span="24">
              <el-form-item label="项目" required>
                <el-select
                  v-model="autoForm.eventIds"
                  multiple
                  filterable
                  collapse-tags
                  collapse-tags-tooltip
                  placeholder="请选择参与编排的项目"
                  style="width: 100%"
                >
                  <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="保留已锁定项">
                <el-switch v-model="autoForm.keepLocked" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="田赛并行">
                <el-switch v-model="autoForm.parallelFieldEvents" />
              </el-form-item>
            </el-col>
          </el-row>
        </el-form>
        <div class="tm-form-tip">
          兼项最小间隔为同一运动员两场比赛之间的最小间隔分钟数；田赛并行允许同一时段多场地同时进行田赛项目；开启「保留已锁定项」后锁定条目不会被重排。
        </div>
      </template>

      <template v-else>
        <div class="tm-stat-grid" style="margin-bottom: 12px">
          <div class="tm-stat-card">
            <div class="tm-stat-label">可排入日程</div>
            <div class="tm-stat-value">{{ autoPlan?.scheduledCount ?? 0 }}</div>
          </div>
          <div class="tm-stat-card">
            <div class="tm-stat-label">无法排入日程</div>
            <div class="tm-stat-value">{{ autoPlan?.unscheduledCount ?? 0 }}</div>
          </div>
          <div class="tm-stat-card">
            <div class="tm-stat-label">计划明细</div>
            <div class="tm-stat-value">{{ autoPlan?.items?.length ?? 0 }}</div>
          </div>
          <div class="tm-stat-card">
            <div class="tm-stat-label">冲突提示</div>
            <div class="tm-stat-value">{{ autoMergedConflicts.length }}</div>
          </div>
        </div>

        <el-alert
          v-for="(message, index) in autoPlan?.shortageMessages || []"
          :key="`shortage-${index}`"
          :title="message"
          type="warning"
          :closable="false"
          show-icon
          style="margin-bottom: 8px"
        />

        <div class="tm-page-title" style="margin: 12px 0 8px">计划明细</div>
        <el-table :data="autoPlan?.items || []" border size="small" max-height="260">
          <el-table-column prop="date" label="日期" width="110" />
          <el-table-column label="时段" width="80" align="center">
            <template #default="{ row }">{{ periodLabel(sessionPeriod((row as AutoPlanItem).sessionId)) }}</template>
          </el-table-column>
          <el-table-column label="开始" width="90" align="center">
            <template #default="{ row }">{{ (row as AutoPlanItem).startTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="结束" width="90" align="center">
            <template #default="{ row }">{{ (row as AutoPlanItem).endTime || '-' }}</template>
          </el-table-column>
          <el-table-column label="场地" min-width="120" show-overflow-tooltip>
            <template #default="{ row }">{{ venueNameById((row as AutoPlanItem).venueId) }}</template>
          </el-table-column>
          <el-table-column label="项目" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ (row as AutoPlanItem).eventName || '-' }}</template>
          </el-table-column>
          <el-table-column label="轮次" width="100" align="center">
            <template #default="{ row }">{{ (row as AutoPlanItem).roundCode || '-' }}</template>
          </el-table-column>
          <el-table-column label="组数" width="70" align="center">
            <template #default="{ row }">{{ (row as AutoPlanItem).heatCount ?? '-' }}</template>
          </el-table-column>
          <el-table-column label="单元" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">{{ sessionNameById((row as AutoPlanItem).sessionId) }}</template>
          </el-table-column>
          <template #empty>
            <el-empty description="没有可排入的日程" />
          </template>
        </el-table>

        <div class="tm-page-title" style="margin: 12px 0 8px">预览冲突（{{ autoMergedConflicts.length }} 条）</div>
        <div class="tm-error-box">
          <el-table :data="autoMergedConflicts" border size="small" max-height="200">
            <el-table-column label="类型" width="110" align="center">
              <template #default="{ row }">
                <el-tag :type="conflictTypeMeta((row as Conflict).conflictType).type" size="small" effect="plain">
                  {{ conflictTypeMeta((row as Conflict).conflictType).label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="conflictLevelType((row as Conflict).level)" size="small">
                  {{ (row as Conflict).level || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="说明" min-width="280" show-overflow-tooltip>
              <template #default="{ row }">{{ (row as Conflict).message || '-' }}</template>
            </el-table-column>
          </el-table>
        </div>
        <div v-if="autoApplied" class="tm-form-tip" style="margin-top: 8px">本次预览结果已应用。</div>
      </template>

      <template #footer>
        <el-button @click="autoVisible = false">关闭</el-button>
        <template v-if="autoStep === 0">
          <el-button
            v-if="userStore.hasPerm('schedule:auto')"
            :loading="autoLoading"
            @click="handleRegenerate"
          >
            重新生成
          </el-button>
          <el-button
            v-if="userStore.hasPerm('schedule:auto')"
            type="primary"
            :loading="autoPlanLoading"
            @click="handleAutoPreview"
          >
            预览编排
          </el-button>
        </template>
        <template v-else>
          <el-button @click="autoStep = 0">返回改参数</el-button>
          <el-button
            v-if="userStore.hasPerm('schedule:auto')"
            type="primary"
            :loading="autoLoading"
            @click="handleAutoApply"
          >
            应用编排
          </el-button>
        </template>
      </template>
    </el-dialog>

    <!-- 移动日程 -->
    <el-dialog v-model="moveVisible" title="移动日程" width="620px" destroy-on-close>
      <el-form :model="moveForm" label-width="110px">
        <el-form-item label="日程">
          <span>
            {{ moveTarget?.eventName || '-' }} {{ moveTarget ? roundText(moveTarget) : '' }}（当前：
            {{ sessionNameById(moveTarget?.sessionId) }}）
          </span>
        </el-form-item>
        <el-form-item label="目标单元" required>
          <el-select v-model="moveForm.sessionId" placeholder="请选择目标竞赛单元" filterable style="width: 100%">
            <el-option v-for="item in sessionOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始时间" required>
          <el-date-picker
            v-model="moveForm.startTime"
            type="datetime"
            value-format="YYYY-MM-DD HH:mm:ss"
            placeholder="请选择计划开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="场地">
          <el-select v-model="moveForm.venueId" placeholder="请选择场地" clearable style="width: 100%">
            <el-option v-for="item in venues" :key="item.id" :label="item.venueName" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="调整原因">
          <el-input v-model="moveForm.adjustReason" maxlength="100" placeholder="如：场地冲突调整" />
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">结束时间由后端按项目预计时长顺延；已锁定日程请先解锁再移动。</div>
      <template #footer>
        <el-button @click="moveVisible = false">取消</el-button>
        <el-button type="primary" :loading="moveSubmitting" @click="handleMoveSubmit">确认移动</el-button>
      </template>
    </el-dialog>

    <!-- 编辑日程 -->
    <el-dialog v-model="editVisible" title="编辑日程" width="680px" destroy-on-close>
      <el-form :model="editForm" label-width="110px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="竞赛单元" required>
              <el-select v-model="editForm.sessionId" placeholder="请选择竞赛单元" filterable style="width: 100%">
                <el-option v-for="item in sessionOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="场地">
              <el-select v-model="editForm.venueId" placeholder="请选择场地" clearable style="width: 100%">
                <el-option v-for="item in venues" :key="item.id" :label="item.venueName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划开始">
              <el-date-picker
                v-model="editForm.planStartTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="计划开始时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计划结束">
              <el-date-picker
                v-model="editForm.planEndTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="计划结束时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="组数">
              <el-input-number v-model="editForm.heatCount" :min="1" :max="99" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="预计时长">
              <el-input-number v-model="editForm.durationMin" :min="0" :max="1440" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="排序号">
              <el-input-number v-model="editForm.orderNo" :min="0" :max="9999" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="调整原因">
              <el-input v-model="editForm.adjustReason" maxlength="100" placeholder="如：裁判申请调整" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="editForm.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="handleEditSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 恢复快照 -->
    <el-dialog v-model="snapshotVisible" title="恢复快照" width="620px" destroy-on-close>
      <el-alert
        title="接口仅提供保存/恢复快照，无快照列表接口，此处仅列出本次会话保存过的最近 5 条快照。"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      />
      <el-table :data="recentSnapshots" border size="small" max-height="300">
        <el-table-column label="快照名称" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as SnapshotItem).name }}</template>
        </el-table-column>
        <el-table-column label="快照 ID" width="100" align="center">
          <template #default="{ row }">{{ (row as SnapshotItem).id }}</template>
        </el-table-column>
        <el-table-column label="保存时间" width="180" align="center">
          <template #default="{ row }">{{ (row as SnapshotItem).time }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleRestoreSnapshot(row as SnapshotItem)">恢复</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="本次会话尚未保存过快照" />
        </template>
      </el-table>
      <template #footer>
        <el-button @click="snapshotVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.tm-gantt-group {
  margin-bottom: 16px;
}

.tm-gantt-group-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #303133;
}

.tm-gantt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.tm-gantt-label {
  width: 220px;
  flex: 0 0 220px;
}

.tm-gantt-label-name {
  font-weight: 500;
}

.tm-gantt-track {
  position: relative;
  flex: 1;
  min-height: 40px;
  background-color: #f5f7fa;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  overflow: hidden;
}

.tm-gantt-bar {
  position: absolute;
  top: 6px;
  bottom: 6px;
  background-color: #409eff;
  color: #fff;
  border: 1px solid #409eff;
  border-radius: 3px;
  padding: 0 6px;
  font-size: 12px;
  line-height: 26px;
  white-space: nowrap;
  cursor: default;
}

.tm-gantt-bar.is-overflow {
  border-color: #f56c6c;
  box-shadow: 0 0 0 1px #f56c6c inset;
}

.tm-gantt-bar-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tm-gantt-empty {
  position: absolute;
  left: 8px;
  top: 10px;
  color: #909399;
  font-size: 12px;
}

.tm-conflict-badge {
  margin-top: 2px;
}

/* 拖拽落点高亮 / 冲突定位高亮：需穿透到 el-table 渲染出的 tr */
:deep(.el-table__row.tm-row-drag-over) > td {
  background-color: #ecf5ff !important;
  border-top: 2px dashed #409eff;
  border-bottom: 2px dashed #409eff;
}

:deep(.el-table__row.tm-row-highlight) > td {
  background-color: #fdf6ec !important;
}
</style>
