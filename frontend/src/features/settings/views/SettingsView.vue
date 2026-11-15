<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { useAppStore } from '@/core/stores/app'
import type { ThemeId } from '@/core/theme/catalog'
import ThemePicker from '@/shared/components/ThemePicker.vue'
import { settingsApi, type Integration, type TestResult } from '../api'
import { kbApi } from '@/features/study/kb/api'

const appStore = useAppStore()

const form = reactive<{ city: string; theme: ThemeId }>({
  city: '北京',
  theme: 'mint',
})

interface IntegrationForm {
  enabled: boolean
  apiHost: string
  apiKey: string
  maskedKey: string
  hasKey: boolean
}

const kb = ref<IntegrationForm>({
  enabled: false,
  apiHost: 'https://docs.minims.cn',
  apiKey: '',
  maskedKey: '',
  hasKey: false,
})

const testing = ref(false)
const kbTesting = ref(false)
const weatherStatus = ref<TestResult | null>(null)
const settingsQuery = useQuery({
  queryKey: ['settings'],
  queryFn: () => settingsApi.get(),
})

const integrationsQuery = useQuery({
  queryKey: ['settings', 'integrations'],
  queryFn: () => settingsApi.integrations(),
})

const SOURCE_LABEL: Record<string, string> = {
  cma: '中国天气网',
  'open-meteo': 'Open-Meteo',
  mock: '模拟数据',
}

const activeSource = computed(() =>
  weatherStatus.value && weatherStatus.value.ok ? weatherStatus.value.source : '',
)
const sourceLabel = computed(() => SOURCE_LABEL[activeSource.value] ?? activeSource.value)

onMounted(async () => {
  const settings = await settingsQuery.refetch()
  if (settings.data) {
    form.city = settings.data.city
    // 界面主题以本地当前生效的为准（后端可能是老版本的 light/dark）
    form.theme = appStore.theme
  }

  const integrations = await integrationsQuery.refetch()
  const list: Integration[] = integrations.data ?? []
  applyIntegration(list.find((item) => item.provider === 'minidocs'), kb.value)
  await checkWeather()
})

function applyIntegration(source: Integration | undefined, target: IntegrationForm): void {
  if (!source) {
    return
  }
  target.enabled = source.enabled
  target.apiHost = source.apiHost || target.apiHost
  target.maskedKey = source.maskedKey
  target.hasKey = source.hasKey
}

const saveSettings = useMutation({
  mutationFn: () => settingsApi.update({ city: form.city, theme: form.theme }),
  onSuccess: () => {
    appStore.setTheme(form.theme)
    ElMessage.success('设置已保存')
  },
})

async function saveIntegration(): Promise<void> {
  const result = await settingsApi.saveIntegration('minidocs', {
    apiKey: kb.value.apiKey || undefined,
    apiHost: kb.value.apiHost,
    enabled: kb.value.enabled,
  })
  kb.value.apiKey = ''
  kb.value.maskedKey = result.maskedKey
  kb.value.hasKey = result.hasKey
  ElMessage.success('已保存')
}

/** 页面加载时静默探测一次，不弹提示；命中 30 分钟缓存，不会产生额外外部请求 */
async function checkWeather(): Promise<TestResult> {
  const result = await settingsApi.testWeather(form.city)
  weatherStatus.value = result
  return result
}

async function testWeather(): Promise<void> {
  testing.value = true
  try {
    const result = await checkWeather()
    if (result.ok) {
      ElMessage.success(`${result.message}：${result.text} ${result.temp}℃`)
    } else {
      ElMessage.warning(result.message)
    }
  } finally {
    testing.value = false
  }
}

async function testMinidocs(): Promise<void> {
  if (kb.value.apiKey.trim()) {
    ElMessage.warning('新令牌需要先保存，再点测试连接')
    return
  }
  kbTesting.value = true
  try {
    const result = await kbApi.test()
    ElMessage.success(
      `连接成功：${result.displayName || result.username} · 组织 ${result.defaultOrg}`,
    )
  } finally {
    kbTesting.value = false
  }
}

const kbHint = computed(() =>
  kb.value.hasKey ? `已配置（${kb.value.maskedKey}），留空则不修改` : '粘贴 mdt_ 开头的访问令牌',
)
</script>

<template>
  <div class="settings-page">
    <header class="page-head">
      <h1>设置</h1>
      <p class="page-head__sub">偏好与第三方集成都只存在你自己的数据库里</p>
    </header>

    <section class="card">
      <h2>个人偏好</h2>

      <div class="field">
        <label>所在城市</label>
        <el-input v-model="form.city" placeholder="用于天气展示" style="max-width: 240px" />
      </div>

      <div class="field field--stack">
        <label>界面主题</label>
        <ThemePicker v-model="form.theme" :columns="3" />
      </div>

      <el-button type="primary" :loading="saveSettings.isPending.value" @click="saveSettings.mutate()">
        保存偏好
      </el-button>
    </section>

    <section class="card">
      <div class="card__head">
        <h2>天气数据源</h2>
        <span class="card__tag">免费 · 无需 API Key</span>
      </div>
      <p class="card__hint">
        默认读取中国天气网公开实况，不可用时自动切换 Open-Meteo（CC BY 4.0）；两者都失败才降级为模拟数据。
        城市在上方「个人偏好」设置，海外部署可用环境变量 <code>MINIDESK_WEATHER_PROVIDERS</code> 调整优先级。
      </p>

      <div class="weather-sources">
        <div class="weather-source" :class="{ 'is-active': activeSource === 'cma' }">
          <span class="weather-source__dot" aria-hidden="true" />
          <div class="weather-source__text">
            <b>中国天气网</b>
            <small>默认 · 国内直连，城市自动匹配</small>
          </div>
          <span v-if="activeSource === 'cma'" class="weather-source__flag">生效中</span>
        </div>
        <div class="weather-source" :class="{ 'is-active': activeSource === 'open-meteo' }">
          <span class="weather-source__dot" aria-hidden="true" />
          <div class="weather-source__text">
            <b>Open-Meteo</b>
            <small>备用 · 全球覆盖，公开免费</small>
          </div>
          <span v-if="activeSource === 'open-meteo'" class="weather-source__flag">生效中</span>
        </div>
      </div>

      <div
        v-if="weatherStatus"
        class="weather-result"
        :class="{ 'is-error': !weatherStatus.ok }"
        :title="sourceLabel ? `当前数据源：${sourceLabel}` : ''"
      >
        <span class="weather-result__state" :class="{ 'is-live': weatherStatus.ok }" aria-hidden="true" />
        <span>{{ weatherStatus.message }}</span>
        <b v-if="weatherStatus.temp !== undefined">{{ weatherStatus.text }} {{ weatherStatus.temp }}℃</b>
      </div>

      <div class="card__actions">
        <button type="button" class="action-btn action-btn--primary" :disabled="testing" @click="testWeather">
          <el-icon><Refresh /></el-icon>
          {{ testing ? '正在检测…' : '测试连接' }}
        </button>
        <span class="card__footnote">结果缓存 30 分钟，不影响首页加载速度</span>
      </div>
    </section>

    <section class="card">
      <div class="card__head">
        <h2>知识库（MiniDocs）</h2>
        <el-switch v-model="kb.enabled" />
      </div>
      <p class="card__hint">
        站点地址 + 访问令牌（mdt_ 开头），在知识库「账号 → 第三方应用授权」签发，令牌只显示一次。
        它代表你本人的身份：只读令牌只能浏览，读写令牌才能把笔记沉淀成文档。服务端只存密文。
        填站点根地址即可，后端自动补接口路径。
      </p>

      <div class="field">
        <label>站点地址</label>
        <el-input v-model="kb.apiHost" placeholder="https://docs.minims.cn" style="max-width: 320px" />
      </div>

      <div class="field">
        <label>访问令牌</label>
        <el-input
          v-model="kb.apiKey"
          type="password"
          show-password
          :placeholder="kbHint"
          style="max-width: 320px"
        />
      </div>

      <div class="card__actions">
        <el-button type="primary" @click="saveIntegration">保存</el-button>
        <el-button :loading="kbTesting" @click="testMinidocs">测试连接</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  width: 100%;
  padding-top: 8px;
}

.page-head {
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

.card {
  padding: 18px 20px;
  @include card($radius-xl);

  h2 {
    font-size: 14.5px;
    font-weight: 600;
  }

  code {
    padding: 1px 5px;
    border-radius: 4px;
    background: color-mix(in srgb, var(--brand-50) 70%, transparent);
    font-family: var(--font-mono, monospace);
    font-size: 10.5px;
  }
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.card__tag {
  padding: 3px 10px;
  border: 1px solid color-mix(in srgb, var(--brand-500) 24%, transparent);
  border-radius: 999px;
  background: color-mix(in srgb, var(--brand-50) 55%, transparent);
  color: var(--brand-600);
  font-size: 10.5px;
  font-weight: 600;
  white-space: nowrap;
}

.card__hint {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-tertiary);
  line-height: 1.7;
}

.card__actions {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 18px;
}

.card__footnote {
  font-size: 10.5px;
  color: var(--text-tertiary);
}

.weather-sources {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 16px;

  @include respond-below(sm) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.weather-source {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid var(--border-color);
  border-radius: 12px;
  background: color-mix(in srgb, var(--brand-50) 16%, transparent);
  transition: border-color 0.2s ease, background 0.2s ease, box-shadow 0.2s ease;

  &.is-active {
    border-color: color-mix(in srgb, var(--brand-500) 45%, transparent);
    background: color-mix(in srgb, var(--brand-50) 58%, transparent);
    box-shadow: 0 8px 20px color-mix(in srgb, var(--brand-500) 10%, transparent);

    .weather-source__dot {
      background: var(--brand-500);
      opacity: 1;
      box-shadow: 0 0 0 4px color-mix(in srgb, var(--brand-400) 20%, transparent);
    }
  }

  &__dot {
    flex: 0 0 8px;
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--text-tertiary);
    opacity: 0.55;
  }

  &__text {
    min-width: 0;

    b {
      display: block;
      font-size: 12.5px;
      font-weight: 600;
      color: var(--text-primary);
    }

    small {
      display: block;
      margin-top: 3px;
      font-size: 10.5px;
      line-height: 1.5;
      color: var(--text-tertiary);
    }
  }

  &__flag {
    margin-left: auto;
    flex: 0 0 auto;
    padding: 2px 8px;
    border-radius: 999px;
    background: color-mix(in srgb, var(--brand-500) 15%, transparent);
    color: var(--brand-600);
    font-size: 10px;
    font-weight: 600;
  }
}

.weather-result {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
  padding: 9px 12px;
  border: 1px dashed color-mix(in srgb, var(--brand-400) 34%, var(--border-color));
  border-radius: 10px;
  background: color-mix(in srgb, var(--brand-50) 40%, transparent);
  font-size: 11.5px;
  color: var(--text-secondary);

  &.is-error {
    border-color: color-mix(in srgb, #e6a23c 45%, var(--border-color));
  }

  b {
    margin-left: auto;
    color: var(--text-primary);
    font-family: var(--font-number);
  }

  &__state {
    flex: 0 0 6px;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    background: #e6a23c;

    &.is-live {
      background: var(--brand-500);
    }
  }
}

.action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px 15px;
  border: 1px solid transparent;
  border-radius: 10px;
  background: linear-gradient(135deg, var(--brand-500), var(--brand-600));
  box-shadow: 0 8px 18px color-mix(in srgb, var(--brand-500) 24%, transparent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  cursor: pointer;
  transition: filter 0.2s ease, transform 0.2s ease;

  &:hover {
    filter: brightness(1.06);
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.65;
    cursor: wait;
    transform: none;
    filter: none;
  }
}

.field {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 14px;

  label {
    width: 84px;
    flex: 0 0 84px;
    font-size: 12.5px;
    color: var(--text-secondary);
  }

  /* 主题卡片要占满一行，不能和 label 并排 */
  &--stack {
    flex-direction: column;
    align-items: stretch;
    gap: 10px;

    label {
      width: auto;
      flex: none;
    }
  }

  @include respond-below(sm) {
    flex-direction: column;
    align-items: flex-start;
    gap: 6px;
  }
}
</style>
