<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, FolderChecked, Plus, Refresh, Upload } from '@element-plus/icons-vue'
import {
  consoleApi,
  downloadBackup,
  uploadRestore,
  type BackupFile,
} from '../api'

const queryClient = useQueryClient()
const fileInput = ref<HTMLInputElement | null>(null)

const backupsQuery = useQuery({
  queryKey: ['console', 'backups'],
  queryFn: () => consoleApi.backups(),
  staleTime: 0,
})

const statusQuery = useQuery({
  queryKey: ['console', 'backups', 'restore-status'],
  queryFn: () => consoleApi.restoreStatus(),
  staleTime: 0,
})

const files = computed(() => backupsQuery.data.value ?? [])
const totalSize = computed(() => files.value.reduce((sum, item) => sum + item.size, 0))

function invalidateAll(): void {
  queryClient.invalidateQueries({ queryKey: ['console', 'backups'] })
}

const createMutation = useMutation({
  mutationFn: () => consoleApi.createBackup(),
  onSuccess: (file) => {
    ElMessage.success(`备份已生成：${file.fileName}（${file.sizeText}）`)
    invalidateAll()
  },
})

const deleteMutation = useMutation({
  mutationFn: (fileName: string) => consoleApi.deleteBackup(fileName),
  onSuccess: () => {
    ElMessage.success('备份已删除')
    invalidateAll()
  },
})

const restoreMutation = useMutation({
  mutationFn: (file: File) => uploadRestore(file),
  onSuccess: (result) => {
    ElMessage({ type: 'success', message: result.message, duration: 6000, showClose: true })
    invalidateAll()
  },
  onError: (error: Error) => {
    ElMessage.error(error.message || '恢复包上传失败')
  },
})

const downloadMutation = useMutation({
  mutationFn: (fileName: string) => downloadBackup(fileName),
  onError: (error: Error) => {
    ElMessage.error(error.message || '备份下载失败')
  },
})

async function removeFile(value: unknown): Promise<void> {
  const row = value as BackupFile
  try {
    await ElMessageBox.confirm(`删除备份 ${row.fileName}？该操作不可恢复。`, '删除备份', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  deleteMutation.mutate(row.fileName)
}

function pickFile(): void {
  fileInput.value?.click()
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  try {
    await ElMessageBox.confirm(
      '恢复包会在下次重启应用时覆盖当前数据库与附件；当前数据库会先另存一份。确认排队恢复？',
      '恢复确认',
      { type: 'warning', confirmButtonText: '排队恢复', cancelButtonText: '取消' },
    )
  } catch {
    return
  }
  restoreMutation.mutate(file)
}
</script>

<template>
  <div class="backups-page">
    <header class="page-head">
      <div>
        <h1>备份与恢复</h1>
        <p>一份 ZIP 就是全部数据：数据库、附件、品牌资源与集成加密主密钥</p>
      </div>
      <div class="page-head__actions">
        <el-button :icon="Refresh" @click="invalidateAll()">刷新</el-button>
        <el-button type="primary" :icon="Plus" :loading="createMutation.isPending.value" @click="createMutation.mutate()">
          立即备份
        </el-button>
      </div>
    </header>

    <section class="info-card">
      <div class="info-card__icon"><el-icon><FolderChecked /></el-icon></div>
      <ul>
        <li>快照由 SQLite 的一致性备份生成，运行中也可以安全导出，不需要停服务。</li>
        <li>ZIP 内含 <code>minidesk.db</code>、<code>uploads/</code>、<code>branding/</code> 与 <code>.secret</code>；少了密钥，恢复后天气与邮箱配置会解不开。</li>
        <li>恢复包上传后先排队，<strong>重启应用时</strong>才生效；启动前会校验 ZIP 与数据库完整性，失败自动保留原库。</li>
      </ul>
    </section>

    <section class="restore-card" :class="{ 'is-pending': statusQuery.data.value?.pending }">
      <div class="restore-card__head">
        <div>
          <h2>恢复状态</h2>
          <p v-if="statusQuery.data.value?.pending">
            有恢复包等待生效（排队于 {{ statusQuery.data.value.queuedAt ?? '—' }}），重启应用后自动还原
          </p>
          <p v-else>当前没有待生效的恢复包</p>
          <p v-if="statusQuery.data.value?.lastResult" class="last-result">
            上次结果：{{ statusQuery.data.value.lastResult }}
          </p>
        </div>
        <el-button type="warning" plain :icon="Upload" :loading="restoreMutation.isPending.value" @click="pickFile()">
          选择恢复包
        </el-button>
        <input ref="fileInput" class="file-input" type="file" accept=".zip" @change="onFileChange" />
      </div>
    </section>

    <section class="list">
      <header class="list__head">
        <div>
          <h2>备份文件</h2>
          <p>共 {{ files.length }} 份 · {{ (totalSize / 1024 / 1024).toFixed(1) }} MB，自动备份按「备份保留份数」清理</p>
        </div>
      </header>

      <el-table :data="files" class="table" v-loading="backupsQuery.isLoading.value" empty-text="还没有备份，点右上角「立即备份」生成第一份">
        <el-table-column label="文件名" min-width="230">
          <template #default="{ row }">
            <span class="file-name">{{ row.fileName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sizeText" label="大小" width="110" />
        <el-table-column label="来源" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.triggerBy === 'auto' ? 'info' : 'success'" effect="plain">
              {{ row.triggerBy === 'auto' ? '自动' : '手动' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'success' ? 'success' : 'danger'" effect="light">
              {{
                row.status === 'success'
                  ? '可下载'
                  : row.status === 'missing'
                    ? '文件缺失'
                    : row.status === 'failed'
                      ? '生成失败'
                      : '生成中'
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :icon="Download"
              :disabled="row.status !== 'success'"
              :loading="downloadMutation.isPending.value"
              @click="downloadMutation.mutate(row.fileName)"
            >
              下载
            </el-button>
            <el-button link type="danger" size="small" @click="removeFile(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>
  </div>
</template>

<style scoped lang="scss">
.backups-page {
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

.page-head__actions {
  display: flex;
  gap: 8px;
}

.info-card {
  display: flex;
  gap: 14px;
  padding: 14px 18px;
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  background: linear-gradient(100deg, #f2fbf7, #fff);

  ul {
    margin: 0;
    padding-left: 16px;
    display: flex;
    flex-direction: column;
    gap: 5px;
    font-size: 11.5px;
    line-height: 1.7;
    color: #6b7c87;
  }

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: #eef5f2;
    color: var(--brand-700);
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 11px;
  }

  strong {
    color: #1b2a32;
  }
}

.info-card__icon {
  @include flex-center;
  width: 36px;
  height: 36px;
  flex: 0 0 36px;
  border-radius: 10px;
  background: var(--brand-100);
  color: var(--brand-600);
  font-size: 18px;
}

.restore-card {
  padding: 14px 18px;
  border: 1px solid #e8ecf2;
  border-radius: 10px;
  background: #fff;

  &.is-pending {
    border-color: #f5d6a8;
    background: #fffbf4;
  }
}

.restore-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;

  h2 {
    font-size: 14px;
    font-weight: 700;
    color: #1b2a32;
  }

  p {
    margin-top: 4px;
    font-size: 11.5px;
    color: #7b8d97;
  }

  .last-result {
    color: #9aa8b2;
    font-size: 11px;
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  }
}

.file-input {
  display: none;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.list__head {
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

.table {
  width: 100%;
  background: #fff;
  border: 1px solid #e8ecf2;
  border-radius: 8px;
}

.file-name {
  font-size: 12px;
  color: #1b2a32;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}
</style>
