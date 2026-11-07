<script setup lang="ts">
import { ref } from 'vue'

const activeTab = ref('all')
</script>

<template>
  <div class="page">
    <div class="notice">
      <i class="notice-icon" aria-hidden="true">i</i>
      <div>
        <strong>审核链路待接入</strong>
        <p>公开内容遵循先审后发；匿名内容展示时需脱敏，反查作者须专项授权并留痕。</p>
      </div>
    </div>

    <section class="panel">
      <div class="panel-head">
        <h2>审核队列</h2>
        <span class="badge">待接入</span>
      </div>

      <el-tabs v-model="activeTab" class="audit-tabs">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="待机审" name="machine" />
        <el-tab-pane label="待人审" name="manual" />
        <el-tab-pane label="已处置" name="resolved" />
      </el-tabs>

      <el-table :data="[]" empty-text="暂无待审核内容，接入 community-service / admin-service 后展示">
        <el-table-column prop="id" label="内容 ID" min-width="160" />
        <el-table-column prop="type" label="类型" width="110" />
        <el-table-column prop="author" label="作者" min-width="140" />
        <el-table-column prop="summary" label="摘要" min-width="240" />
        <el-table-column prop="status" label="审核状态" width="120" />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" width="120" fixed="right" />
      </el-table>

      <div class="panel-foot">
        高风险内容（自伤、自杀等表达）将进入人工干预流程，处置操作全程审计。
      </div>
    </section>
  </div>
</template>
