<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  batchGenerateEvents,
  createEvent,
  deleteEvent,
  getEvent,
  pageEvents,
  updateEvent,
  type EventQuery,
  type SportEvent
} from '@/api/event'
import { DICT_TYPE } from '@/api/dict'
import DictSelect from '@/components/DictSelect.vue'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 项目管理：项目检索、新增/编辑、批量生成标准项目。
 */
defineOptions({ name: 'EventList' })

const router = useRouter()
const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)

/** 项目类型展示配置 */
const EVENT_TYPE_OPTIONS = [
  { label: '径赛', value: 'TRACK', tag: 'primary' },
  { label: '田赛', value: 'FIELD', tag: 'success' },
  { label: '全能', value: 'ALLROUND', tag: 'warning' },
  { label: '接力', value: 'RELAY', tag: 'danger' }
] as const

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

/** 项目子类型中文名 */
const SUB_TYPE_LABELS: Record<string, string> = {
  SPRINT: '短跑',
  MIDDLE: '中跑',
  LONG: '长跑',
  HURDLE: '跨栏',
  STEEPLE: '障碍赛',
  WALK: '竞走',
  JUMP: '跳跃',
  THROW: '投掷',
  RELAY: '接力'
}

/** 性别中文名 */
const GENDER_LABELS: Record<string, string> = {
  MALE: '男',
  FEMALE: '女',
  MIXED: '混合'
}

/** 批量生成可用的标准项目模板（与后端 EventBatchGenerateDTO 约定一致） */
const EVENT_TEMPLATES = ['100米', '200米', '400米', '800米', '1500米', '跳远', '跳高', '铅球', '4×100米接力']

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' }
]

// ---------------- 列表 ----------------
const loading = ref(false)
const tableData = ref<SportEvent[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const query = reactive<EventQuery>({
  keyword: '',
  groupId: undefined,
  eventType: '',
  gender: '',
  status: ''
})

function eventTypeLabel(value?: string): string {
  return EVENT_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

function eventTypeTag(value?: string): TagType {
  return (EVENT_TYPE_OPTIONS.find((item) => item.value === value)?.tag || 'info') as TagType
}

function subTypeLabel(value?: string): string {
  if (!value) {
    return '-'
  }
  return SUB_TYPE_LABELS[value] || value
}

function genderLabel(value?: string): string {
  if (!value) {
    return '-'
  }
  return GENDER_LABELS[value] || value
}

function buildQuery(): EventQuery {
  const params: EventQuery = {
    page: pagination.page,
    size: pagination.size,
    meetId: meetId.value
  }
  const keyword = query.keyword?.trim()
  if (keyword) {
    params.keyword = keyword
  }
  if (query.groupId !== undefined && query.groupId !== null) {
    params.groupId = query.groupId
  }
  if (query.eventType) {
    params.eventType = query.eventType
  }
  if (query.gender) {
    params.gender = query.gender
  }
  if (query.status) {
    params.status = query.status
  }
  return params
}

async function loadData() {
  if (!meetId.value) {
    tableData.value = []
    pagination.total = 0
    return
  }
  loading.value = true
  try {
    const result = await pageEvents(buildQuery())
    tableData.value = result?.records || []
    pagination.total = result?.total || 0
  } catch {
    // 错误提示由响应拦截器统一处理
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pagination.page = 1
  loadData()
}

function handleReset() {
  query.keyword = ''
  query.groupId = undefined
  query.eventType = ''
  query.gender = ''
  query.status = ''
  pagination.page = 1
  loadData()
}

function handlePageChange(page: number) {
  pagination.page = page
  loadData()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  loadData()
}

function requireMeet(): boolean {
  if (!meetId.value) {
    ElMessage.warning('请先在顶部选择届次')
    return false
  }
  return true
}

// ---------------- 新增 / 编辑 ----------------
const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const editingId = ref<number | undefined>(undefined)
const submitting = ref(false)
const formRef = ref<FormInstance>()

function createEmptyForm(): SportEvent {
  return {
    meetId: meetId.value,
    eventName: '',
    eventCode: '',
    eventType: 'TRACK',
    subType: undefined,
    groupId: undefined,
    gender: 'MALE',
    isRelay: 0,
    relayMin: 4,
    relayMax: 4,
    maxPerUnit: 3,
    durationMin: 30,
    windApplicable: 0,
    isTeamScore: 1,
    roundCount: 1,
    sortNo: 0,
    status: 'ENABLED',
    remark: ''
  }
}

const form = reactive<SportEvent>(createEmptyForm())

const formRules: FormRules = {
  eventName: [{ required: true, message: '请输入项目名称', trigger: 'blur' }],
  eventCode: [{ required: true, message: '请输入项目编码', trigger: 'blur' }],
  eventType: [{ required: true, message: '请选择项目类型', trigger: 'change' }],
  groupId: [{ required: true, message: '请选择组别', trigger: 'change' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }]
}

/** 子类型字典：田赛用 field_sub_type，其余用 track_sub_type */
const subTypeDict = computed(() => (form.eventType === 'FIELD' ? DICT_TYPE.fieldSubType : DICT_TYPE.trackSubType))

watch(
  () => form.eventType,
  (value, oldValue) => {
    if (dialogVisible.value && oldValue !== undefined && value !== oldValue) {
      form.subType = undefined
    }
  }
)

function resetForm() {
  Object.assign(form, createEmptyForm())
  formRef.value?.clearValidate()
}

function handleAdd() {
  if (!requireMeet()) {
    return
  }
  dialogMode.value = 'add'
  editingId.value = undefined
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: SportEvent) {
  if (!requireMeet() || !row.id) {
    return
  }
  dialogMode.value = 'edit'
  editingId.value = row.id
  resetForm()
  let source: SportEvent = { ...row }
  try {
    const detail = await getEvent(row.id)
    if (detail?.event) {
      source = { ...row, ...detail.event }
    }
  } catch {
    // 详情读取失败时回退为列表行数据（拦截器已提示）
  }
  Object.assign(form, createEmptyForm(), source, {
    meetId: meetId.value,
    eventName: source.eventName || '',
    eventCode: source.eventCode || '',
    eventType: source.eventType || 'TRACK',
    gender: source.gender || 'MALE',
    // 列表/详情视图未返回的可选字段显式取原值，取不到时保持 undefined，提交时跳过，避免误清空
    windApplicable: source.windApplicable,
    isTeamScore: source.isTeamScore,
    remark: source.remark
  })
  dialogVisible.value = true
}

/** 组装提交体：未从后端取到的可选属性不提交，避免误清空 */
function buildPayload(): SportEvent {
  const payload: SportEvent = {
    meetId: meetId.value,
    eventName: form.eventName?.trim() || '',
    eventCode: form.eventCode?.trim() || '',
    eventType: form.eventType,
    groupId: form.groupId,
    gender: form.gender,
    isRelay: form.isRelay ?? 0,
    maxPerUnit: form.maxPerUnit,
    durationMin: form.durationMin,
    roundCount: form.roundCount ?? 1,
    sortNo: form.sortNo,
    status: form.status
  }
  if (form.subType) {
    payload.subType = form.subType
  }
  if (form.isRelay === 1) {
    payload.relayMin = form.relayMin ?? 4
    payload.relayMax = form.relayMax ?? 4
  }
  if (form.windApplicable !== undefined) {
    payload.windApplicable = form.windApplicable
  }
  if (form.isTeamScore !== undefined) {
    payload.isTeamScore = form.isTeamScore
  }
  if (form.remark !== undefined) {
    payload.remark = form.remark
  }
  return payload
}

async function handleSubmit() {
  if (!formRef.value || !requireMeet()) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (form.isRelay === 1 && (form.relayMin ?? 4) > (form.relayMax ?? 4)) {
    ElMessage.warning('接力人数下限不能大于上限')
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await createEvent(buildPayload())
      ElMessage.success('新增项目成功')
    } else if (editingId.value) {
      await updateEvent(editingId.value, buildPayload())
      ElMessage.success('保存项目成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: SportEvent) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除项目「${row.eventName}」吗？其轮次与规则配置将一并删除。`,
      '删除确认',
      { type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteEvent(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

function handleRounds(row: SportEvent) {
  if (!row.id) {
    return
  }
  router.push({ path: '/event/rounds', query: { eventId: String(row.id) } })
}

// ---------------- 批量生成 ----------------
const batchVisible = ref(false)
const batchSubmitting = ref(false)
const batchFormRef = ref<FormInstance>()
const batchResult = ref<{ createdCount: number; skippedCount: number; skippedNames: string[] } | null>(null)
const batchForm = reactive<{ groupIds: number[]; gender: string; templates: string[] }>({
  groupIds: [],
  gender: 'MALE',
  templates: []
})

const batchRules: FormRules = {
  groupIds: [{ required: true, type: 'array', message: '请至少选择一个组别', trigger: 'change' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  templates: [{ required: true, type: 'array', message: '请至少选择一个项目模板', trigger: 'change' }]
}

function handleBatchOpen() {
  if (!requireMeet()) {
    return
  }
  batchForm.groupIds = []
  batchForm.gender = 'MALE'
  batchForm.templates = []
  batchResult.value = null
  batchVisible.value = true
}

async function handleBatchSubmit() {
  if (!batchFormRef.value || !requireMeet()) {
    return
  }
  try {
    await batchFormRef.value.validate()
  } catch {
    return
  }
  batchSubmitting.value = true
  try {
    const result = await batchGenerateEvents({
      meetId: meetId.value,
      groupIds: [...batchForm.groupIds],
      gender: batchForm.gender,
      templates: [...batchForm.templates]
    })
    batchResult.value = {
      createdCount: result?.createdCount ?? 0,
      skippedCount: result?.skippedCount ?? 0,
      skippedNames: result?.skippedNames || []
    }
    ElMessage.success('批量生成完成')
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    batchSubmitting.value = false
  }
}

// ---------------- 生命周期 ----------------
onMounted(async () => {
  if (meetId.value && meetStore.groups.length === 0) {
    try {
      await meetStore.loadGroupsAndUnits()
    } catch {
      // 组别加载失败时仍允许查询（组别下拉为空）
    }
  }
  await loadData()
})

watch(meetId, async () => {
  pagination.page = 1
  await loadData()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">项目管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜维护比赛项目、赛制、限报与计分属性
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-input v-model="query.keyword" placeholder="项目名称/编码" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <el-select v-model="query.groupId" placeholder="组别" clearable style="width: 160px">
          <el-option v-for="item in meetStore.groupOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <DictSelect v-model="query.eventType" :dict-type="DICT_TYPE.eventType" placeholder="项目类型" width="150px" />
        <DictSelect v-model="query.gender" :dict-type="DICT_TYPE.gender" placeholder="性别" width="130px" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="userStore.hasPerm('event:add')" type="primary" :disabled="!meetId" @click="handleAdd">新增项目</el-button>
          <el-button v-if="userStore.hasPerm('event:batch')" type="success" plain :disabled="!meetId" @click="handleBatchOpen">
            批量生成标准项目
          </el-button>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :disabled="!meetId" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column prop="eventName" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="eventCode" label="项目编码" min-width="130" show-overflow-tooltip />
        <el-table-column label="类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="eventTypeTag(row.eventType)" effect="light">{{ eventTypeLabel(row.eventType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="子类型" width="100" align="center">
          <template #default="{ row }">{{ subTypeLabel(row.subType) }}</template>
        </el-table-column>
        <el-table-column label="组别" min-width="120" show-overflow-tooltip>
          <template #default="{ row }">{{ row.groupName || '-' }}</template>
        </el-table-column>
        <el-table-column label="性别" width="80" align="center">
          <template #default="{ row }">{{ genderLabel(row.gender) }}</template>
        </el-table-column>
        <el-table-column label="赛制" width="90" align="center">
          <template #default="{ row }">{{ row.isRelay === 1 ? '接力' : '单项' }}</template>
        </el-table-column>
        <el-table-column prop="maxPerUnit" label="每单位限报" width="110" align="center" />
        <el-table-column prop="durationMin" label="预计时长" width="100" align="center" />
        <el-table-column prop="roundCount" label="轮次数" width="90" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'" effect="light">
              {{ row.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('event:edit')" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="userStore.hasPerm('event:round:list')" link type="primary" @click="handleRounds(row)">轮次管理</el-button>
            <el-button v-if="userStore.hasPerm('event:delete')" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="meetId ? '暂无项目数据，可新增或批量生成' : '请先在顶部选择届次'" />
        </template>
      </el-table>

      <div class="tm-pagination">
        <el-pagination
          :current-page="pagination.page"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </div>

    <!-- 新增 / 编辑项目 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增项目' : '编辑项目'"
      width="780px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="项目名称" prop="eventName">
              <el-input v-model="form.eventName" maxlength="100" placeholder="如：男子100米" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目编码" prop="eventCode">
              <el-input v-model="form.eventCode" maxlength="64" placeholder="届次内唯一，如：M100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目类型" prop="eventType">
              <DictSelect v-model="form.eventType" :dict-type="DICT_TYPE.eventType" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="项目子类型">
              <DictSelect v-model="form.subType" :dict-type="subTypeDict" placeholder="按项目类型选择" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="组别" prop="groupId">
              <el-select v-model="form.groupId" placeholder="请选择组别" clearable style="width: 100%">
                <el-option v-for="item in meetStore.groupOptions" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="性别" prop="gender">
              <DictSelect v-model="form.gender" :dict-type="DICT_TYPE.gender" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否接力">
              <el-switch v-model="form.isRelay" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col v-if="form.isRelay === 1" :span="12">
            <el-form-item label="接力人数区间">
              <el-input-number v-model="form.relayMin" :min="1" :max="20" controls-position="right" style="width: 110px" />
              <span style="margin: 0 6px">至</span>
              <el-input-number v-model="form.relayMax" :min="1" :max="20" controls-position="right" style="width: 110px" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="每单位限报">
              <el-input-number v-model="form.maxPerUnit" :min="0" :max="999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预计时长(分)">
              <el-input-number v-model="form.durationMin" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="是否计风速">
              <el-switch v-model="form.windApplicable" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="计入团体分">
              <el-switch v-model="form.isTeamScore" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮次数">
              <el-input-number v-model="form.roundCount" :min="1" :max="10" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="100" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>
        <div class="tm-form-tip">
          接力项目请填写接力人数区间（默认 4 至 4）；是否计风速用于径赛超风速判定，是否计入团体分影响团体总分统计。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量生成标准项目 -->
    <el-dialog v-model="batchVisible" title="批量生成标准项目" width="680px" destroy-on-close>
      <el-alert
        v-if="batchResult"
        :type="batchResult.skippedCount > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        style="margin-bottom: 12px"
      >
        <template #title>
          新增 {{ batchResult.createdCount }} 个，跳过 {{ batchResult.skippedCount }} 个（同名 + 同组别 + 同性别已存在）
        </template>
        <div v-if="batchResult.skippedNames.length > 0" style="margin-top: 4px">
          跳过项目：{{ batchResult.skippedNames.join('、') }}
        </div>
      </el-alert>

      <el-form ref="batchFormRef" :model="batchForm" :rules="batchRules" label-width="110px">
        <el-form-item label="组别" prop="groupIds">
          <el-select v-model="batchForm.groupIds" multiple placeholder="可多选" style="width: 100%">
            <el-option v-for="item in meetStore.groupOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <DictSelect v-model="batchForm.gender" :dict-type="DICT_TYPE.gender" />
        </el-form-item>
        <el-form-item label="项目模板" prop="templates">
          <el-checkbox-group v-model="batchForm.templates">
            <el-checkbox v-for="item in EVENT_TEMPLATES" :key="item" :value="item" :label="item">{{ item }}</el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">
        生成规则：组别 × 性别 × 项目模板 的笛卡尔积；系统会自动填充项目类型、子类型、接力人数与预计时长。
      </div>
      <template #footer>
        <el-button @click="batchVisible = false">{{ batchResult ? '关闭' : '取消' }}</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="handleBatchSubmit">开始生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>
