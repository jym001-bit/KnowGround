<script setup lang="ts">
import { computed, ref } from 'vue';
import { useElementSize } from '@vueuse/core';
import WatercolorPaint from './watercolor-paint.vue';
const props = withDefaults(defineProps<{ fraction: number; label: string; color?: string; seed?: number }>(), {
  color: '#ad9a7e', seed: 1
});
const track = ref<HTMLElement>();
const { width } = useElementSize(track);
const ratio = computed(() => Number.isFinite(props.fraction) ? Math.min(1, Math.max(0, props.fraction)) : 0);
</script>

<template>
  <div ref="track" class="marker-track" role="progressbar" :aria-label="label" :aria-valuenow="Math.round(ratio * 100)" :aria-valuemin="0" :aria-valuemax="100">
    <WatercolorPaint v-if="width > 0" class="marker-paper" :width="Math.round(width)" :height="24" color="#d5cbbb" :seed="seed" />
    <WatercolorPaint v-if="width * ratio > 2" class="marker-ink" :width="Math.round(width * ratio)" :height="24" :color="color" :seed="31.7 * seed + 3" />
  </div>
</template>

<style scoped>
.marker-track { position: relative; height: 24px; margin: 18px 0; isolation: isolate; }
.marker-paper, .marker-ink { position: absolute; left: -8px; top: -8px; pointer-events: none; }
.marker-paper { opacity: .25; }
</style>
