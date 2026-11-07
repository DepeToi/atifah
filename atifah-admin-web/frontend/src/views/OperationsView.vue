<script setup lang="ts">
const modules = [
  { mark: '荐', title: '推荐位管理', description: '首页推荐位、每日内容编排与灰度投放配置。' },
  { mark: '活', title: '活动配置', description: '打卡活动、任务规则、奖励发放与时间窗口。' },
  { mark: '推', title: '推送任务', description: '定向推送、提醒策略、频控与发送记录。' },
  { mark: '词', title: '敏感词库', description: '文本审核词库、命中规则与分级处置策略。' },
]

const principles = [
  '运营配置统一经 admin-service 落库，变更记录操作审计。',
  '推荐位与活动支持灰度开关，可快速回滚。',
  '敏感词与处置策略变更需双人复核后生效。',
  '推送任务必须配置频控与退订入口，避免打扰用户。',
]
</script>

<template>
  <div class="page">
    <section class="module-grid">
      <article v-for="item in modules" :key="item.title" class="module-card">
        <span class="module-icon" aria-hidden="true">{{ item.mark }}</span>
        <h3>{{ item.title }}</h3>
        <p>{{ item.description }}</p>
        <span class="badge">待接入</span>
      </article>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>配置原则</h2>
      </div>
      <ul class="checklist">
        <li v-for="item in principles" :key="item">{{ item }}</li>
      </ul>
    </section>
  </div>
</template>
