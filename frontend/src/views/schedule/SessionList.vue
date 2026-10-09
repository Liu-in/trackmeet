<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  createSession,
  deleteSession,
  pageSessions,
  updateSession,
  type ScheduleSession
} from '@/api/schedule'
import { listVenues, type Venue } from '@/api/meet'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 竞赛单元管理：按日期/关键词检索，新增、编辑、删除竞赛单元。
 * 权限编码：schedule:session:list / schedule:session:add / schedule:session:edit / schedule:session:delete
 */
defineOptions({ name: 'SessionList' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const loading = ref(false)
const submitting = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  date: '',
  keyword: ''
})

const rows = ref<ScheduleSession[]>([])
const total = ref(0)
const venues = ref<Venue[]>([])

const meetReady = computed(() => !!meetStore.currentMeetId)

/** 时段选项：与后端约定 MORNING/AFTERNOON/EVENING */
const PERIOD_OPTIONS = [
  { value: 'MORNING', label: '上午', type: 'primary' },
  { value: 'AFTERNOON', label: '下午', type: 'success' },
  { value: 'EVENING', label: '晚上', type: 'warning' }
] as const

/** 竞赛单元发布状态（schedule_session.publish_status；该表无 status 列） */
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿', type: 'info' },
  { value: 'PUBLISHED', label: '已发布', type: 'success' }
] as const

function periodMeta(period?: string) {
  const hit = PERIOD_OPTIONS.find((item) => item.value === period)
  return { label: hit?.label || period || '-', type: (hit?.type || 'info') as 'primary' | 'success' | 'warning' | 'info' }
}

function statusMeta(publishStatus?: string) {
  const hit = STATUS_OPTIONS.find((item) => item.value === publishStatus)
  return { label: hit?.label || publishStatus || '草稿', type: (hit?.type || 'info') as 'success' | 'info' }
}

/** 主场地名称：优先用接口返回的场地列表映射，缺失时回退为 ID */
function venueName(venueId?: number): string {
  if (!venueId) {
    return '-'
  }
  const hit = venues.value.find((item) => item.id === venueId)
  return hit?.venueName || String(venueId)
}

/* ------------------------------- 列表查询 ------------------------------- */

async function loadVenues() {
  if (!meetStore.currentMeetId) {
    venues.value = []
    return
  }
  try {
    venues.value = (await listVenues({ meetId: meetStore.currentMeetId })) || []
  } catch {
    // 错误提示由 request 拦截器统一处理
    venues.value = []
  }
}

async function load() {
  if (!meetStore.currentMeetId) {
    rows.value = []
    total.value = 0
    return
  }
  loading.value = true
  try {
    const page = await pageSessions({
      page: query.page,
      size: query.size,
      meetId: meetStore.currentMeetId,
      date: query.date || undefined,
      keyword: query.keyword.trim() || undefined
    })
    rows.value = page?.records || []
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
  query.date = ''
  query.keyword = ''
  query.page = 1
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

/* ------------------------------- 新增 / 编辑 ------------------------------- */

interface SessionForm {
  id?: number
  sessionName: string
  sessionDate: string
  dayNo?: number
  period: string
  startTime: string
  endTime: string
  venueId?: number
  publishStatus: string
  sortNo?: number
  remark: string
}

const dialogVisible = ref(false)
const isEdit = ref(false)
const form = reactive<SessionForm>({
  id: undefined,
  sessionName: '',
  sessionDate: '',
  dayNo: undefined,
  period: 'MORNING',
  startTime: '',
  endTime: '',
  venueId: undefined,
  publishStatus: 'DRAFT',
  sortNo: undefined,
  remark: ''
})

function resetForm() {
  form.id = undefined
  form.sessionName = ''
  form.sessionDate = ''
  form.dayNo = undefined
  form.period = 'MORNING'
  form.startTime = ''
  form.endTime = ''
  form.venueId = undefined
  form.publishStatus = 'DRAFT'
  form.sortNo = undefined
  form.remark = ''
}

function handleAdd() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

function handleEdit(row: ScheduleSession) {
  if (!row.id) {
    return
  }
  isEdit.value = true
  form.id = row.id
  form.sessionName = row.sessionName || ''
  form.sessionDate = row.sessionDate || ''
  form.dayNo = row.dayNo
  form.period = row.period || 'MORNING'
  form.startTime = row.startTime || ''
  form.endTime = row.endTime || ''
  form.venueId = row.venueId
  form.publishStatus = row.publishStatus || 'DRAFT'
  form.sortNo = row.sortNo
  form.remark = row.remark || ''
  dialogVisible.value = true
}

function validateForm(): string | null {
  if (!form.sessionName.trim()) {
    return '请输入单元名称'
  }
  if (!form.sessionDate) {
    return '请选择单元日期'
  }
  if (!form.period) {
    return '请选择时段'
  }
  if (form.startTime && form.endTime && form.endTime <= form.startTime) {
    return '结束时刻需晚于开始时刻'
  }
  return null
}

async function handleSubmit() {
  const invalid = validateForm()
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  if (!meetStore.currentMeetId) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  const payload: ScheduleSession = {
    meetId: meetStore.currentMeetId,
    sessionName: form.sessionName.trim(),
    sessionDate: form.sessionDate,
    dayNo: form.dayNo,
    period: form.period,
    startTime: form.startTime || undefined,
    endTime: form.endTime || undefined,
    venueId: form.venueId,
    publishStatus: form.publishStatus || 'DRAFT',
    sortNo: form.sortNo,
    remark: form.remark.trim() || undefined
  }
  submitting.value = true
  try {
    if (isEdit.value && form.id) {
      await updateSession(form.id, payload)
      ElMessage.success('竞赛单元已更新')
    } else {
      await createSession(payload)
      ElMessage.success('竞赛单元已新增')
    }
    dialogVisible.value = false
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: ScheduleSession) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(`确认删除竞赛单元「${row.sessionName}」吗？单元下的日程条目需另行处理。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteSession(row.id)
    ElMessage.success('竞赛单元已删除')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 初始化 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  await Promise.all([loadVenues(), load()])
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">竞赛单元</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ total }} 个竞赛单元
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-date-picker
          v-model="query.date"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="单元日期"
          clearable
          style="width: 180px"
        />
        <el-input
          v-model="query.keyword"
          placeholder="单元名称 / 备注"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button
            v-if="userStore.hasPerm('schedule:session:add')"
            type="primary"
            :disabled="!meetReady"
            @click="handleAdd"
          >
            新增单元
          </el-button>
          <span v-if="!meetReady" class="tm-form-tip">请先在顶栏选择届次</span>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="rows" border stripe max-height="560">
        <el-table-column prop="sessionName" label="单元名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="sessionDate" label="日期" width="120" />
        <el-table-column prop="dayNo" label="第几天" width="90" align="center">
          <template #default="{ row }">{{ (row as ScheduleSession).dayNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="时段" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="periodMeta((row as ScheduleSession).period).type" size="small" effect="plain">
              {{ periodMeta((row as ScheduleSession).period).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="开始时刻" width="110" align="center">
          <template #default="{ row }">{{ (row as ScheduleSession).startTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="结束时刻" width="110" align="center">
          <template #default="{ row }">{{ (row as ScheduleSession).endTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="主场地" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ venueName((row as ScheduleSession).venueId) }}</template>
        </el-table-column>
        <el-table-column label="发布状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusMeta((row as ScheduleSession).publishStatus).type" size="small">
              {{ statusMeta((row as ScheduleSession).publishStatus).label }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sortNo" label="排序号" width="90" align="center">
          <template #default="{ row }">{{ (row as ScheduleSession).sortNo ?? '-' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ (row as ScheduleSession).remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="userStore.hasPerm('schedule:session:edit')"
              link
              type="primary"
              @click="handleEdit(row as ScheduleSession)"
            >
              编辑
            </el-button>
            <el-button
              v-if="userStore.hasPerm('schedule:session:delete')"
              link
              type="danger"
              @click="handleDelete(row as ScheduleSession)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无竞赛单元" />
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

    <!-- 新增 / 编辑竞赛单元 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑竞赛单元' : '新增竞赛单元'"
      width="720px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="单元名称" required>
              <el-input v-model="form.sessionName" maxlength="100" placeholder="如：第一天上午" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="单元日期" required>
              <el-date-picker
                v-model="form.sessionDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="请选择单元日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="第几天">
              <el-input-number v-model="form.dayNo" :min="1" :max="60" placeholder="赛期第几天" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="时段" required>
              <el-select v-model="form.period" placeholder="请选择时段" style="width: 100%">
                <el-option v-for="item in PERIOD_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始时刻">
              <el-time-picker
                v-model="form.startTime"
                value-format="HH:mm:ss"
                placeholder="请选择开始时刻"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束时刻">
              <el-time-picker
                v-model="form.endTime"
                value-format="HH:mm:ss"
                placeholder="请选择结束时刻"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主场地">
              <el-select v-model="form.venueId" placeholder="请选择主场地" clearable style="width: 100%">
                <el-option v-for="item in venues" :key="item.id" :label="item.venueName" :value="item.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="发布状态">
              <el-select v-model="form.publishStatus" placeholder="请选择发布状态" style="width: 100%">
                <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" placeholder="越小越靠前" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="100" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <div class="tm-form-tip">带 * 为必填项；单元起止时刻为 HH:mm:ss，同一届次内日期 + 时段唯一。</div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
