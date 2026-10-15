<script setup lang="ts">
import { computed, nextTick, onActivated, ref, watch } from "vue";
import type { Component } from "vue";
import { useAppStore } from "@/store/modules/app";
import { useThemeStore } from "@/store/modules/theme";
import BrandIntro from "@/components/ui/brand-intro.vue";
import KnowledgeCharacters from "@/components/ui/knowledge-characters.vue";
import PwdLogin from "./modules/pwd-login.vue";
import CodeLogin from "./modules/code-login.vue";
import Register from "./modules/register.vue";
import ResetPwd from "./modules/reset-pwd.vue";
import BindWechat from "./modules/bind-wechat.vue";

interface Props {
  /** The login module */
  module?: UnionKey.LoginModule;
}

const props = defineProps<Props>();
const appStore = useAppStore();
const themeStore = useThemeStore();
// Begin each login visit in the light theme; the user's later switch remains available.
themeStore.setThemeScheme("light");

function canPlayIntro() {
  return (
    (!props.module || props.module === "pwd-login") &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

const showIntro = ref(canPlayIntro());
const introRun = ref(1);
const loginErrorSignal = ref(0);
let hasActivated = false;

function handleInvalidCredentials() {
  loginErrorSignal.value += 1;
}

async function replayIntro() {
  if (!canPlayIntro()) return;

  showIntro.value = false;
  await nextTick();
  introRun.value += 1;
  showIntro.value = true;
}

onActivated(() => {
  themeStore.setThemeScheme("light");
  if (hasActivated) {
    replayIntro();
    return;
  }
  hasActivated = true;
});

watch(
  () => props.module,
  (module, previousModule) => {
    const returnedToLogin =
      (!module || module === "pwd-login") &&
      previousModule &&
      previousModule !== "pwd-login";
    if (returnedToLogin) replayIntro();
  },
);

interface LoginModule {
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  "pwd-login": { component: PwdLogin },
  "code-login": { component: CodeLogin },
  register: { component: Register },
  "reset-pwd": { component: ResetPwd },
  "bind-wechat": { component: BindWechat },
};

const activeModule = computed(() => moduleMap[props.module || "pwd-login"]);
const isRegisterModule = computed(
  () => (props.module || "pwd-login") === "register",
);
</script>

<template>
  <Transition name="intro-reveal" mode="out-in">
    <BrandIntro
      v-if="showIntro"
      :key="`intro-${introRun}`"
      @finish="showIntro = false"
    />
    <div v-else key="login" class="login-scene">
      <header class="login-topbar">
        <a href="#/login" class="brand-lockup" aria-label="知据首页">
          <span class="brand-monogram">K<span class="brand-monogram-dot"></span></span>
          <span class="brand-name">知据<small>KnowGround</small></span>
        </a>
        <div class="topbar-right">
          <ThemeSchemaSwitch
            :theme-schema="themeStore.themeScheme"
            :show-tooltip="false"
            @switch="themeStore.toggleThemeScheme"
          />
        </div>
      </header>
      <main class="login-stage" :class="{ 'register-stage': isRegisterModule }">
        <section class="brand-panel">
          <div class="brand-heading">
            <h1>让每个答案<br /><em>都有来处</em></h1>
          </div>
          <KnowledgeCharacters :error-signal="loginErrorSignal" />
          <div class="rag-progress" role="img" aria-label="RAG 流程：提问、检索、引用、回答">
            <div class="rag-track"><span class="rag-track-fill" /><span class="rag-runner" /></div>
            <div class="rag-steps">
              <span class="rag-step"><i /><b>提问</b></span>
              <span class="rag-step"><i /><b>检索</b></span>
              <span class="rag-step"><i /><b>引用</b></span>
              <span class="rag-step"><i /><b>回答</b></span>
            </div>
          </div>
        </section>
        <section class="form-panel">
          <div class="form-heading">
            <h2>{{ isRegisterModule ? "创建你的账户" : "欢迎回到知据" }}</h2>
          </div>
          <Transition :name="themeStore.page.animateMode" mode="out-in"
            ><component :is="activeModule.component" @invalid-credentials="handleInvalidCredentials"
          /></Transition>
          <LangSwitch
            v-if="themeStore.header.multilingual.visible"
            :lang="appStore.locale"
            :lang-options="appStore.localeOptions"
            :show-tooltip="false"
            @change-lang="appStore.changeLocale"
          />
        </section>
      </main>
    </div>
  </Transition>
</template>

<style scoped>
.login-scene {
  --ink: #4c4840;
  --muted: #898277;
  --paper: #f8f5ee;
  --brand-paper: #eae5da;
  background: var(--paper);
  color: var(--ink);
  font-family: var(--kg-font-sans);
  position: relative;
  min-height: 100svh;
}
.login-topbar {
  padding: 30px clamp(30px, 4.5vw, 72px);
  position: absolute; inset: 0 0 auto; z-index: 10;
  display: flex; align-items: center; justify-content: space-between;
}
.brand-lockup { display: flex; align-items: center; gap: 14px; text-decoration: none; color: var(--ink); }
.brand-monogram {
  position: relative; width: 43px; height: 43px; display: grid; place-items: center;
  border: 1px solid #c9c1b2; border-radius: 12px; background: #e8e1d3;
  font: 600 24px/1 Georgia, serif; letter-spacing: -.08em;
}
.brand-monogram-dot { position: absolute; right: 8px; top: 10px; width: 5px; height: 5px; border-radius: 50%; background: #a8987e; }
.brand-name { display: flex; flex-direction: column; gap: 1px; font-size: 23px; font-weight: 650; letter-spacing: -.04em; line-height: 1.08; }
.brand-name small { margin: 0; font-size: 11px; font-weight: 550; letter-spacing: .09em; color: #8c8478; }
.login-stage { display: grid; min-height: 100svh; grid-template-columns: minmax(0, 1.12fr) minmax(440px, .88fr); background: var(--paper); overflow: clip; }
.brand-panel {
  position: relative; min-height: 100svh; padding: 154px clamp(40px, 5vw, 82px) 44px;
  display: flex;
  align-items: stretch; justify-content: center; flex-direction: column;
  background: #eae5da; border-right: 1px solid #d9d1c3;
}
.brand-panel::after { display: none; }
.brand-heading { position: relative; z-index: 2; width: min(680px, 100%); margin: 0; animation: content-arrive .6s ease-out both; }
.brand-heading h1 { margin: 0; font-size: clamp(48px, 4.5vw, 76px); font-weight: 560; line-height: 1.18; letter-spacing: -.075em; }
.brand-heading h1 em { font-family: var(--kg-font-sans); font-style: normal; font-weight: 500; color: #7b7163; letter-spacing: -.025em; }
.rag-progress { position: relative; z-index: 2; width: min(760px, 100%); height: 62px; margin: 28px auto 0; }
.rag-track { position: absolute; top: 5px; left: 35px; right: 35px; height: 2px; background: #cfc5b6; }
.rag-track-fill { display: block; width: 100%; height: 100%; background: #968771; transform: scaleX(0); transform-origin: left center; animation: rag-fill 8s linear infinite; }
.rag-runner { position: absolute; left: 0; top: -4px; width: 10px; height: 10px; border-radius: 50%; background: #8e806d; box-shadow: 0 0 0 4px #e7dfd2; animation: rag-travel 8s linear infinite; }
.rag-steps { position: relative; display: flex; justify-content: space-between; }
.rag-step { display: flex; flex-direction: column; align-items: center; gap: 15px; width: 70px; color: #665e54; }
.rag-step i { width: 12px; height: 12px; border: 2px solid #b9ad9c; border-radius: 50%; background: #eae5da; }
.rag-step b { font-size: 15px; font-weight: 600; white-space: nowrap; letter-spacing: .02em; }
.rag-step:first-child i { background: #8e806d; border-color: #8e806d; }
.form-panel { position: relative; min-width: 0; width: min(410px, calc(100% - 90px)); padding-top: 112px; padding-bottom: 70px; align-self: center; justify-self: center; animation: content-arrive .6s .07s ease-out both; }
.register-stage .form-panel { width: min(650px, calc(100% - 64px)); }
.form-heading h2 { margin-bottom: 34px; font-size: 38px; font-weight: 610; letter-spacing: -.055em; }
.form-panel :deep(.n-form-item-label) { color: #514b42; font-size: 14px; font-weight: 600; padding-bottom: 9px; }
.form-panel :deep(.n-input) {
  background: #fffdf8;
  border: 1px solid #d7d0c3;
  border-radius: 14px;
  box-shadow: 0 2px 8px rgb(69 56 38 / 4%);
  transition: border-color 180ms ease, box-shadow 180ms ease;
}
.form-panel :deep(.n-input:hover) { border-color: #a89a85; }
.form-panel :deep(.n-input:focus-within) {
  border-color: #74634d;
  box-shadow: 0 0 0 3px rgb(116 99 77 / 15%);
}
.form-panel :deep(.n-input__input-el) { color: #342f29; }
.form-panel :deep(.n-input__placeholder) { color: #777169; }
.form-panel :deep(.n-input__input-el::placeholder) { color: #777169; opacity: 1; }
.form-panel :deep(.n-input__prefix),
.form-panel :deep(.n-input__suffix) { color: #5e5a52; }
.form-panel :deep(.n-checkbox__label) { color: #49443c; }
.dark .login-scene { --ink: #e8e2d8; --muted: #afa598; --paper: #171716; --brand-paper: #22211f; }
.dark .brand-panel { background: #23221f; border-color: #34312b; }
.dark .brand-heading h1 em { color: #c9bea9; }
.dark .form-panel :deep(.n-form-item-label),
.dark .form-panel :deep(.n-checkbox__label) { color: #dcd5c9; }
.dark .form-panel :deep(.n-input) { background: #292823; border-color: #655d50; box-shadow: none; }
.dark .form-panel :deep(.n-input:hover) { border-color: #a99a83; }
.dark .form-panel :deep(.n-input:focus-within) { border-color: #c5b296; box-shadow: 0 0 0 3px rgb(197 178 150 / 20%); }
.dark .form-panel :deep(.n-input__input-el) { color: #f2ece3; }
.dark .form-panel :deep(.n-input__input-el::placeholder),
.dark .form-panel :deep(.n-input__placeholder),
.dark .form-panel :deep(.n-input__prefix),
.dark .form-panel :deep(.n-input__suffix) { color: #b8ae9e; }
.dark .rag-step { color: #c8beb1; }
.dark .rag-track { background: #6c6256; }
.dark .rag-step i { border-color: #a59988; }
.dark .rag-step i { background: #23221f; }
.dark .brand-monogram { background: #343129; border-color: #6b6152; }
@keyframes rag-fill { 0%, 7% { transform: scaleX(0) } 90%, 100% { transform: scaleX(1) } }
@keyframes rag-travel { 0%, 7% { left: 0 } 90%, 100% { left: calc(100% - 10px) } }
@keyframes content-arrive { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }
@media (min-width: 901px) and (max-width: 1200px) {
  .brand-panel :deep(.character-stage) { height: min(37vh, 330px); }
}
@media (min-width: 901px) and (max-height: 800px) {
  .brand-panel { padding-top: 124px; padding-bottom: 36px; }
  .brand-panel :deep(.character-stage) { margin-top: 22px; }
}
@media (min-width: 901px) and (max-height: 700px) {
  .brand-panel { padding-top: 100px; padding-bottom: 26px; }
  .brand-heading h1 { font-size: clamp(44px, 4vw, 64px); }
  .brand-panel :deep(.character-stage) { height: 250px; }
}
@media (max-width: 900px) {
  .login-topbar { position: relative; padding: 18px 24px; border-bottom: 1px solid #ded7ca; background: var(--paper); }
  .login-stage { display: flex; flex-direction: column; }
  .brand-panel { display: flex; min-height: auto; padding: 38px 24px 23px; border-right: 0; border-bottom: 1px solid #d9d1c3; }
  .brand-heading h1 { font-size: clamp(38px, 9vw, 56px); }
  .brand-panel :deep(.character-stage) { display: none; }
  .rag-progress { width: min(420px, 100%); margin: 30px auto 0; }
  .form-panel { width: calc(100% - 48px); max-width: 470px; padding: 44px 0 60px; align-self: center; }
  .register-stage .form-panel { width: calc(100% - 48px); }
}
@media (prefers-reduced-motion: reduce) {
  .rag-track-fill, .rag-runner { animation: none; }
  .brand-heading, .form-panel { animation: none; }
  .rag-track-fill { transform: scaleX(1); }
  .rag-runner { left: calc(100% - 12px); }
}
</style>

<style scoped src="./login-input-fix.css"></style>
