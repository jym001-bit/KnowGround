<script setup lang="ts">
import { computed, nextTick, onActivated, ref, watch } from "vue";
import HandwritingText from "@/components/ui/handwriting-text.vue";
import BrandIntro from "@/components/ui/brand-intro.vue";
import type { Component } from "vue";
import { loginModuleRecord } from "@/constants/app";
import { useAppStore } from "@/store/modules/app";
import { useThemeStore } from "@/store/modules/theme";
import { $t } from "@/locales";
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
function canPlayIntro() {
  return (
    (!props.module || props.module === "pwd-login") &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches
  );
}

const showIntro = ref(canPlayIntro());
const introRun = ref(1);
let hasActivated = false;

async function replayIntro() {
  if (!canPlayIntro()) return;

  showIntro.value = false;
  await nextTick();
  introRun.value += 1;
  showIntro.value = true;
}

onActivated(() => {
  if (hasActivated) {
    void replayIntro();
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
    if (returnedToLogin) void replayIntro();
  },
);

const artOffset = ref({ x: 0, y: 0 });
function moveArt(event: PointerEvent) {
  if (
    event.pointerType !== "mouse" ||
    window.matchMedia("(prefers-reduced-motion: reduce)").matches
  )
    return;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  artOffset.value = {
    x: ((event.clientX - rect.left) / rect.width - 0.5) * 12,
    y: ((event.clientY - rect.top) / rect.height - 0.5) * 12,
  };
}
const appStore = useAppStore();
const themeStore = useThemeStore();

interface LoginModule {
  label: string;
  component: Component;
}

const moduleMap: Record<UnionKey.LoginModule, LoginModule> = {
  "pwd-login": { label: loginModuleRecord["pwd-login"], component: PwdLogin },
  "code-login": {
    label: loginModuleRecord["code-login"],
    component: CodeLogin,
  },
  register: { label: loginModuleRecord.register, component: Register },
  "reset-pwd": { label: loginModuleRecord["reset-pwd"], component: ResetPwd },
  "bind-wechat": {
    label: loginModuleRecord["bind-wechat"],
    component: BindWechat,
  },
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
        <a href="#/login" class="brand-lockup"
          ><SystemLogo /><span>知据 <small>KnowGround</small></span></a
        >
        <div class="topbar-right">
          <ThemeSchemaSwitch
            :theme-schema="themeStore.themeScheme"
            :show-tooltip="false"
            @switch="themeStore.toggleThemeScheme"
          />
        </div>
      </header>
      <main class="login-stage" :class="{ 'register-stage': isRegisterModule }">
        <section
          class="brand-panel"
          :style="{
            '--art-x': `${artOffset.x}px`,
            '--art-y': `${artOffset.y}px`,
          }"
          @pointermove="moveArt"
          @pointerleave="artOffset = { x: 0, y: 0 }"
        >
          <div class="brand-heading">
            <div class="handwriting-row">
              <HandwritingText :animated="false" />
            </div>
            <h1>知据，让答案<br /><span>有据可循。</span></h1>
          </div>
          <div class="brand-art" aria-hidden="true">
            <span /><span /><span /><i /> <b class="art-node node-one" /><b
              class="art-node node-two"
            /><b class="art-node node-three" />
          </div>
          <div class="brand-path" aria-hidden="true">
            <span /><i /><i /><i />
          </div>
          <button
            class="replay-intro"
            type="button"
            aria-label="重播开场动画"
            @click="replayIntro"
          >
            ↻ 重播开场
          </button>
        </section>
        <section class="form-panel">
          <div class="form-heading">
            <span class="form-kicker">{{
              isRegisterModule ? "CREATE ACCOUNT" : "WELCOME BACK"
            }}</span>
            <h2>{{ isRegisterModule ? "加入知据" : "欢迎回来" }}</h2>
            <p>
              {{
                isRegisterModule
                  ? "建立你的知识连接，从这里开始。"
                  : "登录工作台，继续探索你的知识。"
              }}
            </p>
          </div>
          <div class="form-mode">
            <span>{{ $t(activeModule.label) }}</span
            ><i />
          </div>
          <Transition :name="themeStore.page.animateMode" mode="out-in"
            ><component :is="activeModule.component"
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
  --ink: #203c31;
  --muted: #536c5e;
  --paper: #fbfcfa;
  --brand-paper: #e7efe3;
  position: relative;
  min-height: 100svh;
  background: var(--paper);
  color: var(--ink);
}
.login-topbar {
  position: absolute;
  inset: 0 0 auto;
  z-index: 10;
  width: 100%;
  padding: 24px clamp(28px, 5vw, 72px);
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.brand-lockup {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 22px;
  color: var(--ink);
  font-weight: 600;
}
.brand-lockup svg {
  font-size: 36px;
}
.brand-lockup small {
  font-size: 14px;
  font-weight: 500;
  margin-left: 8px;
}
.topbar-right {
  display: flex;
  gap: 20px;
  align-items: center;
  font-size: 14px;
  color: var(--muted);
}
.login-stage {
  width: 100%;
  min-height: 100svh;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  background: var(--paper);
  overflow: clip;
}
.brand-panel {
  position: relative;
  min-height: 100svh;
  padding: 104px clamp(40px, 6vw, 96px) 80px;
  overflow: hidden;
  background: radial-gradient(
      circle at 15% 20%,
      rgb(255 255 255 / 52%) 0,
      transparent 31%
    ),
    linear-gradient(145deg, #edf3e9 0%, var(--brand-paper) 52%, #dce9dd 100%);
  display: flex;
  align-items: center;
}
.brand-panel::after {
  content: "";
  position: absolute;
  inset: 0;
  opacity: 0.28;
  pointer-events: none;
  background-image: radial-gradient(
    rgb(52 111 78 / 22%) 0.7px,
    transparent 0.7px
  );
  background-size: 22px 22px;
  mask-image: linear-gradient(
    130deg,
    transparent 10%,
    #000 55%,
    transparent 90%
  );
}
.brand-heading {
  position: relative;
  z-index: 2;
  width: min(430px, 100%);
  margin: auto;
  animation: content-arrive 0.72s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.handwriting-row {
  width: min(330px, 100%);
  margin-left: -7px;
  color: #28735b;
}
.brand-heading h1 {
  font-size: clamp(38px, 3.2vw, 54px);
  font-weight: 600;
  letter-spacing: -1.6px;
  line-height: 1.42;
  margin-top: 26px;
}
.brand-heading h1 span {
  color: #28735b;
}
.brand-art {
  position: absolute;
  z-index: 1;
  width: clamp(360px, 31vw, 540px);
  aspect-ratio: 1;
  right: -13%;
  bottom: -20%;
  transform: translate(var(--art-x, 0px), var(--art-y, 0px));
  transition: transform 0.6s ease-out;
  pointer-events: none;
}
.brand-art span {
  position: absolute;
  inset: 0;
  border: 1px solid #a4bf9c75;
  border-radius: 50%;
  animation: ring-breathe 7s ease-in-out infinite;
}
.brand-art span:nth-child(2) {
  inset: 48px;
  animation-delay: -2.2s;
}
.brand-art span:nth-child(3) {
  inset: 96px;
  animation-delay: -4.4s;
}
.brand-art i {
  position: absolute;
  top: 48px;
  left: 38px;
  width: 9px;
  height: 9px;
  border-radius: 50%;
  background: #629d74;
}
.art-node {
  position: absolute;
  width: 13px;
  height: 13px;
  border: 3px solid #e8efe3;
  border-radius: 50%;
  background: #28735b;
  box-shadow: 0 0 0 1px rgb(40 115 91 / 20%);
  animation: node-pulse 3.2s ease-in-out infinite;
}
.node-one {
  top: 28%;
  left: 3%;
}
.node-two {
  top: 13%;
  left: 42%;
  animation-delay: -1.1s;
}
.node-three {
  top: 44%;
  left: 24%;
  animation-delay: -2.1s;
}
.brand-path {
  position: absolute;
  top: 14%;
  right: 9%;
  width: 130px;
  height: 1px;
  background: rgb(82 123 92 / 24%);
  transform: rotate(-18deg);
  transform-origin: right center;
  z-index: 2;
}
.brand-path span {
  position: absolute;
  inset: -1px 34% auto 0;
  height: 3px;
  border-radius: 999px;
  background: #5c9670;
  transform-origin: left;
  animation: path-flow 3.8s ease-in-out infinite;
}
.brand-path i {
  position: absolute;
  top: -3px;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7ca489;
}
.brand-path i:nth-of-type(1) {
  left: 0;
}
.brand-path i:nth-of-type(2) {
  left: 50%;
}
.brand-path i:nth-of-type(3) {
  right: 0;
}
.replay-intro {
  position: absolute;
  bottom: 32px;
  left: clamp(40px, 6vw, 96px);
  font-size: 13px;
  padding: 6px 0;
  background: transparent;
  color: #527b5c;
  cursor: pointer;
}
.replay-intro:focus-visible {
  outline: 2px solid #28735b;
  outline-offset: 4px;
}
.form-panel {
  position: relative;
  width: min(430px, calc(100% - 72px));
  padding: 96px 0 54px;
  justify-self: center;
  min-width: 0;
  align-self: center;
  animation: content-arrive 0.72s 0.08s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.form-kicker {
  display: block;
  margin-bottom: 16px;
  font-size: 11px;
  letter-spacing: 2.5px;
  color: var(--muted);
}
.form-heading h2 {
  font-size: 36px;
  font-weight: 600;
  letter-spacing: -1px;
  margin-bottom: 12px;
}
.form-heading p {
  font-size: 15px;
  color: var(--muted);
  line-height: 1.7;
}
.form-mode {
  display: flex;
  gap: 14px;
  align-items: center;
  margin: 30px 0 22px;
  font-size: 14px;
  color: var(--muted);
}
.form-mode i {
  height: 1px;
  background: #dce5dc;
  flex: 1;
}
.register-stage {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.register-stage .form-panel {
  width: min(650px, calc(100% - 64px));
}
.dark .login-scene {
  --ink: #e8f0e7;
  --muted: #b5c6b7;
  --paper: #19251e;
  --brand-paper: #263a2c;
}
.dark .brand-panel {
  background: radial-gradient(
      circle at 15% 20%,
      rgb(130 171 137 / 9%) 0,
      transparent 31%
    ),
    linear-gradient(145deg, #2b402f 0%, var(--brand-paper) 52%, #203225 100%);
}
.dark .brand-heading h1 span,
.dark .handwriting-row {
  color: #abd8ac;
}
.dark .art-node {
  border-color: #263a2c;
  background: #abd8ac;
}
.dark .replay-intro {
  color: #b6d4b8;
}
.form-panel :deep(.login-form) {
  animation: content-arrive 0.72s 0.16s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.intro-reveal-leave-active,
.intro-reveal-enter-active {
  transition:
    opacity 0.4s ease,
    transform 0.4s ease;
}
.intro-reveal-leave-to {
  opacity: 0;
  transform: scale(1.015);
}
.intro-reveal-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
@keyframes content-arrive {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes ring-breathe {
  0%,
  100% {
    opacity: 0.62;
    transform: scale(1);
  }
  50% {
    opacity: 1;
    transform: scale(1.025);
  }
}
@keyframes node-pulse {
  0%,
  100% {
    box-shadow: 0 0 0 1px rgb(40 115 91 / 20%);
  }
  50% {
    box-shadow: 0 0 0 7px rgb(40 115 91 / 8%);
  }
}
@keyframes path-flow {
  0%,
  100% {
    opacity: 0.45;
    transform: scaleX(0.15);
  }
  55% {
    opacity: 1;
    transform: scaleX(1);
  }
}
@media (max-width: 900px) {
  .login-topbar {
    padding: 18px 24px;
    border-bottom: 1px solid #dce5dc;
    background: var(--paper);
  }
  .login-stage {
    grid-template-columns: 1fr;
  }
  .brand-panel {
    display: none;
  }
  .form-panel {
    width: calc(100% - 48px);
    padding: 116px 0 40px;
    align-self: start;
  }
  .topbar-right > span {
    display: none;
  }
}
@media (prefers-reduced-motion: reduce) {
  .brand-art,
  .brand-art span,
  .art-node,
  .brand-path span,
  .brand-heading,
  .form-panel,
  .form-panel :deep(.login-form),
  .intro-reveal-leave-active,
  .intro-reveal-enter-active {
    animation: none;
    transition: none;
  }
}
</style>
