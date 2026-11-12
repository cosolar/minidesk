<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Delete, EditPen, Message, Phone, Plus, Search } from '@element-plus/icons-vue'
import { contactApi, type ContactPayload, type ContactVO } from '../api'

const queryClient = useQueryClient()

const TYPES = [
  { key: 'client', label: '客户' },
  { key: 'colleague', label: '同事' },
  { key: 'partner', label: '合作伙伴' },
  { key: 'family', label: '家人' },
]

const keyword = ref('')
const typeFilter = ref('')

const listQuery = useQuery({
  queryKey: ['contacts', keyword, typeFilter],
  queryFn: () =>
    contactApi.list({ keyword: keyword.value || undefined, type: typeFilter.value || undefined }),
  staleTime: 30_000,
})

const contacts = computed(() => listQuery.data.value ?? [])

/** 按首字分组，超过 30 人时滚起来更有方向 */
const groups = computed(() => {
  const map = new Map<string, ContactVO[]>()
  for (const item of contacts.value) {
    const list = map.get(item.initial) ?? []
    list.push(item)
    map.set(item.initial, list)
  }
  return Array.from(map.entries()).sort((a, b) => a[0].localeCompare(b[0], 'zh-CN'))
})

function typeLabel(type: string): string {
  return TYPES.find((item) => item.key === type)?.label ?? '其他'
}

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()

const form = reactive<ContactPayload>({
  name: '',
  type: 'client',
  company: '',
  position: '',
  phone: '',
  email: '',
  wechat: '',
  birthday: '',
  note: '',
})

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    name: '',
    type: 'client',
    company: '',
    position: '',
    phone: '',
    email: '',
    wechat: '',
    birthday: '',
    note: '',
  })
  dialogVisible.value = true
}

function openEdit(item: ContactVO): void {
  editingId.value = item.id
  Object.assign(form, {
    name: item.name,
    type: item.type,
    company: item.company ?? '',
    position: item.position ?? '',
    phone: item.phone ?? '',
    email: item.email ?? '',
    wechat: item.wechat ?? '',
    birthday: item.birthday ?? '',
    note: item.note ?? '',
  })
  dialogVisible.value = true
}

const saveMutation = useMutation({
  mutationFn: () =>
    editingId.value ? contactApi.update(editingId.value, { ...form }) : contactApi.create({ ...form }),
  onSuccess: () => {
    ElMessage.success('已保存')
    dialogVisible.value = false
    queryClient.invalidateQueries({ queryKey: ['contacts'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saveMutation.mutate()
}

async function removeContact(item: ContactVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${item.name}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await contactApi.remove(item.id)
  queryClient.invalidateQueries({ queryKey: ['contacts'] })
  ElMessage.success('已删除')
}

async function copy(text: string | null, label: string): Promise<void> {
  if (!text) {
    return
  }
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success(`${label}已复制`)
  } catch {
    ElMessage.warning('浏览器未授权剪贴板')
  }
}
</script>

<template>
  <section class="contact-page">
    <header class="page-head">
      <div>
        <h1>联系人</h1>
        <p class="page-head__sub">客户、同事、合作伙伴与家人，生日自动倒数</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新增联系人</el-button>
    </header>

    <div class="contact-bar">
      <el-radio-group v-model="typeFilter" size="small">
        <el-radio-button value="">全部</el-radio-button>
        <el-radio-button v-for="item in TYPES" :key="item.key" :value="item.key">
          {{ item.label }}
        </el-radio-button>
      </el-radio-group>
      <el-input v-model="keyword" :prefix-icon="Search" placeholder="搜姓名 / 公司 / 电话" clearable class="contact-search" />
    </div>

    <div v-if="listQuery.isLoading.value" class="contact-hint">加载中…</div>
    <el-empty v-else-if="!contacts.length" description="还没有联系人" />
    <div v-else class="contact-groups">
      <section v-for="group in groups" :key="group[0]" class="contact-group">
        <h2 class="contact-group__title">{{ group[0] }}</h2>
        <div class="contact-grid">
          <article v-for="item in group[1]" :key="item.id" class="contact-card">
            <span class="contact-card__avatar">{{ item.initial }}</span>
            <div class="contact-card__body">
              <div class="contact-card__name">
                <h3>{{ item.name }}</h3>
                <el-tag size="small" effect="plain">{{ typeLabel(item.type) }}</el-tag>
                <el-tag v-if="item.daysToBirthday !== null && item.daysToBirthday <= 30" size="small" type="danger" effect="light">
                  {{ item.daysToBirthday === 0 ? '今天生日' : `生日 ${item.daysToBirthday} 天后` }}
                </el-tag>
              </div>
              <p class="contact-card__org">
                {{ [item.company, item.position].filter(Boolean).join(' · ') || '—' }}
              </p>
              <div class="contact-card__links">
                <button v-if="item.phone" type="button" @click="copy(item.phone, '电话')">
                  <el-icon><Phone /></el-icon>{{ item.phone }}
                </button>
                <button v-if="item.wechat" type="button" @click="copy(item.wechat, '微信号')">
                  <el-icon><Message /></el-icon>{{ item.wechat }}
                </button>
                <span v-if="item.birthday" class="contact-card__birthday">
                  生日 {{ item.birthday.slice(5) }}
                </span>
              </div>
            </div>
            <div class="contact-card__ops">
              <el-button text size="small" :icon="EditPen" @click="openEdit(item)" />
              <el-button text size="small" :icon="Delete" @click="removeContact(item)" />
            </div>
          </article>
        </div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑联系人' : '新增联系人'" width="560px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="72px">
        <el-form-item
          label="姓名"
          prop="name"
          :rules="[{ required: true, message: '请输入姓名', trigger: 'blur' }]"
        >
          <el-input v-model="form.name" maxlength="64" />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 180px">
            <el-option v-for="item in TYPES" :key="item.key" :label="item.label" :value="item.key" />
          </el-select>
        </el-form-item>
        <el-form-item label="公司">
          <el-input v-model="form.company" maxlength="128" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="form.position" maxlength="64" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" maxlength="32" />
        </el-form-item>
        <el-form-item label="微信">
          <el-input v-model="form.wechat" maxlength="64" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="128" />
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="用于生日提醒" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.note" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saveMutation.isPending.value" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.contact-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;

  h1 {
    font-size: 22px;
    font-weight: 700;
  }
}

.page-head__sub {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.contact-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.contact-search {
  max-width: 280px;
}

.contact-hint {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.contact-groups {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.contact-group__title {
  margin-bottom: 10px;
  font-size: 12px;
  font-weight: 600;
  color: var(--text-tertiary);
}

.contact-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 12px;
}

.contact-card {
  @include card($radius-lg);
  position: relative;
  display: flex;
  gap: 12px;
  padding: 14px;
}

.contact-card__avatar {
  @include flex-center;
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #35c79b, #4c6fff);
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  flex-shrink: 0;
}

.contact-card__body {
  flex: 1;
  min-width: 0;
}

.contact-card__name {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;

  h3 {
    font-size: 14px;
    font-weight: 600;
  }
}

.contact-card__org {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  @include ellipsis(1);
}

.contact-card__links {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-tertiary);

  button {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    border: none;
    background: transparent;
    color: inherit;
    font-size: inherit;
    cursor: pointer;
    padding: 0;

    &:hover {
      color: var(--brand-500);
    }
  }
}

.contact-card__birthday {
  margin-left: auto;
}

.contact-card__ops {
  position: absolute;
  right: 8px;
  top: 8px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.contact-card:hover .contact-card__ops {
  opacity: 1;
}
</style>
