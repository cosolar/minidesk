<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, ElMessageBox, type UploadRequestOptions } from 'element-plus'
import { Delete, Picture, Plus, Upload } from '@element-plus/icons-vue'
import { uploadFile } from '@/core/http/upload'
import { fileUrl } from '@/core/utils/file'
import { albumApi, type AlbumVO, type PhotoVO } from '../api'

const queryClient = useQueryClient()

const albumsQuery = useQuery({
  queryKey: ['albums'],
  queryFn: () => albumApi.list(),
  staleTime: 20_000,
})

const albums = computed(() => albumsQuery.data.value ?? [])
const activeId = ref<number | null>(null)

watch(
  albums,
  (list) => {
    if (!activeId.value && list.length) {
      activeId.value = list[0].id
    }
  },
  { immediate: true },
)

const detailQuery = useQuery({
  queryKey: ['album', activeId],
  queryFn: () => albumApi.detail(activeId.value as number),
  enabled: computed(() => activeId.value !== null),
})

const activeAlbum = computed<AlbumVO | null>(() => detailQuery.data.value ?? null)
const photos = computed<PhotoVO[]>(() => activeAlbum.value?.photos ?? [])
const previewList = computed(() => photos.value.map((item) => fileUrl(item.url)))

const albumDialog = ref(false)
const albumForm = reactive({ name: '', description: '' })

function openCreateAlbum(): void {
  albumForm.name = ''
  albumForm.description = ''
  albumDialog.value = true
}

const createAlbumMutation = useMutation({
  mutationFn: () => albumApi.create({ name: albumForm.name, description: albumForm.description }),
  onSuccess: (created) => {
    ElMessage.success('相册已创建')
    albumDialog.value = false
    activeId.value = created.id
    queryClient.invalidateQueries({ queryKey: ['albums'] })
  },
})

async function removeAlbum(): Promise<void> {
  if (!activeId.value) {
    return
  }
  const ok = await ElMessageBox.confirm('删除相册会同时删除其中的照片记录，确定？', '确认删除', {
    type: 'warning',
  }).catch(() => false)
  if (!ok) {
    return
  }
  await albumApi.remove(activeId.value)
  activeId.value = null
  queryClient.invalidateQueries({ queryKey: ['albums'] })
  ElMessage.success('已删除')
}

const photoDialog = ref(false)
const photoTab = ref<'upload' | 'url'>('upload')
const uploading = ref(false)
const photoForm = reactive({ title: '', url: '', takenAt: '' })

function openAddPhoto(): void {
  photoTab.value = 'upload'
  photoForm.title = ''
  photoForm.url = ''
  photoForm.takenAt = ''
  photoDialog.value = true
}

const addPhotoMutation = useMutation({
  mutationFn: (payload: { url?: string; fileId?: number; title?: string; takenAt?: string }) =>
    albumApi.addPhoto(activeId.value as number, payload),
  onSuccess: () => {
    ElMessage.success('已添加')
    photoDialog.value = false
    invalidate()
  },
})

async function onUpload(options: UploadRequestOptions): Promise<void> {
  if (!activeId.value) {
    return
  }
  uploading.value = true
  try {
    const result = await uploadFile(options.file as File, 'album')
    addPhotoMutation.mutate({ fileId: result.id, title: photoForm.title || result.name })
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function submitUrl(): void {
  if (!photoForm.url.trim()) {
    ElMessage.warning('请填写图片地址')
    return
  }
  addPhotoMutation.mutate({
    url: photoForm.url.trim(),
    title: photoForm.title || undefined,
    takenAt: photoForm.takenAt || undefined,
  })
}

async function removePhoto(photo: PhotoVO): Promise<void> {
  const ok = await ElMessageBox.confirm('删除这张照片？', '确认删除', { type: 'warning' }).catch(
    () => false,
  )
  if (!ok) {
    return
  }
  await albumApi.removePhoto(photo.id)
  invalidate()
  ElMessage.success('已删除')
}

function invalidate(): void {
  queryClient.invalidateQueries({ queryKey: ['album', activeId] })
  queryClient.invalidateQueries({ queryKey: ['albums'] })
}
</script>

<template>
  <section class="album-page">
    <header class="page-head">
      <div>
        <h1>相册</h1>
        <p class="page-head__sub">图片可以上传到本机，也可以直接贴图床地址</p>
      </div>
      <el-button type="primary" :icon="Plus" @click="openCreateAlbum">新建相册</el-button>
    </header>

    <div class="album-body">
      <aside class="album-side">
        <div v-if="albumsQuery.isLoading.value" class="album-hint">加载中…</div>
        <div v-else-if="!albums.length" class="album-hint">还没有相册</div>
        <ul v-else class="album-list">
          <li
            v-for="album in albums"
            :key="album.id"
            class="album-item"
            :class="{ 'is-active': album.id === activeId }"
            @click="activeId = album.id"
          >
            <div class="album-item__cover">
              <el-image v-if="album.coverUrl" :src="fileUrl(album.coverUrl)" fit="cover" />
              <el-icon v-else><Picture /></el-icon>
            </div>
            <div class="album-item__meta">
              <span class="album-item__name">{{ album.name }}</span>
              <span class="album-item__count">{{ album.photoCount }} 张</span>
            </div>
          </li>
        </ul>
      </aside>

      <div class="album-main">
        <div v-if="!activeAlbum" class="album-empty">
          <el-empty description="选择左侧相册，或先新建一个" />
        </div>
        <template v-else>
          <div class="album-main__head">
            <div>
              <h2>{{ activeAlbum.name }}</h2>
              <p v-if="activeAlbum.description" class="album-main__desc">{{ activeAlbum.description }}</p>
            </div>
            <div class="album-main__ops">
              <el-button :icon="Upload" @click="openAddPhoto">添加照片</el-button>
              <el-button :icon="Delete" @click="removeAlbum" />
            </div>
          </div>

          <div v-if="!photos.length" class="album-empty">
            <el-empty description="相册还是空的，添加第一张照片" />
          </div>
          <div v-else class="photo-grid">
            <figure v-for="(photo, index) in photos" :key="photo.id" class="photo-card">
              <el-image
                :src="fileUrl(photo.url)"
                :preview-src-list="previewList"
                :initial-index="index"
                fit="cover"
                preview-teleported
                class="photo-card__img"
              />
              <figcaption class="photo-card__cap">
                <span>{{ photo.title || photo.takenAt || '' }}</span>
                <el-button text size="small" :icon="Delete" @click.stop="removePhoto(photo)" />
              </figcaption>
            </figure>
          </div>
        </template>
      </div>
    </div>

    <el-dialog v-model="albumDialog" title="新建相册" width="420px">
      <el-form :model="albumForm" label-width="64px">
        <el-form-item label="名称">
          <el-input v-model="albumForm.name" maxlength="120" placeholder="如：2026 旅行" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="albumForm.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="albumDialog = false">取消</el-button>
        <el-button
          type="primary"
          :loading="createAlbumMutation.isPending.value"
          :disabled="!albumForm.name.trim()"
          @click="createAlbumMutation.mutate()"
        >
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="photoDialog" title="添加照片" width="520px">
      <el-tabs v-model="photoTab">
        <el-tab-pane label="上传图片" name="upload">
          <el-upload :show-file-list="false" :http-request="onUpload" accept=".jpg,.jpeg,.png,.webp,.gif,.bmp" drag>
            <el-icon class="photo-upload__icon"><Upload /></el-icon>
            <div class="photo-upload__text">点击或拖拽图片到这里</div>
            <div class="photo-upload__hint">单张不超过 20MB，支持 jpg / png / webp</div>
          </el-upload>
          <el-input v-model="photoForm.title" placeholder="照片说明（选填）" class="photo-upload__title" />
        </el-tab-pane>
        <el-tab-pane label="图床地址" name="url">
          <el-input v-model="photoForm.url" placeholder="oss.example.com/2026/xxx.jpg" />
          <el-input v-model="photoForm.title" placeholder="照片说明（选填）" class="photo-upload__title" />
          <el-date-picker v-model="photoForm.takenAt" type="date" value-format="YYYY-MM-DD" placeholder="拍摄日期（选填）" style="width: 100%" />
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="photoDialog = false">取消</el-button>
        <el-button v-if="photoTab === 'url'" type="primary" :loading="addPhotoMutation.isPending.value" @click="submitUrl">
          添加
        </el-button>
        <span v-else class="photo-upload__loading">{{ uploading ? '上传中…' : '' }}</span>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped lang="scss">
.album-page {
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

.album-body {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.album-side {
  @include card($radius-lg);
  padding: 10px;
}

.album-hint {
  padding: 24px 8px;
  font-size: 12px;
  color: var(--text-tertiary);
  text-align: center;
}

.album-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.album-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border-radius: 10px;
  cursor: pointer;

  &:hover {
    background: var(--bg-hover);
  }

  &.is-active {
    background: var(--brand-50);
  }
}

.album-item__cover {
  @include flex-center;
  width: 44px;
  height: 44px;
  border-radius: 8px;
  overflow: hidden;
  background: var(--bg-hover);
  color: var(--text-tertiary);

  :deep(.el-image) {
    width: 100%;
    height: 100%;
  }
}

.album-item__meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.album-item__name {
  font-size: 13px;
  font-weight: 600;
  @include ellipsis(1);
}

.album-item__count {
  font-size: 11px;
  color: var(--text-tertiary);
}

.album-main {
  @include card($radius-lg);
  padding: 18px;
  min-height: 420px;
}

.album-main__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;

  h2 {
    font-size: 16px;
    font-weight: 700;
  }
}

.album-main__desc {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-secondary);
}

.album-main__ops {
  display: flex;
  gap: 8px;
}

.album-empty {
  padding: 24px 0;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
  gap: 12px;
}

.photo-card {
  border-radius: 12px;
  overflow: hidden;
  background: var(--bg-hover);
}

.photo-card__img {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: block;
  cursor: zoom-in;
}

.photo-card__cap {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 6px 8px;
  font-size: 11px;
  color: var(--text-secondary);

  span {
    @include ellipsis(1);
  }
}

.photo-upload__icon {
  font-size: 28px;
  color: var(--text-tertiary);
  margin-top: 12px;
}

.photo-upload__text {
  font-size: 13px;
  margin-top: 6px;
}

.photo-upload__hint {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-bottom: 12px;
}

.photo-upload__title {
  margin-top: 12px;
}

.photo-upload__loading {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 900px) {
  .album-body {
    grid-template-columns: 1fr;
  }
}
</style>
