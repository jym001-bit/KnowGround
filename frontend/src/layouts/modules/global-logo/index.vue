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
  <RouterLink to="/" class="w-full flex-center nowrap-hidden">
    <SystemLogo class="text-32px text-primary" />
    <h2 v-show="showTitle" class="pl-8px text-16px text-primary font-bold transition duration-300 ease-in-out">
      {{ $t('system.title') }}<span class="brand-english">KnowGround</span>
    </h2>
  </RouterLink>
</template>

<style scoped>
.brand-english {
  display: block;
  font-size: 10px;
  letter-spacing: 1px;
  font-weight: 500;
  color: #73827d;
}
</style>
