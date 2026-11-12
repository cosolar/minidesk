<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { consoleApi, type ConfigItem } from '../api'
import BrandSettings from '../components/BrandSettings.vue'
import MailSettings from '../components/MailSettings.vue'

const queryClient = useQueryClient()
const tab = ref<'config' | 'brand' | 'mail' | 'login' | 'oper'>('config')
const form = reactive<Record<string, string>>({})
const loaded = ref(false)

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
  queryKey: ['console', 'logs', 'oper'],
  queryFn: () => consoleApi.operLogs(1, 30),
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

function isSwitch(key: string): boolean {
  return key === 'allow_register' || key === 'install_claimed'
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
                <strong>{{ item.key }}</strong>
                <span>{{ item.remark }}</span>
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
        <el-table :data="loginQuery.data.value?.list ?? []" height="calc(100vh - 240px)" class="table">
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
        <el-table :data="operQuery.data.value?.list ?? []" height="calc(100vh - 240px)" class="table">
          <el-table-column prop="username" label="操作人" width="120" />
          <el-table-column prop="module" label="模块" width="150" />
          <el-table-column prop="action" label="动作" width="150" />
          <el-table-column prop="targetId" label="对象" width="100" />
          <el-table-column prop="result" label="结果" width="90" />
          <el-table-column prop="createdAt" label="时间" />
        </el-table>
        <p class="hint">审计日志只增不改；当前版本尚未接入 AOP 自动埋点，先记录登录与账号操作。</p>
      </el-tab-pane>
    </el-tabs>
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

.config-list {
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8ecf2;
}

.config-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f3f7;

  &:last-child {
    border-bottom: none;
  }
}

.config-row__label {
  display: flex;
  flex-direction: column;

  strong {
    font-size: 12.5px;
    color: #1b2a32;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }

  span {
    margin-top: 2px;
    font-size: 11px;
    color: #9aa8b2;
  }
}

.table {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8ecf2;
}

.hint {
  margin-top: 10px;
  font-size: 11px;
  color: #9aa8b2;
}
</style>
