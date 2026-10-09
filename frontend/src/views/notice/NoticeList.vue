<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import {
  createAnnouncement,
  deleteAnnouncement,
  getAnnouncement,
  pageAnnouncements,
  publishAnnouncement,
  updateAnnouncement,
  type Announcement,
  type AnnouncementQuery
} from '@/api/screen'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 公告管理：公告的检索、新增/编辑、发布/下线、删除。
 * 权限：notice:list（查看）、notice:add / notice:edit / notice:delete / notice:publish（按钮）
 * 大屏联动：screenShow=1 且状态为 PUBLISHED 的公告会进入大屏公告轮播，screenDuration 为其停留秒数。
 */
defineOptions({ name: 'NoticeList' })

const meetStore = useMeetStore()
const userStore = useUserStore()

const meetId = computed(() => meetStore.currentMeetId)

type TagType = 'primary' | 'success' | 'info' | 'warning' | 'danger'

const NOTICE_TYPE_OPTIONS = [
  { label: '通知', value: 'NOTICE', tag: 'primary' as TagType },
  { label: '成绩公告', value: 'RESULT', tag: 'success' as TagType },
  { label: '日程', value: 'SCHEDULE', tag: 'info' as TagType },
  { label: '紧急', value: 'URGENT', tag: 'danger' as TagType }
]

const STATUS_OPTIONS = [
  { label: '草稿', value: 'DRAFT', tag: 'info' as TagType },
  { label: '已发布', value: 'PUBLISHED', tag: 'success' as TagType },
  { label: '已下线', value: 'OFFLINE', tag: 'warning' as TagType }
]

const SCOPE_OPTIONS = [
  { label: '公开', value: 'PUBLIC' },
  { label: '领队可见', value: 'LEADER' },
  { label: '裁判可见', value: 'REFEREE' }
]

const TOP_OPTIONS = [
  { label: '置顶', value: 1 },
  { label: '不置顶', value: 0 }
]

/* ------------------------------- 列表 ------------------------------- */

const loading = ref(false)
const tableData = ref<Announcement[]>([])
const pagination = reactive({ page: 1, size: 20, total: 0 })
const query = reactive({
  noticeType: '',
  status: '',
  scope: '',
  isTop: undefined as number | undefined,
  keyword: '',
  timeRange: [] as string[]
})

function typeLabel(value?: string): string {
  return NOTICE_TYPE_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

function typeTag(value?: string): TagType {
  return NOTICE_TYPE_OPTIONS.find((item) => item.value === value)?.tag || 'info'
}

function statusLabel(value?: string): string {
  return STATUS_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

function statusTag(value?: string): TagType {
  return STATUS_OPTIONS.find((item) => item.value === value)?.tag || 'info'
}

function scopeLabel(value?: string): string {
  return SCOPE_OPTIONS.find((item) => item.value === value)?.label || value || '-'
}

/** 表格正文列：优先 summary，无则前端去标签并截断 60 字 */
function plainText(row: Announcement): string {
  const source = row.summary || row.contentHtml || ''
  const text = String(source)
    .replace(/<[^>]*>/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  if (!text) {
    return '-'
  }
  return text.length > 60 ? `${text.slice(0, 60)}…` : text
}

function buildQuery(): AnnouncementQuery {
  const params: AnnouncementQuery = {
    page: pagination.page,
    size: pagination.size,
    meetId: meetId.value
  }
  if (query.noticeType) {
    params.noticeType = query.noticeType
  }
  if (query.status) {
    params.status = query.status
  }
  if (query.scope) {
    params.scope = query.scope
  }
  if (query.isTop !== undefined && query.isTop !== null) {
    params.isTop = query.isTop
  }
  const keyword = query.keyword?.trim()
  if (keyword) {
    params.keyword = keyword
  }
  if (Array.isArray(query.timeRange) && query.timeRange.length === 2) {
    params.beginTime = query.timeRange[0]
    params.endTime = query.timeRange[1]
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
    const result = await pageAnnouncements(buildQuery())
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
  void loadData()
}

function handleReset() {
  query.noticeType = ''
  query.status = ''
  query.scope = ''
  query.isTop = undefined
  query.keyword = ''
  query.timeRange = []
  pagination.page = 1
  void loadData()
}

function handlePageChange(page: number) {
  pagination.page = page
  void loadData()
}

function handleSizeChange(size: number) {
  pagination.size = size
  pagination.page = 1
  void loadData()
}

function requireMeet(): boolean {
  if (!meetId.value) {
    ElMessage.warning('请先在顶栏选择届次')
    return false
  }
  return true
}

/* ------------------------------- 新增 / 编辑 ------------------------------- */

const dialogVisible = ref(false)
const dialogMode = ref<'add' | 'edit'>('add')
const submitting = ref(false)
const formRef = ref<FormInstance>()

function createEmptyForm(): Announcement {
  return {
    meetId: meetId.value,
    title: '',
    contentHtml: '',
    noticeType: 'NOTICE',
    scope: 'PUBLIC',
    isTop: 0,
    screenShow: 0,
    screenDuration: 15,
    expireTime: undefined,
    sortNo: 0,
    remark: ''
  }
}

const form = reactive<Announcement>(createEmptyForm())

const formRules: FormRules = {
  title: [
    { required: true, message: '请输入公告标题', trigger: 'blur' },
    { max: 100, message: '标题不超过 100 字', trigger: 'blur' }
  ],
  noticeType: [{ required: true, message: '请选择公告类型', trigger: 'change' }],
  scope: [{ required: true, message: '请选择可见范围', trigger: 'change' }]
}

function resetForm() {
  Object.assign(form, createEmptyForm())
  formRef.value?.clearValidate()
}

function handleAdd() {
  if (!requireMeet()) {
    return
  }
  dialogMode.value = 'add'
  resetForm()
  dialogVisible.value = true
}

async function handleEdit(row: Announcement) {
  if (!requireMeet() || !row.id) {
    return
  }
  dialogMode.value = 'edit'
  resetForm()
  let source: Announcement = { ...row }
  try {
    const detail = await getAnnouncement(row.id)
    if (detail) {
      source = { ...row, ...detail }
    }
  } catch {
    // 详情读取失败时回退为列表行数据（拦截器已提示）
  }
  Object.assign(form, createEmptyForm(), source, {
    meetId: meetId.value,
    title: source.title || '',
    noticeType: source.noticeType || 'NOTICE',
    scope: source.scope || 'PUBLIC',
    isTop: source.isTop ?? 0,
    screenShow: source.screenShow ?? 0,
    screenDuration: source.screenDuration ?? 15,
    sortNo: source.sortNo ?? 0
  })
  dialogVisible.value = true
}

/** 组装提交体：只提交可编辑字段，状态/发布时间/阅读次数等由发布与下线接口维护 */
function buildPayload(): Announcement {
  const payload: Announcement = {
    meetId: meetId.value,
    title: form.title?.trim() || '',
    contentHtml: form.contentHtml || '',
    noticeType: form.noticeType,
    scope: form.scope,
    isTop: form.isTop ? 1 : 0,
    screenShow: form.screenShow ? 1 : 0,
    screenDuration: form.screenShow ? Number(form.screenDuration) || 15 : undefined,
    sortNo: form.sortNo ?? 0
  }
  if (form.expireTime) {
    payload.expireTime = form.expireTime
  }
  if (form.remark !== undefined) {
    payload.remark = form.remark
  }
  return payload
}

async function handleSubmit() {
  if (!requireMeet() || !formRef.value) {
    return
  }
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    if (dialogMode.value === 'add') {
      await createAnnouncement(buildPayload())
      ElMessage.success('新增公告成功')
    } else if (form.id) {
      await updateAnnouncement(form.id, buildPayload())
      ElMessage.success('保存公告成功')
    }
    dialogVisible.value = false
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    submitting.value = false
  }
}

/* ------------------------------- 发布 / 下线 ------------------------------- */

const publishVisible = ref(false)
const publishSubmitting = ref(false)
const publishRow = ref<Announcement | undefined>(undefined)
const publishForm = reactive({ screenShow: 0, screenDuration: 15 })

function handlePublish(row: Announcement) {
  if (!row.id) {
    return
  }
  publishRow.value = row
  publishForm.screenShow = row.screenShow ?? 1
  publishForm.screenDuration = row.screenDuration ?? 15
  publishVisible.value = true
}

async function handlePublishSubmit() {
  const row = publishRow.value
  if (!row?.id) {
    return
  }
  const show = publishForm.screenShow ? 1 : 0
  const duration = Number(publishForm.screenDuration) || 15
  try {
    await ElMessageBox.confirm(
      show === 1
        ? `确定发布公告「${row.title}」吗？发布后立即在大屏轮播（停留 ${duration} 秒）。`
        : `确定发布公告「${row.title}」吗？本次不推送大屏，仅站内公告可见。`,
      '发布确认',
      { type: 'warning', confirmButtonText: '确定发布', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  publishSubmitting.value = true
  try {
    await publishAnnouncement(row.id, {
      status: 'PUBLISHED',
      screenShow: show,
      screenDuration: show === 1 ? duration : undefined
    })
    ElMessage.success('公告已发布')
    publishVisible.value = false
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    publishSubmitting.value = false
  }
}

async function handleOffline(row: Announcement) {
  if (!row.id) {
    return
  }
  try {
    await ElMessageBox.confirm(`确定下线公告「${row.title}」吗？下线后大屏不再展示。`, '下线确认', {
      type: 'warning',
      confirmButtonText: '确定下线',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await publishAnnouncement(row.id, {
      status: 'OFFLINE',
      screenShow: row.screenShow ?? 0,
      screenDuration: row.screenDuration
    })
    ElMessage.success('公告已下线，大屏已停止展示')
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 删除 ------------------------------- */

async function handleDelete(row: Announcement) {
  if (!row.id) {
    return
  }
  if (row.status === 'PUBLISHED') {
    ElMessage.warning('该公告已发布，请先下线后再删除')
    return
  }
  try {
    await ElMessageBox.confirm(`确定删除公告「${row.title}」吗？删除后不可恢复。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }
  loading.value = true
  try {
    await deleteAnnouncement(row.id)
    ElMessage.success('删除成功')
    await loadData()
  } catch {
    // 错误提示由响应拦截器统一处理
  } finally {
    loading.value = false
  }
}

/* ------------------------------- 生命周期 ------------------------------- */

onMounted(async () => {
  if (!meetStore.loaded) {
    await meetStore.loadMeets().catch(() => undefined)
  }
  await loadData()
})
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title">公告管理</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}｜共 {{ pagination.total }} 条公告；发布并开启「大屏推送」的公告会进入大屏轮播
      </div>
    </div>

    <!-- 筛选 -->
    <div class="tm-card">
      <div class="tm-search-bar">
        <el-select v-model="query.noticeType" placeholder="公告类型" clearable style="width: 140px">
          <el-option v-for="item in NOTICE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px">
          <el-option v-for="item in STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.scope" placeholder="可见范围" clearable style="width: 140px">
          <el-option v-for="item in SCOPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="query.isTop" placeholder="是否置顶" clearable style="width: 130px">
          <el-option v-for="item in TOP_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-input
          v-model="query.keyword"
          placeholder="标题 / 正文关键字"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
        <el-date-picker
          v-model="query.timeRange"
          type="datetimerange"
          value-format="YYYY-MM-DD HH:mm:ss"
          range-separator="至"
          start-placeholder="发布时间起"
          end-placeholder="发布时间止"
          style="width: 380px"
        />
        <el-button type="primary" :disabled="!meetId" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
    </div>

    <!-- 列表 -->
    <div class="tm-card">
      <div class="tm-toolbar" style="margin-bottom: 12px">
        <div class="tm-toolbar-actions">
          <el-button v-if="userStore.hasPerm('notice:add')" type="primary" :disabled="!meetId" @click="handleAdd">
            新增公告
          </el-button>
          <span v-if="!meetId" class="tm-form-tip">请先在顶栏选择届次</span>
        </div>
        <div class="tm-toolbar-actions">
          <el-button :loading="loading" :disabled="!meetId" @click="loadData">刷新</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="tableData" border stripe>
        <el-table-column label="标题" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <el-tag v-if="row.isTop === 1" type="danger" size="small" effect="dark" style="margin-right: 6px">顶</el-tag>
            <span>{{ row.title || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="正文摘要" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ plainText(row) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.noticeType)" effect="light">{{ typeLabel(row.noticeType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="范围" width="100" align="center">
          <template #default="{ row }">{{ scopeLabel(row.scope) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="推大屏" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.screenShow === 1" type="success" effect="plain">推大屏 {{ row.screenDuration ?? '-' }}s</el-tag>
            <el-tag v-else type="info" effect="plain">不推送</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发布时间" width="170" align="center">
          <template #default="{ row }">{{ row.publishTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="发布人" width="110" align="center">
          <template #default="{ row }">{{ row.publisherName || '-' }}</template>
        </el-table-column>
        <el-table-column label="阅读次数" width="100" align="center">
          <template #default="{ row }">{{ row.readCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button v-if="userStore.hasPerm('notice:edit')" link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button
              v-if="userStore.hasPerm('notice:publish') && row.status !== 'PUBLISHED'"
              link
              type="success"
              @click="handlePublish(row)"
            >
              发布
            </el-button>
            <el-button
              v-if="userStore.hasPerm('notice:publish') && row.status === 'PUBLISHED'"
              link
              type="warning"
              @click="handleOffline(row)"
            >
              下线
            </el-button>
            <el-button v-if="userStore.hasPerm('notice:delete')" link type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty :description="meetId ? '暂无公告，可新增后发布' : '请先在顶部选择届次'" />
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

    <!-- 新增 / 编辑公告 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogMode === 'add' ? '新增公告' : '编辑公告'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="120px">
        <el-form-item label="公告标题" prop="title">
          <el-input v-model="form.title" maxlength="100" show-word-limit placeholder="如：男子100米决赛成绩公告" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="公告类型" prop="noticeType">
              <el-select v-model="form.noticeType" style="width: 100%">
                <el-option v-for="item in NOTICE_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="可见范围" prop="scope">
              <el-select v-model="form.scope" style="width: 100%">
                <el-option v-for="item in SCOPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="公告正文">
          <el-input
            v-model="form.contentHtml"
            type="textarea"
            :rows="8"
            placeholder="支持基础 HTML 标签，如 <p>、<strong>、<br/>、<table>"
          />
          <div class="tm-form-tip">支持基础 HTML 标签（&lt;p&gt;、&lt;strong&gt;、&lt;br/&gt;、&lt;table&gt; 等）；不建议粘贴含脚本的外部内容。</div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="是否置顶">
              <el-switch v-model="form.isTop" :active-value="1" :inactive-value="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="大屏推送">
              <el-switch v-model="form.screenShow" :active-value="1" :inactive-value="0" />
              <span class="tm-form-tip" style="margin-left: 8px">开启后公告进入大屏轮播</span>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="大屏停留(秒)">
              <el-input-number
                v-model="form.screenDuration"
                :min="5"
                :max="120"
                :disabled="form.screenShow !== 1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="失效时间">
              <el-date-picker
                v-model="form.expireTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm:ss"
                placeholder="可空，到期后不再展示"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序号">
              <el-input-number v-model="form.sortNo" :min="0" :max="9999" controls-position="right" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="备注">
              <el-input v-model="form.remark" maxlength="200" placeholder="选填" />
            </el-form-item>
          </el-col>
        </el-row>

        <div class="tm-form-tip">
          大屏推送需在大屏配置中开启「公告」模块，且公告状态为「已发布」；停留秒数范围 5~120 秒，未开启推送时不生效。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 发布公告 -->
    <el-dialog v-model="publishVisible" title="发布公告" width="520px" destroy-on-close>
      <div class="nl-publish-title">{{ publishRow?.title }}</div>
      <el-form label-width="120px" style="margin-top: 12px">
        <el-form-item label="推送大屏">
          <el-switch v-model="publishForm.screenShow" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="大屏停留(秒)">
          <el-input-number
            v-model="publishForm.screenDuration"
            :min="5"
            :max="120"
            :disabled="publishForm.screenShow !== 1"
            controls-position="right"
            style="width: 180px"
          />
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">发布后公告在大屏公告模块轮播；关闭「推送大屏」则仅站内可见。</div>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishSubmitting" @click="handlePublishSubmit">确定发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.nl-publish-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}
</style>
