<script setup lang="tsx">
import { computed, onMounted, ref, watch } from 'vue';
import { NButton, NCard, NEmpty, NTag } from 'naive-ui';
import type { ECOption } from '@/hooks/common/echarts';
import { useEcharts } from '@/hooks/common/echarts';

const trendWindow = ref<7 | 30>(7);
const rankingScope = ref<'llm' | 'embedding'>('llm');
const rankQuery = ref('');
const overviewError = ref(false);
const filteredRankings = computed(
  () =>
    (rankingScope.value === 'llm' ? overview.value?.llmRankings : overview.value?.embeddingRankings)?.filter((item) =>
      item.username.toLowerCase().includes(rankQuery.value.trim().toLowerCase())
    ) || []
);
const overviewLoading = ref(false);
const overview = ref<Api.Admin.UsageOverview | null>(null);
const rateLimitLoading = ref(false);
const rateLimitSaving = ref(false);
const rateLimits = ref<Api.Admin.RateLimitSettings | null>(null);

async function getOverview() {
  overviewLoading.value = true;
  overviewError.value = false;
  try {
    const { error, data } = await request<Api.Admin.UsageOverview>({
      url: '/admin/usage/overview',
      params: { days: trendWindow.value }
    });
    overviewError.value = Boolean(error);
    if (!error && data) overview.value = data;
  } catch {
    overviewError.value = true;
  } finally {
    overviewLoading.value = false;
  }
}

function createDefaultRateLimitSettings(): Api.Admin.RateLimitSettings {
  return {
    chatMessage: {
      max: 30,
      windowSeconds: 60
    },
    llmGlobalToken: {
      minuteMax: 120000,
      minuteWindowSeconds: 60,
      dayMax: 8000000,
      dayWindowSeconds: 86400
    },
    embeddingUploadToken: {
      minuteMax: 200000,
      minuteWindowSeconds: 60,
      dayMax: 20000000,
      dayWindowSeconds: 86400
    },
    embeddingQueryRequest: {
      minuteMax: 60,
      minuteWindowSeconds: 60,
      dayMax: 5000,
      dayWindowSeconds: 86400
    },
    embeddingQueryGlobalToken: {
      minuteMax: 60000,
      minuteWindowSeconds: 60,
      dayMax: 4000000,
      dayWindowSeconds: 86400
    }
  };
}

function cloneTokenBudgetLimit(payload?: Partial<Api.Admin.TokenBudgetLimit> | null): Api.Admin.TokenBudgetLimit {
  return {
    minuteMax: Number(payload?.minuteMax || 0),
    minuteWindowSeconds: Number(payload?.minuteWindowSeconds || 0),
    dayMax: Number(payload?.dayMax || 0),
    dayWindowSeconds: Number(payload?.dayWindowSeconds || 0)
  };
}

function cloneDualWindowLimit(payload?: Partial<Api.Admin.DualWindowLimit> | null): Api.Admin.DualWindowLimit {
  return {
    minuteMax: Number(payload?.minuteMax || 0),
    minuteWindowSeconds: Number(payload?.minuteWindowSeconds || 0),
    dayMax: Number(payload?.dayMax || 0),
    dayWindowSeconds: Number(payload?.dayWindowSeconds || 0)
  };
}

function cloneRateLimitSettings(payload?: Api.Admin.RateLimitSettings | null): Api.Admin.RateLimitSettings {
  return {
    chatMessage: {
      max: Number(payload?.chatMessage.max || 0),
      windowSeconds: Number(payload?.chatMessage.windowSeconds || 0)
    },
    llmGlobalToken: cloneTokenBudgetLimit(payload?.llmGlobalToken),
    embeddingUploadToken: cloneTokenBudgetLimit(payload?.embeddingUploadToken),
    embeddingQueryRequest: cloneDualWindowLimit(payload?.embeddingQueryRequest),
    embeddingQueryGlobalToken: cloneTokenBudgetLimit(payload?.embeddingQueryGlobalToken)
  };
}

async function getRateLimits() {
  rateLimitLoading.value = true;
  const { error, data } = await request<Api.Admin.RateLimitSettings>({
    url: '/admin/rate-limits'
  });

  if (!error && data) {
    rateLimits.value = cloneRateLimitSettings(data);
  } else if (!rateLimits.value) {
    rateLimits.value = createDefaultRateLimitSettings();
  }
  rateLimitLoading.value = false;
}

async function submitRateLimits() {
  if (!rateLimits.value) {
    return;
  }

  rateLimitSaving.value = true;
  const { error, data } = await request<Api.Admin.RateLimitSettings>({
    url: '/admin/rate-limits',
    method: 'put',
    data: cloneRateLimitSettings(rateLimits.value)
  });

  if (!error && data) {
    rateLimits.value = cloneRateLimitSettings(data);
    window.$message?.success('限流配置已更新');
  }

  rateLimitSaving.value = false;
}

const { domRef: trendChartRef, updateOptions } = useEcharts<ECOption>(() => ({
  tooltip: {
    trigger: 'axis'
  },
  legend: {
    type: 'scroll',
    top: 0
  },
  grid: {
    top: 48,
    left: 24,
    right: 24,
    bottom: 24,
    containLabel: true
  },
  xAxis: {
    type: 'category',
    data: []
  },
  yAxis: [
    {
      type: 'value',
      name: 'Tokens'
    },
    {
      type: 'value',
      name: 'Requests'
    }
  ],
  series: []
}));

watch([overview, trendWindow], async () => {
  const trends = overview.value?.trends || [];
  await updateOptions(() => ({
    color: ['#287c65', '#99b89b', '#587889', '#839592', '#bac5c1'],
    tooltip: {
      trigger: 'axis'
    },
    legend: {
      type: 'scroll',
      top: 0
    },
    grid: {
      top: 48,
      left: 24,
      right: 24,
      bottom: 24,
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: trends.map((item) => dayjs(item.day).format('MM-DD'))
    },
    yAxis: [
      {
        type: 'value',
        name: 'Tokens'
      },
      {
        type: 'value',
        name: 'Requests'
      }
    ],
    series: [
      {
        name: 'LLM Tokens',
        itemStyle: { color: '#287c65' },
        lineStyle: { color: '#287c65' },
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.08 },
        data: trends.map((item) => item.llmUsedTokens)
      },
      {
        name: 'Embedding Tokens',
        itemStyle: { color: '#94b099' },
        lineStyle: { color: '#94b099' },
        type: 'line',
        smooth: true,
        showSymbol: false,
        areaStyle: { opacity: 0.08 },
        data: trends.map((item) => item.embeddingUsedTokens)
      },
      {
        name: 'Chat Messages',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          color: '#547889', opacity: 0.4
        },
        data: trends.map((item) => item.chatRequestCount)
      },
      {
        name: 'LLM Requests',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          color: '#839592', opacity: 0.32
        },
        data: trends.map((item) => item.llmRequestCount)
      },
      {
        name: 'Embedding Requests',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          color: '#bac5c1', opacity: 0.22
        },
        data: trends.map((item) => item.embeddingRequestCount)
      }
    ]
  }));
});

const alertCount = computed(() => overview.value?.alerts.length || 0);
const criticalAlertCount = computed(
  () => overview.value?.alerts.filter((item) => item.level === 'critical').length || 0
);
const todaySummary = computed(() => overview.value?.today);

function formatNumber(value?: number) {
  return value === undefined ? '—' : Number(value).toLocaleString('zh-CN');
}

function scopeLabel(scope: 'llm' | 'embedding') {
  return scope === 'llm' ? 'LLM' : 'Embedding';
}

function alertType(level: 'critical' | 'warning') {
  return level === 'critical' ? 'error' : 'warning';
}

onMounted(() => {
  getOverview();
  getRateLimits();
});
</script>

<template>
  <div class="usage-dashboard">
    <header class="dashboard-header">
      <div>
        <span class="dashboard-eyebrow">WORKSPACE / ANALYTICS</span>
        <h1>用量管理</h1>
        <p>掌握使用趋势，合理分配每一份资源。</p>
      </div>
      <NButton :loading="overviewLoading" @click="getOverview">刷新数据 ↻</NButton>
    </header>
    <div v-if="overviewError" class="dashboard-error" role="alert">
      用量加载失败{{ overview ? '，当前展示上次加载的数据' : '' }}。<NButton text @click="getOverview">重试</NButton>
    </div>
    <NSpin :show="overviewLoading">
      <div class="metric-grid">
        <article class="metric-card">
          <span class="metric-icon">↗</span>
          <div class="summary-label">今日聊天消息</div>
          <div class="summary-value">{{ formatNumber(todaySummary?.chatRequestCount) }}</div>
          <div class="summary-sub">通过限流的消息总数</div>
        </article>
        <article class="metric-card">
          <span class="metric-icon">✦</span>
          <div class="summary-label">今日 LLM 用量</div>
          <div class="summary-value">{{ formatNumber(todaySummary?.llmUsedTokens) }}<small>Tokens</small></div>
          <div class="summary-sub">{{ formatNumber(todaySummary?.llmRequestCount) }} 次模型请求</div>
        </article>
        <article class="metric-card">
          <span class="metric-icon">▤</span>
          <div class="summary-label">今日向量化用量</div>
          <div class="summary-value">{{ formatNumber(todaySummary?.embeddingUsedTokens) }}<small>Tokens</small></div>
          <div class="summary-sub">{{ formatNumber(todaySummary?.embeddingRequestCount) }} 次向量请求</div>
        </article>
        <article class="metric-card">
          <span class="metric-icon">◷</span>
          <div class="summary-label">今日额度告警</div>
          <div class="summary-value">{{ overview ? alertCount : '—' }}</div>
          <div class="summary-sub">{{ overview ? criticalAlertCount : '—' }} 项额度耗尽</div>
        </article>
      </div>
      <section class="dashboard-panel trend-panel">
        <header class="panel-header">
          <div>
            <h2>用量趋势</h2>
            <p>近 {{ trendWindow }} 天的 Token 消耗与请求情况</p>
          </div>
          <div class="period-switch">
            <NButton
              :type="trendWindow === 7 ? 'primary' : 'default'"
              :disabled="overviewLoading"
              @click="
                trendWindow = 7;
                getOverview();
              "
              >近 7 天</NButton
            ><NButton
              :type="trendWindow === 30 ? 'primary' : 'default'"
              :disabled="overviewLoading"
              @click="
                trendWindow = 30;
                getOverview();
              "
              >近 30 天</NButton
            >
          </div>
        </header>
        <div ref="trendChartRef" class="usage-chart" />
        <NEmpty v-if="overview && !overview.trends.length" class="chart-empty" description="所选时间段暂无用量" />
      </section>
      <section class="dashboard-panel">
        <header class="panel-header">
          <div>
            <h2>今日用户用量</h2>
            <p>按当前返回的用量排行，查看用户额度与请求次数</p>
          </div>
          <NSelect
            v-model:value="rankingScope"
            aria-label="用量类型"
            :options="[
              { label: 'LLM 用量', value: 'llm' },
              { label: 'Embedding 用量', value: 'embedding' }
            ]"
            class="scope-select"
          />
        </header>
        <NInput
          v-model:value="rankQuery"
          placeholder="搜索用户名"
          aria-label="搜索用户名"
          clearable
          class="rank-search"
        />
        <div class="ranking-table-scroll">
          <table class="ranking-table">
            <thead>
              <tr>
                <th>用户</th>
                <th>已用 Token</th>
                <th>额度上限</th>
                <th>剩余 Token</th>
                <th>请求次数</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in filteredRankings" :key="item.userId">
                <td>
                  <span class="user-initial">{{ item.username.slice(0, 1) }}</span
                  >{{ item.username }}
                </td>
                <td>{{ formatNumber(item.usedTokens) }}</td>
                <td>{{ formatNumber(item.limitTokens) }}</td>
                <td>{{ formatNumber(item.remainingTokens) }}</td>
                <td>{{ formatNumber(item.requestCount) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
        <NEmpty
          v-if="!filteredRankings.length"
          class="table-empty"
          :description="rankQuery ? '没有匹配的用户' : '暂无用量排行'"
        />
      </section>
      <section class="dashboard-panel">
        <header class="panel-header">
          <div>
            <h2>
              额度告警 <span class="alert-count">{{ overview ? alertCount : '—' }}</span>
            </h2>
            <p>关注接近上限或已耗尽的额度</p>
          </div>
        </header>
        <div v-if="overview?.alerts.length" class="alerts-grid">
          <article v-for="alert in overview.alerts" :key="`${alert.userId}-${alert.scope}`" class="alert-item">
            <div>
              <strong>{{ alert.username }}</strong
              ><NTag :type="alertType(alert.level)">{{ scopeLabel(alert.scope) }}</NTag>
            </div>
            <p>{{ alert.message }}</p>
            <span>已用 {{ formatNumber(alert.usedTokens) }} / {{ formatNumber(alert.limitTokens) }}</span>
          </article>
        </div>
        <NEmpty v-else description="暂无额度告警" class="table-empty" />
      </section>
    </NSpin>
    <details class="rate-details">
      <summary>调用限流配置 <span>展开管理消息频率与预算</span></summary>
      <NCard :bordered="false" size="small" class="card-wrapper">
        <template #header>调用限流配置</template>
        <template #header-extra>
          <div class="flex items-center gap-2">
            <span class="text-sm text-stone-400">保存后立即对新请求生效</span>
            <NButton type="primary" size="small" :loading="rateLimitSaving" @click="submitRateLimits">
              保存配置
            </NButton>
          </div>
        </template>

        <NSpin :show="rateLimitLoading">
          <div class="mb-4 rounded-2xl border border-stone-200 bg-stone-50 px-4 py-3 text-sm text-stone-500">
            设置消息频率与 Token 预算，保存后对新请求生效。
          </div>

          <div v-if="rateLimits" class="grid gap-4 xl:grid-cols-2">
            <div class="limit-card">
              <div class="limit-title">聊天消息</div>
              <div class="limit-grid">
                <div>
                  <div class="limit-label">次数上限</div>
                  <NInputNumber v-model:value="rateLimits.chatMessage.max" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">窗口秒数</div>
                  <NInputNumber v-model:value="rateLimits.chatMessage.windowSeconds" :min="1" class="w-full" />
                </div>
              </div>
            </div>

            <div class="limit-card">
              <div class="limit-title">LLM 全网 Token 预算</div>
              <div class="limit-grid">
                <div>
                  <div class="limit-label">分钟 Token 上限</div>
                  <NInputNumber v-model:value="rateLimits.llmGlobalToken.minuteMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">分钟窗口秒数</div>
                  <NInputNumber v-model:value="rateLimits.llmGlobalToken.minuteWindowSeconds" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">日 Token 上限</div>
                  <NInputNumber v-model:value="rateLimits.llmGlobalToken.dayMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">日窗口秒数</div>
                  <NInputNumber v-model:value="rateLimits.llmGlobalToken.dayWindowSeconds" :min="1" class="w-full" />
                </div>
              </div>
            </div>

            <div class="limit-card">
              <div class="limit-title">Embedding 上传 Token 预算</div>
              <div class="limit-grid">
                <div>
                  <div class="limit-label">分钟 Token 上限</div>
                  <NInputNumber v-model:value="rateLimits.embeddingUploadToken.minuteMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">分钟窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingUploadToken.minuteWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
                <div>
                  <div class="limit-label">日 Token 上限</div>
                  <NInputNumber v-model:value="rateLimits.embeddingUploadToken.dayMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">日窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingUploadToken.dayWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
              </div>
            </div>

            <div class="limit-card">
              <div class="limit-title">Embedding 查询</div>
              <div class="limit-grid">
                <div>
                  <div class="limit-label">单用户分钟次数</div>
                  <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.minuteMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">分钟窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingQueryRequest.minuteWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
                <div>
                  <div class="limit-label">单用户日次数</div>
                  <NInputNumber v-model:value="rateLimits.embeddingQueryRequest.dayMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">日窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingQueryRequest.dayWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
                <div>
                  <div class="limit-label">全网分钟 Token</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingQueryGlobalToken.minuteMax"
                    :min="1"
                    class="w-full"
                  />
                </div>
                <div>
                  <div class="limit-label">查询分钟窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingQueryGlobalToken.minuteWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
                <div>
                  <div class="limit-label">全网日 Token</div>
                  <NInputNumber v-model:value="rateLimits.embeddingQueryGlobalToken.dayMax" :min="1" class="w-full" />
                </div>
                <div>
                  <div class="limit-label">查询日窗口秒数</div>
                  <NInputNumber
                    v-model:value="rateLimits.embeddingQueryGlobalToken.dayWindowSeconds"
                    :min="1"
                    class="w-full"
                  />
                </div>
              </div>
            </div>
          </div>
          <NEmpty v-else size="small" description="暂无限流配置" />
        </NSpin>
      </NCard>
    </details>
  </div>
</template>
<style scoped>
.usage-dashboard {
  --dash-text: #23372d;
  --dash-muted: #647267;
  --dash-line: #dfe5df;
  --dash-card: #fff;
  padding: 24px;
  overflow-y: auto;
  color: var(--dash-text);
}
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 24px;
}
.dashboard-eyebrow {
  font-size: 11px;
  letter-spacing: 1.5px;
  color: var(--dash-muted);
}
.dashboard-header h1 {
  font-size: 28px;
  font-weight: 600;
  margin: 6px 0;
}
.dashboard-header p,
.panel-header p {
  color: var(--dash-muted);
  font-size: 14px;
  line-height: 1.7;
}
.metric-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 20px;
}
.metric-card {
  border: 1px solid var(--dash-line);
  background: linear-gradient(180deg, var(--dash-card), #f3f6f1);
  border-radius: 14px;
  padding: 20px;
}
.metric-icon {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: #eef3ea;
  border: 1px solid #dce6d8;
  color: #487153;
  border-radius: 8px;
  margin-bottom: 12px;
}
.summary-label {
  font-size: 15px;
  color: var(--dash-muted);
}
.summary-value {
  margin: 12px 0 8px;
  font-size: clamp(24px, 2.2vw, 34px);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.summary-value small {
  font-size: 11px;
  font-weight: 400;
  margin-left: 6px;
}
.summary-sub {
  font-size: 13px;
  color: var(--dash-muted);
}
.dashboard-panel {
  position: relative;
  background: var(--dash-card);
  border: 1px solid var(--dash-line);
  border-radius: 14px;
  padding: 22px;
  margin-bottom: 20px;
}
.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 20px;
}
.panel-header h2 {
  font-size: 18px;
  font-weight: 600;
  margin-bottom: 5px;
}
.period-switch {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.usage-chart {
  height: 350px;
  width: 100%;
}
.chart-empty {
  position: absolute;
  top: 50%;
  left: 42%;
}
.scope-select {
  width: 180px;
  flex-shrink: 0;
}
.rank-search {
  max-width: 290px;
  margin-bottom: 16px;
}
.ranking-table-scroll {
  overflow-x: auto;
}
.ranking-table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
  font-size: 15px;
}
.ranking-table th {
  font-size: 13px;
  font-weight: 500;
  color: var(--dash-muted);
  background: #f6f8f4;
  text-align: left;
  padding: 12px 14px;
}
.ranking-table td {
  padding: 16px 14px;
  border-bottom: 1px solid var(--dash-line);
  font-variant-numeric: tabular-nums;
}
.ranking-table tr:last-child td {
  border-bottom: 0;
}
.user-initial {
  display: inline-grid;
  place-items: center;
  width: 30px;
  height: 30px;
  background: #edf3e8;
  border: 1px solid #dfe8db;
  border-radius: 8px;
  color: #3e6d4a;
  margin-right: 10px;
}
.table-empty {
  padding: 24px 0;
}
.alerts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(300px, 100%), 1fr));
  gap: 12px;
}
.alert-item {
  padding: 16px;
  border: 1px solid var(--dash-line);
  border-radius: 10px;
}
.alert-item > div {
  display: flex;
  justify-content: space-between;
}
.alert-item p {
  margin: 10px 0;
  font-size: 15px;
}
.alert-item > span {
  font-size: 13px;
  color: var(--dash-muted);
}
.alert-count {
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 8px;
  background: #f2e7d5;
  color: #7d5d28;
}
.rate-details {
  border: 1px solid var(--dash-line);
  border-radius: 14px;
  background: var(--dash-card);
}
.rate-details summary {
  cursor: pointer;
  padding: 22px;
  font-size: 17px;
  font-weight: 500;
}
.rate-details summary span {
  font-size: 13px;
  color: var(--dash-muted);
  margin-left: 12px;
}
.limit-card {
  border: 1px solid var(--dash-line);
  border-radius: 10px;
  padding: 20px;
}
.limit-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 18px;
}
.limit-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.limit-label {
  font-size: 14px;
  color: var(--dash-muted);
  margin-bottom: 8px;
}
.dashboard-error {
  display: flex;
  justify-content: space-between;
  background: #fff0e7;
  color: #85441e;
  padding: 14px;
  border-radius: 10px;
  margin-bottom: 16px;
}
.dark .usage-dashboard {
  --dash-text: #e6eee4;
  --dash-muted: #abb9aa;
  --dash-line: #354536;
  --dash-card: #202a22;
}
.dark .metric-card {
  background: linear-gradient(180deg, #263328, #202a22);
}
.dark .ranking-table th {
  background: #28372a;
}
@media (max-width: 1100px) {
  .metric-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 650px) {
  .usage-dashboard {
    padding: 16px 10px;
  }
  .metric-grid {
    gap: 10px;
  }
  .metric-card {
    padding: 14px;
  }
  .summary-label {
    font-size: 13px;
  }
  .summary-value {
    font-size: 24px;
  }
  .summary-value small {
    display: block;
    margin-left: 0;
  }
  .dashboard-panel {
    padding: 16px;
  }
  .panel-header {
    flex-wrap: wrap;
    gap: 12px;
  }
  .usage-chart {
    height: 300px;
  }
  .limit-grid {
    grid-template-columns: 1fr;
  }
  .rate-details summary span {
    display: block;
    margin: 8px 0 0;
  }
}
</style>
