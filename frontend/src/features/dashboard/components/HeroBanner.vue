<script setup lang="ts">
import { computed } from 'vue'
import dayjs from 'dayjs'
import { useClock } from '@/core/composables/useClock'
import ClockCard from './ClockCard.vue'
import WeatherCard from './WeatherCard.vue'
import { useUserStore } from '@/core/stores/user'
import { useAppStore } from '@/core/stores/app'
import lakeImage from '@/assets/hero-lake.jpg'

const { now } = useClock(60_000)
const userStore = useUserStore()
const appStore = useAppStore()

const props = defineProps<{
  weather?: { temp: number; text: string; city: string; isMock: boolean } | null
}>()

const greeting = computed(() => {
  const hour = dayjs(now.value).hour()
  if (hour < 6) {
    return '凌晨好'
  }
  if (hour < 11) {
    return '上午好'
  }
  if (hour < 14) {
    return '中午好'
  }
  if (hour < 18) {
    return '下午好'
  }
  return '晚上好'
})
</script>

<template>
  <section class="hero">
    <img class="hero__photo" :src="lakeImage" alt="" aria-hidden="true" />
    <span class="hero__tint" aria-hidden="true" />
    <span class="hero__haze" aria-hidden="true" />
    <span class="hero__fade" aria-hidden="true" />

    <div class="hero__content">
      <div class="hero__greeting">
        <h1>{{ greeting }}，{{ userStore.displayName }}！<span class="wave">👋</span></h1>
        <p>{{ appStore.themeMeta.greeting }}</p>
      </div>

      <div class="hero__meta">
        <ClockCard />
        <WeatherCard
          v-if="props.weather"
          :temp="props.weather.temp"
          :text="props.weather.text"
          :city="props.weather.city"
          :is-mock="props.weather.isMock"
        />
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.hero {
  position: relative;
  margin: -4px (-1 * $content-padding) 0;
  height: 318px;
  overflow: hidden;
  isolation: isolate;

  @include respond-below(sm) {
    height: 236px;
    margin: -4px -14px 0;
  }
}

.hero__photo {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 46%;
  z-index: 0;
  /* 同一张实景图，按主题做调色（滤镜与透明度都由 CSS 变量给） */
  filter: var(--hero-photo-filter);
  opacity: var(--hero-photo-opacity);
  /* 图片自身向下淡出，避免与页面底色之间出现硬边 */
  -webkit-mask-image: linear-gradient(180deg, #000 0%, #000 52%, rgba(0, 0, 0, 0.55) 78%, transparent 100%);
  mask-image: linear-gradient(180deg, #000 0%, #000 52%, rgba(0, 0, 0, 0.55) 78%, transparent 100%);
}

/* 主题色罩：紫/暖/夜空靠它把照片调成对应气质 */
.hero__tint {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-tint);
  mix-blend-mode: var(--hero-tint-blend, normal);
  opacity: var(--hero-tint-opacity, 1);
}

/* 左侧雾化，保证问候语在水面之上依然清晰 */
.hero__haze {
  position: absolute;
  inset: 0;
  z-index: 1;
  background: var(--hero-haze);
}

/* 底部渐隐到页面底色，避免出现硬边 */
.hero__fade {
  position: absolute;
  inset: auto 0 0 0;
  height: 120px;
  z-index: 1;
  background: linear-gradient(180deg, transparent 0%, var(--hero-fade-mid) 62%, var(--bg-page) 100%);
}

.hero__content {
  position: relative;
  z-index: 2;
  height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  padding: 44px 56px 0;

  @include respond-below(sm) {
    padding: 26px 20px 0;
    gap: 12px;
  }
}

.hero__greeting {
  padding-top: 6px;

  h1 {
    font-family: var(--font-display);
    font-size: 38px;
    font-weight: 700;
    line-height: 1.24;
    letter-spacing: 0.6px;
    color: var(--hero-title);
    text-shadow: 0 1px 18px rgba(10, 30, 40, 0.12);
  }

  p {
    margin-top: 10px;
    font-size: 13.5px;
    letter-spacing: 0.6px;
    color: var(--hero-sub);
  }

  @include respond-below(sm) {
    h1 {
      font-size: 24px;
    }

    p {
      margin-top: 6px;
      font-size: 12px;
    }
  }
}

.wave {
  display: inline-block;
  font-size: 0.78em;
  transform-origin: 70% 70%;
  animation: wave 2.6s $ease-out 0.5s 2;
}

@keyframes wave {
  0%,
  60%,
  100% {
    transform: rotate(0deg);
  }
  10%,
  30% {
    transform: rotate(14deg);
  }
  20%,
  40% {
    transform: rotate(-8deg);
  }
}

.hero__meta {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-end;
  padding-top: 2px;

  @include respond-below(sm) {
    gap: 6px;
  }
}
</style>
