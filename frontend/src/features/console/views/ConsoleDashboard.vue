<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { consoleApi } from '../api'

const { data, isLoading } = useQuery({
  queryKey: ['console', 'dashboard'],
  queryFn: () => consoleApi.dashboard(),
  staleTime: 30_000,
})

const stats = computed(() => [
  { label: '用户总数', value: data.value?.totalUsers ?? 0, suffix: '人' },
  { label: '7 日活跃', value: data.value?.active7d ?? 0, suffix: '人' },
  { label: '任务', value: data.value?.tasks ?? 0, suffix: '条' },
  { label: '笔记', value: data.value?.notes ?? 0, suffix: '篇' },
  { label: '记账', value: data.value?.ledgers ?? 0, suffix: '笔' },
  { label: '生活记录', value: data.value?.lifeLogs ?? 0, suffix: '条' },
])

const health = computed(() => [
  { label: '数据库大小', value: `${data.value?.dbSizeMb ?? 0} MB` },
  { label: '运行时长', value: `${data.value?.uptimeHours ?? 0} 小时` },
  { label: '禁用账号', value: `${data.value?.disabledUsers ?? 0} 个` },
])
</script>

<template>
  <div class="console-dash">
    <header class="dash-head">
      <h1>概览</h1>
      <p>只统计数量，不读取任何用户内容</p>
    </header>

    <el-skeleton v-if="isLoading" :rows="5" animated />

    <template v-else>
      <div class="stat-grid">
        <div v-for="item in stats" :key="item.label" class="stat">
          <span class="stat__label">{{ item.label }}</span>
          <strong class="stat__value tabular">{{ item.value }}<em>{{ item.suffix }}</em></strong>
        </div>
      </div>

      <section class="health">
        <h2>系统</h2>
        <div class="health__row">
          <div v-for="item in health" :key="item.label" class="health__item">
            <span>{{ item.label }}</span>
            <strong class="tabular">{{ item.value }}</strong>
          </div>
        </div>
      </section>

      <section class="notice">
        <h2>权限边界</h2>
        <p>
          管理员可管理账号、查看数据量、执行备份与恢复，但**无法读取**任何用户的笔记、日记、记账明细。
          这是产品约束，不是权限没做。
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped lang="scss">
.console-dash {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.dash-head {
  h1 {
    font-size: 20px;
    font-weight: 700;
    color: #1b2a32;
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    color: #6b7c87;
  }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;

  @include respond-below(lg) {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.stat {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px 16px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8ecf2;
}

.stat__label {
  font-size: 11.5px;
  color: #6b7c87;
}

.stat__value {
  font-size: 24px;
  font-weight: 700;
  color: #1b2a32;

  em {
    margin-left: 3px;
    font-size: 11px;
    font-style: normal;
    font-weight: 500;
    color: #9aa8b2;
  }
}

.health,
.notice {
  padding: 16px 18px;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8ecf2;

  h2 {
    font-size: 14px;
    font-weight: 600;
    color: #1b2a32;
  }
}

.health__row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.health__item {
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: 6px;
  background: #f7f9fc;
  font-size: 12.5px;
  color: #6b7c87;

  strong {
    color: #1b2a32;
  }
}

.notice p {
  margin-top: 10px;
  font-size: 12.5px;
  line-height: 1.8;
  color: #6b7c87;
}
</style>
