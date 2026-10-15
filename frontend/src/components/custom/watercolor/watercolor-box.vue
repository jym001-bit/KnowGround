<script setup lang="ts">
import { ref } from 'vue';
import { useElementSize } from '@vueuse/core';
import WatercolorPaint from './watercolor-paint.vue';
withDefaults(defineProps<{ seed?: number; color?: string }>(), { seed: 1, color: '#ded4c3' });
const box = ref<HTMLElement>();
const { width, height } = useElementSize(box);
</script>

<template>
  <div ref="box" class="watercolor-box">
    <WatercolorPaint v-if="width > 0 && height > 0" class="watercolor-box__paint" :width="Math.round(width)" :height="Math.round(height)" :color="color" :seed="seed" />
    <div class="watercolor-box__content"><slot /></div>
  </div>
</template>

<style scoped>
.watercolor-box { position: relative; isolation: isolate; }
.watercolor-box__paint { position: absolute; left: -8px; top: -8px; opacity: .35; pointer-events: none; }
.watercolor-box__content { position: relative; }
</style>
