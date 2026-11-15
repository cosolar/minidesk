<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { consoleApi, type ConfigItem, type OperLog } from '../api'
import BrandSettings from '../components/BrandSettings.vue'
import MailSettings from '../components/MailSettings.vue'

const queryClient = useQueryClient()
const tab = ref<'config' | 'brand' | 'mail' | 'login' | 'oper'>('config')
const form = reactive<Record<string, string>>({})
const loaded = ref(false)
const operKeyword = ref('')
const operResult = ref('')
const operPage = ref(1)
const detailVisible = ref(false)
const detailRow = ref<OperLog | null>(null)

const configQuery = useQuery({
  queryKey: ['console', 'config'],
  queryFn: () => consoleApi.config(),
  staleTime: 0,
})

const loginQuery = useQuery({
  queryKey: ['console', 'logs', 'login'],
  queryFn: () => consoleApi.loginLogs(1, 30),
  staleTime: 0,
})

const operQuery = useQuery({
  queryKey: ['console', 'logs', 'oper', operKeyword, operResult, operPage],
  queryFn: () => consoleApi.operLogs(operPage.value, 20, operKeyword.value, operResult.value),
  staleTime: 0,
})

onMounted(async () => {
  const result = await configQuery.refetch()
  ;(result.data ?? []).forEach((item: ConfigItem) => {
    form[item.key] = item.value
  })
  loaded.value = true
})

const saveMutation = useMutation({
  mutationFn: () => consoleApi.updateConfig({ ...form }),
  onSuccess: () => {
    ElMessage.success('配置已保存')
    queryClient.invalidateQueries({ queryKey: ['console', 'config'] })
  },
})

const items = computed(() => configQuery.data.value ?? [])

const detailFields = computed(() => {
  const row = detailRow.value
  if (!row) {
    return []
  }
  return [
    { label: '操作人', value: row.username },
    { label: '模块 / 动作', value: `${row.module} / ${row.action}` },
    { label: '对象', value: `${row.targetType ?? '-'} ${row.targetId ?? ''}` },
    { label: '结果', value: row.result },
    { label: '耗时', value: row.costMs === null ? null : `${row.costMs} ms` },
    { label: '时间', value: row.createdAt },
    { label: 'traceId', value: row.traceId },
    { label: '错误信息', value: row.errorMsg },
  ]
})

function isSwitch(key: string): boolean {
  return key === 'allow_register' || key === 'install_claimed'
}

/** 配置项中文名：直接展示 config_key 太像后台日志，认不出在改什么 */
const CONFIG_LABELS: Record<string, string> = {
  site_name: '站点名称',
  allow_register: '开放自助注册',
  install_claimed: '管理员领取状态',
  password_min_length: '密码最小长度',
  login_fail_limit: '登录失败锁定阈值',
  login_lock_minutes: '锁定时长（分钟）',
  audit_keep_days: '审计日志保留天数',
  note_recycle_days: '笔记回收站保留天数',
  backup_keep: '备份保留份数',
  site_logo_wide: '横版 Logo 文件名',
  'bookmark.migration.v1': '旧书签迁移标记',
}

function configLabel(key: string): string {
  return CONFIG_LABELS[key] ?? key
}

function openDetail(value: unknown): void {
  detailRow.value = value as OperLog
  detailVisible.value = true
}

function resetOperPage(): void {
  operPage.value = 1
}
</script>

<template>
  <div class="console-settings">
    <header class="page-head">
      <div>
        <h1>系统设置</h1>
        <p>站点级配置与审计日志</p>
      </div>
      <el-button v-if="tab === 'config'" type="primary" :loading="saveMutation.isPending.value" @click="saveMutation.mutate()">
        保存配置
      </el-button>
    </header>

    <el-tabs v-model="tab">
      <el-tab-pane label="站点配置" name="config">
        <el-skeleton :loading="!loaded" :rows="6" animated>
          <div class="config-list">
            <div v-for="item in items" :key="item.key" class="config-row">
              <div class="config-row__label">
                <strong>{{ configLabel(item.key) }}</strong>
                <span>{{ item.key }} · {{ item.remark }}</span>
              </div>
              <el-switch
                v-if="isSwitch(item.key)"
                :model-value="form[item.key] === 'true'"
                @update:model-value="(value: string | number | boolean) => (form[item.key] = value ? 'true' : 'false')"
              />
              <el-input v-else v-model="form[item.key]" style="max-width: 220px" />
            </div>
          </div>
        </el-skeleton>
      </el-tab-pane>

      <el-tab-pane label="品牌设置" name="brand">
        <BrandSettings />
      </el-tab-pane>

      <el-tab-pane label="发件邮箱" name="mail">
        <MailSettings />
      </el-tab-pane>

      <el-tab-pane label="登录日志" name="login">
        <el-table :data="loginQuery.data.value?.list ?? []" class="table">
          <el-table-column prop="username" label="账号" width="140" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.success ? 'success' : 'danger'" size="small">
                {{ row.success ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="failReason" label="原因" width="180" />
          <el-table-column prop="ip" label="IP" width="150" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="操作审计" name="oper">
        <div class="audit-tools">
          <el-input
            v-model="operKeyword"
            clearable
            placeholder="账号 / 模块 / 动作 / 对象"
            style="max-width: 240px"
            @keyup.enter="resetOperPage()"
            @clear="resetOperPage()"
          />
          <el-select v-model="operResult" clearable placeholder="结果" style="width: 120px" @change="resetOperPage()">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
          <el-button :icon="Refresh" @click="operQuery.refetch()">刷新</el-button>
        </div>
        <el-table :data="operQuery.data.value?.list ?? []" class="table">
          <el-table-column prop="username" label="操作人" width="120" />
          <el-table-column prop="module" label="模块" width="150" />
          <el-table-column prop="action" label="动作" width="150" />
          <el-table-column prop="targetId" label="对象" width="100" />
          <el-table-column label="结果" width="90">
            <template #default="{ row }">
              <el-tag :type="row.result === 'success' ? 'success' : 'danger'" size="small" effect="light">
                {{ row.result === 'success' ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="耗时" width="90">
            <template #default="{ row }">{{ row.costMs === null ? '—' : `${row.costMs} ms` }}</template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" width="170" />
          <el-table-column label="详情" width="80" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openDetail(row)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pager">
          <el-pagination
            v-model:current-page="operPage"
            :page-size="20"
            :total="operQuery.data.value?.total ?? 0"
            layout="total, prev, pager, next"
            background
          />
        </div>
        <p class="hint">写操作由 AOP 自动埋点；密码、令牌、验证码等字段落库前替换为 ***，日志只增不改。</p>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="detailVisible" title="审计详情" width="560px">
      <div v-if="detailRow" class="detail">
        <div v-for="field in detailFields" :key="field.label" class="detail__row">
          <span>{{ field.label }}</span>
          <code>{{ field.value ?? '—' }}</code>
        </div>
        <div class="detail__row detail__row--block">
          <span>请求参数（已脱敏）</span>
          <pre>{{ detailRow.params ?? '未记录' }}</pre>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.console-settings {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;

  h1 {
    color: var(--text-primary);
    font-size: 20px;
    font-weight: 700;
  }

  p {
    margin-top: 4px;
    color: var(--text-tertiary);
    font-size: 12px;
  }
}

.config-list {
  @include card($radius-lg);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-color);

  &:last-child {
    border-bottom: none;
  }
}

.config-row__label {
  display: flex;
  flex-direction: column;

  strong {
    color: var(--text-primary);
    font-size: 12.5px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }

  span {
    margin-top: 2px;
    color: var(--text-tertiary);
    font-size: 11px;
  }
}

.table {
  @include card($radius-lg);
  overflow: hidden;
}

.hint {
  margin-top: 10px;
  color: var(--text-tertiary);
  font-size: 11px;
}

.audit-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.detail__row {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border-color);

  > span {
    flex: 0 0 110px;
    color: var(--text-tertiary);
    font-size: 11.5px;
  }

  code,
  pre {
    flex: 1;
    min-width: 0;
    margin: 0;
    color: var(--text-primary);
    font-size: 11.5px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    white-space: pre-wrap;
    word-break: break-all;
  }

  &--block {
    flex-direction: column;
    border-bottom: none;
  }
}
</style>
