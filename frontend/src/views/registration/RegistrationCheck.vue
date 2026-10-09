<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { pageEvents, type SportEvent } from '@/api/event'
import {
  getCheckResult,
  getRegistrationStatistics,
  getRegistrationWarnings,
  type RegistrationCheckItem,
  type RegistrationCheckResult,
  type RegistrationStat,
  type RegistrationWarning
} from '@/api/registration'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 报名校验与统计：校验结果、报名预警、报名统计（按单位/项目/组别）。
 * 权限编码：registration:check / registration:list
 */
defineOptions({ name: 'RegistrationCheck' })

const meetStore = useMeetStore()
const userStore = useUserStore()

type TabName = 'check' | 'warning' | 'stat'
type StatDimension = 'UNIT' | 'EVENT' | 'GROUP'

const activeTab = ref<TabName>('check')
const loading = ref(false)

const DIMENSION_OPTIONS: Array<{ value: StatDimension; label: string }> = [
  { value: 'UNIT', label: '按单位' },
  { value: 'EVENT', label: '按项目' },
  { value: 'GROUP', label: '按组别' }
]

const eventList = ref<SportEvent[]>([])

const checkQuery = reactive({
  eventId: undefined as number | undefined,
  unitId: undefined as number | undefined
})

const checkResult = ref<RegistrationCheckResult | null>(null)
const warnings = ref<RegistrationWarning[]>([])
const stats = ref<RegistrationStat[]>([])
const dimension = ref<StatDimension>('EVENT')

const meetReady = computed(() => !!meetStore.currentMeetId)

const failCount = computed(() => checkResult.value?.failCount || 0)
const passCount = computed(() => checkResult.value?.passCount || 0)
const checkItems = computed(() => checkResult.value?.items || [])

const approvedRate = (stat: RegistrationStat): number => {
  if (!stat.total) {
    return 0
  }
  return Math.round((stat.approved / stat.total) * 100)
}

function levelTagType(level?: string): 'danger' | 'warning' {
  return level === 'ERROR' ? 'danger' : 'warning'
}

/** 模块权限：有 registration:check 或 registration:list 任一即可访问 */
const canView = computed(() => userStore.hasPerm('registration:check') || userStore.hasPerm('registration:list'))

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

async function loadCheck() {
  if (!meetStore.currentMeetId) {
    checkResult.value = null
    return
  }
  loading.value = true
  try {
    const result = await getCheckResult({
      meetId: meetStore.currentMeetId,
      eventId: checkQuery.eventId,
      unitId: checkQuery.unitId
    })
    checkResult.value = result
  } catch {
    checkResult.value = null
  } finally {
    loading.value = false
  }
}

async function loadWarnings() {
  if (!meetStore.currentMeetId) {
    warnings.value = []
    return
  }
  loading.value = true
  try {
    warnings.value = (await getRegistrationWarnings({ meetId: meetStore.currentMeetId })) || []
  } catch {
    warnings.value = []
  } finally {
    loading.value = false
  }
}

async function loadStatistics() {
  if (!meetStore.currentMeetId) {
    stats.value = []
    return
  }
  loading.value = true
  try {
    stats.value = (await getRegistrationStatistics({ meetId: meetStore.currentMeetId, dimension: dimension.value })) || []
  } catch {
    stats.value = []
  } finally {
    loading.value = false
  }
}

async function loadByTab() {
  if (activeTab.value === 'check') {
    await loadCheck()
  } else if (activeTab.value === 'warning') {
    await loadWarnings()
  } else {
    await loadStatistics()
  }
}

function handleCheckSearch() {
  if (!meetReady.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  loadCheck()
}

function handleCheckReset() {
  checkQuery.eventId = undefined
  checkQuery.unitId = undefined
  loadCheck()
}

function handleDimensionChange() {
  loadStatistics()
}

watch(activeTab, () => {
  loadByTab()
})

watch(
  () => meetStore.currentMeetId,
  async () => {
    await loadEvents()
    await loadByTab()
  }
)

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  if (!meetStore.units.length && meetStore.currentMeetId) {
    await meetStore.loadGroupsAndUnits().catch(() => undefined)
  }
  await loadEvents()
  await loadByTab()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">报名校验与统计</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}
        <span v-if="!canView">（当前账号无校验权限，仅可查看统计）</span>
      </div>
    </div>

    <el-alert
      title="校验规则"
      type="info"
      :closable="false"
      show-icon
      description="报名期、资格状态、组别性别、重复报名、每人限报项数、每单位每项限报人数、接力人数、年龄区间。"
    />

    <div class="tm-card">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="校验结果" :name="'check'">
          <div class="tm-search-bar" style="margin-bottom: 12px">
            <el-select v-model="checkQuery.eventId" placeholder="报名项目" clearable filterable style="width: 200px">
              <el-option v-for="item in eventList" :key="item.id" :label="item.eventName" :value="item.id as number" />
            </el-select>
            <el-select v-model="checkQuery.unitId" placeholder="参赛单位" clearable style="width: 180px">
              <el-option v-for="item in meetStore.unitOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <el-button type="primary" :disabled="!meetReady" :loading="loading" @click="handleCheckSearch">查询</el-button>
            <el-button @click="handleCheckReset">重置</el-button>
          </div>

          <div class="tm-stat-grid" style="margin-bottom: 12px">
            <div class="tm-stat-card">
              <div class="tm-stat-label">通过条数</div>
              <div class="tm-stat-value" style="color: #67c23a">{{ passCount }}</div>
            </div>
            <div class="tm-stat-card">
              <div class="tm-stat-label">不通过条数</div>
              <div class="tm-stat-value" style="color: #f56c6c">{{ failCount }}</div>
            </div>
          </div>

          <el-table v-loading="loading" :data="checkItems" border stripe max-height="480">
            <el-table-column label="级别" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="levelTagType((row as RegistrationCheckItem).level)" size="small">
                  {{ (row as RegistrationCheckItem).level === 'ERROR' ? '错误' : '警告' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="athleteName" label="涉及运动员" width="140" show-overflow-tooltip />
            <el-table-column prop="eventName" label="项目" min-width="160" show-overflow-tooltip />
            <el-table-column prop="code" label="编码" width="170" show-overflow-tooltip />
            <el-table-column prop="message" label="说明" min-width="240" show-overflow-tooltip />
            <template #empty>
              <el-empty description="暂无校验问题，报名数据校验通过" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="报名预警" :name="'warning'">
          <div class="tm-toolbar" style="margin-bottom: 12px">
            <span class="tm-form-tip">按项目统计报名人数，低于开赛要求时给出提示。</span>
            <el-button :loading="loading" @click="loadWarnings">刷新</el-button>
          </div>
          <el-table v-loading="loading" :data="warnings" border stripe max-height="480">
            <el-table-column prop="eventName" label="项目名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="total" label="报名总数" width="120" align="center" />
            <el-table-column prop="approved" label="已通过" width="120" align="center" />
            <el-table-column prop="message" label="提示" min-width="260" show-overflow-tooltip />
            <template #empty>
              <el-empty description="暂无报名不足的项目" />
            </template>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="报名统计" :name="'stat'">
          <div class="tm-toolbar" style="margin-bottom: 12px">
            <el-radio-group v-model="dimension" @change="handleDimensionChange">
              <el-radio-button v-for="item in DIMENSION_OPTIONS" :key="item.value" :value="item.value">
                {{ item.label }}
              </el-radio-button>
            </el-radio-group>
            <el-button :loading="loading" @click="loadStatistics">刷新</el-button>
          </div>
          <el-table v-loading="loading" :data="stats" border stripe max-height="480">
            <el-table-column prop="name" label="名称" min-width="200" show-overflow-tooltip />
            <el-table-column prop="total" label="报名总数" width="110" align="center" />
            <el-table-column prop="pending" label="待审" width="100" align="center" />
            <el-table-column prop="approved" label="已通过" width="100" align="center" />
            <el-table-column prop="rejected" label="已退回" width="100" align="center" />
            <el-table-column label="通过占比" min-width="200">
              <template #default="{ row }">
                <el-progress :percentage="approvedRate(row as RegistrationStat)" :stroke-width="14" />
              </template>
            </el-table-column>
            <template #empty>
              <el-empty description="暂无报名统计数据" />
            </template>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>
