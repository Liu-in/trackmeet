<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

/**
 * 占位页：菜单中已配置但尚未交付的页面统一渲染此页，避免出现空白与 404。
 */
defineOptions({ name: 'Placeholder' })

const route = useRoute()
const router = useRouter()
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <el-result icon="info" title="该功能将在后续阶段交付" :sub-title="`当前路由：${route.path}`">
        <template #extra>
          <el-button type="primary" @click="router.push('/dashboard')">返回首页看板</el-button>
        </template>
      </el-result>
      <div class="tm-form-tip" style="text-align: center">
        阶段 2 已交付：运动员管理、报名管理/审核/校验、项目管理、轮次管理、规则配置、名次分与加分规则。
      </div>
    </div>
  </div>
</template>
