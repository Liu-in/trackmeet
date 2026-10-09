<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createScreenConfig,
  getScreenConfig,
  refreshScreenToken,
  updateScreenConfig,
  type ScreenConfig
} from '@/api/screen'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 大屏配置：轮播/刷新间隔、显示模块开关、主题与状态、访问令牌与匿名大屏地址。
 * 无配置时保存走 createScreenConfig，已有配置走 updateScreenConfig(id)。
 * 权限：screen:config:list（查看）、screen:config:add / screen:config:edit（保存）、screen:token（重置令牌）
 */
defineOptions({ name: 'ScreenConfig' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)

const THEME_OPTIONS = [
  { label: '深色（默认，适合会议室大屏）', value: 'DARK' },
  { label: '浅色', value: 'LIGHT' }
]

const STATUS_OPTIONS = [
  { label: '启用', value: 'ENABLED' },
  { label: '停用', value: 'DISABLED' }
]

const loading = ref(false)
const saving = ref(false)
const refreshingToken = ref(false)
const formRef = ref<FormInstance>()

/** 默认配置：轮播 15 秒、刷新 30 秒、四类面板全开 */
function createDefaultForm(): ScreenConfig {
  return {
    meetId: meetId.value,
    screenName: '运动会大屏',
    rotateSeconds: 15,
    refreshSeconds: 30,
    showAnnouncement: 1,
    showMedal: 1,
    showPoints: 1,
    showResult: 1,
    theme: 'DARK',
    status: 'ENABLED',
    accessToken: '',
    remark: ''
  }
}

const form = reactive<ScreenConfig>(createDefaultForm())

const formRules: FormRules = {
  screenName: [{ max: 100, message: '大屏名称不超过 100 字', trigger: 'blur' }],
  rotateSeconds: [{ required: true, message: '请输入轮播间隔', trigger: 'blur' }],
  refreshSeconds: [{ required: true, message: '请输入刷新间隔', trigger: 'blur' }]
}

const canSave = computed(() => (form.id ? userStore.hasPerm('screen:config:edit') : userStore.hasPerm('screen:config:add')))
const canToken = computed(() => userStore.hasPerm('screen:token'))

/** 至少保留一个显示模块 */
const showModuleCount = computed(
  () => [form.showAnnouncement, form.showMedal, form.showPoints, form.showResult].filter((item) => item === 1).length
)

/* ------------------------------- 数据加载 ------------------------------- */

async function loadConfig() {
  if (!meetId.value) {
    Object.assign(form, createDefaultForm())
    return
  }
  loading.value = true
  try {
    const result = await getScreenConfig(meetId.value)
    if (result && (result.id || result.accessToken || result.screenName || result.status)) {
      // 已有配置：回填全部字段，缺失项按默认值补齐（保留 id，保存走 update）
      Object.assign(form, createDefaultForm(), result, {
        meetId: meetId.value,
        id: result.id,
        screenName: result.screenName || '运动会大屏',
        showAnnouncement: result.showAnnouncement ?? 1,
        showMedal: result.showMedal ?? 1,
        showPoints: result.showPoints ?? 1,
        showResult: result.showResult ?? 1,
        rotateSeconds: result.rotateSeconds ?? 15,
        refreshSeconds: result.refreshSeconds ?? 30,
        theme: result.theme || 'DARK',
        status: result.status || 'ENABLED',
        accessToken: result.accessToken || ''
      })
    } else {
      Object.assign(form, createDefaultForm())
    }
  } catch {
    // 无配置（404/空数据）与网络异常都回退为默认值，错误提示由拦截器处理
    Object.assign(form, createDefaultForm())
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 保存 ------------------------------- */

function buildPayload(): ScreenConfig {
  return {
    meetId: meetId.value,
    screenName: form.screenName?.trim() || '运动会大屏',
    rotateSeconds: Number(form.rotateSeconds) || 15,
    refreshSeconds: Number(form.refreshSeconds) || 30,
    showAnnouncement: form.showAnnouncement ? 1 : 0,
    showMedal: form.showMedal ? 1 : 0,
    showPoints: form.showPoints ? 1 : 0,
    showResult: form.showResult ? 1 : 0,
    theme: form.theme || 'DARK',
    status: form.status || 'ENABLED',
    remark: form.remark || ''
  }
}

async function handleSave() {
  if (!meetId.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canSave.value) {
    ElMessage.warning('当前账号无大屏配置保存权限（screen:config:add / screen:config:edit）')
    return
  }
  if (!formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (showModuleCount.value === 0) {
    ElMessage.warning('请至少开启一个显示模块（公告 / 奖牌榜 / 团体总分 / 最新成绩）')
    return
  }
  saving.value = true
  try {
    const payload = buildPayload()
    if (form.id) {
      await updateScreenConfig(form.id, payload)
      ElMessage.success('大屏配置已保存')
    } else {
      const id = await createScreenConfig(payload)
      if (id) {
        form.id = id
      }
      ElMessage.success('大屏配置已创建')
    }
    await loadConfig()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    saving.value = false
  }
}

function handleReset() {
  formRef.value?.clearValidate()
  void loadConfig()
}

/* ------------------------------- 访问令牌 ------------------------------- */

/** 脱敏显示：前 6 位 + **** + 后 4 位 */
const maskedToken = computed(() => {
  const token = form.accessToken || ''
  if (!token) {
    return '暂无令牌，请点击「重新生成令牌」'
  }
  if (token.length <= 12) {
    return `${token.slice(0, 3)}****${token.slice(-3)}`
  }
  return `${token.slice(0, 6)}****${token.slice(-4)}`
})

const anonymousUrl = computed(() => {
  const origin = window.location.origin
  const meet = meetId.value ?? ''
  const token = form.accessToken || ''
  // 匿名大屏走静态公开路由（不套主框架、无需登录）；应用内查看用菜单里的 /screen/board
  return `${origin}/screen/public-board?meetId=${meet}&token=${token}`
})

/** 复制文本：优先 navigator.clipboard，失败时提示手动复制（令牌区提供明文输入框） */
async function copyText(text: string, tip: string): Promise<void> {
  if (!text) {
    ElMessage.warning('暂无可复制的内容')
    return
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text)
      ElMessage.success(`${tip}已复制到剪贴板`)
      return
    }
    throw new Error('clipboard unavailable')
  } catch {
    ElMessage.warning(`浏览器不允许自动复制，请在下方的明文输入框中手动全选复制（${tip}）`)
  }
}

function handleCopyToken() {
  void copyText(form.accessToken || '', '访问令牌')
}

function handleCopyUrl() {
  void copyText(anonymousUrl.value, '匿名大屏地址')
}

/** 明文令牌输入框获得焦点时全选，方便自动复制失败时手动复制 */
function handleTokenFocus(event: FocusEvent): void {
  const target = event.target as HTMLInputElement | null
  target?.select()
}

function handleOpenUrl() {
  if (!form.accessToken) {
    ElMessage.warning('请先生成访问令牌')
    return
  }
  window.open(anonymousUrl.value, '_blank', 'noopener')
}

async function handleRefreshToken() {
  if (!meetId.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return
  }
  if (!canToken.value) {
    ElMessage.warning('当前账号无重置令牌权限（screen:token）')
    return
  }
  try {
    await ElMessageBox.confirm(
      '重新生成后，旧令牌会立即失效，正在使用旧地址的大屏需要更新为新地址。确定继续吗？',
      '重新生成访问令牌',
      { type: 'warning', confirmButtonText: '确定重新生成', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  refreshingToken.value = true
  try {
    const token = await refreshScreenToken()
    form.accessToken = token || ''
    ElMessage.success('访问令牌已重新生成')
    await loadConfig()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    refreshingToken.value = false
  }
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  await loadConfig()
})
</script>

<template>
  <div class="tm-page" v-loading="loading">
    <div class="tm-card no-print">
      <div class="tm-page-title">大屏配置</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜配置公告大屏的轮播节奏、显示模块与对外访问令牌
      </div>
    </div>

    <el-alert
      v-if="!meetId"
      type="warning"
      :closable="false"
      show-icon
      title="请先在顶栏选择届次，再配置大屏。"
    />

    <!-- 基础配置 -->
    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">基础配置</div>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="140px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="大屏名称">
              <el-input v-model="form.screenName" maxlength="100" placeholder="如：第一届全国田径运动会大屏" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="轮播间隔（秒）" prop="rotateSeconds">
              <el-input-number
                v-model="form.rotateSeconds"
                :min="5"
                :max="120"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="刷新间隔（秒）" prop="refreshSeconds">
              <el-input-number
                v-model="form.refreshSeconds"
                :min="10"
                :max="600"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主题">
              <el-select v-model="form.theme" style="width: 100%">
                <el-option v-for="item in THEME_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
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
            <el-form-item label="显示模块">
              <div class="sc-switch-group">
                <span class="sc-switch-item">
                  <el-switch v-model="form.showAnnouncement" :active-value="1" :inactive-value="0" />
                  <span>公告</span>
                </span>
                <span class="sc-switch-item">
                  <el-switch v-model="form.showMedal" :active-value="1" :inactive-value="0" />
                  <span>奖牌榜</span>
                </span>
                <span class="sc-switch-item">
                  <el-switch v-model="form.showPoints" :active-value="1" :inactive-value="0" />
                  <span>团体总分</span>
                </span>
                <span class="sc-switch-item">
                  <el-switch v-model="form.showResult" :active-value="1" :inactive-value="0" />
                  <span>最新成绩</span>
                </span>
              </div>
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="200" placeholder="选填，如：用于体育馆 LED 屏" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="tm-form-tip">
          轮播间隔 5~120 秒、刷新间隔 10~600 秒；四个显示模块至少开启一个，否则大屏无内容可轮播。
        </div>
      </el-form>

      <div class="tm-toolbar" style="margin-top: 12px">
        <div class="tm-toolbar-actions">
          <el-button type="primary" :loading="saving" :disabled="!meetId || !canSave" @click="handleSave">
            {{ form.id ? '保存配置' : '创建配置' }}
          </el-button>
          <el-button :disabled="!meetId" @click="handleReset">重置</el-button>
        </div>
        <div class="tm-toolbar-actions">
          <span v-if="!canSave" class="tm-form-tip">当前账号无保存权限（screen:config:add / screen:config:edit）</span>
        </div>
      </div>
    </div>

    <!-- 访问令牌 -->
    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">访问令牌</div>
      <div class="sc-token-row">
        <span class="sc-token-label">当前令牌：</span>
        <span class="sc-token-value">{{ maskedToken }}</span>
        <el-button size="small" :disabled="!form.accessToken" @click="handleCopyToken">复制令牌</el-button>
        <el-button
          size="small"
          type="warning"
          plain
          :loading="refreshingToken"
          :disabled="!meetId || !canToken"
          @click="handleRefreshToken"
        >
          重新生成令牌
        </el-button>
        <span v-if="!canToken" class="tm-form-tip">需 screen:token 权限</span>
      </div>
      <div class="sc-token-plain">
        <span class="sc-token-label">明文令牌：</span>
        <el-input v-model="form.accessToken" readonly placeholder="暂无令牌" @focus="handleTokenFocus" />
      </div>
      <div class="tm-form-tip">
        令牌用于外接大屏设备免登录访问。自动复制失败时（浏览器限制），可在上方明文输入框中全选复制；重新生成后旧令牌立即失效。
      </div>
    </div>

    <!-- 匿名大屏地址 -->
    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">匿名大屏地址</div>
      <div class="sc-url-row">
        <el-input v-model="anonymousUrl" readonly />
        <el-button :disabled="!form.accessToken" @click="handleCopyUrl">复制地址</el-button>
        <el-button type="primary" :disabled="!form.accessToken" @click="handleOpenUrl">新窗口打开</el-button>
      </div>
      <div class="tm-form-tip">
        该地址可直接在会议室大屏的浏览器中打开，无需登录。请确认大屏设备与本系统网络互通，并保持届次一致（地址中的 meetId={{ meetId ?? '未选择' }}）。
      </div>
    </div>
  </div>
</template>

<style scoped>
.sc-switch-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 18px;
  align-items: center;
}

.sc-switch-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.sc-token-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.sc-token-label {
  color: #606266;
  font-size: 13px;
}

.sc-token-value {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  letter-spacing: 1px;
  padding: 2px 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.sc-token-plain {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sc-url-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.sc-url-row .el-input {
  flex: 1;
  min-width: 320px;
}
</style>
