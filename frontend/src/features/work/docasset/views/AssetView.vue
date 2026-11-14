<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance, type UploadRequestOptions } from 'element-plus'
import { Delete, Document, EditPen, Link, Paperclip, Plus, Search } from '@element-plus/icons-vue'
import MarkdownEditor from '@/shared/components/MarkdownEditor.vue'
import { uploadFile } from '@/core/http/upload'
import { fileUrl } from '@/core/utils/file'
import { docAssetApi, type DocAssetPayload, type DocAssetVO, type DocType } from '../api'

const queryClient = useQueryClient()

const TABS: Array<{ key: string; label: string }> = [
  { key: '', label: '全部' },
  { key: 'markdown', label: '自建文档' },
  { key: 'link', label: '外链文档' },
  { key: 'file', label: '上传文件' },
]

const PROVIDERS = [
  { key: 'feishu', label: '飞书' },
  { key: 'tencent', label: '腾讯文档' },
  { key: 'yuque', label: '语雀' },
  { key: 'other', label: '其他' },
]

const activeTab = ref('')
const keyword = ref('')

const listQuery = useQuery({
  queryKey: ['doc-assets', activeTab, keyword],
  queryFn: () =>
    docAssetApi.list({ docType: activeTab.value || undefined, keyword: keyword.value || undefined }),
  staleTime: 15_000,
})

const items = computed(() => listQuery.data.value ?? [])

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const submitting = ref(false)
const uploading = ref(false)
const formRef = ref<FormInstance>()

interface AssetForm {
  title: string
  docType: DocType
  provider: string
  url: string
  content: string
  fileId: number | null
  category: string
  description: string
}

const form = reactive<AssetForm>({
  title: '',
  docType: 'markdown',
  provider: 'feishu',
  url: '',
  content: '',
  fileId: null,
  category: '',
  description: '',
})

const rules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
}

function providerLabel(provider: string | null): string {
  return PROVIDERS.find((item) => item.key === provider)?.label ?? '外链'
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, {
    title: '',
    docType: 'markdown',
    provider: 'feishu',
    url: '',
    content: '',
    fileId: null,
    category: '',
    description: '',
  })
  dialogVisible.value = true
}

async function openEdit(item: DocAssetVO): Promise<void> {
  const detail = await docAssetApi.detail(item.id)
  editingId.value = item.id
  Object.assign(form, {
    title: detail.title,
    docType: detail.docType,
    provider: detail.provider ?? 'feishu',
    url: detail.url ?? '',
    content: detail.content ?? '',
    fileId: detail.fileId,
    category: detail.category ?? '',
    description: detail.description ?? '',
  })
  dialogVisible.value = true
}

const saveMutation = useMutation({
  mutationFn: (payload: DocAssetPayload) =>
    editingId.value ? docAssetApi.update(editingId.value, payload) : docAssetApi.create(payload),
  onSuccess: () => {
    ElMessage.success('已保存')
    dialogVisible.value = false
    queryClient.invalidateQueries({ queryKey: ['doc-assets'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  if (form.docType === 'link' && !form.url?.trim()) {
    ElMessage.warning('外链文档请填写地址')
    return
  }
  if (form.docType === 'file' && !form.fileId) {
    ElMessage.warning('请先上传文件')
    return
  }
  submitting.value = true
  try {
    saveMutation.mutate({ ...form })
  } finally {
    submitting.value = false
  }
}

async function onUpload(options: UploadRequestOptions): Promise<void> {
  uploading.value = true
  try {
    const result = await uploadFile(options.file as File, 'doc')
    form.fileId = result.id
    if (!form.title) {
      form.title = result.name
    }
    form.url = result.url
    ElMessage.success('文件已上传')
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function openAsset(item: DocAssetVO): void {
  if (item.docType === 'markdown') {
    openEdit(item)
    return
  }
  const target = item.docType === 'file' && item.fileId ? fileUrl(`/api/files/${item.fileId}/raw`) : item.url
  if (target) {
    window.open(target, '_blank', 'noopener')
  }
}

async function removeAsset(item: DocAssetVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${item.title}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await docAssetApi.remove(item.id)
  queryClient.invalidateQueries({ queryKey: ['doc-assets'] })
  ElMessage.success('已删除')
}

function typeIcon(type: DocType) {
  return type === 'link' ? Link : type === 'file' ? Paperclip : Document
}
</script>

<template>
  <section class="asset-page">
    <header class="page-head">
      <div>
        <h1>资产</h1>
        <p class="page-head__sub">外部文档收口到这里：飞书、腾讯文档、自建 Markdown、上传附件</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">新建文档</el-button>
    </header>

    <div class="asset-bar">
      <el-radio-group v-model="activeTab" size="small">
        <el-radio-button v-for="tab in TABS" :key="tab.key" :value="tab.key">{{ tab.label }}</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜索标题或描述"
        clearable
        class="asset-search"
      />
    </div>

    <div v-if="listQuery.isLoading.value" class="asset-hint">加载中…</div>
    <div v-else-if="!items.length" class="asset-empty">
      <el-empty description="还没有文档，把常用的飞书链接放进来试试" />
    </div>
    <div v-else class="asset-grid">
      <article v-for="item in items" :key="item.id" class="asset-card" @click="openAsset(item)">
        <div class="asset-card__icon" :class="`is-${item.docType}`">
          <el-icon><component :is="typeIcon(item.docType)" /></el-icon>
        </div>
        <div class="asset-card__body">
          <h3>{{ item.title }}</h3>
          <p v-if="item.description" class="asset-card__desc">{{ item.description }}</p>
          <div class="asset-card__meta">
            <span class="asset-card__tag">
              {{ item.docType === 'link' ? providerLabel(item.provider) : item.docType === 'file' ? '附件' : 'Markdown' }}
            </span>
            <span v-if="item.category" class="asset-card__cat">{{ item.category }}</span>
            <span class="asset-card__time">{{ item.updatedAt?.slice(0, 10) }}</span>
          </div>
        </div>
        <div class="asset-card__ops" @click.stop>
          <el-button text size="small" :icon="EditPen" @click="openEdit(item)" />
          <el-button text size="small" :icon="Delete" @click="removeAsset(item)" />
        </div>
      </article>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingId ? '编辑文档' : '新建文档'"
      width="760px"
      destroy-on-close
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="76px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.docType">
            <el-radio-button value="markdown">自建文档</el-radio-button>
            <el-radio-button value="link">外链文档</el-radio-button>
            <el-radio-button value="file">上传文件</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="文档标题" maxlength="200" />
        </el-form-item>

        <template v-if="form.docType === 'link'">
          <el-form-item label="来源">
            <el-select v-model="form.provider" style="width: 180px">
              <el-option v-for="p in PROVIDERS" :key="p.key" :label="p.label" :value="p.key" />
            </el-select>
          </el-form-item>
          <el-form-item label="地址">
            <el-input v-model="form.url" placeholder="xxx.feishu.cn/docx/..." />
          </el-form-item>
        </template>

        <template v-else-if="form.docType === 'file'">
          <el-form-item label="文件">
            <el-upload :show-file-list="false" :http-request="onUpload">
              <el-button :loading="uploading" :icon="Paperclip">
                {{ form.fileId ? '重新上传' : '选择文件' }}
              </el-button>
            </el-upload>
            <span v-if="form.fileId" class="asset-uploaded">已上传</span>
          </el-form-item>
        </template>

        <el-form-item label="分类">
          <el-input v-model="form.category" placeholder="如：产品 / 合同 / 规范" maxlength="64" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="一句话说明用途" maxlength="200" />
        </el-form-item>

        <el-form-item v-if="form.docType === 'markdown'" label="正文">
          <div class="asset-editor">
            <MarkdownEditor v-model="form.content" :height="420" placeholder="支持 Markdown" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.asset-page {
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

.asset-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.asset-search {
  max-width: 260px;
}

.asset-hint {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.asset-empty {
  @include card($radius-lg);
  padding: 24px;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 14px;
}

.asset-card {
  @include card($radius-lg);
  position: relative;
  display: flex;
  gap: 12px;
  padding: 16px;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 26px rgba(24, 36, 32, 0.08);
  }
}

.asset-card__icon {
  @include flex-center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  font-size: 18px;
  flex-shrink: 0;

  &.is-markdown {
    background: rgba(76, 111, 255, 0.12);
    color: #4c6fff;
  }

  &.is-link {
    background: color-mix(in srgb, var(--brand-500) 14%, transparent);
    color: var(--brand-500);
  }

  &.is-file {
    background: rgba(255, 159, 67, 0.14);
    color: #ff9f43;
  }
}

.asset-card__body {
  flex: 1;
  min-width: 0;

  h3 {
    font-size: 14px;
    font-weight: 600;
    @include ellipsis(1);
  }
}

.asset-card__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
  @include ellipsis(2);
}

.asset-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.asset-card__tag {
  padding: 1px 6px;
  border-radius: 5px;
  background: var(--bg-hover);
}

.asset-card__ops {
  position: absolute;
  right: 8px;
  bottom: 6px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.asset-card:hover .asset-card__ops {
  opacity: 1;
}

.asset-editor {
  width: 100%;
}

.asset-uploaded {
  margin-left: 10px;
  font-size: 12px;
  color: var(--brand-500);
}
</style>
