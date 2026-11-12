<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { Icon } from '@iconify/vue'

const route = useRoute()
const title = computed(() => route.meta.title ?? '页面')
</script>

<template>
  <section class="placeholder">
    <div class="placeholder__icon">
      <Icon icon="lucide:construction" width="34" />
    </div>
    <h2>{{ title }}</h2>
    <p>这个页面已进入开发计划，将在一期后续阶段或二期实现。</p>
    <p class="placeholder__hint">
      当前版本完成的是：工程骨架、设计系统、首页看板、路由与请求层。
    </p>
    <RouterLink to="/" class="placeholder__back">返回工作台</RouterLink>
  </section>
</template>

<style scoped lang="scss">
.placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 8px;
  text-align: center;
  @include card($radius-xl);
  padding: 48px 24px;
}

.placeholder__icon {
  @include flex-center;
  width: 64px;
  height: 64px;
  margin-bottom: 6px;
  border-radius: 20px;
  background: var(--brand-50);
  color: var(--brand-600);
}

h2 {
  font-size: 18px;
  font-weight: 600;
}

p {
  font-size: 13px;
  color: var(--text-secondary);
  max-width: 420px;
}

.placeholder__hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.placeholder__back {
  margin-top: 12px;
  padding: 8px 18px;
  border-radius: 999px;
  background: var(--brand-500);
  color: #fff;
  font-size: 12.5px;
  transition: background $duration-fast $ease-out;

  &:hover {
    background: var(--brand-600);
  }
}
</style>
