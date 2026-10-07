<script setup lang="ts">
import { normalizeBackendMessage } from '@/service/request/shared';
import { $t } from '@/locales';

defineOptions({
  name: 'Register'
});

const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  username: string;
  password: string;
  confirmPassword: string;
}

const model: FormModel = reactive({
  username: '',
  password: '',
  confirmPassword: ''
});

const rules = computed<Record<keyof FormModel, App.Global.FormRule[]>>(() => {
  const { formRules, createConfirmPwdRule } = useFormRules();

  return {
    username: formRules.userName,
    password: formRules.pwd,
    confirmPassword: createConfirmPwdRule(model.password)
  };
});

const loading = ref(false);

async function handleSubmit() {
  await validate();
  loading.value = true;
  const { error } = await fetchRegister(model.username, model.password);

  if (!error) {
    window.$message?.success('注册成功，请登录');
    toggleLoginModule('pwd-login');
  } else {
    const rawMessage = String(error?.response?.data?.message || error?.message || '注册失败，请稍后重试');
    window.$message?.error(normalizeBackendMessage(rawMessage));
  }

  loading.value = false;
}
</script>

<template>
  <div class="register-layout">
    <aside class="contact-card" aria-label="作者联系方式">
      <h3 class="contact-title">作者联系方式</h3>

      <a
        class="qr-frame"
        href="/images/wechat-austerity.jpg"
        target="_blank"
        rel="noopener noreferrer"
        aria-label="打开 Austerity 微信二维码原图"
      >
        <img src="/images/wechat-austerity.jpg" alt="Austerity 微信好友二维码" class="qr-image" />
      </a>
    </aside>

    <NForm
      ref="formRef"
      :model="model"
      :rules="rules"
      size="large"
      :show-label="false"
      class="register-form-panel"
      @keyup.enter="handleSubmit"
    >
      <div class="form-section-label">账号信息</div>

      <NFormItem path="username">
        <NInput v-model:value="model.username" :placeholder="$t('page.login.common.userNamePlaceholder')">
          <template #prefix>
            <icon-ant-design:user-outlined />
          </template>
        </NInput>
      </NFormItem>

      <NFormItem path="password">
        <NInput
          v-model:value="model.password"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.passwordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>

      <NFormItem path="confirmPassword">
        <NInput
          v-model:value="model.confirmPassword"
          type="password"
          show-password-on="click"
          :placeholder="$t('page.login.common.confirmPasswordPlaceholder')"
        >
          <template #prefix>
            <icon-ant-design:key-outlined />
          </template>
        </NInput>
      </NFormItem>

      <NSpace vertical :size="14" class="action-stack">
        <NButton type="primary" size="large" round block :loading="loading" @click="handleSubmit">
          {{ $t('page.login.common.register') }}
        </NButton>
        <NButton size="large" round block @click="toggleLoginModule('pwd-login')">
          {{ $t('page.login.common.back') }}
        </NButton>
      </NSpace>

      <div class="agreement-copy">
        {{ $t('page.login.register.agreement') }}
        <NButton text type="primary">{{ $t('page.login.register.protocol') }}</NButton>
        {{ $t('page.login.register.and') }}
        <NButton text type="primary">{{ $t('page.login.register.policy') }}</NButton>
      </div>
    </NForm>
  </div>
</template>

<style scoped>
.register-layout {
  display: grid;
  grid-template-columns: 230px minmax(280px, 1fr);
  gap: 34px;
  align-items: center;
}

.contact-card {
  min-width: 0;
  overflow: hidden;
  border: 1px solid rgb(var(--primary-color) / 0.18);
  border-radius: 8px;
  padding: 16px;
  background: rgb(var(--container-bg-color));
  box-shadow: 0 14px 32px rgb(var(--primary-color) / 0.07);
}

.form-section-label {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
  color: rgb(var(--primary-600-color));
}

.contact-title {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.4;
  color: rgb(var(--base-text-color));
}

.qr-frame {
  display: block;
  margin-top: 14px;
  overflow: hidden;
  border: 1px solid rgb(var(--primary-color) / 0.1);
  border-radius: 4px;
  background: #fff;
}

.qr-frame:focus-visible {
  outline: 2px solid rgb(var(--primary-color));
  outline-offset: 3px;
}

.qr-image {
  display: block;
  width: 100%;
  height: auto;
}

.register-form-panel {
  min-width: 0;
}

.form-section-label {
  margin-bottom: 16px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgb(var(--base-text-color) / 0.12);
  color: rgb(var(--base-text-color) / 0.58);
}

.register-form-panel :deep(.n-form-item) {
  margin-bottom: 4px;
}

.register-form-panel :deep(.n-input) {
  --n-border-radius: 12px !important;
}

.action-stack {
  width: 100%;
  margin-top: 8px;
}

.agreement-copy {
  margin-top: 17px;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
  color: rgb(var(--base-text-color) / 0.56);
}

@media (max-width: 1180px) {
  .register-layout {
    grid-template-columns: 1fr;
    gap: 22px;
  }

  .contact-card {
    width: min(230px, 100%);
  }
}

@media (max-width: 560px) {
  .register-layout {
    gap: 20px;
  }

  .contact-card {
    width: min(230px, 100%);
  }

  .agreement-copy {
    font-size: 11px;
  }
}
</style>
