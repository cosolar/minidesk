<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Upload } from '@element-plus/icons-vue'
import { siteApi } from '@/core/api/site'
import { useSiteStore } from '@/core/stores/site'

const siteStore = useSiteStore()

const siteName = ref(siteStore.siteName)
const savingName = ref(false)
const uploading = ref<'logo' | 'logo-wide' | null>(null)
const wideInput = ref<HTMLInputElement>()
const markInput = ref<HTMLInputElement>()

const wideLogo = computed(() => siteStore.logoWideUrl || siteStore.wideLogo)
const markLogo = computed(() => siteStore.logoUrl || siteStore.markLogo)

async function saveName(): Promise<void> {
  if (!siteName.value.trim()) {
    ElMessage.warning('请输入站点名称')
    return
  }
  savingName.value = true
  try {
    const info = await siteApi.rename(siteName.value.trim())
    siteStore.apply(info)
    ElMessage.success('站点名称已更新')
  } finally {
    savingName.value = false
  }
}

async function onPick(kind: 'logo' | 'logo-wide', event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) {
    return
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('图片请控制在 2MB 以内')
    return
  }
  uploading.value = kind
  try {
    const info = await siteApi.uploadLogo(kind, file)
    siteStore.apply(info)
    ElMessage.success('已替换')
  } catch (error) {
    ElMessage.error((error as Error).message || '上传失败')
  } finally {
    uploading.value = null
  }
}

async function remove(kind: 'logo' | 'logo-wide'): Promise<void> {
  await siteApi.removeLogo(kind)
  await siteStore.load()
  ElMessage.success('已恢复默认')
}
</script>

<template>
  <div class="brand">
    <section class="brand__block">
      <h3>站点名称</h3>
      <p>显示在登录页、浏览器标签与邮件标题里。</p>
      <div class="brand__row">
        <el-input v-model="siteName" maxlength="32" style="max-width: 280px" />
        <el-button type="primary" :loading="savingName" @click="saveName">保存</el-button>
      </div>
    </section>

    <section class="brand__block">
      <h3>横版 Logo</h3>
      <p>用于侧栏展开态与登录页，建议 3:1，高度 26px 左右最清晰。</p>
      <div class="brand__preview brand__preview--wide">
        <img :src="wideLogo" alt="横版 logo" />
      </div>
      <div class="brand__row">
        <el-button :icon="Upload" :loading="uploading === 'logo-wide'" @click="wideInput?.click()">
          上传横版 Logo
        </el-button>
        <el-button v-if="siteStore.logoWideUrl" text @click="remove('logo-wide')">恢复默认</el-button>
        <input ref="wideInput" type="file" accept=".png,.jpg,.jpeg,.webp,.svg" hidden @change="onPick('logo-wide', $event)" />
      </div>
    </section>

    <section class="brand__block">
      <h3>方形 Logo</h3>
      <p>用于侧栏收起态与 favicon，建议正方形。</p>
      <div class="brand__preview brand__preview--mark">
        <img :src="markLogo" alt="方形 logo" />
      </div>
      <div class="brand__row">
        <el-button :icon="Upload" :loading="uploading === 'logo'" @click="markInput?.click()">
          上传方形 Logo
        </el-button>
        <el-button v-if="siteStore.logoUrl" text @click="remove('logo')">恢复默认</el-button>
        <input ref="markInput" type="file" accept=".png,.jpg,.jpeg,.webp,.svg" hidden @change="onPick('logo', $event)" />
      </div>
    </section>

    <p class="brand__hint">
      <el-icon><Picture /></el-icon>
      未上传时使用内置的「极简工作台」标识；替换后立即生效，无需重启。
    </p>
  </div>
</template>

<style scoped lang="scss">
.brand {
  display: flex;
  flex-direction: column;
  gap: 22px;
  max-width: 720px;
}

.brand__block {
  padding-bottom: 20px;
  border-bottom: 1px dashed var(--border-color);

  h3 {
    font-size: 13.5px;
    font-weight: 700;
    color: #22383c;
  }

  p {
    margin-top: 4px;
    font-size: 12px;
    color: var(--text-tertiary);
  }
}

.brand__row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 12px;
}

.brand__preview {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-top: 12px;
  border-radius: 12px;
  border: 1px dashed var(--border-strong);
  background: #fbfefd;

  img {
    max-width: 100%;
    object-fit: contain;
  }

  &--wide {
    height: 88px;
    padding: 10px 18px;

    img {
      height: 100%;
    }
  }

  &--mark {
    width: 96px;
    height: 96px;
    padding: 8px;

    img {
      max-height: 100%;
      border-radius: 10px;
    }
  }
}

.brand__hint {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11.5px;
  color: var(--text-tertiary);
}
</style>
