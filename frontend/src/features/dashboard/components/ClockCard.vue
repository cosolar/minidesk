<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import 'dayjs/locale/zh-cn'
import { useClock } from '@/core/composables/useClock'

dayjs.locale('zh-cn')

const { now } = useClock(1000)

const timeText = computed(() => dayjs(now.value).format('HH:mm:ss'))
const dateText = computed(() => dayjs(now.value).format('YYYY年MM月DD日 dddd'))
</script>

<template>
  <div class="clock">
    <div class="clock__time">{{ timeText }}</div>
    <div class="clock__date">{{ dateText }}</div>
  </div>
</template>

<style scoped lang="scss">
.clock {
  min-width: 168px;
  padding: 11px 16px 10px;
  border-radius: 16px;
  background: var(--surface-glass);
  border: 1px solid var(--glass-border);
  box-shadow: 0 10px 26px rgba(26, 74, 60, 0.1);
  backdrop-filter: blur(10px);
  text-align: right;
}

.clock__time {
  font-family: var(--font-number);
  font-size: 26px;
  font-weight: 700;
  line-height: 1.15;
  letter-spacing: 0.8px;
  color: var(--hero-title);
  font-variant-numeric: tabular-nums;
}

.clock__date {
  margin-top: 3px;
  font-size: 10.5px;
  letter-spacing: 0.2px;
  color: var(--hero-sub);
  white-space: nowrap;
}

@include respond-below(sm) {
  .clock {
    min-width: 132px;
    padding: 8px 12px;
  }

  .clock__time {
    font-size: 19px;
  }

  .clock__date {
    font-size: 9.5px;
  }
}
</style>
