<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import { DocumentChecked, Odometer, Setting, Tools, User } from '@element-plus/icons-vue'

const route = useRoute()

const activeMenu = computed(() => route.path)
const pageTitle = computed(() =>
  typeof route.meta.title === 'string' ? route.meta.title : '管理后台',
)
const pageSubtitle = computed(() =>
  typeof route.meta.subtitle === 'string' ? route.meta.subtitle : '',
)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/dashboard">
        <span class="brand-mark" aria-hidden="true">m</span>
        <span class="brand-text">
          <strong>atiFah</strong>
          <small>管理后台</small>
        </span>
      </RouterLink>

      <el-menu :default-active="activeMenu" router class="nav-menu" aria-label="主导航">
        <el-menu-item-group title="运营">
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <span>仪表盘</span>
          </el-menu-item>
          <el-menu-item index="/users">
            <el-icon><User /></el-icon>
            <span>用户管理</span>
          </el-menu-item>
          <el-menu-item index="/content-review">
            <el-icon><DocumentChecked /></el-icon>
            <span>内容审核</span>
          </el-menu-item>
        </el-menu-item-group>

        <el-menu-item-group title="配置">
          <el-menu-item index="/operations">
            <el-icon><Tools /></el-icon>
            <span>运营配置</span>
          </el-menu-item>
          <el-menu-item index="/system">
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </el-menu-item>
        </el-menu-item-group>
      </el-menu>

      <div class="sidebar-foot">
        <span class="env-dot" aria-hidden="true"></span>
        <span>工程骨架 · v1.0.0</span>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-title">
          <h1>{{ pageTitle }}</h1>
          <p v-if="pageSubtitle">{{ pageSubtitle }}</p>
        </div>
        <div class="topbar-actions">
          <span class="badge badge-warning">认证待接入</span>
          <span class="avatar" aria-hidden="true">M</span>
        </div>
      </header>

      <main class="content">
        <RouterView />
      </main>

      <footer class="footer">
        <span>atiFah 管理后台</span>
        <span>Vue 3 · Vite · Element Plus</span>
      </footer>
    </div>
  </div>
</template>
