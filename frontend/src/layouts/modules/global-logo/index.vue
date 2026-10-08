<script setup lang="ts">
import { $t } from '@/locales';

defineOptions({
  name: 'GlobalLogo'
});

interface Props {
  /** Whether to show the title */
  showTitle?: boolean;
}

withDefaults(defineProps<Props>(), {
  showTitle: true
});
</script>

<template>
  <RouterLink to="/" class="global-brand w-full flex-center nowrap-hidden">
    <span class="global-brand-mark">K<span /></span>
    <h2 v-show="showTitle" class="global-brand-name transition duration-300 ease-in-out">
      {{ $t('system.title') }}<span class="brand-english">KnowGround</span>
    </h2>
  </RouterLink>
</template>

<style scoped>
.global-brand { gap: 10px; text-decoration: none; color: #5f584d; }
.global-brand-mark { position: relative; flex: none; width: 35px; height: 35px; display: grid; place-items: center; border: 1px solid #cfc6b6; border-radius: 10px; background: #e7e0d2; color: #60584c; font: 600 21px/1 Georgia, serif; letter-spacing: -.08em; }
.global-brand-mark span { position: absolute; width: 4px; height: 4px; top: 8px; right: 7px; border-radius: 50%; background: #9d8e78; }
.global-brand-name { font-size: 19px; line-height: 1.03; font-weight: 650; letter-spacing: -.04em; }
.brand-english {
  display: block;
  font-size: 10px;
  letter-spacing: .08em;
  font-weight: 500;
  color: #8b8376;
}
:global(.dark) .global-brand { color: #e8e1d7; }
:global(.dark) .global-brand-mark { background: #34312b; color: #e8e1d7; border-color: #655c4e; }
</style>
