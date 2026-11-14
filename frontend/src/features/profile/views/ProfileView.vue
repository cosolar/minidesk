<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Camera, Key, Lock, Message, User } from '@element-plus/icons-vue'
import { useUserStore } from '@/core/stores/user'
import { fileUrl } from '@/core/utils/file'
import { profileApi, type PasswordPayload, type ProfilePayload } from '../api'

const userStore = useUserStore()
const queryClient = useQueryClient()

const { data: profile, isLoading } = useQuery({
  queryKey: ['profile'],
  queryFn: () => profileApi.get(),
  staleTime: 30_000,
})

const avatarUrl = computed(() => fileUrl(profile.value?.avatarUrl ?? undefined))
const initials = computed(() =>
  (profile.value?.nickname || profile.value?.username || '朋').slice(0, 1).toUpperCase(),
)
const roleText = computed(() => (userStore.isAdmin ? '管理员' : '成员'))

const form = reactive({
  nickname: '',
  email: '',
})

const formRef = ref<FormInstance>()
const pwdRef = ref<FormInstance>()
const passwords = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const uploading = ref(false)
const fileInput = ref<HTMLInputElement>()

// 资料加载后回填一次，之后不再覆盖用户正在输入的内容
watch(
  profile,
  (value) => {
    if (value) {
      form.nickname = value.nickname ?? ''
      form.email = value.email ?? ''
    }
  },
  { once: true },
)

const rules: FormRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 8, message: '密码至少 8 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value !== passwords.newPassword) {
          callback(new Error('两次输入的新密码不一致'))
          return
        }
        callback()
      },
      trigger: 'blur',
    },
  ],
}

const saveMutation = useMutation({
  mutationFn: (payload: ProfilePayload) => profileApi.update(payload),
  onSuccess: (data) => {
    userStore.applyProfile(data)
    queryClient.invalidateQueries({ queryKey: ['profile'] })
    ElMessage.success('资料已更新')
  },
})

const pwdMutation = useMutation({
  mutationFn: (payload: PasswordPayload) => profileApi.changePassword(payload),
  onSuccess: () => {
    passwords.oldPassword = ''
    passwords.newPassword = ''
    passwords.confirmPassword = ''
    pwdRef.value?.clearValidate()
    ElMessage.success('密码已修改，下次登录请使用新密码')
  },
})

async function saveProfile(): Promise<void> {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  saveMutation.mutate({ nickname: form.nickname, email: form.email || null })
}

async function savePassword(): Promise<void> {
  const valid = await pwdRef.value?.validate().catch(() => false)
  if (!valid) {
    return
  }
  pwdMutation.mutate({ ...passwords })
}

function pickAvatar(): void {
  fileInput.value?.click()
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像请控制在 2MB 以内')
    return
  }
  uploading.value = true
  try {
    const data = await profileApi.uploadAvatar(file)
    userStore.applyProfile(data)
    queryClient.invalidateQueries({ queryKey: ['profile'] })
    ElMessage.success('头像已更新')
  } catch (error) {
    ElMessage.error((error as Error).message || '头像上传失败')
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <section class="profile-page">
    <header class="page-head">
      <div>
        <h1>个人中心</h1>
        <p class="page-head__sub">头像、昵称与密码都在这里管理</p>
      </div>
    </header>

    <div class="profile-grid">
      <aside class="profile-card">
        <el-skeleton v-if="isLoading" :rows="4" animated />
        <template v-else>
          <button class="profile-avatar" type="button" :disabled="uploading" @click="pickAvatar">
            <img v-if="avatarUrl" :src="avatarUrl" alt="头像" />
            <span v-else>{{ initials }}</span>
            <span class="profile-avatar__mask">
              <el-icon><Camera /></el-icon>
              {{ uploading ? '上传中' : '更换' }}
            </span>
          </button>
          <input
            ref="fileInput"
            type="file"
            accept=".jpg,.jpeg,.png,.webp"
            class="profile-avatar__input"
            @change="onAvatarChange"
          />

          <h2 class="profile-name">{{ profile?.nickname || profile?.username }}</h2>
          <p class="profile-account">@{{ profile?.username }}</p>
          <el-tag size="small" effect="plain" class="profile-role">{{ roleText }}</el-tag>

          <dl class="profile-meta">
            <div>
              <dt><el-icon><User /></el-icon>昵称</dt>
              <dd>{{ profile?.nickname || '未设置' }}</dd>
            </div>
            <div>
              <dt><el-icon><Message /></el-icon>邮箱</dt>
              <dd>{{ profile?.email || '未绑定' }}</dd>
            </div>
          </dl>
        </template>
      </aside>

      <div class="profile-panels">
        <section class="panel">
          <header class="panel__head">
            <span class="panel__icon"><el-icon><User /></el-icon></span>
            <div>
              <h3>基本资料</h3>
              <p>昵称会显示在首页问候语与留言中</p>
            </div>
          </header>

          <el-form ref="formRef" :model="form" :rules="rules" label-width="72px">
            <el-form-item label="昵称" prop="nickname">
              <el-input v-model="form.nickname" maxlength="64" placeholder="怎么称呼你" />
            </el-form-item>
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="form.email" maxlength="128" placeholder="用于登录与找回（可选）" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saveMutation.isPending.value" @click="saveProfile">
                保存资料
              </el-button>
            </el-form-item>
          </el-form>
        </section>

        <section class="panel">
          <header class="panel__head">
            <span class="panel__icon is-warn"><el-icon><Lock /></el-icon></span>
            <div>
              <h3>修改密码</h3>
              <p>修改后其他设备需要重新登录</p>
            </div>
          </header>

          <el-form ref="pwdRef" :model="passwords" :rules="pwdRules" label-width="96px">
            <el-form-item label="原密码" prop="oldPassword">
              <el-input v-model="passwords.oldPassword" type="password" show-password autocomplete="current-password" />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input v-model="passwords.newPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input v-model="passwords.confirmPassword" type="password" show-password autocomplete="new-password" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Key" :loading="pwdMutation.isPending.value" @click="savePassword">
                更新密码
              </el-button>
            </el-form-item>
          </el-form>
        </section>
      </div>
    </div>
  </section>
</template>

<style scoped lang="scss">
.profile-page {
  display: flex;
  flex-direction: column;
  gap: 18px;
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

.profile-grid {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.profile-card {
  @include card($radius-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 26px 20px 22px;
  text-align: center;
}

.profile-avatar {
  position: relative;
  width: 92px;
  height: 92px;
  border: none;
  border-radius: 28px;
  overflow: hidden;
  cursor: pointer;
  background: linear-gradient(145deg, var(--brand-400), var(--brand-500));
  color: #fff;
  font-size: 32px;
  font-weight: 600;
  padding: 0;

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.profile-avatar__mask {
  position: absolute;
  inset: auto 0 0 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 3px;
  height: 28px;
  background: rgba(16, 42, 36, 0.55);
  color: #fff;
  font-size: 11px;
  opacity: 0;
  transition: opacity $duration-fast $ease-out;
}

.profile-avatar:hover .profile-avatar__mask {
  opacity: 1;
}

.profile-avatar__input {
  display: none;
}

.profile-name {
  margin-top: 14px;
  font-size: 17px;
  font-weight: 700;
  color: #22383c;
}

.profile-account {
  margin-top: 2px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.profile-role {
  margin-top: 8px;
}

.profile-meta {
  width: 100%;
  margin-top: 18px;
  padding-top: 14px;
  border-top: 1px dashed var(--border-color);
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;

  div {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    font-size: 12px;
  }

  dt {
    display: inline-flex;
    align-items: center;
    gap: 5px;
    color: var(--text-tertiary);
  }

  dd {
    margin: 0;
    max-width: 150px;
    color: var(--text-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.profile-panels {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.panel {
  @include card($radius-lg);
  padding: 20px 22px 8px;
}

.panel__head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;

  h3 {
    font-size: 14px;
    font-weight: 700;
    color: #24393d;
  }

  p {
    margin-top: 2px;
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.panel__icon {
  @include flex-center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  background: var(--brand-50);
  color: var(--brand-600);
  font-size: 15px;

  &.is-warn {
    background: var(--c-life-bg);
    color: #ff9f43;
  }
}

@media (max-width: 991px) {
  .profile-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
