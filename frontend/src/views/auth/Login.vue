<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getCaptcha } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { usePermissionStore } from '@/stores/permission'
import { useMeetStore } from '@/stores/meet'

/**
 * 登录页：账号口令登录（验证码开关由后端 trackmeet.captcha.enabled 控制）。
 */
defineOptions({ name: 'Login' })

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const permissionStore = usePermissionStore()
const meetStore = useMeetStore()

const form = reactive({
  username: 'admin',
  password: 'Admin@123',
  captchaKey: '',
  captchaCode: ''
})

const loading = ref(false)
const captchaImage = ref('')
const captchaVisible = ref(false)

async function refreshCaptcha() {
  try {
    const result = await getCaptcha()
    captchaImage.value = result.imageBase64
    form.captchaKey = result.captchaKey
    captchaVisible.value = true
  } catch {
    captchaVisible.value = false
  }
}

async function handleLogin() {
  if (!form.username || !form.password) {
    ElMessage.warning('请输入用户名与密码')
    return
  }
  loading.value = true
  try {
    await userStore.login({
      username: form.username,
      password: form.password,
      captchaKey: form.captchaKey,
      captchaCode: form.captchaCode
    })
    permissionStore.buildRoutes(userStore.menus)
    meetStore.loaded = false
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/dashboard'
    await router.replace(redirect)
  } catch {
    await refreshCaptcha()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  refreshCaptcha()
})
</script>

<template>
  <div class="tm-login">
    <div class="tm-login-card">
      <div class="tm-login-title">田径运动会编排系统</div>
      <div class="tm-login-sub">TrackMeet · 报名 / 编排 / 成绩 / 积分全流程</div>

      <el-form label-position="top" @submit.prevent>
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" clearable @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item v-if="captchaVisible" label="验证码">
          <div style="display: flex; gap: 8px; width: 100%">
            <el-input v-model="form.captchaCode" placeholder="请输入验证码" style="flex: 1" @keyup.enter="handleLogin" />
            <img
              v-if="captchaImage"
              :src="captchaImage"
              alt="验证码"
              style="height: 32px; cursor: pointer"
              @click="refreshCaptcha"
            />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">登录</el-button>
        </el-form-item>
      </el-form>

      <div class="tm-form-tip">
        默认账号：admin / Admin@123（首次登录后请立即修改口令）<br />
        演示账号：scheduler（编排员）、referee（裁判）、leader1（领队）
      </div>
    </div>
  </div>
</template>
