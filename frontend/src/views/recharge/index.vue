<script setup lang="ts">
import { computed, onMounted, ref, h } from 'vue';
import type { DataTableColumns } from 'naive-ui';
import {
  NCard,
  NButton,
  NInputNumber,
  NModal,
  NSpace,
  NTag,
  NEmpty,
  NSpin,
  NDataTable,
  NTabs,
  NTabPane
} from 'naive-ui';
import QRCode from 'qrcode.vue';
import {
  fetchRechargePackages,
  fetchCreateRechargeOrder,
  fetchRechargeOrderDetail,
  fetchRechargeOrders
} from '@/service/api';
import { useAuthStore } from '@/store/modules/auth';

const TOKEN_UNIT = 10000;

const authStore = useAuthStore();
const loading = ref(false);
const packageLoadFailed = ref(false);
const paymentAmount = ref(0);
const packages = ref<Api.Recharge.Package[]>([]);
const selectedPackageId = ref<number | null>(null);
const customAmountYuan = ref<number | null>(null); // 自定义充值金额（元）
const isCustomAmount = ref(false);

// 充值记录相关
const orderListLoading = ref(false);
const orders = ref<Api.Recharge.Order[]>([]);
const activeTab = ref<string>('all');

// 支付弹窗相关
const showPayModal = ref(false);
const payLoading = ref(false);
const orderInfo = ref<Api.Recharge.OrderInfo | null>(null);
const currentTradeNo = ref<string>('');

// 获取当前订单金额（分）
const getOrderAmount = computed(() => {
  if (isCustomAmount.value && customAmountYuan.value) {
    return Math.round(customAmountYuan.value * 100);
  }
  if (selectedPackageId.value) {
    const pkg = packages.value.find((p) => p.id === selectedPackageId.value);
    return pkg?.packagePrice || 0;
  }
  return 0;
});

// 验证自定义充值金额是否有效
const isValidCustomAmount = computed(() => {
  if (!isCustomAmount.value || !customAmountYuan.value) return false;
  return Number.isFinite(customAmountYuan.value) && customAmountYuan.value >= 0.01;
});

function formatTokenWan(value: number) {
  const wan = value / TOKEN_UNIT;
  const hasDecimal = Math.abs(wan - Math.round(wan)) > 0.000001;
  const formatted = wan.toLocaleString('zh-CN', {
    minimumFractionDigits: hasDecimal ? 2 : 0,
    maximumFractionDigits: hasDecimal ? 2 : 0
  });
  return `${formatted} 万`;
}

function packageBenefitLines(content?: string | null) {
  return (content || '')
    .split('\n')
    .map((item) => item.trim())
    .filter(Boolean);
}

// 获取充值套餐列表
const getPackages = async () => {
  loading.value = true;
  packageLoadFailed.value = false;
  try {
    const { error, data } = await fetchRechargePackages();
    packageLoadFailed.value = Boolean(error);
    if (!error && data) packages.value = data.filter((pkg) => pkg.enabled);
  } catch {
    packageLoadFailed.value = true;
  } finally {
    loading.value = false;
  }
};

// 获取充值记录列表
const getOrders = async () => {
  orderListLoading.value = true;
  try {
    const status = activeTab.value === 'all' ? undefined : (activeTab.value as any);
    const { error, data } = await fetchRechargeOrders(status);
    if (!error && data) {
      orders.value = data;
    }
  } finally {
    orderListLoading.value = false;
  }
};

// 切换标签页时刷新订单列表
const handleTabChange = (tab: string) => {
  activeTab.value = tab;
  getOrders();
};

// 选择套餐
const selectPackage = (packageId: number) => {
  selectedPackageId.value = packageId;
  isCustomAmount.value = false;
  customAmountYuan.value = null;
};

// 选择自定义金额
const selectCustomAmount = () => {
  isCustomAmount.value = true;
  selectedPackageId.value = null;
};

// 创建订单
const createOrder = async () => {
  if (payLoading.value) return;
  if (!isCustomAmount.value && !selectedPackageId.value) {
    window.$message?.warning('请选择充值套餐或输入充值金额');
    return;
  }

  if (isCustomAmount.value && !isValidCustomAmount.value) {
    window.$message?.warning('请输入有效的充值金额，至少 0.01 元');
    return;
  }

  payLoading.value = true;
  const amount = getOrderAmount.value;
  try {
    const { error, data } = await fetchCreateRechargeOrder(
      isCustomAmount.value ? undefined : selectedPackageId.value || undefined,
      isCustomAmount.value ? getOrderAmount.value : undefined
    );

    if (!error && data) {
      paymentAmount.value = amount;
      orderInfo.value = data;
      currentTradeNo.value = data.outTradeNo;
      showPayModal.value = true;
      window.$message?.success('订单创建成功，请扫码支付');
    }
  } finally {
    payLoading.value = false;
  }
};

// 检查支付状态
const checkPayStatus = async () => {
  if (!currentTradeNo.value) return;

  try {
    const { error, data } = await fetchRechargeOrderDetail(currentTradeNo.value);
    if (!error && data) {
      if (data.status === 'SUCCEED') {
        window.$message?.success('充值成功！');
        showPayModal.value = false;
        // 刷新用户信息
        await authStore.initUserInfo();
        // 刷新订单列表
        getOrders();
      } else if (data.status === 'NOT_PAY' || data.status === 'PAYING') {
        window.$message?.info('尚未支付，请继续扫码支付');
      } else {
        window.$message?.error('支付失败，请重试');
      }
    }
  } catch (e) {
    window.$message?.error('查询支付状态失败');
  }
};

// 定义订单表格列
const orderColumns = computed<DataTableColumns<Api.Recharge.Order>>(() => [
  {
    key: 'tradeNo',
    title: '订单号',
    width: 200,
    ellipsis: {
      tooltip: true
    }
  },
  {
    key: 'description',
    title: '充值说明',
    minWidth: 200,
    ellipsis: {
      tooltip: true
    }
  },
  {
    key: 'amount',
    title: '充值金额',
    width: 120,
    render: (row) => `¥${(row.amount / 100).toFixed(2)}`
  },
  {
    key: 'llmToken',
    title: 'LLM Token（万）',
    width: 120,
    render: (row) => formatTokenWan(row.llmToken)
  },
  {
    key: 'embeddingToken',
    title: 'Embedding Token（万）',
    width: 140,
    render: (row) => formatTokenWan(row.embeddingToken)
  },
  {
    key: 'status',
    title: '状态',
    width: 100,
    render: (row) => {
      const statusMap: Record<string, { text: string; type: any }> = {
        NOT_PAY: { text: '未支付', type: 'default' },
        PAYING: { text: '支付中', type: 'warning' },
        SUCCEED: { text: '支付成功', type: 'success' },
        FAIL: { text: '支付失败', type: 'error' },
        CANCELLED: { text: '已取消', type: 'default' }
      };
      const status = statusMap[row.status] || { text: '未知', type: 'default' };
      return h(NTag, { type: status.type }, () => status.text);
    }
  },
  {
    key: 'payTime',
    title: '支付时间',
    width: 180,
    render: (row) => (row.payTime ? new Date(row.payTime).toLocaleString('zh-CN') : '-')
  },
  {
    key: 'createdAt',
    title: '创建时间',
    width: 180,
    render: (row) => new Date(row.createdAt).toLocaleString('zh-CN')
  }
]);

onMounted(() => {
  getPackages();
  getOrders();
});
</script>

<template>
  <div class="pricing-page">
    <section class="pricing-section" aria-labelledby="pricing-title">
      <header class="pricing-heading">
        <span class="pricing-eyebrow">KNOWGROUND · 为知识探索续航</span>
        <h1 id="pricing-title">为每一次探索，<span>选择更多可能</span></h1>
        <p>选择适合你的用量，让文档理解与知识问答持续发生。</p>
        <div class="billing-switch" role="group" aria-label="充值方式">
          <span class="switch-indicator" :class="{ 'is-custom': isCustomAmount }" aria-hidden="true" />
          <button type="button" :aria-pressed="!isCustomAmount" @click="isCustomAmount = false">套餐充值</button>
          <button type="button" :aria-pressed="isCustomAmount" @click="selectCustomAmount">
            自定义金额 <small>灵活</small>
          </button>
        </div>
        <div class="billing-note">一次性充值 <i /> 微信扫码支付</div>
      </header>
      <NSpin :show="loading && !isCustomAmount">
        <div v-if="isCustomAmount" class="custom-panel">
          <div class="custom-intro">
            <span class="plan-symbol">↗</span>
            <h2>按你的节奏，灵活充值</h2>
            <p>输入需要充值的金额，按当前规则折算 Token 用量。</p>
            <ul>
              <li>支持精确到分的充值金额</li>
              <li>通过微信扫码完成支付</li>
              <li>在下方随时查看充值记录</li>
            </ul>
          </div>
          <div class="custom-form">
            <label>充值金额（元）</label
            ><NInputNumber
              v-model:value="customAmountYuan"
              :min="0.01"
              :step="1"
              :precision="2"
              :input-props="{ 'aria-label': '充值金额（元）' }"
              placeholder="输入充值金额"
              size="large"
            />
            <p>最低充值 ¥0.01，实际 Token 额度以系统折算为准。</p>
            <div class="custom-total">
              <span>本次支付</span><strong>¥{{ (getOrderAmount / 100).toFixed(2) }}</strong>
            </div>
            <NButton
              type="primary"
              size="large"
              block
              :disabled="!isValidCustomAmount"
              :loading="payLoading"
              @click="createOrder"
              >确认金额，去支付 ↗</NButton
            >
          </div>
        </div>
        <div v-else-if="packageLoadFailed" class="pricing-empty" role="alert">
          <NEmpty description="套餐加载失败，请稍后重试" /><NButton secondary @click="getPackages">重新加载</NButton>
        </div>
        <div v-else-if="packages.length" class="plans-grid">
          <article
            v-for="(pkg, index) in packages"
            :key="pkg.id"
            class="plan-card"
            :class="{ selected: selectedPackageId === pkg.id }"
            :style="{ '--reveal-delay': `${Math.min(index, 5) * 90}ms` }"
          >
            <div class="plan-top">
              <span class="plan-symbol">{{ String(index + 1).padStart(2, '0') }}</span
              ><span v-if="selectedPackageId === pkg.id" class="selected-badge">✓ 当前选择</span
              ><span v-else class="plan-kind">TOKEN PACKAGE</span>
            </div>
            <h2>{{ pkg.packageName }}</h2>
            <p class="plan-description">{{ pkg.packageDesc || '为知识问答与文档处理补充用量。' }}</p>
            <div class="plan-price">
              <span>¥</span><strong>{{ (pkg.packagePrice / 100).toFixed(2) }}</strong
              ><small>/ 次</small>
            </div>
            <NButton
              v-if="selectedPackageId === pkg.id"
              type="primary"
              block
              size="large"
              :loading="payLoading"
              @click="createOrder"
              >确认套餐，去支付 ↗</NButton
            >
            <NButton
              v-else
              block
              size="large"
              :disabled="payLoading"
              :aria-label="`选择${pkg.packageName}`"
              @click="selectPackage(pkg.id)"
              >选择此套餐 <span class="button-arrow">↗</span></NButton
            >
            <div class="plan-quotas">
              <div>
                <span class="quota-icon">✦</span><span>智能问答<small>LLM Token</small></span
                ><strong>{{ formatTokenWan(pkg.llmToken) }}</strong>
              </div>
              <div>
                <span class="quota-icon">▤</span><span>文档向量化<small>Embedding Token</small></span
                ><strong>{{ formatTokenWan(pkg.embeddingToken) }}</strong>
              </div>
            </div>
            <div class="plan-benefits">
              <h3>套餐说明</h3>
              <ul v-if="packageBenefitLines(pkg.packageBenefit).length">
                <li
                  v-for="(line, benefitIndex) in packageBenefitLines(pkg.packageBenefit)"
                  :key="`${pkg.id}-${benefitIndex}`"
                >
                  <span aria-hidden="true">✓</span>{{ line }}
                </li>
              </ul>
              <p v-else class="benefit-fallback">包含以上问答与向量化额度，按实际使用消耗。</p>
            </div>
          </article>
        </div>
        <div v-else class="pricing-empty">
          <NEmpty :description="loading ? '正在获取充值套餐…' : '暂无可用套餐'" /><NButton
            v-if="!loading"
            secondary
            @click="selectCustomAmount"
            >使用自定义金额</NButton
          >
        </div>
      </NSpin>
      <div class="pricing-footnote"><span>◇</span> 先选择，再确认支付。所有充值记录均可在下方查看。</div>
    </section>

    <!-- 充值记录列表 -->
    <NCard title="充值记录" :bordered="false" size="large" class="order-history card-wrapper">
      <NTabs v-model:value="activeTab" @update:value="handleTabChange">
        <NTabPane name="all" tab="全部订单" />
        <NTabPane name="NOT_PAY" tab="未支付" />
        <NTabPane name="PAYING" tab="支付中" />
        <NTabPane name="SUCCEED" tab="支付成功" />
        <NTabPane name="FAIL" tab="支付失败" />
      </NTabs>

      <NSpin :show="orderListLoading">
        <NDataTable
          v-if="orders.length > 0"
          :columns="orderColumns"
          :data="orders"
          :loading="orderListLoading"
          :scroll-x="1200"
          class="mt-4"
        />
        <NEmpty v-else description="暂无充值记录" />
      </NSpin>
    </NCard>

    <!-- 支付二维码弹窗 -->
    <NModal
      v-model:show="showPayModal"
      preset="dialog"
      title="扫码支付"
      :show-icon="false"
      style="width: min(480px, calc(100vw - 32px))"
    >
      <div class="flex flex-col items-center gap-6 py-6">
        <div v-if="orderInfo" class="flex flex-col items-center gap-4">
          <div class="rounded-lg border-2 border-primary bg-white p-6">
            <QRCode
              :value="orderInfo.prePayId"
              :size="200"
              :level="'H'"
              include-margin
              render-as="svg"
              foreground="#000000"
              background="#ffffff"
            />
          </div>
          <div class="text-center">
            <p class="text-lg font-semibold">请使用微信扫码支付</p>
            <p class="mt-2 text-sm text-stone-500">订单号：{{ currentTradeNo }}</p>
            <p class="mt-1 text-sm text-stone-500">支付金额：¥{{ (paymentAmount / 100).toFixed(2) }}</p>
          </div>
        </div>

        <NSpace vertical class="w-full px-8">
          <NButton type="primary" size="large" block @click="checkPayStatus"> 我已完成支付 </NButton>
        </NSpace>
      </div>
    </NModal>
  </div>
</template>

<style scoped>
.pricing-page {
  --price-ink: #253b31;
  --price-muted: #758479;
  --price-line: #dde6dd;
  --price-card: #fff;
  --price-accent: #167565;
  padding: 28px 24px;
  overflow-y: auto;
  color: var(--price-ink);
}
.pricing-section {
  width: min(1120px, 100%);
  margin: 0 auto;
  position: relative;
}
.pricing-section::before {
  content: '';
  position: absolute;
  inset: 90px 5% 60px;
  pointer-events: none;
  background: radial-gradient(ellipse, #b4d8bd65, transparent 70%);
  z-index: 0;
}
.pricing-heading,
.plans-grid,
.custom-panel,
.pricing-footnote {
  position: relative;
}
.pricing-heading {
  text-align: center;
  margin-bottom: 30px;
}
.pricing-eyebrow {
  font-size: 10px;
  letter-spacing: 2px;
  color: var(--price-muted);
}
.pricing-heading h1 {
  font-size: clamp(25px, 3vw, 38px);
  font-weight: 600;
  letter-spacing: -1px;
  margin: 16px 0;
  line-height: 1.6;
}
.pricing-heading h1 > span {
  display: inline-block;
  padding: 0 10px;
  border: 1px dashed #86b59a;
  background: #e4f0e4;
  border-radius: 10px;
  color: #26765b;
}
.pricing-heading > p {
  font-size: 13px;
  color: var(--price-muted);
  margin: 0 0 24px;
}
.billing-switch {
  display: inline-grid;
  grid-template-columns: 1fr 1fr;
  position: relative;
  padding: 5px;
  border: 1px solid var(--price-line);
  border-radius: 40px;
  background: var(--price-card);
}
.billing-switch button {
  position: relative;
  padding: 10px 20px;
  background: transparent;
  border-radius: 30px;
  cursor: pointer;
  color: var(--price-muted);
  font-size: 13px;
  transition: color 0.2s;
}
.billing-switch button[aria-pressed='true'] {
  color: #fff;
}
.billing-switch small {
  font-size: 9px;
  padding: 2px 5px;
  border-radius: 8px;
  background: #edf5ea;
  color: #3d7155;
  margin-left: 4px;
}
.switch-indicator {
  position: absolute;
  left: 5px;
  top: 5px;
  bottom: 5px;
  width: calc((100% - 10px) / 2);
  border-radius: 30px;
  background: linear-gradient(180deg, #298978, #167565);
  box-shadow: 0 3px 7px #16756525;
  transition: transform 0.25s ease;
}
.switch-indicator.is-custom {
  transform: translateX(100%);
}
.billing-note {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 9px;
  margin-top: 12px;
  font-size: 10px;
  color: var(--price-muted);
}
.billing-note i {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #96aa98;
}
.plans-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(min(280px, 100%), 1fr));
  gap: 18px;
  align-items: stretch;
}
.plan-card {
  min-width: 0;
  padding: 26px;
  background: var(--price-card);
  border: 1px solid var(--price-line);
  border-radius: 18px;
  box-shadow: 0 8px 24px #244a2910;
  animation: plan-reveal 0.45s ease both;
  animation-delay: var(--reveal-delay);
  transition:
    border-color 0.2s,
    box-shadow 0.2s;
}
.plan-card:hover {
  border-color: #8db39c;
}
.plan-card.selected {
  border-color: var(--price-accent);
  box-shadow:
    0 0 0 1px var(--price-accent),
    0 12px 35px #16756518;
  background: #f1f7ef;
}
.plan-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
}
.plan-symbol {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #eef4eb;
  border: 1px solid #e1eadc;
  font-size: 12px;
  color: #38745b;
}
.plan-kind {
  font-size: 9px;
  color: #9ba79c;
  letter-spacing: 1px;
}
.selected-badge {
  font-size: 11px;
  border-radius: 20px;
  padding: 5px 10px;
  color: #fff;
  background: var(--price-accent);
}
.plan-card h2 {
  font-size: 23px;
  font-weight: 600;
  line-height: 1.4;
  overflow-wrap: anywhere;
}
.plan-description {
  font-size: 12px;
  line-height: 1.8;
  color: var(--price-muted);
  min-height: 44px;
  margin: 10px 0 16px;
  overflow-wrap: anywhere;
}
.plan-price {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 4px;
  margin-bottom: 24px;
  font-variant-numeric: tabular-nums;
}
.plan-price > span {
  font-size: 21px;
}
.plan-price strong {
  font-size: clamp(32px, 3vw, 42px);
  font-weight: 600;
  letter-spacing: -1.5px;
  overflow-wrap: anywhere;
}
.plan-price small {
  font-size: 12px;
  color: var(--price-muted);
  margin-left: 5px;
}
.plan-card :deep(.n-button),
.custom-form :deep(.n-button) {
  border-radius: 9px;
}
.button-arrow {
  margin-left: 12px;
}
.plan-quotas {
  display: grid;
  gap: 18px;
  padding: 26px 0;
}
.plan-quotas > div {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 12px;
}
.quota-icon {
  color: #4f8061;
  font-size: 19px;
  width: 22px;
  text-align: center;
}
.plan-quotas small {
  display: block;
  color: #8d9c90;
  font-size: 9px;
  margin-top: 3px;
}
.plan-quotas strong {
  margin-left: auto;
  font-size: 14px;
  font-weight: 600;
}
.plan-benefits {
  border-top: 1px solid var(--price-line);
  padding-top: 20px;
}
.plan-benefits h3 {
  font-size: 12px;
  font-weight: 600;
  margin-bottom: 14px;
}
.plan-benefits ul,
.custom-intro ul {
  list-style: none;
  margin: 0;
  padding: 0;
}
.plan-benefits li {
  display: flex;
  gap: 10px;
  font-size: 12px;
  color: var(--price-muted);
  margin-top: 12px;
  line-height: 1.7;
  overflow-wrap: anywhere;
}
.plan-benefits li > span {
  display: grid;
  place-items: center;
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  font-size: 10px;
  border-radius: 50%;
  border: 1px solid #a2c9ad;
  color: #26765b;
  background: #edf6e9;
}
.benefit-fallback {
  color: var(--price-muted);
  font-size: 12px;
  line-height: 1.8;
}
.pricing-footnote {
  display: flex;
  justify-content: center;
  gap: 8px;
  color: var(--price-muted);
  font-size: 11px;
  padding: 24px 0 30px;
}
.order-history {
  width: min(1120px, 100%);
  margin: 0 auto;
  border: 1px solid var(--price-line);
  border-radius: 16px;
}
.order-history :deep(.n-card__content) {
  padding: 0 24px 24px;
}
.custom-panel {
  max-width: 850px;
  margin: 0 auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  background: var(--price-card);
  border: 1px solid var(--price-line);
  border-radius: 18px;
  overflow: hidden;
  animation: plan-reveal 0.3s ease both;
}
.custom-intro {
  padding: 32px;
  background: #eaf2e6;
}
.custom-intro h2 {
  font-size: 23px;
  font-weight: 600;
  margin: 18px 0 12px;
}
.custom-intro p {
  font-size: 12px;
  color: var(--price-muted);
  line-height: 1.9;
}
.custom-intro li {
  font-size: 12px;
  margin-top: 16px;
}
.custom-intro li::before {
  content: '✓';
  margin-right: 10px;
  color: #397d57;
}
.custom-form {
  padding: 32px;
  align-self: center;
  min-width: 0;
}
.custom-form > label {
  display: block;
  font-size: 13px;
  margin-bottom: 12px;
}
.custom-form > p {
  font-size: 11px;
  line-height: 1.8;
  margin: 12px 0;
  color: var(--price-muted);
}
.custom-total {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 20px 0;
  gap: 8px;
}
.custom-total > span {
  font-size: 12px;
}
.custom-total > strong {
  font-size: 28px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
}
.pricing-empty {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
}
.billing-switch button:focus-visible {
  outline: 2px solid #5d9d7a;
  outline-offset: 3px;
}
.dark .pricing-page {
  --price-ink: #e6eee6;
  --price-muted: #a4b6a9;
  --price-line: #3b4d3f;
  --price-card: #222c25;
  --price-accent: #57a78f;
}
.dark .pricing-heading h1 > span {
  background: #283e2f;
  color: #a4d8b4;
}
.dark .plan-card.selected {
  background: #293e30;
}
.dark .pricing-section::before {
  opacity: 0.13;
}
.dark .custom-intro {
  background: #2b3e30;
}
@keyframes plan-reveal {
  from {
    opacity: 0;
    transform: translateY(14px);
    filter: blur(3px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
    filter: blur(0);
  }
}
@media (max-width: 700px) {
  .pricing-page {
    padding: 20px 12px;
  }
  .pricing-heading h1 {
    font-size: 25px;
  }
  .pricing-heading > p {
    line-height: 1.8;
  }
  .custom-panel {
    grid-template-columns: 1fr;
  }
  .custom-intro,
  .custom-form {
    padding: 24px;
  }
  .pricing-footnote {
    text-align: center;
    line-height: 1.8;
  }
  .plan-card {
    padding: 24px;
  }
}
@media (prefers-reduced-motion: reduce) {
  .plan-card,
  .custom-panel {
    animation: none;
  }
  .switch-indicator,
  .billing-switch button {
    transition: none;
  }
}
</style>
