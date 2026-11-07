<script setup lang="ts">
const modules = [
  { mark: '权', title: '管理员与角色', description: '管理员账号、角色权限与数据权限范围。' },
  { mark: '单', title: '菜单权限', description: '菜单、按钮与接口权限的统一授权模型。' },
  { mark: '审', title: '操作审计', description: '全量操作留痕，敏感数据访问专项记录。' },
  { mark: '警', title: '告警看板', description: '服务健康、错误率、MQ 堆积与容量告警。' },
]

const requirements = [
  '独立域名与内网 / IP 白名单访问，不复用 App 端鉴权链路。',
  '独立登录体系，管理端 Token 使用独立密钥并强制 MFA。',
  'RBAC + 数据权限双重校验，匿名内容反查需专项授权。',
  '全量操作审计，审计日志独立存储且不可篡改。',
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
        <h2>安全要求</h2>
        <span class="badge badge-warning">登录待接入</span>
      </div>
      <ul class="checklist">
        <li v-for="item in requirements" :key="item">{{ item }}</li>
      </ul>
    </section>
  </div>
</template>
