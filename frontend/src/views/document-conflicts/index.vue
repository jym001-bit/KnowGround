<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { NButton, NEmpty, NPopconfirm, NSpin, NTag } from 'naive-ui';
import {
  decideDocumentConflict,
  fetchDocumentConflictContext,
  fetchDocumentConflicts,
  undoDocumentConflict
} from '@/service/api/document-conflict';
import type { ConflictContext, DocumentConflict } from '@/service/api/document-conflict';

type ListStatus = 'PENDING' | 'REVIEWED';

const activeStatus = ref<ListStatus>('PENDING');
const conflicts = ref<DocumentConflict[]>([]);
const loading = ref(false);
const loadFailed = ref(false);
const savingId = ref<number | null>(null);
const expandedId = ref<number | null>(null);
const contextLoadingId = ref<number | null>(null);
const contextErrors = ref<Record<number, boolean>>({});
const contexts = ref<Record<number, ConflictContext>>({});
let loadSequence = 0;

const pendingCount = computed(() => (activeStatus.value === 'PENDING' ? conflicts.value.length : null));

async function loadConflicts() {
  loadSequence += 1;
  const sequence = loadSequence;
  loading.value = true;
  loadFailed.value = false;
  const { data, error } = await fetchDocumentConflicts(activeStatus.value);
  if (sequence !== loadSequence) return;
  if (error || !data) {
    loadFailed.value = true;
  } else {
    conflicts.value = data;
  }
  loading.value = false;
}

function changeStatus(status: ListStatus) {
  if (activeStatus.value === status) return;
  activeStatus.value = status;
  conflicts.value = [];
  expandedId.value = null;
  loadConflicts();
}

async function toggleContext(item: DocumentConflict) {
  if (expandedId.value === item.id) {
    expandedId.value = null;
    return;
  }
  expandedId.value = item.id;
  if (contexts.value[item.id]) return;
  await loadContext(item.id);
}

async function loadContext(id: number) {
  if (contextLoadingId.value === id) return;
  contextLoadingId.value = id;
  contextErrors.value[id] = false;
  const { data, error } = await fetchDocumentConflictContext(id);
  if (error || !data) {
    contextErrors.value[id] = true;
  } else {
    contexts.value[id] = data;
  }
  contextLoadingId.value = null;
}

async function decide(item: DocumentConflict, keepFileMd5?: string) {
  if (savingId.value !== null) return;
  savingId.value = item.id;
  const { error } = await decideDocumentConflict(item.id, keepFileMd5 ? { keepFileMd5 } : { dismiss: true });
  savingId.value = null;
  if (error) return;
  window.$message?.success(keepFileMd5 ? '审核完成，另一篇文档已停止参与检索' : '已驳回疑似冲突');
  await loadConflicts();
}

async function undo(item: DocumentConflict) {
  if (savingId.value !== null) return;
  savingId.value = item.id;
  const { error } = await undoDocumentConflict(item.id);
  savingId.value = null;
  if (error) return;
  window.$message?.success('已撤销审核决定');
  await loadConflicts();
}

function similarityText(value: number) {
  return `${(value * 100).toFixed(1)}%`;
}

function formatTime(value: string | null) {
  return value ? new Date(value).toLocaleString('zh-CN') : '—';
}

function decisionText(item: DocumentConflict) {
  if (item.status === 'DISMISSED') return '已驳回误报';
  if (item.status === 'KEEP_LEFT') return `保留 ${item.leftFileName}`;
  if (item.status === 'KEEP_RIGHT') return `保留 ${item.rightFileName}`;
  return '待审核';
}

onMounted(() => {
  loadConflicts();
});
</script>

<template>
  <div class="conflict-page">
    <header class="page-header">
      <div>
        <span class="page-eyebrow">KNOWLEDGE / REVIEW</span>
        <h1>文档冲突审核</h1>
        <p>核对同一事实的不同说法，再决定哪份文档继续参与知识检索。</p>
      </div>
      <NButton secondary :loading="loading" @click="loadConflicts">刷新列表</NButton>
    </header>

    <section class="review-overview" aria-label="审核说明">
      <div class="overview-mark">≠</div>
      <div class="overview-copy">
        <strong>{{ pendingCount === null ? '审核记录' : `${pendingCount} 条待审核` }}</strong>
        <span>相似内容只是线索；请查看双方原文后作出决定。</span>
      </div>
      <span class="overview-note">管理员专用</span>
    </section>

    <nav class="status-switch" aria-label="冲突记录状态">
      <button type="button" :class="{ active: activeStatus === 'PENDING' }" @click="changeStatus('PENDING')">
        待审核
      </button>
      <button type="button" :class="{ active: activeStatus === 'REVIEWED' }" @click="changeStatus('REVIEWED')">
        已处理
      </button>
    </nav>

    <div v-if="loadFailed" class="load-error" role="alert">
      列表加载失败。
      <NButton text type="primary" @click="loadConflicts">重新加载</NButton>
    </div>

    <NSpin :show="loading">
      <div v-if="!loading && !loadFailed && conflicts.length === 0" class="empty-state">
        <NEmpty :description="activeStatus === 'PENDING' ? '当前没有待审核的文档冲突' : '还没有已处理的记录'" />
      </div>

      <div v-else class="conflict-list">
        <article v-for="item in conflicts" :key="item.id" class="conflict-card">
          <header class="card-header">
            <div class="card-title-group">
              <span class="record-number">#{{ item.id }}</span>
              <h2>{{ item.conflictKey }}</h2>
              <NTag v-if="item.status === 'PENDING'" type="warning" size="small" round>待判断</NTag>
              <NTag v-else size="small" round>{{ decisionText(item) }}</NTag>
            </div>
            <div class="card-meta">
              <span>相似度 {{ similarityText(item.similarity) }}</span>
              <span>{{ formatTime(item.createdAt) }}</span>
            </div>
          </header>

          <div class="document-pair">
            <section class="document-side" aria-label="第一篇文档">
              <div class="document-kicker">文档 A · 分块 {{ item.leftChunkId }}</div>
              <strong :title="item.leftFileName">{{ item.leftFileName }}</strong>
              <span class="document-md5" :title="item.leftFileMd5">{{ item.leftFileMd5 }}</span>
              <div class="fact-value">{{ item.leftValue }}</div>
            </section>
            <div class="pair-divider" aria-hidden="true">VS</div>
            <section class="document-side" aria-label="第二篇文档">
              <div class="document-kicker">文档 B · 分块 {{ item.rightChunkId }}</div>
              <strong :title="item.rightFileName">{{ item.rightFileName }}</strong>
              <span class="document-md5" :title="item.rightFileMd5">{{ item.rightFileMd5 }}</span>
              <div class="fact-value">{{ item.rightValue }}</div>
            </section>
          </div>

          <div class="context-toggle">
            <NButton text type="primary" :loading="contextLoadingId === item.id" @click="toggleContext(item)">
              {{ expandedId === item.id ? '收起原文' : '查看双方原文与相邻分块' }}
            </NButton>
          </div>
          <div v-if="expandedId === item.id" class="context-panel">
            <div v-if="contextErrors[item.id]" class="context-error">
              原文加载失败。
              <NButton text type="primary" @click="loadContext(item.id)">重试</NButton>
            </div>
            <div v-else-if="contexts[item.id]" class="context-grid">
              <div>
                <span>文档 A 原文</span>
                <pre>{{ contexts[item.id].leftText || '暂无原文' }}</pre>
              </div>
              <div>
                <span>文档 B 原文</span>
                <pre>{{ contexts[item.id].rightText || '暂无原文' }}</pre>
              </div>
            </div>
          </div>

          <footer class="card-footer">
            <div v-if="item.status !== 'PENDING'" class="review-meta">
              {{ decisionText(item) }} · {{ formatTime(item.reviewedAt) }}
            </div>
            <div v-else class="review-meta">选择保留后，另一篇文档将停止参与检索。</div>
            <div v-if="item.status === 'PENDING'" class="action-group">
              <NPopconfirm @positive-click="decide(item, item.leftFileMd5)">
                <template #trigger>
                  <NButton size="small" type="primary" :disabled="savingId !== null">保留文档 A</NButton>
                </template>
                保留「{{ item.leftFileName }}」，停止检索「{{ item.rightFileName }}」？
              </NPopconfirm>
              <NPopconfirm @positive-click="decide(item, item.rightFileMd5)">
                <template #trigger>
                  <NButton size="small" secondary type="primary" :disabled="savingId !== null">保留文档 B</NButton>
                </template>
                保留「{{ item.rightFileName }}」，停止检索「{{ item.leftFileName }}」？
              </NPopconfirm>
              <NPopconfirm @positive-click="decide(item)">
                <template #trigger>
                  <NButton size="small" quaternary :disabled="savingId !== null">驳回误报</NButton>
                </template>
                确认两篇文档没有冲突，继续允许检索？
              </NPopconfirm>
            </div>
            <NPopconfirm v-else @positive-click="undo(item)">
              <template #trigger>
                <NButton size="small" secondary :disabled="savingId !== null">撤销决定</NButton>
              </template>
              撤销后该记录会回到待审核；此前停用的文档可能重新参与检索。确定继续？
            </NPopconfirm>
          </footer>
        </article>
      </div>
    </NSpin>
  </div>
</template>

<style scoped>
.conflict-page {
  display: grid;
  gap: 20px;
  padding: 18px 22px 32px;
}
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 20px;
}
.page-eyebrow {
  color: #769189;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.18em;
}
.page-header h1 {
  margin: 8px 0 4px;
  font-size: clamp(25px, 3vw, 34px);
  line-height: 1.2;
  font-weight: 700;
}
.page-header p {
  margin: 0;
  color: #7a8582;
  font-size: 14px;
}
.review-overview {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 22px;
  border: 1px solid #dce9e3;
  border-radius: 18px;
  background: linear-gradient(110deg, #f3faf6, #f9fbf8);
}
.overview-mark {
  display: grid;
  width: 42px;
  height: 42px;
  flex: none;
  place-items: center;
  border-radius: 12px;
  background: #e1f1e7;
  color: #3e7965;
  font-size: 25px;
  font-weight: 600;
}
.overview-copy {
  display: grid;
  gap: 3px;
}
.overview-copy strong {
  font-size: 17px;
  color: #27463a;
}
.overview-copy span,
.overview-note {
  color: #71837b;
  font-size: 12px;
}
.overview-note {
  margin-left: auto;
  white-space: nowrap;
}
.status-switch {
  display: flex;
  gap: 4px;
  width: fit-content;
  padding: 4px;
  border: 1px solid #e4e9e6;
  border-radius: 12px;
  background: #f4f6f4;
}
.status-switch button {
  padding: 8px 18px;
  border: 0;
  border-radius: 9px;
  background: transparent;
  color: #6d7771;
  font-size: 13px;
  cursor: pointer;
}
.status-switch button.active {
  background: white;
  color: #28664e;
  font-weight: 650;
  box-shadow: 0 1px 6px #142d1a12;
}
.status-switch button:focus-visible {
  outline: 2px solid #388f68;
  outline-offset: 2px;
}
.load-error {
  padding: 12px 16px;
  border-radius: 10px;
  background: #fff1ee;
  color: #a44c3d;
}
.empty-state {
  padding: 52px 20px;
  border: 1px dashed #d8e2dc;
  border-radius: 16px;
  background: #fff;
}
.conflict-list {
  display: grid;
  gap: 16px;
}
.conflict-card {
  overflow: hidden;
  border: 1px solid #e0e7e3;
  border-radius: 18px;
  background: #fff;
  box-shadow: 0 8px 30px #1c3c2910;
}
.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 18px;
  padding: 20px 22px 15px;
}
.card-title-group {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  min-width: 0;
}
.record-number {
  color: #91a39a;
  font-size: 12px;
  font-weight: 700;
}
.card-title-group h2 {
  margin: 0;
  color: #203d31;
  font-size: 18px;
  font-weight: 700;
  overflow-wrap: anywhere;
}
.card-meta {
  display: flex;
  gap: 14px;
  color: #8b9690;
  font-size: 12px;
  white-space: nowrap;
}
.document-pair {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 48px minmax(0, 1fr);
  align-items: stretch;
  padding: 0 22px;
}
.document-side {
  display: grid;
  align-content: start;
  gap: 6px;
  min-width: 0;
  padding: 17px 18px;
  border: 1px solid #e8ece9;
  border-radius: 12px;
  background: #fcfdfc;
}
.document-kicker {
  color: #5f8b74;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}
.document-side strong {
  overflow: hidden;
  color: #32463b;
  font-size: 14px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.document-md5 {
  overflow: hidden;
  color: #a1aaa4;
  font-family: monospace;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.fact-value {
  margin-top: 9px;
  padding: 13px 14px;
  border-left: 3px solid #6aaf87;
  border-radius: 0 8px 8px 0;
  background: #edf6ef;
  color: #235c40;
  font-size: 15px;
  font-weight: 650;
  overflow-wrap: anywhere;
}
.pair-divider {
  display: grid;
  place-items: center;
  color: #a5b3a9;
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.08em;
}
.context-toggle {
  padding: 12px 22px 4px;
}
.context-panel {
  margin: 10px 22px 4px;
  padding: 16px;
  border-radius: 12px;
  background: #f4f7f4;
}
.context-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.context-grid span {
  color: #61776b;
  font-size: 12px;
  font-weight: 700;
}
.context-grid pre {
  max-height: 270px;
  overflow: auto;
  margin: 8px 0 0;
  color: #405047;
  font-family: inherit;
  font-size: 12px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.context-error {
  color: #a44c3d;
}
.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  min-height: 64px;
  margin-top: 14px;
  padding: 13px 22px;
  border-top: 1px solid #edf0ed;
}
.review-meta {
  color: #8b9690;
  font-size: 12px;
}
.action-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
@media (max-width: 760px) {
  .conflict-page {
    padding: 12px 10px 24px;
  }
  .page-header,
  .card-header,
  .card-footer {
    align-items: flex-start;
    flex-direction: column;
  }
  .overview-note {
    display: none;
  }
  .document-pair {
    grid-template-columns: minmax(0, 1fr);
    gap: 8px;
  }
  .pair-divider {
    min-height: 24px;
  }
  .context-grid {
    grid-template-columns: 1fr;
  }
  .card-meta {
    flex-wrap: wrap;
    white-space: normal;
  }
}
</style>
