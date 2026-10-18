<script setup lang="ts">
import { reactive } from 'vue'

// 仅页面模板状态；查询逻辑待接入 admin-service 后补充
const filters = reactive({
  keyword: '',
  status: '',
})

const statusOptions = [
  { label: '正常', value: 'active' },
  { label: '已禁用', value: 'disabled' },
  { label: '已注销', value: 'cancelled' },
]

const handleReset = () => {
  filters.keyword = ''
  filters.status = ''
}
</script>

<template>
  <div class="page">
    <section class="panel">
      <el-form :inline="true" :model="filters" class="filters" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="filters.keyword"
            placeholder="用户 ID / 手机号 / 昵称"
            clearable
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filters.status" placeholder="全部" clearable class="status-select">
            <el-option
              v-for="option in statusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" native-type="submit">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <section class="panel">
      <div class="panel-head">
        <h2>用户列表</h2>
        <span class="badge">待接入</span>
      </div>

      <el-table :data="[]" empty-text="暂无数据，接入 admin-service 后展示">
        <el-table-column prop="id" label="用户 ID" min-width="160" />
        <el-table-column prop="nickname" label="昵称" min-width="140" />
        <el-table-column prop="phone" label="手机号" min-width="150" />
        <el-table-column prop="status" label="状态" width="110" />
        <el-table-column prop="createdAt" label="注册时间" min-width="170" />
        <el-table-column label="操作" width="100" fixed="right" />
      </el-table>

      <div class="panel-foot">
        手机号、情绪数据等敏感字段默认脱敏展示，查看明文需专项授权并记录审计日志。
      </div>
    </section>
  </div>
</template>
