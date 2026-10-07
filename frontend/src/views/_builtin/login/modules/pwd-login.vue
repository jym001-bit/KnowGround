<script setup lang="ts">
import { reactive } from 'vue';
import { loginModuleRecord } from '@/constants/app';
import { useAuthStore } from '@/store/modules/auth';
import { useRouterPush } from '@/hooks/common/router';
import { useFormRules, useNaiveForm } from '@/hooks/common/form';
import { localStg } from '@/utils/storage';
import { $t } from '@/locales';

defineOptions({
  name: 'PwdLogin'
});

const authStore = useAuthStore();
const { toggleLoginModule } = useRouterPush();
const { formRef, validate } = useNaiveForm();

interface FormModel {
  userName: string;
  password: string;
  rememberMe: boolean;
}

type FormRuleModel = Pick<FormModel, 'userName' | 'password'>;

const rememberedLogin = localStg.get('rememberedLogin');

const model: FormModel = reactive({
  userName: rememberedLogin?.userName || '',
  password: rememberedLogin?.password || '',
  rememberMe: Boolean(rememberedLogin)
});

const rules = computed<Record<keyof FormRuleModel, App.Global.FormRule[]>>(() => {
  // inside computed to make locale reactive, if not apply i18n, you can define it without computed
  const { formRules } = useFormRules();

  return {
    userName: formRules.userName,
    password: formRules.pwd
  };
});

async function handleSubmit() {
  try {
    await validate();
  } catch {
    return;
  }

  const success = await authStore.login(model.userName, model.password);

  if (!success) {
    return;
  }

  if (model.rememberMe) {
    localStg.set('rememberedLogin', {
      userName: model.userName,
      password: model.password
    });
  } else {
    localStg.remove('rememberedLogin');
  }
}
</script>

<template>
  <NForm
    ref="formRef"
    class="login-form"
    :model="model"
    :rules="rules"
    :show-require-mark="false"
    size="large"
    label-placement="top"
    @keyup.enter="handleSubmit"
  >
    <NFormItem path="userName" label="用户名">
      <NInput
        v-model:value="model.userName"
        :input-props="{ autocomplete: 'username', 'aria-label': '用户名' }"
        :placeholder="$t('page.login.common.userNamePlaceholder')"
      >
        <template #prefix>
          <icon-ant-design:user-outlined />
        </template>
      </NInput>
    </NFormItem>
    <NFormItem path="password" label="密码">
      <NInput
        v-model:value="model.password"
        type="password"
        :input-props="{ autocomplete: 'current-password', 'aria-label': '密码' }"
        show-password-on="click"
        :placeholder="$t('page.login.common.passwordPlaceholder')"
      >
        <template #prefix>
          <icon-ant-design:key-outlined />
        </template>
      </NInput>
    </NFormItem>
    <div class="mb-6 flex-y-center justify-between">
      <NCheckbox v-model:checked="model.rememberMe">
        {{ $t('page.login.pwdLogin.rememberMe') }}
      </NCheckbox>
    </div>
    <div class="login-actions">
      <NButton type="primary" size="large" block :loading="authStore.loginLoading" @click="handleSubmit">
        登录工作台 <span class="login-arrow">→</span>
      </NButton>
      <NButton type="primary" size="large" block @click="toggleLoginModule('register')">
        {{ $t(loginModuleRecord.register) }} <span class="login-arrow">→</span>
      </NButton>
    </div>
  </NForm>
</template>

<style scoped>
.login-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.login-actions :deep(.n-button) {
  height: 44px;
  color: #fff;
  font-size: 16px;
  font-weight: 500;
  transition:
    transform 180ms ease,
    box-shadow 180ms ease;
}
.login-actions :deep(.n-button:hover),
.login-actions :deep(.n-button:focus-visible),
.login-actions :deep(.n-button:active) {
  color: #fff;
}
.login-actions :deep(.n-button:hover) {
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgb(22 117 101 / 18%);
}
.login-arrow {
  margin-left: 12px;
  transition: transform 180ms ease;
}
.login-actions :deep(.n-button:hover) .login-arrow {
  transform: translateX(3px);
}
.login-form :deep(.n-input),
.login-actions :deep(.n-button) {
  border-radius: 8px;
}
.login-form :deep(.n-form-item-label) {
  font-size: 14px;
  padding-bottom: 7px;
}
.login-form :deep(.n-checkbox__label) {
  font-size: 14px;
}
@media (prefers-reduced-motion: reduce) {
  .login-actions :deep(.n-button),
  .login-arrow {
    transition: none;
  }
}
</style>
