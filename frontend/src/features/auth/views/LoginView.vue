<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Icon } from '@iconify/vue'
import { useUserStore } from '@/core/stores/user'
import { useSiteStore } from '@/core/stores/site'
import lakeImage from '@/assets/hero-lake.jpg'
import { authApi } from '../api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const siteStore = useSiteStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
/** 登录失败原因常驻展示，避免只靠会自动消失的轻提示 */
const errorMessage = ref('')
/** 演示模式只在开发环境暴露 */
const isDev = import.meta.env.DEV

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少 6 位', trigger: 'blur' },
  ],
}

async function onSubmit(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  errorMessage.value = ''
  try {
    const session = await authApi.login(form.username.trim(), form.password)
    userStore.applySession(session)
    ElMessage.success(`欢迎回来，${session.user.nickname}`)

    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : ''
    if (redirect) {
      await router.replace(redirect)
    } else {
      await router.replace(userStore.isAdmin ? '/console' : '/')
    }
  } catch (error) {
    // 轻提示会立刻消失，这里再常驻一条，方便看清失败原因
    errorMessage.value = (error as Error).message || '登录失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    loading.value = false
  }
}

/** 仅开发环境可用：注入本地会话，用于在没有后端时验证前端 */
function enterDemo(): void {
  userStore.applySession({
    tokenName: 'MiniDesk-Token',
    tokenValue: 'demo-session',
    expiresIn: 2_592_000,
    user: { id: '0', username: 'demo', nickname: '朋友', avatarUrl: null },
    roles: ['MEMBER'],
    permissions: ['work:*', 'study:*', 'life:*', 'shared:*'],
  })
  const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
  router.replace(redirect)
}
</script>

<template>
  <div class="login">
    <aside class="login__aside">
      <img class="login__scene" :src="lakeImage" alt="" aria-hidden="true" />
      <span class="login__tint" aria-hidden="true" />
      <span class="login__veil" aria-hidden="true" />

      <div class="login__brand">
        <img class="login__logo" :src="siteStore.wideLogo" :alt="siteStore.siteName" />
      </div>

      <div class="login__slogan">
        <h1>让工作更高效<br />让生活更简单</h1>
        <p>工作 · 学习 · 生活，一屏收敛</p>
      </div>
    </aside>

    <main class="login__main">
      <div class="login__card">
        <h2>欢迎回来</h2>
        <p class="login__sub">登录后继续你的一天</p>

        <el-alert
          v-if="errorMessage"
          class="login__alert"
          type="error"
          :closable="false"
          :title="errorMessage"
          show-icon
        />

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" autocomplete="username">
              <template #prefix>
                <el-icon><Icon icon="lucide:user-round" width="15" /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              size="large"
              show-password
              placeholder="请输入密码"
              autocomplete="current-password"
              @keyup.enter="onSubmit"
            >
              <template #prefix>
                <el-icon><Icon icon="lucide:lock" width="15" /></el-icon>
              </template>
            </el-input>
          </el-form-item>

          <el-button class="login__submit" type="primary" size="large" :loading="loading" @click="onSubmit">
            登录
          </el-button>
        </el-form>

        <div class="login__foot">
          <span>还没有账号？</span>
          <RouterLink class="login__link" to="/register">立即注册</RouterLink>
        </div>

        <template v-if="isDev">
          <el-divider><span class="login__divider">开发预览</span></el-divider>

          <el-button class="login__demo" size="large" plain @click="enterDemo">
            演示模式（无后端）
          </el-button>
        </template>
      </div>
    </main>
  </div>
</template>

<style scoped lang="scss">
.login {
  display: flex;
  min-height: 100vh;
  background: var(--bg-page);
}

.login__aside {
  position: relative;
  flex: 0 0 46%;
  max-width: 620px;
  overflow: hidden;
  isolation: isolate;
  padding: 36px 40px;
  display: flex;
  flex-direction: column;

  @include respond-below(md) {
    display: none;
  }
}

.login__scene {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  object-position: center 42%;
  z-index: 0;
  filter: var(--hero-photo-filter);
  opacity: var(--hero-photo-opacity);
}

/* 与首页同一套主题染色 */
.login__tint {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: var(--hero-tint);
  mix-blend-mode: var(--hero-tint-blend, normal);
  opacity: var(--hero-tint-opacity, 1);
}

/* 与首页同一套主题染色 */
.login__tint {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: var(--hero-tint);
  mix-blend-mode: var(--hero-tint-blend, normal);
  opacity: var(--hero-tint-opacity, 1);
}

/* 上浅下深：顶部压住 logo 的底色，底部让标语可读 */
.login__veil {
  position: absolute;
  inset: 0;
  z-index: 0;
  background: var(--login-veil);
}

.login__logo {
  height: 40px;
  /* logo 本体是白底 JPG，浅色页面上用 multiply 把白底融掉 */
  mix-blend-mode: multiply;
  width: auto;
  object-fit: contain;
  filter: drop-shadow(0 6px 16px rgba(17, 73, 60, 0.18));
}

.login__brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 10px;

  strong {
    display: block;
    font-size: 15px;
    font-weight: 600;
  }

  span {
    font-size: 9px;
    letter-spacing: 1.8px;
    color: var(--text-tertiary);
  }
}

.brand-mark {
  @include flex-center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  color: #fff;
  background: linear-gradient(145deg, $brand-400, $brand-600);
  box-shadow: 0 8px 18px rgba(53, 199, 155, 0.3);
}

.login__slogan {
  position: relative;
  z-index: 1;
  margin-top: auto;
  padding-bottom: 42px;

  h1 {
    font-size: 34px;
    font-weight: 700;
    line-height: 1.35;
    letter-spacing: 0.5px;
    color: #fff;
    text-shadow: 0 2px 20px rgba(6, 46, 38, 0.5);
  }

  p {
    margin-top: 10px;
    font-size: 13px;
    letter-spacing: 0.4px;
    color: rgba(255, 255, 255, 0.84);
    text-shadow: 0 1px 12px rgba(6, 46, 38, 0.45);
  }
}

.login__main {
  flex: 1;
  @include flex-center;
  padding: 32px 20px;
}

.login__card {
  width: 100%;
  max-width: 380px;
  padding: 34px 34px 28px;
  @include card($radius-2xl);

  h2 {
    font-size: 22px;
    font-weight: 700;
  }
}

.login__sub {
  margin: 6px 0 24px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.login__alert {
  margin-bottom: 16px;
}

.login__submit {
  width: 100%;
  margin-top: 4px;
  border-radius: 10px;
  font-weight: 500;
}

.login__foot {
  display: flex;
  justify-content: center;
  gap: 4px;
  margin-top: 16px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.login__link {
  color: var(--brand-600);
  font-weight: 500;
}

.login__divider {
  font-size: 11px;
  color: var(--text-tertiary);
}

.login__demo {
  width: 100%;
  border-radius: 10px;
  border-style: dashed;
}
</style>
