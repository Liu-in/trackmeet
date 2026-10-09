<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { getRegistrationStatistics, type RegistrationStat } from '@/api/registration'
import { pageMeets } from '@/api/meet'
import { useMeetStore } from '@/stores/meet'
import { useUserStore } from '@/stores/user'

/**
 * 首页看板：届次概览、报名统计 Top、快捷入口。
 */
defineOptions({ name: 'Dashboard' })

const router = useRouter()
const meetStore = useMeetStore()
const userStore = useUserStore()

const meetCount = ref(0)
const stats = ref<RegistrationStat[]>([])
const loading = ref(false)

const quickLinks = computed(() =>
  [
    { title: '运动员管理', path: '/athlete/list', perm: 'athlete:list' },
    { title: '报名管理', path: '/registration/list', perm: 'registration:list' },
    { title: '报名审核', path: '/registration/audit', perm: 'registration:audit' },
    { title: '项目管理', path: '/event/list', perm: 'event:list' },
    { title: '规则配置', path: '/event/rules', perm: 'event:rule:list' },
    { title: '名次分与加分', path: '/event/score-rules', perm: 'rule:score:list' }
  ].filter((item) => userStore.hasPerm(item.perm))
)

async function loadData() {
  loading.value = true
  try {
    const page = await pageMeets({ page: 1, size: 1 })
    meetCount.value = page.total || 0
    if (!meetStore.currentMeetId) {
      await meetStore.loadMeets()
    }
    if (meetStore.currentMeetId) {
      stats.value = (await getRegistrationStatistics({ meetId: meetStore.currentMeetId, dimension: 'EVENT' })) || []
    }
  } catch {
    stats.value = []
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<template>
  <div class="tm-page" v-loading="loading">
    <div class="tm-card">
      <div class="tm-page-title">欢迎，{{ userStore.displayName }}</div>
      <div class="tm-page-desc">
        当前届次：{{ meetStore.currentMeet?.meetName || '未选择' }}
        <span v-if="meetStore.currentMeet?.edition">（{{ meetStore.currentMeet.edition }}）</span>
        ｜ 状态：{{ meetStore.currentMeet?.status || '-' }}
      </div>
    </div>

    <div class="tm-stat-grid">
      <div class="tm-stat-card">
        <div class="tm-stat-label">运动会届次总数</div>
        <div class="tm-stat-value">{{ meetCount }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">当前届次组别数</div>
        <div class="tm-stat-value">{{ meetStore.groups.length }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">当前届次参赛单位</div>
        <div class="tm-stat-value">{{ meetStore.units.length }}</div>
      </div>
      <div class="tm-stat-card">
        <div class="tm-stat-label">已报名项目数</div>
        <div class="tm-stat-value">{{ stats.length }}</div>
      </div>
    </div>

    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">快捷入口</div>
      <el-space wrap>
        <el-button v-for="link in quickLinks" :key="link.path" type="primary" plain @click="router.push(link.path)">
          {{ link.title }}
        </el-button>
      </el-space>
    </div>

    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">报名情况（按项目）</div>
      <el-table :data="stats" border stripe max-height="360">
        <el-table-column prop="name" label="项目" min-width="200" />
        <el-table-column prop="total" label="报名总数" width="110" align="center" />
        <el-table-column prop="pending" label="待审" width="100" align="center" />
        <el-table-column prop="approved" label="已通过" width="100" align="center" />
        <el-table-column prop="rejected" label="已退回" width="100" align="center" />
      </el-table>
      <el-empty v-if="!loading && stats.length === 0" description="暂无报名数据（可先导入运动员并提交报名）" />
    </div>
  </div>
</template>
