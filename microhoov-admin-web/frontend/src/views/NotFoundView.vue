<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()

const goDashboard = () => {
  router.push('/dashboard')
}
</script>

<template>
  <div class="error-shell">
    <div class="error-card">
      <span class="error-code">404</span>
      <h1>页面不存在</h1>
      <p>请检查访问地址是否正确。</p>
      <p class="error-path"><code>{{ route.fullPath }}</code></p>
      <el-button type="primary" @click="goDashboard">返回仪表盘</el-button>
    </div>
  </div>
</template>
