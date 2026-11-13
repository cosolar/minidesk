<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { Refresh, Timer, VideoPlay } from '@element-plus/icons-vue'
import { consoleApi, type ScheduleJob } from '../api'

const queryClient = useQueryClient()
const dialogVisible = ref(false)
const editing = ref<ScheduleJob | null>(null)
const cronDraft = ref('')
const logFilter = ref('')
const logPage = ref(1)
const runningCode = ref('')

const presets = [
  { label: '每 15 分钟', value: '0 0/15 * * * ?' },
  { label: '每小时整点', value: '0 0 * * * ?' },
  { label: '每天 03:00', value: '0 0 3 * * ?' },
  { label: '每天 22:30', value: '0 30 22 * * ?' },
  { label: '每周一 09:00', value: '0 0 9 ? * MON' },
  { label: '每月 1 日 04:00', value: '0 0 4 1 * ?' },
]

const jobsQuery = useQuery({
  queryKey: ['console', 'schedule', 'jobs'],
  queryFn: () => consoleApi.jobs(),
  staleTime: 0,
})

const logsQuery = useQuery({
  queryKey: ['console', 'schedule', 'logs', logFilter, logPage],
  queryFn: () => consoleApi.jobLogs({ jobCode: logFilter.value, page: logPage.value, size: 15 }),
  staleTime: 0,
})

const jobs = computed(() => jobsQuery.data.value ?? [])
const enabledCount = computed(() => jobs.value.filter((item) => item.enabled).length)

function invalidateAll(): void {
  queryClient.invalidateQueries({ queryKey: ['console', 'schedule'] })
}

const updateMutation = useMutation({
  mutationFn: (input: { code: string; payload: { cronExpr?: string; enabled?: boolean } }) =>
    consoleApi.updateJob(input.code, input.payload),
  onSuccess: () => {
    ElMessage.success('任务配置已保存')
    dialogVisible.value = false
    invalidateAll()
  },
})

const triggerMutation = useMutation({
  mutationFn: (code: string) => consoleApi.triggerJob(code),
  onSuccess: (result) => {
    ElMessage({
      type: result.success ? 'success' : 'error',
      message: result.message,
      duration: 5000,
      showClose: true,
    })
    invalidateAll()
  },
  onSettled: () => {
    runningCode.value = ''
  },
})

function triggerJob(code: string): void {
  runningCode.value = code
  triggerMutation.mutate(code)
}

function openEditor(value: unknown): void {
  const row = value as ScheduleJob
  editing.value = row
  cronDraft.value = row.cronExpr
  dialogVisible.value = true
}

function saveCron(): void {
  if (!editing.value) {
    return
  }
  updateMutation.mutate({ code: editing.value.code, payload: { cronExpr: cronDraft.value.trim() } })
}

function toggleJob(value: unknown, enabled: string | number | boolean): void {
  const row = value as ScheduleJob
  updateMutation.mutate({ code: row.code, payload: { enabled: Boolean(enabled) } })
}

function refreshLogs(): void {
  queryClient.invalidateQueries({ queryKey: ['console', 'schedule', 'logs'] })
}

function statusLabel(value: string | null): string {
  if (value === 'success') return '成功'
  if (value === 'failed') return '失败'
  return '未执行'
}
</script>

<template>
  <div class="jobs-page">
    <header class="page-head">
      <div>
        <h1>定时任务</h1>
        <p>配置与执行历史存在数据库，重启后自动重建触发器；手动触发立即同步执行</p>
      </div>
      <el-button :icon="Refresh" :loading="jobsQuery.isFetching.value" @click="invalidateAll()">刷新</el-button>
    </header>

    <section class="summary">
      <div class="summary__icon"><el-icon><Timer /></el-icon></div>
      <div class="summary__text">
        <strong>{{ enabledCount }} / {{ jobs.length }} 个任务已启用</strong>
        <span>提醒扫描 · 日报草稿 · 自动备份 · 日志清理，均由 Quartz 按 Cron 调度</span>
      </div>
      <span class="summary__tag">单机 RAM 调度</span>
    </section>

    <el-table :data="jobs" class="table" row-key="code" v-loading="jobsQuery.isLoading.value">
      <el-table-column label="任务" min-width="210">
        <template #default="{ row }">
          <div class="job-name">
            <strong>{{ row.name }}</strong>
            <span>{{ row.description }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="Cron" width="210">
        <template #default="{ row }">
          <div class="cron-cell">
            <code>{{ row.cronExpr }}</code>
            <el-button link type="primary" size="small" @click="openEditor(row)">编辑</el-button>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled"
            :loading="updateMutation.isPending.value"
            @update:model-value="(value: string | number | boolean) => toggleJob(row, value)"
          />
        </template>
      </el-table-column>
      <el-table-column label="下次执行" width="160">
        <template #default="{ row }">
          <span class="mono">{{ row.enabled ? row.nextRunAt ?? '—' : '已停用' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="最近执行" min-width="180">
        <template #default="{ row }">
          <div class="last-run">
            <el-tag :type="row.lastStatus === 'success' ? 'success' : row.lastStatus === 'failed' ? 'danger' : 'info'" size="small" effect="light">
              {{ statusLabel(row.lastStatus) }}
            </el-tag>
            <span class="mono">{{ row.lastRunAt ?? '—' }}</span>
            <span v-if="row.lastCostMs !== null" class="muted">{{ row.lastCostMs }} ms</span>
            <span v-if="row.lastError" class="error-text" :title="row.lastError">{{ row.lastError }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            type="primary"
            plain
            size="small"
            :icon="VideoPlay"
            :disabled="!row.allowManual"
            :loading="runningCode === row.code"
            @click="triggerJob(row.code)"
          >
            立即执行
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <section class="logs">
      <header class="logs__head">
        <div>
          <h2>执行日志</h2>
          <p>成功摘要、失败原因与耗时都会留痕，按站点配置的保留天数自动清理</p>
        </div>
        <div class="logs__tools">
          <el-select v-model="logFilter" clearable placeholder="全部任务" style="width: 170px" @change="logPage = 1">
            <el-option v-for="item in jobs" :key="item.code" :label="item.name" :value="item.code" />
          </el-select>
          <el-button :icon="Refresh" @click="refreshLogs()" />
        </div>
      </header>

      <el-table :data="logsQuery.data.value?.list ?? []" class="table" size="small">
        <el-table-column prop="startedAt" label="开始时间" width="160" />
        <el-table-column label="任务" width="150">
          <template #default="{ row }">
            {{ jobs.find((item) => item.code === row.jobName)?.name ?? row.jobName }}
          </template>
        </el-table-column>
        <el-table-column label="触发" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.triggerBy === 'manual' ? 'warning' : 'info'" effect="plain">
              {{ row.triggerBy === 'manual' ? '手动' : '定时' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="结果" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.success ? 'success' : 'danger'" effect="light">
              {{ row.success ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.costMs === null ? '—' : `${row.costMs} ms` }}</template>
        </el-table-column>
        <el-table-column label="摘要 / 错误" min-width="240">
          <template #default="{ row }">
            <span :class="{ 'error-text': !row.success }">{{ row.success ? row.message : row.errorMsg }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pager">
        <el-pagination
          v-model:current-page="logPage"
          :page-size="15"
          :total="logsQuery.data.value?.total ?? 0"
          layout="total, prev, pager, next"
          background
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="`编辑 Cron · ${editing?.name ?? ''}`" width="460px">
      <div class="cron-form">
        <label class="cron-form__label">Cron 表达式（Quartz 六位或七位，支持 ?）</label>
        <el-input v-model="cronDraft" placeholder="0 0 3 * * ?" />
        <div class="cron-form__presets">
          <span>常用预设</span>
          <div>
            <el-button v-for="preset in presets" :key="preset.value" size="small" text bg @click="cronDraft = preset.value">
              {{ preset.label }}
            </el-button>
          </div>
        </div>
        <p class="cron-form__tip">保存后立刻重建触发器；表达式不合法时后端会拒绝并保留原配置。</p>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updateMutation.isPending.value" @click="saveCron()">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.jobs-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

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

.summary {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 18px;
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  background: linear-gradient(100deg, #f2fbf7, #fff);
}

.summary__icon {
  @include flex-center;
  width: 38px;
  height: 38px;
  border-radius: 10px;
  background: #dff5ec;
  color: #23a981;
  font-size: 19px;
}

.summary__text {
  display: flex;
  flex-direction: column;
  gap: 3px;

  strong {
    font-size: 13.5px;
    color: #1b2a32;
  }

  span {
    font-size: 11.5px;
    color: #7b8d97;
  }
}

.summary__tag {
  margin-left: auto;
  padding: 3px 10px;
  border-radius: 999px;
  background: #eef4ff;
  color: #4c6fff;
  font-size: 11px;
}

.table {
  width: 100%;
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 8px;
}

.job-name {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding: 4px 0;

  strong {
    font-size: 12.5px;
    color: #1b2a32;
  }

  span {
    font-size: 11px;
    line-height: 1.5;
    color: #9aa8b2;
  }
}

.cron-cell {
  display: flex;
  align-items: center;
  gap: 6px;

  code {
    font-size: 11px;
    color: #4c6fff;
    background: #f2f5ff;
    padding: 3px 7px;
    border-radius: 5px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }
}

.mono {
  font-size: 11.5px;
  color: #6b7c87;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.muted {
  font-size: 11px;
  color: #a6b2ba;
}

.last-run {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
}

.error-text {
  display: inline-block;
  max-width: 260px;
  @include ellipsis;
  color: #e05b5b;
  font-size: 11px;
}

.logs {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.logs__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;

  h2 {
    font-size: 15px;
    font-weight: 700;
    color: #1b2a32;
  }

  p {
    margin-top: 3px;
    font-size: 11.5px;
    color: #9aa8b2;
  }
}

.logs__tools {
  display: flex;
  gap: 8px;
}

.pager {
  display: flex;
  justify-content: flex-end;
}

.cron-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cron-form__label {
  font-size: 12px;
  color: #4a5b66;
}

.cron-form__presets {
  display: flex;
  flex-direction: column;
  gap: 8px;

  > span {
    font-size: 11.5px;
    color: #9aa8b2;
  }

  > div {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
}

.cron-form__tip {
  margin: 0;
  font-size: 11px;
  line-height: 1.6;
  color: #9aa8b2;
}
</style>
