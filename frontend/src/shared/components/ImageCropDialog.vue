<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { RefreshLeft, ZoomIn, ZoomOut } from '@element-plus/icons-vue'

/**
 * 图片裁剪 + 压缩弹窗。
 *
 * 上传前先在本地裁好：视口是固定尺寸的方形（按 aspect 可调），图片用 cover 方式铺满，
 * 拖动平移、滚轮或滑杆缩放，确认时把可见区域画进 canvas，统一导出 WebP。
 * 这样后端拿到的永远是压缩过的小图，也不用引入 cropper 之类的依赖。
 */

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    file: File | null
    aspect?: number
    outputSize?: number
    quality?: number
    title?: string
  }>(),
  {
    aspect: 1,
    outputSize: 640,
    quality: 0.86,
    title: '裁剪图片',
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [file: File]
}>()

const VIEW_W = 264
const viewH = computed(() => Math.round(VIEW_W / props.aspect))
const MAX_ZOOM = 3

const loading = ref(false)
const saving = ref(false)
const zoom = ref(1)
const offset = ref({ x: 0, y: 0 })
const natural = ref({ w: 0, h: 0 })
const objectUrl = ref('')
const errorText = ref('')

let source: HTMLImageElement | ImageBitmap | null = null
let dragging = false
let dragStart = { x: 0, y: 0, ox: 0, oy: 0 }

/** 铺满视口所需的基准比例：zoom=1 时短边刚好贴满 */
const baseScale = computed(() => {
  const { w, h } = natural.value
  if (!w || !h) {
    return 1
  }
  return Math.max(VIEW_W / w, viewH.value / h)
})

const scale = computed(() => baseScale.value * zoom.value)

const imageStyle = computed(() => ({
  width: `${natural.value.w * scale.value}px`,
  height: `${natural.value.h * scale.value}px`,
  transform: `translate3d(${offset.value.x}px, ${offset.value.y}px, 0)`,
}))

/** 裁剪框在源图上的实际边长：小图不放大，避免糊 */
const sourceSide = computed(() => Math.round(VIEW_W / scale.value))

const outputBox = computed(() => {
  const side = Math.max(64, Math.min(props.outputSize, sourceSide.value))
  return {
    w: Math.round(side * props.aspect),
    h: side,
  }
})

function clampOffset(): void {
  const sx = natural.value.w * scale.value
  const sy = natural.value.h * scale.value
  const minX = Math.min(0, VIEW_W - sx)
  const minY = Math.min(0, viewH.value - sy)
  offset.value = {
    x: Math.min(0, Math.max(minX, offset.value.x)),
    y: Math.min(0, Math.max(minY, offset.value.y)),
  }
}

function centerImage(): void {
  const sx = natural.value.w * scale.value
  const sy = natural.value.h * scale.value
  offset.value = { x: (VIEW_W - sx) / 2, y: (viewH.value - sy) / 2 }
  clampOffset()
}

function reset(): void {
  zoom.value = 1
  centerImage()
}

function applyZoom(next: number, anchor?: { x: number; y: number }): void {
  const value = Math.min(MAX_ZOOM, Math.max(1, Number(next.toFixed(3))))
  if (value === zoom.value) {
    return
  }
  const point = anchor ?? { x: VIEW_W / 2, y: viewH.value / 2 }
  const prev = scale.value
  const imgX = (point.x - offset.value.x) / prev
  const imgY = (point.y - offset.value.y) / prev
  zoom.value = value
  offset.value = {
    x: point.x - imgX * scale.value,
    y: point.y - imgY * scale.value,
  }
  clampOffset()
}

function onWheel(event: WheelEvent): void {
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect()
  const anchor = { x: event.clientX - rect.left, y: event.clientY - rect.top }
  applyZoom(zoom.value - event.deltaY * 0.0016, anchor)
}

function onPointerDown(event: PointerEvent): void {
  if (!source) {
    return
  }
  dragging = true
  dragStart = { x: event.clientX, y: event.clientY, ox: offset.value.x, oy: offset.value.y }
  ;(event.currentTarget as HTMLElement).setPointerCapture(event.pointerId)
}

function onPointerMove(event: PointerEvent): void {
  if (!dragging) {
    return
  }
  offset.value = {
    x: dragStart.ox + (event.clientX - dragStart.x),
    y: dragStart.oy + (event.clientY - dragStart.y),
  }
  clampOffset()
}

function onPointerUp(event: PointerEvent): void {
  dragging = false
  ;(event.currentTarget as HTMLElement).releasePointerCapture?.(event.pointerId)
}

async function loadFile(file: File): Promise<void> {
  loading.value = true
  errorText.value = ''
  // 先释放上一张（含 ImageBitmap），再挂新的 objectURL
  cleanupUrl()
  natural.value = { w: 0, h: 0 }
  objectUrl.value = URL.createObjectURL(file)

  try {
    if (typeof createImageBitmap === 'function') {
      const bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
      source = bitmap
      natural.value = { w: bitmap.width, h: bitmap.height }
    } else {
      const image = await new Promise<HTMLImageElement>((resolve, reject) => {
        const element = new Image()
        element.onload = () => resolve(element)
        element.onerror = () => reject(new Error('图片读取失败'))
        element.src = objectUrl.value
      })
      source = image
      natural.value = { w: image.naturalWidth, h: image.naturalHeight }
    }
    reset()
  } catch {
    errorText.value = '这张图片读不出来，换一张试试'
  } finally {
    loading.value = false
  }
}

function cleanupUrl(): void {
  if (objectUrl.value) {
    URL.revokeObjectURL(objectUrl.value)
    objectUrl.value = ''
  }
  if (typeof ImageBitmap !== 'undefined' && source instanceof ImageBitmap) {
    source.close()
  }
  source = null
}

function canvasToBlob(canvas: HTMLCanvasElement, type: string, quality: number): Promise<Blob | null> {
  return new Promise((resolve) => canvas.toBlob(resolve, type, quality))
}

async function confirm(): Promise<void> {
  if (!source || saving.value) {
    return
  }
  saving.value = true
  try {
    const { w, h } = outputBox.value
    const canvas = document.createElement('canvas')
    canvas.width = w
    canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) {
      throw new Error('浏览器不支持画布裁切')
    }
    ctx.imageSmoothingEnabled = true
    ctx.imageSmoothingQuality = 'high'

    // 视口坐标 → 原图像素坐标
    const sx = -offset.value.x / scale.value
    const sy = -offset.value.y / scale.value
    const sw = VIEW_W / scale.value
    const sh = viewH.value / scale.value
    ctx.drawImage(source, sx, sy, sw, sh, 0, 0, w, h)

    // 统一压成 WebP；个别浏览器不支持时退回 JPEG，避免传空文件
    let blob = await canvasToBlob(canvas, 'image/webp', props.quality)
    let type = 'image/webp'
    let ext = 'webp'
    if (!blob || blob.type !== 'image/webp') {
      blob = await canvasToBlob(canvas, 'image/jpeg', props.quality)
      type = 'image/jpeg'
      ext = 'jpg'
    }
    if (!blob) {
      throw new Error('图片压缩失败')
    }

    const base = (props.file?.name ?? 'image').replace(/\.[^.]+$/, '')
    emit('confirm', new File([blob], `${base}.${ext}`, { type }))
    close()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '裁剪失败')
  } finally {
    saving.value = false
  }
}

function close(): void {
  emit('update:modelValue', false)
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible && props.file) {
      void loadFile(props.file)
    }
    if (!visible) {
      cleanupUrl()
    }
  },
)

watch(
  () => props.file,
  (file) => {
    if (props.modelValue && file) {
      void loadFile(file)
    }
  },
)

onBeforeUnmount(cleanupUrl)
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    width="360px"
    append-to-body
    align-center
    class="crop-dialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="crop">
      <div
        class="crop__viewport"
        :style="{ height: `${viewH}px` }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
        @pointercancel="onPointerUp"
        @wheel.prevent="onWheel"
      >
        <img v-if="objectUrl" class="crop__image" :src="objectUrl" :style="imageStyle" alt="" draggable="false" />
        <div v-if="loading" class="crop__mask">正在读取图片…</div>
        <div v-else-if="errorText" class="crop__mask is-error">{{ errorText }}</div>
        <span class="crop__corner is-tl" aria-hidden="true" />
        <span class="crop__corner is-tr" aria-hidden="true" />
        <span class="crop__corner is-bl" aria-hidden="true" />
        <span class="crop__corner is-br" aria-hidden="true" />
      </div>

      <div class="crop__tools">
        <button type="button" class="crop__zoom" :disabled="zoom <= 1" @click="applyZoom(zoom - 0.1)">
          <el-icon><ZoomOut /></el-icon>
        </button>
        <el-slider
          :model-value="zoom"
          :min="1"
          :max="3"
          :step="0.01"
          :show-tooltip="false"
          class="crop__slider"
          @update:model-value="applyZoom(Number($event))"
        />
        <button type="button" class="crop__zoom" :disabled="zoom >= 3" @click="applyZoom(zoom + 0.1)">
          <el-icon><ZoomIn /></el-icon>
        </button>
        <button type="button" class="crop__reset" title="复位" @click="reset">
          <el-icon><RefreshLeft /></el-icon>
        </button>
      </div>

      <p class="crop__hint">
        拖动调整位置，滚轮或滑杆缩放 · 压缩为 WebP（{{ outputBox.w }}×{{ outputBox.h }}）
      </p>
    </div>

    <template #footer>
      <el-button @click="close">取消</el-button>
      <el-button type="primary" :loading="saving" :disabled="loading || !!errorText" @click="confirm">
        使用这张
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped lang="scss">
.crop {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.crop__viewport {
  position: relative;
  width: 264px;
  overflow: hidden;
  border-radius: 14px;
  background: var(--bg-subtle);
  cursor: grab;
  touch-action: none;
  box-shadow: inset 0 0 0 1px var(--border-color);

  &:active {
    cursor: grabbing;
  }
}

.crop__image {
  position: absolute;
  top: 0;
  left: 0;
  display: block;
  max-width: none;
  user-select: none;
  pointer-events: none;
  will-change: transform;
}

.crop__mask {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 16px;
  font-size: 12px;
  text-align: center;
  color: var(--text-secondary);
  background: color-mix(in srgb, var(--bg-card) 82%, transparent);

  &.is-error {
    color: var(--c-alert);
  }
}

.crop__corner {
  position: absolute;
  width: 16px;
  height: 16px;
  border: 2px solid #fff;
  opacity: 0.9;
  pointer-events: none;

  &.is-tl {
    top: 6px;
    left: 6px;
    border-right: 0;
    border-bottom: 0;
    border-radius: 4px 0 0;
  }

  &.is-tr {
    top: 6px;
    right: 6px;
    border-bottom: 0;
    border-left: 0;
    border-radius: 0 4px 0 0;
  }

  &.is-bl {
    bottom: 6px;
    left: 6px;
    border-top: 0;
    border-right: 0;
    border-radius: 0 0 0 4px;
  }

  &.is-br {
    right: 6px;
    bottom: 6px;
    border-top: 0;
    border-left: 0;
    border-radius: 0 0 4px;
  }
}

.crop__tools {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.crop__slider {
  flex: 1;
  --el-slider-height: 4px;
  --el-slider-button-size: 14px;
}

.crop__zoom,
.crop__reset {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 26px;
  height: 26px;
  border: 1px solid var(--border-color);
  border-radius: 8px;
  background: var(--bg-card);
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: color $duration-fast $ease-out, border-color $duration-fast $ease-out;

  &:hover:not(:disabled) {
    color: var(--brand-600);
    border-color: color-mix(in srgb, var(--brand-400) 45%, var(--border-color));
  }

  &:disabled {
    opacity: 0.45;
    cursor: default;
  }
}

.crop__reset {
  margin-left: 2px;
}

.crop__hint {
  font-size: 10.5px;
  line-height: 1.6;
  text-align: center;
  color: var(--text-tertiary);
}
</style>
