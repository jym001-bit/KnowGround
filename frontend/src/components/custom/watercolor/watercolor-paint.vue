<script setup lang="ts">
import { computed, useId } from 'vue';
import { renderCompleteWatercolorBox } from '@/vendor/watercolor/engine/drawing-tasks.js';

const props = withDefaults(defineProps<{ width: number; height: number; color?: string; seed?: number }>(), {
  color: '#b9aa91', seed: 1
});
const filterId = `kg-paint-${useId().replace(/[^a-zA-Z0-9_-]/g, '')}`;
// Only locally generated SVG geometry is inserted; document content never enters this markup.
const markup = computed(() => renderCompleteWatercolorBox(0, 0, props.width, props.height, {
  color: props.color, seed: props.seed, opacity: 0.94, endScatter: 0.3
}));
</script>

<template>
  <svg :width="width + 16" :height="height + 16" :viewBox="`-8 -8 ${width + 16} ${height + 16}`" aria-hidden="true" focusable="false">
    <defs>
      <filter :id="filterId" x="-8%" y="-8%" width="116%" height="116%" color-interpolation-filters="sRGB">
        <feTurbulence type="fractalNoise" baseFrequency="0.55" numOctaves="2" seed="7" result="grain" />
        <feColorMatrix in="grain" type="luminanceToAlpha" result="ga" />
        <feComposite in="SourceGraphic" in2="ga" operator="arithmetic" k1="0" k2="1" k3="-0.30" k4="0" result="inked" />
        <feTurbulence type="fractalNoise" baseFrequency="0.11" numOctaves="2" seed="11" result="warp" />
        <feDisplacementMap in="inked" in2="warp" scale="2.6" xChannelSelector="R" yChannelSelector="G" />
      </filter>
    </defs>
    <g :filter="`url(#${filterId})`" style="isolation: isolate" v-html="markup" />
  </svg>
</template>
