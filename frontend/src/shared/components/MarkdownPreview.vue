<script lang="ts">
/** 预览区提取出的大纲条目，id 由组件生成，供目录点击跳转 */
export interface MarkdownHeading {
  id: string
  text: string
  level: number
}
</script>

<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Viewer } from '@bytemd/vue-next'
import 'bytemd/dist/index.css'
import { markdownPlugins } from '@/shared/markdown/plugins'

const props = withDefaults(defineProps<{ source: string }>(), { source: '' })

const emit = defineEmits<{
  outline: [items: MarkdownHeading[]]
  active: [id: string]
}>()

const HEADING_SELECTOR = 'h1, h2, h3, h4'

const host = ref<HTMLDivElement>()

let renderSeq = 0
let scrollHost: HTMLElement | null = null
let activeId = ''
let frame = 0

/** 找到真正滚动的祖先容器，滚动高亮挂在它上面 */
function findScrollHost(element: HTMLElement): HTMLElement | null {
  let node = element.parentElement
  while (node) {
    const overflow = getComputedStyle(node).overflowY
    if (overflow === 'auto' || overflow === 'scroll') {
      return node
    }
    node = node.parentElement
  }
  return null
}

function collectHeadings(): MarkdownHeading[] {
  if (!host.value) {
    return []
  }
  const nodes = Array.from(host.value.querySelectorAll<HTMLElement>(HEADING_SELECTOR))
  return nodes.map((node, index) => {
    const id = `md-heading-${index}`
    node.id = id
    return { id, text: node.textContent?.trim() ?? '', level: Number(node.tagName.slice(1)) }
  })
}

function syncActive(): void {
  if (!scrollHost || !host.value) {
    return
  }
  const threshold = scrollHost.getBoundingClientRect().top + 84
  const nodes = host.value.querySelectorAll<HTMLElement>(HEADING_SELECTOR)
  // 未滚动时默认高亮第一个标题，避免目录整列都是灰的
  let current = nodes.length ? nodes[0].id : ''
  nodes.forEach((node) => {
    if (node.getBoundingClientRect().top <= threshold) {
      current = node.id
    }
  })
  if (current && current !== activeId) {
    activeId = current
    emit('active', current)
  }
}

function onScroll(): void {
  if (frame) {
    return
  }
  frame = requestAnimationFrame(() => {
    frame = 0
    syncActive()
  })
}

/** 等 Viewer 把新内容写入 DOM 后再抽大纲 */
async function refresh(): Promise<void> {
  const seq = ++renderSeq
  activeId = ''
  emit('active', '')
  await nextTick()
  if (seq !== renderSeq || !host.value) {
    return
  }
  emit('outline', props.source.trim() ? collectHeadings() : [])
  syncActive()
}

/** 目录点击跳转 */
function scrollTo(id: string): void {
  host.value
    ?.querySelector<HTMLElement>(`#${id}`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

watch(
  () => props.source,
  () => {
    void refresh()
  },
)

onMounted(() => {
  void refresh()
  void nextTick(() => {
    scrollHost = host.value ? findScrollHost(host.value) : null
    scrollHost?.addEventListener('scroll', onScroll, { passive: true })
  })
})

onBeforeUnmount(() => {
  renderSeq++
  scrollHost?.removeEventListener('scroll', onScroll)
  scrollHost = null
  if (frame) {
    cancelAnimationFrame(frame)
  }
})

defineExpose({ scrollTo })
</script>

<template>
  <div ref="host" class="md-preview">
    <Viewer v-if="source.trim()" :value="source" :plugins="markdownPlugins" />
  </div>
</template>

<style scoped lang="scss">
.md-preview {
  overflow-wrap: break-word;

  // ByteMD 给正文容器加了 800px 限宽和 4% 内边距，外层已控制版心，这里还原
  :deep(.markdown-body) {
    max-width: none;
    margin: 0;
    padding: 0;
  }
}
</style>
