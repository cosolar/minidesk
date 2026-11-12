<script setup lang="ts">
import { computed } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { Link } from '@element-plus/icons-vue'
import { settingsApi, type Integration } from '@/features/settings/api'

const { data } = useQuery({
  queryKey: ['settings', 'integrations'],
  queryFn: () => settingsApi.integrations(),
  staleTime: 60_000,
})

const minidocs = computed<Integration | undefined>(() =>
  (data.value ?? []).find((item) => item.provider === 'minidocs'),
)

const siteUrl = computed(() => minidocs.value?.apiHost || 'https://docs.minims.cn')

function open(): void {
  window.open(siteUrl.value, '_blank', 'noopener')
}
</script>

<template>
  <div class="kb-page">
    <header class="page-head">
      <div>
        <h1>知识库</h1>
        <p class="page-head__sub">直接复用已上线的 minidocs，不重复造一套</p>
      </div>
      <el-button type="primary" :icon="Link" @click="open">打开知识库</el-button>
    </header>

    <section class="kb-card">
      <div class="kb-card__icon">📚</div>
      <div class="kb-card__body">
        <h2>{{ siteUrl.replace(/^https?:\/\//, '') }}</h2>
        <p>
          知识库运行在 Halo + MiniDocs 上，负责文档树、全文搜索与发布。
          MiniDesk 只做入口与「笔记沉淀」，不再维护第二份内容。
        </p>
        <div class="kb-card__status">
          <span :class="minidocs?.enabled && minidocs?.hasKey ? 'is-ok' : 'is-off'">
            {{ minidocs?.enabled && minidocs?.hasKey ? '已配置访问令牌' : '未配置访问令牌' }}
          </span>
          <RouterLink to="/settings" class="kb-card__link">去设置</RouterLink>
        </div>
      </div>
    </section>

    <section class="kb-note">
      <h3>后续会打通的能力</h3>
      <ul>
        <li>在笔记页一键「沉淀到知识库」，直接创建文档</li>
        <li>按知识库分类浏览，不在应用内重复造文档树</li>
        <li>站内搜索聚合（调用 Halo 搜索接口）</li>
      </ul>
      <p class="kb-note__hint">访问令牌只存服务端并加密，前端永远拿不到明文。</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.kb-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-top: 8px;
}

.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;

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

.kb-card {
  display: flex;
  gap: 18px;
  padding: 22px 24px;
  @include card($radius-xl);
}

.kb-card__icon {
  @include flex-center;
  width: 56px;
  height: 56px;
  flex: 0 0 56px;
  border-radius: 16px;
  background: var(--c-study-bg);
  font-size: 26px;
}

.kb-card__body {
  h2 {
    font-size: 16px;
    font-weight: 600;
  }

  p {
    margin-top: 8px;
    font-size: 12.5px;
    line-height: 1.7;
    color: var(--text-secondary);
    max-width: 620px;
  }
}

.kb-card__status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-top: 14px;
  font-size: 11.5px;

  .is-ok {
    color: var(--brand-600);
  }

  .is-off {
    color: var(--c-warn);
  }
}

.kb-card__link {
  color: var(--brand-600);
}

.kb-note {
  padding: 16px 20px;
  @include card($radius-xl);

  h3 {
    font-size: 13.5px;
    font-weight: 600;
  }

  ul {
    margin-top: 10px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    padding-left: 18px;
    list-style: disc;
    font-size: 12.5px;
    color: var(--text-secondary);
  }
}

.kb-note__hint {
  margin-top: 12px;
  font-size: 11px;
  color: var(--text-tertiary);
}
</style>
