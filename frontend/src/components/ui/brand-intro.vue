<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import HandwritingText from './handwriting-text.vue';

const emit = defineEmits<{ finish: [] }>();
const skipButton = ref<HTMLButtonElement>();
const INTRO_DURATION = 3800;
let timer: ReturnType<typeof setTimeout> | undefined;
let finished = false;

function finish() {
  if (finished) return;
  finished = true;
  clearTimeout(timer);
  emit('finish');
}

onMounted(() => {
  skipButton.value?.focus({ preventScroll: true });
  timer = setTimeout(finish, window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 0 : INTRO_DURATION);
});
onUnmounted(() => clearTimeout(timer));
</script>

<template>
  <section class="brand-intro" aria-label="知据开场动画" @keydown.esc="finish">
    <div class="intro-corner">知据 <span>KNOWGROUND</span></div>
    <button ref="skipButton" class="intro-skip" type="button" @click="finish">跳过动画 <span>↗</span></button>
    <div class="intro-center">
      <SystemLogo class="intro-logo" />
      <div class="intro-lettering"><HandwritingText /></div>
      <h1>知据</h1>
      <p>让每一个答案，都有依据。</p>
      <div class="intro-line" aria-hidden="true"><span /></div>
    </div>
    <div class="intro-footer">KNOWLEDGE, GROUNDED.</div>
  </section>
</template>

<style scoped>
.brand-intro {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: #f8f5ee;
  color: #685f52;
  color-scheme: light;
}
.brand-intro::before,
.brand-intro::after {
  content: '';
  position: absolute;
  width: min(76vw, 780px);
  aspect-ratio: 1;
  border: 1px solid #ded5c7;
  border-radius: 50%;
  pointer-events: none;
}
.brand-intro::after {
  width: min(95vw, 1010px);
}
.intro-corner {
  position: absolute;
  top: 32px;
  left: 40px;
  font-weight: 600;
  font-size: 18px;
}
.intro-corner span {
  margin-left: 10px;
  font-size: 9px;
  letter-spacing: 2px;
  color: #9b9183;
}
.intro-skip {
  position: absolute;
  top: 28px;
  right: 36px;
  padding: 10px 16px;
  border: 1px solid #d7cdbd;
  border-radius: 30px;
  background: transparent;
  color: #756b5d;
  font-size: 12px;
  cursor: pointer;
  z-index: 1;
}
.intro-skip span {
  margin-left: 10px;
}
.intro-skip:hover {
  background: #eee8dd;
}
.intro-skip:focus-visible {
  outline: 2px solid #8c806e;
  outline-offset: 4px;
}
.intro-center {
  width: min(860px, 94vw);
  text-align: center;
  z-index: 1;
}
.intro-logo {
  font-size: 46px;
  margin: 0 auto 32px;
  animation: arrive 0.6s ease both;
}
.intro-lettering {
  width: min(720px, 100%);
  margin-inline: auto;
}
.intro-center h1 {
  font-size: 26px;
  font-weight: 500;
  letter-spacing: 8px;
  margin: 26px 0 14px;
  animation: arrive 0.7s ease 1.65s both;
}
.intro-center p {
  font-size: 13px;
  letter-spacing: 3px;
  color: #928778;
  animation: arrive 0.7s ease 1.95s both;
}
.intro-line {
  position: relative;
  width: min(760px, 90vw);
  height: 6px;
  border-radius: 999px;
  background: #ded5c7;
  margin: 42px auto 0;
  box-shadow: inset 0 1px 2px rgb(104 95 82 / 10%);
}
.intro-line span {
  display: block;
  position: relative;
  width: 0;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #c9baa2 0%, #8c806e 72%, #a79880 100%);
  box-shadow: 0 0 14px rgb(140 128 110 / 22%);
  will-change: width;
  animation: progress 3.35s cubic-bezier(0.38, 0, 0.18, 1) both;
}
.intro-line span::after {
  content: '';
  position: absolute;
  top: 50%;
  right: -5px;
  width: 10px;
  height: 10px;
  border: 2px solid #f8f5ee;
  border-radius: 50%;
  background: #8c806e;
  box-shadow:
    0 0 0 4px rgb(140 128 110 / 12%),
    0 0 14px rgb(140 128 110 / 26%);
  transform: translateY(-50%);
}
.intro-footer {
  position: absolute;
  bottom: 30px;
  color: #a69a89;
  font-size: 9px;
  letter-spacing: 3px;
}
@keyframes arrive {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@keyframes progress {
  from {
    width: 0;
  }
  to {
    width: 100%;
  }
}
@media (max-width: 600px) {
  .intro-corner {
    top: 24px;
    left: 20px;
  }
  .intro-corner span {
    display: none;
  }
  .intro-skip {
    top: 18px;
    right: 18px;
  }
  .intro-center {
    width: 84vw;
  }
  .intro-logo {
    font-size: 40px;
  }
  .intro-center p {
    font-size: 12px;
    letter-spacing: 1px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .intro-logo,
  .intro-center h1,
  .intro-center p,
  .intro-line span {
    animation: none;
  }
  .intro-line span {
    width: 100%;
  }
}
</style>
