<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listRounds, pageEvents, type EventRound, type SportEvent } from '@/api/event'
import {
  approveResults,
  getResultAudits,
  pageResults,
  rejectResults,
  revokeApproval,
  type ResultAudit,
  type ResultQuery,
  type ResultVO
} from '@/api/result'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 成绩审核：筛选（项目/轮次/单位/审核状态/关键字）→ 批量通过（锁定并生成名次）/ 批量驳回 → 撤销审核与留痕对照。
 * 权限编码：result:list / result:approve / result:reject / result:revoke / result:audit:list
 */
defineOptions({ name: 'ResultAudit' })

/** 留痕前后值对照行 */
interface DiffRow {
  label: string
  before: string
  after: string
  changed: boolean
}

/** 需要参与前后对照的字段（含中文标签） */
const DIFF_FIELDS: { key: string; label: string }[] = [
  { key: 'resultRaw', label: '成绩原文' },
  { key: 'resultValue', label: '成绩数值' },
  { key: 'resultUnit', label: '单位' },
  { key: 'resultScore', label: '全能单项得分' },
  { key: 'status', label: '成绩状态' },
  { key: 'foulType', label: '犯规类型' },
  { key: 'foulReason', label: '犯规说明' },
  { key: 'windSpeed', label: '风速' },
  { key: 'rankNo', label: '名次' },
  { key: 'rankDisplay', label: '名次显示' },
  { key: 'auditStatus', label: '审核状态' },
  { key: 'rejectReason', label: '驳回原因' },
  { key: 'isLocked', label: '是否锁定' },
  { key: 'isRecordConfirmed', label: '纪录是否认定' },
  { key: 'remark', label: '备注' },
  { key: 'attempts', label: '试跳/试掷明细' }
]

const meetStore = useMeetStore()
const userStore = useUserStore()

/* ------------------------------- 权限 ------------------------------- */

const canList = computed(() => userStore.hasPerm('result:list'))
const canApprove = computed(() => userStore.hasPerm('result:approve'))
const canReject = computed(() => userStore.hasPerm('result:reject'))
const canRevoke = computed(() => userStore.hasPerm('result:revoke'))
const canAuditList = computed(() => userStore.hasPerm('result:audit:list'))

const meetReady = computed(() => !!meetStore.currentMeetId)

/* ------------------------------- 筛选 ------------------------------- */

const AUDIT_STATUS_META: Record<string, { label: string; type: 'info' | 'warning' | 'success' | 'danger' }> = {
  DRAFT: { label: '草稿', type: 'info' },
  SUBMITTED: { label: '待审核', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已驳回', type: 'danger' }
}

const AUDIT_STATUS_OPTIONS = [
  { label: '全部审核状态', value: '' },
  { label: '草稿（DRAFT）', value: 'DRAFT' },
  { label: '待审核（SUBMITTED）', value: 'SUBMITTED' },
  { label: '已通过（APPROVED）', value: 'APPROVED' },
  { label: '已驳回（REJECTED）', value: 'REJECTED' }
]

const EVENT_TYPE_LABELS: Record<string, string> = {
  TRACK: '径赛',
  FIELD: '田赛',
  RELAY: '接力',
  ALLROUND: '全能'
}

const RESULT_STATUS_LABELS: Record<string, string> = {
  VALID: '有效',
  DNS: '未出发',
  DNF: '未完成',
  DSQ: '取消资格',
  NM: '无有效成绩',
  PASS: '免跳',
  SCRATCH: '弃权'
}

const query = reactive({
  page: 1,
  size: 20,
  eventId: undefined as number | undefined,
  roundId: undefined as number | undefined,
  unitId: undefined as number | undefined,
  auditStatus: 'SUBMITTED' as string,
  keyword: ''
})

const rows = ref<ResultVO[]>([])
const total = ref(0)
const loading = ref(false)
const auditing = ref(false)

const eventList = ref<SportEvent[]>([])
const roundList = ref<EventRound[]>([])
const roundsLoading = ref(false)

const selectedRows = ref<ResultVO[]>([])

function auditMeta(status?: string) {
  if (!status) {
    return { label: '未提交', type: 'info' as const }
  }
  return AUDIT_STATUS_META[status] || { label: status, type: 'info' as const }
}

function statusText(row: ResultVO): string {
  if (row.statusName) {
    return row.statusName
  }
  if (!row.status) {
    return '有效'
  }
  return RESULT_STATUS_LABELS[row.status] || row.status
}

function statusTagType(status?: string): 'success' | 'info' | 'warning' | 'danger' {
  if (status === 'VALID') {
    return 'success'
  }
  if (status === 'DSQ') {
    return 'danger'
  }
  if (status === 'DNF') {
    return 'warning'
  }
  return 'info'
}

function eventTypeText(eventId?: number): string {
  const type = eventList.value.find((item) => item.id === eventId)?.eventType
  if (!type) {
    return ''
  }
  return EVENT_TYPE_LABELS[type] || type
}

function unitName(unitId?: number): string {
  if (!unitId) {
    return '-'
  }
  return meetStore.units.find((item) => item.id === unitId)?.unitName || String(unitId)
}

/** 仅「待审核（SUBMITTED）」且未锁定的行可勾选 */
function selectableRow(row: ResultVO): boolean {
  return row.auditStatus === 'SUBMITTED' && row.isLocked !== 1
}

function isSubmitted(row: ResultVO): boolean {
  return row.auditStatus === 'SUBMITTED'
}

function isApproved(row: ResultVO): boolean {
  return row.auditStatus === 'APPROVED'
}

/* ------------------------------- 数据加载 ------------------------------- */

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    eventList.value = []
    return
  }
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId })
    eventList.value = page?.records || []
  } catch {
    eventList.value = []
  }
}

async function loadRounds() {
  roundList.value = []
  query.roundId = undefined
  if (typeof query.eventId !== 'number') {
    return
  }
  roundsLoading.value = true
  try {
    roundList.value = (await listRounds(query.eventId)) || []
  } catch {
    roundList.value = []
  } finally {
    roundsLoading.value = false
  }
}

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  if (!canList.value) {
    rows.value = []
    total.value = 0
    ElMessage.warning('缺少「成绩查看」权限（result:list）')
    return
  }
  loading.value = true
  try {
    const params: ResultQuery = {
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      eventId: query.eventId,
      roundId: query.roundId,
      unitId: query.unitId,
      auditStatus: query.auditStatus || undefined,
      keyword: query.keyword.trim() || undefined
    }
    const page = await pageResults(params)
    rows.value = page?.records || []
    total.value = page?.total || 0
    selectedRows.value = []
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  query.page = 1
  load()
}

function handleReset() {
  query.page = 1
  query.eventId = undefined
  query.roundId = undefined
  query.unitId = undefined
  query.auditStatus = 'SUBMITTED'
  query.keyword = ''
  roundList.value = []
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

function onSelectionChange(list: ResultVO[]): void {
  selectedRows.value = list.filter((row) => selectableRow(row))
}

watch(
  () => query.eventId,
  async () => {
    await loadRounds()
    if (meetReady.value) {
      query.page = 1
      await load()
    }
  }
)

/* ------------------------------- 审核操作 ------------------------------- */

async function handleBatchApprove() {
  const ids = selectedRows.value.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning('请先勾选「待审核」的成绩行')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认通过选中的 ${ids.length} 条成绩吗？通过后将锁定成绩并自动生成名次。`,
      '批量通过',
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  auditing.value = true
  try {
    await approveResults(ids)
    ElMessage.success(`已通过 ${ids.length} 条成绩，名次已生成`)
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditing.value = false
  }
}

async function handleBatchReject() {
  const ids = selectedRows.value.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (ids.length === 0) {
    ElMessage.warning('请先勾选「待审核」的成绩行')
    return
  }
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(`请输入驳回原因（必填，200 字以内），将驳回选中的 ${ids.length} 条成绩`, '批量驳回', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：成绩与计时设备记录不一致 / 犯规判罚待复核',
      inputValidator: (value: string) => {
        const text = (value || '').trim()
        if (!text) {
          return '驳回原因不能为空'
        }
        if (text.length > 200) {
          return '驳回原因不能超过 200 字'
        }
        return true
      }
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  auditing.value = true
  try {
    await rejectResults(ids, reason)
    ElMessage.success(`已驳回 ${ids.length} 条成绩`)
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditing.value = false
  }
}

async function handleApprove(row: ResultVO) {
  if (typeof row.id !== 'number') {
    return
  }
  if (!isSubmitted(row)) {
    ElMessage.warning('仅「待审核」成绩可通过')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认通过「${row.athleteName || row.bibNo || '该运动员'}」的成绩（${row.resultRaw || '无成绩'}）吗？通过后将锁定成绩并自动生成名次。`,
      '通过成绩',
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  auditing.value = true
  try {
    await approveResults([row.id])
    ElMessage.success('成绩已通过审核')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditing.value = false
  }
}

async function handleReject(row: ResultVO) {
  if (typeof row.id !== 'number') {
    return
  }
  if (!isSubmitted(row)) {
    ElMessage.warning('仅「待审核」成绩可驳回')
    return
  }
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入驳回原因（必填，200 字以内）', '驳回成绩', {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：成绩抄录有误 / 需补充犯规说明',
      inputValidator: (value: string) => {
        const text = (value || '').trim()
        if (!text) {
          return '驳回原因不能为空'
        }
        if (text.length > 200) {
          return '驳回原因不能超过 200 字'
        }
        return true
      }
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  auditing.value = true
  try {
    await rejectResults([row.id], reason)
    ElMessage.success('成绩已驳回')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditing.value = false
  }
}

async function handleRevoke(row: ResultVO) {
  if (typeof row.id !== 'number') {
    return
  }
  if (!isApproved(row)) {
    ElMessage.warning('仅「已通过」成绩可撤销审核')
    return
  }
  let reason = ''
  try {
    const result = await ElMessageBox.prompt(
      `请输入撤销原因（必填，200 字以内）。撤销「${row.athleteName || row.bibNo || '该运动员'}」的审核后成绩将被解锁。`,
      '撤销审核',
      {
        confirmButtonText: '确认撤销',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputPlaceholder: '如：计时设备复核后需更正成绩',
        inputValidator: (value: string) => {
          const text = (value || '').trim()
          if (!text) {
            return '撤销原因不能为空'
          }
          if (text.length > 200) {
            return '撤销原因不能超过 200 字'
          }
          return true
        }
      }
    )
    reason = (result.value || '').trim()
  } catch {
    return
  }
  auditing.value = true
  try {
    await revokeApproval(row.id, reason)
    ElMessage.success('已撤销审核，成绩已解锁可修改')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    auditing.value = false
  }
}

/* ------------------------------- 留痕 ------------------------------- */

const drawerVisible = ref(false)
const auditLoading = ref(false)
const audits = ref<ResultAudit[]>([])
const auditTarget = ref<ResultVO | null>(null)

const ACTION_META: Record<string, { label: string; type: 'primary' | 'success' | 'danger' | 'warning' | 'info' }> = {
  SUBMIT: { label: '提交', type: 'primary' },
  APPROVE: { label: '审核通过', type: 'success' },
  REJECT: { label: '驳回', type: 'danger' },
  MODIFY: { label: '修改', type: 'warning' },
  REVOKE: { label: '撤销审核', type: 'info' }
}

function actionMeta(action?: string) {
  if (!action) {
    return { label: '-', type: 'info' as const }
  }
  return ACTION_META[action] || { label: action, type: 'info' as const }
}

function displayValue(value: unknown): string {
  if (value === undefined || value === null || value === '') {
    return '空'
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return '空'
    }
    return value
      .map((item, index) => {
        if (item && typeof item === 'object') {
          const record = item as Record<string, unknown>
          const no = record.attemptNo ?? index + 1
          const raw = record.attemptRaw ?? record.attemptValue ?? ''
          const marks: string[] = []
          if (record.isPass === 1) {
            marks.push('免跳')
          }
          if (record.isFoul === 1) {
            marks.push('犯规')
          }
          if (record.isSuccess === 1 && marks.length === 0) {
            marks.push('成功')
          }
          return `第${no}次 ${raw === '' ? '-' : String(raw)}${marks.length ? `（${marks.join('/')}）` : ''}`
        }
        return String(item)
      })
      .join('；')
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function flatten(source: Record<string, unknown>): Record<string, string> {
  const flat: Record<string, string> = {}
  Object.keys(source).forEach((key) => {
    const value = source[key]
    if (Array.isArray(value) || (value !== null && typeof value === 'object')) {
      flat[key] = displayValue(value)
      return
    }
    flat[key] = String(value ?? '')
  })
  return flat
}

function parseSnapshot(raw?: string): Record<string, unknown> | null {
  if (!raw) {
    return null
  }
  try {
    const parsed: unknown = JSON.parse(raw)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      return parsed as Record<string, unknown>
    }
    return null
  } catch {
    return null
  }
}

function diffRows(beforeRaw?: string, afterRaw?: string): DiffRow[] | null {
  const before = parseSnapshot(beforeRaw)
  const after = parseSnapshot(afterRaw)
  if (!before && !after) {
    return null
  }
  const beforeFlat = flatten(before || {})
  const afterFlat = flatten(after || {})
  const used = new Set<string>()
  const result: DiffRow[] = []
  DIFF_FIELDS.forEach((field) => {
    const label = field.label
    const beforeText = displayValue(beforeFlat[field.key] === '' ? undefined : beforeFlat[field.key])
    const afterText = displayValue(afterFlat[field.key] === '' ? undefined : afterFlat[field.key])
    if (beforeText === '空' && afterText === '空') {
      return
    }
    used.add(field.key)
    result.push({
      label,
      before: beforeText,
      after: afterText,
      changed: beforeText !== afterText
    })
  })
  const extras = new Set<string>([...Object.keys(beforeFlat), ...Object.keys(afterFlat)])
  extras.forEach((key) => {
    if (used.has(key) || key === 'id' || key === 'createTime' || key === 'updateTime') {
      return
    }
    const beforeText = displayValue(beforeFlat[key] === '' ? undefined : beforeFlat[key])
    const afterText = displayValue(afterFlat[key] === '' ? undefined : afterFlat[key])
    if (beforeText === '空' && afterText === '空') {
      return
    }
    result.push({ label: key, before: beforeText, after: afterText, changed: beforeText !== afterText })
  })
  return result
}

function hasRawSnapshot(audit: ResultAudit): boolean {
  return !!audit.beforeJson || !!audit.afterJson
}

function rawText(audit: ResultAudit): string {
  const before = audit.beforeJson || '空'
  const after = audit.afterJson || '空'
  return `变更前：\n${before}\n\n变更后：\n${after}`
}

async function openAudits(row: ResultVO) {
  if (typeof row.id !== 'number') {
    ElMessage.warning('该成绩尚未保存，暂无可查看的留痕')
    return
  }
  if (!canAuditList.value) {
    ElMessage.warning('缺少「成绩留痕查看」权限（result:audit:list）')
    return
  }
  auditTarget.value = row
  audits.value = []
  drawerVisible.value = true
  auditLoading.value = true
  try {
    audits.value = (await getResultAudits(row.id)) || []
  } catch {
    audits.value = []
  } finally {
    auditLoading.value = false
  }
}

/* ------------------------------- 初始化 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (meetStore.currentMeetId && !meetStore.units.length) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([loadEvents(), load()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">成绩审核</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜当前筛选共 {{ total }} 条成绩
      </div>
    </div>

    <el-alert
      v-if="!canList"
      title="当前账号缺少「成绩查看」权限（result:list），列表不可加载。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-alert
      title="审核流：草稿（DRAFT）→ 提交（SUBMITTED）→ 审核通过（APPROVED，锁定成绩并自动生成名次）/ 驳回（REJECTED，退回录入端修改）"
      type="info"
      :closable="false"
      show-icon
    />

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-select v-model="query.eventId" placeholder="项目" clearable filterable style="width: 200px">
          <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
        </el-select>
        <el-select
          v-model="query.roundId"
          :loading="roundsLoading"
          :disabled="typeof query.eventId !== 'number'"
          placeholder="轮次"
          clearable
          style="width: 160px"
        >
          <el-option
            v-for="item in roundList"
            :key="item.id"
            :label="item.roundName || item.roundCode"
            :value="item.id as number"
          />
        </el-select>
        <el-select v-model="query.unitId" placeholder="参赛单位" clearable filterable style="width: 170px">
          <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.auditStatus" placeholder="审核状态" style="width: 190px">
          <el-option v-for="item in AUDIT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="号码 / 姓名 / 单位关键字"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <div v-if="!meetReady" class="tm-form-tip" style="margin-top: 8px">请先在顶部选择届次。</div>
      <div v-else class="tm-form-tip" style="margin-top: 8px">
        仅「待审核（SUBMITTED）」的成绩行可勾选与审核；已通过的成绩需先撤销审核才能修改。
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button
            v-if="canApprove"
            type="primary"
            :disabled="!selectedRows.length"
            :loading="auditing"
            @click="handleBatchApprove"
          >
            批量通过{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
          <el-button
            v-if="canReject"
            type="danger"
            plain
            :disabled="!selectedRows.length"
            :loading="auditing"
            @click="handleBatchReject"
          >
            批量驳回{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="rows"
        border
        stripe
        max-height="580"
        row-key="id"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="selectableRow" />
        <el-table-column label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as ResultVO).eventName || '-' }}
            <el-tag v-if="eventTypeText((row as ResultVO).eventId)" size="small" effect="plain">
              {{ eventTypeText((row as ResultVO).eventId) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="轮次" width="100" align="center">
          <template #default="{ row }">
            {{ (row as ResultVO).roundName || (row as ResultVO).roundCode || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="组次/道次" width="110" align="center">
          <template #default="{ row }">
            {{ (row as ResultVO).heatNo ?? '-' }} / {{ (row as ResultVO).laneNo ?? (row as ResultVO).orderNo ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="bibNo" label="号码" width="80" align="center" />
        <el-table-column prop="athleteName" label="姓名" min-width="90" show-overflow-tooltip />
        <el-table-column label="单位" min-width="130" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as ResultVO).unitName || unitName((row as ResultVO).unitId) }}
          </template>
        </el-table-column>
        <el-table-column label="成绩" width="110" align="center">
          <template #default="{ row }">
            {{ (row as ResultVO).resultRaw || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="风速" width="100" align="center">
          <template #default="{ row }">
            <template v-if="typeof (row as ResultVO).windSpeed === 'number'">
              <span :class="{ 'audit-wind-illegal': (row as ResultVO).windLegal === 0 }">
                {{ (row as ResultVO).windSpeed }} m/s
              </span>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="名次" width="80" align="center">
          <template #default="{ row }">
            {{ (row as ResultVO).rankDisplay || (row as ResultVO).rankNo || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="成绩状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as ResultVO).status)" size="small">
              {{ statusText(row as ResultVO) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="审核状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="auditMeta((row as ResultVO).auditStatus).type" size="small">
              {{ (row as ResultVO).auditStatusName || auditMeta((row as ResultVO).auditStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="驳回原因" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as ResultVO).rejectReason || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="isSubmitted(row as ResultVO)">
              <el-button v-if="canApprove" link type="success" @click="handleApprove(row as ResultVO)">通过</el-button>
              <el-button v-if="canReject" link type="danger" @click="handleReject(row as ResultVO)">驳回</el-button>
            </template>
            <el-button
              v-if="canRevoke && isApproved(row as ResultVO)"
              link
              type="warning"
              @click="handleRevoke(row as ResultVO)"
            >
              撤销审核
            </el-button>
            <el-button link type="primary" @click="openAudits(row as ResultVO)">留痕</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无符合条件的成绩记录" />
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
    </div>

    <!-- 留痕抽屉 -->
    <el-drawer v-model="drawerVisible" title="成绩留痕" size="760px" destroy-on-close>
      <div v-loading="auditLoading">
        <div class="tm-page-desc" style="margin-bottom: 12px">
          {{ auditTarget?.athleteName || '-' }}（{{ auditTarget?.bibNo || '无号码' }}）｜
          {{ auditTarget?.eventName || '-' }} / {{ auditTarget?.roundName || auditTarget?.roundCode || '-' }}｜
          成绩 {{ auditTarget?.resultRaw || '-' }}
        </div>

        <el-empty v-if="audits.length === 0 && !auditLoading" description="暂无留痕记录" />

        <el-timeline v-else>
          <el-timeline-item
            v-for="item in audits"
            :key="item.id"
            :timestamp="item.operateTime || '-'"
            placement="top"
          >
            <div class="audit-item">
              <el-tag :type="actionMeta(item.action).type" size="small">{{ actionMeta(item.action).label }}</el-tag>
              <span class="audit-operator">操作人：{{ item.operatorName || item.operatorId || '-' }}</span>
            </div>
            <div v-if="item.reason" class="tm-form-tip" style="margin-top: 4px">原因：{{ item.reason }}</div>

            <template v-if="diffRows(item.beforeJson, item.afterJson)">
              <el-table :data="diffRows(item.beforeJson, item.afterJson) as DiffRow[]" border size="small" style="margin-top: 8px">
                <el-table-column prop="label" label="字段" width="140" />
                <el-table-column prop="before" label="变更前" min-width="200" show-overflow-tooltip />
                <el-table-column prop="after" label="变更后" min-width="200" show-overflow-tooltip />
                <el-table-column label="变化" width="70" align="center">
                  <template #default="scope">
                    <el-tag v-if="(scope.row as DiffRow).changed" size="small" type="warning">已改</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </template>
            <template v-else-if="hasRawSnapshot(item)">
              <el-alert
                title="快照非标准结构，未解析为对照表，以下为原始内容"
                type="warning"
                :closable="false"
                show-icon
                style="margin-top: 8px"
              />
              <el-input
                :model-value="rawText(item)"
                type="textarea"
                :rows="8"
                readonly
                style="margin-top: 8px"
              />
            </template>
            <div v-else class="tm-form-tip" style="margin-top: 4px">该次操作无前后快照。</div>
          </el-timeline-item>
        </el-timeline>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.audit-wind-illegal {
  color: #f56c6c;
  font-weight: 600;
}

.audit-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.audit-operator {
  color: #909399;
  font-size: 12px;
}
</style>
