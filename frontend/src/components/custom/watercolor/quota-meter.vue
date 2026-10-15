<script setup lang="ts">
import { computed } from 'vue';
import MarkerBar from './marker-bar.vue';
const props = withDefaults(defineProps<{ label: string; quota: Api.User.UsageQuota; seed?: number; admin?: boolean }>(), { seed: 1, admin: false });
const ratio = computed(() => props.quota.limitTokens > 0 ? props.quota.usedTokens / props.quota.limitTokens : 0);
const exhausted = computed(() => props.quota.enabled && props.quota.usedTokens >= props.quota.limitTokens);
</script>

<template>
  <section class="quota-meter" :aria-label="`${label} 今日消耗`">
    <div class="quota-meter__heading"><h3>{{ label }}</h3><span>今日消耗</span></div>
    <div class="quota-meter__value">{{ quota.usedTokens.toLocaleString() }}<small>tokens</small></div>
    <MarkerBar v-if="quota.enabled" :fraction="ratio" :label="`${label} 今日已用比例`" :seed="seed" :color="exhausted ? '#bd7c65' : '#ad9a7e'" />
    <div class="quota-meter__footer">
      <span v-if="quota.enabled" :class="{ 'quota-meter__exhausted': exhausted }">{{ exhausted ? '今日额度已用完' : `今日剩余 ${Math.max(0, quota.limitTokens - quota.usedTokens).toLocaleString()}` }}</span>
      <span v-else-if="admin">管理员免扣余额</span>
      <span v-else>余额 {{ quota.remainingTokens.toLocaleString() }}</span>
      <span>今日 {{ quota.requestCount.toLocaleString() }} 次请求</span>
    </div>
    <p class="quota-meter__limit">{{ quota.enabled ? `每日上限 ${quota.limitTokens.toLocaleString()} tokens` : '无个人每日 Token 上限' }}</p>
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
