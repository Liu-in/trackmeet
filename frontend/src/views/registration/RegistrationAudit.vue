<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pageEvents, type SportEvent } from '@/api/event'
import {
  approveRegistrations,
  getCheckResult,
  pageRegistrations,
  rejectRegistration,
  type RegistrationCheckItem,
  type RegistrationCheckResult,
  type RegistrationVO
} from '@/api/registration'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 报名审核：状态/项目/单位筛选、批量通过、单条退回、校验快照查看。
 * 权限编码：registration:approve（列表读取复用 registration:list）
 */
defineOptions({ name: 'RegistrationAudit' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const loading = ref(false)
const approving = ref(false)

const query = reactive({
  page: 1,
  size: 20,
  status: 'PENDING',
  eventId: undefined as number | undefined,
  unitId: undefined as number | undefined
})

const rows = ref<RegistrationVO[]>([])
const total = ref(0)
const eventList = ref<SportEvent[]>([])
const selectedRows = ref<RegistrationVO[]>([])

const meetReady = computed(() => !!meetStore.currentMeetId)

const STATUS_META: Record<string, { label: string; type: 'warning' | 'success' | 'danger' | 'info' }> = {
  PENDING: { label: '待审', type: 'warning' },
  APPROVED: { label: '已通过', type: 'success' },
  REJECTED: { label: '已退回', type: 'danger' },
  CANCELLED: { label: '已取消', type: 'info' }
}

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

function isPending(row: RegistrationVO): boolean {
  return row.status === 'PENDING'
}

/** 仅待审行可通过左侧多选勾选 */
function selectable(row: RegistrationVO): boolean {
  return isPending(row)
}

/* ------------------------------- 数据加载 ------------------------------- */

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
      status: query.status || undefined,
      eventId: query.eventId,
      unitId: query.unitId
    })
    rows.value = page.records || []
    total.value = page.total || 0
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
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  query.page = 1
  load()
}

function handleReset() {
  query.page = 1
  query.status = 'PENDING'
  query.eventId = undefined
  query.unitId = undefined
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

function handleSelectionChange(selection: RegistrationVO[]) {
  selectedRows.value = selection
}

/* ------------------------------- 批量通过 ------------------------------- */

async function handleBatchApprove() {
  const ids = selectedRows.value.map((row) => row.id).filter((id): id is number => typeof id === 'number')
  if (!ids.length) {
    ElMessage.warning('请先勾选待审核的报名记录')
    return
  }
  try {
    await ElMessageBox.confirm(`确认通过选中的 ${ids.length} 条报名吗？`, '批量通过', {
      type: 'warning',
      confirmButtonText: '确认通过',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  approving.value = true
  try {
    await approveRegistrations(ids)
    ElMessage.success(`已通过 ${ids.length} 条报名`)
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    approving.value = false
  }
}

/* ------------------------------- 单条通过 / 退回 ------------------------------- */

async function handleApprove(row: RegistrationVO) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认通过「${row.isRelay === 1 ? row.relayName || '接力队' : row.athleteName || '该运动员'}」的报名吗？`,
      '通过报名',
      { type: 'warning', confirmButtonText: '确认通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  approving.value = true
  try {
    await approveRegistrations([row.id])
    ElMessage.success('报名已通过')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    approving.value = false
  }
}

async function handleReject(row: RegistrationVO) {
  if (!row.id) {
    return
  }
  let reason = ''
  try {
    const result = await ElMessageBox.prompt('请输入退回原因（必填，200 字以内）', '退回报名', {
      confirmButtonText: '确认退回',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '如：项目限报人数已满 / 组别与性别不符',
      inputValidator: (value: string) => {
        const text = (value || '').trim()
        if (!text) {
          return '退回原因不能为空'
        }
        if (text.length > 200) {
          return '退回原因不能超过 200 字'
        }
        return true
      }
    })
    reason = (result.value || '').trim()
  } catch {
    return
  }
  approving.value = true
  try {
    await rejectRegistration(row.id, reason)
    ElMessage.success('报名已退回')
    await load()
  } catch {
    // 错误提示由 request 拦截器统一处理
  } finally {
    approving.value = false
  }
}

/* ------------------------------- 校验快照 ------------------------------- */

const drawerVisible = ref(false)
const snapshotRaw = ref('')
const snapshotItems = ref<RegistrationCheckItem[] | null>(null)
const snapshotTitle = ref('')
const snapshotLoading = ref(false)
const snapshotSummary = ref<RegistrationCheckResult | null>(null)

function parseCheckItems(raw: string): RegistrationCheckItem[] | null {
  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed)) {
      return parsed as RegistrationCheckItem[]
    }
    if (parsed && typeof parsed === 'object') {
      const items = (parsed as { items?: unknown }).items
      if (Array.isArray(items)) {
        return items as RegistrationCheckItem[]
      }
    }
    return null
  } catch {
    return null
  }
}

async function openSnapshot(row: RegistrationVO) {
  snapshotTitle.value = `${row.isRelay === 1 ? row.relayName || '接力队' : row.athleteName || '报名'}｜${
    row.eventName || eventName(row.eventId)
  }`
  snapshotRaw.value = row.checkResultJson || ''
  snapshotItems.value = null
  snapshotSummary.value = null
  drawerVisible.value = true

  if (snapshotRaw.value) {
    snapshotItems.value = parseCheckItems(snapshotRaw.value)
    return
  }
  snapshotLoading.value = true
  try {
    const result = await getCheckResult({
      meetId: meetStore.currentMeetId,
      eventId: row.eventId,
      unitId: row.unitId
    })
    snapshotSummary.value = result
    snapshotItems.value = result?.items || []
    snapshotRaw.value = result ? JSON.stringify(result, null, 2) : ''
  } catch {
    snapshotItems.value = null
  } finally {
    snapshotLoading.value = false
  }
}

function levelTagType(level?: string): 'danger' | 'warning' {
  return level === 'ERROR' ? 'danger' : 'warning'
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
      <div class="tm-page-title">报名审核</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜待审核记录共 {{ total }} 条（当前筛选）
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <el-select v-model="query.status" placeholder="报名状态" clearable style="width: 160px">
          <el-option label="全部状态" :value="''" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已通过" value="APPROVED" />
          <el-option label="已退回" value="REJECTED" />
        </el-select>
        <el-select v-model="query.eventId" placeholder="报名项目" clearable filterable style="width: 200px">
          <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
        </el-select>
        <el-select v-model="query.unitId" placeholder="参赛单位" clearable style="width: 180px">
          <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-button type="primary" :disabled="!meetReady" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button
            v-if="userStore.hasPerm('registration:approve')"
            type="primary"
            :disabled="!selectedRows.length"
            :loading="approving"
            @click="handleBatchApprove"
          >
            批量通过{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
          <span class="tm-form-tip">仅「待审核」记录可勾选</span>
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
        max-height="560"
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="46" :selectable="selectable" />
        <el-table-column label="项目" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as RegistrationVO).eventName || eventName((row as RegistrationVO).eventId) }}
            <el-tag v-if="(row as RegistrationVO).isRelay === 1" size="small" type="warning" effect="plain">接力</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="参赛人" min-width="190" show-overflow-tooltip>
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
        <el-table-column label="校验快照" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSnapshot(row as RegistrationVO)">查看校验</el-button>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right" align="center">
          <template #default="{ row }">
            <template v-if="isPending(row as RegistrationVO)">
              <el-button
                v-if="userStore.hasPerm('registration:approve')"
                link
                type="success"
                @click="handleApprove(row as RegistrationVO)"
              >
                通过
              </el-button>
              <el-button
                v-if="userStore.hasPerm('registration:approve')"
                link
                type="danger"
                @click="handleReject(row as RegistrationVO)"
              >
                退回
              </el-button>
            </template>
            <span v-else class="tm-form-tip">已处理</span>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无待审核报名" />
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

    <!-- 校验快照 -->
    <el-drawer v-model="drawerVisible" title="校验快照" size="640px" destroy-on-close>
      <div v-loading="snapshotLoading">
        <div class="tm-page-desc" style="margin-bottom: 12px">{{ snapshotTitle }}</div>

        <template v-if="snapshotItems && snapshotItems.length > 0">
          <el-alert
            v-if="snapshotSummary"
            :title="`通过 ${snapshotSummary.passCount} 条 / 不通过 ${snapshotSummary.failCount} 条`"
            type="info"
            :closable="false"
            show-icon
            style="margin-bottom: 12px"
          />
          <el-table :data="snapshotItems" border size="small" max-height="420">
            <el-table-column label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="levelTagType((row as RegistrationCheckItem).level)" size="small">
                  {{ (row as RegistrationCheckItem).level === 'ERROR' ? '错误' : '警告' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="编码" width="150" show-overflow-tooltip />
            <el-table-column prop="athleteName" label="运动员" width="110" show-overflow-tooltip />
            <el-table-column prop="message" label="说明" min-width="200" show-overflow-tooltip />
          </el-table>
        </template>

        <template v-else-if="snapshotItems">
          <el-empty description="该校验快照暂无问题项" />
        </template>

        <template v-else>
          <el-alert title="无校验快照（未解析到结构化数据，以下为原始内容）" type="warning" :closable="false" show-icon />
          <el-input
            :model-value="snapshotRaw || '暂无校验快照数据'"
            type="textarea"
            :rows="14"
            readonly
            style="margin-top: 12px"
          />
        </template>
      </div>
    </el-drawer>
  </div>
</template>
