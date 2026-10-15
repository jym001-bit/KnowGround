<script setup lang="ts">
import WatercolorBox from '@/components/custom/watercolor/watercolor-box.vue';
defineProps<{ references: Array<{ referenceNumber: number; fileName: string; fileMd5?: string; snippet?: string; pageNumber?: number | null }> }>();
const emit = defineEmits<{ open: [reference: { referenceNumber: number; fileName: string; fileMd5?: string }] }>();
</script>

<template>
  <details v-if="references.length" class="reference-sources">
    <summary>引用依据 <span>{{ references.length }}</span></summary>
    <div class="reference-sources__grid">
      <button v-for="reference in references" :key="reference.referenceNumber" type="button" class="reference-source" :aria-label="`打开来源 ${reference.referenceNumber}：${reference.fileName}`" @click="emit('open', reference)">
        <WatercolorBox :seed="reference.referenceNumber + 9">
          <div class="reference-source__body">
            <div class="reference-source__heading"><span class="reference-source__number">{{ String(reference.referenceNumber).padStart(2, '0') }}</span><strong>{{ reference.fileName }}</strong><span aria-hidden="true">↗</span></div>
            <p v-if="reference.snippet">{{ reference.snippet }}</p>
            <span v-if="reference.pageNumber" class="reference-source__page">第 {{ reference.pageNumber }} 页</span>
          </div>
        </WatercolorBox>
      </button>
    </div>
  </details>
</template>

<style scoped>
.reference-sources { margin: 20px 0 8px; color: rgb(var(--base-text-color)); }
summary { cursor: pointer; width: fit-content; font-size: 14px; }
summary > span { margin-left: 6px; font-variant-numeric: tabular-nums; opacity: .65; }
.reference-sources__grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 240px), 1fr)); gap: 16px; margin-top: 16px; }
.reference-source { min-width: 0; align-self: start; padding: 0; border: none; background: transparent; color: inherit; text-align: left; cursor: pointer; border-radius: 8px; transition: transform .18s ease; }
.reference-source:hover { transform: translateY(-2px); }
.reference-source:focus-visible, summary:focus-visible { outline: 2px solid rgb(var(--primary-color)); outline-offset: 5px; }
.reference-source__body { padding: 16px; }
.reference-source__heading { display: flex; align-items: baseline; gap: 10px; font-size: 14px; }
.reference-source__heading strong { flex: 1; min-width: 0; overflow-wrap: anywhere; font-weight: 500; line-height: 1.6; }
.reference-source__number, .reference-source__page { font-size: 12px; opacity: .7; }
.reference-source p { display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; margin: 10px 0 0; line-height: 1.7; font-size: 14px; opacity: .8; overflow-wrap: anywhere; }
.reference-source__page { display: block; margin-top: 8px; }
@media (prefers-reduced-motion: reduce) { .reference-source { transition: none; } .reference-source:hover { transform: none; } }
</style>
