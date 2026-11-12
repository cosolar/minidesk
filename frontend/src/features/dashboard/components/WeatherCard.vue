<script setup lang="ts">
import { computed } from 'vue'
import { Location } from '@element-plus/icons-vue'

interface Props {
  temp?: number
  text?: string
  city?: string
  isMock?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  temp: 18,
  text: '晴',
  city: '东京',
  isMock: true,
})

const summary = computed(() => `${props.temp}℃ ${props.text} · ${props.city}`)
</script>

<template>
  <div class="weather" role="status">
    <svg class="weather__icon" viewBox="0 0 32 32" width="18" height="18" aria-hidden="true">
      <circle cx="16" cy="16" r="6.6" fill="#FFC85C" />
      <g stroke="#FFC85C" stroke-width="1.7" stroke-linecap="round">
        <path d="M16 3.4v3.4M16 25.2v3.4M3.4 16h3.4M25.2 16h3.4M7.2 7.2l2.4 2.4M22.4 22.4l2.4 2.4M24.8 7.2l-2.4 2.4M9.6 22.4l-2.4 2.4" />
      </g>
    </svg>
    <span class="weather__temp">{{ temp }}℃</span>
    <span class="weather__desc">{{ text }}</span>
    <span class="weather__sep" aria-hidden="true">·</span>
    <el-icon class="weather__pin"><Location /></el-icon>
    <span class="weather__city">{{ city }}</span>
    <span v-if="isMock" class="weather__badge" title="未配置和风天气 Key，当前为模拟数据">模拟</span>
    <span class="sr-only">{{ summary }}</span>
  </div>
</template>

<style scoped lang="scss">
.weather {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 12px;
  border-radius: 12px;
  background: var(--surface-glass);
  border: 1px solid var(--glass-border);
  box-shadow: 0 8px 20px rgba(26, 74, 60, 0.09);
  backdrop-filter: blur(10px);
  white-space: nowrap;
}

.weather__icon {
  flex: 0 0 18px;
}

.weather__temp {
  font-family: var(--font-number);
  font-size: 12.5px;
  font-weight: 700;
  color: var(--hero-title);
}

.weather__desc {
  font-size: 11.5px;
  color: var(--hero-sub);
}

.weather__sep {
  color: var(--text-tertiary);
  font-size: 11px;
}

.weather__pin {
  font-size: 11px;
  color: var(--text-tertiary);
}

.weather__city {
  font-size: 11px;
  color: var(--text-secondary);
}

.weather__badge {
  margin-left: 2px;
  padding: 1px 5px;
  border-radius: 5px;
  background: rgba(255, 176, 32, 0.16);
  color: #c98214;
  font-size: 9px;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
