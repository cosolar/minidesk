<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell, Brush, Expand, Monitor, Search } from '@element-plus/icons-vue'
import { useAppStore } from '@/core/stores/app'
import { useUserStore } from '@/core/stores/user'
import { fileUrl } from '@/core/utils/file'
import ThemePicker from '@/shared/components/ThemePicker.vue'

const emit = defineEmits<{ 'toggle-sidebar': [] }>()

const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()

const keyword = ref('')
const unread = computed(() => 2)

/** 搜索框只做入口：真正的聚合搜索在 /search，避免顶栏重复实现一套 */
function submitSearch(): void {
  const q = keyword.value.trim()
  router.push(q ? { path: '/search', query: { q } } : '/search')
}

/** Ctrl+K：统一跳到搜索页（那里输入框自动聚焦），行为比「当前页聚焦」更可预期 */
function focusSearch(): void {
  router.push('/search')
}

function onGlobalKeydown(event: KeyboardEvent): void {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    focusSearch()
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>

<template>
  <header class="topbar">
    <button
      v-if="appStore.isMobile || appStore.sidebarCollapsed"
      class="icon-btn topbar__menu"
      type="button"
      aria-label="展开菜单"
      @click="emit('toggle-sidebar')"
    >
      <el-icon><Expand /></el-icon>
    </button>

    <label class="search">
      <el-icon class="search__icon"><Search /></el-icon>
      <input
        v-model="keyword"
        class="search__input"
        placeholder="搜索文档、待办、功能…"
        @keyup.enter="submitSearch"
      />
      <kbd class="search__kbd">Ctrl K</kbd>
    </label>

    <div class="topbar__actions">
      <RouterLink
        v-if="userStore.isAdmin"
        class="icon-btn"
        to="/console"
        title="管理后台"
        aria-label="管理后台"
      >
        <el-icon><Monitor /></el-icon>
      </RouterLink>

      <el-popover
        placement="bottom-end"
        :width="300"
        trigger="click"
        :show-arrow="false"
        popper-class="theme-popover"
        :offset="10"
      >
        <template #reference>
          <button
            class="icon-btn theme-btn"
            type="button"
            :aria-label="`切换主题，当前${appStore.themeMeta.label}`"
            :title="`界面主题 · ${appStore.themeMeta.label}`"
          >
            <el-icon><Brush /></el-icon>
            <span class="theme-btn__dot" />
          </button>
        </template>

        <div class="theme-pop">
          <header class="theme-pop__head">
            <strong>界面主题</strong>
            <span>当前：{{ appStore.themeMeta.label }}</span>
          </header>
          <ThemePicker :columns="2" />
          <p class="theme-pop__tip">选择后立即生效，并记住到下次打开</p>
        </div>
      </el-popover>

      <RouterLink class="icon-btn" to="/reminders" aria-label="提醒中心" title="提醒中心">
        <el-icon><Bell /></el-icon>
        <span v-if="unread" class="badge">{{ unread }}</span>
      </RouterLink>

      <RouterLink class="avatar" to="/profile" :title="`${userStore.displayName} · 个人中心`">
        <img v-if="userStore.profile?.avatarUrl" :src="fileUrl(userStore.profile.avatarUrl)" alt="" />
        <span v-else>{{ userStore.initials }}</span>
      </RouterLink>
    </div>
  </header>
</template>

<style scoped lang="scss">
.topbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 58px;
  padding: 0 $content-padding;
  background: transparent;
}

.topbar__menu {
  display: none;

  @include respond-below(sm) {
    display: inline-flex;
  }
}

.search {
  display: flex;
  align-items: center;
  gap: 9px;
  width: 372px;
  max-width: 46vw;
  height: 40px;
  padding: 0 14px;
  border-radius: 999px;
  background: var(--surface-glass-strong);
  border: 1px solid var(--border-color);
  box-shadow: 0 4px 14px rgba(23, 74, 60, 0.04);
  transition: border-color $duration-fast $ease-out, box-shadow $duration-fast $ease-out;

  &:focus-within {
    border-color: var(--brand-200);
    box-shadow: 0 6px 18px rgba(53, 199, 155, 0.14);
  }
}

.search__icon {
  color: var(--text-tertiary);
  font-size: 15px;
}

.search__input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  color: var(--text-primary);
  font-family: inherit;
  font-size: 12.5px;

  &::placeholder {
    color: #a9b7b3;
  }
}

.search__kbd {
  padding: 2px 7px;
  border-radius: 7px;
  background: #f4f9f7;
  color: #a6b4b0;
  font-size: 9.5px;
  font-family: inherit;
  letter-spacing: 0.2px;
  white-space: nowrap;
}

.topbar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-left: auto;
}

.icon-btn {
  position: relative;
  @include flex-center;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  border: 1px solid var(--border-color);
  background: var(--surface-glass);
  color: var(--text-secondary);
  cursor: pointer;
  transition:
    color $duration-fast $ease-out,
    border-color $duration-fast $ease-out,
    transform $duration-fast $ease-out;

  &:hover {
    color: var(--brand-600);
    border-color: var(--brand-200);
    transform: translateY(-1px);
  }
}

/* 主题按钮：右下角一颗当前主色小圆点 */
.theme-btn__dot {
  position: absolute;
  right: 5px;
  bottom: 5px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand-500);
  box-shadow: 0 0 0 2px var(--bg-card);
}

.badge {
  position: absolute;
  top: 1px;
  right: 0;
  min-width: 7px;
  height: 7px;
  border-radius: 999px;
  background: var(--c-alert);
  box-shadow: 0 0 0 2px var(--bg-card);
  font-size: 0;
}

.avatar {
  @include flex-center;
  width: 34px;
  height: 34px;
  border-radius: 11px;
  overflow: hidden;
  background: linear-gradient(145deg, var(--brand-400), var(--brand-500));
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 6px 14px color-mix(in srgb, var(--brand-500) 30%, transparent);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

@media (max-width: 767px) {
  .topbar {
    height: 54px;
    padding: 0 14px;
  }

  .search {
    height: 36px;
    max-width: none;
    flex: 1;
  }

  .search__kbd {
    display: none;
  }
}

/* 弹层被 teleport 到 body，样式要放全局作用域 */
:global(.theme-popover.el-popover.el-popper) {
  padding: 14px;
  border-radius: 16px;
  border: 1px solid var(--border-color);
  background: var(--bg-card);
  box-shadow: var(--shadow-float);
}

.theme-pop {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.theme-pop__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;

  strong {
    font-size: 13px;
    font-weight: 700;
    color: var(--text-primary);
  }

  span {
    font-size: 11px;
    color: var(--text-tertiary);
  }
}

.theme-pop__tip {
  margin: 0;
  font-size: 10.5px;
  line-height: 1.6;
  color: var(--text-tertiary);
}
</style>
