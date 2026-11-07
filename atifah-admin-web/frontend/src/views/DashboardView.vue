<script setup lang="ts">
const stats = [
  { label: '今日活跃用户', value: '--', hint: 'DAU · 待接入' },
  { label: '今日打卡量', value: '--', hint: '较昨日 --' },
  { label: '平均情绪指数', value: '--', hint: '满分 100 · 待接入' },
  { label: '社区互动量', value: '--', hint: '点赞 / 评论 / 分享' },
]

const quickLinks = [
  { path: '/users', title: '用户管理', description: '查询用户、状态管理与隐私数据脱敏' },
  { path: '/content-review', title: '内容审核', description: '机审队列、人审工作台与举报处置' },
  { path: '/operations', title: '运营配置', description: '推荐位、活动、推送与敏感词' },
  { path: '/system', title: '系统管理', description: 'RBAC、菜单、审计与告警看板' },
]
</script>

<template>
  <div class="page">
    <div class="notice">
      <i class="notice-icon" aria-hidden="true">i</i>
      <div>
        <strong>工程骨架阶段</strong>
        <p>页面框架已就绪，指标数据将在接入 admin-service（BFF）后展示。</p>
      </div>
    </div>

    <section class="stat-grid" aria-label="核心指标">
      <article v-for="stat in stats" :key="stat.label" class="stat-card">
        <span class="stat-label">{{ stat.label }}</span>
        <strong class="stat-value">{{ stat.value }}</strong>
        <span class="stat-hint">{{ stat.hint }}</span>
      </article>
    </section>

    <section class="panel-grid">
      <article class="panel">
        <div class="panel-head">
          <h2>情绪分布</h2>
          <span class="badge">待接入</span>
        </div>
        <div class="chart-empty">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M21 12a9 9 0 1 1-9-9" />
            <path d="M21 3v9h-9" />
          </svg>
          <span>接入统计数据后展示情绪占比</span>
        </div>
      </article>

      <article class="panel">
        <div class="panel-head">
          <h2>近 7 日打卡趋势</h2>
          <span class="badge">待接入</span>
        </div>
        <div class="chart-empty">
          <svg
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
            aria-hidden="true"
          >
            <path d="M3 3v18h18" />
            <path d="m7 14 4-4 3 3 5-6" />
          </svg>
          <span>接入统计数据后展示趋势曲线</span>
        </div>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>快捷入口</h2>
        <span class="badge">骨架页面</span>
      </div>
      <div class="quick-grid">
        <RouterLink
          v-for="link in quickLinks"
          :key="link.path"
          class="quick-link"
          :to="link.path"
        >
          <strong>{{ link.title }}</strong>
          <span>{{ link.description }}</span>
        </RouterLink>
      </div>
    </section>
  </div>
</template>
