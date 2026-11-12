<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { consoleApi, type ConsoleUser } from '../api'

const queryClient = useQueryClient()
const keyword = ref('')
const page = ref(1)

const { data, isLoading } = useQuery({
  queryKey: ['console', 'users', keyword, page],
  queryFn: () => consoleApi.users({ keyword: keyword.value || undefined, page: page.value, size: 20 }),
  staleTime: 0,
})

const users = computed(() => data.value?.list ?? [])
const total = computed(() => data.value?.total ?? 0)


const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ username: '', nickname: '', password: 'Init@123456', roleCode: 'MEMBER' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入初始密码', trigger: 'blur' }],
}

const createMutation = useMutation({
  mutationFn: () => consoleApi.createUser({ ...form }),
  onSuccess: () => {
    ElMessage.success('用户已创建')
    dialogVisible.value = false
    form.username = ''
    form.nickname = ''
    invalidate()
  },
})

const statusMutation = useMutation({
  mutationFn: (payload: { id: string; enabled: boolean }) =>
    consoleApi.updateStatus(payload.id, payload.enabled),
  onSuccess: () => {
    ElMessage.success('状态已更新')
    invalidate()
  },
  onError: () => invalidate(),
})

const deleteMutation = useMutation({
  mutationFn: (id: string) => consoleApi.removeUser(id),
  onSuccess: () => {
    ElMessage.success('已删除')
    invalidate()
  },
})

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['console'] })
}

async function toggleStatus(row: ConsoleUser): Promise<void> {
  statusMutation.mutate({ id: row.id, enabled: row.status !== 1 })
}

async function resetPassword(row: ConsoleUser): Promise<void> {
  const confirmed = await ElMessageBox.confirm(
    `重置「${row.username}」的密码？将生成一次性临时密码。`,
    '重置密码',
    { type: 'warning', confirmButtonText: '重置', cancelButtonText: '取消' },
  ).catch(() => false)
  if (!confirmed) {
    return
  }
  const result = await consoleApi.resetPassword(row.id)
  await ElMessageBox.alert(
    `临时密码：${result.tempPassword}\n请通过安全渠道转达，用户下次登录需修改。`,
    '已重置',
    { confirmButtonText: '我已记录' },
  )
}

async function removeUser(row: ConsoleUser): Promise<void> {
  const confirmed = await ElMessageBox.confirm(`删除「${row.username}」？数据保留 30 天后清理。`, '确认删除', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  }).catch(() => false)
  if (confirmed) {
    deleteMutation.mutate(row.id)
  }
}

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  submitting.value = true
  try {
    await createMutation.mutateAsync()
  } finally {
    submitting.value = false
  }
}

function asUser(row: unknown): ConsoleUser {
  return row as ConsoleUser
}

function roleText(roles: string[]): string {
  return roles.includes('ADMIN') ? '管理员' : '成员'
}
</script>

<template>
  <div class="console-users">
    <header class="page-head">
      <div>
        <h1>用户管理</h1>
        <p>共 {{ total }} 个账号；只能看到数据量，看不到内容</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">新建用户</el-button>
    </header>

    <div class="toolbar">
      <el-input
        v-model="keyword"
        placeholder="搜索用户名或昵称"
        clearable
        style="width: 240px"
        @change="page = 1"
      />
    </div>

    <el-table v-loading="isLoading" :data="users" class="table" height="calc(100vh - 250px)">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="nickname" label="昵称" width="120" />
      <el-table-column label="角色" width="90">
        <template #default="{ row }">{{ roleText(row.roles) }}</template>
      </el-table-column>
      <el-table-column label="来源" width="100">
        <template #default="{ row }">
          {{ row.source === 'self' ? '自助注册' : row.source === 'init' ? '初始化' : '后台创建' }}
        </template>
      </el-table-column>
      <el-table-column label="数据量（任务/笔记/账单）" width="200">
        <template #default="{ row }">
          <span class="tabular">{{ row.taskCount }} / {{ row.noteCount }} / {{ row.ledgerCount }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" width="170" />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
            {{ row.status === 1 ? '正常' : '已禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template #default="{ row }">
          <el-button link type="primary" @click="toggleStatus(asUser(row))">
            {{ row.status === 1 ? '禁用' : '启用' }}
          </el-button>
          <el-button link type="primary" @click="resetPassword(asUser(row))">重置密码</el-button>
          <el-button link type="danger" @click="removeUser(asUser(row))">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      class="pager"
      layout="prev, pager, next"
      :total="total"
      :page-size="20"
      background
    />

    <el-dialog v-model="dialogVisible" title="新建用户" width="400px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" maxlength="32" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="32" />
        </el-form-item>
        <el-form-item label="初始密码" prop="password">
          <el-input v-model="form.password" show-password />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width: 100%">
            <el-option label="成员" value="MEMBER" />
            <el-option label="管理员" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.console-users {
  display: flex;
  flex-direction: column;
  gap: 14px;
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

.toolbar {
  display: flex;
  gap: 10px;
}

.table {
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e8ecf2;
}

.pager {
  justify-content: flex-end;
}
</style>
