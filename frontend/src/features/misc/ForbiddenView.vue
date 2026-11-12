<template>
  <section class="forbidden">
    <strong class="forbidden__code">403</strong>
    <h1>没有访问权限</h1>
    <p>当前账号不是管理员，无法进入该页面。</p>
    <RouterLink to="/" class="forbidden__back">返回工作台</RouterLink>
  </section>
</template>

<style scoped lang="scss">
.forbidden {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  gap: 10px;
  text-align: center;
  background: var(--bg-page);
}

.forbidden__code {
  font-size: 64px;
  font-weight: 800;
  line-height: 1;
  color: var(--brand-200);
  letter-spacing: 2px;
}

h1 {
  font-size: 18px;
  font-weight: 600;
}

p {
  font-size: 13px;
  color: var(--text-secondary);
}

.forbidden__back {
  margin-top: 10px;
  padding: 8px 18px;
  border-radius: 999px;
  background: var(--brand-500);
  color: #fff;
  font-size: 12.5px;
}
</style>
