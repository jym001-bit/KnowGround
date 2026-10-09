<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue';

const props = defineProps<{ errorSignal?: number }>();

const stageRef = ref<HTMLElement>();
const gaze = ref({ x: 0, y: 0 });
const activeField = ref<'none' | 'username' | 'password'>('none');
const isError = ref(false);
let motionQuery: MediaQueryList;
let errorTimer: ReturnType<typeof setTimeout> | undefined;
let errorStartTimer: ReturnType<typeof setTimeout> | undefined;

watch(() => props.errorSignal, signal => {
  if (!signal) return;
  clearTimeout(errorTimer);
  clearTimeout(errorStartTimer);
  isError.value = false;
  errorStartTimer = setTimeout(() => {
    isError.value = true;
    errorTimer = setTimeout(() => { isError.value = false; }, 1800);
  }, 20);
});

function updateGaze(event: PointerEvent) {
  if (event.pointerType !== 'mouse' || motionQuery.matches || !stageRef.value || activeField.value === 'password') return;
  const rect = stageRef.value.getBoundingClientRect();
  gaze.value = {
    x: Math.max(-7, Math.min(7, ((event.clientX - rect.left) / rect.width - 0.5) * 18)),
    y: Math.max(-5, Math.min(5, ((event.clientY - rect.top) / rect.height - 0.5) * 13))
  };
}

function updateFocus(event: FocusEvent) {
  const target = event.target;
  if (!(target instanceof HTMLInputElement)) return;
  const field = target.getAttribute('autocomplete');
  if (field === 'username') activeField.value = 'username';
  if (field === 'current-password' || field === 'new-password') activeField.value = 'password';
}

function clearFocus(event: FocusEvent) {
  const target = event.target;
  if (target instanceof HTMLInputElement) activeField.value = 'none';
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
  document.addEventListener('focusin', updateFocus);
  document.addEventListener('focusout', clearFocus);
  document.addEventListener('pointermove', updateGaze);
});

onUnmounted(() => {
  clearTimeout(errorTimer);
  clearTimeout(errorStartTimer);
  document.removeEventListener('focusin', updateFocus);
  document.removeEventListener('focusout', clearFocus);
  document.removeEventListener('pointermove', updateGaze);
});
</script>

<template>
  <div
    ref="stageRef"
    class="character-stage"
    :class="{ 'is-typing': activeField === 'username', 'is-private': activeField === 'password', 'is-error': isError }"
    :style="{ '--gaze-x': `${gaze.x}px`, '--gaze-y': `${gaze.y}px` }"
    aria-hidden="true"
  >
    <div class="character character-tall">
      <div class="eyes eyes-light"><i class="eye"><b /></i><i class="eye"><b /></i></div>
    </div>
    <div class="character character-dark">
      <div class="eyes eyes-light"><i class="eye"><b /></i><i class="eye"><b /></i></div>
    </div>
    <div class="character character-arch">
      <div class="eyes eyes-light"><i class="eye"><b /></i><i class="eye"><b /></i></div>
    </div>
    <div class="character character-gold">
      <div class="eyes eyes-light"><i class="eye"><b /></i><i class="eye"><b /></i></div>
    </div>
  </div>
</template>

<style scoped>
.character-stage {
  position: relative;
  width: min(860px, 100%);
  height: clamp(310px, 43vh, 510px);
  margin: clamp(45px, 7vh, 80px) auto 0;
  border-bottom: 2px solid #bcb0a0;
  isolation: isolate;
}
.character-stage::before {
  content: '';
  position: absolute;
  z-index: -1;
  left: 8%;
  right: 9%;
  bottom: 5%;
  height: 78%;
  border-radius: 50%;
  background: radial-gradient(ellipse at 50% 75%, rgb(255 252 246 / 58%), rgb(255 252 246 / 0%) 67%);
  pointer-events: none;
}
.character {
  position: absolute;
  bottom: 0;
  transform-origin: bottom center;
  transition: transform 650ms cubic-bezier(.2, .7, .2, 1), height 650ms cubic-bezier(.2, .7, .2, 1);
}
.character-tall { left: 13%; z-index: 1; width: 31%; height: 98%; border-radius: 14px 14px 0 0; background: #8d8094; transform: skewX(-9deg); }
.character-dark { left: 45%; z-index: 2; width: 22%; height: 70%; border-radius: 12px 12px 0 0; background: #58514e; }
.character-arch { left: 0; z-index: 3; width: 42%; height: 45%; border-radius: 52% 52% 0 0 / 90% 90% 0 0; background: #d6a080; }
.character-gold { left: 60%; z-index: 4; width: 26%; height: 55%; border-radius: 50% 50% 0 0 / 28% 28% 0 0; background: #d4bd81; }
.eyes { position: absolute; display: flex; align-items: center; gap: 22px; transform: translate(var(--gaze-x), var(--gaze-y)); transition: transform 320ms cubic-bezier(.22, 1, .36, 1); }
.character-tall .eyes { top: 12%; left: 26%; }
.character-dark .eyes { top: 15%; left: 19%; gap: 16px; }
.character-arch .eyes { top: 44%; left: 34%; gap: 28px; }
.character-gold .eyes { top: 20%; left: 32%; gap: 18px; }
.eye { position: relative; display: block; flex: none; width: 18px; height: 18px; border-radius: 50%; background: #f8f5ee; animation: character-blink 7.1s infinite; }
.character-dark .eye { width: 15px; height: 15px; animation-delay: -2.1s; }
.eye b { position: absolute; top: 6px; left: 7px; width: 7px; height: 7px; border-radius: 50%; background: #5b514d; transform: translate(calc(var(--gaze-x) * .25), calc(var(--gaze-y) * .25)); transition: transform 280ms cubic-bezier(.22, 1, .36, 1); }
.is-typing .character-tall { height: 100%; transform: skewX(-11deg) translateX(7px); }
.is-typing .character-dark { transform: skewX(5deg) translateX(9px); }
.is-typing .character-arch { transform: skewX(-3deg); }
.is-typing .character-gold { transform: skewX(3deg); }
.is-private .character-tall { transform: skewX(-9deg) translateX(5px); }
.is-private .character-dark { transform: skewX(-2deg); }
.is-private .eyes { transform: translate(-9px, -3px); }
.is-private .eyes-light .eye { height: 3px; margin-top: 7px; border-radius: 999px; }
.is-private .eyes-light .eye b { display: none; }
.is-error .character-tall { transform: skewX(-9deg); }
.is-error .character-dark { transform: none; }
.is-error .character-tall .eyes { transform: translate(8px, 2px); }
.is-error .character-dark .eyes { transform: translate(-4px, -5px); }
.is-error .eyes-light .eye { height: 18px; margin-top: 0; border-radius: 50%; }
.is-error .character-dark .eye { height: 15px; }
.is-error .eyes-light .eye b { display: block; }
.is-error .character-tall .eye b { transform: translate(3px, 2px); }
.is-error .character-dark .eye b { transform: translate(-2px, -4px); }
@keyframes character-blink { 0%, 42%, 44%, 100% { transform: scaleY(1); } 43% { transform: scaleY(.12); } }
@media (prefers-reduced-motion: reduce) {
  .character, .eyes, .eye, .eye b { animation: none; transition: none; }
}
</style>
