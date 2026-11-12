<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useMutation, useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { useAppStore } from '@/core/stores/app'
import type { ThemeId } from '@/core/theme/catalog'
import ThemePicker from '@/shared/components/ThemePicker.vue'
import { settingsApi, type Integration } from '../api'

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

const weather = ref<IntegrationForm>({
  enabled: false,
  apiHost: 'https://devapi.qweather.com',
  apiKey: '',
  maskedKey: '',
  hasKey: false,
})

const kb = ref<IntegrationForm>({
  enabled: false,
  apiHost: 'https://docs.minims.cn',
  apiKey: '',
  maskedKey: '',
  hasKey: false,
})

const testing = ref(false)
const settingsQuery = useQuery({
  queryKey: ['settings'],
  queryFn: () => settingsApi.get(),
})

const integrationsQuery = useQuery({
  queryKey: ['settings', 'integrations'],
  queryFn: () => settingsApi.integrations(),
})

onMounted(async () => {
  const settings = await settingsQuery.refetch()
  if (settings.data) {
    form.city = settings.data.city
    // 界面主题以本地当前生效的为准（后端可能是老版本的 light/dark）
    form.theme = appStore.theme
  }

  const integrations = await integrationsQuery.refetch()
  const list: Integration[] = integrations.data ?? []
  applyIntegration(list.find((item) => item.provider === 'qweather'), weather.value)
  applyIntegration(list.find((item) => item.provider === 'minidocs'), kb.value)
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

async function saveIntegration(provider: 'qweather' | 'minidocs', target: IntegrationForm): Promise<void> {
  const result = await settingsApi.saveIntegration(provider, {
    apiKey: target.apiKey || undefined,
    apiHost: target.apiHost,
    enabled: target.enabled,
  })
  target.apiKey = ''
  target.maskedKey = result.maskedKey
  target.hasKey = result.hasKey
  ElMessage.success('已保存')
}

async function testWeather(): Promise<void> {
  testing.value = true
  try {
    const result = await settingsApi.testWeather(form.city)
    if (result.ok) {
      ElMessage.success(`${result.message}：${result.text} ${result.temp}℃`)
    } else {
      ElMessage.warning(result.message)
    }
  } finally {
    testing.value = false
  }
}

const weatherHint = computed(() =>
  weather.value.hasKey ? `已配置（${weather.value.maskedKey}），留空则不修改` : '未配置，首页显示模拟天气',
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
        <h2>和风天气</h2>
        <el-switch v-model="weather.enabled" />
      </div>
      <p class="card__hint">配置后首页显示真实天气；Key 加密存储，前端只回显后四位。</p>

      <div class="field">
        <label>API Host</label>
        <el-input v-model="weather.apiHost" placeholder="https://devapi.qweather.com" style="max-width: 320px" />
      </div>

      <div class="field">
        <label>API Key</label>
        <el-input
          v-model="weather.apiKey"
          type="password"
          show-password
          :placeholder="weatherHint"
          style="max-width: 320px"
        />
      </div>

      <div class="card__actions">
        <el-button type="primary" @click="saveIntegration('qweather', weather)">保存</el-button>
        <el-button :loading="testing" @click="testWeather">测试连接</el-button>
      </div>
    </section>

    <section class="card">
      <div class="card__head">
        <h2>知识库（minidocs）</h2>
        <el-switch v-model="kb.enabled" />
      </div>
      <p class="card__hint">
        Halo 站点地址 + 个人访问令牌（PAT），用于在学习板块浏览与沉淀文档。令牌仅存服务端。
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
          :placeholder="kb.hasKey ? `已配置（${kb.maskedKey}），留空则不修改` : '粘贴 Halo PAT'"
          style="max-width: 320px"
        />
      </div>

      <div class="card__actions">
        <el-button type="primary" @click="saveIntegration('minidocs', kb)">保存</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped lang="scss">
.settings-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
  max-width: 880px;
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
}

.card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card__hint {
  margin-top: 6px;
  font-size: 11.5px;
  color: var(--text-tertiary);
  line-height: 1.6;
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

.card__actions {
  display: flex;
  gap: 8px;
  margin-top: 18px;
}
</style>
