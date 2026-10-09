<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getEventRuleConfig,
  getMeetRuleConfig,
  pageEvents,
  pageScoreRules,
  resetEventRuleConfig,
  saveEventRuleConfig,
  saveMeetRuleConfig,
  type EventRuleConfig,
  type MeetRuleConfig,
  type ScoreRule,
  type SportEvent
} from '@/api/event'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 规则配置：届次默认规则（Tab1）与项目级规则（Tab2，含三层继承来源标注）。
 */
defineOptions({ name: 'RuleConfig' })

/**
 * 项目级规则视图对象扩展：接口契约中还包含以下来源标记字段，
 * 类型声明里未列出（见交付说明），此处本地补全，不修改 src/api/event.ts。
 */
interface EventRuleConfigExt extends EventRuleConfig {
  qualifyStandardSource?: string
  qualifyStandardValueSource?: string
  heightPlanJsonSource?: string
  extraJsonSource?: string
}

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)
const meetName = computed(() => meetStore.currentMeet?.meetName || '未选择')

const MEDAL_SORT_OPTIONS = [
  { label: '金牌优先', value: 'GOLD_FIRST' },
  { label: '奖牌总数优先', value: 'TOTAL_FIRST' },
  { label: '积分优先', value: 'POINT_FIRST' }
]

const TIME_PRECISION_OPTIONS = [
  { label: '百分秒', value: 'HUNDREDTH' },
  { label: '十分秒', value: 'TENTH' },
  { label: '秒', value: 'SECOND' }
]

const LANE_COUNT_OPTIONS = [
  { label: '8 道', value: 8 },
  { label: '6 道', value: 6 }
]

const MEET_LANE_ASSIGN_OPTIONS = [
  { label: '按成绩（种子）', value: 'SEED' },
  { label: '抽签', value: 'DRAW' },
  { label: '蛇形', value: 'SERPENT' }
]

const EVENT_LANE_ASSIGN_OPTIONS = [
  { label: '按成绩（种子）', value: 'SEED' },
  { label: '抽签', value: 'DRAW' },
  { label: '蛇形', value: 'SERPENT' },
  { label: '按顺序', value: 'ORDER' }
]

const DRAW_MODE_OPTIONS = [
  { label: '蛇形', value: 'SERPENT' },
  { label: '随机', value: 'RANDOM' },
  { label: '按成绩段', value: 'SEED' },
  { label: '按单位顺序', value: 'UNIT' }
]

/** 道次顺序默认值：8 道 / 6 道 */
const DEFAULT_LANE_ORDER_8 = '[4,5,3,6,2,7,1,8]'
const DEFAULT_LANE_ORDER_6 = '[3,4,2,5,1,6]'
const DEFAULT_LANE_ORDER_TIP = `8 道默认 ${DEFAULT_LANE_ORDER_8}；6 道默认 ${DEFAULT_LANE_ORDER_6}`
const DEFAULT_WIND_JSON = '{"applicable":true,"maxLegal":2.0,"unit":"m/s"}'
const DEFAULT_TIE_BREAK_JSON = '{"order":["SECOND_BEST","BEST_ATTEMPT_ROUND","DRAW"],"allowParallelRank":true}'

const activeTab = ref<'meet' | 'event'>('meet')

// ---------------- 来源标注 ----------------
function sourceLabel(source?: string): string {
  if (source === 'PROJECT') {
    return '项目级'
  }
  if (source === 'MEET') {
    return '届次级'
  }
  if (source === 'DEFAULT') {
    return '系统默认'
  }
  return '未知来源'
}

function sourceTagType(source?: string): TagType {
  if (source === 'PROJECT') {
    return 'primary'
  }
  if (source === 'MEET') {
    return 'info'
  }
  return 'warning'
}

function validateJson(text?: string): boolean {
  if (!text || !text.trim()) {
    return true
  }
  try {
    JSON.parse(text)
    return true
  } catch {
    ElMessage.warning('JSON 格式不正确')
    return false
  }
}

// ---------------- 届次默认规则 ----------------
function createEmptyMeetForm(): MeetRuleConfig {
  return {
    admitRank: 8,
    scoreRuleId: undefined,
    athleteMaxEvents: 3,
    relayMaxPerAthlete: 2,
    maxPerUnit: 3,
    maxAthletePerUnit: 20,
    conflictIntervalMin: 30,
    refereeIntervalMin: 30,
    relayDoubleFactor: 2,
    bonusInTeamScore: 1,
    medalSort: 'GOLD_FIRST',
    timePrecision: 'HUNDREDTH',
    fieldAttemptCount: 3,
    defaultLaneCount: 8,
    defaultLaneOrderJson: DEFAULT_LANE_ORDER_8,
    laneAssignMode: 'SEED',
    windJson: DEFAULT_WIND_JSON,
    tieBreakJson: DEFAULT_TIE_BREAK_JSON
  }
}

const meetForm = reactive<MeetRuleConfig>(createEmptyMeetForm())
const meetLoading = ref(false)
const meetSaving = ref(false)
const scoreRules = ref<ScoreRule[]>([])

async function loadScoreRules() {
  if (!meetId.value) {
    scoreRules.value = []
    return
  }
  try {
    const page = await pageScoreRules({ page: 1, size: 100, meetId: meetId.value })
    scoreRules.value = page?.records || []
  } catch {
    scoreRules.value = []
  }
}

async function loadMeetConfig() {
  Object.assign(meetForm, createEmptyMeetForm())
  if (!meetId.value) {
    return
  }
  meetLoading.value = true
  try {
    const config = await getMeetRuleConfig(meetId.value)
    if (config) {
      Object.assign(meetForm, config, {
        defaultLaneOrderJson: config.defaultLaneOrderJson || DEFAULT_LANE_ORDER_8,
        windJson: config.windJson || DEFAULT_WIND_JSON,
        tieBreakJson: config.tieBreakJson || DEFAULT_TIE_BREAK_JSON
      })
    }
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    meetLoading.value = false
  }
}

function buildMeetPayload(): MeetRuleConfig | null {
  if (
    !validateJson(meetForm.defaultLaneOrderJson) ||
    !validateJson(meetForm.windJson) ||
    !validateJson(meetForm.tieBreakJson)
  ) {
    return null
  }
  return {
    meetId: meetId.value,
    admitRank: meetForm.admitRank,
    scoreRuleId: meetForm.scoreRuleId,
    athleteMaxEvents: meetForm.athleteMaxEvents,
    relayMaxPerAthlete: meetForm.relayMaxPerAthlete,
    maxPerUnit: meetForm.maxPerUnit,
    maxAthletePerUnit: meetForm.maxAthletePerUnit,
    conflictIntervalMin: meetForm.conflictIntervalMin,
    refereeIntervalMin: meetForm.refereeIntervalMin,
    relayDoubleFactor: meetForm.relayDoubleFactor,
    bonusInTeamScore: meetForm.bonusInTeamScore,
    medalSort: meetForm.medalSort,
    timePrecision: meetForm.timePrecision,
    fieldAttemptCount: meetForm.fieldAttemptCount,
    defaultLaneCount: meetForm.defaultLaneCount,
    defaultLaneOrderJson: meetForm.defaultLaneOrderJson?.trim() || undefined,
    laneAssignMode: meetForm.laneAssignMode,
    windJson: meetForm.windJson?.trim() || undefined,
    tieBreakJson: meetForm.tieBreakJson?.trim() || undefined
  }
}

async function handleMeetSave() {
  if (!meetId.value) {
    ElMessage.warning('请先在顶部选择届次')
    return
  }
  const payload = buildMeetPayload()
  if (!payload) {
    return
  }
  meetSaving.value = true
  try {
    await saveMeetRuleConfig(meetId.value, payload)
    ElMessage.success('届次默认规则保存成功')
    await loadMeetConfig()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    meetSaving.value = false
  }
}

// ---------------- 项目级规则 ----------------
const events = ref<SportEvent[]>([])
const eventLoading = ref(false)
const selectedEventId = ref<number | undefined>(undefined)
const eventConfig = ref<EventRuleConfigExt | null>(null)
const eventLoading2 = ref(false)
const eventSaving = ref(false)
const eventResetting = ref(false)

function createEmptyEventForm(): EventRuleConfig {
  return {
    admitRank: undefined,
    drawMode: undefined,
    laneOrderJson: DEFAULT_LANE_ORDER_8,
    laneAssignMode: undefined,
    conflictIntervalMin: undefined,
    windJson: DEFAULT_WIND_JSON,
    attemptCount: undefined,
    qualifyStandard: undefined,
    qualifyStandardValue: undefined,
    heightPlanJson: undefined,
    tieBreakJson: DEFAULT_TIE_BREAK_JSON,
    durationMin: undefined
  }
}

const eventForm = reactive<EventRuleConfig>(createEmptyEventForm())

async function loadEvents() {
  if (!meetId.value) {
    events.value = []
    selectedEventId.value = undefined
    return
  }
  eventLoading.value = true
  try {
    const page = await pageEvents({ page: 1, size: 200, meetId: meetId.value })
    events.value = page?.records || []
    if (selectedEventId.value === undefined || !events.value.some((item) => item.id === selectedEventId.value)) {
      selectedEventId.value = events.value.length > 0 ? events.value[0].id : undefined
    }
  } catch {
    events.value = []
  } finally {
    eventLoading.value = false
  }
}

async function loadEventConfig() {
  Object.assign(eventForm, createEmptyEventForm())
  eventConfig.value = null
  if (!selectedEventId.value) {
    return
  }
  eventLoading2.value = true
  try {
    const config = await getEventRuleConfig(selectedEventId.value)
    if (config) {
      eventConfig.value = config
      Object.assign(eventForm, config, {
        laneOrderJson: config.laneOrderJson || DEFAULT_LANE_ORDER_8,
        windJson: config.windJson || DEFAULT_WIND_JSON,
        tieBreakJson: config.tieBreakJson || DEFAULT_TIE_BREAK_JSON,
        heightPlanJson: config.heightPlanJson || ''
      })
    }
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    eventLoading2.value = false
  }
}

function buildEventPayload(): EventRuleConfig | null {
  if (
    !validateJson(eventForm.laneOrderJson) ||
    !validateJson(eventForm.windJson) ||
    !validateJson(eventForm.tieBreakJson) ||
    !validateJson(eventForm.heightPlanJson)
  ) {
    return null
  }
  return {
    eventId: selectedEventId.value,
    admitRank: eventForm.admitRank,
    drawMode: eventForm.drawMode,
    laneOrderJson: eventForm.laneOrderJson?.trim() || undefined,
    laneAssignMode: eventForm.laneAssignMode,
    conflictIntervalMin: eventForm.conflictIntervalMin,
    windJson: eventForm.windJson?.trim() || undefined,
    attemptCount: eventForm.attemptCount,
    qualifyStandard: eventForm.qualifyStandard?.trim() || undefined,
    qualifyStandardValue: eventForm.qualifyStandardValue,
    heightPlanJson: eventForm.heightPlanJson?.trim() || undefined,
    tieBreakJson: eventForm.tieBreakJson?.trim() || undefined,
    durationMin: eventForm.durationMin
  }
}

async function handleEventSave() {
  if (!selectedEventId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  const payload = buildEventPayload()
  if (!payload) {
    return
  }
  eventSaving.value = true
  try {
    await saveEventRuleConfig(selectedEventId.value, payload)
    ElMessage.success('项目级规则保存成功')
    await loadEventConfig()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    eventSaving.value = false
  }
}

async function handleEventReset() {
  if (!selectedEventId.value) {
    ElMessage.warning('请先选择项目')
    return
  }
  try {
    await ElMessageBox.confirm(
      '恢复继承将清空该项目的全部项目级规则，未配置项改为继承届次默认值，确定继续吗？',
      '恢复继承确认',
      { type: 'warning', confirmButtonText: '确定恢复', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  eventResetting.value = true
  try {
    await resetEventRuleConfig(selectedEventId.value)
    ElMessage.success('已恢复继承届次默认规则')
    await loadEventConfig()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    eventResetting.value = false
  }
}

function handleLaneCountChange(value: number | undefined) {
  if (value === 6 && (!meetForm.defaultLaneOrderJson || meetForm.defaultLaneOrderJson === DEFAULT_LANE_ORDER_8)) {
    meetForm.defaultLaneOrderJson = DEFAULT_LANE_ORDER_6
  }
  if (value === 8 && (!meetForm.defaultLaneOrderJson || meetForm.defaultLaneOrderJson === DEFAULT_LANE_ORDER_6)) {
    meetForm.defaultLaneOrderJson = DEFAULT_LANE_ORDER_8
  }
}

function resetMeetJson(field: 'defaultLaneOrderJson' | 'windJson' | 'tieBreakJson') {
  if (field === 'defaultLaneOrderJson') {
    meetForm.defaultLaneOrderJson = meetForm.defaultLaneCount === 6 ? DEFAULT_LANE_ORDER_6 : DEFAULT_LANE_ORDER_8
  } else if (field === 'windJson') {
    meetForm.windJson = DEFAULT_WIND_JSON
  } else {
    meetForm.tieBreakJson = DEFAULT_TIE_BREAK_JSON
  }
}

function resetEventJson(field: 'laneOrderJson' | 'windJson' | 'tieBreakJson') {
  if (field === 'laneOrderJson') {
    eventForm.laneOrderJson = DEFAULT_LANE_ORDER_8
  } else if (field === 'windJson') {
    eventForm.windJson = DEFAULT_WIND_JSON
  } else {
    eventForm.tieBreakJson = DEFAULT_TIE_BREAK_JSON
  }
}

// ---------------- 生命周期 ----------------
onMounted(async () => {
  await Promise.all([loadScoreRules(), loadMeetConfig(), loadEvents()])
  await loadEventConfig()
})

watch(meetId, async () => {
  await Promise.all([loadScoreRules(), loadMeetConfig(), loadEvents()])
  await loadEventConfig()
})

watch(selectedEventId, () => {
  loadEventConfig()
})

watch(activeTab, (value) => {
  if (value === 'event' && !eventConfig.value && selectedEventId.value) {
    loadEventConfig()
  }
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">规则配置</div>
      <div class="tm-page-desc">当前届次：{{ meetName }}｜配置届次默认规则与项目级规则</div>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 12px"
        title="规则继承顺序：轮次级 > 项目级 > 届次级 > 系统默认"
        description="项目级规则中未配置的字段会自动继承届次默认值；如需完全继承，可点击「恢复继承」。"
      />
    </div>

    <div class="tm-card">
      <div class="tm-search-bar">
        <span>配置项目：</span>
        <el-select
          v-model="selectedEventId"
          :loading="eventLoading"
          placeholder="选择项目后配置项目级规则"
          filterable
          clearable
          style="width: 320px"
        >
          <el-option v-for="item in events" :key="item.id" :label="item.eventName" :value="item.id" />
        </el-select>
        <el-tag v-if="!meetId" type="warning" effect="plain">未选择届次</el-tag>
      </div>
    </div>

    <div class="tm-card">
      <el-tabs v-model="activeTab">
        <!-- 届次默认规则 -->
        <el-tab-pane label="届次默认规则" name="meet">
          <el-form v-loading="meetLoading" :model="meetForm" label-width="180px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="录取名次">
                  <el-input-number v-model="meetForm.admitRank" :min="0" :max="999" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认名次分方案">
                  <el-select v-model="meetForm.scoreRuleId" placeholder="请选择名次分方案" clearable style="width: 100%">
                    <el-option v-for="item in scoreRules" :key="item.id" :label="item.ruleName" :value="item.id">
                      <span>{{ item.ruleName }}</span>
                      <el-tag v-if="item.isDefault === 1" type="success" size="small" effect="plain" style="margin-left: 6px">
                        默认
                      </el-tag>
                    </el-option>
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="每人限报项数">
                  <el-input-number v-model="meetForm.athleteMaxEvents" :min="0" :max="99" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="每人限报接力项数">
                  <el-input-number
                    v-model="meetForm.relayMaxPerAthlete"
                    :min="0"
                    :max="99"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="每单位每项限报人数">
                  <el-input-number v-model="meetForm.maxPerUnit" :min="0" :max="999" controls-position="right" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="每单位报名总人数上限">
                  <el-input-number
                    v-model="meetForm.maxAthletePerUnit"
                    :min="0"
                    :max="9999"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="兼项最小间隔(分)">
                  <el-input-number
                    v-model="meetForm.conflictIntervalMin"
                    :min="0"
                    :max="1440"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="裁判连场最小间隔(分)">
                  <el-input-number
                    v-model="meetForm.refereeIntervalMin"
                    :min="0"
                    :max="1440"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="接力双倍系数">
                  <el-input-number
                    v-model="meetForm.relayDoubleFactor"
                    :min="0"
                    :max="10"
                    :step="0.5"
                    :precision="2"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="破纪录加分计入团体分">
                  <el-switch v-model="meetForm.bonusInTeamScore" :active-value="1" :inactive-value="0" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="奖牌榜排序">
                  <el-select v-model="meetForm.medalSort" placeholder="请选择" style="width: 100%">
                    <el-option v-for="item in MEDAL_SORT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="计时精度">
                  <el-select v-model="meetForm.timePrecision" placeholder="请选择" style="width: 100%">
                    <el-option v-for="item in TIME_PRECISION_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="田赛试跳次数">
                  <el-input-number
                    v-model="meetForm.fieldAttemptCount"
                    :min="1"
                    :max="20"
                    controls-position="right"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="默认道数">
                  <el-select v-model="meetForm.defaultLaneCount" style="width: 100%" @change="handleLaneCountChange">
                    <el-option v-for="item in LANE_COUNT_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="道次分配方式">
                  <el-select v-model="meetForm.laneAssignMode" placeholder="请选择" clearable style="width: 100%">
                    <el-option v-for="item in MEET_LANE_ASSIGN_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="默认道次顺序 JSON">
                  <el-input v-model="meetForm.defaultLaneOrderJson" type="textarea" :rows="2" placeholder="如 [4,5,3,6,2,7,1,8]" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_LANE_ORDER_TIP }}
                    <el-button link type="primary" @click="resetMeetJson('defaultLaneOrderJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="风速规则 JSON">
                  <el-input v-model="meetForm.windJson" type="textarea" :rows="2" placeholder="风速规则 JSON" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_WIND_JSON }}
                    <el-button link type="primary" @click="resetMeetJson('windJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item label="平局规则 JSON">
                  <el-input v-model="meetForm.tieBreakJson" type="textarea" :rows="2" placeholder="平局判定顺序 JSON" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_TIE_BREAK_JSON }}
                    <el-button link type="primary" @click="resetMeetJson('tieBreakJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="tm-form-tip">
              每人限报项数为 0 表示不限；提交前会校验各 JSON 文本格式，格式不正确时不会提交。
            </div>
            <div class="tm-toolbar" style="margin-top: 12px">
              <div class="tm-toolbar-actions">
                <el-button
                  type="primary"
                  :loading="meetSaving"
                  :disabled="!meetId || !userStore.hasPerm('event:rule:edit')"
                  @click="handleMeetSave"
                >
                  保存届次默认规则
                </el-button>
                <el-button :disabled="!meetId" @click="loadMeetConfig">重置表单</el-button>
              </div>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- 项目级规则 -->
        <el-tab-pane label="项目级规则" name="event">
          <el-empty v-if="!selectedEventId" description="请选择项目后配置项目级规则" />
          <el-form v-else v-loading="eventLoading2" :model="eventForm" label-width="180px">
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="录取名次">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input-number
                      v-model="eventForm.admitRank"
                      :min="0"
                      :max="999"
                      controls-position="right"
                      style="flex: 1"
                    />
                    <el-tag :type="sourceTagType(eventConfig?.admitRankSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.admitRankSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="分组方式">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-select v-model="eventForm.drawMode" placeholder="请选择" clearable style="flex: 1">
                      <el-option v-for="item in DRAW_MODE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-tag :type="sourceTagType(eventConfig?.drawModeSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.drawModeSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="道次分配方式">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-select v-model="eventForm.laneAssignMode" placeholder="请选择" clearable style="flex: 1">
                      <el-option v-for="item in EVENT_LANE_ASSIGN_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
                    </el-select>
                    <el-tag :type="sourceTagType(eventConfig?.laneAssignModeSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.laneAssignModeSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="兼项最小间隔(分)">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input-number
                      v-model="eventForm.conflictIntervalMin"
                      :min="0"
                      :max="1440"
                      controls-position="right"
                      style="flex: 1"
                    />
                    <el-tag :type="sourceTagType(eventConfig?.conflictIntervalMinSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.conflictIntervalMinSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="试跳/试掷次数">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input-number
                      v-model="eventForm.attemptCount"
                      :min="1"
                      :max="20"
                      controls-position="right"
                      style="flex: 1"
                    />
                    <el-tag :type="sourceTagType(eventConfig?.attemptCountSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.attemptCountSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="预计时长(分)">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input-number
                      v-model="eventForm.durationMin"
                      :min="0"
                      :max="9999"
                      controls-position="right"
                      style="flex: 1"
                    />
                    <el-tag :type="sourceTagType(eventConfig?.durationMinSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.durationMinSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="及格标准原文">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input v-model="eventForm.qualifyStandard" placeholder="如 11.20s / 1.80m" style="flex: 1" />
                    <el-tag :type="sourceTagType(eventConfig?.qualifyStandardSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.qualifyStandardSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="及格标准值">
                  <div style="display: flex; align-items: center; gap: 8px; width: 100%">
                    <el-input-number
                      v-model="eventForm.qualifyStandardValue"
                      :precision="2"
                      :step="0.01"
                      :min="0"
                      controls-position="right"
                      style="flex: 1"
                    />
                    <el-tag :type="sourceTagType(eventConfig?.qualifyStandardValueSource)" size="small" effect="plain">
                      {{ sourceLabel(eventConfig?.qualifyStandardValueSource) }}
                    </el-tag>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item>
                  <template #label>
                    <span>道次顺序 JSON</span>
                    <el-tag :type="sourceTagType(eventConfig?.laneOrderJsonSource)" size="small" effect="plain" style="margin-left: 6px">
                      {{ sourceLabel(eventConfig?.laneOrderJsonSource) }}
                    </el-tag>
                  </template>
                  <el-input v-model="eventForm.laneOrderJson" type="textarea" :rows="2" placeholder="如 [4,5,3,6,2,7,1,8]" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_LANE_ORDER_TIP }}
                    <el-button link type="primary" @click="resetEventJson('laneOrderJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item>
                  <template #label>
                    <span>风速规则 JSON</span>
                    <el-tag :type="sourceTagType(eventConfig?.windJsonSource)" size="small" effect="plain" style="margin-left: 6px">
                      {{ sourceLabel(eventConfig?.windJsonSource) }}
                    </el-tag>
                  </template>
                  <el-input v-model="eventForm.windJson" type="textarea" :rows="2" placeholder="风速规则 JSON" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_WIND_JSON }}
                    <el-button link type="primary" @click="resetEventJson('windJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item>
                  <template #label>
                    <span>升杆方案 JSON</span>
                    <el-tag :type="sourceTagType(eventConfig?.heightPlanJsonSource)" size="small" effect="plain" style="margin-left: 6px">
                      {{ sourceLabel(eventConfig?.heightPlanJsonSource) }}
                    </el-tag>
                  </template>
                  <el-input
                    v-model="eventForm.heightPlanJson"
                    type="textarea"
                    :rows="2"
                    placeholder="如 {&quot;start&quot;:1.5,&quot;step&quot;:0.05}"
                  />
                  <div class="tm-form-tip">跳高/撑竿跳高项目的起跳高度与升杆幅度，仅田赛项目需要配置。</div>
                </el-form-item>
              </el-col>
              <el-col :span="24">
                <el-form-item>
                  <template #label>
                    <span>平局规则 JSON</span>
                    <el-tag :type="sourceTagType(eventConfig?.tieBreakJsonSource)" size="small" effect="plain" style="margin-left: 6px">
                      {{ sourceLabel(eventConfig?.tieBreakJsonSource) }}
                    </el-tag>
                  </template>
                  <el-input v-model="eventForm.tieBreakJson" type="textarea" :rows="2" placeholder="平局判定顺序 JSON" />
                  <div class="tm-form-tip">
                    默认 {{ DEFAULT_TIE_BREAK_JSON }}
                    <el-button link type="primary" @click="resetEventJson('tieBreakJson')">恢复默认</el-button>
                  </div>
                </el-form-item>
              </el-col>
            </el-row>
            <div class="tm-form-tip">
              来源标记：项目级=本项目自定义；届次级=继承届次默认；系统默认=届次也未配置时使用的兜底值。
            </div>
            <div class="tm-toolbar" style="margin-top: 12px">
              <div class="tm-toolbar-actions">
                <el-button
                  type="primary"
                  :loading="eventSaving"
                  :disabled="!userStore.hasPerm('event:rule:edit')"
                  @click="handleEventSave"
                >
                  保存项目级规则
                </el-button>
                <el-button
                  type="danger"
                  plain
                  :loading="eventResetting"
                  :disabled="!userStore.hasPerm('event:rule:edit')"
                  @click="handleEventReset"
                >
                  恢复继承
                </el-button>
                <el-button @click="loadEventConfig">重置表单</el-button>
              </div>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>
