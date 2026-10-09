<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import DictSelect from '@/components/DictSelect.vue'
import { pageAthletes, type Athlete } from '@/api/athlete'
import { pageEvents, type SportEvent } from '@/api/event'
import {
  batchCreateRegistrations,
  createRegistration,
  deleteRegistration,
  lockRegistrations,
  pageRegistrations,
  updateRegistration,
  type Registration,
  type RegistrationVO
} from '@/api/registration'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 报名管理：条件查询、单条/批量报名、取消报名、锁定报名。
 * 权限编码：registration:list / registration:add / registration:edit / registration:delete / registration:lock
 */
defineOptions({ name: 'RegistrationList' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)
const batchSubmitting = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  keyword: '',
  eventId: undefined as number | undefined,
  unitId: undefined as number | undefined,
  groupId: undefined as number | undefined,
  status: '',
  isRelay: undefined as number | undefined
})

const rows = ref<RegistrationVO[]>([])
const total = ref(0)
const eventList = ref<SportEvent[]>([])
const athleteList = ref<Athlete[]>([])

const meetReady = computed(() => !!meetStore.currentMeetId)

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  PENDING: { label: '待审', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已退回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

const SEED_SOURCE_OPTIONS = [
  { value: 'MANUAL', label: '手工录入' },
  { value: 'PB', label: '个人最好（PB）' },
  { value: 'SB', label: '赛季最好（SB）' },
  { value: 'LAST_MEET', label: '上届成绩' },
  { value: 'CUSTOM', label: '自定义' }
]

function statusMeta(status?: string) {
  if (!status) {
    return { label: '-', type: 'info' as const }
  }
  return STATUS_META[status] || { label: status, type: 'info' as const }
}

function unitName(unitId?: number): string {
  if (!unitId) {
    return '-'
  }
  return meetStore.units.find((item) => item.id === unitId)?.unitName || String(unitId)
}

function groupName(groupId?: number): string {
  if (!groupId) {
    return '-'
  }
  return meetStore.groups.find((item) => item.id === groupId)?.groupName || String(groupId)
}

function eventName(eventId?: number): string {
  if (!eventId) {
    return '-'
  }
  return eventList.value.find((item) => item.id === eventId)?.eventName || String(eventId)
}

function athleteLabel(athlete: Athlete): string {
  const bib = athlete.bibNo ? `（${athlete.bibNo}）` : ''
  return `${athlete.athleteName}${bib}`
}

/* ------------------------------- 基础数据 ------------------------------- */

async function loadEvents() {
  if (!meetStore.currentMeetId) {
    eventList.value = []
    return
  }
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetStore.currentMeetId })
    eventList.value = page.records || []
  } catch {
    eventList.value = []
  }
}

async function loadAthletes(unitId?: number) {
  if (!meetStore.currentMeetId) {
    athleteList.value = []
    return
  }
  try {
    const page = await pageAthletes({
      page: 1,
      size: 200,
      meetId: meetStore.currentMeetId,
      unitId
    })
    athleteList.value = page.records || []
  } catch {
    athleteList.value = []
  }
}

/* ------------------------------- 列表查询 ------------------------------- */

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageRegistrations({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      keyword: query.keyword || undefined,
      eventId: query.eventId,
      unitId: query.unitId,
      groupId: query.groupId,
      status: query.status || undefined,
      isRelay: query.isRelay
    })
    rows.value = page.records || []
    total.value = page.total || 0
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
  query.page = 1
  query.keyword = ''
  query.eventId = undefined
  query.unitId = undefined
  query.groupId = undefined
  query.status = ''
  query.isRelay = undefined
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

/* ------------------------------- 新增 / 编辑报名 ------------------------------- */

interface RegistrationForm {
  id?: number
  eventId?: number
  unitId?: number
  athleteId?: number
  groupId?: number
  relayName: string
  selectedAthleteIds: number[]
  seedResult: string
  seedValue: number | undefined
  seedSource: string
  remark: string
}

const dialogVisible = ref(false)
const isEdit = ref(false)

const form = reactive<RegistrationForm>({
  id: undefined,
  eventId: undefined,
  unitId: undefined,
  athleteId: undefined,
  groupId: undefined,
  relayName: '',
  selectedAthleteIds: [],
  seedResult: '',
  seedValue: undefined,
  seedSource: 'MANUAL',
  remark: ''
})

const currentEvent = computed<SportEvent | undefined>(() =>
  eventList.value.find((item) => item.id === form.eventId)
)

const isRelayEvent = computed(() => currentEvent.value?.isRelay === 1)

const filteredAthletes = computed(() => {
  const event = currentEvent.value
  if (!event) {
    return athleteList.value
  }
  return athleteList.value.filter((athlete) => !event.gender || athlete.gender === event.gender)
})

function resetForm() {
  form.id = undefined
  form.eventId = undefined
  form.unitId = userStore.unitId ?? (meetStore.units.length === 1 ? meetStore.units[0].id : undefined)
  form.athleteId = undefined
  form.groupId = undefined
  form.relayName = ''
  form.selectedAthleteIds = []
  form.seedResult = ''
  form.seedValue = undefined
  form.seedSource = 'MANUAL'
  form.remark = ''
}

async function handleAdd() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  isEdit.value = false
  resetForm()
  if (!eventList.value.length) {
    await loadEvents()
  }
  if (!athleteList.value.length) {
    await loadAthletes(form.unitId)
  }
  dialogVisible.value = true
}

async function handleEdit(row: RegistrationVO) {
  if (!row.id) {
    return
  }
  isEdit.value = true
  resetForm()
  form.id = row.id
  form.eventId = row.eventId
  form.unitId = row.unitId
  form.groupId = row.groupId
  form.athleteId = row.athleteId
  form.relayName = row.relayName || ''
  form.selectedAthleteIds = (row.members || [])
    .map((member) => member.athleteId)
    .filter((id): id is number => typeof id === 'number')
  form.seedResult = row.seedResult || ''
  form.seedValue = row.seedValue
  form.seedSource = row.seedSource || 'MANUAL'
  form.remark = row.remark || ''
  if (!eventList.value.length) {
    await loadEvents()
  }
  await loadAthletes(row.unitId)
  dialogVisible.value = true
}

async function handleFormUnitChange(unitId?: number) {
  form.athleteId = undefined
  form.selectedAthleteIds = []
  await loadAthletes(unitId)
}

function handleFormEventChange(eventId?: number) {
  const event = eventList.value.find((item) => item.id === eventId)
  form.groupId = event?.groupId
  form.athleteId = undefined
  form.selectedAthleteIds = []
  form.relayName = ''
}

function validateForm(): string | null {
  if (!form.eventId) {
    return '请选择报名项目'
  }
  if (!form.unitId) {
    return '请选择参赛单位'
  }
  if (isRelayEvent.value) {
    if (!form.relayName.trim()) {
      return '请填写接力队名称'
    }
    const event = currentEvent.value
    const min = event?.relayMin ?? 2
    if (form.selectedAthleteIds.length < min) {
      return `接力项目至少需要 ${min} 名队员`
    }
    if (event?.relayMax && form.selectedAthleteIds.length > event.relayMax) {
      return `接力项目最多 ${event.relayMax} 名队员`
    }
  } else if (!form.athleteId) {
    return '请选择参赛运动员'
  }
  return null
}

async function handleSubmit() {
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  if (!meetStore.currentMeetId || !form.eventId || !form.unitId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  const payload: Registration = {
    meetId: meetStore.currentMeetId,
    eventId: form.eventId,
    unitId: form.unitId,
    groupId: form.groupId,
    isRelay: isRelayEvent.value ? 1 : 0,
    relayName: isRelayEvent.value ? form.relayName.trim() : undefined,
    athleteId: isRelayEvent.value ? undefined : form.athleteId,
    members: isRelayEvent.value
      ? form.selectedAthleteIds.map((athleteId, index) => ({ athleteId, legNo: index + 1 }))
      : undefined,
    seedResult: form.seedResult.trim() || undefined,
    seedValue: form.seedValue,
    seedSource: form.seedSource || undefined,
    remark: form.remark.trim() || undefined
  }
  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      await updateRegistration(form.id, payload)
      ElMessage.success('报名信息已更新')
    } else {
      await createRegistration(payload)
      ElMessage.success('报名已提交，等待审核')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleCancel(row: RegistrationVO) {
  if (!row.id) {
    return
  }
  const who = row.isRelay === 1 ? row.relayName || '接力队' : row.athleteName || '该运动员'
  try {
    await ElMessageBox.confirm(`确认取消「${who}」在「${row.eventName || eventName(row.eventId)}」的报名吗？`, '取消报名', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteRegistration(row.id)
    ElMessage.success('报名已取消')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 批量报名 ------------------------------- */

const batchVisible = ref(false)
const batchForm = reactive({
  unitId: undefined as number | undefined,
  eventId: undefined as number | undefined,
  athleteIds: [] as number[],
  remark: ''
})

async function openBatch() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  batchForm.unitId = userStore.unitId ?? undefined
  batchForm.eventId = undefined
  batchForm.athleteIds = []
  batchForm.remark = ''
  if (!eventList.value.length) {
    await loadEvents()
  }
  await loadAthletes(batchForm.unitId)
  batchVisible.value = true
}

const singleEvents = computed(() => eventList.value.filter((item) => item.isRelay !== 1))

async function handleBatchUnitChange(unitId?: number) {
  batchForm.athleteIds = []
  await loadAthletes(unitId)
}

async function handleBatchSubmit() {
  if (!batchForm.unitId) {
    ElMessage.warning('请选择参赛单位')
    return
  }
  if (!batchForm.eventId) {
    ElMessage.warning('请选择报名项目')
    return
  }
  if (!batchForm.athleteIds.length) {
    ElMessage.warning('请选择至少一名运动员')
    return
  }
  batchSubmitting.value = true
  try {
    await batchCreateRegistrations({
      meetId: meetStore.currentMeetId,
      unitId: batchForm.unitId,
      eventId: batchForm.eventId,
      athleteIds: batchForm.athleteIds,
      remark: batchForm.remark.trim() || undefined
    })
    ElMessage.success(`已提交 ${batchForm.athleteIds.length} 条报名`)
    batchVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    batchSubmitting.value = false
  }
}

/* ------------------------------- 锁定报名 ------------------------------- */

const locking = ref(false)

async function handleLock() {
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  try {
    await ElMessageBox.confirm('锁定后不可再报名，且不可新增/修改报名记录，确认锁定当前届次的报名吗？', '锁定报名', {
      type: 'warning',
      confirmButtonText: '确认锁定',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  locking.value = true
  try {
    await lockRegistrations(meetStore.currentMeetId)
    ElMessage.success('报名已锁定')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    locking.value = false
  }
}

/* ------------------------------- 初始化 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (!meetStore.units.length && meetStore.currentMeetId) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await Promise.all([loadEvents(), load()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">报名管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ total }} 条报名记录
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-input
          v-model="query.keyword"
          placeholder="姓名 / 号码 / 接力队名"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-select v-model="query.eventId" placeholder="报名项目" clearable filterable style="width: 200px">
          <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
        </el-select>
        <el-select v-model="query.unitId" placeholder="参赛单位" clearable style="width: 180px">
          <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.groupId" placeholder="组别" clearable style="width: 150px">
          <el-option v-for="item in meetStore.groupOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <DictSelect v-model="query.status" dict-type="registration_status" placeholder="报名状态" width="150px" />
        <el-select v-model="query.isRelay" placeholder="全部/单项/接力" clearable style="width: 160px">
          <el-option label="全部" :value="undefined" />
          <el-option label="单项" :value="0" />
          <el-option label="接力" :value="1" />
        </el-select>
        <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="userStore.hasPerm('registration:add')" type="primary" @click="handleAdd">新增报名</el-button>
          <el-button v-if="userStore.hasPerm('registration:add')" @click="openBatch">批量报名</el-button>
          <el-button
            v-if="userStore.hasPerm('registration:lock')"
            type="danger"
            plain
            :loading="locking"
            @click="handleLock"
          >
            锁定报名
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe max-height="560">
        <el-table-column label="项目" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ (row as RegistrationVO).eventName || eventName((row as RegistrationVO).eventId) }}</span>
            <el-tag v-if="(row as RegistrationVO).isRelay === 1" size="small" type="warning" effect="plain" style="margin-left: 6px">
              接力
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参赛人" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <template v-if="(row as RegistrationVO).isRelay === 1">
              <div>{{ (row as RegistrationVO).relayName || '-' }}</div>
              <div class="tm-page-desc">{{ (row as RegistrationVO).memberNames || '未登记队员' }}</div>
            </template>
            <template v-else>
              {{ (row as RegistrationVO).athleteName || '-' }}
              <span v-if="(row as RegistrationVO).bibNo">（{{ (row as RegistrationVO).bibNo }}）</span>
            </template>
          </template>
        </el-table-column>
        <el-table-column label="单位" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as RegistrationVO).unitName || unitName((row as RegistrationVO).unitId) }}
          </template>
        </el-table-column>
        <el-table-column label="组别" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as RegistrationVO).groupName || groupName((row as RegistrationVO).groupId) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta((row as RegistrationVO).status).type" size="small">
              {{ (row as RegistrationVO).statusName || statusMeta((row as RegistrationVO).status).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="seedResult" label="种子成绩" width="110" align="center" />
        <el-table-column prop="submitTime" label="提交时间" width="170" show-overflow-tooltip />
        <el-table-column prop="auditTime" label="审核时间" width="170" show-overflow-tooltip />
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPerm('registration:edit')"
              link
              type="primary"
              @click="handleEdit(row as RegistrationVO)"
            >
              编辑
            </el-button>
            <el-button
              v-if="userStore.hasPerm('registration:delete')"
              link
              type="danger"
              @click="handleCancel(row as RegistrationVO)"
            >
              取消报名
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无报名数据" />
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

    <!-- 新增 / 编辑报名 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑报名' : '新增报名'" width="720px" destroy-on-close>
      <el-form :model="form" label-width="110px">
        <el-form-item label="报名项目" required>
          <el-select
            v-model="form.eventId"
            placeholder="请选择报名项目"
            filterable
            style="width: 100%"
            @change="handleFormEventChange"
          >
            <el-option
              v-for="item in eventList"
              :key="item.id"
              :label="item.isRelay === 1 ? `${item.eventName}（接力）` : item.eventName"
              :value="item.id as number"
            />
          </el-select>
          <div v-if="currentEvent" class="tm-form-tip">
            项目类型：{{ currentEvent.isRelay === 1 ? '接力' : '单项' }}｜
            每单位限报人数：{{ currentEvent.maxPerUnit ?? '不限' }}
            <template v-if="currentEvent.isRelay === 1">
              ｜接力人数：{{ currentEvent.relayMin ?? 2 }} - {{ currentEvent.relayMax ?? 4 }} 人
            </template>
          </div>
        </el-form-item>
        <el-form-item label="参赛单位" required>
          <el-select
            v-model="form.unitId"
            placeholder="请选择参赛单位"
            style="width: 100%"
            @change="handleFormUnitChange"
          >
            <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>

        <template v-if="isRelayEvent">
          <el-form-item label="接力队名称" required>
            <el-input v-model="form.relayName" maxlength="100" placeholder="如：XX 单位 A 队" />
          </el-form-item>
          <el-form-item label="接力队员" required>
            <el-select
              v-model="form.selectedAthleteIds"
              multiple
              filterable
              placeholder="按棒次顺序选择队员"
              style="width: 100%"
            >
              <el-option
                v-for="item in filteredAthletes"
                :key="item.id"
                :label="athleteLabel(item)"
                :value="item.id as number"
              />
            </el-select>
            <div class="tm-form-tip">选择顺序即棒次顺序（第 1 个为第一棒），提交时自动写入棒次。</div>
          </el-form-item>
        </template>
        <el-form-item v-else label="参赛运动员" required>
          <el-select v-model="form.athleteId" filterable placeholder="请选择运动员" style="width: 100%">
            <el-option
              v-for="item in filteredAthletes"
              :key="item.id"
              :label="athleteLabel(item)"
              :value="item.id as number"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="种子成绩">
              <el-input v-model="form.seedResult" placeholder='如 11"23' />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成绩数值">
              <el-input-number v-model="form.seedValue" :min="0" :precision="2" :step="0.1" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="成绩来源">
              <el-select v-model="form.seedSource" placeholder="请选择" style="width: 100%">
                <el-option v-for="item in SEED_SOURCE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量报名 -->
    <el-dialog v-model="batchVisible" title="批量报名" width="640px" destroy-on-close>
      <el-form :model="batchForm" label-width="110px">
        <el-form-item label="参赛单位" required>
          <el-select
            v-model="batchForm.unitId"
            placeholder="请选择参赛单位"
            style="width: 100%"
            @change="handleBatchUnitChange"
          >
            <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="报名项目" required>
          <el-select v-model="batchForm.eventId" placeholder="仅支持单项批量报名" filterable style="width: 100%">
            <el-option v-for="item in singleEvents" :key="item.id" :label="item.eventName" :value="item.id as number" />
          </el-select>
        </el-form-item>
        <el-form-item label="运动员" required>
          <el-select
            v-model="batchForm.athleteIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            placeholder="可多选"
            style="width: 100%"
          >
            <el-option
              v-for="item in athleteList"
              :key="item.id"
              :label="athleteLabel(item)"
              :value="item.id as number"
            />
          </el-select>
          <div class="tm-form-tip">已选 {{ batchForm.athleteIds.length }} 人；接力项目请使用「新增报名」逐队提交。</div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" type="textarea" :rows="2" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="handleBatchSubmit">提交报名</el-button>
      </template>
    </el-dialog>
  </div>
</template>
