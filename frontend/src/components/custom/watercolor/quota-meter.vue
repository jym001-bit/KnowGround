<script setup lang="ts">
import { computed } from 'vue';
import MarkerBar from './marker-bar.vue';
const props = withDefaults(defineProps<{ label: string; quota: Api.User.UsageQuota; seed?: number }>(), { seed: 1 });
const ratio = computed(() => props.quota.limitTokens > 0 ? props.quota.usedTokens / props.quota.limitTokens : 0);
const exhausted = computed(() => props.quota.enabled && props.quota.remainingTokens <= 0);
</script>

<template>
  <section class="quota-meter" :aria-label="`${label} 今日额度`">
    <div class="quota-meter__heading"><h3>{{ label }}</h3><span>{{ quota.enabled ? '今日额度' : '未启用日配额' }}</span></div>
    <div class="quota-meter__value">{{ quota.usedTokens.toLocaleString() }}<small>已用 tokens</small></div>
    <MarkerBar v-if="quota.enabled" :fraction="ratio" :label="`${label} 今日已用比例`" :seed="seed" :color="exhausted ? '#bd7c65' : '#ad9a7e'" />
    <div class="quota-meter__footer">
      <span v-if="quota.enabled" :class="{ 'quota-meter__exhausted': exhausted }">{{ exhausted ? '今日额度已用完' : `剩余 ${quota.remainingTokens.toLocaleString()}` }}</span>
      <span v-else>今日不设 Token 上限</span>
      <span>{{ quota.requestCount.toLocaleString() }} 次请求</span>
    </div>
    <p v-if="quota.enabled" class="quota-meter__limit">每日上限 {{ quota.limitTokens.toLocaleString() }} tokens</p>
  </section>
</template>

<style scoped>
.quota-meter { border: 1px solid rgb(var(--base-text-color) / .14); border-radius: 16px; background: rgb(var(--container-bg-color)); padding: 22px; color: rgb(var(--base-text-color)); min-width: 0; }
.quota-meter__heading, .quota-meter__footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; flex-wrap: wrap; }
.quota-meter__heading h3 { margin: 0; font-size: 16px; font-weight: 500; letter-spacing: .01em; }
.quota-meter__heading > span, .quota-meter__limit { font-size: 12px; opacity: .72; }
.quota-meter__value { margin-top: 20px; font-size: clamp(28px, 3vw, 36px); font-weight: 500; letter-spacing: -.025em; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
.quota-meter__value small { display: inline-block; margin-left: 10px; font-size: 13px; font-weight: 400; opacity: .72; }
.quota-meter__footer { margin-top: 18px; font-size: 14px; font-variant-numeric: tabular-nums; }
.quota-meter__limit { margin: 8px 0 0; }
.quota-meter__exhausted { color: #ab624b; }
:global(.dark) .quota-meter__exhausted { color: #e0a18b; }
</style>
