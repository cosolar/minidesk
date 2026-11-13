<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Icon } from '@iconify/vue'
import { useUserStore } from '@/core/stores/user'
import { authApi, type RegisterConfig } from '../api'

const router = useRouter()
const userStore = useUserStore()

const formRef = ref<FormInstance>()
const loading = ref(false)
const submitting = ref(false)
const config = ref<RegisterConfig | null>(null)
/** 提交失败原因常驻展示，避免只靠会自动消失的轻提示 */
const errorMessage = ref('')

/** 用户名注册 / 邮箱注册，邮箱模式需要后台先配好发件邮箱 */
const mode = ref<'username' | 'email'>('username')
const emailRef = ref<FormInstance>()
const sending = ref(false)
const countdown = ref(0)
const emailForm = reactive({
  email: '',
  code: '',
  nickname: '',
  password: '',
  confirmPassword: '',
})

const emailRules = computed<FormRules>(() => ({
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== emailForm.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}))

async function sendCode(): Promise<void> {
  if (!emailForm.email.trim()) {
    ElMessage.warning('请先填写邮箱')
    return
  }
  sending.value = true
  try {
    await authApi.sendEmailCode(emailForm.email.trim())
    ElMessage.success('验证码已发送，请查收邮箱')
    countdown.value = 60
    const timer = setInterval(() => {
      countdown.value -= 1
      if (countdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch {
    // 拦截器已提示（未配置发件邮箱会明确告知）
  } finally {
    sending.value = false
  }
}

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  claimToken: '',
})

/** 首个用户会成为管理员，这里给出明确提示，不隐藏 */
const isFirstUser = computed(() => config.value?.isFirstUser === true)

const rules = computed<FormRules>(() => ({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z0-9_]{3,32}$/,
      message: '3-32 位字母、数字或下划线',
      trigger: 'blur',
    },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    {
      min: config.value?.passwordPolicy.minLength ?? 8,
      message: `密码至少 ${config.value?.passwordPolicy.minLength ?? 8} 位`,
      trigger: 'blur',
    },
    ...(config.value?.passwordPolicy.requireLetter
      ? [{ pattern: /[A-Za-z]/, message: '密码需包含字母', trigger: 'blur' }]
      : []),
    ...(config.value?.passwordPolicy.requireDigit
      ? [{ pattern: /\d/, message: '密码需包含数字', trigger: 'blur' }]
      : []),
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.password) {
          callback(new Error('两次输入的密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}))

onMounted(async () => {
  loading.value = true
  try {
    config.value = await authApi.registerConfig()
  } catch {
    config.value = null
  } finally {
    loading.value = false
  }
})

async function onSubmit(): Promise<void> {
  const target = mode.value === 'email' ? emailRef.value : formRef.value
  const valid = await target?.validate().catch(() => false)
  if (!valid) {
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const session =
      mode.value === 'email'
        ? await authApi.registerByEmail({
            email: emailForm.email.trim(),
            code: emailForm.code.trim(),
            nickname: emailForm.nickname.trim() || undefined,
            password: emailForm.password,
            confirmPassword: emailForm.confirmPassword,
          })
        : await authApi.register({
            username: form.username.trim(),
            nickname: form.nickname.trim() || undefined,
            password: form.password,
            confirmPassword: form.confirmPassword,
            claimToken: form.claimToken || null,
          })
    userStore.applySession(session)
    ElMessage.success(session.isFirstUser ? '管理员账号创建成功' : '注册成功')
    await router.replace(userStore.isAdmin ? '/console' : '/')
  } catch (error) {
    // 轻提示会立刻消失，这里再常驻一条，方便看清失败原因
    errorMessage.value = (error as Error).message || '注册失败，请稍后重试'
    ElMessage.error(errorMessage.value)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="register">
    <div class="register__card">
      <RouterLink to="/login" class="register__back">
        <el-icon><Icon icon="lucide:arrow-left" width="14" /></el-icon>
        返回登录
      </RouterLink>

      <h1>创建账号</h1>
      <p class="register__sub">数据存在你自己的设备上，随时可导出备份。</p>

      <el-alert
        v-if="errorMessage"
        class="register__alert"
        type="error"
        :closable="false"
        :title="errorMessage"
        show-icon
      />

      <el-alert
        v-if="config && !config.allowRegister"
        class="register__alert"
        type="warning"
        :closable="false"
        title="暂未开放注册"
        description="管理员已关闭自助注册，请联系管理员创建账号。"
        show-icon
      />

      <el-alert
        v-else-if="isFirstUser"
        class="register__alert"
        type="success"
        :closable="false"
        title="你是第一个用户"
        description="注册后将自动获得管理员权限，可在后台管理其他账号。"
        show-icon
      />

      <div v-if="config?.emailEnabled" class="register__modes">
        <button
          type="button"
          class="register__mode"
          :class="{ 'is-active': mode === 'username' }"
          @click="mode = 'username'"
        >
          用户名注册
        </button>
        <button
          type="button"
          class="register__mode"
          :class="{ 'is-active': mode === 'email' }"
          @click="mode = 'email'"
        >
          邮箱注册
        </button>
      </div>

      <el-form
        v-if="(!config || config.allowRegister) && mode === 'username'"
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        :disabled="loading"
        :validate-on-rule-change="false"
        @submit.prevent
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" size="large" placeholder="字母、数字或下划线" />
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" size="large" placeholder="不填则默认为「朋友」" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            size="large"
            show-password
            placeholder="至少 8 位"
          />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            size="large"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-form-item v-if="config?.needClaimToken" label="初始化口令" prop="claimToken">
          <el-input v-model="form.claimToken" size="large" placeholder="由部署者在启动参数中设置" />
        </el-form-item>

        <el-button
          class="register__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="onSubmit"
        >
          注册并登录
        </el-button>
      </el-form>

      <el-form
        v-else-if="config?.allowRegister && mode === 'email'"
        ref="emailRef"
        :model="emailForm"
        :rules="emailRules"
        label-position="top"
        :disabled="loading"
        :validate-on-rule-change="false"
        @submit.prevent
      >
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="emailForm.email" size="large" placeholder="用于接收验证码与登录" />
        </el-form-item>

        <el-form-item label="验证码" prop="code">
          <div class="register__code">
            <el-input v-model="emailForm.code" size="large" placeholder="6 位验证码" maxlength="6" />
            <el-button
              size="large"
              :loading="sending"
              :disabled="countdown > 0"
              @click="sendCode"
            >
              {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>

        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="emailForm.nickname" size="large" placeholder="不填则用邮箱前缀" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="emailForm.password" type="password" size="large" show-password placeholder="至少 8 位" />
        </el-form-item>

        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="emailForm.confirmPassword"
            type="password"
            size="large"
            show-password
            @keyup.enter="onSubmit"
          />
        </el-form-item>

        <el-button
          class="register__submit"
          type="primary"
          size="large"
          :loading="submitting"
          @click="onSubmit"
        >
          注册并登录
        </el-button>
      </el-form>

    </div>
  </div>
</template>

<style scoped lang="scss">
.register {
  @include flex-center;
  min-height: 100vh;
  padding: 32px 20px;
  background:
    radial-gradient(900px 420px at 12% 8%, var(--brand-100), transparent 60%),
    radial-gradient(700px 380px at 88% 92%, var(--brand-50), transparent 55%), var(--bg-page);
}

.register__card {
  width: 100%;
  max-width: 420px;
  padding: 32px 34px 30px;
  @include card($radius-2xl);

  h1 {
    font-size: 22px;
    font-weight: 700;
  }
}

.register__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: 14px;
  font-size: 12px;
  color: var(--text-tertiary);

  &:hover {
    color: var(--brand-600);
  }
}

.register__sub {
  margin: 6px 0 18px;
  font-size: 12.5px;
  color: var(--text-secondary);
}

.register__alert {
  margin-bottom: 16px;
}

.register__modes {
  display: flex;
  gap: 6px;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: 12px;
  background: var(--bg-subtle);
}

.register__mode {
  flex: 1;
  height: 34px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(23, 74, 60, 0.08);
  }
}

.register__code {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-input) {
    flex: 1;
  }
}

.register__modes {
  display: flex;
  gap: 6px;
  margin-bottom: 18px;
  padding: 4px;
  border-radius: 12px;
  background: var(--bg-subtle);
}

.register__mode {
  flex: 1;
  height: 34px;
  border: none;
  border-radius: 9px;
  background: transparent;
  color: var(--text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background $duration-fast $ease-out, color $duration-fast $ease-out;

  &.is-active {
    background: var(--bg-card);
    color: var(--brand-700);
    font-weight: 600;
    box-shadow: 0 2px 8px rgba(23, 74, 60, 0.08);
  }
}

.register__code {
  display: flex;
  gap: 8px;
  width: 100%;

  :deep(.el-input) {
    flex: 1;
  }
}

.register__submit {
  width: 100%;
  margin-top: 6px;
  border-radius: 10px;
}
</style>
