<script setup lang="ts">
import { computed, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getPublicScreenBoard, getScreenBoard, type Announcement, type ScreenBoard, type ScreenConfig } from '@/api/screen'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 大屏公告（整屏暗色轮播）
 * 取数模式：
 *   1) URL 带 token → 匿名接口 getPublicScreenBoard(meetId, token)，供会议室大屏免登录使用；
 *   2) 无 token → 登录接口 getScreenBoard(meetId)，需要 screen:board 权限。
 * 轮播：公告 / 奖牌榜 / 团体总分 / 最新成绩，按 ScreenConfig 的 showXxx 组合、rotateSeconds 切换。
 */
defineOptions({ name: 'ScreenBoard' })

const route = useRoute()
const router = useRouter()
const meetStore = useMeetStore()
const userStore = useUserStore()

/** 轮播面板标识 */
type PanelKey = 'announcement' | 'medal' | 'points' | 'result'

interface PanelMeta {
  key: PanelKey
  label: string
  icon: string
}

const ALL_PANELS: PanelMeta[] = [
  { key: 'announcement', label: '公告', icon: '📢' },
  { key: 'medal', label: '奖牌榜', icon: '🏅' },
  { key: 'points', label: '团体总分', icon: '📊' },
  { key: 'result', label: '最新成绩', icon: '⏱' }
]

const PANEL_LABEL: Record<PanelKey, string> = {
  announcement: '公告',
  medal: '奖牌榜',
  points: '团体总分',
  result: '最新成绩'
}

/* --------------------------------- 取数模式 --------------------------------- */

function firstQuery(value: unknown): string {
  if (Array.isArray(value)) {
    return typeof value[0] === 'string' ? value[0] : ''
  }
  return typeof value === 'string' ? value : ''
}

const urlToken = firstQuery(route.query.token).trim()
const urlMeetId = Number(firstQuery(route.query.meetId)) || undefined
const meetId = computed(() => urlMeetId || meetStore.currentMeetId)
const anonymousMode = computed(() => urlToken.length > 0)

/* --------------------------------- 页面状态 --------------------------------- */

/** 根节点（keep-alive 缓存本页面时用于隐藏，避免遮挡其他页面） */
const boardRoot = ref<HTMLElement | null>(null)
const loading = ref(false)
const refreshing = ref(false)
const errorText = ref('')
const board = ref<ScreenBoard>({})
const config = ref<ScreenConfig>({})
/** 轮播总开关（暂停/继续） */
const rotating = ref(true)
/** 全屏状态（跟随浏览器 fullscreenchange 事件） */
const isFullscreen = ref(false)
const clock = ref(new Date())
const lastRefreshAt = ref<Date | undefined>(undefined)
const activePanelIndex = ref(0)
const announcementIndex = ref(0)

/** 默认配置：轮播 15 秒、刷新 30 秒、四类面板全开 */
const DEFAULT_CONFIG: Required<Pick<ScreenConfig,
  'rotateSeconds' | 'refreshSeconds' | 'showAnnouncement' | 'showMedal' | 'showPoints' | 'showResult' | 'theme' | 'status'>> = {
  rotateSeconds: 15,
  refreshSeconds: 30,
  showAnnouncement: 1,
  showMedal: 1,
  showPoints: 1,
  showResult: 1,
  theme: 'DARK',
  status: 'ENABLED'
}

function clampNumber(value: unknown, min: number, max: number, fallback: number): number {
  const num = Number(value)
  if (!Number.isFinite(num) || num <= 0) {
    return fallback
  }
  return Math.min(Math.max(num, min), max)
}

const rotateSeconds = computed(() => clampNumber(config.value.rotateSeconds, 5, 120, DEFAULT_CONFIG.rotateSeconds))
const refreshSeconds = computed(() => clampNumber(config.value.refreshSeconds, 10, 600, DEFAULT_CONFIG.refreshSeconds))

/* --------------------------------- 面板组合 --------------------------------- */

const panels = computed<PanelMeta[]>(() => {
  const cfg = config.value
  const showAnnouncement = cfg.showAnnouncement === undefined ? DEFAULT_CONFIG.showAnnouncement : cfg.showAnnouncement
  const showMedal = cfg.showMedal === undefined ? DEFAULT_CONFIG.showMedal : cfg.showMedal
  const showPoints = cfg.showPoints === undefined ? DEFAULT_CONFIG.showPoints : cfg.showPoints
  const showResult = cfg.showResult === undefined ? DEFAULT_CONFIG.showResult : cfg.showResult
  const flags: Record<PanelKey, number> = {
    announcement: showAnnouncement,
    medal: showMedal,
    points: showPoints,
    result: showResult
  }
  return ALL_PANELS.filter((panel) => flags[panel.key] !== 0)
})

const activePanel = computed<PanelMeta | undefined>(() => panels.value[activePanelIndex.value])

/* --------------------------------- 数据清洗 --------------------------------- */

function num(value: unknown): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

/** 去除 <script> 标签，避免公告正文注入脚本 */
function sanitizeHtml(html?: string): string {
  const raw = html || ''
  return raw.replace(/<script[\s\S]*?<\/script>/gi, '')
}

/** 公告排序：置顶优先，其次 sortNo → 发布时间倒序 → id */
const announcementList = computed<Announcement[]>(() => {
  const list = Array.isArray(board.value.announcements) ? [...board.value.announcements] : []
  return list.sort((a, b) => {
    const topDiff = num(b.isTop) - num(a.isTop)
    if (topDiff !== 0) {
      return topDiff
    }
    const sortDiff = num(a.sortNo) - num(b.sortNo)
    if (sortDiff !== 0) {
      return sortDiff
    }
    const timeDiff = String(b.publishTime || '').localeCompare(String(a.publishTime || ''))
    if (timeDiff !== 0) {
      return timeDiff
    }
    return num(a.id) - num(b.id)
  })
})

const currentAnnouncement = computed<Announcement | undefined>(() => {
  const list = announcementList.value
  if (list.length === 0) {
    return undefined
  }
  const index = Math.min(announcementIndex.value, list.length - 1)
  return list[index]
})

const announcementHtml = computed(() => sanitizeHtml(currentAnnouncement.value?.contentHtml || ''))

const medals = computed(() => (Array.isArray(board.value.medals) ? board.value.medals.slice(0, 20) : []))
const teamScores = computed(() => (Array.isArray(board.value.teamScores) ? board.value.teamScores.slice(0, 20) : []))
const latestResults = computed(() => (Array.isArray(board.value.latestResults) ? board.value.latestResults.slice(0, 20) : []))

const maxGold = computed(() => medals.value.reduce((max, row) => Math.max(max, num(row.gold)), 0))
const maxPoint = computed(() => teamScores.value.reduce((max, row) => Math.max(max, num(row.totalPoint)), 0))

const hasAnyData = computed(
  () =>
    announcementList.value.length > 0 ||
    medals.value.length > 0 ||
    teamScores.value.length > 0 ||
    latestResults.value.length > 0
)

const emptyText = '暂无数据，请先在公告管理中发布公告或完成成绩审核'

const meetInfo = computed(() => board.value.meetInfo || {})

const meetName = computed(() => meetInfo.value.meetName || meetStore.currentMeet?.meetName || '田径运动会')

const meetDateRange = computed(() => {
  const start = meetInfo.value.startDate || ''
  const end = meetInfo.value.endDate || ''
  if (start && end) {
    return `${start} ~ ${end}`
  }
  return start || end || ''
})

const venue = computed(() => meetInfo.value.venue || '')

/* --------------------------------- 展示工具 --------------------------------- */

const clockText = computed(() =>
  clock.value.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit', second: '2-digit' })
)

const clockDateText = computed(() => {
  const date = clock.value
  const week = ['日', '一', '二', '三', '四', '五', '六'][date.getDay()]
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} 周${week}`
})

const updatedAtText = computed(() => {
  const source = board.value.updatedAt
  const date = lastRefreshAt.value
  if (!date) {
    return source ? formatDateTime(source) : '-'
  }
  return date.toLocaleTimeString('zh-CN', { hour12: false })
})

function formatDateTime(value?: string): string {
  if (!value) {
    return '-'
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }
  return date.toLocaleString('zh-CN', { hour12: false })
}

function formatWind(value?: number): string {
  if (value === undefined || value === null || !Number.isFinite(Number(value))) {
    return '-'
  }
  const num = Number(value)
  return `${num > 0 ? '+' : ''}${num.toFixed(1)} m/s`
}

function isRecord(row: { recordCandidate?: unknown; remark?: unknown; eventName?: unknown }): boolean {
  if (row.recordCandidate === 1 || row.recordCandidate === true) {
    return true
  }
  return String(row.remark || '').includes('破')
}

function medalBarWidth(gold: unknown): string {
  const value = num(gold)
  const max = maxGold.value
  if (max <= 0 || value <= 0) {
    return '0%'
  }
  return `${Math.max((value / max) * 100, 6)}%`
}

function pointBarWidth(point: unknown): string {
  const value = num(point)
  const max = maxPoint.value
  if (max <= 0 || value <= 0) {
    return '0%'
  }
  return `${Math.max((value / max) * 100, 4)}%`
}

function rankClass(rank?: number): string {
  if (rank === 1) {
    return 'is-first'
  }
  if (rank === 2) {
    return 'is-second'
  }
  if (rank === 3) {
    return 'is-third'
  }
  return ''
}

/* --------------------------------- 数据加载 --------------------------------- */

/** 失败后 10 秒自动重试 */
const RETRY_DELAY = 10_000

let reloadTimer: ReturnType<typeof setInterval> | undefined
let rotateTimer: ReturnType<typeof setInterval> | undefined
let announcementTimer: ReturnType<typeof setTimeout> | undefined
let clockTimer: ReturnType<typeof setInterval> | undefined
let retryTimer: ReturnType<typeof setTimeout> | undefined

/** 被 keep-alive 缓存（deactivated）时停止一切定时器与重试 */
const active = ref(false)

function clearRetryTimer(): void {
  if (retryTimer) {
    clearTimeout(retryTimer)
    retryTimer = undefined
  }
}

function scheduleRetry(): void {
  clearRetryTimer()
  if (!active.value) {
    return
  }
  retryTimer = setTimeout(() => {
    retryTimer = undefined
    void loadData(false)
  }, RETRY_DELAY)
}

function startReloadTimer(): void {
  if (reloadTimer) {
    clearInterval(reloadTimer)
    reloadTimer = undefined
  }
  if (!active.value) {
    return
  }
  reloadTimer = setInterval(() => {
    void loadData(false)
  }, refreshSeconds.value * 1000)
}

async function loadData(manual = true): Promise<void> {
  if (!active.value) {
    return
  }
  if (!meetId.value) {
    errorText.value = '未选择届次，无法加载大屏数据'
    board.value = {}
    return
  }
  if (!anonymousMode.value && !userStore.hasPerm('screen:board')) {
    errorText.value = '当前账号无大屏查看权限（screen:board）'
    scheduleRetry()
    return
  }
  if (manual) {
    refreshing.value = true
  } else {
    loading.value = true
  }
  errorText.value = ''
  try {
    const result = anonymousMode.value
      ? await getPublicScreenBoard(meetId.value, urlToken)
      : await getScreenBoard(meetId.value)
    board.value = result || {}
    config.value = result?.config || {}
    if (activePanelIndex.value > Math.max(panels.value.length - 1, 0)) {
      activePanelIndex.value = 0
    }
    if (announcementIndex.value > Math.max(announcementList.value.length - 1, 0)) {
      announcementIndex.value = 0
    }
    lastRefreshAt.value = new Date()
    clearRetryTimer()
  } catch {
    // 具体错误提示由 request 拦截器统一处理
    errorText.value = '数据加载失败，将自动重试'
    scheduleRetry()
  } finally {
    refreshing.value = false
    loading.value = false
  }
}

/* --------------------------------- 轮播控制 --------------------------------- */

function clearRotateTimer(): void {
  if (rotateTimer) {
    clearInterval(rotateTimer)
    rotateTimer = undefined
  }
}

function clearAnnouncementTimer(): void {
  if (announcementTimer) {
    clearTimeout(announcementTimer)
    announcementTimer = undefined
  }
}

/** 当前公告在大屏上的停留秒数（5~120，缺省 5） */
function announcementDuration(): number {
  return clampNumber(currentAnnouncement.value?.screenDuration, 5, 120, 5)
}

function advanceAnnouncement(): void {
  const total = announcementList.value.length
  if (total <= 1) {
    return
  }
  announcementIndex.value = (announcementIndex.value + 1) % total
}

function startAnnouncementTimer(): void {
  clearAnnouncementTimer()
  if (!active.value || !rotating.value || activePanel.value?.key !== 'announcement' || announcementList.value.length <= 1) {
    return
  }
  announcementTimer = setTimeout(() => {
    announcementTimer = undefined
    advanceAnnouncement()
    startAnnouncementTimer()
  }, announcementDuration() * 1000)
}

function startRotateTimer(): void {
  clearRotateTimer()
  if (!active.value || !rotating.value || panels.value.length <= 1) {
    return
  }
  rotateTimer = setInterval(() => {
    activePanelIndex.value = (activePanelIndex.value + 1) % panels.value.length
    onPanelChanged()
  }, rotateSeconds.value * 1000)
}

function onPanelChanged(): void {
  if (activePanel.value?.key !== 'announcement') {
    announcementIndex.value = 0
  }
  startAnnouncementTimer()
}

/** 手动切换面板：重置自动轮播计时 */
function selectPanel(index: number): void {
  if (index < 0 || index >= panels.value.length) {
    return
  }
  activePanelIndex.value = index
  onPanelChanged()
  startRotateTimer()
}

function toggleRotate(): void {
  rotating.value = !rotating.value
  if (rotating.value) {
    startRotateTimer()
    startAnnouncementTimer()
  } else {
    clearRotateTimer()
    clearAnnouncementTimer()
  }
}

async function handleManualRefresh(): Promise<void> {
  await loadData(true)
  if (!errorText.value) {
    startRotateTimer()
    startAnnouncementTimer()
  }
}

/* --------------------------------- 全屏控制 --------------------------------- */

function syncFullscreen(): void {
  isFullscreen.value = !!document.fullscreenElement
}

async function toggleFullscreen(): Promise<void> {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else {
      await document.documentElement.requestFullscreen()
    }
  } catch {
    // 浏览器拒绝（非用户手势等）时忽略，保持当前状态
  } finally {
    syncFullscreen()
  }
}

function handleKeydown(event: KeyboardEvent): void {
  const target = event.target as HTMLElement | null
  const tag = target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA' || target?.isContentEditable) {
    return
  }
  if (event.key === 'f' || event.key === 'F') {
    event.preventDefault()
    void toggleFullscreen()
  } else if (event.key === 'Escape' && document.fullscreenElement) {
    void document.exitFullscreen().catch(() => undefined)
  }
}

/* --------------------------------- 生命周期 --------------------------------- */

function exitBoard(): void {
  router.push('/dashboard').catch(() => undefined)
}

/** 挂载/取消挂载根节点（keep-alive 缓存本页面时避免遮挡其他页面） */
function mountRoot(visible: boolean): void {
  const el = boardRoot.value
  if (!el) {
    return
  }
  el.style.display = visible ? '' : 'none'
}

function startAllTimers(): void {
  startReloadTimer()
  startRotateTimer()
  startAnnouncementTimer()
}

function stopAllTimers(): void {
  if (reloadTimer) {
    clearInterval(reloadTimer)
    reloadTimer = undefined
  }
  if (clockTimer) {
    clearInterval(clockTimer)
    clockTimer = undefined
  }
  clearRotateTimer()
  clearAnnouncementTimer()
  clearRetryTimer()
}

onMounted(async () => {
  active.value = true
  if (!meetStore.loaded && !urlMeetId) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  clockTimer = setInterval(() => {
    clock.value = new Date()
  }, 1000)
  await loadData(false)
  startAllTimers()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', syncFullscreen)
  syncFullscreen()
})

onActivated(() => {
  active.value = true
  mountRoot(true)
  clock.value = new Date()
  clockTimer = setInterval(() => {
    clock.value = new Date()
  }, 1000)
  void loadData(false).then(() => {
    startAllTimers()
  })
  syncFullscreen()
})

onDeactivated(() => {
  active.value = false
  mountRoot(false)
  stopAllTimers()
})

onUnmounted(() => {
  active.value = false
  mountRoot(false)
  stopAllTimers()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', syncFullscreen)
})

/** 大屏配置变化时重建刷新定时器 */
watch(refreshSeconds, () => {
  startReloadTimer()
})

/** 首次渲染即贴合 active 状态（keep-alive 缓存期间避免遮挡其他页面） */
watch(
  boardRoot,
  (el) => {
    if (el) {
      el.style.display = active.value ? '' : 'none'
    }
  },
  { flush: 'post' }
)
</script>

<template>
  <div ref="boardRoot" class="screen-board">
    <!-- 顶部栏 -->
    <header class="sb-header no-print">
      <div class="sb-header-main">
        <div class="sb-meet">{{ meetName }}</div>
        <div class="sb-meta">
          <span v-if="meetDateRange">📅 {{ meetDateRange }}</span>
          <span v-if="venue">📍 {{ venue }}</span>
          <span v-if="anonymousMode" class="sb-mode">匿名令牌访问</span>
          <span v-else class="sb-mode">登录访问</span>
        </div>
      </div>

      <div class="sb-header-clock">
        <div class="sb-clock">{{ clockText }}</div>
        <div class="sb-clock-date">{{ clockDateText }}</div>
      </div>

      <div class="sb-header-actions">
        <span class="sb-updated">更新：{{ updatedAtText }}</span>
        <el-button size="small" type="primary" plain @click="toggleFullscreen">
          {{ isFullscreen ? '退出全屏' : '全屏显示' }}
        </el-button>
        <el-button size="small" type="primary" plain :loading="refreshing" @click="handleManualRefresh">手动刷新</el-button>
        <el-button size="small" type="primary" plain @click="toggleRotate">
          {{ rotating ? '暂停轮播' : '继续轮播' }}
        </el-button>
        <el-button size="small" plain @click="exitBoard">退出大屏</el-button>
      </div>
    </header>

    <!-- 状态提示 -->
    <el-alert
      v-if="errorText"
      class="sb-alert no-print"
      :title="errorText"
      type="error"
      :closable="false"
      show-icon
      effect="dark"
    />
    <div v-if="!hasAnyData && !loading && !errorText" class="sb-empty no-print">{{ emptyText }}</div>

    <!-- 主体轮播 -->
    <main v-loading="loading" class="sb-main" element-loading-background="rgba(6, 16, 33, 0.75)">
      <section v-if="activePanel" :key="activePanel.key" class="sb-panel">
        <div class="sb-panel-title">
          <span class="sb-panel-icon">{{ activePanel.icon }}</span>
          {{ activePanel.label }}
          <span v-if="activePanel.key === 'announcement' && announcementList.length > 1" class="sb-panel-sub">
            第 {{ Math.min(announcementIndex + 1, announcementList.length) }} / {{ announcementList.length }} 条
          </span>
        </div>

        <!-- 公告面板 -->
        <div v-if="activePanel.key === 'announcement'" class="sb-panel-body sb-announcement">
          <template v-if="currentAnnouncement">
            <div class="sb-announcement-title">
              <span v-if="num(currentAnnouncement.isTop) === 1" class="sb-top-tag">顶</span>
              {{ currentAnnouncement.title || '公告' }}
            </div>
            <div class="sb-announcement-content" v-html="announcementHtml" />
            <div class="sb-announcement-time">
              发布时间：{{ formatDateTime(currentAnnouncement.publishTime) }}
              <span v-if="currentAnnouncement.publisherName">｜发布人：{{ currentAnnouncement.publisherName }}</span>
            </div>
          </template>
          <div v-else class="sb-panel-empty">{{ emptyText }}</div>
        </div>

        <!-- 奖牌榜面板 -->
        <div v-else-if="activePanel.key === 'medal'" class="sb-panel-body">
          <template v-if="medals.length > 0">
            <div class="sb-table sb-medal-table">
              <div class="sb-row sb-row-head">
                <div class="sb-cell sb-col-rank">名次</div>
                <div class="sb-cell sb-col-name">单位</div>
                <div class="sb-cell sb-col-num is-gold">金</div>
                <div class="sb-cell sb-col-num is-silver">银</div>
                <div class="sb-cell sb-col-num is-bronze">铜</div>
                <div class="sb-cell sb-col-num">总数</div>
                <div class="sb-cell sb-col-bar">金牌占比</div>
              </div>
              <div v-for="(row, index) in medals" :key="index" class="sb-row" :class="rankClass(row.rankNo)">
                <div class="sb-cell sb-col-rank">
                  <span class="sb-rank-badge">{{ row.rankNo ?? index + 1 }}</span>
                </div>
                <div class="sb-cell sb-col-name">{{ row.unitName || '-' }}</div>
                <div class="sb-cell sb-col-num is-gold">{{ num(row.gold) }}</div>
                <div class="sb-cell sb-col-num is-silver">{{ num(row.silver) }}</div>
                <div class="sb-cell sb-col-num is-bronze">{{ num(row.bronze) }}</div>
                <div class="sb-cell sb-col-num sb-total">{{ num(row.totalMedal) || num(row.gold) + num(row.silver) + num(row.bronze) }}</div>
                <div class="sb-cell sb-col-bar">
                  <div class="sb-bar-track">
                    <div class="sb-bar-fill is-gold" :style="{ width: medalBarWidth(row.gold) }" />
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="sb-panel-empty">{{ emptyText }}</div>
        </div>

        <!-- 团体总分面板 -->
        <div v-else-if="activePanel.key === 'points'" class="sb-panel-body">
          <template v-if="teamScores.length > 0">
            <div class="sb-table sb-points-table">
              <div class="sb-row sb-row-head">
                <div class="sb-cell sb-col-rank">名次</div>
                <div class="sb-cell sb-col-name">单位</div>
                <div class="sb-cell sb-col-point">总分</div>
                <div class="sb-cell sb-col-bar">得分占比</div>
              </div>
              <div v-for="(row, index) in teamScores" :key="index" class="sb-row" :class="rankClass(row.rankNo)">
                <div class="sb-cell sb-col-rank">
                  <span class="sb-rank-badge">{{ row.rankNo ?? index + 1 }}</span>
                </div>
                <div class="sb-cell sb-col-name">{{ row.unitName || '-' }}</div>
                <div class="sb-cell sb-col-point">{{ num(row.totalPoint) }}</div>
                <div class="sb-cell sb-col-bar">
                  <div class="sb-bar-track">
                    <div class="sb-bar-fill is-point" :style="{ width: pointBarWidth(row.totalPoint) }" />
                  </div>
                </div>
              </div>
            </div>
          </template>
          <div v-else class="sb-panel-empty">{{ emptyText }}</div>
        </div>

        <!-- 最新成绩面板 -->
        <div v-else class="sb-panel-body">
          <template v-if="latestResults.length > 0">
            <div class="sb-table sb-result-table">
              <div class="sb-row sb-row-head">
                <div class="sb-cell sb-col-event">项目</div>
                <div class="sb-cell sb-col-round">轮次</div>
                <div class="sb-cell sb-col-rank">名次</div>
                <div class="sb-cell sb-col-name">姓名</div>
                <div class="sb-cell sb-col-unit">单位</div>
                <div class="sb-cell sb-col-result">成绩</div>
                <div class="sb-cell sb-col-wind">风速</div>
              </div>
              <div
                v-for="(row, index) in latestResults"
                :key="index"
                class="sb-row"
                :class="[rankClass(row.rankNo), { 'is-record': isRecord(row) }]"
              >
                <div class="sb-cell sb-col-event">
                  {{ row.eventName || '-' }}
                  <span v-if="isRecord(row)" class="sb-record-tag">破纪录</span>
                </div>
                <div class="sb-cell sb-col-round">{{ row.roundName || '-' }}</div>
                <div class="sb-cell sb-col-rank">{{ row.rankNo ?? '-' }}</div>
                <div class="sb-cell sb-col-name">{{ row.athleteName || '-' }}</div>
                <div class="sb-cell sb-col-unit">{{ row.unitName || '-' }}</div>
                <div class="sb-cell sb-col-result">{{ row.resultRaw || '-' }}</div>
                <div class="sb-cell sb-col-wind">{{ formatWind(row.windSpeed) }}</div>
              </div>
            </div>
          </template>
          <div v-else class="sb-panel-empty">{{ emptyText }}</div>
        </div>
      </section>
    </main>

    <!-- 底部指示点 -->
    <footer class="sb-footer no-print">
      <div class="sb-dots">
        <button
          v-for="(panel, index) in panels"
          :key="panel.key"
          type="button"
          class="sb-dot"
          :class="{ 'is-active': index === activePanelIndex }"
          @click="selectPanel(index)"
        >
          <span class="sb-dot-icon">{{ panel.icon }}</span>
          <span class="sb-dot-label">{{ panel.label }}</span>
        </button>
      </div>
      <div class="sb-footer-tip">
        {{ rotating ? `每 ${rotateSeconds} 秒自动轮播` : '轮播已暂停' }}｜数据每 {{ refreshSeconds }} 秒自动刷新｜按 F 键全屏，Esc 退出全屏
      </div>
    </footer>
  </div>
</template>

<style scoped>
.screen-board {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 0;
  overflow: hidden;
  background: linear-gradient(135deg, #0b1a33, #122a4d);
  color: #eaf2ff;
  font-size: clamp(14px, 0.95vw, 22px);
}

.screen-board * {
  box-sizing: border-box;
}

/* ---------------- 顶部栏 ---------------- */
.sb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: clamp(12px, 1.2vw, 32px);
  padding: clamp(10px, 1vh, 22px) clamp(16px, 1.6vw, 44px);
  border-bottom: 1px solid rgba(120, 190, 255, 0.22);
  background: rgba(8, 22, 44, 0.72);
}

.sb-header-main {
  min-width: 0;
}

.sb-meet {
  font-size: clamp(20px, 1.85vw, 46px);
  font-weight: 700;
  letter-spacing: 0.06em;
  color: #ffffff;
  text-shadow: 0 0 18px rgba(64, 158, 255, 0.55);
  line-height: 1.15;
}

.sb-meta {
  display: flex;
  flex-wrap: wrap;
  gap: clamp(10px, 1vw, 26px);
  margin-top: 0.35em;
  font-size: clamp(12px, 0.82vw, 18px);
  color: #9dc4f0;
}

.sb-mode {
  padding: 0 0.5em;
  border: 1px solid rgba(64, 158, 255, 0.5);
  border-radius: 999px;
  color: #7fc0ff;
}

.sb-header-clock {
  text-align: center;
  flex-shrink: 0;
}

.sb-clock {
  font-size: clamp(26px, 2.35vw, 58px);
  font-weight: 700;
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.05em;
  color: #6fe3ff;
  text-shadow: 0 0 22px rgba(64, 200, 255, 0.5);
  line-height: 1.1;
}

.sb-clock-date {
  font-size: clamp(12px, 0.82vw, 18px);
  color: #9dc4f0;
}

.sb-header-actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  flex-shrink: 0;
}

.sb-updated {
  font-size: clamp(12px, 0.78vw, 16px);
  color: #9dc4f0;
  margin-right: 4px;
}

/* ---------------- 提示 ---------------- */
.sb-alert {
  margin: clamp(6px, 0.8vh, 14px) clamp(16px, 1.6vw, 44px) 0;
}

.sb-empty {
  margin: clamp(6px, 0.8vh, 14px) clamp(16px, 1.6vw, 44px) 0;
  padding: 0.6em 1em;
  border-left: 4px solid #e6a23c;
  background: rgba(230, 162, 60, 0.14);
  color: #ffd591;
  font-size: clamp(13px, 0.88vw, 20px);
}

/* ---------------- 主体面板 ---------------- */
.sb-main {
  flex: 1;
  min-height: 0;
  padding: clamp(8px, 1vh, 20px) clamp(16px, 1.6vw, 44px);
  display: flex;
}

.sb-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 1vh, 18px);
  animation: sb-fade 0.5s ease;
}

@keyframes sb-fade {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.sb-panel-title {
  display: flex;
  align-items: center;
  gap: 0.5em;
  font-size: clamp(18px, 1.5vw, 38px);
  font-weight: 700;
  color: #ffffff;
  padding-left: 0.6em;
  border-left: 6px solid #409eff;
  line-height: 1.2;
}

.sb-panel-icon {
  font-size: 1em;
}

.sb-panel-sub {
  font-size: clamp(12px, 0.8vw, 18px);
  font-weight: 400;
  color: #9dc4f0;
}

.sb-panel-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 10px;
  background: rgba(10, 28, 54, 0.55);
  border: 1px solid rgba(120, 190, 255, 0.18);
  padding: clamp(10px, 1.2vh, 24px) clamp(12px, 1.2vw, 34px);
}

.sb-panel-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #9dc4f0;
  font-size: clamp(15px, 1.05vw, 26px);
}

/* ---------------- 公告面板 ---------------- */
.sb-announcement {
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 1.2vh, 20px);
  overflow: auto;
}

.sb-announcement-title {
  font-size: clamp(22px, 1.9vw, 48px);
  font-weight: 700;
  color: #ffe9a8;
  text-align: center;
  line-height: 1.25;
}

.sb-top-tag {
  display: inline-block;
  margin-right: 0.5em;
  padding: 0 0.35em;
  border-radius: 6px;
  background: #f56c6c;
  color: #fff;
  font-size: 0.5em;
  vertical-align: middle;
}

.sb-announcement-content {
  flex: 1;
  font-size: clamp(16px, 1.25vw, 32px);
  line-height: 1.75;
  color: #e6f1ff;
  word-break: break-word;
}

.sb-announcement-content :deep(img) {
  max-width: 100%;
}

.sb-announcement-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
}

.sb-announcement-content :deep(th),
.sb-announcement-content :deep(td) {
  border: 1px solid rgba(150, 200, 255, 0.4);
  padding: 0.3em 0.5em;
}

.sb-announcement-content :deep(a) {
  color: #6fe3ff;
}

.sb-announcement-time {
  text-align: right;
  font-size: clamp(12px, 0.82vw, 18px);
  color: #9dc4f0;
}

/* ---------------- 排行类表格 ---------------- */
.sb-table {
  height: 100%;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 0.25em;
}

.sb-row {
  display: grid;
  align-items: center;
  gap: 0.5em;
  padding: 0.28em 0.5em;
  border-radius: 6px;
  font-size: clamp(13px, 0.92vw, 22px);
  background: rgba(255, 255, 255, 0.03);
}

.sb-row.is-first {
  background: rgba(255, 215, 0, 0.16);
  box-shadow: inset 0 0 0 1px rgba(255, 215, 0, 0.45);
}

.sb-row.is-second {
  background: rgba(192, 204, 218, 0.14);
  box-shadow: inset 0 0 0 1px rgba(192, 204, 218, 0.4);
}

.sb-row.is-third {
  background: rgba(205, 127, 50, 0.16);
  box-shadow: inset 0 0 0 1px rgba(205, 127, 50, 0.45);
}

.sb-row.is-record {
  background: rgba(245, 108, 108, 0.18);
  box-shadow: inset 0 0 0 1px rgba(245, 108, 108, 0.5);
}

.sb-row-head {
  background: rgba(64, 158, 255, 0.22);
  font-weight: 700;
  color: #d6ecff;
  position: sticky;
  top: 0;
  z-index: 2;
}

.sb-cell {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sb-rank-badge {
  display: inline-block;
  min-width: 1.9em;
  padding: 0 0.3em;
  text-align: center;
  border-radius: 6px;
  background: rgba(64, 158, 255, 0.25);
  font-weight: 700;
}

.is-first .sb-rank-badge {
  background: #ffd700;
  color: #4a3a00;
}

.is-second .sb-rank-badge {
  background: #c0ccda;
  color: #2c3542;
}

.is-third .sb-rank-badge {
  background: #cd7f32;
  color: #2b1600;
}

/* 奖牌榜列宽 */
.sb-medal-table .sb-row {
  grid-template-columns: 5em minmax(6em, 1fr) 4em 4em 4em 4.5em minmax(6em, 1.4fr);
}

.sb-points-table .sb-row {
  grid-template-columns: 5em minmax(6em, 1.4fr) 6em minmax(8em, 2fr);
}

.sb-result-table .sb-row {
  grid-template-columns: minmax(8em, 1.6fr) 6em 4.5em minmax(5em, 1fr) minmax(6em, 1.3fr) 7em 6.5em;
}

.sb-col-num,
.sb-col-rank,
.sb-col-point,
.sb-col-wind {
  text-align: center;
}

.sb-col-num.is-gold {
  color: #ffd700;
  font-weight: 700;
}

.sb-col-num.is-silver {
  color: #dfe6ef;
  font-weight: 700;
}

.sb-col-num.is-bronze {
  color: #e0a165;
  font-weight: 700;
}

.sb-total {
  color: #6fe3ff;
  font-weight: 700;
}

.sb-col-point {
  color: #6fe3ff;
  font-weight: 700;
}

.sb-col-result {
  color: #ffe9a8;
  font-weight: 700;
  text-align: center;
}

.sb-record-tag {
  margin-left: 0.4em;
  padding: 0 0.35em;
  border-radius: 4px;
  background: #f56c6c;
  color: #fff;
  font-size: 0.72em;
  font-weight: 700;
}

.sb-bar-track {
  height: 0.75em;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.1);
  overflow: hidden;
}

.sb-bar-fill {
  height: 100%;
  border-radius: 999px;
  transition: width 0.6s ease;
}

.sb-bar-fill.is-gold {
  background: linear-gradient(90deg, #ffb800, #ffe58f);
}

.sb-bar-fill.is-point {
  background: linear-gradient(90deg, #1f6feb, #6fe3ff);
}

/* ---------------- 底部指示点 ---------------- */
.sb-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: clamp(8px, 0.9vh, 18px) clamp(16px, 1.6vw, 44px);
  border-top: 1px solid rgba(120, 190, 255, 0.22);
  background: rgba(8, 22, 44, 0.72);
}

.sb-dots {
  display: flex;
  align-items: center;
  gap: clamp(8px, 0.8vw, 20px);
}

.sb-dot {
  display: flex;
  align-items: center;
  gap: 0.4em;
  padding: 0.3em 0.85em;
  border: 1px solid rgba(120, 190, 255, 0.35);
  border-radius: 999px;
  background: transparent;
  color: #9dc4f0;
  font-size: clamp(12px, 0.85vw, 19px);
  cursor: pointer;
  transition: all 0.25s ease;
}

.sb-dot:hover {
  border-color: #409eff;
  color: #d6ecff;
}

.sb-dot.is-active {
  background: rgba(64, 158, 255, 0.28);
  border-color: #6fe3ff;
  color: #ffffff;
  box-shadow: 0 0 16px rgba(64, 200, 255, 0.35);
}

.sb-dot-icon {
  font-size: 1.05em;
}

.sb-footer-tip {
  font-size: clamp(11px, 0.76vw, 16px);
  color: #9dc4f0;
}

@media (max-width: 1280px) {
  .sb-header {
    flex-wrap: wrap;
  }

  .sb-result-table .sb-row,
  .sb-medal-table .sb-row {
    grid-template-columns: 4em minmax(5em, 1fr) 3em 3em 3em 4em;
  }

  .sb-result-table .sb-row {
    grid-template-columns: minmax(6em, 1.4fr) 4.5em 3.5em minmax(4em, 1fr) minmax(5em, 1.2fr) 5.5em 5em;
  }
}
</style>
