<script setup lang="ts">
import lettering from './knowground-lettering.json';

withDefaults(defineProps<{ replayKey?: number; animated?: boolean }>(), { replayKey: 0, animated: true });

// Precomputed from the user-provided 21st.dev font: no runtime font or CDN dependency.
// Separate contour strokes preserve progressive drawing; the unified fill preserves letter counters.
</script>

<template>
  <svg
    :key="replayKey"
    :viewBox="lettering.viewBox"
    class="handwriting"
    :class="{ 'is-static': !animated }"
    role="img"
    aria-label="KnowGround"
  >
    <path :d="lettering.full" class="lettering-fill" fill="currentColor" />
    <path
      v-for="(contour, index) in lettering.contours"
      :key="index"
      :d="contour"
      class="lettering-stroke"
      pathLength="1"
      fill="none"
      stroke="currentColor"
      stroke-width="0.85"
      stroke-linecap="round"
      stroke-linejoin="round"
      :style="{ animationDelay: `${0.15 + (index / lettering.contours.length) * 1.7}s` }"
    />
  </svg>
</template>

<style scoped>
.handwriting {
  display: block;
  width: 100%;
  overflow: visible;
}
.is-static .lettering-fill {
  opacity: 1;
  animation: none;
}
.is-static .lettering-stroke {
  stroke-dashoffset: 0;
  animation: none;
}
.lettering-stroke {
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: write-in 0.6s ease-out forwards;
}
.lettering-fill {
  opacity: 0;
  animation: ink-in 0.65s ease-out 1.55s forwards;
}
@keyframes write-in {
  to {
    stroke-dashoffset: 0;
  }
}
@keyframes ink-in {
  to {
    opacity: 1;
  }
}
@media (prefers-reduced-motion: reduce) {
  .lettering-fill {
    opacity: 1;
    animation: none;
  }
  .lettering-stroke {
    stroke-dashoffset: 0;
    animation: none;
  }
}
</style>
