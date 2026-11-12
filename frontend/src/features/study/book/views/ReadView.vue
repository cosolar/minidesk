<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { bookApi, type BookVO } from '../api'

const queryClient = useQueryClient()
const statusFilter = ref('')

const { data, isLoading } = useQuery({
  queryKey: ['books', statusFilter],
  queryFn: () => bookApi.list(statusFilter.value || undefined),
  staleTime: 10_000,
})

const books = computed(() => data.value ?? [])
const reading = computed(() => books.value.filter((book) => book.status === 'reading').length)

const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const form = reactive({ title: '', author: '', totalPages: 300 })

const rules: FormRules = { title: [{ required: true, message: '请输入书名', trigger: 'blur' }] }

const createMutation = useMutation({
  mutationFn: () =>
    bookApi.create({
      title: form.title.trim(),
      author: form.author || undefined,
      totalPages: form.totalPages,
    }),
  onSuccess: () => {
    ElMessage.success('已加入书单')
    dialogVisible.value = false
    form.title = ''
    form.author = ''
    queryClient.invalidateQueries({ queryKey: ['books'] })
  },
})

const updateMutation = useMutation({
  mutationFn: (payload: { id: string; status?: string; readPages?: number }) =>
    bookApi.update(payload.id, { status: payload.status, readPages: payload.readPages }),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['books'] }),
})

const notesBook = ref<BookVO | null>(null)
const notesVisible = ref(false)
const noteContent = ref('')

const notesQuery = useQuery({
  queryKey: ['book-notes', notesBook],
  queryFn: () => bookApi.notes((notesBook.value as BookVO).id),
  enabled: computed(() => Boolean(notesBook.value)),
  staleTime: 0,
})

function openNotes(book: BookVO): void {
  notesBook.value = book
  notesVisible.value = true
}

async function addNote(): Promise<void> {
  if (!notesBook.value || !noteContent.value.trim()) {
    return
  }
  await bookApi.addNote(notesBook.value.id, { content: noteContent.value.trim() })
  noteContent.value = ''
  notesQuery.refetch()
  ElMessage.success('笔记已保存')
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

function statusText(status: string): string {
  return { wish: '想读', reading: '在读', done: '读完' }[status] ?? status
}
</script>

<template>
  <div class="read-page">
    <header class="page-head">
      <div>
        <h1>阅读</h1>
        <p class="page-head__sub">在读 {{ reading }} 本，读完的会沉淀成笔记</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="dialogVisible = true">加入书单</el-button>
    </header>

    <div class="filters">
      <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 140px">
        <el-option label="想读" value="wish" />
        <el-option label="在读" value="reading" />
        <el-option label="读完" value="done" />
      </el-select>
    </div>

    <el-skeleton v-if="isLoading" :rows="5" animated />

    <div v-else-if="books.length" class="book-grid">
      <article v-for="book in books" :key="book.id" class="book">
        <div class="book__head">
          <div class="book__spine" aria-hidden="true" />
          <div class="book__info">
            <h3>{{ book.title }}</h3>
            <p>{{ book.author || '未知作者' }} · {{ statusText(book.status) }}</p>
          </div>
        </div>

        <div class="book__progress">
          <div class="book__bar"><span :style="{ width: `${book.progress}%` }" /></div>
          <span class="tabular">{{ book.progress }}%</span>
        </div>

        <div class="book__actions">
          <el-button v-if="book.status !== 'reading'" size="small" @click="updateMutation.mutate({ id: book.id, status: 'reading' })">
            开始读
          </el-button>
          <el-button v-else size="small" type="primary" @click="updateMutation.mutate({ id: book.id, status: 'done' })">
            读完了
          </el-button>
          <el-button size="small" text @click="openNotes(book)">笔记</el-button>
        </div>
      </article>
    </div>

    <el-empty v-else description="书单是空的">
      <el-button type="primary" @click="dialogVisible = true">加一本</el-button>
    </el-empty>

    <el-dialog v-model="dialogVisible" title="加入书单" width="400px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="书名" prop="title">
          <el-input v-model="form.title" maxlength="80" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.author" maxlength="40" />
        </el-form-item>
        <el-form-item label="总页数">
          <el-input-number v-model="form.totalPages" :min="1" :max="5000" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="notesVisible" :title="notesBook?.title ?? '读书笔记'" size="420px">
      <div class="notes">
        <ul v-if="notesQuery.data.value?.length" class="notes__list">
          <li v-for="note in notesQuery.data.value" :key="note.id" class="notes__item">
            <p>{{ note.content }}</p>
            <span>{{ note.createdAt?.slice(0, 16).replace('T', ' ') }}</span>
          </li>
        </ul>
        <el-empty v-else description="还没有笔记" :image-size="60" />

        <div class="notes__add">
          <el-input v-model="noteContent" type="textarea" :rows="3" placeholder="记下这一段的想法" />
          <el-button type="primary" size="small" @click="addNote">添加笔记</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.read-page {
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

.book-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;

  @include respond-below(lg) {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.book {
  padding: 16px;
  @include card($radius-xl);
}

.book__head {
  display: flex;
  gap: 12px;
}

.book__spine {
  width: 42px;
  height: 58px;
  flex: 0 0 42px;
  border-radius: 4px 8px 8px 4px;
  background: linear-gradient(135deg, var(--brand-200), var(--brand-400));
}

.book__info {
  min-width: 0;

  h3 {
    font-size: 14px;
    font-weight: 600;
    @include ellipsis(1);
  }

  p {
    margin-top: 4px;
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.book__progress {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 14px 0 10px;

  span {
    font-size: 11px;
    color: var(--brand-700);
    font-weight: 600;
  }
}

.book__bar {
  flex: 1;
  height: 5px;
  border-radius: 999px;
  background: var(--bg-subtle);
  overflow: hidden;

  span {
    display: block;
    height: 100%;
    border-radius: 999px;
    background: linear-gradient(90deg, var(--brand-400), var(--brand-500));
  }
}

.book__actions {
  display: flex;
  gap: 6px;
}

.notes {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
}

.notes__list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.notes__item {
  padding: 10px 12px;
  border-radius: 10px;
  background: var(--bg-subtle);

  p {
    font-size: 12.5px;
    line-height: 1.7;
    white-space: pre-wrap;
  }

  span {
    display: block;
    margin-top: 6px;
    font-size: 10px;
    color: var(--text-tertiary);
  }
}

.notes__add {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
}
</style>
