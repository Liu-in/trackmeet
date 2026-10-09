<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { changePassword } from '@/api/auth'
import { useUserStore } from '@/stores/user'

/**
 * 个人中心：账号信息与修改密码。
 */
defineOptions({ name: 'Profile' })

const userStore = useUserStore()
const loading = ref(false)
const form = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

async function handleSubmit() {
  if (!form.oldPassword || !form.newPassword) {
    ElMessage.warning('请输入原密码与新密码')
    return
  }
  if (form.newPassword !== form.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  loading.value = true
  try {
    await changePassword({ oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改，请重新登录')
    await userStore.logout()
    window.location.href = '/login'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="tm-page">
    <div class="tm-card">
      <div class="tm-page-title" style="margin-bottom: 12px">账号信息</div>
      <el-descriptions :column="3" border>
        <el-descriptions-item label="登录名">{{ userStore.username }}</el-descriptions-item>
        <el-descriptions-item label="姓名">{{ userStore.displayName }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ userStore.roles.join('、') || '-' }}</el-descriptions-item>
        <el-descriptions-item label="单位 ID">{{ userStore.unitId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="权限数量">{{ userStore.permissions.length }}</el-descriptions-item>
        <el-descriptions-item label="菜单数量">{{ userStore.menus.length }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <div class="tm-card" style="max-width: 520px">
      <div class="tm-page-title" style="margin-bottom: 12px">修改密码</div>
      <el-form label-width="100px">
        <el-form-item label="原密码">
          <el-input v-model="form.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="form.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="form.confirmPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="handleSubmit">提交</el-button>
        </el-form-item>
      </el-form>
      <div class="tm-form-tip">修改成功后旧令牌立即失效，需要使用新密码重新登录。</div>
    </div>
  </div>
</template>
