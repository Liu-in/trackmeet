<script setup lang="ts">
import { useRouter } from 'vue-router'

defineOptions({ name: 'NotFound' })

const router = useRouter()
</script>

<template>
  <div class="tm-login">
    <div class="tm-login-card">
      <el-result icon="warning" title="404" sub-title="页面不存在或已被移除">
        <template #extra>
          <el-button type="primary" @click="router.push('/dashboard')">返回首页</el-button>
        </template>
      </el-result>
    </div>
  </div>
</template>
