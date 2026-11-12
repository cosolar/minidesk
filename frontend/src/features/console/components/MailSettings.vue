<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useQuery } from '@tanstack/vue-query'
import { ElMessage } from 'element-plus'
import { mailApi, type MailConfig } from '../mailApi'

const form = reactive<MailConfig>({
  host: '',
  port: 465,
  username: '',
  from: '',
  ssl: true,
  maskedPassword: '',
  configured: false,
  enabled: true,
})
const loaded = ref(false)
const saving = ref(false)
const testing = ref(false)
const testTo = ref('')

const { isLoading } = useQuery({
  queryKey: ['console', 'mail'],
  queryFn: async () => {
    const data = await mailApi.get()
    Object.assign(form, data)
    testTo.value = data.username
    loaded.value = true
    return data
  },
  staleTime: 0,
})

const status = computed(() => {
  if (!form.host || !form.username) {
    return { text: '未配置', type: 'info' as const }
  }
  return form.enabled
    ? { text: '已启用', type: 'success' as const }
    : { text: '已停用', type: 'warning' as const }
})

async function save(): Promise<void> {
  saving.value = true
  try {
    const data = await mailApi.save({ ...form })
    Object.assign(form, data)
    ElMessage.success('发件配置已保存')
  } finally {
    saving.value = false
  }
}

async function sendTest(): Promise<void> {
  if (!testTo.value.trim()) {
    ElMessage.warning('请填写接收测试邮件的地址')
    return
  }
  testing.value = true
  try {
    await mailApi.test(testTo.value.trim())
    ElMessage.success('测试邮件已发送，请查收')
  } finally {
    testing.value = false
  }
}
</script>

<template>
  <div class="mail">
    <el-skeleton v-if="isLoading || !loaded" :rows="6" animated />

    <template v-else>
      <div class="mail__status">
        <el-tag :type="status.type" effect="light" size="small">{{ status.text }}</el-tag>
        <span>未配置时，注册页不显示「邮箱注册」入口。</span>
      </div>

      <el-form :model="form" label-width="96px" class="mail__form">
        <el-form-item label="SMTP 主机">
          <el-input v-model="form.host" placeholder="smtp.qq.com / smtp.feishu.cn" />
        </el-form-item>
        <el-form-item label="端口">
          <el-input-number v-model="form.port" :min="1" :max="65535" controls-position="right" />
          <span class="mail__tip">465 走 SSL，587 走 STARTTLS</span>
        </el-form-item>
        <el-form-item label="账号">
          <el-input v-model="form.username" placeholder="通常是完整邮箱地址" />
        </el-form-item>
        <el-form-item label="密码 / 授权码">
          <el-input v-model="form.maskedPassword" placeholder="留空或保持 **** 表示不修改" show-password />
        </el-form-item>
        <el-form-item label="发件人">
          <el-input v-model="form.from" placeholder="不填则与账号相同" />
        </el-form-item>
        <el-form-item label="选项">
          <div class="mail__switches">
            <el-switch v-model="form.ssl" active-text="使用 SSL" />
            <el-switch v-model="form.enabled" active-text="启用邮箱注册" />
          </div>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="saving" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>

      <div class="mail__test">
        <h4>发送测试邮件</h4>
        <div class="mail__test-row">
          <el-input v-model="testTo" placeholder="接收测试邮件的地址" style="max-width: 280px" />
          <el-button :loading="testing" @click="sendTest">发送测试</el-button>
        </div>
        <p>保存后可点这里验证；失败会提示具体原因（认证失败、端口不通等）。</p>
      </div>
    </template>
  </div>
</template>

<style scoped lang="scss">
.mail {
  max-width: 640px;
}

.mail__status {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 10px 14px;
  border-radius: 10px;
  background: #f7fbf9;
  font-size: 12px;
  color: var(--text-secondary);
}

.mail__form {
  :deep(.el-form-item) {
    margin-bottom: 16px;
  }
}

.mail__tip {
  margin-left: 10px;
  font-size: 11px;
  color: var(--text-tertiary);
}

.mail__switches {
  display: flex;
  gap: 24px;
}

.mail__test {
  margin-top: 6px;
  padding-top: 18px;
  border-top: 1px dashed var(--border-color);

  h4 {
    font-size: 13px;
    font-weight: 600;
    color: #22383c;
  }

  p {
    margin-top: 8px;
    font-size: 11.5px;
    color: var(--text-tertiary);
  }
}

.mail__test-row {
  display: flex;
  gap: 10px;
  margin-top: 10px;
}
</style>
