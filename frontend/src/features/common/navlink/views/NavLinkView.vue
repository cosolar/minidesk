<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Delete, EditPen, Link, Plus, Search } from '@element-plus/icons-vue'
import { navLinkApi, type NavLinkVO } from '../api'

const props = defineProps<{ module: 'work' | 'study' }>()

const queryClient = useQueryClient()

const meta = computed(() =>
  props.module === 'work'
    ? { title: '工具', sub: '把每天要开的后台、文档、监控收到一页，少翻收藏夹' }
    : { title: '资源', sub: '常用学习站点导航，点开次数会自动累计' },
)

const keyword = ref('')

const listQuery = useQuery({
  queryKey: ['nav-links', props.module, keyword],
  queryFn: () => navLinkApi.list(props.module, keyword.value || undefined),
  staleTime: 30_000,
})

const links = computed(() => listQuery.data.value ?? [])

/** 按分组聚合；分组名保持后端返回顺序 */
const groups = computed(() => {
  const map = new Map<string, NavLinkVO[]>()
  for (const link of links.value) {
    const list = map.get(link.groupName) ?? []
    list.push(link)
    map.set(link.groupName, list)
  }
  return Array.from(map.entries()).map(([name, items]) => ({ name, items }))
})

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const form = reactive({
  groupName: '常用',
  title: '',
  url: '',
  description: '',
})

function colorOf(title: string): string {
  const palette = ['#35C79B', '#4C6FFF', '#FF9F43', '#8B7CF6', '#F25F5C', '#2AA6C9']
  let hash = 0
  for (const char of title) {
    hash = (hash * 31 + char.charCodeAt(0)) % 9973
  }
  return palette[hash % palette.length]
}

/** 只显示域名，避免模板里写正则（Volar 对模板中的 // 解析不稳） */
function hostOf(url: string): string {
  return url.replace(/^https?:\/\//, '').replace(/\/$/, '')
}

function openCreate(): void {
  editingId.value = null
  Object.assign(form, { groupName: '常用', title: '', url: '', description: '' })
  dialogVisible.value = true
}

function openEdit(link: NavLinkVO): void {
  editingId.value = link.id
  Object.assign(form, {
    groupName: link.groupName,
    title: link.title,
    url: link.url,
    description: link.description ?? '',
  })
  dialogVisible.value = true
}

const saveMutation = useMutation({
  mutationFn: () =>
    editingId.value
      ? navLinkApi.update(editingId.value, { module: props.module, ...form })
      : navLinkApi.create({ module: props.module, ...form }),
  onSuccess: () => {
    ElMessage.success('已保存')
    dialogVisible.value = false
    queryClient.invalidateQueries({ queryKey: ['nav-links'] })
  },
})

async function submit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saveMutation.mutate()
}

async function openLink(link: NavLinkVO): Promise<void> {
  window.open(link.url, '_blank', 'noopener')
  navLinkApi.click(link.id).catch(() => undefined)
}

async function removeLink(link: NavLinkVO): Promise<void> {
  const ok = await ElMessageBox.confirm(`删除「${link.title}」？`, '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await navLinkApi.remove(link.id)
  queryClient.invalidateQueries({ queryKey: ['nav-links'] })
  ElMessage.success('已删除')
}

/** 空态推荐：从零开始一页空白很难受，给几个通用站点一键添加 */
const SUGGESTIONS = computed(() =>
  props.module === 'work'
    ? [
        { title: '飞书', url: 'https://www.feishu.cn' },
        { title: '腾讯文档', url: 'https://docs.qq.com' },
        { title: 'GitHub', url: 'https://github.com' },
        { title: '阿里云控制台', url: 'https://home.console.aliyun.com' },
      ]
    : [
        { title: 'MDN', url: 'https://developer.mozilla.org' },
        { title: 'GitHub', url: 'https://github.com' },
        { title: '掘金', url: 'https://juejin.cn' },
        { title: 'B 站', url: 'https://www.bilibili.com' },
      ],
)

async function addSuggestion(item: { title: string; url: string }): Promise<void> {
  await navLinkApi.create({ module: props.module, groupName: '常用', title: item.title, url: item.url })
  queryClient.invalidateQueries({ queryKey: ['nav-links'] })
}
</script>

<template>
  <section class="nav-page">
    <header class="page-head">
      <div>
        <h1>{{ meta.title }}</h1>
        <p class="page-head__sub">{{ meta.sub }}</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreate">添加</el-button>
    </header>

    <el-input
      v-model="keyword"
      :prefix-icon="Search"
      placeholder="搜索名称、描述或域名"
      clearable
      class="nav-search"
    />

    <div v-if="listQuery.isLoading.value" class="nav-hint">加载中…</div>

    <div v-else-if="!links.length" class="nav-empty">
      <p>还没有链接。先加几个常用的：</p>
      <div class="nav-suggest">
        <el-button v-for="item in SUGGESTIONS" :key="item.url" plain @click="addSuggestion(item)">
          <el-icon class="nav-suggest__icon"><Link /></el-icon>
          {{ item.title }}
        </el-button>
      </div>
    </div>

    <div v-else class="nav-groups">
      <section v-for="group in groups" :key="group.name" class="nav-group">
        <h2 class="nav-group__title">{{ group.name }}</h2>
        <div class="nav-grid">
          <article v-for="link in group.items" :key="link.id" class="nav-card" @click="openLink(link)">
            <span class="nav-card__avatar" :style="{ background: colorOf(link.title) }">
              {{ link.title.slice(0, 1).toUpperCase() }}
            </span>
            <div class="nav-card__body">
              <h3>{{ link.title }}</h3>
              <p>{{ link.description || hostOf(link.url) }}</p>
            </div>
            <div class="nav-card__ops" @click.stop>
              <el-button text size="small" :icon="EditPen" @click="openEdit(link)" />
              <el-button text size="small" :icon="Delete" @click="removeLink(link)" />
            </div>
          </article>
        </div>
      </section>
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑' : '添加'" width="480px" destroy-on-close>
      <el-form ref="formRef" :model="form" label-width="68px">
        <el-form-item label="分组">
          <el-input v-model="form.groupName" placeholder="如：内部系统 / 文档" maxlength="64" />
        </el-form-item>
        <el-form-item
          label="名称"
          prop="title"
          :rules="[{ required: true, message: '请输入名称', trigger: 'blur' }]"
        >
          <el-input v-model="form.title" placeholder="如：飞书" maxlength="120" />
        </el-form-item>
        <el-form-item label="地址" prop="url">
          <el-input v-model="form.url" placeholder="feishu.cn" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="选填，一句话说明" maxlength="200" />
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
.nav-page {
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

.nav-search {
  max-width: 320px;
}

.nav-hint {
  padding: 40px;
  text-align: center;
  color: var(--text-tertiary);
  font-size: 13px;
}

.nav-empty {
  @include card($radius-lg);
  padding: 28px;
  color: var(--text-secondary);
  font-size: 13px;
}

.nav-suggest {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 14px;
}

.nav-suggest__icon {
  margin-right: 4px;
}

.nav-groups {
  display: flex;
  flex-direction: column;
  gap: 22px;
}

.nav-group__title {
  margin-bottom: 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}

.nav-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 12px;
}

.nav-card {
  @include card($radius-lg);
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 10px 26px rgba(24, 36, 32, 0.08);
  }
}

.nav-card__avatar {
  @include flex-center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}

.nav-card__body {
  min-width: 0;

  h3 {
    font-size: 13px;
    font-weight: 600;
    @include ellipsis(1);
  }

  p {
    margin-top: 2px;
    font-size: 11px;
    color: var(--text-tertiary);
    @include ellipsis(1);
  }
}

.nav-card__ops {
  position: absolute;
  right: 6px;
  top: 6px;
  opacity: 0;
  transition: opacity 0.16s ease;
}

.nav-card:hover .nav-card__ops {
  opacity: 1;
}
</style>
