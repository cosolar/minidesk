<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Lock } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import BrandLogo from '@/shared/components/BrandLogo.vue'
import MarkdownPreview from '@/shared/components/MarkdownPreview.vue'
import { noteShareApi, type PublicNote } from '@/features/note/api'

type ViewStatus = 'loading' | 'ok' | 'password_required' | 'wrong_password' | 'expired' | 'not_found'

const route = useRoute()

const status = ref<ViewStatus>('loading')
const note = ref<PublicNote | null>(null)
const password = ref('')
const submitting = ref(false)

const token = computed(() =>
  typeof route.params.token === 'string' ? route.params.token : String(route.params.token ?? ''),
)

const updatedText = computed(() => {
  if (!note.value?.updatedAt) {
    return ''
  }
  return dayjs(note.value.updatedAt).format('YYYY-MM-DD HH:mm')
})

function apply(next: PublicNote): void {
  status.value = next.status
  note.value = next.status === 'ok' ? next : null
  if (next.status === 'ok' && next.title) {
    document.title = `${next.title} · 分享的笔记`
  }
}

async function load(): Promise<void> {
  if (!token.value) {
    status.value = 'not_found'
    return
  }
  status.value = 'loading'
  note.value = null
  password.value = ''
  try {
    apply(await noteShareApi.view(token.value))
  } catch {
    status.value = 'not_found'
  }
}

async function submitPassword(): Promise<void> {
  if (!password.value || submitting.value) {
    return
  }
  submitting.value = true
  try {
    apply(await noteShareApi.unlock(token.value, password.value))
  } catch {
    status.value = 'wrong_password'
  } finally {
    submitting.value = false
  }
}

onMounted(load)
watch(token, load)
</script>

<template>
  <div class="share-page">
    <header class="share-top">
      <BrandLogo mode="wide" />
      <span class="share-top__tag">只读分享</span>
    </header>

    <main class="share-main">
      <div v-if="status === 'loading'" class="share-card">
        <el-skeleton :rows="8" animated />
      </div>

      <div v-else-if="status === 'password_required' || status === 'wrong_password'" class="share-card share-lock">
        <span class="share-lock__icon">
          <el-icon><Lock /></el-icon>
        </span>
        <h1>这篇笔记需要访问密码</h1>
        <p class="share-lock__hint">输入作者提供的密码后即可阅读。</p>
        <el-input
          v-model="password"
          type="password"
          size="large"
          show-password
          placeholder="访问密码"
          class="share-lock__input"
          @keyup.enter="submitPassword"
        />
        <p v-if="status === 'wrong_password'" class="share-lock__error">密码不正确，请重试</p>
        <el-button type="primary" size="large" :loading="submitting" @click="submitPassword">
          解锁阅读
        </el-button>
      </div>

      <div v-else-if="status === 'expired'" class="share-card share-empty">
        <h1>链接已过期</h1>
        <p>这篇笔记的分享有效期已结束，请联系作者重新分享。</p>
      </div>

      <div v-else-if="status === 'not_found'" class="share-card share-empty">
        <h1>链接不存在或已关闭</h1>
        <p>分享可能已被作者关闭，或链接地址有误。</p>
      </div>

      <article v-else-if="note" class="share-card share-article">
        <h1 class="share-article__title">{{ note.title || '无标题' }}</h1>
        <p v-if="updatedText" class="share-article__meta">更新于 {{ updatedText }}</p>
        <MarkdownPreview v-if="note.content?.trim()" :source="note.content" />
        <p v-else class="share-article__empty">这篇笔记还没有内容</p>
      </article>
    </main>

    <footer class="share-foot">由 MiniDesk 分享 · 只读页面</footer>
  </div>
</template>

<style scoped lang="scss">
.share-page {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  padding: 22px 20px 30px;
  background: var(--bg-page);
}

.share-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  max-width: 860px;
  margin: 0 auto 18px;
}

.share-top__tag {
  padding: 3px 9px;
  border-radius: 999px;
  background: var(--bg-subtle);
  color: var(--text-tertiary);
  font-size: 11px;
}

.share-main {
  width: 100%;
  max-width: 860px;
  margin: 0 auto;
  flex: 1;
}

.share-card {
  padding: 40px 46px 56px;
  @include card($radius-xl);

  @include respond-below(md) {
    padding: 26px 18px 40px;
  }
}

.share-lock {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  max-width: 420px;
  margin: 40px auto 0;
  padding: 40px 32px 44px;
  text-align: center;

  h1 {
    font-family: var(--font-display);
    font-size: 18px;
    font-weight: 650;
    color: var(--text-primary);
  }

  &__icon {
    @include flex-center;
    width: 46px;
    height: 46px;
    border-radius: 14px;
    background: var(--brand-50);
    color: var(--brand-600);
    font-size: 20px;
  }

  &__hint {
    margin-bottom: 4px;
    font-size: 12.5px;
    color: var(--text-tertiary);
  }

  &__input {
    width: 100%;
  }

  &__error {
    font-size: 12px;
    color: $c-alert;
  }
}

.share-empty {
  max-width: 460px;
  margin: 48px auto 0;
  text-align: center;

  h1 {
    margin-bottom: 8px;
    font-family: var(--font-display);
    font-size: 18px;
    font-weight: 650;
    color: var(--text-primary);
  }

  p {
    font-size: 12.5px;
    line-height: 1.7;
    color: var(--text-tertiary);
  }
}

.share-article__title {
  font-family: var(--font-display);
  font-size: 27px;
  font-weight: 700;
  line-height: 1.35;
  letter-spacing: -0.01em;
  color: var(--text-primary);
}

.share-article__meta {
  margin: 10px 0 24px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}

.share-article__empty {
  padding: 32px 0;
  font-size: 12.5px;
  color: var(--text-tertiary);
}

.share-foot {
  margin-top: 22px;
  text-align: center;
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
