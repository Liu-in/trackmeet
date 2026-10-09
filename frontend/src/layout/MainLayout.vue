<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useMeetStore } from '@/stores/meet'
import type { MenuNode } from '@/api/auth'

/**
 * 主框架：侧边菜单（来自权限菜单树）+ 顶栏（届次切换、用户菜单）+ 内容区。
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const meetStore = useMeetStore()

const collapsed = ref(false)

const visibleMenus = computed(() => filterMenus(permissionStore.menus))
const currentTitle = computed(() => (route.meta?.title as string) || '')

function filterMenus(nodes: MenuNode[]): MenuNode[] {
  return (nodes || [])
    .filter((node) => node.menuType !== 'B' && node.visible !== 0)
    .map((node) => ({
      ...node,
      children: node.children ? filterMenus(node.children) : []
    }))
    .filter((node) => node.menuType === 'M' || (node.children && node.children.length > 0))
}

function handleMeetChange(meetId: number) {
  meetStore
    .setCurrentMeet(meetId)
    .then(() => {
      ElMessageBox.alert('已切换届次，列表数据将按新届次加载', '提示', { type: 'success', confirmButtonText: '知道了' }).catch(() => undefined)
      router.go(0)
    })
    .catch(() => undefined)
}

async function handleLogout() {
  await userStore.logout()
  permissionStore.reset()
  router.push('/login')
}

onMounted(() => {
  if (!meetStore.loaded) {
    meetStore.loadMeets().catch(() => undefined)
  }
})
</script>

<template>
  <div class="tm-layout">
    <aside class="tm-aside" :class="{ 'is-collapsed': collapsed }">
      <div class="tm-logo">{{ collapsed ? 'TM' : '田径运动会编排' }}</div>
      <el-menu
        :default-active="route.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409eff"
        :collapse="collapsed"
        router
        unique-opened
      >
        <template v-for="menu in visibleMenus" :key="menu.id">
          <el-sub-menu v-if="menu.children && menu.children.length > 0" :index="String(menu.id)">
            <template #title>
              <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
              <span>{{ menu.menuName }}</span>
            </template>
            <el-menu-item v-for="child in menu.children" :key="child.id" :index="child.path">
              <span>{{ child.menuName }}</span>
            </el-menu-item>
          </el-sub-menu>
          <el-menu-item v-else :index="menu.path">
            <el-icon v-if="menu.icon"><component :is="menu.icon" /></el-icon>
            <template #title>{{ menu.menuName }}</template>
          </el-menu-item>
        </template>
      </el-menu>
    </aside>

    <section class="tm-main">
      <header class="tm-header">
        <div class="tm-header-left">
          <el-button text @click="collapsed = !collapsed">
            <el-icon><Fold /></el-icon>
          </el-button>
          <span class="tm-page-title">{{ currentTitle }}</span>
        </div>
        <div class="tm-header-right">
          <el-select
            v-if="meetStore.meets.length > 0"
            :model-value="meetStore.currentMeetId"
            placeholder="选择届次"
            style="width: 240px"
            filterable
            @change="handleMeetChange"
          >
            <el-option
              v-for="meet in meetStore.meets"
              :key="meet.id"
              :label="`${meet.meetName}${meet.edition ? '（' + meet.edition + '）' : ''}`"
              :value="meet.id as number"
            />
          </el-select>
          <el-tag v-if="meetStore.currentMeet?.status" type="info">{{ meetStore.currentMeet.status }}</el-tag>
          <el-dropdown>
            <span style="cursor: pointer">
              {{ userStore.displayName }}
              <el-tag size="small" style="margin-left: 6px">{{ userStore.roles.join('/') || '未分配角色' }}</el-tag>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="router.push('/profile')">个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="tm-content">
        <router-view v-slot="{ Component }">
          <keep-alive>
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </main>
    </section>
  </div>
</template>
